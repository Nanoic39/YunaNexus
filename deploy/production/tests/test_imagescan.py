#!/usr/bin/env python3
"""Container image scanning: distribution mapping, package parsing, hygiene checks."""

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
    name = "imagescan_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "imagescan.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.WORK = Path(root) / "runtime/imagescan"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/images.prom"
    # the scanned modules resolve their own helpers through <root>/ops, so give the
    # sandbox a real copy instead of pointing at /YunaNexus
    ops = Path(root) / "ops"
    ops.mkdir(parents=True, exist_ok=True)
    for name in ("vulnscan.py", "backup.py"):
        (ops / name).write_text((PRODUCTION / name).read_text(encoding="utf-8"))
    return module


class EcosystemTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_known_distributions_map_to_osv_ecosystems(self):
        self.assertEqual(self.module.ecosystem_for({"id": "ubuntu", "version": "24.04"}),
                         ("Ubuntu:24.04", ""))
        self.assertEqual(self.module.ecosystem_for({"id": "alpine", "version": "3.23.3"}),
                         ("Alpine:v3.23.3", ""))
        self.assertEqual(self.module.ecosystem_for({"id": "debian", "version": "12.5"})[0],
                         "Debian:12")

    def test_oracle_linux_is_reported_not_guessed(self):
        ecosystem, reason = self.module.ecosystem_for({"id": "ol", "version": "9.8"})
        self.assertIsNone(ecosystem)
        self.assertIn("Oracle Linux", reason)

    def test_missing_os_release_is_reported(self):
        ecosystem, reason = self.module.ecosystem_for({})
        self.assertIsNone(ecosystem)
        self.assertIn("unknown", reason)

    def test_missing_version_is_reported(self):
        ecosystem, reason = self.module.ecosystem_for({"id": "ubuntu"})
        self.assertIsNone(ecosystem)
        self.assertIn("VERSION_ID", reason)


class PackageParsingTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def test_dpkg_output(self):
        raw = "libssl3t64\t3.0.13-0ubuntu3.5\nbash\t5.2.21-2ubuntu4\n"
        self.assertEqual(self.module.parse_packages("dpkg", raw),
                         [("libssl3t64", "3.0.13-0ubuntu3.5"), ("bash", "5.2.21-2ubuntu4")])

    def test_apk_output_splits_from_the_right(self):
        raw = "musl-1.2.5-r1\nlibssl3-3.3.2-r4\napk-tools-2.14.4-r1\n"
        self.assertEqual(self.module.parse_packages("apk", raw),
                         [("musl", "1.2.5-r1"), ("libssl3", "3.3.2-r4"),
                          ("apk-tools", "2.14.4-r1")])

    def test_rpm_output(self):
        raw = "openssl-libs\t3.2.2-6.el9_5\n"
        self.assertEqual(self.module.parse_packages("rpm", raw),
                         [("openssl-libs", "3.2.2-6.el9_5")])

    def test_garbage_lines_are_skipped(self):
        self.assertEqual(self.module.parse_packages("apk", "not-a-package\n\n"), [])


class ScanTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.module = load(self.root)
        self.details = [
            {"container": "yu-edge-1", "image": "nginx@sha256:abc", "digest": "abc",
             "pinned": True, "managed": True, "project": "yunanexus-production",
             "started_at": "", "image_id": "1",
             "os": {"id": "alpine", "version": "3.23.3", "pretty": "Alpine Linux v3.23"},
             "ecosystem": "Alpine:v3.23.3", "ecosystem_reason": "",
             "package_manager": "apk", "package_count": 2,
             "packages": [("musl", "1.2.5-r1"), ("libssl3", "3.3.2-r4")]},
            {"container": "legacy-1", "image": "nblog-jre", "digest": "", "pinned": False,
             "managed": False, "project": "nblog", "started_at": "", "image_id": "2",
             "os": {"id": "ol", "version": "9.8", "pretty": "Oracle Linux 9.8"},
             "ecosystem": None, "ecosystem_reason": "Oracle Linux has no OSV package database",
             "package_manager": "rpm", "package_count": 1,
             "packages": [("openssl", "3.2.2")]},
        ]

    def test_docker_calls_are_plain_cli_not_mysql_exec(self):
        """backup.docker() prefixes `exec mysql`; management commands must not use it."""
        import subprocess as real_subprocess
        captured = {}

        class Result:
            returncode = 0
            stdout = ""
            stderr = ""

        def fake_run(command, **kwargs):
            captured.setdefault("commands", []).append(command)
            return Result()

        self.module.subprocess = type("S", (), {"run": staticmethod(fake_run)})
        self.module.docker("ps", "--format", "{{json .}}")
        self.module.docker("exec", "some-container", "sh", "-c", "true")
        first, second = captured["commands"]
        self.assertEqual(first, ["docker", "ps", "--format", "{{json .}}"])
        self.assertEqual(second[:4], ["docker", "exec", "some-container", "sh"])

    def test_scan_reports_only_covering_advisories(self):
        self.module.vulnscan = lambda: _FakeScanner()
        findings = self.module.scan(self.details)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0]["package"], "musl")
        self.assertEqual(findings[0]["severity"], "critical")
        self.assertEqual(findings[0]["container"], "yu-edge-1")

    def test_unsupported_distribution_is_never_scanned(self):
        self.module.vulnscan = lambda: _FakeScanner()
        findings = self.module.scan(self.details)
        self.assertNotIn("legacy-1", [item["container"] for item in findings])

    def test_duplicates_collapse_to_one_row_with_all_containers(self):
        second = dict(self.details[0], container="yu-edge-2")
        self.module.vulnscan = lambda: _FakeScanner()
        findings = self.module.scan(self.details + [second])
        document = self.module.report(self.details + [second], findings, {})
        self.assertEqual(len(document["findings"]), 1)
        self.assertEqual(document["findings"][0]["containers"], ["yu-edge-1", "yu-edge-2"])

    def test_hygiene_flags_unmanaged_and_unpinned(self):
        document = self.module.report(self.details, [], {})
        self.assertEqual(document["hygiene"]["unmanaged"], ["legacy-1"])
        self.assertEqual(document["hygiene"]["unpinned"], ["legacy-1"])
        self.assertEqual(document["hygiene"]["unscanned"], ["legacy-1"])

    def test_ignored_findings_are_excluded_from_counts(self):
        self.module.vulnscan = lambda: _FakeScanner()
        findings = self.module.scan(self.details)
        document = self.module.report(self.details, findings, {"GHSA-os-1": "accepted"})
        self.assertEqual(document["counts"]["critical"], 0)

    def test_metrics_include_hygiene_gauges(self):
        document = self.module.report(self.details, [], {}, now=100)
        text = self.module.render_metrics(document, now=100)
        self.assertIn("yunanexus_image_unmanaged_containers 1", text)
        self.assertIn("yunanexus_image_unscanned_containers 1", text)
        self.assertIn("yunanexus_imagescan_success 1", text)

    def test_gate_blocks_on_unignored_high(self):
        self.module.vulnscan = lambda: _FakeScanner()
        document = self.module.report(self.details, self.module.scan(self.details), {})
        blocking = [row for row in document["findings"]
                    if row["severity"] in ("critical", "high")]
        self.assertEqual(len(blocking), 1)


class _FakeScanner:
    """Stands in for vulnscan.py: one advisory for musl, one unrelated one."""

    TIMEOUT = 5

    def load_ignore(self, path=None):
        return {}

    def query_batch(self, packages, timeout=None, ecosystem="Maven"):
        answers = []
        for name, version in packages:
            if name == "musl" and ecosystem.startswith("Alpine"):
                answers.append({"vulns": [{"id": "GHSA-os-1"}, {"id": "GHSA-os-2"}]})
            else:
                answers.append({})
        return answers

    def details(self, identifiers, cache=None):
        return {
            "GHSA-os-1": {
                "id": "GHSA-os-1",
                "summary": "musl buffer overflow",
                "severity": [{"type": "CVSS_V3",
                              "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"}],
                "affected": [{"package": {"ecosystem": "Alpine:v3.23.3", "name": "musl"},
                              "ranges": [{"events": [{"fixed": "1.2.5-r2"}]}]}],
                "database_specific": {},
            },
            # same batch slot, but about a different package: must not be attributed
            "GHSA-os-2": {
                "id": "GHSA-os-2",
                "summary": "unrelated",
                "severity": [{"type": "CVSS_V3",
                              "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"}],
                "affected": [{"package": {"ecosystem": "Alpine:v3.23.3", "name": "zlib"},
                              "ranges": [{"events": [{"fixed": "1.3"}]}]}],
                "database_specific": {},
            },
        }

    def severity_from_vulnerability(self, vulnerability):
        return "critical", 9.8, "cvss"

    def fixed_versions(self, advisory, component):
        return ["1.2.5-r2"] if component["name"] == "musl" else []


if __name__ == "__main__":
    unittest.main()
