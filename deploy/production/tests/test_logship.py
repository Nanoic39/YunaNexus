#!/usr/bin/env python3
"""Log collector: line parsing, de-duplication, rotation and metric rendering."""

import gzip
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


def load():
    root = tempfile.mkdtemp()
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = root
    module_name = "logship_%s" % abs(hash(root))
    spec = importlib.util.spec_from_file_location(module_name, PRODUCTION / "logship.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    module.STATE_PATH = Path(root) / "runtime/logship-state.json"
    module.OUTPUT_DIR = Path(root) / "logs/aggregated"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/logs.prom"
    return module


SAMPLE = (
    "2026-10-27T16:43:23.243123456+08:00  INFO 1 --- [yunanexus-auth] [nio-8101-exec-1] c.Foo : started\n"
    "2026-10-27T16:43:24.000000000+08:00 ERROR 1 --- [yunanexus-auth] [nio-8101-exec-2] c.Foo : boom\n"
    "2026-10-27T16:43:25.000000000+08:00  WARN 1 --- [yunanexus-auth] [nio-8101-exec-3] c.Foo : slow\n"
)


class LogShipTests(unittest.TestCase):
    def setUp(self):
        self.module = load()

    def test_service_names(self):
        self.assertEqual(self.module.service_name("yunanexus-production-auth-1"), "auth")
        self.assertEqual(self.module.service_name("yunanexus-monitoring-grafana-1"), "monitoring-grafana")

    def test_spring_levels_detected(self):
        self.assertEqual(self.module.detect_level("2026-10-27T16:43:23  INFO 1 --- [x] c.Foo : ok"), "INFO")
        self.assertEqual(self.module.detect_level("2026-10-27T16:43:23 ERROR 1 --- [x] c.Foo : bad"), "ERROR")
        self.assertEqual(self.module.detect_level("plain line without level"), "UNKNOWN")
        self.assertEqual(self.module.detect_level('{"level":"warn","msg":"x"}'), "WARN")

    def test_keyword_mentions_are_not_levels(self):
        # Real false positives found in production logs.
        banner = ("Picked up JAVA_TOOL_OPTIONS: -Xmx112m -XX:+HeapDumpOnOutOfMemoryError "
                  "-XX:ActiveProcessorCount=2")
        self.assertEqual(self.module.detect_level(banner), "UNKNOWN")
        access = ('172.29.10.1 [27/Oct/2026:08:54:28 +0000] "GET /assets/handle-error-BJX.js '
                  'HTTP/1.1" 200 236')
        self.assertEqual(self.module.detect_level(access), "INFO")

    def test_access_log_status_codes(self):
        def line(status):
            return '1.2.3.4 - - "GET /api/v1/status HTTP/1.1" %d 12' % status
        self.assertEqual(self.module.detect_level(line(200)), "INFO")
        self.assertEqual(self.module.detect_level(line(404)), "WARN")
        self.assertEqual(self.module.detect_level(line(502)), "ERROR")

    def test_bracketed_levels(self):
        self.assertEqual(self.module.detect_level("[ERROR] [MY-010119] [Server] Aborting"), "ERROR")
        self.assertEqual(self.module.detect_level("[Warning] [MY-010055] [Server] x"), "WARN")
        self.assertEqual(self.module.detect_level("[Note] [MY-010116] [Server] x"), "INFO")

    def test_spring_level_requires_the_full_prefix(self):
        self.assertEqual(self.module.detect_level("noise INFO 1 --- [x] c.Foo : ok"), "INFO")
        self.assertEqual(self.module.detect_level("just mentions INFO here"), "UNKNOWN")

    def test_docker_nanosecond_timestamps_parse(self):
        from datetime import datetime, timezone
        expected = datetime(2026, 10, 27, 9, 1, 6, 894134, tzinfo=timezone.utc).timestamp()
        # Exactly what `docker logs --timestamps` emits: UTC with nanoseconds.
        self.assertAlmostEqual(
            self.module.parse_timestamp("2026-10-27T09:01:06.894134540Z"), expected, places=3)
        local = self.module.parse_timestamp("2026-10-27T17:01:06.893+08:00")
        self.assertAlmostEqual(local, 1793091666.893, places=2)
        self.assertIsNone(self.module.parse_timestamp("not-a-time"))

    def test_collect_appends_lines_newer_than_watermark(self):
        payload = (
            "2026-10-27T09:01:06.894134540Z 2026-10-27T17:01:06.893+08:00  INFO 1 --- [x] : hello\n"
        )
        watermark = self.module.parse_timestamp("2026-10-27T09:00:00.000000000Z")
        entries = list(self.module.parse_lines(payload, watermark))
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0][2], "INFO")

    def test_since_is_sent_with_utc_offset(self):
        commands = []
        self.module.docker_containers = lambda: ["yunanexus-production-auth-1"]
        self.module.save_state = lambda state: None

        def fake_docker(*args, **kwargs):
            commands.append(args)
            return type("R", (), {"returncode": 0, "stdout": "", "stderr": ""})()

        self.module.docker = fake_docker
        state = {"yunanexus-production-auth-1": {
            "last_ts": 1793091600.0, "counts": {}, "lines": 0, "bytes": 0,
            "last_line": 1793091600.0, "initialised": True}}
        self.module.collect(state)
        logs_call = next(call for call in commands if call[0] == "logs")
        since = logs_call[logs_call.index("--since") + 1]
        # A naive value would be read as UTC by docker and land in the future.
        self.assertTrue(since.endswith("+00:00"), since)

    def test_second_cycle_actually_collects(self):
        """A zero watermark must not be mistaken for "never seen this container"."""
        calls = []
        self.module.docker_containers = lambda: ["yunanexus-production-auth-1"]
        self.module.save_state = lambda state: None
        payload = "2026-10-27T09:01:06.894134540Z 2026-10-27T17:01:06 IST INFO hello\n"

        def fake_docker(*args, **kwargs):
            calls.append(args)
            if args[0] == "logs":
                return type("R", (), {"returncode": 0, "stdout": payload, "stderr": ""})()
            return type("R", (), {"returncode": 0, "stdout": "", "stderr": ""})()

        self.module.docker = fake_docker
        state = {}
        self.module.collect(state, now=1793092000.0)          # first sighting: backfill only
        self.assertFalse([c for c in calls if c[0] == "logs"])
        self.module.collect(state, now=1793092010.0)          # second cycle must fetch
        self.assertTrue([c for c in calls if c[0] == "logs"])
        self.assertEqual(state["yunanexus-production-auth-1"]["lines"], 1)

    def test_quiet_container_does_not_duplicate_lines(self):
        payload = "2026-10-27T09:01:06.894134540Z hello once\n"

        def fake_docker(*args, **kwargs):
            if args[0] == "logs":
                return type("R", (), {"returncode": 0, "stdout": payload, "stderr": ""})()
            return type("R", (), {"returncode": 0, "stdout": "", "stderr": ""})()

        self.module.docker_containers = lambda: ["yunanexus-production-auth-1"]
        self.module.save_state = lambda state: None
        self.module.docker = fake_docker
        state = {"yunanexus-production-auth-1": {
            "last_ts": 1793091000.0, "counts": {}, "lines": 0, "bytes": 0,
            "last_line": 0.0, "initialised": True}}
        for cycle in range(3):
            self.module.collect(state, now=1793092000.0 + cycle * 10)
        self.assertEqual(state["yunanexus-production-auth-1"]["lines"], 1)

    def test_parse_lines_respects_last_timestamp(self):
        entries = list(self.module.parse_lines(SAMPLE, 0))
        self.assertEqual([entry[2] for entry in entries], ["INFO", "ERROR", "WARN"])
        newer = list(self.module.parse_lines(SAMPLE, entries[0][0]))
        self.assertEqual(len(newer), 2)   # the first line must not be replayed

    def test_append_and_metrics(self):
        entries = list(self.module.parse_lines(SAMPLE, 0))
        self.assertEqual(self.module.append_jsonl("auth", entries), 3)
        path = next(self.module.OUTPUT_DIR.rglob("*.jsonl"))
        first = json.loads(path.read_text().splitlines()[0])
        self.assertEqual(first["service"], "auth")
        self.assertEqual(first["level"], "INFO")
        state = {"yunanexus-production-auth-1": {
            "last_ts": entries[-1][0], "counts": {"INFO": 1, "ERROR": 1, "WARN": 1},
            "lines": 3, "bytes": 30, "last_line": entries[-1][0]}}
        text = self.module.render_metrics(state, now=1)
        self.assertIn('yunanexus_log_lines_total{service="auth",level="ERROR"} 1', text)
        self.assertIn('yunanexus_log_last_line_timestamp_seconds{service="auth"}', text)

    def test_rotation_compresses_and_expires(self):
        directory = self.module.OUTPUT_DIR / "auth"
        directory.mkdir(parents=True)
        old = directory / "2026-10-01.jsonl"
        old.write_text('{"epoch":1}\n')
        ancient = directory / "2026-09-01.jsonl"
        ancient.write_text('{"epoch":0}\n')
        now = time.time()
        os.utime(old, (now - 72 * 3600, now - 72 * 3600))
        os.utime(ancient, (now - 30 * 86400, now - 30 * 86400))
        self.module.rotate(now)
        self.assertFalse(old.exists())
        self.assertTrue((directory / "2026-10-01.jsonl.gz").exists())
        # An expired file must be dropped even though compression re-created it.
        self.assertFalse(ancient.exists())
        self.assertFalse((directory / "2026-09-01.jsonl.gz").exists())


if __name__ == "__main__":
    unittest.main()
