#!/usr/bin/env python3
"""Capacity: trend fitting, forecast gating, deleted-file detection, metric shape."""

import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def load(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "capacity_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "capacity.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.WORK = Path(root) / "runtime/capacity"
    module.HISTORY = module.WORK / "history.jsonl"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/capacity.prom"
    return module


def snap(at, avail, used=0, directories=None, deleted=0):
    return {"at": at, "filesystems": {"/": {"available": avail, "used": used,
                                            "total": avail + used}},
            "directories": directories or {}, "deleted_open": deleted}


class SlopeTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_flat_series_has_no_slope_direction(self):
        points = [(1000, 100.0), (1000 + 3600, 100.0), (1000 + 7200, 100.0)]
        self.assertAlmostEqual(self.module.slope(points), 0.0)

    def test_known_rate_is_recovered(self):
        # losing 1 MiB per hour = 24 MiB per day
        step = self.module.MIB
        points = [(0, 1000.0), (3600, 1000.0 - step), (7200, 1000.0 - 2 * step)]
        self.assertAlmostEqual(self.module.slope(points), -24 * step, delta=1)

    def test_two_points_are_not_a_trend(self):
        self.assertIsNone(self.module.slope([(0, 100.0), (7200, 50.0)]))

    def test_short_span_is_not_a_trend(self):
        points = [(0, 100.0), (60, 90.0), (120, 80.0)]
        self.assertIsNone(self.module.slope(points))

    def test_identical_timestamps_do_not_divide_by_zero(self):
        self.assertIsNone(self.module.slope([(0, 1.0), (0, 2.0), (0, 3.0)]))


class RobustSlopeTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_single_cleanup_does_not_become_the_trend(self):
        """A one-off 2 GiB deletion in the middle must not dominate the fit."""
        gib = self.module.GIB
        points = [(0, 20 * gib), (3600, 20 * gib - self.module.MIB),
                  (7200, 20 * gib - 2 * self.module.MIB + 2 * gib),
                  (10800, 20 * gib - 3 * self.module.MIB + 2 * gib)]
        robust = self.module.robust_slope(points)
        least_squares = self.module.slope(points)
        self.assertAlmostEqual(robust, -24 * self.module.MIB, delta=2048)
        self.assertNotAlmostEqual(least_squares, robust, delta=1024)

    def test_steady_decline_is_recovered(self):
        gib = self.module.GIB
        points = [(index * 3600, 20 * gib - index * gib) for index in range(4)]
        self.assertAlmostEqual(self.module.robust_slope(points), -24 * gib, delta=1)

    def test_thin_history_still_yields_nothing(self):
        self.assertIsNone(self.module.robust_slope([(0, 1), (3600, 2)]))

    def test_one_hour_of_points_is_enough(self):
        points = [(index * 300, 1000 - index * 10) for index in range(13)]
        self.assertIsNotNone(self.module.robust_slope(points))


class ForecastTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_days_to_full_from_shrinking_filesystem(self):
        # 10 GiB free, reserve 512 MiB, losing 1 GiB/day
        days = self.module.days_to_full(10 * self.module.GIB, -self.module.GIB)
        self.assertAlmostEqual(days, 9.5, places=1)

    def test_growing_or_flat_filesystem_never_fills(self):
        self.assertIsNone(self.module.days_to_full(self.module.GIB, 0.0))
        self.assertIsNone(self.module.days_to_full(self.module.GIB, 1024.0))

    def test_unknown_rate_is_unknown_forecast(self):
        self.assertIsNone(self.module.days_to_full(self.module.GIB, None))

    def test_already_below_reserve_is_zero_not_negative(self):
        self.assertEqual(self.module.days_to_full(self.module.MIB, -self.module.MIB), 0.0)

    def test_evaluate_prefers_history_and_falls_back_to_backend(self):
        history = [snap(0, 20 * self.module.GIB), snap(3600, 19 * self.module.GIB),
                   snap(7200, 18 * self.module.GIB)]
        called = []

        def backend(mountpoint, window):
            called.append(mountpoint)
            return -2 * self.module.GIB

        states = self.module.evaluate(history, backend=backend)
        root = next(state for state in states if state["target"] == "/")
        self.assertEqual(root["source"], "history")
        self.assertNotIn("/", called, "the backend must not be consulted when history works")
        # 10 GiB lost per 2 hours is a real, fast trend: a number, not a guess
        self.assertIsNotNone(root["days_to_full"])
        self.assertGreater(root["days_to_full"], 0)

    def test_backend_series_is_parsed_into_points(self):
        import json as json_module

        class Response:
            def read(self):
                return json_module.dumps({"data": {"result": [
                    {"values": [[1000, "500"], [1300, "480"], [1600, "460"]]}]}}).encode()

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

        self.module.urllib.request.urlopen = lambda url, timeout=None: Response()
        points = self.module.backend_series("/", "1h")
        self.assertEqual([value for _, value in points], [500.0, 480.0, 460.0])

    def test_backend_failure_returns_no_points(self):
        def broken(url, timeout=None):
            raise OSError("backend down")

        self.module.urllib.request.urlopen = broken
        self.assertEqual(self.module.backend_series("/", "1h"), [])
        self.assertIsNone(self.module.backend_slope("/", "1h"))

    def test_backend_is_used_when_history_is_thin(self):
        history = [snap(7200, 18 * self.module.GIB)]
        states = self.module.evaluate(
            history, backend=lambda mountpoint, window: -3 * self.module.GIB)
        root = next(state for state in states if state["target"] == "/")
        self.assertEqual(root["source"], "backend")
        self.assertAlmostEqual(root["growth_per_day"], -3 * self.module.GIB)

    def test_directory_projection_needs_a_rate(self):
        history = [snap(0, 100, directories={"logs": 1000}),
                   snap(3600, 100, directories={"logs": 2000}),
                   snap(7200, 100, directories={"logs": 3000})]
        states = self.module.evaluate(history, backend=lambda *a: None)
        logs = next(state for state in states if state["target"] == "logs")
        # 1000 bytes per hour is 24000 bytes per day
        self.assertAlmostEqual(logs["growth_per_day"], 24000.0, delta=2)
        self.assertGreater(logs["projected_30d"], logs["used"])


class DeletedOpenTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_regular_deleted_files_are_counted(self):
        entries = [("/var/log/app.log (deleted)", 5 * self.module.GIB),
                   ("/tmp/x (deleted)", 100)]
        self.assertEqual(self.module.deleted_open_bytes(entries),
                         5 * self.module.GIB + 100)

    def test_memfd_and_shared_memory_are_ignored(self):
        """A 2 TB memfd has nothing to do with free disk space."""
        entries = [("/memfd:doublemapper (deleted)", 2 * 1024 ** 4),
                   ("/dev/shm/.chromium (deleted)", 262144),
                   ("/proc/self/fd/3 (deleted)", 4096),
                   ("/sys/kernel/x (deleted)", 8192)]
        self.assertEqual(self.module.deleted_open_bytes(entries), 0)

    def test_live_files_are_ignored(self):
        self.assertEqual(self.module.deleted_open_bytes([("/var/log/app.log", 999)]), 0)


class SeedTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_seeding_writes_point_per_timestamp(self):
        def fake_series(mountpoint, window="24h", step=300, timeout=20):
            return [(1000, 500.0), (1300, 480.0), (1600, 460.0)]

        self.module.backend_series = fake_series
        count = self.module.seed_from_backend(mounts=[("root", "/")])
        self.assertEqual(count, 3)
        records = self.module.load_history(now=2000, days=30)
        self.assertEqual(len(records), 3)
        self.assertTrue(records[0]["seeded"])
        self.assertEqual(records[0]["filesystems"]["root"]["available"], 500)

    def test_seeding_an_empty_backend_writes_nothing(self):
        self.module.backend_series = lambda *a, **k: []
        self.assertEqual(self.module.seed_from_backend(mounts=[("root", "/")]), 0)


class HistoryTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_history_round_trips(self):
        self.module.append_history(snap(1000, 10))
        self.module.append_history(snap(2000, 20))
        records = self.module.load_history(now=2000)
        self.assertEqual([record["at"] for record in records], [1000, 2000])

    def test_old_snapshots_fall_out_of_the_window(self):
        self.module.append_history(snap(1000, 10))
        self.module.append_history(snap(10 ** 6, 20))
        records = self.module.load_history(now=10 ** 6, days=1)
        self.assertEqual([record["at"] for record in records], [10 ** 6])

    def test_corrupt_lines_are_skipped_not_fatal(self):
        path = self.module.append_history(snap(1000, 10))
        with path.open("a", encoding="utf-8") as sink:
            sink.write("{not json\n")
        self.assertEqual(len(self.module.load_history(now=1000)), 1)

    def test_trim_keeps_the_window(self):
        for index in range(5):
            self.module.append_history(snap(index * 86400, 10 + index))
        self.module.trim_history(now=4 * 86400, days=2)
        self.assertEqual(len(self.module.load_history(now=4 * 86400, days=30)), 3)


class MetricsTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def states(self):
        history = [snap(0, 20 * self.module.GIB), snap(3600, 19 * self.module.GIB),
                   snap(7200, 18 * self.module.GIB)]
        return self.module.evaluate(history, backend=lambda *a: None)

    def test_metrics_carry_target_and_kind(self):
        text = self.module.render_metrics(self.states(), deleted_open=123, now=5)
        self.assertIn('yunanexus_capacity_used_bytes{target="/",kind="filesystem"}', text)
        self.assertIn("yunanexus_capacity_deleted_open_bytes 123", text)
        self.assertIn("yunanexus_capacity_timestamp_seconds 5", text)

    def test_forecast_ready_is_published_per_target(self):
        text = self.module.render_metrics(self.states(), now=1)
        self.assertIn('yunanexus_capacity_forecast_ready{target="/"} 1', text)
        self.assertIn('yunanexus_capacity_forecast_ready{target="backups"} 0', text)

    def test_failure_is_visible(self):
        self.assertIn("yunanexus_capacity_success 0",
                      self.module.render_metrics([], ok=False, now=1))

    def test_table_marks_missing_forecast(self):
        table = self.module.render_table(self.states())
        self.assertIn("filesystem", table)


if __name__ == "__main__":
    unittest.main()
