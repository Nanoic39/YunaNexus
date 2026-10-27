#!/usr/bin/env python3
"""Aggregate container logs into rotated JSONL files and export metrics.

Why not Loki yet: Loki plus Promtail costs ~100 MiB of resident memory, and this
4.9 GiB host is already swapping (see the monitoring report). This collector
costs ~15 MiB, keeps the same information, and writes newline-delimited JSON that
Promtail can ship verbatim once the host has memory to spare - no rework.

Design notes:
  * `docker logs --since <ts>` is polled instead of holding a follow pipe, so a
    collector restart cannot lose or duplicate lines: the last timestamp is
    persisted and everything at or before it is dropped.
  * Counters live in the state file and are exported as Prometheus counters, so
    error rates eventually become alert rules.
"""
import gzip
import json
import logging
import os
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
DOCKER_HOST = os.environ.get("YUNANEXUS_DOCKER_HOST", "unix://" + str(R / "runtime/docker.sock"))
STATE_PATH = R / "runtime/logship-state.json"
OUTPUT_DIR = R / "logs/aggregated"
TEXTFILE = R / "runtime/node_exporter_textfile/logs.prom"
NAME_PREFIX = os.environ.get("YUNANEXUS_LOG_PREFIX", "yunanexus-")
KEEP_DAYS = int(os.environ.get("YUNANEXUS_LOG_KEEP_DAYS", "7"))
COMPRESS_AFTER_HOURS = int(os.environ.get("YUNANEXUS_LOG_COMPRESS_HOURS", "48"))
MAX_DIR_BYTES = int(os.environ.get("YUNANEXUS_LOG_MAX_BYTES", str(2 * 1024 ** 3)))
CYCLE_SECONDS = int(os.environ.get("YUNANEXUS_LOG_CYCLE", "10"))
MAX_LINES_PER_CYCLE = 200000
BACKFILL_SECONDS = int(os.environ.get("YUNANEXUS_LOG_BACKFILL", "3600"))
LEVELS = ("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")

STAMP = re.compile(r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?(?:Z|[+-]\d\d:\d\d))\s(.*)$", re.S)
# Structured markers only. Keyword guessing on "error" misreads JVM startup banners
# (they mention HeapDumpOnOutOfMemoryError) and nginx access logs for files whose
# name contains "error", which would make the error-spike alert cry wolf.
SPRING_LEVEL = re.compile(r"(?:^|\s)(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\s+\d+\s+---")
JSON_LEVEL = re.compile(r'"(?:level|severity)"\s*:\s*"([A-Za-z]+)"')
BRACKET_LEVEL = re.compile(r"^\[(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|NOTE|SYSTEM)\]", re.I)
ACCESS_STATUS = re.compile(r'"(?:GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS)\s[^"]*"\s(\d{3})\b')

STARTED_AT = time.time()

log = logging.getLogger("logship")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


def docker(*args, timeout=120):
    return subprocess.run(["docker", *args], capture_output=True, text=True, timeout=timeout,
                          env=dict(os.environ, DOCKER_HOST=DOCKER_HOST))


def service_name(container):
    """yunanexus-production-auth-1 -> auth, yunanexus-monitoring-grafana-1 -> monitoring-grafana."""
    name = container[:-2] if container.endswith("-1") else container
    for prefix in (NAME_PREFIX, "production-"):
        if name.startswith(prefix):
            name = name[len(prefix):]
    return name


ISO_STAMP = re.compile(
    r"^(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d+))?(Z|[+-]\d\d:\d\d)?$")


def parse_timestamp(text):
    """Parse an ISO timestamp, including docker's nanosecond precision.

    Splitting the fraction with a digit filter would swallow the offset digits and
    silently reinterpret a UTC timestamp as local time, which made every line look
    older than the watermark and dropped it.
    """
    match = ISO_STAMP.match((text or "").strip())
    if not match:
        return None
    base, fraction, offset = match.groups()
    fraction = (fraction or "")[:6].ljust(6, "0")
    offset = "+00:00" if offset in (None, "Z") else offset
    try:
        return datetime.fromisoformat("%s.%s%s" % (base, fraction, offset)).timestamp()
    except ValueError:
        return None


def detect_level(content):
    match = SPRING_LEVEL.search(content)
    if match:
        return match.group(1)
    match = JSON_LEVEL.search(content)
    if match:
        candidate = match.group(1).upper()
        if candidate in LEVELS:
            return candidate
        return {"WARNING": "WARN", "ERR": "ERROR", "CRIT": "ERROR", "CRITICAL": "ERROR"}.get(
            candidate, "UNKNOWN")
    match = BRACKET_LEVEL.search(content)
    if match:
        candidate = match.group(1).upper()
        return {"WARNING": "WARN", "NOTE": "INFO", "SYSTEM": "INFO"}.get(candidate, candidate)
    match = ACCESS_STATUS.search(content)
    if match:
        status = int(match.group(1))
        if status >= 500:
            return "ERROR"
        return "WARN" if status >= 400 else "INFO"
    return "UNKNOWN"


def parse_lines(payload, last_ts):
    """Yield (timestamp, timestamp_text, level, content) newer than last_ts."""
    for raw in payload.splitlines():
        match = STAMP.match(raw)
        if not match:
            continue
        stamp_text, content = match.group(1), match.group(2)
        stamp = parse_timestamp(stamp_text)
        if stamp is None or stamp <= last_ts:
            continue
        yield stamp, stamp_text, detect_level(content), content


def append_jsonl(service, entries):
    if not entries:
        return 0
    day = datetime.fromtimestamp(entries[0][0]).strftime("%Y-%m-%d")
    directory = OUTPUT_DIR / service
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / ("%s.jsonl" % day)
    with path.open("a", encoding="utf-8") as handle:
        for stamp, _text, level, content in entries:
            handle.write(json.dumps({
                "ts": datetime.fromtimestamp(stamp).isoformat(timespec="milliseconds"),
                "epoch": round(stamp, 3),
                "service": service,
                "level": level,
                "msg": content,
            }, ensure_ascii=False) + "\n")
    return len(entries)


def docker_containers():
    listing = docker("ps", "--format", "{{.Names}}")
    if listing.returncode != 0:
        raise RuntimeError("docker ps failed: %s" % listing.stderr.strip()[:200])
    return [name for name in listing.stdout.split() if name.startswith(NAME_PREFIX)]


def load_state():
    try:
        data = json.loads(STATE_PATH.read_text())
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_state(state):
    STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
    pending = STATE_PATH.with_suffix(".tmp")
    pending.write_text(json.dumps(state, indent=2, sort_keys=True))
    pending.replace(STATE_PATH)


def collect(state, now=None):
    now = now or time.time()
    containers = docker_containers()
    for name in containers:
        entry = state.setdefault(name, {"last_ts": 0.0, "counts": {}, "lines": 0, "bytes": 0,
                                        "last_line": 0.0})
        if not entry.get("initialised"):
            # First sighting: import the last hour only. Starting at "now" hides the
            # context that matters right after an incident, starting at zero would
            # replay the whole container history.
            entry["last_ts"] = now - BACKFILL_SECONDS
            entry["initialised"] = True
            state[name] = entry
            continue
        # Must carry an explicit UTC offset: docker reads a naive timestamp as UTC,
        # which on a +08:00 host turns "now" into a time eight hours in the future
        # and silently returns no lines.
        since = datetime.fromtimestamp(max(entry["last_ts"] - 1, 0), timezone.utc).isoformat(
            timespec="seconds")
        fetch_started = now if now is not None else time.time()
        result = docker("logs", "--since", since, "--timestamps", name)
        if result.returncode != 0:
            log.warning("docker logs failed for %s: %s", name, result.stderr.strip()[:200])
            continue
        entries = list(parse_lines(result.stdout + result.stderr, entry["last_ts"]))
        if len(entries) > MAX_LINES_PER_CYCLE:
            entries = entries[-MAX_LINES_PER_CYCLE:]
        service = service_name(name)
        written = append_jsonl(service, entries)
        for stamp, _text, level, content in entries:
            entry["counts"][level] = entry["counts"].get(level, 0) + 1
            entry["bytes"] = entry["bytes"] + len(content) + 1
            entry["last_line"] = max(entry["last_line"], stamp)
        entry["lines"] = entry["lines"] + written
        # Advance to the fetch time, not just to the newest line: otherwise a quiet
        # container keeps its old watermark and every cycle re-appends the same
        # window (observed as duplicated log lines).
        entry["last_ts"] = max(entry["last_ts"], entries[-1][0] if entries else 0.0,
                               fetch_started)
    known = set(containers)
    for stale in [key for key in state if key not in known]:
        state.pop(stale)   # container was removed by a deploy
    return state


def render_metrics(state, now=None):
    now = now or time.time()
    lines = [
        "# HELP yunanexus_log_lines_total Log lines collected per service and level.",
        "# TYPE yunanexus_log_lines_total counter",
        "# HELP yunanexus_log_bytes_total Log payload bytes collected per service.",
        "# TYPE yunanexus_log_bytes_total counter",
        "# HELP yunanexus_log_last_line_timestamp_seconds Timestamp of the newest collected line.",
        "# TYPE yunanexus_log_last_line_timestamp_seconds gauge",
        "# HELP yunanexus_logship_last_cycle_timestamp_seconds When the collector last ran.",
        "# TYPE yunanexus_logship_last_cycle_timestamp_seconds gauge",
        "# HELP yunanexus_logship_uptime_seconds How long the collector has been running.",
        "# TYPE yunanexus_logship_uptime_seconds gauge",
    ]
    for name, entry in sorted(state.items()):
        service = service_name(name)
        for level, count in sorted(entry["counts"].items()):
            lines.append('yunanexus_log_lines_total{service="%s",level="%s"} %d'
                         % (service, level, count))
        lines.append('yunanexus_log_bytes_total{service="%s"} %d' % (service, entry["bytes"]))
        if entry["last_line"]:
            lines.append('yunanexus_log_last_line_timestamp_seconds{service="%s"} %.3f'
                         % (service, entry["last_line"]))
    lines.append("yunanexus_logship_last_cycle_timestamp_seconds %d" % int(now))
    lines.append("yunanexus_logship_uptime_seconds %d" % max(0, int(now - STARTED_AT)))
    return "\n".join(lines) + "\n"


def write_textfile(state, now=None):
    TEXTFILE.parent.mkdir(parents=True, exist_ok=True)
    pending = TEXTFILE.with_suffix(".tmp")
    pending.write_text(render_metrics(state, now))
    os.chmod(pending, 0o644)
    pending.replace(TEXTFILE)


def rotate(now=None):
    """Compress finished days, then delete anything past the retention window."""
    now = now or time.time()
    if not OUTPUT_DIR.is_dir():
        return
    compressed = removed = 0
    for path in OUTPUT_DIR.rglob("*.jsonl"):
        modified = path.stat().st_mtime
        if (now - modified) / 3600 <= COMPRESS_AFTER_HOURS:
            continue
        target_path = Path(str(path) + ".gz")
        with path.open("rb") as source, gzip.open(target_path, "wb") as target:
            shutil.copyfileobj(source, target)
        # Keep the original timestamp: compressing must not make an expired file
        # look fresh to the retention sweep below.
        os.utime(target_path, (modified, modified))
        path.unlink()
        compressed += 1
    cutoff = now - KEEP_DAYS * 86400
    for path in sorted(OUTPUT_DIR.rglob("*")):
        if path.is_file() and path.stat().st_mtime < cutoff:
            path.unlink()
            removed += 1
    total = sum(p.stat().st_size for p in OUTPUT_DIR.rglob("*") if p.is_file())
    if total > MAX_DIR_BYTES:
        for path in sorted((p for p in OUTPUT_DIR.rglob("*") if p.is_file()),
                           key=lambda p: p.stat().st_mtime):
            size = path.stat().st_size
            path.unlink()
            removed += 1
            total -= size
            if total <= MAX_DIR_BYTES * 0.9:
                break
    if compressed or removed:
        log.info("rotation: compressed %d, removed %d", compressed, removed)


def cycle():
    state = collect(load_state())
    save_state(state)
    write_textfile(state)
    rotate()
    return state


def main(argv):
    if "--once" in argv:
        state = cycle()
        log.info("collected from %d containers", len(state))
        return 0
    log.info("log collector started (cycle %ds, retention %dd)", CYCLE_SECONDS, KEEP_DAYS)
    while True:
        try:
            cycle()
        except Exception as error:   # keep collecting whatever still works
            log.error("collector cycle failed: %s: %s", type(error).__name__, error)
        time.sleep(CYCLE_SECONDS)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
