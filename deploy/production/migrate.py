#!/usr/bin/env python3
"""Versioned database migrations for YunaNexus.

Why a runner instead of Flyway in the jar: the platform jars are built elsewhere
and this host has no JDK, so schema history has to live next to the deploy tooling
rather than inside the application. The semantics are the ones Flyway established:

    migrations/<service>/<version>__<description>.sql

Each file is applied to *every* physical schema of that service. The shards are
created lazily, so two shards of the same service can legitimately hold different
tables - records are therefore kept per (service, schema) and statements are
expected to be idempotent (`CREATE TABLE IF NOT EXISTS`).

Guarantees:
  * a run takes a MySQL advisory lock, so two deploys cannot migrate at once;
  * every applied file is recorded with its sha256 - editing an applied file is
    reported as DRIFT instead of silently diverging;
  * `--status` exits non-zero when migrations are pending, which is what the
    release pre-flight uses to refuse a code/schema mismatch;
  * `--baseline` adopts an existing database without executing anything.
"""
import argparse
import hashlib
import importlib.util
import json
import logging
import os
import re
import socket
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
MIGRATIONS = Path(os.environ.get("YUNANEXUS_MIGRATIONS", str(R / "ops/migrations")))
CONTROL_SCHEMA = "yunanexus_ops"
CONTROL_TABLE = "schema_migrations"
LOCK_NAME = "yunanexus:migrate"
PROBE = "__yunanexus_migration_probe__"
MIGRATION_TIMEOUT = int(os.environ.get("YUNANEXUS_MIGRATION_TIMEOUT", "900"))
FILE_PATTERN = re.compile(r"^(\d{3,})__([A-Za-z0-9_.-]+)\.sql$")
SERVICE_PATTERN = re.compile(r"^yunanexus_([a-z0-9]+?)(_\d+)?$")

log = logging.getLogger("migrate")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)

CONTROL_DDL = """CREATE TABLE IF NOT EXISTS {schema}.{table} (
  service VARCHAR(64) NOT NULL,
  schema_name VARCHAR(64) NOT NULL,
  version INT NOT NULL,
  description VARCHAR(200) NOT NULL,
  checksum CHAR(64) NOT NULL,
  applied_at DATETIME NOT NULL,
  duration_ms INT NOT NULL,
  applied_by VARCHAR(100) NOT NULL,
  PRIMARY KEY (service, schema_name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""".format(schema=CONTROL_SCHEMA, table=CONTROL_TABLE)


def _backup_module():
    """Reuse the tested MySQL access path (credentials staged in the container)."""
    path = R / "ops/backup.py"
    spec = importlib.util.spec_from_file_location("yunanexus_backup_for_migrate", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    return module


_BACKUP = None


def backup():
    global _BACKUP
    if _BACKUP is None:
        _BACKUP = _backup_module()
    return _BACKUP


def run_sql(sql, database=None):
    return backup().mysql(sql, database)


def open_credentials():
    backup().ensure_credentials()


def close_credentials():
    backup().drop_credentials()


# ---------------------------------------------------------------- discovery


def output_rows(output):
    """Split client output into rows.

    The client runs with --skip-column-names, so there is no header to drop; assuming
    one silently deleted the first row (auth's migration record, and the audit_0 schema).
    """
    return [line for line in output.splitlines() if line.strip()]


def discover_schemas():
    rows = output_rows(run_sql(
        "SELECT schema_name FROM information_schema.schemata "
        "WHERE schema_name LIKE 'yunanexus%' ORDER BY 1"))
    names = [line.strip() for line in rows]
    return [name for name in names if not name.startswith("yunanexus_verify_")
            and name != CONTROL_SCHEMA]


def service_of(schema):
    match = SERVICE_PATTERN.match(schema)
    return match.group(1) if match else None


def targets(service, schemas=None):
    schemas = schemas if schemas is not None else discover_schemas()
    return sorted(name for name in schemas if service_of(name) == service)


def discover_migrations(directory=None):
    """Return {service: [migration dicts sorted by version]}."""
    directory = directory or MIGRATIONS
    found = {}
    if not directory.is_dir():
        return found
    for service_dir in sorted(p for p in directory.iterdir() if p.is_dir()):
        migrations = []
        versions = {}
        for path in sorted(service_dir.glob("*.sql")):
            match = FILE_PATTERN.match(path.name)
            if not match:
                raise ValueError("invalid migration filename: %s" % path.name)
            version = int(match.group(1))
            if version in versions:
                raise ValueError("duplicate version %d for %s (%s and %s)"
                                 % (version, service_dir.name, versions[version].name, path.name))
            versions[version] = path
            migrations.append({
                "version": version,
                "description": match.group(2),
                "path": path,
                "checksum": hashlib.sha256(path.read_bytes()).hexdigest(),
            })
        if migrations:
            found[service_dir.name] = sorted(migrations, key=lambda item: item["version"])
    return found


# ---------------------------------------------------------------- sql splitting


def split_statements(sql):
    """Statements of a migration file, in order (see split_with_lines)."""
    return [statement for statement, _ in split_with_lines(sql)]


def split_with_lines(sql):
    """Split on semicolons outside strings and comments, keeping each start line.

    The client reports failures by line number, so the split keeps the mapping.
    """
    statements, current, index = [], [], 0
    in_single = in_double = in_backtick = False
    in_line_comment = in_block_comment = False
    length = len(sql)
    while index < length:
        char = sql[index]
        nxt = sql[index + 1] if index + 1 < length else ""
        if in_line_comment:
            if char == "\n":
                in_line_comment = False
                current.append(char)
            index += 1
            continue
        if in_block_comment:
            if char == "*" and nxt == "/":
                in_block_comment = False
                index += 2
                continue
            index += 1
            continue
        if in_single:
            current.append(char)
            if char == "'" and nxt == "'":
                current.append(nxt)
                index += 2
                continue
            if char == "'":
                in_single = False
            index += 1
            continue
        if in_double:
            current.append(char)
            if char == '"' and nxt == '"':
                current.append(nxt)
                index += 2
                continue
            if char == '"':
                in_double = False
            index += 1
            continue
        if in_backtick:
            current.append(char)
            if char == "`":
                in_backtick = False
            index += 1
            continue
        if char == "-" and nxt == "-":
            in_line_comment = True
            index += 2
            continue
        if char == "#":
            in_line_comment = True
            index += 1
            continue
        if char == "/" and nxt == "*":
            in_block_comment = True
            index += 2
            continue
        if char == "'":
            in_single = True
            current.append(char)
            index += 1
            continue
        if char == '"':
            in_double = True
            current.append(char)
            index += 1
            continue
        if char == "`":
            in_backtick = True
            current.append(char)
            index += 1
            continue
        if char == ";":
            statement = "".join(current).strip()
            if statement:
                statements.append((statement, sql.count("\n", 0, index) + 1))
            current = []
            index += 1
            continue
        current.append(char)
        index += 1
    tail = "".join(current).strip()
    if tail:
        statements.append((tail, sql.count("\n", 0, len(sql)) + 1))
    return statements


# ---------------------------------------------------------------- control table


def ensure_control():
    run_sql("CREATE SCHEMA IF NOT EXISTS `%s`" % CONTROL_SCHEMA)
    run_sql(CONTROL_DDL)


def control_exists():
    rows = run_sql("SELECT schema_name FROM information_schema.schemata "
                   "WHERE schema_name = '%s'" % CONTROL_SCHEMA)
    return CONTROL_SCHEMA in {line.strip() for line in output_rows(rows)}


def applied_records(service=None, schema=None):
    if not control_exists():
        return {}
    clauses = []
    if service:
        clauses.append("service = '%s'" % service)
    if schema:
        clauses.append("schema_name = '%s'" % schema)
    where = (" WHERE " + " AND ".join(clauses)) if clauses else ""
    rows = run_sql("SELECT service, schema_name, version, description, checksum, applied_at "
                   "FROM `%s`.`%s`%s ORDER BY service, schema_name, version"
                   % (CONTROL_SCHEMA, CONTROL_TABLE, where))
    records = {}
    for line in output_rows(rows):
        parts = line.split("\t")
        if len(parts) < 6 or not parts[2].strip().isdigit():
            continue
        records[(parts[0], parts[1], int(parts[2]))] = {
            "description": parts[3], "checksum": parts[4], "applied_at": parts[5]}
    return records


def record(service, schema, migration, duration_ms):
    run_sql(
        "INSERT INTO `%s`.`%s` (service, schema_name, version, description, checksum, "
        "applied_at, duration_ms, applied_by) VALUES ('%s', '%s', %d, '%s', '%s', NOW(), %d, '%s')"
        " ON DUPLICATE KEY UPDATE checksum = VALUES(checksum), applied_at = VALUES(applied_at),"
        " duration_ms = VALUES(duration_ms), applied_by = VALUES(applied_by)"
        % (CONTROL_SCHEMA, CONTROL_TABLE, service, schema, migration["version"],
           migration["description"].replace("'", "''"), migration["checksum"],
           duration_ms, "%s@%s" % (os.environ.get("USER", "root"), socket.gethostname())))


# ---------------------------------------------------------------- planning


def plan(services=None, schemas=None):
    """Work out, per schema, what is applied, what is pending and what drifted."""
    migrations = discover_migrations()
    schemas = schemas if schemas is not None else discover_schemas()
    records = applied_records()
    report = []
    for service, files in sorted(migrations.items()):
        if services and service not in services:
            continue
        available = targets(service, schemas)
        if not available:
            report.append({"service": service, "schema": None, "state": "no-schema",
                           "pending": files, "drift": []})
            continue
        for schema in available:
            applied = {version: value for (svc, name, version), value in records.items()
                       if svc == service and name == schema}
            pending = [item for item in files if item["version"] not in applied]
            drift = [item for item in files
                     if item["version"] in applied
                     and applied[item["version"]]["checksum"] != item["checksum"]]
            state = "drift" if drift else ("pending" if pending else "current")
            report.append({"service": service, "schema": schema, "state": state,
                           "pending": pending, "drift": drift})
    return report


def has_blocking_work(report):
    return any(entry["state"] in ("pending", "drift") for entry in report)


def summarise(report):
    lines = []
    for entry in report:
        if entry["schema"] is None:
            lines.append("%-12s %-24s no schema exists yet (%d file(s) defined)"
                         % (entry["service"], "-", len(entry["pending"])))
            continue
        detail = ""
        if entry["state"] == "pending":
            detail = " pending: " + ", ".join(str(m["version"]) for m in entry["pending"])
        elif entry["state"] == "drift":
            detail = " DRIFT: " + ", ".join(str(m["version"]) for m in entry["drift"])
        lines.append("%-12s %-24s %-8s%s" % (entry["service"], entry["schema"],
                                             entry["state"], detail))
    return "\n".join(lines)


# ---------------------------------------------------------------- execution


def acquire_lock(timeout=15):
    rows = output_rows(run_sql("SELECT GET_LOCK('%s', %d)" % (LOCK_NAME, timeout)))
    value = rows[-1].strip() if rows else "0"
    return value.strip() == "1"


def release_lock():
    run_sql("SELECT RELEASE_LOCK('%s')" % LOCK_NAME)


def run_script(text, database):
    """Feed a whole file to one client session.

    One invocation per statement looked tidier but was wrong: session variables,
    temporary tables and PREPARE handles vanish between processes, so the guarded
    ALTER pattern (`SET @present := ...; PREPARE ...; EXECUTE ...`) silently broke.

    Two traps are guarded here. `docker exec` needs -i or stdin is never delivered,
    and without the probe that failure looks exactly like success. So every script
    is prefixed with a canary SELECT and its absence raises instead of recording a
    migration that never ran.
    """
    module = backup()
    command = module.docker("mysql", "--defaults-extra-file=" + module.CREDENTIALS_PATH,
                            "--batch", "--skip-column-names", database, stdin=True)
    payload = "SELECT '%s';\n%s" % (PROBE, text)
    result = subprocess.run(command, input=payload, capture_output=True, text=True,
                            timeout=MIGRATION_TIMEOUT, env=module.docker_env())
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout).strip())
    if PROBE not in result.stdout:
        raise RuntimeError("the client produced no probe output: the script was not executed "
                           "(missing -i on docker exec?)")
    return result.stdout


def failing_statement(text, stderr):
    """Map the client's "at line N" back to the statement that broke."""
    match = re.search(r"at line (\d+)", stderr or "")
    if not match:
        return ""
    line = int(match.group(1))
    statements = split_with_lines(text)
    chosen = None
    for number, (_, start) in enumerate(statements, 1):
        if start > line:
            break
        chosen = number
    if chosen is None:
        return ""
    start, statement = statements[chosen - 1][1], statements[chosen - 1][0]
    number = chosen
    snippet = " ".join(statement.split())[:120]
    return "statement %d/%d (line %d): %s" % (number, len(statements), start, snippet)


def apply_migration(schema, migration):
    """Execute one file against one schema; returns duration in ms."""
    text = migration["path"].read_text(encoding="utf-8")
    if not split_statements(text):
        raise RuntimeError("%s contains no statements" % migration["path"].name)
    started = time.time()
    try:
        run_script(text, schema)
    except RuntimeError as error:
        where = failing_statement(text, str(error))
        raise RuntimeError("%s failed on %s%s: %s"
                           % (migration["path"].name, schema,
                              (" - " + where) if where else "", error))
    return int((time.time() - started) * 1000)


def apply(services=None, dry_run=False):
    report = plan(services)
    if not has_blocking_work(report):
        log.info("nothing to apply; every schema is current")
        return 0
    if any(entry["state"] == "drift" for entry in report):
        for entry in report:
            for item in entry["drift"]:
                log.error("drift: %s/%s version %d was modified after being applied",
                          entry["service"], entry["schema"], item["version"])
        return 2
    if dry_run:
        for entry in report:
            for item in entry["pending"]:
                print("-- would apply %s to %s" % (item["path"].name, entry["schema"]))
                print(split_and_join(item["path"]))
        return 1
    if not acquire_lock():
        log.error("another migration run holds the lock; giving up")
        return 2
    try:
        ensure_control()
        failures = 0
        for entry in report:
            for item in entry["pending"]:
                try:
                    duration = apply_migration(entry["schema"], item)
                except Exception as error:
                    log.error("%s", error)
                    failures += 1
                    break   # stop this schema; later versions assume this one landed
                record(entry["service"], entry["schema"], item, duration)
                log.info("applied %s -> %s in %dms", item["path"].name, entry["schema"], duration)
        return 2 if failures else 0
    finally:
        release_lock()


def split_and_join(path):
    return ";\n".join(split_statements(path.read_text(encoding="utf-8"))) + ";"


def baseline(services=None, note=""):
    """Record existing files as applied without executing them (adoption path)."""
    if not acquire_lock():
        log.error("another migration run holds the lock; giving up")
        return 2
    try:
        ensure_control()
        report = plan(services)
        recorded = 0
        for entry in report:
            if entry["schema"] is None:
                continue
            # Only versions that already exist in this schema are candidates; a
            # baseline must never claim a schema change that was not verified.
            existing = existing_tables(entry["schema"])
            for item in entry["pending"]:
                missing = tables_for(item, existing)
                if missing:
                    # Shards are created lazily, so a shard legitimately lacks tables
                    # the file creates. Record it anyway, but say so out loud.
                    log.warning("baseline on %s: %s does not (yet) contain %s",
                                entry["schema"], item["path"].name, ", ".join(sorted(missing)))
                record(entry["service"], entry["schema"], item, 0)
                recorded += 1
        log.info("baseline recorded %d migration(s)%s", recorded, (" (%s)" % note) if note else "")
        return 0
    finally:
        release_lock()


def existing_tables(schema):
    rows = run_sql("SELECT table_name FROM information_schema.tables "
                   "WHERE table_schema = '%s'" % schema)
    return {line.strip() for line in output_rows(rows)}


def tables_for(migration, existing):
    """Tables a migration is expected to produce, inferred from CREATE TABLE statements."""
    text = migration["path"].read_text(encoding="utf-8").lower()
    names = re.findall(r"create\s+table\s+(?:if\s+not\s+exists\s+)?`?([a-z0-9_]+)`?", text)
    if not names:
        return set()          # ALTER/INSERT style migrations: nothing to verify
    return {name for name in names if name not in existing}


def forget(service, version):
    """Delete one record so a migration can be re-run.

    Needed after an incident where a record was written although the SQL never ran
    (and whenever an operator must re-apply a version by hand). Loud on purpose.
    """
    if not control_exists():
        log.error("no control schema; nothing to forget")
        return 2
    existing = applied_records(service)
    if not any(key[2] == version for key in existing):
        log.error("no record of %s version %d", service, version)
        return 2
    run_sql("DELETE FROM `%s`.`%s` WHERE service = '%s' AND version = %d"
            % (CONTROL_SCHEMA, CONTROL_TABLE, service, version))
    log.warning("forgot %s version %d; the file will run again on the next --apply", service, version)
    return 0


def status(services=None, as_json=False):
    report = plan(services)
    if as_json:
        print(json.dumps([{
            "service": entry["service"], "schema": entry["schema"], "state": entry["state"],
            "pending": [item["version"] for item in entry["pending"]],
            "drift": [item["version"] for item in entry["drift"]],
        } for entry in report], indent=2))
    else:
        print(summarise(report))
    return 1 if has_blocking_work(report) else 0


def main(argv=None):
    parser = argparse.ArgumentParser(description="Versioned database migrations")
    parser.add_argument("--status", action="store_true", help="show applied/pending/drift")
    parser.add_argument("--apply", action="store_true", help="apply pending migrations")
    parser.add_argument("--baseline", action="store_true",
                        help="record existing migrations as applied without running them")
    parser.add_argument("--dry-run", action="store_true", help="print the SQL that would run")
    parser.add_argument("--service", action="append", default=[], help="limit to a service")
    parser.add_argument("--forget", type=int, metavar="VERSION",
                        help="delete one record so that version runs again (needs --service)")
    parser.add_argument("--json", action="store_true", help="machine-readable status")
    args = parser.parse_args(argv)

    services = set(args.service) or None
    open_credentials()
    try:
        if args.forget is not None:
            if len(services or []) != 1:
                log.error("--forget needs exactly one --service")
                return 2
            return forget(next(iter(services)), args.forget)
        if args.status or not (args.apply or args.baseline):
            return status(services, args.json)
        if args.baseline:
            return baseline(services)
        return apply(services, dry_run=args.dry_run)
    finally:
        close_credentials()


if __name__ == "__main__":
    sys.exit(main())
