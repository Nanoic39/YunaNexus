#!/usr/bin/env python3
"""Runtime baseline: every check, exception handling and metric rendering."""

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
    name = "runtimeaudit_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "runtimeaudit.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.WORK = Path(root) / "runtime/runtimeaudit"
    module.TEXTFILE = Path(root) / "runtime/node_exporter_textfile/runtime.prom"
    return module


def hardened(**overrides):
    """A container that satisfies the baseline; overrides poke one hole at a time."""
    base = {
        "name": "app-1", "image": "img@sha256:abc", "project": "yunanexus-production",
        "managed": True, "privileged": False, "read_only_rootfs": True,
        "user": "10001:10", "memory_limit": 384 * 1024 * 1024, "cpu_limit": 0.5,
        "pids_limit": 256, "pid_mode": "", "network_mode": "bridge",
        "restart_policy": "unless-stopped",
        "security_opts": ["no-new-privileges:true"], "cap_add": [], "cap_drop": ["ALL"],
        "log_config": {"Type": "local", "Config": {"max-size": "10m", "max-file": "3"}},
        "healthcheck": True, "docker_socket": False, "env_secrets": [],
    }
    base.update(overrides)
    return base


class CheckTests(unittest.TestCase):
    def setUp(self):
        self.module = load(tempfile.mkdtemp())

    def findings_for(self, container, exceptions=None):
        return self.module.evaluate([container], exceptions if exceptions is not None else {})

    def test_hardened_container_has_no_findings(self):
        self.assertEqual(self.findings_for(hardened()), [])

    def test_each_hole_is_reported(self):
        cases = {
            "privileged": {"privileged": True},
            "docker_socket": {"docker_socket": True},
            "no_new_privileges": {"security_opts": []},
            "capabilities_not_dropped": {"cap_drop": ["NET_RAW"]},
            "memory_limit_missing": {"memory_limit": 0},
            "log_rotation_missing": {"log_config": {"Type": "json-file", "Config": {}}},
            "secret_in_env": {"env_secrets": ["MYSQL_ROOT_PASSWORD"]},
            "cpu_limit_missing": {"cpu_limit": 0},
            "root_user": {"user": ""},
            "read_only_rootfs_missing": {"read_only_rootfs": False},
            "pids_limit_missing": {"pids_limit": 0},
            "healthcheck_missing": {"healthcheck": False},
            "restart_policy_missing": {"restart_policy": "no"},
            "host_pid": {"pid_mode": "host"},
            "unmanaged_container": {"managed": False, "project": "nblog"},
        }
        for expected, override in cases.items():
            findings = self.findings_for(hardened(**override))
            self.assertIn(expected, [finding["check"] for finding in findings], expected)

    def test_uid_zero_is_treated_as_root(self):
        findings = self.findings_for(hardened(user="0:0"))
        self.assertIn("root_user", [finding["check"] for finding in findings])

    def test_declared_exception_suppresses_the_finding(self):
        container = hardened(name="node-exporter", pid_mode="host")
        exceptions = {("node-exporter", "host_pid"): "needs host /proc"}
        self.assertEqual(self.findings_for(container, exceptions), [])
        applied = self.module.suppressed([container], exceptions)
        self.assertEqual(applied[0]["reason"], "needs host /proc")

    def test_exception_that_no_longer_applies_is_not_reported_as_applied(self):
        container = hardened(name="node-exporter")
        exceptions = {("node-exporter", "host_pid"): "stale"}
        self.assertEqual(self.module.suppressed([container], exceptions), [])

    def test_metrics_group_by_check_and_severity(self):
        findings = self.findings_for(hardened(cpu_limit=0, user="0"))
        document = self.module.report([hardened()], findings, [], now=700)
        text = self.module.render_metrics(document, now=700)
        self.assertIn('yunanexus_runtime_findings{check="cpu_limit_missing",severity="medium"} 1',
                      text)
        self.assertIn("yunanexus_runtime_containers 1", text)
        self.assertIn("yunanexus_runtimeaudit_timestamp_seconds 700", text)

    def test_summary_mentions_baseline_satisfied(self):
        document = self.module.report([hardened()], [], {})
        self.assertIn("baseline satisfied", self.module.summarise(document))

    def test_file_references_are_not_treated_as_secrets(self):
        """`..._PASSWORD__FILE` points at a mounted file; that is the remediation."""
        source = (PRODUCTION / "runtimeaudit.py").read_text(encoding="utf-8")
        self.assertIn("FILE_REFERENCE", source)
        self.assertTrue(self.module.FILE_REFERENCE.search("GF_SECURITY_ADMIN_PASSWORD__FILE"))
        self.assertTrue(self.module.FILE_REFERENCE.search("MYSQL_PASSWORD_FILE"))
        self.assertFalse(self.module.FILE_REFERENCE.search("MYSQL_ROOT_PASSWORD"))

    def test_secret_values_are_never_read(self):
        source = (PRODUCTION / "runtimeaudit.py").read_text(encoding="utf-8")
        self.assertIn("Names only", source)
        self.assertNotIn("item.split(\"=\", 1)[1]", source)


if __name__ == "__main__":
    unittest.main()
