#!/usr/bin/env python3
"""Export database migration state as Prometheus metrics.

A pending or drifted migration is invisible until someone runs the deployer, which
is exactly when it is most expensive to discover. This writes the same answer
`migrate.py --status` gives into the node_exporter textfile collector, so drift
turns into an alert hours before the next release instead of a failed deploy.
"""

import json
import logging
import os
import subprocess
import sys
import time
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
TEXTFILE = R / "runtime/node_exporter_textfile/migrations.prom"
TIMEOUT = int(os.environ.get("YUNANEXUS_MIGRATION_METRICS_TIMEOUT", "300"))

log = logging.getLogger("migration-metrics")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


def collect(timeout=TIMEOUT):
    """Run the migration tool and return (report, ok)."""
    try:
        result = subprocess.run(
            ["python3", str(R / "ops/migrate.py"), "--status", "--json"],
            capture_output=True, text=True, timeout=timeout, cwd=str(R),
        )
    except (OSError, subprocess.SubprocessError) as error:
        log.error("migration status failed to start: %s", error)
        return None, False
    if result.returncode not in (0, 1):
        detail = (result.stderr or result.stdout).strip().splitlines()[-2:]
        log.error("migration status exited %d: %s", result.returncode, " | ".join(detail))
        return None, False
    try:
        return json.loads(result.stdout), True
    except ValueError:
        log.error("migration status returned unparsable output")
        return None, False


def render(report, ok, now=None):
    now = time.time() if now is None else now
    lines = [
        "# HELP yunanexus_migration_pending Migrations defined but not applied.",
        "# TYPE yunanexus_migration_pending gauge",
        "# HELP yunanexus_migration_drift Applied migrations whose file has changed.",
        "# TYPE yunanexus_migration_drift gauge",
        "# HELP yunanexus_migration_check_success Whether the last check could read the state.",
        "# TYPE yunanexus_migration_check_success gauge",
        "# HELP yunanexus_migration_check_timestamp_seconds When the last check ran.",
        "# TYPE yunanexus_migration_check_timestamp_seconds gauge",
    ]
    for entry in report or []:
        schema = entry.get("schema")
        if not schema:
            continue
        labels = 'service="%s",schema="%s"' % (entry.get("service", ""), schema)
        lines.append("yunanexus_migration_pending{%s} %d" % (labels, len(entry.get("pending") or [])))
        lines.append("yunanexus_migration_drift{%s} %d" % (labels, len(entry.get("drift") or [])))
    lines.append("yunanexus_migration_check_success %d" % (1 if ok else 0))
    lines.append("yunanexus_migration_check_timestamp_seconds %d" % int(now))
    return "\n".join(lines) + "\n"


def write(text, target=None):
    target = Path(target or TEXTFILE)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".prom.tmp")
    temporary.write_text(text, encoding="utf-8")
    os.chmod(temporary, 0o644)
    temporary.replace(target)
    return target


def main():
    report, ok = collect()
    target = write(render(report, ok))
    pending = sum(len(entry.get("pending") or []) for entry in report or [])
    drift = sum(len(entry.get("drift") or []) for entry in report or [])
    log.info("migration metrics written to %s (pending=%d drift=%d ok=%s)",
             target, pending, drift, ok)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
