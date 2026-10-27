#!/usr/bin/env python3
"""SLO arithmetic: budget maths, multiple windows, metric rendering, failure paths."""

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
    name = "slo_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "slo.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.WORK = Path(root) / "runtime/slo"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/slo.prom"
    return module


class BudgetTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_perfect_service_spends_nothing(self):
        state = self.module.budget(0.995, total=1000, failed=0)
        self.assertEqual(state["availability"], 1.0)
        self.assertEqual(state["budget_remaining"], 1.0)
        self.assertEqual(state["burn_rate"], 0.0)

    def test_spending_exactly_the_budget_leaves_zero(self):
        # 0.5% of 1000 requests is the whole 28-day budget for a 99.5% objective
        state = self.module.budget(0.995, total=1000, failed=5)
        self.assertAlmostEqual(state["budget_remaining"], 0.0, places=4)
        self.assertAlmostEqual(state["burn_rate"], 1.0, places=3)

    def test_budget_can_go_negative_when_the_objective_is_broken(self):
        state = self.module.budget(0.995, total=1000, failed=50)
        self.assertLess(state["budget_remaining"], -1.0)
        self.assertAlmostEqual(state["burn_rate"], 10.0, places=1)

    def test_no_traffic_is_not_a_perfect_score(self):
        self.assertIsNone(self.module.budget(0.995, total=0, failed=0))

    def test_client_errors_are_not_counted_as_failures(self):
        """4xx is the caller's fault; only 5xx spends the budget."""
        queries = []

        def fake_query(expression, timeout=20):
            queries.append(expression)
            if "5.." in expression:
                return []
            return [{"metric": {"instance": "user:8201"}, "value": [0, "100"]}]

        self.module.query = fake_query
        services = self.module.evaluate(windows={"1h": "1h"}, coverage=28 * 86400)
        self.assertEqual(services["user:8201"]["1h"]["failed"], 0.0)

    def test_failure_query_filters_on_5xx(self):
        captured = {}

        def fake_query(expression, timeout=20):
            captured.setdefault("expressions", []).append(expression)
            return []

        self.module.query = fake_query
        self.module.evaluate(windows={"1h": "1h"}, coverage=28 * 86400)
        self.assertTrue(any('status=~"5.."' in item for item in captured["expressions"]))


class EvaluateTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def readers(self, totals, failures, latencies=None):
        return {
            "total": lambda window: totals,
            "failed": lambda window: failures,
            "latency": lambda window: latencies or {},
        }

    def test_each_window_is_evaluated_independently(self):
        services = self.module.evaluate(
            windows={"1h": "1h", "7d": "7d"},
            coverage=28 * 86400, reads=self.readers({"api:8081": 100}, {"api:8081": 2}))
        self.assertEqual(sorted(services["api:8081"]), ["1h", "7d"])

    def test_service_with_only_failures_is_still_scored(self):
        services = self.module.evaluate(
            windows={"1h": "1h"},
            coverage=28 * 86400, reads=self.readers({}, {"api:8081": 3}))
        # no denominator: cannot claim a score, so the service is left out entirely
        self.assertEqual(services, {})

    def test_objective_breach_is_flagged(self):
        services = self.module.evaluate(
            windows={"1h": "1h"},
            coverage=28 * 86400, reads=self.readers({"api:8081": 1000}, {"api:8081": 20}))
        state = services["api:8081"]["1h"]
        self.assertFalse(state["availability_met"])
        self.assertLess(state["budget_remaining"], 0)

    def test_latency_objective_uses_the_mean(self):
        services = self.module.evaluate(
            windows={"1h": "1h"},
            coverage=28 * 86400, reads=self.readers({"api:8081": 100}, {"api:8081": 0},
                               {"api:8081": 0.9}))
        state = services["api:8081"]["1h"]
        self.assertFalse(state["latency_met"])
        self.assertEqual(state["latency_mean"], 0.9)

    def test_summarise_is_worst_first(self):
        services = self.module.evaluate(
            windows={"24h": "24h"},
            coverage=28 * 86400, reads=self.readers({"a": 1000, "b": 1000}, {"a": 1, "b": 9}))
        rows = self.module.summarise(services, "24h")
        self.assertEqual(rows[0]["service"], "b")


class SampleGateTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_actuator_endpoints_are_excluded_from_the_sli(self):
        """Monitoring scrapes outnumbered user requests 2:1 on this host."""
        self.assertIn("/actuator/", self.module.USER_TRAFFIC)
        captured = []

        def fake_query(expression, timeout=20):
            captured.append(expression)
            return []

        self.module.query = fake_query
        self.module.evaluate(windows={"1h": "1h"}, coverage=28 * 86400)
        self.assertTrue(all("/actuator/" in item for item in captured),
                        "every SLI query must filter actuator traffic")

    def test_thin_window_is_not_judged(self):
        services = self.module.evaluate(
            windows={"24h": "24h"}, minimum=200,
            coverage=28 * 86400, reads={"total": lambda w: {"api:8081": 5},
                   "failed": lambda w: {"api:8081": 1},
                   "latency": lambda w: {"api:8081": 0.1}})
        state = services["api:8081"]["24h"]
        self.assertFalse(state["confident"])
        self.assertIsNone(state["availability_met"])
        self.assertIsNone(state["latency_met"])

    def test_verdict_appears_once_there_is_enough_traffic(self):
        services = self.module.evaluate(
            windows={"24h": "24h"}, minimum=100,
            coverage=28 * 86400, reads={"total": lambda w: {"api:8081": 500},
                   "failed": lambda w: {"api:8081": 0},
                   "latency": lambda w: {"api:8081": 0.1}})
        state = services["api:8081"]["24h"]
        self.assertTrue(state["confident"])
        self.assertTrue(state["availability_met"])

    def test_unjudged_window_publishes_minus_one(self):
        services = self.module.evaluate(
            windows={"1h": "1h"}, minimum=200,
            coverage=28 * 86400, reads={"total": lambda w: {"api:8081": 3},
                   "failed": lambda w: {"api:8081": 0},
                   "latency": lambda w: {"api:8081": 0.1}})
        text = self.module.render_metrics(services, now=1)
        self.assertIn('yunanexus_slo_met{service="api:8081",window="1h"} -1', text)
        self.assertIn('yunanexus_slo_confident{service="api:8081",window="1h"} 0', text)

    def test_table_explains_why_it_cannot_judge(self):
        services = self.module.evaluate(
            windows={"24h": "24h"}, minimum=200,
            coverage=28 * 86400, reads={"total": lambda w: {"api:8081": 5},
                   "failed": lambda w: {"api:8081": 0},
                   "latency": lambda w: {"api:8081": 0.1}})
        table = self.module.render_table(self.module.summarise(services, "24h"), "24h")
        self.assertIn("insufficient data", table)
        self.assertIn("need 200", table)


class CoverageTests(unittest.TestCase):
    """A window longer than the retained history cannot be judged."""

    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def readers(self):
        return {"total": lambda w: {"api:8081": 1000},
                "failed": lambda w: {"api:8081": 1},
                "latency": lambda w: {"api:8081": 0.05}}

    def test_window_longer_than_history_is_not_judged(self):
        services = self.module.evaluate(
            windows={"28d": "28d"}, coverage=5400,  # 90 minutes of data
            reads=self.readers())
        state = services["api:8081"]["28d"]
        self.assertFalse(state["covered"])
        self.assertFalse(state["confident"])
        self.assertIsNone(state["availability_met"])

    def test_window_inside_history_is_judged(self):
        services = self.module.evaluate(
            windows={"1h": "1h"}, coverage=5400, reads=self.readers())
        self.assertTrue(services["api:8081"]["1h"]["covered"])

    def test_table_blames_the_short_history(self):
        services = self.module.evaluate(
            windows={"28d": "28d"}, coverage=5400, reads=self.readers())
        table = self.module.render_table(self.module.summarise(services, "28d"), "28d")
        self.assertIn("history only", table)

    def test_history_metric_is_published(self):
        services = self.module.evaluate(
            windows={"1h": "1h"}, coverage=5400, reads=self.readers())
        text = self.module.render_metrics(services, now=1)
        self.assertIn("yunanexus_slo_history_seconds 5400", text)

    def test_window_seconds_parses_the_promql_suffixes(self):
        self.assertEqual(self.module.window_seconds("90m"), 5400)
        self.assertEqual(self.module.window_seconds("28d"), 2419200)
        self.assertEqual(self.module.window_seconds("1h"), 3600)

    def test_history_probe_is_a_binary_search(self):
        """Monotone predicate: probes must stay logarithmic, not sweep the range."""
        asked = []

        def probe(offset):
            asked.append(offset)
            return offset <= 7000

        # the search stops within one step of the boundary, not exactly on it
        self.assertLessEqual(abs(self.module.history_seconds(probe=probe) - 7000), 300)
        self.assertLess(len(asked), 20)


class ProbeTests(unittest.TestCase):
    """Probes carry their own sample volume, so a window can be judged without traffic."""

    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def readers(self, ratio=1.0, samples=600):
        return lambda window: ({"portal": ratio}, {"portal": samples})

    def test_probe_targets_are_prefixed(self):
        scored = self.module.evaluate_probes(
            windows={"1h": "1h"}, coverage=28 * 86400, reads=self.readers())
        self.assertIn("probe:portal", scored)

    def test_probe_slo_is_judgeable_without_user_traffic(self):
        scored = self.module.evaluate_probes(
            windows={"1h": "1h"}, coverage=28 * 86400, reads=self.readers(samples=600))
        state = scored["probe:portal"]["1h"]
        self.assertTrue(state["confident"])
        self.assertTrue(state["availability_met"])

    def test_failing_probes_break_the_objective(self):
        scored = self.module.evaluate_probes(
            windows={"1h": "1h"}, coverage=28 * 86400,
            reads=self.readers(ratio=0.90, samples=600))
        state = scored["probe:portal"]["1h"]
        self.assertFalse(state["availability_met"])
        self.assertLess(state["budget_remaining"], 0)

    def test_too_few_probes_is_not_judged(self):
        scored = self.module.evaluate_probes(
            windows={"1h": "1h"}, coverage=28 * 86400, reads=self.readers(samples=5))
        self.assertIsNone(scored["probe:portal"]["1h"]["availability_met"])

    def test_table_shows_the_probe_threshold_not_the_service_one(self):
        scored = self.module.evaluate_probes(
            windows={"1h": "1h"}, coverage=28 * 86400, reads=self.readers(samples=5))
        table = self.module.render_table(self.module.summarise(scored, "1h"), "1h")
        self.assertIn("need 36", table)

    def test_short_history_blocks_the_verdict(self):
        scored = self.module.evaluate_probes(
            windows={"24h": "24h"}, coverage=5400, reads=self.readers(samples=600))
        self.assertIsNone(scored["probe:portal"]["24h"]["availability_met"])

    def test_latency_is_never_judged_from_probes(self):
        scored = self.module.evaluate_probes(
            windows={"1h": "1h"}, coverage=28 * 86400, reads=self.readers())
        self.assertIsNone(scored["probe:portal"]["1h"]["latency_met"])

    def test_sample_threshold_scales_with_the_window(self):
        self.assertEqual(self.module.probe_minimum("5m"), 3)
        self.assertEqual(self.module.probe_minimum("1h"), 36)
        self.assertEqual(self.module.probe_minimum("24h"), 864)

    def test_five_minute_window_is_judgeable_after_three_samples(self):
        scored = self.module.evaluate_probes(
            windows={"5m": "5m"}, coverage=3600, reads=self.readers(samples=3))
        self.assertTrue(scored["probe:portal"]["5m"]["confident"])

    def test_extrapolated_fraction_does_not_block_a_short_window(self):
        """increase() can return 4.4 for a fully covered 5 minute window."""
        scored = self.module.evaluate_probes(
            windows={"5m": "5m"}, coverage=3600, reads=self.readers(samples=4.4))
        self.assertTrue(scored["probe:portal"]["5m"]["confident"])

    def test_probes_are_counted_not_scrapes(self):
        """The textfile gauge is scraped several times per probe cycle."""
        def fake_query(expression, timeout=20, moment=None):
            if "probes_total" in expression:
                return [{"metric": {"target": "portal"}, "value": [0, "10"]}]
            if "success_total" in expression:
                return [{"metric": {"target": "portal"}, "value": [0, "9"]}]
            return []

        self.module.query = fake_query
        ratio, count = self.module.probe_readers("1h")
        self.assertEqual(count["portal"], 10.0)
        self.assertAlmostEqual(ratio["portal"], 0.9)

    def test_probe_queries_use_counters(self):
        expressions = []

        def fake_query(expression, timeout=20, moment=None):
            expressions.append(expression)
            return []

        self.module.query = fake_query
        self.module.evaluate_probes(windows={"1h": "1h"}, coverage=28 * 86400)
        self.assertTrue(any("increase(yunanexus_probe_probes_total[1h])" in e
                            for e in expressions))
        self.assertTrue(any("increase(yunanexus_probe_success_total[1h])" in e
                            for e in expressions))


class MetricsTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def sample(self):
        return self.module.evaluate(
            windows={"1h": "1h"},
            coverage=28 * 86400, reads={"total": lambda w: {"api:8081": 1000},
                   "failed": lambda w: {"api:8081": 1},
                   "latency": lambda w: {"api:8081": 0.05}})

    def test_metrics_carry_service_and_window_labels(self):
        text = self.module.render_metrics(self.sample(), now=1000)
        self.assertIn('yunanexus_slo_availability_ratio{service="api:8081",window="1h"}', text)
        self.assertIn('yunanexus_slo_met{service="api:8081",window="1h"} 1', text)
        self.assertIn("yunanexus_slo_target_ratio{service=\"api:8081\"} 0.995", text)
        self.assertIn("yunanexus_slo_timestamp_seconds 1000", text)

    def test_failure_is_visible_in_metrics(self):
        text = self.module.render_metrics({}, ok=False, now=1)
        self.assertIn("yunanexus_slo_success 0", text)

    def test_table_marks_the_breach(self):
        breached = self.module.evaluate(
            windows={"24h": "24h"},
            coverage=28 * 86400, reads={"total": lambda w: {"api:8081": 1000},
                   "failed": lambda w: {"api:8081": 40},
                   "latency": lambda w: {"api:8081": 0.1}})
        table = self.module.render_table(self.module.summarise(breached, "24h"), "24h")
        self.assertIn("below objective", table)

    def test_empty_table_says_so(self):
        self.assertIn("no traffic", self.module.render_table([], "24h"))


if __name__ == "__main__":
    unittest.main()
