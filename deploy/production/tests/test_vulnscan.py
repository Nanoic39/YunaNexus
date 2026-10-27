#!/usr/bin/env python3
"""Vulnerability scanning: CVSS scoring, OSV matching, policy and gating."""

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
    name = "vulnscan_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "vulnscan.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.SNAPSHOTS = Path(root) / "runtime/sbom"
    module.WORK = Path(root) / "runtime/vulnscan"
    module.IGNORE_FILE = Path(root) / "ops/vuln-ignore.txt"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/vulnerabilities.prom"
    return module


class FakeResponse:
    def __init__(self, payload):
        self.payload = json.dumps(payload).encode()

    def read(self):
        return self.payload

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


class CvssTests(unittest.TestCase):
    """Scores must match the published values; the spec rounding is unusual."""

    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_known_vectors(self):
        cases = {
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H": 10.0,   # log4shell
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H": 9.8,
            "CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:H": 7.8,
            "CVSS:3.1/AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:N/A:N": 5.9,
            "CVSS:3.1/AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N": 4.3,
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N": 6.1,
        }
        for vector, expected in cases.items():
            self.assertAlmostEqual(self.module.cvss3_base_score(vector), expected, places=1,
                                   msg=vector)

    def test_zero_impact_scores_zero(self):
        self.assertEqual(self.module.cvss3_base_score(
            "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:N/A:N"), 0.0)

    def test_broken_vector_is_none(self):
        self.assertIsNone(self.module.cvss3_base_score(""))
        self.assertIsNone(self.module.cvss3_base_score("CVSS:3.1/AV:X/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"))

    def test_distribution_label_is_used_when_cvss_is_absent(self):
        """Ubuntu/Alpine put their rating in `severity` with a non-CVSS type."""
        advisory = {"severity": [{"type": "Ubuntu", "score": "medium"}],
                    "database_specific": None}
        self.assertEqual(self.module.severity_from_vulnerability(advisory),
                         ("medium", None, "vendor"))
        advisory = {"severity": [{"type": "Alpine", "score": "low"},
                                {"type": "Ubuntu", "score": "high"}], "database_specific": {}}
        self.assertEqual(self.module.severity_from_vulnerability(advisory)[0], "high")

    def test_cvss_wins_over_a_distribution_label(self):
        advisory = {"severity": [{"type": "Ubuntu", "score": "low"},
                                 {"type": "CVSS_V3",
                                  "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"}],
                    "database_specific": {}}
        severity, score, source = self.module.severity_from_vulnerability(advisory)
        self.assertEqual((severity, score, source), ("critical", 9.8, "cvss"))

    def test_no_severity_information_stays_unknown(self):
        self.assertEqual(self.module.severity_from_vulnerability({}),
                         ("unknown", None, "none"))

    def test_severity_bands(self):
        self.assertEqual(self.module.severity_of(9.8), "critical")
        self.assertEqual(self.module.severity_of(7.0), "high")
        self.assertEqual(self.module.severity_of(4.0), "medium")
        self.assertEqual(self.module.severity_of(0.1), "low")
        self.assertEqual(self.module.severity_of(None, "MODERATE"), "unknown")


class ScanTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.module = load(self.root)
        self.document = {
            "release": "rev1",
            "count": 2,
            "components": [
                {"key": "org.apache.logging.log4j:log4j-core", "group": "org.apache.logging.log4j",
                 "name": "log4j-core", "version": "2.14.1", "services": ["yunanexus-auth"],
                 "services_total": 1},
                {"key": "com.acme:fine", "group": "com.acme", "name": "fine",
                 "version": "3.1.0", "services": ["yunanexus-auth"], "services_total": 1},
            ],
        }

    def advisory(self, identifier="GHSA-1", ecosystem="Maven", name="org.apache.logging.log4j:log4j-core",
                 fixed="2.17.1", vector="CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"):
        return {
            "id": identifier,
            "summary": "remote code execution",
            "aliases": ["CVE-2021-44228"],
            "severity": [{"type": "CVSS_V3", "score": vector}] if vector else [],
            "affected": [{"package": {"ecosystem": ecosystem, "name": name},
                          "ranges": [{"events": [{"fixed": fixed}]}]}],
            "database_specific": {},
        }

    def patch_http(self, batch_vulns, advisory):
        def urlopen(request, timeout=None):
            url = request.full_url if hasattr(request, "full_url") else str(request)
            if url.endswith("/v1/querybatch"):
                return FakeResponse({"results": batch_vulns})
            identifier = url.rsplit("/", 1)[-1]
            return FakeResponse(advisory(identifier))
        self.module.urllib.request.urlopen = urlopen

    def test_finding_is_reported_with_fixed_version(self):
        self.patch_http([{"vulns": [{"id": "GHSA-1"}]}, {}], self.advisory)
        findings = self.module.scan(self.document)
        self.assertEqual(len(findings), 1)
        finding = findings[0]
        self.assertEqual(finding["component"], "org.apache.logging.log4j:log4j-core")
        self.assertEqual(finding["severity"], "critical")
        self.assertEqual(finding["fixed"], ["2.17.1"])
        self.assertEqual(finding["services"], ["yunanexus-auth"])

    def test_other_ecosystem_advisories_are_ignored(self):
        self.patch_http([{"vulns": [{"id": "GHSA-1"}]}, {}],
                        lambda identifier: self.advisory(identifier, ecosystem="npm"))
        self.assertEqual(self.module.scan(self.document), [])

    def test_other_artifact_advisories_are_ignored(self):
        self.patch_http([{"vulns": [{"id": "GHSA-1"}]}, {}],
                        lambda identifier: self.advisory(identifier, name="org.other:thing"))
        self.assertEqual(self.module.scan(self.document), [])

    def test_vendor_label_used_when_no_cvss(self):
        self.patch_http([{"vulns": [{"id": "GHSA-1"}]}, {}],
                        lambda identifier: dict(self.advisory(identifier, vector=None),
                                                database_specific={"severity": "HIGH"}))
        findings = self.module.scan(self.document)
        self.assertEqual(findings[0]["severity"], "high")
        self.assertEqual(findings[0]["score_source"], "vendor")

    def test_ignored_findings_do_not_count(self):
        self.patch_http([{"vulns": [{"id": "GHSA-1"}]}, {}], self.advisory)
        findings = self.module.scan(self.document)
        summary = self.module.counts(findings, {"GHSA-1": "not reachable"})
        self.assertEqual(summary["critical"], 0)

    def test_ignore_file_parsing(self):
        path = self.module.IGNORE_FILE
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("# comment\nGHSA-1 accepted: not reachable\nGHSA-2\n")
        accepted = self.module.load_ignore()
        self.assertEqual(accepted["GHSA-1"], "accepted: not reachable")
        self.assertEqual(accepted["GHSA-2"], "")

    def test_metrics_render_counts_and_success(self):
        text = self.module.render_metrics(self.document, [
            {"id": "A", "severity": "critical"}, {"id": "B", "severity": "low"},
            {"id": "C", "severity": "critical"}], {"C": "accepted"}, now=500)
        self.assertIn('yunanexus_vulnerabilities{severity="critical"} 1', text)
        self.assertIn('yunanexus_vulnerabilities{severity="low"} 1', text)
        self.assertIn("yunanexus_vulnerability_ignored 1", text)
        self.assertIn("yunanexus_vulnscan_timestamp_seconds 500", text)
        self.assertIn("yunanexus_sbom_components 2", text)

    def run_main(self, findings, ignore=None):
        self.module.latest_snapshot = lambda revision=None: self.document
        self.module.load_ignore = lambda path=None: ignore or {}
        self.module.scan = lambda document, cache=None: findings
        return self.module.main(["--gate"])

    def test_gate_blocks_on_critical(self):
        code = self.run_main([{"id": "A", "severity": "critical", "component": "x",
                               "version": "1", "services": [], "fixed": [], "aliases": [],
                               "summary": "", "score": 9.8, "score_source": "cvss"}])
        self.assertEqual(code, 2)

    def test_gate_passes_when_critical_is_accepted(self):
        code = self.run_main([{"id": "A", "severity": "critical", "component": "x",
                               "version": "1", "services": [], "fixed": [], "aliases": [],
                               "summary": "", "score": 9.8, "score_source": "cvss"}],
                             ignore={"A": "accepted risk"})
        self.assertEqual(code, 0)

    def test_gate_ignores_medium_findings(self):
        code = self.run_main([{"id": "B", "severity": "medium", "component": "x",
                               "version": "1", "services": [], "fixed": [], "aliases": [],
                               "summary": "", "score": 5.0, "score_source": "cvss"}])
        self.assertEqual(code, 0)

    def test_missing_snapshot_is_reported_not_crashed(self):
        module = load(tempfile.mkdtemp())
        self.assertEqual(module.main([]), 2)
        self.assertIn("yunanexus_vulnscan_success 0", module.TEXTFILE.read_text())


if __name__ == "__main__":
    unittest.main()
