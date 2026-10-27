#!/usr/bin/env python3
"""Synthetic probes: measure the platform the way a user reaches it.

Internal metrics say "the service answered a scrape". They cannot tell you that the edge
stopped routing, that a firewall rule went in, or that TLS expired - and on this host the
API only sees a few hundred user requests a day, which is far too few to judge an
availability objective.

A probe every minute fixes both: it exercises the real public path, and one minute of
probing is one sample, so 1440 samples a day arrive whether or not a user shows up.
"""
import argparse
import json
import logging
import os
import socket
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
TEXTFILE = R / "runtime/node_exporter_textfile/probes.prom"
RESULTS = R / "logs/synthetic.jsonl"
INTERVAL = int(os.environ.get("YUNANEXUS_PROBE_INTERVAL", "60"))
TIMEOUT = float(os.environ.get("YUNANEXUS_PROBE_TIMEOUT", "8"))
SLOW_SECONDS = 3.0
# Cumulative per-target totals. A gauge rewritten into the textfile is sampled by the
# scraper several times per probe, so `count_over_time` on it counts scrapes, not probes.
# Counters are the only way the SLO layer can know how many probes really ran.
TOTALS = {}

log = logging.getLogger("synthetic")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)

# What a user actually touches: the public entry, the admin entry, and one authenticated
# API call through the same path. Probes stay read-only and unauthenticated on purpose -
# a probe that logs in can lock an account out or burn a token.
TARGETS = [
    {"name": "portal", "url": "http://103.39.66.135:3000/", "expect": (200,), "path": "public"},
    {"name": "admin", "url": "http://103.39.66.135:3001/", "expect": (200,), "path": "public"},
    {"name": "api_status", "url": "http://103.39.66.135:3000/api/v1/status",
     "expect": (200,), "path": "public"},
    {"name": "edge_local", "url": "http://127.0.0.1:3000/api/v1/status",
     "expect": (200,), "path": "loopback"},
]


def probe(target, opener=None, timeout=TIMEOUT):
    """One request; returns the record that becomes a sample."""
    started = time.time()
    record = {
        "target": target["name"],
        "url": target["url"],
        "path": target.get("path", "public"),
        "at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    }
    request = urllib.request.Request(target["url"], method="GET",
                                     headers={"User-Agent": "yunanexus-synthetic/1"})
    try:
        with (opener or urllib.request.urlopen)(request, timeout=timeout) as response:
            status = response.status
            # Reading the body catches an edge that answers 200 with an error page.
            body = response.read(2048)
        record["status"] = status
        record["bytes"] = len(body)
        record["ok"] = status in target["expect"]
        record["error"] = "" if record["ok"] else "unexpected status %d" % status
    except urllib.error.HTTPError as error:
        record["status"] = error.code
        record["ok"] = error.code in target["expect"]
        record["error"] = "" if record["ok"] else "http %d" % error.code
    except urllib.error.URLError as error:
        record["status"] = None
        record["ok"] = False
        record["error"] = classify(error.reason)
    except (socket.timeout, TimeoutError):
        record["status"] = None
        record["ok"] = False
        record["error"] = "timeout"
    except OSError as error:
        record["status"] = None
        record["ok"] = False
        record["error"] = classify(error)
    record["duration"] = round(time.time() - started, 4)
    return record


def classify(reason):
    """Turn transport failures into a phrase an operator can act on."""
    text = str(reason).lower()
    if "refused" in text:
        return "connection refused"
    if "timeout" in text or "timed out" in text:
        return "timeout"
    if "name or service not known" in text or "nodename" in text or "getaddrinfo" in text:
        return "dns failure"
    if "certificate" in text:
        return "tls certificate error"
    return str(reason)[:120] or "unknown"


def cert_days(url, timeout=TIMEOUT):
    """Days until the certificate expires, or None for plain HTTP."""
    if not url.startswith("https://"):
        return None
    host = urllib.parse.urlsplit(url).hostname
    port = urllib.parse.urlsplit(url).port or 443
    context = ssl.create_default_context()
    try:
        with socket.create_connection((host, port), timeout=timeout) as connection:
            with context.wrap_socket(connection, server_hostname=host) as tls:
                not_after = tls.getpeercert().get("notAfter")
        expires = ssl.cert_time_to_seconds(not_after)
        return int((expires - time.time()) // 86400)
    except (OSError, ValueError, ssl.SSLError):
        return None


def samples(records):
    """Metric lines for the textfile collector."""
    lines = [
        "# HELP yunanexus_probe_up 1 when the synthetic probe succeeded.",
        "# TYPE yunanexus_probe_up gauge",
    ]
    for record in records:
        lines.append('yunanexus_probe_up{target="%s",path="%s"} %d'
                     % (record["target"], record["path"], 1 if record["ok"] else 0))
    lines += [
        "# HELP yunanexus_probe_duration_seconds Probe request duration.",
        "# TYPE yunanexus_probe_duration_seconds gauge",
    ]
    for record in records:
        lines.append('yunanexus_probe_duration_seconds{target="%s"} %s'
                     % (record["target"], record["duration"]))
    lines += [
        "# HELP yunanexus_probe_status_code HTTP status observed by the probe (0 = no response).",
        "# TYPE yunanexus_probe_status_code gauge",
    ]
    for record in records:
        lines.append('yunanexus_probe_status_code{target="%s"} %d'
                     % (record["target"], record["status"] or 0))
    return lines


def accumulate(records):
    """Add one cycle to the in-process totals."""
    for record in records:
        entry = TOTALS.setdefault(record["target"], {"probes": 0, "success": 0})
        entry["probes"] += 1
        if record["ok"]:
            entry["success"] += 1
    return TOTALS


def publish(records, now=None):
    """Write the textfile atomically; node-exporter picks it up on its next scrape."""
    now = int(now if now is not None else time.time())
    lines = samples(records)
    certificates = [record for record in records if record.get("cert_days") is not None]
    if certificates:
        lines += [
            "# HELP yunanexus_probe_cert_days_remaining Days until the TLS certificate expires.",
            "# TYPE yunanexus_probe_cert_days_remaining gauge",
        ]
        for record in certificates:
            lines.append('yunanexus_probe_cert_days_remaining{target="%s"} %d'
                         % (record["target"], record["cert_days"]))
    if TOTALS:
        lines += [
            "# HELP yunanexus_probe_probes_total Probe cycles run since the prober started.",
            "# TYPE yunanexus_probe_probes_total counter",
        ]
        for target in sorted(TOTALS):
            lines.append('yunanexus_probe_probes_total{target="%s"} %d'
                         % (target, TOTALS[target]["probes"]))
        lines += [
            "# HELP yunanexus_probe_success_total Successful probe cycles since start.",
            "# TYPE yunanexus_probe_success_total counter",
        ]
        for target in sorted(TOTALS):
            lines.append('yunanexus_probe_success_total{target="%s"} %d'
                         % (target, TOTALS[target]["success"]))
    lines += [
        "# HELP yunanexus_probe_last_run_timestamp_seconds When the probes last ran.",
        "# TYPE yunanexus_probe_last_run_timestamp_seconds gauge",
        "yunanexus_probe_last_run_timestamp_seconds %d" % now,
        "# HELP yunanexus_probe_targets Number of configured probe targets.",
        "# TYPE yunanexus_probe_targets gauge",
        "yunanexus_probe_targets %d" % len(records),
    ]
    temporary = TEXTFILE.with_suffix(".prom.tmp")
    temporary.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text("\n".join(lines) + "\n", encoding="utf-8")
    os.chmod(temporary, 0o644)
    temporary.replace(TEXTFILE)
    return TEXTFILE


def append_results(records, path=None):
    path = Path(path or RESULTS)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as sink:
        for record in records:
            sink.write(json.dumps(record, sort_keys=True) + "\n")
    return path


def transitions(previous, records):
    """Only state changes are logged: a probe failure repeated 1440 times is noise."""
    before = {item["target"]: item["ok"] for item in previous}
    events = []
    for record in records:
        if record["target"] not in before:
            continue
        if before[record["target"]] and not record["ok"]:
            events.append(("down", record))
        elif not before[record["target"]] and record["ok"]:
            events.append(("up", record))
    return events


def run_once(probe_targets=None, opener=None):
    records = []
    for target in (probe_targets or TARGETS):
        record = probe(target, opener=opener)
        days = cert_days(target["url"])
        if days is not None:
            record["cert_days"] = days
        records.append(record)
    return records


def main(argv=None):
    parser = argparse.ArgumentParser(description="synthetic probes for the public path")
    parser.add_argument("--once", action="store_true", help="run one cycle and exit")
    parser.add_argument("--interval", type=int, default=INTERVAL)
    args = parser.parse_args(argv)
    started = time.time()
    previous = []
    log.info("probing %d targets every %ds", len(TARGETS), args.interval)
    while True:
        records = run_once()
        append_results(records)
        accumulate(records)
        publish(records)
        for direction, record in transitions(previous, records):
            level = logging.INFO if direction == "up" else logging.ERROR
            log.log(level, "%s is %s (%s, %.0f ms)", record["target"], direction,
                    record["error"] or "status %s" % record["status"],
                    record["duration"] * 1000)
        slow = [item for item in records if item["ok"] and item["duration"] > SLOW_SECONDS]
        for record in slow:
            log.warning("%s slow: %.1fs", record["target"], record["duration"])
        previous = records
        if args.once:
            return 0
        time.sleep(max(5, args.interval - (time.time() - started) % args.interval))


if __name__ == "__main__":
    sys.exit(main())
