#!/usr/bin/env python3
"""dbhealth: parsing, findings, growth and metric shape - no database required."""

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
    name = "dbhealth_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "dbhealth.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.HISTORY = Path(root) / "runtime/dbhealth/history.jsonl"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/db.prom"
    return module


OUTPUT = {
    "SELECT VERSION()": "8.4.11\t80\t3600",
    "SHOW VARIABLES WHERE Variable_name IN": "\n".join([
        "performance_schema\tOFF", "slow_query_log\tOFF", "long_query_time\t10.000000",
        "log_bin\tON", "binlog_expire_logs_seconds\t604800",
        "innodb_redo_log_capacity\t104857600", "log_output\tFILE"]),
    "SHOW GLOBAL STATUS WHERE Variable_name IN": "\n".join([
        "Threads_connected\t12", "Threads_running\t1", "Max_used_connections\t40",
        "Aborted_connects\t3", "Innodb_buffer_pool_reads\t100",
        "Innodb_buffer_pool_read_requests\t100000", "Slow_queries\t0", "Questions\t9000"]),
    "SELECT table_schema, table_name": "\n".join([
        "yunanexus_auth\tauth_user\tInnoDB\tutf8mb4_unicode_ci\t10\t1048576",
        "yunanexus_meta\tmeta_tenant\tInnoDB\tutf8mb4_0900_ai_ci\t5\t524288"]),
    "SELECT t.table_schema, t.table_name FROM information_schema.tables t": "",
    "SELECT table_schema, table_name, redundant_index_name": "",
    "SELECT file_name": "./mysql.ibd\t33554432\n./ibdata1\t12582912",
}


def fake_query(sql):
    """Longest fragment wins: "SELECT table_schema, table_name" is a prefix of the
    redundant-index statement, and a first-match stub silently answers the wrong one."""
    statement = sql.strip()
    for fragment in sorted(OUTPUT, key=len, reverse=True):
        if statement.startswith(fragment):
            return OUTPUT[fragment]
    raise AssertionError("unexpected statement: %s" % sql[:60])


class CollectionTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_every_measurement_is_parsed(self):
        measurement = self.module.collect(fake_query)
        self.assertEqual(measurement["version"], "8.4.11")
        self.assertEqual(measurement["max_connections"], 80)
        self.assertEqual(measurement["settings"]["performance_schema"], "OFF")
        self.assertEqual(measurement["counters"]["Threads_connected"], 12)
        self.assertEqual(len(measurement["tables"]), 2)
        self.assertEqual(measurement["schemas"]["yunanexus_auth"]["bytes"], 1048576)

    def test_buffer_pool_hit_ratio(self):
        # 100 reads out of 100000 requests is a 0.999 hit ratio
        self.assertAlmostEqual(self.module.hit_ratio({"Innodb_buffer_pool_reads": 100,
                                                      "Innodb_buffer_pool_read_requests": 100000}),
                               0.999, places=4)

    def test_hit_ratio_without_requests_is_unknown(self):
        self.assertIsNone(self.module.hit_ratio({"Innodb_buffer_pool_reads": 5}))

    def test_connection_ratio(self):
        self.assertEqual(self.module.connection_ratio({"Threads_connected": 64}, 80), 0.8)
        self.assertIsNone(self.module.connection_ratio({"Threads_connected": 1}, 0))

    def test_query_visibility_needs_either_switch(self):
        self.assertFalse(self.module.query_visibility({"performance_schema": "OFF",
                                                       "slow_query_log": "OFF"}))
        self.assertTrue(self.module.query_visibility({"performance_schema": "ON",
                                                      "slow_query_log": "OFF"}))
        self.assertTrue(self.module.query_visibility({"performance_schema": "OFF",
                                                      "slow_query_log": "ON"}))


class FindingTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())
        self.measurement = self.module.collect(fake_query)

    def names(self, measurement=None):
        return [name for _, name, _ in
                self.module.findings(measurement or self.measurement)]

    def test_blind_database_is_reported_as_high(self):
        found = {name: severity for severity, name, _ in self.module.findings(self.measurement)}
        self.assertEqual(found["no_query_visibility"], "high")
        self.assertEqual(found["slow_log_off"], "medium")

    def test_mixed_collations_are_flagged(self):
        """Two collations in one platform means cross-schema joins miss indexes."""
        self.assertIn("collation_mismatch", self.names())

    def test_single_collation_is_not_flagged(self):
        measurement = dict(self.measurement)
        measurement["tables"] = [dict(table, collation="utf8mb4_0900_ai_ci")
                                 for table in self.measurement["tables"]]
        self.assertNotIn("collation_mismatch", self.names(measurement))

    def test_missing_primary_key_is_critical(self):
        measurement = dict(self.measurement)
        measurement["no_primary_key"] = [{"schema": "yunanexus_auth", "table": "auth_token"}]
        found = {name: severity for severity, name, _ in self.module.findings(measurement)}
        self.assertEqual(found["missing_primary_key"], "critical")

    def test_non_innodb_engine_is_high(self):
        measurement = dict(self.measurement)
        measurement["tables"] = [dict(self.measurement["tables"][0], engine="MyISAM")]
        self.assertIn("non_innodb", self.names(measurement))

    def test_connection_pressure_is_flagged(self):
        measurement = dict(self.measurement)
        measurement["counters"] = dict(self.measurement["counters"], Threads_connected=70)
        self.assertIn("connections_high", self.names(measurement))

    def test_unreachable_database_is_the_only_finding(self):
        found = self.module.findings({"at": 0})
        self.assertEqual([name for _, name, _ in found], ["unreachable"])

    def test_healthy_database_has_no_findings(self):
        measurement = self.module.collect(fake_query)
        measurement["settings"] = {"performance_schema": "ON", "slow_query_log": "ON"}
        measurement["tables"] = [dict(table, collation="utf8mb4_0900_ai_ci")
                                 for table in measurement["tables"]]
        self.assertEqual(self.module.findings(measurement), [])


class GrowthTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_growth_is_computed_per_schema(self):
        history = [
            {"at": 0, "schemas": {"a": 1000}, "datadir": 100},
            {"at": 3600, "schemas": {"a": 2000}, "datadir": 200},
            {"at": 7200, "schemas": {"a": 3000}, "datadir": 300},
        ]
        rates, datadir = self.module.growth(history)
        self.assertAlmostEqual(rates["a"], 24000.0, delta=2)
        self.assertAlmostEqual(datadir, 2400.0, delta=2)

    def test_thin_history_yields_no_rates(self):
        rates, datadir = self.module.growth([{"at": 0, "schemas": {"a": 1}, "datadir": 1}])
        self.assertEqual(rates, {})
        self.assertIsNone(datadir)

    def test_history_round_trips_and_trims(self):
        measurement = self.module.collect(fake_query)
        self.module.append_history(measurement)
        self.module.append_history(measurement)
        self.assertEqual(len(self.module.load_history(now=measurement["at"] + 10)), 2)


class MetricsTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())
        self.measurement = self.module.collect(fake_query)
        self.found = self.module.findings(self.measurement)

    def test_core_series_are_published(self):
        text = self.module.render_metrics(self.measurement, self.found, {"yunanexus_auth": 1.0},
                                          None, now=7)
        self.assertIn("yunanexus_db_up 1", text)
        self.assertIn('yunanexus_db_connections{state="max"} 80', text)
        self.assertIn('yunanexus_db_schema_bytes{schema="yunanexus_auth"} 1048576', text)
        self.assertIn("yunanexus_db_query_visibility 0", text)
        # two high findings in the fixture: no query visibility, mixed collations
        self.assertIn('yunanexus_db_findings{severity="high"} 2', text)
        self.assertIn("yunanexus_db_timestamp_seconds 7", text)

    def test_failure_is_visible(self):
        text = self.module.render_metrics({"at": 0}, [], {}, None, ok=False, now=1)
        self.assertIn("yunanexus_db_up 0", text)
        self.assertIn("yunanexus_db_success 0", text)

    def test_table_mentions_the_datadir(self):
        table = self.module.render_table(self.measurement, self.found, {}, None)
        self.assertIn("datadir", table)
        self.assertIn("no_query_visibility", table)


if __name__ == "__main__":
    unittest.main()
