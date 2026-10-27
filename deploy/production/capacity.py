#!/usr/bin/env python3
"""Capacity: what is growing, how fast, and when it runs out.

Threshold alerts answer "is the disk full yet", which is the least useful question - by
the time it fires the only remaining option is an emergency. This keeps a local history
of sizes and fits a trend, so the answer becomes "in eleven days" while there is still
time to act.

Two sources, deliberately:

* local snapshots (this file's own JSONL) for directories, which no exporter knows about
* the metrics backend for filesystems, because it already holds history from before this
  tool existed

A trend on three points is a guess, so forecasts stay marked "not ready" until there is
real span. Same rule as the SLO layer: no confident number from thin data.
"""
import argparse
import json
import logging
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
VM_URL = os.environ.get("YUNANEXUS_VM_URL", "http://127.0.0.1:8428")
WORK = R / "runtime/capacity"
HISTORY = WORK / "history.jsonl"
TEXTFILE = R / "runtime/node_exporter_textfile/capacity.prom"
GIB = 1024 ** 3
MIB = 1024 ** 2

# Keep this much room for the OS to breathe before calling a filesystem "full".
RESERVE = 512 * MIB
WARN_DAYS = 21
CRITICAL_DAYS = 7
GROWTH_SPIKE = GIB          # bytes per day that deserves a look
DELETED_OPEN_WARN = GIB
# tmpfs and virtual filesystems only: /tmp and /var/tmp are real disk on this host and
# must stay counted.
# "/memfd:" is not a path at all - it is the kernel naming a memory object, and it can
# report terabytes while the disk is untouched.
VIRTUAL_PREFIXES = ("/dev/", "/proc/", "/sys/", "/run/", "/memfd:")
MIN_POINTS = 3
# One hour is short for a trend, but capacity moves slowly and the backend samples every
# 5 minutes, so a 1 hour span already holds twelve points. Requiring 2 hours would leave
# the tool blind for its first two hours of life for no real gain.
MIN_SPAN = 3600
HISTORY_DAYS = 30

FILESYSTEMS = [("/", "/"), ("/YunaNexus", "/YunaNexus")]
DIRECTORIES = [
    ("platform", R),
    ("docker", R / "runtime/docker"),
    ("mysql", R / "data/mysql"),
    ("rocketmq", R / "data/rocketmq"),
    ("nacos", R / "data/nacos"),
    ("victoriametrics", R / "data/victoriametrics"),
    ("grafana", R / "data/grafana"),
    ("backups", R / "backups"),
    ("logs", R / "logs"),
    ("releases", R / "releases"),
]

log = logging.getLogger("capacity")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


# ------------------------------------------------------------------ measurement


def filesystem(path):
    """total/used/available bytes for the filesystem holding `path`."""
    stat = os.statvfs(path)
    total = stat.f_blocks * stat.f_frsize
    available = stat.f_bavail * stat.f_frsize
    return {"total": total, "used": total - stat.f_bfree * stat.f_frsize,
            "available": available}


def directory_bytes(path, timeout=120):
    """Apparent size of a directory; None when it cannot be read."""
    try:
        result = subprocess.run(["du", "-sb", str(path)], capture_output=True, text=True,
                                timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0 or not result.stdout.strip():
        return None
    try:
        return int(result.stdout.split()[0])
    except ValueError:
        return None


def deleted_open_bytes(entries=None):
    """Bytes held by files that were deleted but are still open.

    This is the classic "df says full, du says empty" cause, and nothing else in the
    stack reports it. Special files (memfd, sockets, /dev/shm) are skipped because their
    apparent size has nothing to do with disk usage.
    """
    if entries is None:
        entries = list_deleted_open()
    total = 0
    for target, size in entries:
        if "(deleted)" not in target:
            continue
        # Only real disk paths count. tmpfs (RAM) and virtual filesystems report sizes
        # that have nothing to do with free space - a 2 TB memfd would dwarf everything.
        if not target.startswith("/") or target.startswith(VIRTUAL_PREFIXES):
            continue
        total += size
    return total


def list_deleted_open():
    """[(link target, bytes)] for every open file descriptor, best effort."""
    found = []
    for process in Path("/proc").iterdir():
        if not process.name.isdigit():
            continue
        descriptor_dir = process / "fd"
        try:
            descriptors = list(descriptor_dir.iterdir())
        except (OSError, PermissionError):
            continue
        for descriptor in descriptors:
            try:
                target = os.readlink(descriptor)
            except OSError:
                continue
            if "(deleted)" not in target:
                continue
            try:
                size = descriptor.stat().st_size
            except OSError:
                size = 0
            found.append((target, size))
    return found


def snapshot():
    """One measurement of everything tracked."""
    now = int(time.time())
    record = {"at": now, "filesystems": {}, "directories": {}}
    for name, path in FILESYSTEMS:
        try:
            record["filesystems"][name] = filesystem(path)
        except OSError as error:
            log.warning("cannot stat %s: %s", path, error)
    for name, path in DIRECTORIES:
        size = directory_bytes(path)
        if size is not None:
            record["directories"][name] = size
    record["deleted_open"] = deleted_open_bytes()
    return record


# ------------------------------------------------------------------ history


def append_history(record, path=None):
    path = Path(path or HISTORY)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as sink:
        sink.write(json.dumps(record, sort_keys=True) + "\n")
    return path


def load_history(path=None, now=None, days=HISTORY_DAYS):
    """Snapshots inside the window, oldest first."""
    path = Path(path or HISTORY)
    now = now or time.time()
    cutoff = now - days * 86400
    if not path.exists():
        return []
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            record = json.loads(line)
        except ValueError:
            continue
        if isinstance(record.get("at"), (int, float)) and record["at"] >= cutoff:
            records.append(record)
    records.sort(key=lambda item: item["at"])
    return records


def trim_history(path=None, now=None, days=HISTORY_DAYS):
    """Drop snapshots older than the window so the file cannot grow forever."""
    path = Path(path or HISTORY)
    if not path.exists():
        return 0
    kept = load_history(path, now=now, days=days)
    removed = 0
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip():
            removed += 1
    temporary = path.with_suffix(".tmp")
    temporary.write_text("".join(json.dumps(item, sort_keys=True) + "\n" for item in kept),
                         encoding="utf-8")
    temporary.replace(path)
    return removed - len(kept)


# ------------------------------------------------------------------ forecasting


def series(history, kind, name):
    """[(timestamp, bytes)] for one tracked item."""
    points = []
    for record in history:
        value = (record.get(kind) or {}).get(name)
        if kind == "filesystems" and isinstance(value, dict):
            value = value.get("available")
        if isinstance(value, (int, float)):
            points.append((record["at"], float(value)))
    return points


def slope(points, minimum=MIN_POINTS, span=MIN_SPAN):
    """Least-squares slope in bytes per day, or None when the data cannot support it."""
    if len(points) < minimum:
        return None
    first, last = points[0][0], points[-1][0]
    if last - first < span:
        return None
    count = len(points)
    mean_x = sum(point[0] for point in points) / count
    mean_y = sum(point[1] for point in points) / count
    denominator = sum((point[0] - mean_x) ** 2 for point in points)
    if not denominator:
        return None
    numerator = sum((point[0] - mean_x) * (point[1] - mean_y) for point in points)
    return (numerator / denominator) * 86400


def robust_slope(points, minimum=MIN_POINTS, span=MIN_SPAN):
    """Median of per-interval deltas, in bytes per day.

    Least squares is easily fooled by a single event: deleting 2 GiB once produces a
    steep "trend" that says nothing about the future. The median of consecutive deltas
    ignores that one interval as long as most intervals are ordinary.
    """
    if len(points) < minimum:
        return None
    if points[-1][0] - points[0][0] < span:
        return None
    deltas = []
    for (start, first), (end, second) in zip(points, points[1:]):
        if end - start <= 0:
            continue
        deltas.append((second - first) / (end - start))
    if not deltas:
        return None
    deltas.sort()
    middle = len(deltas) // 2
    if len(deltas) % 2:
        median = deltas[middle]
    else:
        median = (deltas[middle - 1] + deltas[middle]) / 2
    return median * 86400


def days_to_full(available, rate_per_day, reserve=RESERVE):
    """Days until a filesystem reaches the reserve.

    `available` grows and `rate` is its slope, so shrinking means a positive rate.
    """
    if rate_per_day is None:
        return None
    if rate_per_day >= 0:
        return None            # not shrinking: never fills at this trend
    headroom = available - reserve
    if headroom <= 0:
        return 0.0
    return round(headroom / -rate_per_day, 1)


def backend_series(mountpoint, window="24h", step=300, timeout=20):
    """[(timestamp, available bytes)] from the metrics backend.

    The series is fetched raw and passed through the same robust fit as local history:
    `deriv()` is a least-squares window, so a single large deletion inside the window
    turns into a fake multi-gigabyte-per-day trend - which is exactly what happened the
    first time this ran, right after a 2 GiB cleanup.
    """
    end = int(time.time())
    start = end - window_seconds(window)
    parameters = {
        "query": 'node_filesystem_avail_bytes{mountpoint="%s"}' % mountpoint,
        "start": str(start), "end": str(end), "step": str(step),
    }
    url = "%s/api/v1/query_range?%s" % (VM_URL, urllib.parse.urlencode(parameters))
    try:
        with urllib.request.urlopen(url, timeout=timeout) as response:
            payload = json.loads(response.read())
    except (urllib.error.URLError, OSError, ValueError):
        return []
    result = (payload.get("data") or {}).get("result") or []
    points = []
    for sample in result[:1]:
        for stamp, value in sample.get("values") or []:
            try:
                points.append((float(stamp), float(value)))
            except (TypeError, ValueError):
                continue
    return points


def backend_slope(mountpoint, window="24h"):
    """Robust filesystem slope from the backend, or None when it cannot be computed."""
    return robust_slope(backend_series(mountpoint, window))


def window_seconds(window):
    """"90m" / "6h" / "24h" -> seconds."""
    import re as _re
    units = {"s": 1, "m": 60, "h": 3600, "d": 86400, "w": 604800}
    total = 0
    for value, unit in _re.findall(r"(\d+)([smhdw])", window):
        total += int(value) * units[unit]
    return total


def evaluate(history, backend=None, now=None):
    """Per-target capacity state, with forecasts only where data allows."""
    now = now or time.time()
    backend = backend or backend_slope
    states = []
    for name, _ in FILESYSTEMS:
        points = series(history, "filesystems", name)
        rate = robust_slope(points)
        source = "history"
        if rate is None:
            rate = backend(name, "24h")
            source = "backend"
        latest = (history[-1]["filesystems"].get(name) if history else None) or {}
        available = latest.get("available")
        states.append({
            "kind": "filesystem",
            "target": name,
            "used": latest.get("used"),
            "available": available,
            "growth_per_day": round(rate, 1) if rate is not None else None,
            "source": source if rate is not None else "none",
            "days_to_full": days_to_full(available, rate) if available is not None else None,
        })
    for name, _ in DIRECTORIES:
        points = series(history, "directories", name)
        rate = robust_slope(points)
        latest = (history[-1]["directories"].get(name) if history else None)
        states.append({
            "kind": "directory",
            "target": name,
            "used": latest,
            "available": None,
            "growth_per_day": round(rate, 1) if rate is not None else None,
            "source": "history" if rate is not None else "none",
            "days_to_full": None,
            "projected_30d": round(latest + rate * 30) if (latest and rate) else None,
        })
    return states


# ------------------------------------------------------------------ reporting


def render_metrics(states, deleted_open=0, ok=True, now=None):
    now = int(time.time() if now is None else now)
    lines = [
        "# HELP yunanexus_capacity_used_bytes Bytes in use per target.",
        "# TYPE yunanexus_capacity_used_bytes gauge",
    ]
    for state in states:
        if state["used"] is not None:
            lines.append('yunanexus_capacity_used_bytes{target="%s",kind="%s"} %d'
                         % (state["target"], state["kind"], state["used"]))
    lines += [
        "# HELP yunanexus_capacity_avail_bytes Bytes available on a filesystem.",
        "# TYPE yunanexus_capacity_avail_bytes gauge",
    ]
    for state in states:
        if state.get("available") is not None:
            lines.append('yunanexus_capacity_avail_bytes{target="%s"} %d'
                         % (state["target"], state["available"]))
    lines += [
        "# HELP yunanexus_capacity_growth_bytes_per_day Observed growth (negative = shrinking).",
        "# TYPE yunanexus_capacity_growth_bytes_per_day gauge",
    ]
    for state in states:
        if state["growth_per_day"] is not None:
            lines.append('yunanexus_capacity_growth_bytes_per_day{target="%s"} %s'
                         % (state["target"], state["growth_per_day"]))
    lines += [
        "# HELP yunanexus_capacity_days_to_full Days until the reserve is reached.",
        "# TYPE yunanexus_capacity_days_to_full gauge",
    ]
    for state in states:
        if state.get("days_to_full") is not None:
            lines.append('yunanexus_capacity_days_to_full{target="%s"} %s'
                         % (state["target"], state["days_to_full"]))
    lines += [
        "# HELP yunanexus_capacity_forecast_ready Whether a trend could be computed.",
        "# TYPE yunanexus_capacity_forecast_ready gauge",
    ]
    for state in states:
        lines.append('yunanexus_capacity_forecast_ready{target="%s"} %d'
                     % (state["target"], 0 if state["growth_per_day"] is None else 1))
    lines += [
        "# HELP yunanexus_capacity_deleted_open_bytes Bytes held by deleted-but-open files.",
        "# TYPE yunanexus_capacity_deleted_open_bytes gauge",
        "yunanexus_capacity_deleted_open_bytes %d" % deleted_open,
        "# HELP yunanexus_capacity_success Whether the last capacity run completed.",
        "# TYPE yunanexus_capacity_success gauge",
        "yunanexus_capacity_success %d" % (1 if ok else 0),
        "# HELP yunanexus_capacity_timestamp_seconds When the snapshot was taken.",
        "# TYPE yunanexus_capacity_timestamp_seconds gauge",
        "yunanexus_capacity_timestamp_seconds %d" % now,
    ]
    return "\n".join(lines) + "\n"


def write(text, target=None):
    target = Path(target or TEXTFILE)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".prom.tmp")
    temporary.write_text(text, encoding="utf-8")
    os.chmod(temporary, 0o644)
    temporary.replace(target)
    return target


def describe(value):
    """Human units for the console table."""
    if value is None:
        return "-"
    sign = "-" if value < 0 else ""
    value = abs(value)
    if value >= GIB:
        return "%s%.1fG" % (sign, value / GIB)
    if value >= MIB:
        return "%s%.0fM" % (sign, value / MIB)
    return "%s%d" % (sign, value)


def render_table(states):
    lines = ["%-18s %-10s %9s %9s %10s %9s %s" % (
        "target", "kind", "used", "avail", "per day", "to full", "source")]
    for state in sorted(states, key=lambda item: (item["kind"], -(item["used"] or 0))):
        forecast = "%s d" % state["days_to_full"] if state.get("days_to_full") is not None else "-"
        lines.append("%-18s %-10s %9s %9s %10s %9s %s" % (
            state["target"], state["kind"], describe(state["used"]),
            describe(state.get("available")), describe(state["growth_per_day"]),
            forecast, state["source"]))
    return "\n".join(lines)


def seed_from_backend(mounts=None, window="24h"):
    """Import filesystem history from the metrics backend into the local history.

    Without this the tool has no trend at all until it has been running for an hour,
    while the backend already holds hours of points.
    """
    imported = {}
    for name, mountpoint in (mounts or FILESYSTEMS):
        imported[name] = backend_series(mountpoint, window)
    stamps = sorted({int(stamp) for points in imported.values() for stamp, _ in points})
    written = 0
    for stamp in stamps:
        record = {"at": stamp, "filesystems": {}, "directories": {}, "deleted_open": 0,
                  "seeded": True}
        for name, points in imported.items():
            for point_stamp, value in points:
                if int(point_stamp) == stamp:
                    record["filesystems"][name] = {"available": int(value), "used": 0,
                                                   "total": 0}
                    break
        if record["filesystems"]:
            append_history(record)
            written += 1
    return written


def main(argv=None):
    parser = argparse.ArgumentParser(description="capacity inventory and forecast")
    parser.add_argument("--json", action="store_true", help="print the full measurement")
    parser.add_argument("--seed", action="store_true",
                        help="import filesystem history from the metrics backend first")
    parser.add_argument("--no-snapshot", action="store_true",
                        help="forecast from existing history without recording a new point")
    args = parser.parse_args(argv)
    if args.seed:
        count = seed_from_backend()
        log.info("imported %d filesystem snapshots from the metrics backend", count)
    try:
        record = snapshot()
    except OSError as error:
        log.error("snapshot failed: %s", error)
        write(render_metrics([], ok=False))
        return 2
    if not args.no_snapshot:
        append_history(record)
        trim_history()
        history = load_history()
    else:
        history = load_history() or [record]
    states = evaluate(history)
    write(render_metrics(states, deleted_open=record.get("deleted_open", 0)))
    if args.json:
        print(json.dumps({"snapshot": record, "history_points": len(history),
                          "states": states}, indent=2, sort_keys=True))
    else:
        print(render_table(states))
        print("\nhistory: %d snapshots; deleted-but-open: %s"
              % (len(history), describe(record.get("deleted_open", 0))))
        risky = [state for state in states
                 if state.get("days_to_full") is not None
                 and state["days_to_full"] <= WARN_DAYS]
        for state in risky:
            log.warning("%s fills in %s days at the current rate",
                        state["target"], state["days_to_full"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
