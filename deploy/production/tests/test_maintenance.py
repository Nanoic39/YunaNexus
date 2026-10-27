#!/usr/bin/env python3
"""Scheduling tests: a backup/drill must run once per period, never twice."""

import importlib.util
import os
import sys
import subprocess
import tempfile
import unittest
from unittest import mock
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def load_maintenance(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "maintenance_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "maintenance.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


    def test_hourly_migration_check_runs_once_per_interval(self):
        calls = []
        self.module.run = lambda script, *args, **kwargs: calls.append(script) or True
        self.module.trim_logs = lambda: None
        self.module.trim_cache = lambda: None
        self.module.ensure_daemon = lambda name: None
        self.module.save_state = lambda state: None
        state = {"last_migration_metrics": 0, "last_metrics": 9e9, "last_alerts": 9e9,
                 "last_backup": "2026-10-27", "last_binlog_ship": 9e9}
        self.module.load_state = lambda: state
        self.module.maintain()
        self.assertIn("migration-metrics.py", calls)
        calls.clear()
        self.module.maintain()          # interval not elapsed
        self.assertNotIn("migration-metrics.py", calls)

class ScheduleTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        (Path(self.tmp.name) / "logs").mkdir()
        self.module = load_maintenance(Path(self.tmp.name))

    def tearDown(self):
        self.tmp.cleanup()

    def test_backup_runs_only_after_0330(self):
        early = datetime(2026, 10, 28, 3, 29)
        on_time = datetime(2026, 10, 28, 3, 30)
        late = datetime(2026, 10, 28, 23, 59)
        self.assertFalse(self.module.due_backup(early, {}))
        self.assertTrue(self.module.due_backup(on_time, {}))
        self.assertTrue(self.module.due_backup(late, {}))

    def test_backup_does_not_repeat_in_same_day(self):
        now = datetime(2026, 10, 28, 9, 0)
        state = {"last_backup": "2026-10-28"}
        self.assertFalse(self.module.due_backup(now, state))
        self.assertTrue(self.module.due_backup(datetime(2026, 10, 29, 3, 30), state))

    def test_drill_only_on_sunday_after_0430(self):
        sunday = datetime(2026, 11, 1, 4, 30)          # weekday() == 6
        monday = datetime(2026, 11, 2, 4, 30)
        self.assertEqual(sunday.weekday(), 6)
        self.assertTrue(self.module.due_drill(sunday, {}))
        self.assertFalse(self.module.due_drill(monday, {}))
        self.assertFalse(self.module.due_drill(datetime(2026, 11, 1, 4, 29), {}))

    def test_drill_does_not_repeat_same_week(self):
        state = {"last_drill": "2026-11-01"}
        self.assertFalse(self.module.due_drill(datetime(2026, 11, 1, 12, 0), state))
        self.assertTrue(self.module.due_drill(datetime(2026, 11, 8, 4, 30), state))

    def test_failed_attempt_backs_off(self):
        now = datetime(2026, 10, 28, 9, 0)
        state = {"last_backup_attempt": self.module.time.time() - 60}
        self.assertFalse(self.module.due_backup(now, state))
        state = {"last_backup_attempt": self.module.time.time() - 1801}
        self.assertTrue(self.module.due_backup(now, state))

    def test_drill_failure_backs_off(self):
        sunday = datetime(2026, 11, 1, 9, 0)
        state = {"last_drill_attempt": self.module.time.time() - 60}
        self.assertFalse(self.module.due_drill(sunday, state))

    def test_pitr_drill_monthly_on_first_sunday(self):
        first_sunday = datetime(2026, 11, 1, 5, 0)
        later_sunday = datetime(2026, 11, 15, 5, 0)
        self.assertTrue(self.module.due_pitr(first_sunday, {}))
        self.assertFalse(self.module.due_pitr(later_sunday, {}))
        self.assertFalse(self.module.due_pitr(datetime(2026, 11, 1, 4, 59), {}))
        once = {"last_pitr_drill": "2026-11-01"}
        self.assertFalse(self.module.due_pitr(datetime(2026, 11, 1, 23, 0), once))
        self.assertTrue(self.module.due_pitr(datetime(2026, 12, 6, 5, 0), once))

    def test_dead_daemon_is_restarted(self):
        runtime = Path(self.tmp.name) / "runtime"
        runtime.mkdir(exist_ok=True)
        (runtime / "logship.pid").write_text("999999")
        ops = Path(self.tmp.name) / "ops"
        ops.mkdir(exist_ok=True)
        (ops / "logship.py").write_text("")
        started = []
        # The module shares the global subprocess object, so the patch must be undone:
        # leaving it in place broke subprocess.run for every later test module.
        with mock.patch.object(subprocess, "Popen",
                               lambda *a, **k: started.append(a) or type(
                                   "P", (), {"pid": 5150})()):
            self.assertTrue(self.module.ensure_daemon("logship"))
        self.assertEqual(len(started), 1)
        self.assertEqual((runtime / "logship.pid").read_text(), "5150")

    def test_missing_daemon_script_is_not_fatal(self):
        runtime = Path(self.tmp.name) / "runtime"
        runtime.mkdir(exist_ok=True)
        self.assertFalse(self.module.ensure_daemon("logship"))

    def test_state_survives_restart(self):
        self.module.save_state({"last_backup": "2026-10-28"})
        reloaded = load_maintenance(Path(self.tmp.name))
        self.assertEqual(reloaded.load_state()["last_backup"], "2026-10-28")

    def test_corrupt_state_is_not_fatal(self):
        (Path(self.tmp.name) / "runtime").mkdir(exist_ok=True)
        (Path(self.tmp.name) / "runtime/maintenance-state.json").write_text("{not json")
        self.assertEqual(self.module.load_state(), {})


if __name__ == "__main__":
    unittest.main(verbosity=2)
