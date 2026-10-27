#!/usr/bin/env python3
"""Migration runner: statement splitting, planning, drift detection, execution."""

import importlib.util
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


class FakeDB:
    """Minimal stand-in for the MySQL client used by migrate.py."""

    def __init__(self, schemas, tables=None):
        self.schemas = list(schemas)
        self.tables = tables or {}
        self.records = {}
        self.executed = []
        self.fail_on = None
        self.locked = True

    def run_sql(self, sql, database=None):
        text = " ".join(sql.split())
        if "information_schema.schemata" in text:
            if "'%s'" % "yunanexus_ops" in text:
                return "yunanexus_ops\n" if "yunanexus_ops" in self.schemas else ""
            return "\n".join(sorted(self.schemas)) + "\n"
        if "information_schema.tables" in text:
            schema = text.split("table_schema = '")[1].split("'")[0]
            return "\n".join(sorted(self.tables.get(schema, []))) + "\n"
        if text.startswith("CREATE SCHEMA"):
            name = text.split("`")[1]
            if name not in self.schemas:
                self.schemas.append(name)
            return ""
        if text.startswith("CREATE TABLE IF NOT EXISTS yunanexus_ops"):
            return ""
        if text.startswith("DELETE FROM `yunanexus_ops`"):
            match = re.search(r"service = '([^']+)' AND version = (\d+)", text)
            if match:
                service, version = match.group(1), int(match.group(2))
                for key in [k for k in self.records if k[0] == service and k[2] == version]:
                    self.records.pop(key)
            return ""
        if text.startswith("SELECT GET_LOCK"):
            return "%d" % (1 if self.locked else 0)
        if text.startswith("SELECT RELEASE_LOCK"):
            return "1"
        if text.startswith("SELECT service, schema_name, version"):
            lines = []
            for (service, schema, version), value in sorted(self.records.items()):
                lines.append("%s\t%s\t%d\t%s\t%s\t%s" % (service, schema, version,
                                                         value["description"], value["checksum"],
                                                         value["applied_at"]))
            return "\n".join(lines) + "\n"
        if text.startswith("INSERT INTO `yunanexus_ops`"):
            fields = text.split("VALUES (")[1].split(") ON DUPLICATE")[0]
            parts = [part.strip().strip("'") for part in fields.split(",")]
            service, schema, version = parts[0], parts[1], int(parts[2])
            self.records[(service, schema, version)] = {
                "description": parts[3], "checksum": parts[4], "applied_at": "2026-10-27 10:00:00"}
            return ""
        if self.fail_on and self.fail_on in text:
            raise RuntimeError("simulated failure: %s" % text[:40])
        self.executed.append((database, text))
        return ""

    def run_script(self, text, database):
        """Migrations are sent as one client session, not statement by statement."""
        if self.fail_on and self.fail_on in text:
            line = text[:text.index(self.fail_on)].count("\n") + 1
            raise RuntimeError("mysql failed: ERROR 1064 at line %d: bad syntax" % line)
        self.executed.append((database, text))
        return ""


def load():
    root = tempfile.mkdtemp()
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = root
    name = "migrate_%s" % abs(hash(root))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "migrate.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.MIGRATIONS = Path(root) / "ops/migrations"
    module.open_credentials = lambda: None
    module.close_credentials = lambda: None
    module.log.handlers.clear()
    return module


class MigrateTests(unittest.TestCase):
    def setUp(self):
        self.module = load()
        self.db = FakeDB(["yunanexus_user_0", "yunanexus_user_1", "yunanexus_auth"],
                         {"yunanexus_user_0": ["user_badge"], "yunanexus_user_1": [], "yunanexus_auth": []})
        self.module.run_sql = self.db.run_sql
        self.real_run_script = self.module.run_script
        self.module.run_script = self.db.run_script
        self.root = self.module.MIGRATIONS

    def write(self, service, filename, body):
        directory = self.root / service
        directory.mkdir(parents=True, exist_ok=True)
        (directory / filename).write_text(body)

    # -------------------------------------------------------------- discovery

    def test_service_names(self):
        self.assertEqual(self.module.service_of("yunanexus_user_0"), "user")
        self.assertEqual(self.module.service_of("yunanexus_auth"), "auth")
        self.assertIsNone(self.module.service_of("mysql"))

    def test_targets_fan_out_to_all_shards(self):
        schemas = self.module.discover_schemas()
        self.assertEqual(self.module.targets("user", schemas),
                         ["yunanexus_user_0", "yunanexus_user_1"])

    def test_duplicate_version_is_rejected(self):
        self.write("user", "002__one.sql", "SELECT 1;")
        self.write("user", "002__two.sql", "SELECT 2;")
        with self.assertRaises(ValueError):
            self.module.discover_migrations()

    def test_invalid_filename_is_rejected(self):
        self.write("user", "no_version.sql", "SELECT 1;")
        with self.assertRaises(ValueError):
            self.module.discover_migrations()

    # -------------------------------------------------------------- splitting

    def test_split_respects_strings_and_comments(self):
        sql = """
        -- a comment with a ; inside
        CREATE TABLE IF NOT EXISTS t (v VARCHAR(10) DEFAULT 'a;b');
        /* block ; comment */
        INSERT INTO t VALUES ('x'); INSERT INTO t VALUES ("y");
        """
        statements = self.module.split_statements(sql)
        self.assertEqual(len(statements), 3)
        self.assertIn("'a;b'", statements[0])
        self.assertTrue(statements[2].startswith("INSERT INTO t VALUES (\"y\")"))

    def test_trailing_statement_without_semicolon(self):
        self.assertEqual(self.module.split_statements("SELECT 1"), ["SELECT 1"])

    # -------------------------------------------------------------- planning

    def test_first_row_is_not_dropped(self):
        """The client runs with --skip-column-names, so there is no header to skip."""
        self.write("auth", "001__baseline_schema.sql", "SELECT 1;")
        self.assertEqual(self.module.targets("auth", self.module.discover_schemas()),
                         ["yunanexus_auth"])
        self.module.baseline()
        self.assertIn(("auth", "yunanexus_auth", 1), self.db.records)
        self.assertFalse(self.module.has_blocking_work(self.module.plan()))

    def test_pending_when_nothing_recorded(self):
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id INT);")
        report = self.module.plan()
        states = {entry["schema"]: entry["state"] for entry in report}
        self.assertEqual(states, {"yunanexus_user_0": "pending", "yunanexus_user_1": "pending"})
        self.assertTrue(self.module.has_blocking_work(report))

    def test_current_after_baseline(self):
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id INT);")
        self.assertEqual(self.module.baseline(), 0)
        self.assertEqual(len(self.db.records), 2)          # one row per shard
        report = self.module.plan()
        self.assertFalse(self.module.has_blocking_work(report))

    def test_edited_file_is_reported_as_drift(self):
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id INT);")
        self.module.baseline()
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id BIGINT);")
        report = self.module.plan()
        self.assertTrue(any(entry["state"] == "drift" for entry in report))
        self.assertEqual(self.module.apply(), 2)           # refuse to run over drift

    # -------------------------------------------------------------- execution

    def test_apply_runs_on_every_shard(self):
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id INT);")
        self.write("user", "002__add_tag.sql",
                   "ALTER TABLE `user_badge` ADD COLUMN tag VARCHAR(16);")
        self.assertEqual(self.module.apply(), 0)
        targets = {database for database, _ in self.db.executed}
        self.assertEqual(targets, {"yunanexus_user_0", "yunanexus_user_1"})
        self.assertEqual(len(self.db.records), 4)          # 2 files x 2 shards

    def test_failure_stops_the_schema_and_is_not_recorded(self):
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id INT);")
        self.write("user", "002__broken.sql",
                   "SELECT 1;\nALTER TABLE `user_badge` DROP COLUMN missing;")
        self.db.fail_on = "DROP COLUMN missing"
        self.assertEqual(self.module.apply(), 2)
        self.assertNotIn(("user", "yunanexus_user_0", 2), self.db.records)
        self.assertIn(("user", "yunanexus_user_0", 1), self.db.records)

    def test_probe_missing_is_an_error(self):
        """A silently no-op client (missing -i) must never be recorded as applied."""
        module = self.module
        calls = []

        def fake_docker(*args, **kwargs):
            calls.append(kwargs)
            return ["docker", "exec", "mysql"]

        class Result:
            returncode = 0
            stdout = ""
            stderr = ""

        module.backup = lambda: type("B", (), {
            "docker": staticmethod(fake_docker),
            "docker_env": staticmethod(dict),
            "CREDENTIALS_PATH": "/dev/shm/x.cnf"})
        module.subprocess = type("S", (), {"run": staticmethod(lambda *a, **k: Result())})
        with self.assertRaises(RuntimeError) as caught:
            self.real_run_script("SELECT 1;", "yunanexus_user_0")
        self.assertIn("not executed", str(caught.exception))
        self.assertTrue(calls and calls[0].get("stdin") is True)

    def test_whole_file_runs_in_one_session(self):
        self.write("user", "001__baseline_schema.sql",
                   'SET @present := (SELECT COUNT(*) FROM information_schema.columns);\n'
                   'SET @ddl := IF(@present = 0, "DO 0", "DO 0");\n'
                   'PREPARE guarded FROM @ddl; EXECUTE guarded;')
        self.assertEqual(self.module.apply(), 0)
        self.assertEqual(len(self.db.executed), 2)          # one call per shard
        text = self.db.executed[0][1]
        self.assertIn("SET @present", text)
        self.assertIn("PREPARE guarded", text)

    def test_failure_reports_the_offending_statement(self):
        text = "SELECT 1;\nSELECT 2;\nALTER TABLE nope ADD COLUMN x INT;\n"
        where = self.module.failing_statement(text, "mysql failed: ERROR 1064 at line 3: nope")
        self.assertIn("statement 3/3", where)
        self.assertIn("ALTER TABLE nope", where)

    def test_apply_migration_raises_with_context(self):
        self.write("user", "001__bad.sql", "SELECT 1;\nALTER TABLE nope ADD COLUMN x INT;")
        migration = self.module.discover_migrations()["user"][0]
        self.db.fail_on = "ALTER TABLE nope"
        with self.assertRaises(RuntimeError) as caught:
            self.module.apply_migration("yunanexus_user_0", migration)
        self.assertIn("yunanexus_user_0", str(caught.exception))
        self.assertIn("statement 2/2", str(caught.exception))

    def test_lock_contention_aborts(self):
        self.write("user", "001__baseline_schema.sql", "SELECT 1;")
        self.db.locked = False
        self.assertEqual(self.module.apply(), 2)
        self.assertEqual(self.db.executed, [])

    def test_dry_run_executes_nothing(self):
        self.write("user", "001__baseline_schema.sql", "SELECT 1;")
        self.assertEqual(self.module.apply(dry_run=True), 1)
        self.assertEqual(self.db.executed, [])

    def test_status_exit_code_reflects_pending(self):
        self.write("user", "001__baseline_schema.sql", "SELECT 1;")
        self.assertEqual(self.module.status(), 1)
        self.module.baseline()
        self.assertEqual(self.module.status(), 0)

    def test_forget_allows_a_re_run(self):
        self.write("user", "001__baseline_schema.sql", "SELECT 1;")
        self.module.baseline()
        self.assertEqual(self.module.forget("user", 1), 0)
        self.assertEqual(self.module.forget("user", 9), 2)          # nothing to forget
        self.assertTrue(self.module.has_blocking_work(self.module.plan()))

    def test_baseline_warns_about_missing_tables(self):
        self.write("user", "001__baseline_schema.sql",
                   "CREATE TABLE IF NOT EXISTS `user_badge` (id INT);")
        report = self.module.plan()
        entry = next(e for e in report if e["schema"] == "yunanexus_user_1")
        self.assertEqual(self.module.tables_for(entry["pending"][0], set()), {"user_badge"})
        self.assertEqual(self.module.tables_for(entry["pending"][0], {"user_badge"}), set())


if __name__ == "__main__":
    unittest.main()
