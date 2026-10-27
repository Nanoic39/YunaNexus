#!/usr/bin/env python3
"""Prove point-in-time recovery works, using a throw-away probe schema.

Claiming "we support PITR" is cheap; this script actually loses data and gets it
back the documented way:

    1. create probe schema + row A
    2. take a logical dump (with embedded binlog coordinates)
    3. write row B afterwards
    4. drop the table (the "disaster")
    5. restore the dump, then replay binlogs from the recorded position
    6. assert A and B are both back

The probe schema is dropped again, so nothing leaks into production backups.
"""

import gzip
import importlib.util
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import datetime
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
PROBE = "yunanexus_pitr_probe"
COORDINATE = re.compile(r"SOURCE_LOG_FILE='([^']+)'.*SOURCE_LOG_POS=(\d+)")
ROW_MARKERS = {1: "row-before-backup", 2: "row-after-backup"}
MYSQLBINLOG = os.environ.get("YUNANEXUS_MYSQLBINLOG", "/usr/local/bin/mysqlbinlog")


def load_backup():
    spec = importlib.util.spec_from_file_location("bk", R / "ops" / "backup.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    return module


def binlog_status(bk):
    row = bk.mysql("SHOW BINARY LOG STATUS").strip().split("\t")
    if len(row) < 2:
        raise RuntimeError("cannot read binlog coordinates")
    return {"file": row[0], "position": int(row[1])}


def check(condition, message):
    if not condition:
        raise RuntimeError(message)
    print("  ok  " + message)


def main():
    bk = load_backup()
    bk.ensure_credentials()
    workdir = Path(tempfile.mkdtemp(prefix="pitr-drill-"))
    try:
        print("preparing probe schema %s" % PROBE)
        bk.mysql("DROP DATABASE IF EXISTS `%s`" % PROBE)
        bk.mysql("CREATE DATABASE `%s`" % PROBE)
        bk.mysql("CREATE TABLE `%s`.t (id INT PRIMARY KEY, v VARCHAR(32))" % PROBE)
        bk.mysql("INSERT INTO `%s`.t VALUES (1, '%s')" % (PROBE, ROW_MARKERS[1]))

        dump = workdir / "probe.sql.gz"
        bk.dump_schema(PROBE, dump)
        with gzip.open(dump, "rt", errors="replace") as handle:
            header = handle.read(8192)
        match = COORDINATE.search(header)
        check(match is not None, "dump embeds binlog coordinates")
        log_file, log_position = match.group(1), int(match.group(2))
        print("     snapshot at %s:%d" % (log_file, log_position))

        bk.mysql("INSERT INTO `%s`.t VALUES (2, '%s')" % (PROBE, ROW_MARKERS[2]))
        check(bk.table_counts(PROBE)["t"] == 2, "two rows written before the disaster")
        # Recovery target: the moment just before the incident. Everything after
        # this point (including the incident itself) must NOT be replayed.
        boundary = binlog_status(bk)
        print("     recovery target at %s:%d" % (boundary["file"], boundary["position"]))

        print("simulating data loss: DROP TABLE")
        bk.mysql("DROP TABLE `%s`.t" % PROBE)
        check(bk.table_counts(PROBE).get("t", 0) == 0, "table is gone")

        print("restoring the dump")
        bk.mysql("DROP DATABASE IF EXISTS `%s`" % PROBE)
        bk.mysql("CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci" % PROBE)
        command = bk.docker("mysql", "--defaults-extra-file=" + bk.CREDENTIALS_PATH,
                            PROBE, stdin=True)
        proc = subprocess.Popen(command, stdin=subprocess.PIPE, stderr=subprocess.PIPE,
                                env=bk.docker_env())
        with gzip.open(dump, "rb") as source:
            shutil.copyfileobj(source, proc.stdin)
        proc.stdin.close()
        errors = proc.stderr.read().decode(errors="replace").strip()
        check(proc.wait() == 0, "dump loaded" + ("" if not errors else " (%s)" % errors[:200]))
        check(bk.table_counts(PROBE).get("t") == 1, "dump alone restores only the pre-disaster row")

        print("replaying binary logs up to the incident (%s:%d)"
              % (boundary["file"], boundary["position"]))
        binary = shutil.which("mysqlbinlog") or MYSQLBINLOG
        check(os.access(binary, os.X_OK),
              "mysqlbinlog available at %s (replay needs the client tool)" % binary)
        bk.mysql("FLUSH BINARY LOGS")  # close the current file so every file is complete
        files = [p for p in bk.binlog_files() if log_file <= p.name <= boundary["file"]]
        check(files, "binlog files available for replay")
        for index, path in enumerate(files):
            start = log_position if index == 0 else 4
            arguments = [binary, "--database=" + PROBE, "--start-position=%d" % start]
            if path.name == boundary["file"]:
                # Stop at the incident: replaying past it would re-apply the damage.
                arguments.append("--stop-position=%d" % boundary["position"])
            arguments.append(str(path))
            parsed = subprocess.run(arguments, capture_output=True)
            check(parsed.returncode == 0,
                  "mysqlbinlog parsed %s from position %d%s" % (
                      path.name, start, "" if parsed.returncode == 0
                      else ": %s" % (parsed.stderr or parsed.stdout).decode(
                          errors="replace")[:300]))
            check(len(parsed.stdout) > 0, "mysqlbinlog produced statements for %s" % path.name)
            applied = subprocess.run(
                bk.docker("mysql", "--defaults-extra-file=" + bk.CREDENTIALS_PATH,
                          PROBE, stdin=True),
                input=parsed.stdout, capture_output=True, env=bk.docker_env(),
            )
            check(applied.returncode == 0,
                  "applied %s to the restored schema%s" % (
                      path.name, "" if applied.returncode == 0
                      else ": %s" % (applied.stderr or applied.stdout).decode(
                          errors="replace")[:300]))

        rows = bk.mysql("SELECT id, v FROM `%s`.t ORDER BY id" % PROBE)
        recovered = dict(
            (int(line.split("\t")[0]), line.split("\t")[1])
            for line in rows.splitlines() if "\t" in line
        )
        check(recovered.get(1) == ROW_MARKERS[1], "row written before the backup is present")
        check(recovered.get(2) == ROW_MARKERS[2],
              "row written after the backup was recovered from binlogs")
        print("PITR drill passed at %s" % datetime.now().isoformat(timespec="seconds"))
        return 0
    finally:
        try:
            bk.mysql("DROP DATABASE IF EXISTS `%s`" % PROBE)
        except Exception:
            pass
        bk.drop_credentials()
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        print("PITR DRILL FAILED: %s: %s" % (type(error).__name__, error), file=sys.stderr)
        sys.exit(1)
