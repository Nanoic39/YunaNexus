#!/usr/bin/env python3
"""Migration state metrics: rendering, failure reporting and atomic writes."""

import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def load(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "migration_metrics_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "migration-metrics.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    return module


class MigrationMetricsTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        (self.root / "runtime/node_exporter_textfile").mkdir(parents=True)
        self.module = load(self.root)

    def test_render_reports_counts_per_schema(self):
        report = [
            {"service": "user", "schema": "yunanexus_user_0", "state": "pending", "pending": [2, 3]},
            {"service": "user", "schema": "yunanexus_user_1", "state": "drift", "drift": [1]},
            {"service": "audit", "schema": None, "state": "no-schema", "pending": [1]},
        ]
        text = self.module.render(report, True, now=1000)
        self.assertIn('yunanexus_migration_pending{service="user",schema="yunanexus_user_0"} 2', text)
        self.assertIn('yunanexus_migration_drift{service="user",schema="yunanexus_user_1"} 1', text)
        self.assertIn("yunanexus_migration_check_success 1", text)
        self.assertIn("yunanexus_migration_check_timestamp_seconds 1000", text)
        self.assertNotIn("audit", text)          # no schema yet: nothing to count

    def test_failed_check_still_exports_a_timestamp(self):
        text = self.module.render(None, False, now=2000)
        self.assertIn("yunanexus_migration_check_success 0", text)
        self.assertIn("yunanexus_migration_check_timestamp_seconds 2000", text)

    def test_write_is_atomic_and_world_readable(self):
        target = self.module.write("a 1\n")
        self.assertEqual(target.read_text(), "a 1\n")
        self.assertEqual(target.stat().st_mode & 0o777, 0o644)
        self.assertFalse(target.with_suffix(".prom.tmp").exists())

    def test_collect_reports_failure_on_bad_exit(self):
        script = self.root / "ops/migrate.py"
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_text("import sys; sys.stderr.write('boom'); sys.exit(3)\n")
        report, ok = self.module.collect()
        self.assertFalse(ok)
        self.assertIsNone(report)

    def test_collect_parses_json_and_accepts_exit_one(self):
        script = self.root / "ops/migrate.py"
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_text("import json, sys; print(json.dumps([{'service': 'user', "
                          "'schema': 'yunanexus_user_0', 'state': 'pending', 'pending': [2], "
                          "'drift': []}])); sys.exit(1)\n")
        report, ok = self.module.collect()
        self.assertTrue(ok)
        self.assertEqual(report[0]["state"], "pending")


if __name__ == "__main__":
    unittest.main()
