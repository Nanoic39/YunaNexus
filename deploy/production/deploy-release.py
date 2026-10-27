#!/usr/bin/env python3
"""Install validated release archives. All writes are bounded to the data disk.

Safety invariants kept from the original implementation:

* the target must live on the mounted data disk and keep a 4 GiB reserve;
* release directories are immutable and named by a 40-char commit id;
* backend archives may only carry the nine service jars, frontend archives only
  an ``admin``/``user`` tree with an ``index.html`` each;
* ``current`` only moves after every recreated service reports ``UP``;
* a failed activation restores the previous release and records the reason.

Enterprise additions (R2): staged logging with timings, health checks that fail
fast on a dead container and quote the actuator body, a post-activation business
smoke probe, a memory pre-flight guard, ``--dry-run`` and a state file recording
what was deployed and how long each stage took.
"""

import argparse
import fcntl
import json
import logging
import logging.handlers
import os
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
NAMES = ["meta", "auth", "gateway", "user", "permission", "file", "notice", "audit", "analytics"]
PORTS = dict(zip(NAMES, [8001, 8101, 8081, 8201, 8301, 8401, 8501, 8601, 8701]))
# Dependency order: meta first (id segments), gateway last (public entry).
START_ORDER = ["meta", "permission", "user", "file", "notice", "auth", "audit", "analytics", "gateway"]

HEALTH_ATTEMPTS = int(os.environ.get("YUNANEXUS_HEALTH_ATTEMPTS", "90"))
HEALTH_INTERVAL = float(os.environ.get("YUNANEXUS_HEALTH_INTERVAL", "2"))
MEMORY_WARN_BYTES = int(os.environ.get("YUNANEXUS_DEPLOY_MEMORY_WARN_MB", "768")) * 1024 ** 2
MEMORY_BLOCK_BYTES = int(os.environ.get("YUNANEXUS_DEPLOY_MEMORY_BLOCK_MB", "256")) * 1024 ** 2
MIN_FREE_BYTES = 4 * 1024 ** 3
MAX_ARCHIVE_BYTES = 1200 * 1024 ** 2
MAX_EXPANDED_BYTES = 2 * 1024 ** 3
SMOKE_URLS = [
    ("user entry", "http://127.0.0.1:3000/"),
    ("business status", "http://127.0.0.1:3000/api/v1/status"),
]

os.environ.update(
    DOCKER_HOST="unix://" + str(R / "runtime/docker.sock"),
    DOCKER_CONFIG=str(R / "runtime/docker-client"),
    HOME=str(R / "runtime/home"),
    TMPDIR=str(R / "tmp"),
)

log = logging.getLogger("yunanexus-deploy")
log.setLevel(logging.INFO)
log.propagate = False


def configure_logging():
    """Log to stdout (for the poller) and to a rotating file when available."""
    if log.handlers:
        return
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    stream = logging.StreamHandler(sys.stdout)
    stream.setFormatter(formatter)
    log.addHandler(stream)
    if (R / "logs").is_dir():
        handler = logging.handlers.RotatingFileHandler(
            R / "logs" / "deploy.log", maxBytes=2 * 1024 ** 2, backupCount=3
        )
        handler.setFormatter(formatter)
        log.addHandler(handler)


def compose(*args):
    try:
        return subprocess.check_output(
            [str(R / "ops/compose.sh"), *args], text=True, stderr=subprocess.STDOUT
        ).strip()
    except subprocess.CalledProcessError as error:
        detail = (error.output or "").strip().splitlines()[-3:]
        raise RuntimeError("compose %s failed: %s" % (" ".join(args), " | ".join(detail))) from error


def switch(parent, name, target):
    pending = parent / (name + ".next")
    pending.unlink(missing_ok=True)
    pending.symlink_to(target)
    os.replace(pending, parent / name)


def container_id(service):
    return compose("ps", "-q", service)


def container_diagnostics(service):
    """Best-effort snapshot that makes a health failure actionable."""
    try:
        cid = container_id(service)
        if not cid:
            return "container not created"
        state = subprocess.check_output(
            ["docker", "inspect", "--format",
             "state={{.State.Status}} exit={{.State.ExitCode}} restarts={{.RestartCount}}", cid],
            text=True, timeout=15,
        ).strip()
        tail = subprocess.run(
            ["docker", "logs", "--tail", "20", cid],
            capture_output=True, text=True, timeout=30,
        )
        output = ((tail.stderr or "") + (tail.stdout or "")).strip()
        return state + "\n----- last logs -----\n" + output[-2000:]
    except Exception as error:  # diagnostics must never mask the real failure
        return "diagnostics unavailable: %s: %s" % (type(error).__name__, error)


def healthy(service, attempts=None, interval=None):
    """Poll the management actuator and raise an actionable error on failure."""
    attempts = HEALTH_ATTEMPTS if attempts is None else attempts
    interval = HEALTH_INTERVAL if interval is None else interval
    started = time.time()
    last = "not attempted"
    for attempt in range(1, attempts + 1):
        try:
            cid = container_id(service)
            if not cid:
                last = "container not created yet"
            else:
                status = subprocess.check_output(
                    ["docker", "inspect", "--format", "{{.State.Status}} restarts={{.RestartCount}}", cid],
                    text=True, timeout=15,
                ).split()
                state = status[0] if status else "unknown"
                restarts = int(status[1].split("=")[1]) if len(status) > 1 else 0
                # A force-recreated container starts with restart count 0, so any restart
                # means this container already crashed: fail immediately instead of
                # waiting out the whole window on a crash loop (R2 drill finding).
                if state in ("exited", "dead") or restarts > 0 or state == "restarting":
                    raise RuntimeError(
                        "%s container is %s (restarts=%d)\n%s"
                        % (service, state, restarts, container_diagnostics(service))
                    )
                ip = subprocess.check_output(
                    ["docker", "inspect", "--format",
                     "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", cid],
                    text=True, timeout=15,
                ).strip()
                if ip:
                    url = "http://%s:%d/actuator/health" % (ip, PORTS[service])
                    try:
                        with urllib.request.urlopen(url, timeout=3) as response:
                            body = response.read(4096).decode("utf-8", "replace")
                        if json.loads(body).get("status") == "UP":
                            log.info("health %s UP in %.1fs", service, time.time() - started)
                            return
                        last = "actuator status not UP: " + body[:200]
                    except Exception as error:
                        last = "%s: %s" % (type(error).__name__, str(error)[:160])
                else:
                    last = "container has no address yet"
        except RuntimeError:
            raise
        except Exception as error:
            last = "%s: %s" % (type(error).__name__, str(error)[:160])
        if attempt % 15 == 0:
            log.info("waiting for %s (%d/%d): %s", service, attempt, attempts, last)
        time.sleep(interval)
    raise RuntimeError(
        "%s did not become healthy after %.0fs (last: %s)\n%s"
        % (service, time.time() - started, last, container_diagnostics(service))
    )


def smoke():
    """Verify the public entry and one business endpoint after activation."""
    if os.environ.get("YUNANEXUS_SKIP_SMOKE") == "1":
        log.warning("smoke probe skipped by YUNANEXUS_SKIP_SMOKE")
        return
    for label, url in SMOKE_URLS:
        try:
            with urllib.request.urlopen(url, timeout=15) as response:
                status = response.status
                body = response.read(4096).decode("utf-8", "replace")
        except Exception as error:
            raise RuntimeError("%s smoke failed: %s: %s" % (label, type(error).__name__, error))
        if status != 200:
            raise RuntimeError("%s smoke failed: HTTP %s" % (label, status))
        if label == "business status" and '"code":200' not in body.replace(" ", ""):
            raise RuntimeError("%s smoke failed: %s" % (label, body[:200]))
    log.info("smoke probe passed")


def memory_available_bytes():
    try:
        with open("/proc/meminfo", encoding="ascii") as handle:
            for line in handle:
                if line.startswith("MemAvailable:"):
                    return int(line.split()[1]) * 1024
    except OSError:
        return None
    return None


def migration_state(timeout=180):
    """Ask migrate.py what is pending; None when the tool cannot answer.

    A backend release can depend on a schema change that the auto-deployer does not
    apply, so the code must not land on a database that is behind. A broken tool
    reports None and only warns: an emergency rollback must never be blocked by it.
    """
    script = R / "ops/migrate.py"
    if not script.exists():
        return None
    try:
        result = subprocess.run(
            [sys.executable, str(script), "--status", "--json"],
            capture_output=True, text=True, timeout=timeout, cwd=str(R),
        )
    except (OSError, subprocess.SubprocessError) as error:
        log.warning("migration status unavailable: %s", error)
        return None
    if result.returncode not in (0, 1):
        log.warning("migration status failed (exit %s): %s",
                    result.returncode, (result.stderr or result.stdout).strip()[:300])
        return None
    try:
        return json.loads(result.stdout)
    except ValueError:
        log.warning("migration status returned unparsable output")
        return None


def blocking_migrations(state):
    """Entries that must stop a backend deploy: nobody ships code onto a stale schema."""
    if not state:
        return []
    return [entry for entry in state if entry.get("state") in ("pending", "drift")]


def preflight(kind, revision, force=False):
    """Refuse obviously unsafe activations before touching production."""
    if R == Path("/YunaNexus/platform") and not os.path.ismount("/YunaNexus"):
        raise RuntimeError("Data disk is not mounted")
    free = shutil.disk_usage(R).free
    if free < MIN_FREE_BYTES:
        raise RuntimeError(
            "Less than 4 GiB disk reserve (%.1f GiB free); deployment refused" % (free / 1024 ** 3)
        )
    available = memory_available_bytes()
    if available is not None:
        if available < MEMORY_BLOCK_BYTES and not force:
            raise RuntimeError(
                "Only %.0f MiB memory available; recreating nine JVMs would thrash the host "
                "(use --force to override)" % (available / 1024 ** 2)
            )
        if available < MEMORY_WARN_BYTES:
            log.warning(
                "low memory pre-flight: %.0f MiB available, activation may be slow",
                available / 1024 ** 2,
            )
    blockers = blocking_migrations(migration_state()) if kind == "backend" else []
    if blockers:
        detail = "; ".join(
            "%s/%s %s%s" % (entry["service"], entry["schema"], entry["state"],
                            (" v" + ",".join(str(v) for v in entry.get("drift") or entry.get("pending") or [])))
            for entry in blockers
        )
        message = ("database schema is behind the release: %s (apply with "
                   "`ops/migrate.py --apply`, inspect with `--status`)" % detail)
        if force:
            log.warning("%s - continuing because --force was given", message)
        else:
            raise RuntimeError(message)
    log.info("preflight ok: kind=%s revision=%s free=%.1fGiB", kind, revision, free / 1024 ** 3)


def validate_archive(kind, archive, target):
    """Extract with the original bounding rules; return the extracted file count."""
    seen = set()
    total = 0
    with tarfile.open(archive, "r:*") as tar:
        members = tar.getmembers()
        if len(members) > 10000:
            raise ValueError("Too many archive entries")
        for member in members:
            path = Path(member.name)
            if not member.isfile() or path.is_absolute() or ".." in path.parts or member.name in seen:
                raise ValueError("Unsafe archive entry: %s" % member.name)
            if kind == "backend" and member.name not in ["yunanexus-" + n + ".jar" for n in NAMES]:
                raise ValueError("Unexpected backend file: %s" % member.name)
            if kind == "frontend" and (
                len(path.parts) < 2
                or path.parts[0] not in ["admin", "user"]
                or any(part.startswith(".") for part in path.parts)
            ):
                raise ValueError("Unexpected frontend file: %s" % member.name)
            total += member.size
            if total > MAX_EXPANDED_BYTES:
                raise ValueError("Archive exceeds expanded size limit")
            seen.add(member.name)
            destination = target / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            with tar.extractfile(member) as source, destination.open("xb") as sink:
                shutil.copyfileobj(source, sink)
            destination.chmod(0o644)
    if kind == "backend" and len(seen) != len(NAMES):
        raise ValueError("Nine service jars required, got %d" % len(seen))
    if kind == "frontend" and not all(
        (target / app / "index.html").is_file() for app in ["admin", "user"]
    ):
        raise ValueError("Both frontend applications are required")
    return len(seen)


def retain(parent, revision, previous):
    """Keep current and previous only; never touch data, secrets or unknown names."""
    keep = {revision, previous}
    for older in parent.iterdir():
        if older.name in keep or older.is_symlink() or not older.is_dir():
            continue
        if re.fullmatch("[a-f0-9]{40}", older.name):
            shutil.rmtree(older, ignore_errors=True)


def activate(kind, revision, services=None):
    """Recreate services (or the edge) in dependency order, verifying each one."""
    timings = {}
    order = START_ORDER if kind == "backend" else ["edge"]
    if services:
        order = [service for service in order if service in services]
    for service in order:
        started = time.time()
        compose("up", "-d", "--no-deps", "--force-recreate", service)
        if kind == "backend":
            healthy(service)
        timings[service] = round(time.time() - started, 1)
    return timings


def rollback(kind, previous):
    """Restore the previous release and verify it, so a bad deploy never lingers."""
    switch(R / "releases" / kind, "current", previous)
    log.warning("rolled back %s to %s; recreating services", kind, previous)
    try:
        activate(kind, previous)
    except Exception as error:
        log.error("ROLLBACK HEALTH CHECK FAILED: %s", error)
        raise
    log.info("rollback to %s verified healthy", previous)


def deploy(kind, archive, revision, dry_run=False, force=False):
    started = time.time()
    if not re.fullmatch("[a-f0-9]{40}", revision):
        raise ValueError("Invalid revision")
    if kind not in ("backend", "frontend"):
        raise ValueError("Invalid release kind")
    archive = Path(archive)
    if not archive.is_file():
        raise ValueError("Archive not found: %s" % archive)
    if archive.stat().st_size > MAX_ARCHIVE_BYTES:
        raise ValueError("Compressed artifact too large")

    configure_logging()
    preflight(kind, revision, force=force)
    log.info(
        "deploy %s %s from %s (%.1f MiB)",
        kind, revision, archive.name, archive.stat().st_size / 1024 ** 2,
    )

    parent = R / "releases" / kind
    target = parent / revision
    if target.exists():
        raise RuntimeError("Release already installed; do not overwrite immutable release")
    if dry_run:
        staging = R / "tmp" / ("dry-run-" + revision)
        shutil.rmtree(staging, ignore_errors=True)
        staging.mkdir(parents=True)
        try:
            count = validate_archive(kind, archive, staging)
        finally:
            shutil.rmtree(staging, ignore_errors=True)
        log.info("dry run ok: %s archive valid, %d files, no changes applied", kind, count)
        return {"kind": kind, "revision": revision, "dry_run": True, "files": count}

    target.mkdir(mode=0o755)
    try:
        count = validate_archive(kind, archive, target)
    except Exception:
        shutil.rmtree(target, ignore_errors=True)
        raise
    log.info("extracted %d files into %s", count, target)

    previous = os.readlink(parent / "current") if (parent / "current").is_symlink() else None
    switch(parent, "current", revision)
    try:
        timings = activate(kind, revision)
        smoke()
    except Exception as error:
        log.error("activation of %s failed: %s", revision, error)
        if previous:
            try:
                rollback(kind, previous)
            except Exception as rollback_error:
                log.critical("rollback failed, manual action required: %s", rollback_error)
            else:
                # Artifacts are reproducible from GitHub; keep the disk clean after a failure.
                shutil.rmtree(target, ignore_errors=True)
                log.info("removed failed release directory %s", target)
        else:
            log.critical("no previous release to roll back to; running %s", revision)
        raise
    if previous:
        switch(parent, "previous", previous)
    duration = round(time.time() - started, 1)
    state = {
        "revision": revision,
        "time": time.time(),
        "duration_seconds": duration,
        "files": count,
        "services": timings,
    }
    (R / "runtime" / (kind + "-release.json")).write_text(json.dumps(state))
    retain(parent, revision, previous)
    log.info("%s deployed %s in %.1fs", kind, revision, duration)
    return state


def restart(kind="backend", service=None, dry_run=False):
    """Recreate the running release without changing versions (R1 maintenance)."""
    configure_logging()
    parent = R / "releases" / kind
    current = parent / "current"
    if not current.is_symlink():
        raise RuntimeError("No current %s release to restart" % kind)
    revision = os.readlink(current)
    if not re.fullmatch("[a-f0-9]{40}", revision):
        raise RuntimeError("Unexpected current revision: %s" % revision)
    if service and kind == "backend" and service not in NAMES:
        raise ValueError("Unknown service: %s" % service)
    preflight(kind, revision)
    log.info("restart %s release %s%s", kind, revision,
             " (service=%s)" % service if service else "")
    if dry_run:
        log.info("dry run: would recreate %s", service or "all services")
        return {"kind": kind, "revision": revision, "dry_run": True, "service": service}
    started = time.time()
    timings = activate(kind, revision, services=[service] if service else None)
    smoke()
    duration = round(time.time() - started, 1)
    log.info("restart finished in %.1fs: %s", duration, timings)
    return {"kind": kind, "revision": revision, "duration_seconds": duration, "services": timings}


def restart_main(argv):
    parser = argparse.ArgumentParser(description="Recreate the running release in place")
    parser.add_argument("--kind", choices=["backend", "frontend"], default="backend")
    parser.add_argument("--service", help="recreate a single backend service")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args(argv)
    return restart(args.kind, args.service, dry_run=args.dry_run)


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv and argv[0] == "restart":
        return restart_main(argv[1:])
    parser = argparse.ArgumentParser(description="Install a validated YunaNexus release archive")
    parser.add_argument("kind", choices=["backend", "frontend"])
    parser.add_argument("archive")
    parser.add_argument("revision")
    parser.add_argument("--dry-run", action="store_true", help="validate the archive only")
    parser.add_argument("--force", action="store_true", help="ignore the memory pre-flight guard")
    args = parser.parse_args(argv)
    configure_logging()
    lock_path = R / "runtime/deploy.lock"
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        return deploy(args.kind, args.archive, args.revision, dry_run=args.dry_run, force=args.force)


if __name__ == "__main__":
    try:
        main()
    except Exception as failure:
        log.error("deploy failed: %s: %s", type(failure).__name__, failure)
        sys.exit(1)
