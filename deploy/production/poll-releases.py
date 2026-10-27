#!/usr/bin/env python3
"""Read-only GitHub release polling: no server credential is stored in GitHub.

Enterprise additions (R2):

* transient network failures are retried with backoff instead of burning a cycle;
* every failure is logged with its full message and traceback (the original only
  recorded the exception class, e.g. ``ValueError:``);
* failed revisions back off exponentially and are eventually blocked, so a broken
  release can no longer restart production containers every five minutes;
* ``runtime/cd-state.json`` records attempts/next-attempt/blocked state and can be
  inspected (``--status``) or cleared (``--clear-block``) without guessing.
"""

import argparse
import hashlib
import json
import logging
import logging.handlers
import os
import re
import socket
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
STATE_PATH = R / "runtime" / "cd-state.json"
MAX_ATTEMPTS = int(os.environ.get("YUNANEXUS_CD_MAX_ATTEMPTS", "5"))
BACKOFF_BASE = int(os.environ.get("YUNANEXUS_CD_BACKOFF_SECONDS", "300"))
BACKOFF_CAP = int(os.environ.get("YUNANEXUS_CD_BACKOFF_CAP_SECONDS", "3600"))
DEPLOY_TIMEOUT = int(os.environ.get("YUNANEXUS_CD_DEPLOY_TIMEOUT", "3600"))
POLL_INTERVAL = int(os.environ.get("YUNANEXUS_CD_POLL_SECONDS", "300"))
RETRY_ATTEMPTS = int(os.environ.get("YUNANEXUS_CD_NETWORK_RETRIES", "3"))
MAX_ARTIFACT_BYTES = 1200 * 1024 ** 2
REPOS = [("backend", "YunaNexus"), ("frontend", "YunaNexusClient")]

log = logging.getLogger("yunanexus-cd")
log.setLevel(logging.INFO)
log.propagate = False


def configure_logging():
    if log.handlers:
        return
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    stream = logging.StreamHandler(sys.stdout)
    stream.setFormatter(formatter)
    log.addHandler(stream)
    if (R / "logs").is_dir():
        handler = logging.handlers.RotatingFileHandler(
            R / "logs" / "cd.log", maxBytes=2 * 1024 ** 2, backupCount=3
        )
        handler.setFormatter(formatter)
        log.addHandler(handler)


# --------------------------------------------------------------------------- state


def load_state():
    try:
        data = json.loads(STATE_PATH.read_text())
        if isinstance(data, dict):
            return data
    except FileNotFoundError:
        return {}
    except (ValueError, OSError) as error:
        log.error("cd-state.json unreadable (%s); starting from scratch", error)
    return {}


def save_state(state):
    try:
        STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        pending = STATE_PATH.with_suffix(".tmp")
        pending.write_text(json.dumps(state, indent=2, sort_keys=True))
        pending.replace(STATE_PATH)
    except OSError as error:
        log.error("cannot persist cd-state.json: %s", error)


def record_failure(kind, revision, error):
    state = load_state()
    entry = state.get(kind) or {}
    if entry.get("revision") != revision:
        entry = {"revision": revision, "attempts": 0, "firstFailure": time.time()}
    entry["attempts"] = int(entry.get("attempts", 0)) + 1
    entry["lastError"] = "%s: %s" % (type(error).__name__, str(error)[:500])
    entry["lastAttempt"] = time.time()
    delay = min(BACKOFF_BASE * (2 ** max(entry["attempts"] - 1, 0)), BACKOFF_CAP)
    entry["nextAttemptAt"] = time.time() + delay
    entry["blocked"] = entry["attempts"] >= MAX_ATTEMPTS
    state[kind] = entry
    save_state(state)
    if entry["blocked"]:
        log.critical(
            "%s release %s blocked after %d attempts; fix forward and run "
            "`poll-releases.py --clear-block --kind %s` to resume",
            kind, revision, entry["attempts"], kind,
        )
    else:
        log.warning(
            "%s release %s will retry in %ds (%d/%d attempts)",
            kind, revision, delay, entry["attempts"], MAX_ATTEMPTS,
        )


def record_success(kind):
    state = load_state()
    if state.pop(kind, None) is not None:
        save_state(state)


# ------------------------------------------------------------------------ network


def read(url, timeout=90):
    request = urllib.request.Request(
        url, headers={"User-Agent": "YunaNexus-CD", "Accept": "application/vnd.github+json"}
    )
    return urllib.request.urlopen(request, timeout=timeout)


def read_with_retry(url, timeout=90):
    """GitHub occasionally drops a connection mid-handshake; retry briefly."""
    delay = 5
    for attempt in range(1, RETRY_ATTEMPTS + 1):
        try:
            return read(url, timeout=timeout)
        except urllib.error.HTTPError:
            raise  # 4xx/5xx are not transient enough to blind-retry
        except (urllib.error.URLError, socket.timeout, ConnectionError, OSError) as error:
            if attempt == RETRY_ATTEMPTS:
                raise
            log.warning(
                "network error on attempt %d/%d: %s: %s; retrying in %ds",
                attempt, RETRY_ATTEMPTS, type(error).__name__, error, delay,
            )
            time.sleep(delay)
            delay *= 2


# ---------------------------------------------------------------------- releases


def latest_release(kind, repo):
    """Return the newest published deploy-<kind>-<sha> release, or None."""
    with read_with_retry(
        "https://api.github.com/repos/Nanoic39/" + repo + "/releases?per_page=10"
    ) as response:
        releases = json.load(response)
    candidates = [
        release for release in releases
        if not release["draft"]
        and re.fullmatch("deploy-" + kind + "-[a-f0-9]{40}", release["tag_name"])
    ]
    if not candidates:
        return None
    release = max(candidates, key=lambda item: item["published_at"])
    return {"revision": release["tag_name"].rsplit("-", 1)[1], "release": release}


def resolve_checksum(assets, asset_name):
    """Prefer the API digest, fall back to the published SHA256SUMS asset."""
    asset = assets[asset_name]
    if asset["size"] > MAX_ARTIFACT_BYTES:
        raise ValueError("Compressed artifact too large: %d bytes" % asset["size"])
    metadata = asset.get("digest", "")
    expected = metadata.removeprefix("sha256:") if metadata.startswith("sha256:") else None
    if not expected or not re.fullmatch("[a-f0-9]{64}", expected):
        with read_with_retry(assets["SHA256SUMS"]["browser_download_url"]) as response:
            checks = response.read(4096).decode()
        expected = next(
            (line.split()[0] for line in checks.splitlines()
             if line.split() and line.split()[-1] == asset_name),
            None,
        )
    if not expected or not re.fullmatch("[a-f0-9]{64}", expected):
        raise ValueError("Missing checksum for %s" % asset_name)
    return asset, expected


def fetch_archive(kind, revision, asset, asset_name, expected_sha):
    """Download to cache, verifying size and digest; reuse a valid cached copy."""
    suffix = ".tar.xz" if asset_name.endswith(".xz") else ".tar.gz"
    path = R / "cache" / (kind + "-" + revision + suffix)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_file() and path.stat().st_size == asset["size"]:
        with path.open("rb") as source:
            if hashlib.file_digest(source, "sha256").hexdigest() == expected_sha:
                return path
    partial = path.with_suffix(path.suffix + ".part")
    digest = hashlib.sha256()
    log.info("downloading %s (%.1f MiB)", asset_name, asset["size"] / 1024 ** 2)
    with read_with_retry(asset["browser_download_url"]) as response, partial.open("wb") as output:
        size = 0
        while chunk := response.read(1024 * 1024):
            size += len(chunk)
            if size > MAX_ARTIFACT_BYTES:
                partial.unlink(missing_ok=True)
                raise ValueError("Download exceeds size limit")
            output.write(chunk)
            digest.update(chunk)
    if digest.hexdigest() != expected_sha:
        partial.unlink(missing_ok=True)
        raise ValueError("Checksum mismatch: expected %s got %s" % (expected_sha, digest.hexdigest()))
    partial.replace(path)
    return path


def install(kind, revision, path):
    """Run deploy-release.py, surfacing its tail output on failure."""
    try:
        result = subprocess.run(
            [sys.executable, str(R / "ops/deploy-release.py"), kind, str(path), revision],
            capture_output=True, text=True, timeout=DEPLOY_TIMEOUT,
        )
    except subprocess.TimeoutExpired as error:
        raise RuntimeError("deploy-release.py timed out after %ds" % DEPLOY_TIMEOUT) from error
    if result.returncode != 0:
        details = ((result.stdout or "") + (result.stderr or "")).strip().splitlines()
        raise RuntimeError(
            "deploy-release.py exited %d: %s" % (result.returncode, " | ".join(details[-6:]))
        )


def check(kind, repo, force=False):
    """Deploy the newest validated release when it differs from production."""
    found = latest_release(kind, repo)
    if not found:
        return False
    revision = found["revision"]
    release = found["release"]

    deployed = R / "runtime" / (kind + "-release.json")
    if deployed.exists() and json.loads(deployed.read_text()).get("revision") == revision:
        return False

    entry = load_state().get(kind) or {}
    if entry.get("revision") == revision and not force:
        if entry.get("blocked"):
            log.info("%s %s is blocked after repeated failures; skipping", kind, revision)
            return False
        remaining = float(entry.get("nextAttemptAt", 0)) - time.time()
        if remaining > 0:
            log.info("%s %s backing off for another %.0fs", kind, revision, remaining)
            return False

    try:
        assets = {asset["name"]: asset for asset in release["assets"]}
        asset_name = kind + (".tar.xz" if kind + ".tar.xz" in assets else ".tar.gz")
        if asset_name not in assets or "SHA256SUMS" not in assets:
            raise ValueError(
                "Incomplete release %s: assets=%s" % (release["tag_name"], sorted(assets))
            )
        asset, expected_sha = resolve_checksum(assets, asset_name)
        path = fetch_archive(kind, revision, asset, asset_name, expected_sha)
        log.info("deploying %s %s", kind, revision)
        install(kind, revision, path)
        path.unlink(missing_ok=True)
    except Exception as error:
        record_failure(kind, revision, error)
        raise
    record_success(kind)
    log.info("%s deployment complete %s", kind, revision)
    return True


def cycle(selected=None, force=False):
    for kind, repo in REPOS:
        if selected and kind != selected:
            continue
        try:
            check(kind, repo, force=force)
        except Exception as error:
            log.error("%s check failed: %s: %s", kind, type(error).__name__, error)
            log.error("traceback:\n%s", traceback.format_exc())


# --------------------------------------------------------------------------- cli


def show_status():
    state = load_state()
    print(json.dumps(state, indent=2, sort_keys=True) if state else "no recorded failures")
    for kind, _ in REPOS:
        path = R / "runtime" / (kind + "-release.json")
        if path.exists():
            print(kind, "deployed:", path.read_text())


def clear_block(kind=None):
    state = load_state()
    for key in [kind] if kind else list(state):
        state.pop(key, None)
    save_state(state)
    print("cleared", kind or "all")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Poll GitHub releases and deploy validated packages")
    parser.add_argument("--once", action="store_true", help="run a single cycle")
    parser.add_argument("--kind", choices=[kind for kind, _ in REPOS])
    parser.add_argument("--status", action="store_true", help="print deployment/attempt state")
    parser.add_argument("--clear-block", action="store_true", help="forget failure state")
    parser.add_argument("--force", action="store_true", help="ignore backoff/block for this run")
    args = parser.parse_args(argv)
    configure_logging()
    if args.status:
        show_status()
        return
    if args.clear_block:
        clear_block(args.kind)
        return
    while True:
        cycle(args.kind, force=args.force)
        if args.once:
            break
        time.sleep(POLL_INTERVAL)


if __name__ == "__main__":
    main()
