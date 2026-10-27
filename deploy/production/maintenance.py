#!/usr/bin/env python3
"""Bound logs/caches and run the scheduled backup + restore drill.

Started by `start.sh` and runs forever. Schedule decisions come from the clock
plus a small state file, so a restart neither double-books a backup nor silently
skips one.
"""
import json
import logging
import logging.handlers
import os
import subprocess
import time
from datetime import datetime
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform")).resolve()
STATE_PATH = R / "runtime" / "maintenance-state.json"
LOG_MAX_BYTES = 10 * 1024 ** 2
BACKUP_HOUR, BACKUP_MINUTE = 3, 30
DRILL_HOUR, DRILL_MINUTE = 4, 30
DRILL_WEEKDAY = 6  # Sunday
PITR_HOUR, PITR_MINUTE = 5, 0
BINLOG_SHIP_INTERVAL = 3600
MIGRATION_INTERVAL = int(os.environ.get("YUNANEXUS_MIGRATION_INTERVAL", "3600"))
# Weekly: OSV advisories move slowly and each scan is a few hundred dependency lookups.
VULNSCAN_INTERVAL = int(os.environ.get("YUNANEXUS_VULNSCAN_INTERVAL", str(7 * 24 * 3600)))
METRICS_INTERVAL = 60
ALERT_INTERVAL = 120
CERTIFICATE_INTERVAL = 6 * 3600


def configure_logging():
    logger = logging.getLogger("maintenance")
    if logger.handlers:
        return logger
    logger.setLevel(logging.INFO)
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    # start.sh already redirects stdout into logs/maintenance.log, so one handler
    # is enough - a file handler on the same path would duplicate every line.
    stream = logging.StreamHandler()
    stream.setFormatter(formatter)
    logger.addHandler(stream)
    return logger


log = configure_logging()


def load_state():
    try:
        data = json.loads(STATE_PATH.read_text())
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_state(state):
    try:
        STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        pending = STATE_PATH.with_suffix(".tmp")
        pending.write_text(json.dumps(state, indent=2, sort_keys=True))
        pending.replace(STATE_PATH)
    except OSError as error:
        log.error("cannot persist maintenance state: %s", error)


RETRY_BACKOFF = 1800


def _cooled_down(now, state, key):
    last = float(state.get(key, 0) or 0)
    return time.time() - last >= RETRY_BACKOFF if last else True


def due_backup(now, state):
    """One backup per day, at or after 03:30 local time, with a failure backoff."""
    if state.get("last_backup") == now.strftime("%Y-%m-%d"):
        return False
    if (now.hour, now.minute) < (BACKUP_HOUR, BACKUP_MINUTE):
        return False
    return _cooled_down(now, state, "last_backup_attempt")


def due_drill(now, state, weekday=DRILL_WEEKDAY):
    """One restore drill per week, on Sunday at or after 04:30."""
    if now.weekday() != weekday:
        return False
    if state.get("last_drill") == now.strftime("%Y-%m-%d"):
        return False
    if (now.hour, now.minute) < (DRILL_HOUR, DRILL_MINUTE):
        return False
    return _cooled_down(now, state, "last_drill_attempt")


def due_pitr(now, state):
    """One point-in-time-recovery drill per month, first Sunday at or after 05:00."""
    if now.weekday() != DRILL_WEEKDAY or now.day > 7:
        return False
    if state.get("last_pitr_drill", "")[:7] == now.strftime("%Y-%m"):
        return False
    if (now.hour, now.minute) < (PITR_HOUR, PITR_MINUTE):
        return False
    return _cooled_down(now, state, "last_pitr_attempt")


def run(script, *args, timeout=3600):
    result = subprocess.run(
        ["python3", str(R / "ops" / script), *args],
        capture_output=True, text=True, timeout=timeout,
    )
    tail = ((result.stdout or "") + (result.stderr or "")).strip().splitlines()[-3:]
    if result.returncode != 0:
        log.error("%s failed (exit %d): %s", script, result.returncode, " | ".join(tail))
    return result.returncode == 0


DAEMONS = {"logship": "logship.py"}


def ensure_daemon(name):
    """Restart a long-running helper if its pid is gone.

    There is no systemd on this host, so start.sh launches the daemons at boot and
    this loop takes over if one dies while the machine stays up.
    """
    pidfile = R / "runtime" / ("%s.pid" % name)
    try:
        os.kill(int(pidfile.read_text().strip()), 0)
        return True
    except (OSError, ValueError):
        pass
    script = DAEMONS[name]
    if not (R / "ops" / script).is_file():
        return False
    log_path = R / "logs" / ("%s.log" % name)
    with log_path.open("ab") as sink:
        process = subprocess.Popen(
            ["python3", str(R / "ops" / script)],
            stdout=sink, stderr=sink, stdin=subprocess.DEVNULL,
            env=dict(os.environ, YUNANEXUS_PLATFORM_ROOT=str(R)),
        )
    pidfile.write_text(str(process.pid))
    log.warning("%s was not running; restarted as pid %d", name, process.pid)
    return True


def trim_logs():
    for path in (R / "logs").rglob("*"):
        if path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(R / "logs"):
            if path.stat().st_size > LOG_MAX_BYTES:
                # Truncate in place so running processes continue writing the same inode.
                with path.open("r+b") as output:
                    output.truncate(0)


def trim_cache():
    for path in (p for p in (R / "cache").iterdir() if p.name.endswith((".tar.gz", ".tar.xz", ".part"))):
        if path.is_file() and not path.is_symlink() and time.time() - path.stat().st_mtime > 86400:
            path.unlink()


def maintain():
    trim_logs()
    trim_cache()
    now = datetime.now()
    state = load_state()
    for name in DAEMONS:
        ensure_daemon(name)
    if time.time() - float(state.get("last_metrics", 0) or 0) >= METRICS_INTERVAL:
        state["last_metrics"] = time.time()
        save_state(state)
        run("container-metrics.py", timeout=120)
    if time.time() - float(state.get("last_alerts", 0) or 0) >= ALERT_INTERVAL:
        state["last_alerts"] = time.time()
        save_state(state)
        run("alerts.py", timeout=120)
    if time.time() - float(state.get("last_migration_metrics", 0) or 0) >= MIGRATION_INTERVAL:
        state["last_migration_metrics"] = time.time()
        save_state(state)
        run("migration-metrics.py", timeout=600)
    if time.time() - float(state.get("last_vulnscan", 0) or 0) >= VULNSCAN_INTERVAL:
        state["last_vulnscan"] = time.time()
        save_state(state)
        if not run("sbom.py", "--save", timeout=900):
            log.warning("dependency inventory failed; skipping the vulnerability scan")
        elif not run("vulnscan.py", timeout=1800):
            log.warning("vulnerability scan reported findings or failed; see runtime/vulnscan")
        # Image scan walks every container's package list; slow but weekly is enough.
        if not run("imagescan.py", timeout=2400):
            log.warning("image scan reported findings or failed; see runtime/imagescan")
    if due_backup(now, state):
        log.info("scheduled backup starting")
        state["last_backup_attempt"] = time.time()
        save_state(state)
        if run("backup.py"):
            state["last_backup"] = now.strftime("%Y-%m-%d")
            save_state(state)
            log.info("scheduled backup finished")
    if due_drill(now, state):
        log.info("weekly restore drill starting")
        state["last_drill_attempt"] = time.time()
        save_state(state)
        if run("backup.py", "--verify"):
            state["last_drill"] = now.strftime("%Y-%m-%d")
            save_state(state)
            log.info("weekly restore drill passed")
    if due_pitr(now, state):
        log.info("monthly PITR drill starting")
        state["last_pitr_attempt"] = time.time()
        save_state(state)
        if run("pitr-drill.py"):
            state["last_pitr_drill"] = now.strftime("%Y-%m-%d")
            save_state(state)
            log.info("monthly PITR drill passed")
    if time.time() - float(state.get("last_binlog_ship", 0) or 0) >= BINLOG_SHIP_INTERVAL:
        state["last_binlog_ship"] = time.time()
        save_state(state)
        if not run("backup.py", "--ship-binlogs"):
            log.warning("binlog shipping failed; will retry next cycle")
    if (R / "config/acme.enabled").is_file():
        last = float(state.get("last_certificate_check", 0))
        if time.time() - last >= CERTIFICATE_INTERVAL:
            subprocess.run(["bash", str(R / "ops/renew-certificate.sh")],
                           timeout=180, capture_output=True)
            state["last_certificate_check"] = time.time()
            save_state(state)


if __name__ == "__main__":
    log.info("maintenance loop started")
    while True:
        try:
            maintain()
        except Exception as error:  # keep the loop alive; the log carries the detail
            log.error("maintenance cycle failed: %s: %s", type(error).__name__, error)
        time.sleep(60)
