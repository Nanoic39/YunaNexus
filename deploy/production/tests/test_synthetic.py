#!/usr/bin/env python3
"""Synthetic probes: failure classification, state-change logging, metric shape."""

import importlib.util
import json
import os
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def load(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "synthetic_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "synthetic.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/probes.prom"
    module.RESULTS = Path(root) / "logs/synthetic.jsonl"
    return module


class FakeResponse:
    def __init__(self, status=200, body=b"ok"):
        self.status = status
        self._body = body

    def read(self, size=None):
        return self._body if size is None else self._body[:size]

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class ProbeTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())
        self.target = {"name": "portal", "url": "http://example.test/", "expect": (200,),
                       "path": "public"}

    def test_success_records_status_and_duration(self):
        record = self.module.probe(self.target, opener=lambda request, timeout: FakeResponse())
        self.assertTrue(record["ok"])
        self.assertEqual(record["status"], 200)
        self.assertGreaterEqual(record["duration"], 0)

    def test_unexpected_status_is_a_failure(self):
        record = self.module.probe(self.target,
                                   opener=lambda request, timeout: FakeResponse(status=502))
        self.assertFalse(record["ok"])
        self.assertIn("502", record["error"])

    def test_http_error_is_captured_not_raised(self):
        def opener(request, timeout):
            raise urllib.error.HTTPError("http://x", 503, "unavailable", {}, None)

        record = self.module.probe(self.target, opener=opener)
        self.assertFalse(record["ok"])
        self.assertEqual(record["status"], 503)

    def test_connection_refused_is_named(self):
        def opener(request, timeout):
            raise urllib.error.URLError(ConnectionRefusedError(111, "Connection refused"))

        record = self.module.probe(self.target, opener=opener)
        self.assertEqual(record["error"], "connection refused")
        self.assertIsNone(record["status"])

    def test_dns_failure_is_named(self):
        self.assertEqual(self.module.classify("Name or service not known"), "dns failure")

    def test_timeout_is_named(self):
        def opener(request, timeout):
            raise urllib.error.URLError("timed out")

        record = self.module.probe(self.target, opener=opener)
        self.assertEqual(record["error"], "timeout")

    def test_expected_error_code_counts_as_success(self):
        target = dict(self.target, expect=(404,))
        record = self.module.probe(target,
                                   opener=lambda request, timeout: FakeResponse(status=404))
        self.assertTrue(record["ok"])


class PublishTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())
        self.records = [
            {"target": "portal", "path": "public", "ok": True, "status": 200, "duration": 0.12},
            {"target": "admin", "path": "public", "ok": False, "status": 0, "duration": 8.0},
        ]

    def test_metrics_carry_target_and_status(self):
        lines = self.module.samples(self.records)
        self.assertIn('yunanexus_probe_up{target="portal",path="public"} 1', lines)
        self.assertIn('yunanexus_probe_up{target="admin",path="public"} 0', lines)
        self.assertIn('yunanexus_probe_status_code{target="admin"} 0', lines)

    def test_textfile_is_replaced_atomically(self):
        path = self.module.publish(self.records, now=500)
        text = path.read_text(encoding="utf-8")
        self.assertIn("yunanexus_probe_last_run_timestamp_seconds 500", text)
        self.assertIn("yunanexus_probe_targets 2", text)

    def test_certificate_metric_only_appears_for_https(self):
        plain = self.module.publish(self.records, now=1).read_text(encoding="utf-8")
        self.assertNotIn("cert_days_remaining", plain)
        records = [dict(self.records[0], cert_days=41)]
        secure = self.module.publish(records, now=1).read_text(encoding="utf-8")
        self.assertIn('yunanexus_probe_cert_days_remaining{target="portal"} 41', secure)

    def test_results_append_as_jsonl(self):
        path = self.module.append_results(self.records)
        self.module.append_results(self.records)
        lines = path.read_text(encoding="utf-8").strip().splitlines()
        self.assertEqual(len(lines), 4)
        self.assertEqual(json.loads(lines[0])["target"], "portal")


class TransitionTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def records(self, portal_ok, admin_ok):
        return [
            {"target": "portal", "ok": portal_ok, "status": 200, "duration": 0.1, "error": ""},
            {"target": "admin", "ok": admin_ok, "status": 200, "duration": 0.1, "error": ""},
        ]

    def test_first_cycle_logs_nothing(self):
        self.assertEqual(self.module.transitions([], self.records(True, True)), [])

    def test_going_down_is_one_event(self):
        events = self.module.transitions(self.records(True, False), self.records(False, False))
        self.assertEqual([(direction, record["target"]) for direction, record in events],
                         [("down", "portal")])

    def test_recovery_is_also_one_event(self):
        events = self.module.transitions(self.records(False, True), self.records(True, True))
        self.assertEqual([(direction, record["target"]) for direction, record in events],
                         [("up", "portal")])

    def test_steady_failure_does_not_repeat(self):
        """A permanently broken target must not log every minute."""
        events = self.module.transitions(self.records(False, True), self.records(False, True))
        self.assertEqual(events, [])


if __name__ == "__main__":
    unittest.main()
