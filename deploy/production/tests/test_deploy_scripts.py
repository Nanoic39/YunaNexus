#!/usr/bin/env python3
"""Sandbox tests for the production deploy scripts.

Nothing here touches /YunaNexus: every test builds its own platform root, stubs
compose/docker probes and asserts on the invariants that keep a bad release from
reaching production.
"""

import importlib.util
import io
import json
import os
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent
NAMES = ["meta", "auth", "gateway", "user", "permission", "file", "notice", "audit", "analytics"]
REV_A = "a" * 40
REV_B = "b" * 40


def load_script(name, root):
    """Import a production script with YUNANEXUS_PLATFORM_ROOT pointing at the sandbox."""
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    module_name = "%s_%s" % (name.replace("-", "_"), abs(hash(str(root))))
    spec = importlib.util.spec_from_file_location(module_name, PRODUCTION / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    for handler in list(module.log.handlers):
        handler.close()
    module.log.handlers.clear()
    return module


def build_root(base):
    root = Path(base)
    for relative in ["logs", "tmp", "runtime", "cache", "releases/backend", "releases/frontend", "ops"]:
        (root / relative).mkdir(parents=True, exist_ok=True)
    compose = root / "ops" / "compose.sh"
    compose.write_text("#!/usr/bin/env bash\nexit 0\n")
    compose.chmod(0o755)
    return root


def make_backend_archive(path, files=None):
    files = files if files is not None else ["yunanexus-%s.jar" % name for name in NAMES]
    with tarfile.open(path, "w:gz") as tar:
        for name in files:
            payload = ("jar:%s" % name).encode()
            info = tarfile.TarInfo(name)
            info.size = len(payload)
            tar.addfile(info, io.BytesIO(payload))
    return path


def strip_guards(module):
    """Neutralise host-facing side effects; keep the logic under test intact."""
    module.compose = lambda *args: ""
    module.healthy = lambda *args, **kwargs: None
    module.smoke = lambda: None
    module.memory_available_bytes = lambda: 4 * 1024 ** 3
    module.shutil.disk_usage = lambda path: SimpleNamespace(
        total=100 * 1024 ** 3, used=10 * 1024 ** 3, free=90 * 1024 ** 3
    )


class DeployReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = build_root(self.tmp.name)
        self.module = load_script("deploy-release", self.root)
        strip_guards(self.module)
        self.archive = make_backend_archive(Path(self.tmp.name) / "backend.tar.gz")

    def tearDown(self):
        self.tmp.cleanup()

    def releases(self):
        return self.root / "releases" / "backend"

    def test_installs_archive_and_moves_current(self):
        state = self.module.deploy("backend", self.archive, REV_A)
        self.assertEqual(state["revision"], REV_A)
        self.assertEqual((self.releases() / "current").resolve(), self.releases() / REV_A)
        self.assertTrue((self.releases() / REV_A / "yunanexus-auth.jar").is_file())
        recorded = json.loads((self.root / "runtime/backend-release.json").read_text())
        self.assertEqual(recorded["revision"], REV_A)
        self.assertIn("meta", recorded["services"])

    def test_second_release_moves_previous_and_retains_two(self):
        self.module.deploy("backend", self.archive, REV_A)
        second = make_backend_archive(Path(self.tmp.name) / "second.tar.gz")
        self.module.deploy("backend", second, REV_B)
        self.assertEqual((self.releases() / "current").resolve(), self.releases() / REV_B)
        self.assertEqual((self.releases() / "previous").resolve(), self.releases() / REV_A)
        leftovers = [p.name for p in self.releases().iterdir() if p.is_dir() and len(p.name) == 40]
        self.assertEqual(sorted(leftovers), sorted([REV_A, REV_B]))

    def test_rejects_unexpected_file_and_cleans_target(self):
        bad = make_backend_archive(
            Path(self.tmp.name) / "bad.tar.gz",
            ["yunanexus-auth.jar", "evil.sh"],
        )
        with self.assertRaises(ValueError):
            self.module.deploy("backend", bad, REV_A)
        self.assertFalse((self.releases() / REV_A).exists())

    def test_rejects_incomplete_jar_set(self):
        bad = make_backend_archive(
            Path(self.tmp.name) / "short.tar.gz", ["yunanexus-auth.jar", "yunanexus-user.jar"]
        )
        with self.assertRaises(ValueError):
            self.module.deploy("backend", bad, REV_A)
        self.assertFalse((self.releases() / REV_A).exists())

    def test_rejects_path_traversal(self):
        bad = Path(self.tmp.name) / "traversal.tar.gz"
        with tarfile.open(bad, "w:gz") as tar:
            payload = b"oops"
            info = tarfile.TarInfo("../escape.jar")
            info.size = len(payload)
            tar.addfile(info, io.BytesIO(payload))
        with self.assertRaises(ValueError):
            self.module.deploy("backend", bad, REV_A)
        self.assertFalse((self.releases() / REV_A).exists())

    def test_rejects_invalid_revision(self):
        with self.assertRaises(ValueError):
            self.module.deploy("backend", self.archive, "not-a-sha")

    def test_health_failure_rolls_back_to_previous(self):
        self.module.deploy("backend", self.archive, REV_A)
        failing = make_backend_archive(Path(self.tmp.name) / "failing.tar.gz")

        def explode(service, *args, **kwargs):
            if service == "user":
                raise RuntimeError("container crashed")

        self.module.healthy = explode
        with self.assertRaises(RuntimeError):
            self.module.deploy("backend", failing, REV_B)
        self.assertEqual((self.releases() / "current").resolve(), self.releases() / REV_A)
        recorded = json.loads((self.root / "runtime/backend-release.json").read_text())
        self.assertEqual(recorded["revision"], REV_A)

    def test_rollback_health_failure_is_fatal(self):
        self.module.deploy("backend", self.archive, REV_A)
        failing = make_backend_archive(Path(self.tmp.name) / "failing.tar.gz")

        def always_fail(service, *args, **kwargs):
            raise RuntimeError("boom")

        self.module.healthy = always_fail
        with mock.patch.object(self.module.log, "critical") as critical:
            with self.assertRaises(RuntimeError):
                self.module.deploy("backend", failing, REV_B)
            self.assertTrue(critical.called)

    def test_dry_run_changes_nothing(self):
        result = self.module.deploy("backend", self.archive, REV_A, dry_run=True)
        self.assertTrue(result["dry_run"])
        self.assertFalse((self.releases() / REV_A).exists())
        self.assertFalse((self.releases() / "current").exists())
        self.assertFalse((self.root / "runtime/backend-release.json").exists())

    def test_refuses_small_disk_reserve(self):
        self.module.shutil.disk_usage = lambda path: SimpleNamespace(
            total=10 * 1024 ** 3, used=9 * 1024 ** 3, free=1 * 1024 ** 3
        )
        with self.assertRaises(RuntimeError):
            self.module.deploy("backend", self.archive, REV_A)

    def test_refuses_low_memory_unless_forced(self):
        self.module.memory_available_bytes = lambda: 100 * 1024 ** 2
        with self.assertRaises(RuntimeError):
            self.module.deploy("backend", self.archive, REV_A)
        self.module.deploy("backend", self.archive, REV_A, force=True)
        self.assertTrue((self.releases() / REV_A).is_dir())

    def test_refuses_to_overwrite_immutable_release(self):
        self.module.deploy("backend", self.archive, REV_A)
        with self.assertRaises(RuntimeError):
            self.module.deploy("backend", self.archive, REV_A)

    def test_restart_recreates_without_changing_version(self):
        self.module.deploy("backend", self.archive, REV_A)
        recreated = []
        self.module.compose = lambda *args: recreated.append(args[-1]) or ""
        result = self.module.restart("backend", dry_run=False)
        self.assertEqual(result["revision"], REV_A)
        self.assertEqual(recreated, NAMES and self.module.START_ORDER)
        self.assertEqual((self.releases() / "current").resolve(), self.releases() / REV_A)

    def test_restart_single_service(self):
        self.module.deploy("backend", self.archive, REV_A)
        recreated = []
        self.module.compose = lambda *args: recreated.append(args[-1]) or ""
        self.module.restart("backend", service="auth")
        self.assertEqual(recreated, ["auth"])

    def test_restart_requires_current_release(self):
        with self.assertRaises(RuntimeError):
            self.module.restart("backend")

    def test_restart_dry_run_does_nothing(self):
        self.module.deploy("backend", self.archive, REV_A)
        recreated = []
        self.module.compose = lambda *args: recreated.append(args[-1]) or ""
        result = self.module.restart("backend", dry_run=True)
        self.assertTrue(result["dry_run"])
        self.assertEqual(recreated, [])


class MigrationGateTests(unittest.TestCase):
    """A backend release must not land on a schema that is behind."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = build_root(self.tmp)
        self.module = load_script("deploy-release", self.root)

    def test_no_tool_defined_means_no_gate(self):
        self.assertIsNone(self.module.migration_state())

    def test_pending_and_drift_block(self):
        state = [
            {"service": "user", "schema": "yunanexus_user_0", "state": "pending", "pending": [2]},
            {"service": "auth", "schema": "yunanexus_auth", "state": "current", "pending": []},
        ]
        blocking = self.module.blocking_migrations(state)
        self.assertEqual([entry["schema"] for entry in blocking], ["yunanexus_user_0"])

    def test_current_state_does_not_block(self):
        state = [{"service": "auth", "schema": "yunanexus_auth", "state": "current"}]
        self.assertEqual(self.module.blocking_migrations(state), [])

    def test_tool_failure_warns_instead_of_blocking(self):
        script = self.root / "ops/migrate.py"
        script.write_text("import sys; sys.stderr.write('boom'); sys.exit(3)\n")
        self.assertIsNone(self.module.migration_state())
        self.assertEqual(self.module.blocking_migrations(None), [])

    def test_unparsable_output_is_ignored(self):
        script = self.root / "ops/migrate.py"
        script.write_text("print('not json')\n")
        self.assertIsNone(self.module.migration_state())

    def test_preflight_raises_on_pending_schema(self):
        with mock.patch.object(self.module, "migration_state", return_value=[
                {"service": "user", "schema": "yunanexus_user_0", "state": "pending",
                 "pending": [2], "drift": []}]):
            with mock.patch.object(self.module, "memory_available_bytes", return_value=None):
                with self.assertRaises(RuntimeError) as caught:
                    self.module.preflight("backend", REV_A)
        self.assertIn("schema is behind", str(caught.exception))
        self.assertIn("yunanexus_user_0", str(caught.exception))

    def test_preflight_allows_frontend_release(self):
        with mock.patch.object(self.module, "migration_state",
                               side_effect=AssertionError("frontend must not query the database")):
            with mock.patch.object(self.module, "memory_available_bytes", return_value=None):
                self.module.preflight("frontend", REV_A)


class PollReleasesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = build_root(self.tmp.name)
        self.module = load_script("poll-releases", self.root)
        for handler in list(self.module.log.handlers):
            handler.close()
        self.module.log.handlers.clear()

    def tearDown(self):
        self.tmp.cleanup()

    def state(self):
        path = self.root / "runtime/cd-state.json"
        return json.loads(path.read_text()) if path.exists() else {}

    def test_failure_backs_off_then_blocks(self):
        for attempt in range(1, self.module.MAX_ATTEMPTS + 1):
            self.module.record_failure("backend", REV_A, ValueError("bad checksum"))
            entry = self.state()["backend"]
            self.assertEqual(entry["attempts"], attempt)
            self.assertEqual(entry["blocked"], attempt >= self.module.MAX_ATTEMPTS)
            self.assertGreater(entry["nextAttemptAt"], entry["lastAttempt"])
        self.assertEqual(entry["lastError"], "ValueError: bad checksum")

    def test_success_clears_failure_state(self):
        self.module.record_failure("backend", REV_A, ValueError("x"))
        self.assertIn("backend", self.state())
        self.module.record_success("backend")
        self.assertNotIn("backend", self.state())

    def test_skips_when_already_deployed(self):
        (self.root / "runtime/backend-release.json").write_text(json.dumps({"revision": REV_A}))
        with mock.patch.object(self.module, "latest_release", return_value={"revision": REV_A, "release": {}}):
            with mock.patch.object(self.module, "install") as install:
                self.assertFalse(self.module.check("backend", "YunaNexus"))
                install.assert_not_called()

    def test_respects_backoff_window(self):
        self.module.record_failure("backend", REV_A, ValueError("transient"))
        release = {"assets": [], "tag_name": "deploy-backend-" + REV_A}
        with mock.patch.object(self.module, "latest_release",
                               return_value={"revision": REV_A, "release": release}):
            with mock.patch.object(self.module, "install") as install:
                self.assertFalse(self.module.check("backend", "YunaNexus"))
                install.assert_not_called()

    def test_blocked_release_is_not_retried(self):
        state = {"backend": {"revision": REV_A, "attempts": self.module.MAX_ATTEMPTS,
                             "blocked": True, "nextAttemptAt": 0}}
        (self.root / "runtime/cd-state.json").write_text(json.dumps(state))
        release = {"assets": [], "tag_name": "deploy-backend-" + REV_A}
        with mock.patch.object(self.module, "latest_release",
                               return_value={"revision": REV_A, "release": release}):
            with mock.patch.object(self.module, "install") as install:
                self.assertFalse(self.module.check("backend", "YunaNexus"))
                install.assert_not_called()
        self.module.clear_block("backend")
        self.assertEqual(self.state(), {})

    def test_checksum_mismatch_records_failure(self):
        asset = {"name": "backend.tar.gz", "size": 100, "browser_download_url": "http://example/x"}
        release = {
            "tag_name": "deploy-backend-" + REV_A,
            "assets": [asset, {"name": "SHA256SUMS", "browser_download_url": "http://example/s"}],
        }
        page = mock.MagicMock()
        page.__enter__.return_value = io.BytesIO(b"0" * 64 + b"  backend.tar.gz\n")
        with mock.patch.object(self.module, "latest_release",
                               return_value={"revision": REV_A, "release": release}):
            with mock.patch.object(self.module, "read_with_retry", return_value=page):
                with self.assertRaises(Exception):
                    self.module.check("backend", "YunaNexus", force=True)
        entry = self.state()["backend"]
        self.assertEqual(entry["attempts"], 1)
        self.assertIn("Checksum mismatch", entry["lastError"])

    def test_install_failure_records_failure(self):
        asset = {"name": "backend.tar.gz", "size": 10, "digest": "sha256:" + "1" * 64,
                 "browser_download_url": "http://example/x"}
        release = {
            "tag_name": "deploy-backend-" + REV_A,
            "assets": [asset, {"name": "SHA256SUMS", "browser_download_url": "http://example/s"}],
        }
        archive = self.root / "cache" / ("backend-" + REV_A + ".tar.gz")
        archive.parent.mkdir(parents=True, exist_ok=True)
        archive.write_bytes(b"0123456789")
        with mock.patch.object(self.module, "latest_release",
                               return_value={"revision": REV_A, "release": release}):
            with mock.patch.object(self.module.hashlib, "file_digest") as file_digest:
                file_digest.return_value.hexdigest.return_value = "1" * 64
                with mock.patch.object(self.module, "install", side_effect=RuntimeError("health failed")):
                    with self.assertRaises(RuntimeError):
                        self.module.check("backend", "YunaNexus", force=True)
        entry = self.state()["backend"]
        self.assertEqual(entry["attempts"], 1)
        self.assertIn("health failed", entry["lastError"])

    def test_force_bypasses_backoff(self):
        self.module.record_failure("backend", REV_A, ValueError("transient"))
        asset = {"name": "backend.tar.gz", "size": 10, "digest": "sha256:" + "1" * 64,
                 "browser_download_url": "http://example/x"}
        release = {
            "tag_name": "deploy-backend-" + REV_A,
            "assets": [asset, {"name": "SHA256SUMS", "browser_download_url": "http://example/s"}],
        }
        archive = self.root / "cache" / ("backend-" + REV_A + ".tar.gz")
        archive.write_bytes(b"0123456789")
        with mock.patch.object(self.module, "latest_release",
                               return_value={"revision": REV_A, "release": release}):
            with mock.patch.object(self.module.hashlib, "file_digest") as file_digest:
                file_digest.return_value.hexdigest.return_value = "1" * 64
                with mock.patch.object(self.module, "install") as install:
                    self.assertTrue(self.module.check("backend", "YunaNexus", force=True))
                    install.assert_called_once()


class HealthProbeTests(unittest.TestCase):
    """The probe must fail fast on a crash loop instead of burning the whole window."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = build_root(self.tmp.name)
        self.module = load_script("deploy-release", self.root)
        self.module.compose = lambda *args: "container-id"

    def tearDown(self):
        self.tmp.cleanup()

    def inspect(self, payload):
        return mock.patch.object(self.module.subprocess, "check_output", return_value=payload + "\n")

    def test_crash_loop_fails_immediately(self):
        with self.inspect("running restarts=2"):
            with mock.patch.object(self.module, "container_diagnostics", return_value="tail"):
                import time as _time
                started = _time.time()
                with self.assertRaises(RuntimeError) as caught:
                    self.module.healthy("meta", attempts=90, interval=2)
                self.assertLess(_time.time() - started, 5)
        self.assertIn("restarts=2", str(caught.exception))

    def test_exited_container_fails_immediately(self):
        with self.inspect("exited restarts=0"):
            with mock.patch.object(self.module, "container_diagnostics", return_value="tail"):
                with self.assertRaises(RuntimeError) as caught:
                    self.module.healthy("meta", attempts=90, interval=2)
        self.assertIn("exited", str(caught.exception))

    def test_restarting_state_fails_immediately(self):
        with self.inspect("restarting restarts=1"):
            with mock.patch.object(self.module, "container_diagnostics", return_value="tail"):
                with self.assertRaises(RuntimeError):
                    self.module.healthy("meta", attempts=90, interval=2)

    def test_up_status_returns_without_waiting(self):
        context = mock.MagicMock()
        context.__enter__.return_value = io.BytesIO(b'{"status":"UP"}')
        with mock.patch.object(self.module.subprocess, "check_output",
                               side_effect=lambda *a, **k: "running restarts=0\n10.0.0.5\n"):
            with mock.patch.object(self.module.urllib.request, "urlopen", return_value=context):
                self.module.healthy("meta", attempts=2, interval=0)


if __name__ == "__main__":
    unittest.main(verbosity=2)
