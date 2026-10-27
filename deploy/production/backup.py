#!/usr/bin/env python3
"""Consistent, verifiable backups for the YunaNexus platform (round 3).

The platform had no backup and no restore drill at all, which is the only failure
mode that cannot be undone. This script produces one self-describing backup
directory per run:

    backups/<YYYYmmdd-HHMMSS>/
        db/<schema>.sql.gz      logical dump per schema (restorable standalone)
        grants.sql              account/privilege snapshot (restore needs it)
        files.tar.gz            upload storage + configuration + ops SQL
        secrets.tar.gz          only when YUNANEXUS_BACKUP_SECRETS=1
        manifest.json           revision, table/row counts, sizes, digests
        SHA256SUMS              digest of every artifact above

`--verify` restores the newest (or a named) backup into scratch schemas on the
same server, compares table and row counts against production, then drops the
scratch schemas. That turns "we have backups" into "we proved a restore works".
"""

import argparse
import base64
import gzip
import hashlib
import json
import logging
import logging.handlers
import os
import shutil
import subprocess
import sys
import tarfile
import time
from datetime import datetime
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
BACKUPS = R / "backups"
MYSQL_CONTAINER = os.environ.get("YUNANEXUS_MYSQL_CONTAINER", "yunanexus-production-mysql-1")
DOCKER_HOST = os.environ.get(
    "YUNANEXUS_DOCKER_HOST", "unix://" + str(R / "runtime" / "docker.sock")
)
INCLUDE_SECRETS = os.environ.get("YUNANEXUS_BACKUP_SECRETS", "1") == "1"
PASSPHRASE_FILE = Path(os.environ.get(
    "YUNANEXUS_BACKUP_PASSPHRASE_FILE", str(Path.home() / ".yunanexus-backup.passphrase")
))
BINLOG_DIR = R / "data" / "mysql"
BINLOG_MAGIC = bytes([0xFE]) + b"bin"
MIRROR = Path(os.environ.get("YUNANEXUS_BACKUP_MIRROR", "/var/backups/yunanexus"))
KEEP_DAILY = int(os.environ.get("YUNANEXUS_BACKUP_KEEP_DAILY", "7"))
KEEP_MONTHLY = int(os.environ.get("YUNANEXUS_BACKUP_KEEP_MONTHLY", "4"))
MIN_FREE_BYTES = int(os.environ.get("YUNANEXUS_BACKUP_MIN_FREE_MB", "2048")) * 1024 ** 2
STAMP_FORMAT = "%Y%m%d-%H%M%S"

log = logging.getLogger("yunanexus-backup")
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
            R / "logs" / "backup.log", maxBytes=2 * 1024 ** 2, backupCount=3
        )
        handler.setFormatter(formatter)
        log.addHandler(handler)


# ------------------------------------------------------------------ mysql access


def docker_env():
    """The platform runs its own Docker engine, so never trust an inherited DOCKER_HOST."""
    return dict(os.environ, DOCKER_HOST=DOCKER_HOST)


def docker(*args, **kwargs):
    command = ["docker", "exec"]
    if kwargs.pop("stdin", False):
        command.append("-i")
    command.append(MYSQL_CONTAINER)
    return command + list(args)


def mysql_password():
    compose = R / "secrets" / "compose.env"
    try:
        for line in compose.read_text().splitlines():
            if line.startswith("MYSQL_ROOT_PASSWORD="):
                return line.split("=", 1)[1].strip()
    except OSError:
        pass
    raise RuntimeError("MYSQL_ROOT_PASSWORD not found in secrets/compose.env")


CREDENTIALS_PATH = "/dev/shm/.yunanexus-backup.cnf"


def ensure_credentials(password=None):
    """Place the client password in the container's tmpfs.

    Passing it as `docker exec -e MYSQL_PWD=...` would expose it in the host's
    process list, so it is streamed over stdin into a 0600 defaults file instead.
    """
    password = password or mysql_password()
    script = "umask 077; cat > %s" % CREDENTIALS_PATH
    result = subprocess.run(
        ["docker", "exec", "-i", MYSQL_CONTAINER, "sh", "-c", script],
        input="[client]\nuser=root\npassword=%s\n" % password,
        text=True, capture_output=True, timeout=60, env=docker_env(),
    )
    if result.returncode != 0:
        raise RuntimeError("cannot stage MySQL credentials: %s" % result.stderr.strip())
    return CREDENTIALS_PATH


def drop_credentials():
    subprocess.run(["docker", "exec", MYSQL_CONTAINER, "rm", "-f", CREDENTIALS_PATH],
                   capture_output=True, timeout=60, env=docker_env())


def mysql(sql, database=None):
    """Run a statement through the container's client."""
    command = docker("mysql", "--defaults-extra-file=" + CREDENTIALS_PATH,
                     "--batch", "--skip-column-names", "-e", sql)
    if database:
        command.insert(-2, database)
    result = subprocess.run(command, capture_output=True, text=True, timeout=300, env=docker_env())
    if result.returncode != 0:
        raise RuntimeError("mysql failed: %s" % (result.stderr.strip() or result.stdout.strip()))
    return result.stdout


def schemas():
    """Every production schema; drill scratch schemas are excluded by name."""
    return sorted(
        name for name in mysql("SHOW DATABASES LIKE 'yunanexus%'").split()
        if name and not name.startswith("yunanexus_verify_")
    )


def table_counts(database):
    """Exact row counts per table (information_schema.TABLE_ROWS is an estimate)."""
    tables = [line.strip() for line in mysql("SHOW TABLES", database).splitlines() if line.strip()]
    if not tables:
        return {}
    query = " UNION ALL ".join(
        "SELECT '%s' AS t, COUNT(*) AS c FROM `%s`" % (name, name) for name in tables
    )
    counts = {}
    for line in mysql(query, database).splitlines():
        if "\t" in line:
            name, value = line.split("\t", 1)
            counts[name] = int(value)
    return counts


# -------------------------------------------------------------------- artifacts


def write_stream(command, target, env=None, label=""):
    """Compress + hash a command's stdout in one pass; raise on failure."""
    with target.open("wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=6) as gz:
        proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                env=docker_env() if env is None else env)
        while chunk := proc.stdout.read(1024 * 1024):
            gz.write(chunk)
        error = proc.stderr.read().decode(errors="replace").strip()
        if proc.wait() != 0:
            target.unlink(missing_ok=True)
            raise RuntimeError("%s failed: %s" % (label or command[0], error or "no stderr"))
    if error and "Warning" not in error:
        log.warning("%s stderr: %s", label, error[:400])
    # Digest the compressed bytes on disk: a checksum over the plaintext stream
    # would never match the stored artifact during a restore drill.
    return {"artifact": target.name, "bytes": target.stat().st_size, "sha256": file_digest(target)}


def file_digest(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dump_schema(name, target):
    command = docker(
        "mysqldump", "--defaults-extra-file=" + CREDENTIALS_PATH,
        "--single-transaction", "--quick", "--routines", "--triggers", "--events",
        "--hex-blob", "--no-tablespaces", "--set-gtid-purged=OFF",
        "--source-data=2",  # embed binlog file/position so PITR can replay from here
        "--default-character-set=utf8mb4", name,
    )
    return write_stream(command, target, label="mysqldump " + name)


def dump_grants(target):
    rows = mysql(
        "SELECT CONCAT('SHOW GRANTS FOR ''', user, '''@''', host, ''';') "
        "FROM mysql.user WHERE user NOT IN ('root','mysql.sys','mysql.session','mysql.infoschema')"
    )
    statements = []
    for statement in rows.splitlines():
        statement = statement.strip()
        if not statement:
            continue
        grants = mysql(statement)
        statements.append("-- %s\n%s" % (statement, grants.strip().replace("\t", " ")))
    body = "\n".join(statements) or "-- no non-system accounts\n"
    with gzip.open(target, "wt", encoding="utf-8") as output:
        output.write(body)
    return {
        "artifact": target.name,
        "bytes": target.stat().st_size,
        "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
    }


def archive_files(target, sources):
    """Bundle upload storage and configuration without following odd file types."""
    def keep(info):
        # tarfile filters return the TarInfo to keep (or None); returning a bool
        # makes `tar.add` blow up on `tarinfo.isreg()`.
        if info.name.endswith((".sock", "docker.sock")):
            return None
        return info

    with tarfile.open(target, "w:gz") as tar:
        for source in sources:
            source = Path(source)
            if source.exists():
                tar.add(source, arcname=str(source.relative_to(R)), filter=keep)
    digest = hashlib.sha256()
    with target.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return {"artifact": target.name, "bytes": target.stat().st_size, "sha256": digest.hexdigest()}


# ---------------------------------------------------------------------- binlogs


def binlog_files():
    if not BINLOG_DIR.is_dir():
        return []
    return sorted(
        (p for p in BINLOG_DIR.iterdir()
         if p.is_file() and p.name.startswith("binlog.") and p.name.rsplit(".", 1)[-1].isdigit()),
        key=lambda p: int(p.name.rsplit(".", 1)[-1]),
    )


def binlog_status():
    """Current binlog file and position (MySQL 8.4 renamed SHOW MASTER STATUS)."""
    row = mysql("SHOW BINARY LOG STATUS").strip().split("\t")
    return {"file": row[0], "position": int(row[1])} if len(row) >= 2 else {}


def flush_binlogs():
    try:
        mysql("FLUSH BINARY LOGS")
        return True
    except RuntimeError as error:
        log.warning("cannot flush binary logs: %s", error)
        return False


def archive_binlogs(target):
    """Compress every closed binlog into the backup so a restore can replay them."""
    directory = target / "binlog"
    directory.mkdir(mode=0o700, exist_ok=True)
    status = binlog_status()
    open_name = status.get("file")
    artifacts = []
    for path in binlog_files():
        if path.name == open_name:
            continue  # still being written; the next run picks it up
        destination = directory / (path.name + ".gz")
        digest = hashlib.sha256()
        with path.open("rb") as source, destination.open("wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=6) as gz:
                while chunk := source.read(1024 * 1024):
                    gz.write(chunk)
                    digest.update(chunk)
        os.chmod(destination, 0o600)
        artifacts.append({
            "artifact": destination.name,
            "path": "binlog/" + destination.name,
            "bytes": destination.stat().st_size,
            "sha256": file_digest(destination),
            "source_bytes": path.stat().st_size,
        })
    if artifacts:
        (directory / "STATUS.txt").write_text(
            "archived_at=%s\ncurrent_file=%s\ncurrent_position=%s\n"
            % (datetime.now().astimezone().isoformat(timespec="seconds"),
               status.get("file"), status.get("position"))
        )
        os.chmod(directory / "STATUS.txt", 0o600)
    return artifacts


def ship_binlogs():
    """Copy closed binlogs to the second filesystem between full backups.

    Daily backups alone leave up to 24h of binlogs on the data disk only, so a
    disk failure would still lose the day. Shipping hourly cuts RPO to ~1 hour.
    """
    if MIRROR.exists() and same_device(MIRROR, BACKUPS.parent):
        return []
    directory = MIRROR / "binlog-live"
    ensure_credentials()
    try:
        flush_binlogs()
        status = binlog_status()
        open_name = status.get("file")
        directory.mkdir(parents=True, mode=0o700, exist_ok=True)
        os.chmod(directory, 0o700)
        shipped = []
        for path in binlog_files():
            if path.name == open_name:
                continue
            destination = directory / (path.name + ".gz")
            if destination.is_file() and destination.stat().st_size > 0:
                continue  # already shipped; binlog names are never reused
            with path.open("rb") as source, destination.open("wb") as raw:
                with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=6) as gz:
                    while chunk := source.read(1024 * 1024):
                        gz.write(chunk)
            os.chmod(destination, 0o600)
            shipped.append(destination.name)
        if shipped:
            log.info("shipped %d binlog file(s) to %s", len(shipped), directory)
        return shipped
    finally:
        drop_credentials()


def purge_binlogs(keep_from):
    """Drop binlogs older than the newest archived set once two copies exist."""
    try:
        mysql("PURGE BINARY LOGS TO '%s'" % keep_from)
        log.info("purged binary logs older than %s", keep_from)
    except RuntimeError as error:
        log.warning("binary log purge skipped: %s", error)


# -------------------------------------------------------------------- secrets


def passphrase_path(create=True):
    """Return the passphrase *file* path (openssl wants `-pass file:<path>`)."""
    if not PASSPHRASE_FILE.is_file():
        if not create:
            raise RuntimeError("passphrase file %s is missing" % PASSPHRASE_FILE)
        PASSPHRASE_FILE.parent.mkdir(parents=True, exist_ok=True)
        secret = base64.urlsafe_b64encode(os.urandom(32)).decode()
        PASSPHRASE_FILE.write_text(secret + "\n")
        os.chmod(PASSPHRASE_FILE, 0o600)
        log.warning(
            "generated a new backup passphrase at %s - copy it to a password manager, "
            "without it the encrypted secrets cannot be restored", PASSPHRASE_FILE,
        )
    return PASSPHRASE_FILE


def encrypt_file(source, passphrase_file):
    target = source.with_suffix(source.suffix + ".enc")
    result = subprocess.run(
        ["openssl", "enc", "-aes-256-cbc", "-pbkdf2", "-iter", "200000", "-salt",
         "-in", str(source), "-out", str(target), "-pass", "file:" + str(passphrase_file)],
        capture_output=True, text=True, timeout=600,
    )
    if result.returncode != 0:
        target.unlink(missing_ok=True)
        raise RuntimeError("cannot encrypt %s: %s" % (source.name, result.stderr.strip()))
    os.chmod(target, 0o600)
    source.unlink(missing_ok=True)
    return target


def decrypt_file(source, passphrase_file, destination):
    result = subprocess.run(
        ["openssl", "enc", "-d", "-aes-256-cbc", "-pbkdf2", "-iter", "200000",
         "-in", str(source), "-out", str(destination), "-pass", "file:" + str(passphrase_file)],
        capture_output=True, text=True, timeout=600,
    )
    if result.returncode != 0:
        destination.unlink(missing_ok=True)
        raise RuntimeError("cannot decrypt %s: %s" % (source.name, result.stderr.strip()))
    return destination


# -------------------------------------------------------------------- retention


def parse_stamp(name):
    try:
        return datetime.strptime(name, STAMP_FORMAT)
    except ValueError:
        return None


def select_prunable(stamps, keep_daily=KEEP_DAILY, keep_monthly=KEEP_MONTHLY):
    """Keep the newest `keep_daily` runs plus one run per month for the last `keep_monthly` months."""
    ordered = sorted(stamps, reverse=True)
    keep = set(ordered[:keep_daily])
    monthly = {}
    for stamp in ordered:
        monthly.setdefault((stamp.year, stamp.month), stamp)
    for key in sorted(monthly, reverse=True)[:keep_monthly]:
        keep.add(monthly[key])
    return [stamp for stamp in ordered if stamp not in keep]


def prune(keep_daily=KEEP_DAILY, keep_monthly=KEEP_MONTHLY):
    existing = {}
    if not BACKUPS.is_dir():
        return []
    for entry in BACKUPS.iterdir():
        if entry.is_dir() and (stamp := parse_stamp(entry.name)):
            existing[stamp] = entry
    removed = []
    for stamp in select_prunable(list(existing), keep_daily, keep_monthly):
        shutil.rmtree(existing[stamp], ignore_errors=True)
        removed.append(existing[stamp].name)
    return removed


# ----------------------------------------------------------------------- backup


def deployed_revision(kind):
    path = R / "runtime" / (kind + "-release.json")
    try:
        return json.loads(path.read_text()).get("revision")
    except (OSError, ValueError):
        return None


def disk_free_bytes():
    return shutil.disk_usage(BACKUPS.parent).free


def preflight():
    free = disk_free_bytes()
    if free < MIN_FREE_BYTES:
        raise RuntimeError("Only %.1f GiB free; refusing to write a backup" % (free / 1024 ** 3))
    if not (R / "runtime").is_dir():
        raise RuntimeError("Platform root %s does not look like a YunaNexus deployment" % R)


def backup(stamp=None):
    """Create one backup directory and return its manifest."""
    preflight()
    ensure_credentials()
    stamp = stamp or datetime.now()
    try:
        return _backup_locked(stamp, time.time())
    except Exception:
        # Never leave a half-written backup that looks restorable.
        shutil.rmtree(BACKUPS / stamp.strftime(STAMP_FORMAT), ignore_errors=True)
        raise
    finally:
        drop_credentials()


def _backup_locked(stamp, started):
    target = BACKUPS / stamp.strftime(STAMP_FORMAT)
    if target.exists():
        raise RuntimeError("Backup %s already exists" % target.name)
    (target / "db").mkdir(parents=True, mode=0o700)
    os.chmod(target, 0o700)

    names = schemas()
    if not names:
        raise RuntimeError("No yunanexus_* schema found; refusing to write an empty backup")
    log.info("backing up %d schemas: %s", len(names), ", ".join(names))

    # Close the current binlog first so everything after the snapshot lands in a
    # file that this backup will also archive.
    binlogging = flush_binlogs()
    snapshot = binlog_status() if binlogging else {}

    artifacts = []
    database_meta = {}
    for name in names:
        artifact = dump_schema(name, target / "db" / (name + ".sql.gz"))
        counts = table_counts(name)
        database_meta[name] = {
            "dump": dict(artifact, path="db/" + artifact["artifact"]),
            "tables": len(counts),
            "rows": sum(counts.values()),
            "counts": counts,
        }
        artifacts.append(dict(artifact, path="db/" + artifact["artifact"]))
        log.info("  %s: %d tables, %d rows, %.1f KiB", name, len(counts),
                 sum(counts.values()), artifact["bytes"] / 1024)

    grants = dump_grants(target / "grants.sql.gz")
    artifacts.append(dict(grants, path=grants["artifact"]))

    files = archive_files(target / "files.tar.gz", [R / "config", R / "ops", R / "data" / "file"])
    artifacts.append(dict(files, path=files["artifact"]))

    if INCLUDE_SECRETS:
        secrets = archive_files(target / "secrets.tar.gz", [R / "secrets"])
        encrypted = encrypt_file(target / "secrets.tar.gz", passphrase_path())
        artifacts.append({
            "artifact": encrypted.name,
            "path": encrypted.name,
            "bytes": encrypted.stat().st_size,
            "sha256": file_digest(encrypted),
        })

    binlogs = []
    if binlogging:
        flush_binlogs()
        binlogs = archive_binlogs(target)
        artifacts.extend(binlogs)

    uploads = R / "data" / "file"
    manifest = {
        "stamp": target.name,
        "created": datetime.now().astimezone().isoformat(timespec="seconds"),
        "duration_seconds": round(time.time() - started, 1),
        "revision": {"backend": deployed_revision("backend"), "frontend": deployed_revision("frontend")},
        "databases": database_meta,
        "artifacts": artifacts,
        "uploads": {
            "path": str(uploads),
            "files": sum(1 for p in uploads.rglob("*") if p.is_file()) if uploads.is_dir() else 0,
        },
        "secrets_included": INCLUDE_SECRETS,
        "binlog": {
            "enabled": bool(binlogging),
            "snapshot": snapshot,
            "archived": [entry["artifact"] for entry in binlogs],
        },
        "host": {"disk_free_bytes": disk_free_bytes(), "python": sys.version.split()[0]},
        "verified": None,
    }
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True))
    with (target / "SHA256SUMS").open("w") as output:
        for artifact in artifacts:
            output.write("%s  %s\n" % (artifact["sha256"], artifact["path"]))
    if binlogs:
        (target / "PITR.txt").write_text(pitr_instructions(manifest), encoding="utf-8")
        os.chmod(target / "PITR.txt", 0o600)
    for artifact in artifacts:
        os.chmod(target / artifact["path"], 0o600)
    os.chmod(target / "manifest.json", 0o600)
    os.chmod(target / "SHA256SUMS", 0o600)

    total = sum(artifact["bytes"] for artifact in artifacts)
    log.info("backup %s complete: %d artifacts, %.1f MiB, %.1fs",
             target.name, len(artifacts), total / 1024 ** 2, manifest["duration_seconds"])
    if mirror(target) and binlogs:
        # Two copies exist, so the archived binlogs can leave the database directory.
        purge_binlogs(binlogs[-1]["artifact"].split(".gz")[0])
    for name in prune():
        log.info("pruned old backup %s", name)
    return manifest


def pitr_instructions(manifest):
    snapshot = manifest["binlog"]["snapshot"] or {}
    newest = manifest["binlog"]["archived"][-1].split(".gz")[0] if manifest["binlog"]["archived"] else "?"
    return """Point-in-time recovery for backup {stamp}

1. Restore the logical dump (per schema, e.g. auth):
     gunzip -c db/yunanexus_auth.sql.gz | docker exec -i {container} \
       mysql --defaults-extra-file=<cred> yunanexus_auth
   The dump embeds the snapshot coordinates as a comment:
     SOURCE_LOG_FILE='{file}' / SOURCE_LOG_POS={pos}

2. Replay binlogs from that position UP TO the moment before the incident.
   Always bound the replay, otherwise the incident itself (DROP/UPDATE) is
   re-applied. Use --stop-position (exact) or --stop-datetime (human input):
     for f in binlog/binlog.*.gz; do gunzip -c "$f" > /tmp/"$(basename $f .gz)"; done
     mysqlbinlog --start-position={pos} --stop-datetime='2026-10-27 16:00:00' \
       /tmp/binlog.000001 /tmp/binlog.000002 \
       | docker exec -i {container} mysql --defaults-extra-file=<cred>

   Archived binlogs in this backup: {count} (newest {newest})
   Note: a binlog archive holds the closed files only; the file being written at
   backup time is archived by the next run, so PITR targets between the two runs
   are covered by replaying both backups' binlogs in order.

3. Never replay a binlog twice: start from the position recorded in the dump,
   then continue file by file in numeric order.
""".format(stamp=manifest["stamp"], container=MYSQL_CONTAINER,
           file=snapshot.get("file", "?"), pos=snapshot.get("position", "?"),
           count=len(manifest["binlog"]["archived"]), newest=newest)


def same_device(first, second):
    try:
        return Path(first).stat().st_dev == Path(second).stat().st_dev
    except OSError:
        return False


def mirror(target):
    """Copy a finished backup to the second filesystem.

    /YunaNexus and the OS disk are separate devices on this host, so a mirror
    survives losing the data disk (offsite still needs a decision).
    """
    try:
        if MIRROR.exists() and same_device(MIRROR, BACKUPS.parent):
            log.warning("mirror %s is on the same filesystem as the backups; skipping", MIRROR)
            return None
        destination = MIRROR / target.name
        if destination.exists():
            shutil.rmtree(destination, ignore_errors=True)
        MIRROR.mkdir(parents=True, mode=0o700, exist_ok=True)
        shutil.copytree(target, destination)
        os.chmod(destination, 0o700)
        for path in destination.rglob("*"):
            if path.is_file():
                os.chmod(path, 0o600)
        log.info("mirrored backup to %s", destination)
        stamps = [parse_stamp(p.name) for p in MIRROR.iterdir() if p.is_dir() and parse_stamp(p.name)]
        for stamp in select_prunable(stamps):
            shutil.rmtree(MIRROR / stamp.strftime(STAMP_FORMAT), ignore_errors=True)
            log.info("pruned mirror copy %s", stamp.strftime(STAMP_FORMAT))
        return destination
    except OSError as error:
        log.error("mirroring failed (backup itself is intact): %s", error)
        return None


# ----------------------------------------------------------------------- verify


def scratch_name(schema):
    return "yunanexus_verify_" + schema


def restore_schema(schema, dump_path):
    scratch = scratch_name(schema)
    mysql("DROP DATABASE IF EXISTS `%s`" % scratch)
    mysql("CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" % scratch)
    command = docker("mysql", "--defaults-extra-file=" + CREDENTIALS_PATH,
                     "--default-character-set=utf8mb4", scratch, stdin=True)
    proc = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=subprocess.PIPE, env=docker_env())
    try:
        with gzip.open(dump_path, "rb") as source:
            shutil.copyfileobj(source, proc.stdin)
        proc.stdin.close()
    except BrokenPipeError:
        pass
    error = proc.stderr.read().decode(errors="replace").strip()
    if proc.wait() != 0:
        raise RuntimeError("restore of %s failed: %s" % (schema, error[:500] or "no stderr"))
    return scratch


def verify(stamp=None):
    """Restore one backup into scratch schemas and compare it with production."""
    configure_logging()
    ensure_credentials()
    target = BACKUPS / stamp if stamp else newest_backup()
    if not target or not (target / "manifest.json").is_file():
        raise RuntimeError("No backup found to verify")
    manifest = json.loads((target / "manifest.json").read_text())
    log.info("verifying %s (created %s)", target.name, manifest["created"])

    checksums = {}
    for line in (target / "SHA256SUMS").read_text().splitlines():
        digest, path = line.split("  ", 1)
        checksums[path] = digest
    problems = []
    for path, expected in checksums.items():
        digest = hashlib.sha256()
        with (target / path).open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != expected:
            problems.append("checksum mismatch: %s" % path)

    problems.extend(check_binlogs(target, manifest))
    problems.extend(check_secrets(target, manifest))

    results = {}
    created = []
    try:
        return _verify_databases(target, manifest, results, created, problems)
    finally:
        # Order matters: the scratch schemas can only be dropped while the
        # container still holds the staged credentials file.
        for scratch in created:
            try:
                mysql("DROP DATABASE IF EXISTS `%s`" % scratch)
            except Exception as error:  # pragma: no cover - best effort cleanup
                log.error("cannot drop scratch schema %s: %s", scratch, error)
        drop_credentials()


def _verify_databases(target, manifest, results, created, problems):
    for schema, meta in sorted(manifest["databases"].items()):
        expected_counts = meta["counts"]
        scratch = restore_schema(schema, target / meta["dump"]["path"])
        created.append(scratch)
        restored = table_counts(scratch)
        missing = sorted(set(expected_counts) - set(restored))
        mismatched = sorted(
            name for name in set(expected_counts) & set(restored)
            if expected_counts[name] != restored[name]
        )
        results[schema] = {
            "tables_expected": len(expected_counts),
            "tables_restored": len(restored),
            "rows_expected": sum(expected_counts.values()),
            "rows_restored": sum(restored.values()),
            "missing_tables": missing,
            "row_mismatch": mismatched,
        }
        log.info("  %s: %d/%d tables, %d/%d rows%s", schema,
                 len(restored), len(expected_counts), sum(restored.values()),
                 sum(expected_counts.values()),
                 "" if not (missing or mismatched) else " MISMATCH")
        if missing:
            problems.append("%s missing tables after restore: %s" % (schema, missing))
        if mismatched:
            problems.append("%s row count mismatch: %s" % (schema, mismatched))

    report = {
        "stamp": target.name,
        "verified_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "ok": not problems,
        "problems": problems,
        "schemas": results,
    }
    manifest["verified"] = {"ok": report["ok"], "at": report["verified_at"]}
    (target / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True))
    (target / "verify.json").write_text(json.dumps(report, indent=2, sort_keys=True))
    os.chmod(target / "verify.json", 0o600)
    if problems:
        raise RuntimeError("restore drill failed: %s" % "; ".join(problems))
    log.info("restore drill passed for %s", target.name)
    return report


def check_binlogs(target, manifest):
    """A binlog archive is only useful if the files are real, complete binlogs."""
    problems = []
    for name in manifest.get("binlog", {}).get("archived", []):
        path = target / "binlog" / name
        if not path.is_file():
            problems.append("missing binlog archive %s" % name)
            continue
        try:
            with gzip.open(path, "rb") as handle:
                header = handle.read(4)
                rest = handle.read(1)
        except (OSError, EOFError) as error:
            problems.append("unreadable binlog archive %s: %s" % (name, error))
            continue
        if header[:4] != BINLOG_MAGIC or rest == b"":
            problems.append("binlog archive %s has an invalid header" % name)
    if manifest.get("binlog", {}).get("archived"):
        log.info("  binlog archive: %d file(s) verified",
                 len(manifest["binlog"]["archived"]))
    return problems


def check_secrets(target, manifest):
    """Decrypt the encrypted secrets archive and prove it still contains them."""
    if not manifest.get("secrets_included"):
        return []
    archive = target / "secrets.tar.gz.enc"
    if not archive.is_file():
        return ["encrypted secrets archive is missing"]
    try:
        passphrase_file = passphrase_path(create=False)
    except RuntimeError as error:
        return [str(error)]
    scratch = target / ".secrets-verify.tar.gz"
    try:
        decrypt_file(archive, passphrase_file, scratch)
        with tarfile.open(scratch, "r:gz") as tar:
            names = tar.getnames()
    except Exception as error:
        return ["secrets archive cannot be decrypted: %s" % error]
    finally:
        scratch.unlink(missing_ok=True)
    if not any(name.endswith("compose.env") for name in names):
        return ["secrets archive does not contain compose.env"]
    log.info("  secrets archive: decrypted, %d entries", len(names))
    return []


def newest_backup():
    candidates = [
        entry for entry in (BACKUPS.iterdir() if BACKUPS.is_dir() else [])
        if entry.is_dir() and parse_stamp(entry.name)
    ]
    return max(candidates, key=lambda entry: parse_stamp(entry.name)) if candidates else None


def main(argv=None):
    parser = argparse.ArgumentParser(description="Create or verify a YunaNexus platform backup")
    parser.add_argument("--verify", nargs="?", const="", metavar="STAMP",
                        help="restore the newest (or named) backup into scratch schemas")
    parser.add_argument("--list", action="store_true", help="list existing backups")
    parser.add_argument("--prune", action="store_true", help="apply the retention policy only")
    parser.add_argument("--ship-binlogs", action="store_true",
                        help="copy closed binlogs to the mirror (hourly job)")
    parser.add_argument("--mirror", action="store_true",
                        help="copy every existing backup to the mirror filesystem")
    args = parser.parse_args(argv)
    configure_logging()
    if args.list:
        for entry in sorted(BACKUPS.iterdir()) if BACKUPS.is_dir() else []:
            if not parse_stamp(entry.name):
                continue
            size = sum(p.stat().st_size for p in entry.rglob("*") if p.is_file())
            print("%s  %8.1f MiB  %s" % (entry.name, size / 1024 ** 2, entry.name))
        return
    if args.prune:
        for name in prune():
            print("pruned", name)
        return
    if args.ship_binlogs:
        for name in ship_binlogs():
            print("shipped", name)
        return
    if args.mirror:
        for entry in sorted(BACKUPS.iterdir()) if BACKUPS.is_dir() else []:
            if entry.is_dir() and parse_stamp(entry.name):
                print("mirrored", entry.name, "->", mirror(entry))
        return
    if args.verify is not None:
        verify(args.verify or None)
        return
    manifest = backup()
    print(json.dumps(manifest, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
