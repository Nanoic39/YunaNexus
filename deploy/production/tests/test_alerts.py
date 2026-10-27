#!/usr/bin/env python3
"""Alert rule evaluation: matching, local checks and notification de-duplication."""

import importlib.util
import json
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def load_alerts(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "alerts_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "alerts.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


class AlertTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        for relative in ["logs", "runtime", "backups", "config"]:
            (self.root / relative).mkdir()
        self.module = load_alerts(self.root)
        self.module.STATE = self.root / "runtime/alerts-state.json"
        self.module.LOG_FILE = self.root / "logs/alerts.log"
        self.module.WEBHOOK = ""
        self.delivered = []
        self.module.deliver = lambda alert, status: self.delivered.append((status, alert["name"]))

    def tearDown(self):
        self.tmp.cleanup()

    def test_comparisons(self):
        self.assertTrue(self.module.matches("<", 300, 299.9))
        self.assertFalse(self.module.matches("<", 300, 300))
        self.assertTrue(self.module.matches(">", 90, 97.9))
        self.assertTrue(self.module.matches("==", 0, 0))
        self.assertFalse(self.module.matches("==", 0, 1))
        with self.assertRaises(ValueError):
            self.module.matches("!=", 1, 1)

    def test_local_check_flags_stale_backup(self):
        manifest = self.root / "backups/20260101-010101/manifest.json"
        manifest.parent.mkdir()
        manifest.write_text("{}")
        old = time.time() - 30 * 3600
        os.utime(manifest, (old, old))
        findings = self.module.local_checks(time.time())
        self.assertEqual([f[0]["check"] for f in findings], ["backup_stale"])

    def test_local_check_flags_missing_backup(self):
        findings = self.module.local_checks(time.time())
        self.assertEqual([f[0]["check"] for f in findings], ["backup_missing"])

    def test_local_check_flags_blocked_deploy(self):
        manifest = self.root / "backups/20260101-010101/manifest.json"
        manifest.parent.mkdir()
        manifest.write_text("{}")
        (self.root / "runtime/cd-state.json").write_text(
            json.dumps({"backend": {"blocked": True, "attempts": 5}}))
        findings = self.module.local_checks(time.time())
        self.assertIn("deploy_blocked", [f[0]["check"] for f in findings])

    def test_backend_outage_is_itself_an_alert(self):
        def broken(expression):
            raise OSError("connection refused")

        self.module.query = broken
        manifest = self.root / "backups/20260101-010101/manifest.json"
        manifest.parent.mkdir()
        manifest.write_text("{}")
        firing = self.module.evaluate(time.time())
        self.assertIn("monitoring_backend_down", [alert["name"] for alert in firing.values()])

    def test_counter_rules_wait_for_settling(self):
        """A collector restart resets counters; increase() then reports a fake spike."""
        self.module.SETTLE = {"log_error_spike": ("yunanexus_logship_uptime_seconds", 1800)}
        self.assertFalse(self.module.settled(
            "log_error_spike", {"yunanexus_logship_uptime_seconds": 60.0}))
        self.assertTrue(self.module.settled(
            "log_error_spike", {"yunanexus_logship_uptime_seconds": 3600.0}))
        self.assertTrue(self.module.settled("host_memory_low", {}))

        def query(expression):
            if "log_lines" in expression:
                return [{"metric": {"service": "edge"}, "value": [0, "6"]}]
            if "uptime" in expression:
                return [{"metric": {}, "value": [0, "10"]}]
            return []

        self.module.query = query
        firing = self.module.evaluate(time.time())
        self.assertNotIn("log_error_spike", [alert["name"] for alert in firing.values()])

    def test_rule_hits_produce_alerts(self):
        self.module.query = lambda expression: [{
            "metric": {"mountpoint": "/", "__name__": "node_filesystem_avail_bytes"},
            "value": [0, "0.4"],
        }]
        firing = self.module.evaluate(time.time())
        self.assertTrue(firing)
        sample = next(iter(firing.values()))
        self.assertIn(sample["severity"], ("critical", "warning"))
        self.assertNotIn("__name__", sample["labels"])

    def test_rule_miss_produces_nothing(self):
        # A fresh backup keeps the local checks quiet so only rule hits remain.
        manifest = self.root / "backups/20260101-010101/manifest.json"
        manifest.parent.mkdir()
        manifest.write_text("{}")
        def query(expression):
            # Memory is healthy, every other rule returns no series at all.
            if "MemAvailable" in expression:
                return [{"metric": {}, "value": [0, "900"]}]
            return []

        self.module.query = query
        self.assertEqual(self.module.evaluate(time.time()), {})

    def test_repeat_notifications_are_suppressed(self):
        self.module.query = lambda expression: [{
            "metric": {"mountpoint": "/"}, "value": [0, "0.4"],
        }]
        self.module.main()
        first = len(self.delivered)
        self.assertGreaterEqual(first, 1)
        self.module.main()
        self.assertEqual(len(self.delivered), first)   # still firing, inside the repeat window

    def test_recovery_sends_resolved_once(self):
        self.module.query = lambda expression: [{
            "metric": {"mountpoint": "/"}, "value": [0, "0.4"],
        }]
        self.module.main()
        self.module.query = lambda expression: []
        self.module.main()
        resolved = [entry for entry in self.delivered if entry[0] == "resolved"]
        self.assertTrue(resolved)
        self.module.main()
        self.assertEqual(len([e for e in self.delivered if e[0] == "resolved"]), len(resolved))


class MultiSeriesRuleTests(unittest.TestCase):
    def test_severity_scoped_metric_is_filtered_in_the_query(self):
        """A finding count must carry its severity in the selector, or the rule fires
        once per severity - including the harmless ones."""
        module = load_alerts(tempfile.mkdtemp())
        scoped = [rule for rule in module.RULES
                  if rule["query"].startswith("yunanexus_db_findings")]
        self.assertTrue(scoped)
        for rule in scoped:
            self.assertIn("{severity=", rule["query"],
                          "%s selects every severity and will fire on the wrong one"
                          % rule["name"])


if __name__ == "__main__":
    unittest.main()
