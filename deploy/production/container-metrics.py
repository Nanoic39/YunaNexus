#!/usr/bin/env python3
"""Publish per-container resource metrics through the node_exporter textfile collector.

The JVM services expose their own metrics, but MySQL, Redis, Nacos, RocketMQ and
the auth service (whose actuator is closed) have none. `docker stats` already
knows their memory and CPU, so this writes them as Prometheus text instead of
deploying cAdvisor, which would cost another ~100 MiB on a host that cannot spare it.
"""
import json
import logging
import logging.handlers
import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
TEXTFILE_DIR = Path(os.environ.get("YUNANEXUS_TEXTFILE_DIR", str(R / "runtime/node_exporter_textfile")))
DOCKER_HOST = os.environ.get("YUNANEXUS_DOCKER_HOST", "unix://" + str(R / "runtime/docker.sock"))
OUTPUT = TEXTFILE_DIR / "containers.prom"

log = logging.getLogger("container-metrics")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)

UNITS = {"B": 1, "KiB": 1024, "MiB": 1024 ** 2, "GiB": 1024 ** 3, "TiB": 1024 ** 4}


def docker(*args, timeout=60):
    return subprocess.run(
        ["docker", *args], capture_output=True, text=True, timeout=timeout,
        env=dict(os.environ, DOCKER_HOST=DOCKER_HOST),
    )


def parse_bytes(text):
    """"123.4MiB" / "1.5GiB" / "0B" -> bytes."""
    text = (text or "").strip()
    for unit in ("TiB", "GiB", "MiB", "KiB", "B"):
        if text.endswith(unit):
            return float(text[: -len(unit)]) * UNITS[unit]
    return None


def collect():
    stats = docker("stats", "--no-stream", "--format", "{{json .}}")
    if stats.returncode != 0:
        raise RuntimeError("docker stats failed: %s" % stats.stderr.strip()[:300])
    samples = []
    for line in stats.stdout.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        name = (entry.get("Name") or "").strip()
        if not name:
            continue
        used, _, limit = (entry.get("MemUsage") or "").partition("/")
        memory = parse_bytes(used)
        cap = parse_bytes(limit)
        cpu = (entry.get("CPUPerc") or "0%").rstrip("%")
        try:
            cpu_value = float(cpu)
        except ValueError:
            cpu_value = 0.0
        if memory is not None:
            samples.append(("yunanexus_container_memory_bytes", name, memory))
        if cap:
            samples.append(("yunanexus_container_memory_limit_bytes", name, cap))
        samples.append(("yunanexus_container_cpu_percent", name, cpu_value))
        if memory is not None and cap:
            samples.append(("yunanexus_container_memory_ratio", name, round(memory / cap, 4)))
    return samples


def collect_restarts():
    """Restart counts catch crash loops that never make it into a CPU metric."""
    listing = docker("ps", "-a", "--format", "{{.Names}}")
    samples = []
    for name in listing.stdout.split():
        detail = docker("inspect", "--format", "{{.RestartCount}}", name, timeout=20)
        if detail.returncode == 0 and detail.stdout.strip().isdigit():
            samples.append(("yunanexus_container_restarts_total", name, int(detail.stdout.strip())))
    return samples


def render(samples):
    lines = [
        "# HELP yunanexus_container_memory_bytes Resident memory of a container.",
        "# TYPE yunanexus_container_memory_bytes gauge",
        "# HELP yunanexus_container_memory_limit_bytes Memory limit of a container.",
        "# TYPE yunanexus_container_memory_limit_bytes gauge",
        "# HELP yunanexus_container_memory_ratio Memory used divided by the limit.",
        "# TYPE yunanexus_container_memory_ratio gauge",
        "# HELP yunanexus_container_cpu_percent CPU usage percentage from docker stats.",
        "# TYPE yunanexus_container_cpu_percent gauge",
        "# HELP yunanexus_container_restarts_total Container restart counter.",
        "# TYPE yunanexus_container_restarts_total counter",
        "# HELP yunanexus_container_metrics_scrape_timestamp_seconds When this file was written.",
        "# TYPE yunanexus_container_metrics_scrape_timestamp_seconds gauge",
    ]
    for metric, name, value in samples:
        lines.append('%s{name="%s"} %s' % (metric, name, value))
    lines.append("yunanexus_container_metrics_scrape_timestamp_seconds %d" % int(datetime.now().timestamp()))
    return "\n".join(lines) + "\n"


def main():
    TEXTFILE_DIR.mkdir(parents=True, exist_ok=True)
    samples = collect() + collect_restarts()
    if not samples:
        raise RuntimeError("no container metrics collected")
    pending = OUTPUT.with_suffix(".tmp")
    pending.write_text(render(samples))
    os.chmod(pending, 0o644)
    pending.replace(OUTPUT)
    log.info("wrote %d samples for %d containers",
             len(samples), len({name for _, name, _ in samples}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
