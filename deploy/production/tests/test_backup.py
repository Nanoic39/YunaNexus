#!/usr/bin/env python3
"""Sandbox tests for backup.py: retention maths, manifest shape, drill verdicts."""

import hashlib
import importlib.util
import io
import json
import os
import sys
import tarfile
import tempfile
import unittest
from datetime import datetime
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def load_backup(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "backup_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "backup.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    for handler in list(module.log.handlers):
        handler.close()
    module.log.handlers.clear()
    return module


class RetentionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.module = load_backup(Path(self.tmp.name))

    def tearDown(self):
        self.tmp.cleanup()

    def test_keeps_newest_daily_window(self):
        stamps = [datetime(2026, 10, day) for day in range(1, 11)]
        prunable = self.module.select_prunable(stamps, keep_daily=3, keep_monthly=0)
        self.assertEqual(sorted(prunable), [datetime(2026, 10, day) for day in range(1, 8)])

    def test_keeps_one_per_month_beyond_daily_window(self):
        stamps = [datetime(2026, month, 15) for month in range(1, 7)]
        stamps += [datetime(2026, 7, day) for day in range(1, 6)]
        prunable = self.module.select_prunable(stamps, keep_daily=2, keep_monthly=4)
        kept = set(stamps) - set(prunable)
        # newest two of July plus the April/May/June representatives
        self.assertIn(datetime(2026, 7, 5), kept)
        self.assertIn(datetime(2026, 7, 4), kept)
        self.assertIn(datetime(2026, 6, 15), kept)
        self.assertIn(datetime(2026, 4, 15), kept)
        self.assertNotIn(datetime(2026, 1, 15), kept)
        self.assertNotIn(datetime(2026, 7, 1), kept)

    def test_nothing_pruned_when_under_limits(self):
        stamps = [datetime(2026, 10, day) for day in range(1, 4)]
        self.assertEqual(self.module.select_prunable(stamps, keep_daily=7, keep_monthly=4), [])

    def test_parse_stamp_rejects_foreign_directories(self):
        self.assertIsNone(self.module.parse_stamp("bootstrap"))
        self.assertIsNone(self.module.parse_stamp("20261017-cache"))
        self.assertEqual(self.module.parse_stamp("20261027-160000"), datetime(2026, 10, 27, 16, 0, 0))

    def test_prune_touches_only_stamped_directories(self):
        root = Path(self.tmp.name) / "backups"
        root.mkdir(parents=True)
        for name in ["20260101-030000", "20260102-030000", "20260301-030000", "bootstrap"]:
            (root / name).mkdir()
        self.module.BACKUPS = root
        removed = self.module.prune(keep_daily=1, keep_monthly=1)
        self.assertIn("20260101-030000", removed)
        self.assertNotIn("20260301-030000", removed)          # newest overall, kept as daily
        self.assertIn("20260102-030000", removed)             # older January run, outside both windows
        self.assertTrue((root / "bootstrap").is_dir())        # not ours, never touched


class BackupTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        for relative in ["logs", "runtime", "backups", "config", "ops", "data/file",
                         "data/mysql", "secrets"]:
            (self.root / relative).mkdir(parents=True, exist_ok=True)
        (self.root / "runtime/backend-release.json").write_text(json.dumps({"revision": "a" * 40}))
        (self.root / "config/compose.yaml").write_text("services: {}\n")
        (self.root / "data/file/avatar.png").write_bytes(b"png")
        self.module = load_backup(self.root)
        for handler in list(self.module.log.handlers):
            handler.close()
        self.module.log.handlers.clear()
        self.module.mysql_password = lambda: "secret"
        self.module.ensure_credentials = lambda password=None: "/dev/shm/test.cnf"
        self.module.drop_credentials = lambda: None
        self.module.disk_free_bytes = lambda: 50 * 1024 ** 3
        self.module.schemas = lambda: ["yunanexus_auth", "yunanexus_user_0"]
        self.module.table_counts = lambda database: (
            {"auth_user": 7, "auth_role": 3} if database == "yunanexus_auth" else {"sys_user": 7}
        )

        def fake_dump(name, target):
            target.write_bytes(b"dump:" + name.encode())
            return {"artifact": target.name, "bytes": target.stat().st_size,
                    "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}

        self.module.dump_schema = fake_dump

        def fake_grants(target):
            target.write_bytes(b"grants")
            return {"artifact": target.name, "bytes": 6,
                    "sha256": hashlib.sha256(b"grants").hexdigest()}

        self.module.dump_grants = fake_grants

        def fake_archive(target, sources):
            target.write_bytes(b"tar")
            return {"artifact": target.name, "bytes": 3,
                    "sha256": hashlib.sha256(b"tar").hexdigest()}

        self.module.archive_files = fake_archive
        self.module.PASSPHRASE_FILE = self.root / "passphrase"
        self.module.PASSPHRASE_FILE.write_text("test-passphrase\n")

        def fake_decrypt(source, passphrase_file, destination):
            with tarfile.open(destination, "w:gz") as tar:
                payload = b"SECRET=1\n"
                info = tarfile.TarInfo("secrets/compose.env")
                info.size = len(payload)
                tar.addfile(info, io.BytesIO(payload))
            return destination

        self.module.decrypt_file = fake_decrypt

        def fake_encrypt(source, passphrase_file):
            target = source.with_suffix(source.suffix + ".enc")
            source.replace(target)
            return target

        self.module.encrypt_file = fake_encrypt

    def tearDown(self):
        self.tmp.cleanup()

    def test_backup_writes_manifest_and_checksums(self):
        manifest = self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        target = self.root / "backups/20261027-041000"
        self.assertTrue((target / "manifest.json").is_file())
        self.assertTrue((target / "SHA256SUMS").is_file())
        self.assertTrue((target / "db/yunanexus_auth.sql.gz").is_file())
        self.assertEqual(manifest["stamp"], "20261027-041000")
        self.assertEqual(manifest["revision"]["backend"], "a" * 40)
        self.assertEqual(manifest["databases"]["yunanexus_auth"]["rows"], 10)
        self.assertEqual(manifest["databases"]["yunanexus_auth"]["tables"], 2)
        self.assertEqual(manifest["uploads"]["files"], 1)
        self.assertTrue(manifest["secrets_included"])
        self.assertTrue((target / "secrets.tar.gz.enc").is_file())
        listed = (target / "SHA256SUMS").read_text().splitlines()
        self.assertEqual(len(listed), len(manifest["artifacts"]))
        self.assertEqual(oct(target.stat().st_mode)[-3:], "700")

    def test_backup_refuses_empty_schema_list(self):
        self.module.schemas = lambda: []
        with self.assertRaises(RuntimeError):
            self.module.backup(datetime(2026, 10, 27, 4, 10, 0))

    def test_backup_refuses_low_disk(self):
        self.module.disk_free_bytes = lambda: 100 * 1024 ** 2
        with self.assertRaises(RuntimeError):
            self.module.backup(datetime(2026, 10, 27, 4, 10, 0))

    def test_backup_refuses_duplicate_stamp(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        with self.assertRaises(RuntimeError):
            self.module.backup(datetime(2026, 10, 27, 4, 10, 0))

    def test_mirror_copies_to_second_filesystem(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        mirror_dir = self.root / "mirror"
        self.module.MIRROR = mirror_dir
        self.module.same_device = lambda first, second: False
        destination = self.module.mirror(self.root / "backups/20261027-041000")
        self.assertEqual(destination, mirror_dir / "20261027-041000")
        self.assertTrue((destination / "manifest.json").is_file())
        self.assertTrue((destination / "db/yunanexus_auth.sql.gz").is_file())
        self.assertEqual(oct(destination.stat().st_mode)[-3:], "700")
        self.assertEqual(oct((destination / "manifest.json").stat().st_mode)[-3:], "600")

    def test_mirror_skips_same_filesystem(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        (self.root / "mirror").mkdir()
        self.module.MIRROR = self.root / "mirror"
        self.module.same_device = lambda first, second: True
        self.assertIsNone(self.module.mirror(self.root / "backups/20261027-041000"))
        self.assertEqual(list((self.root / "mirror").iterdir()), [])

    def test_mirror_failure_does_not_break_backup(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        self.module.MIRROR = self.root / "mirror"
        self.module.same_device = lambda first, second: False
        self.module.shutil.copytree = mock.Mock(side_effect=OSError("disk full"))
        self.assertIsNone(self.module.mirror(self.root / "backups/20261027-041000"))

    def test_ship_binlogs_copies_closed_files_only(self):
        self.module.MIRROR = self.root / "mirror"
        self.module.same_device = lambda first, second: False
        self.module.binlog_files = lambda: [self.root / "data/mysql/binlog.000001",
                                            self.root / "data/mysql/binlog.000002"]
        for name in ("binlog.000001", "binlog.000002"):
            (self.root / "data/mysql" / name).write_bytes(b"logs-" + name.encode())
        self.module.flush_binlogs = lambda: True
        self.module.binlog_status = lambda: {"file": "binlog.000002", "position": 4}
        shipped = self.module.ship_binlogs()
        self.assertEqual(shipped, ["binlog.000001.gz"])
        target = self.module.MIRROR / "binlog-live/binlog.000001.gz"
        self.assertTrue(target.is_file())
        self.assertEqual(oct(target.stat().st_mode)[-3:], "600")
        self.assertEqual(self.module.ship_binlogs(), [])   # idempotent

    def test_ship_binlogs_skips_same_filesystem(self):
        self.module.MIRROR = self.root / "mirror"
        self.module.MIRROR.mkdir()
        self.module.same_device = lambda first, second: True
        self.assertEqual(self.module.ship_binlogs(), [])

    def test_verify_passes_when_restore_matches(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        self.module.restore_schema = lambda schema, dump: "restored-" + schema
        self.module.mysql = lambda *args, **kwargs: ""
        self.module.table_counts = lambda database: (
            {"auth_user": 7, "auth_role": 3} if str(database).endswith("yunanexus_auth")
            else {"sys_user": 7}
        )
        report = self.module.verify("20261027-041000")
        self.assertTrue(report["ok"])

    def test_verify_detects_row_mismatch(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        self.module.restore_schema = lambda schema, dump: "scratch"
        self.module.mysql = lambda *args, **kwargs: ""
        self.module.table_counts = lambda database: {"auth_user": 6, "auth_role": 3}
        with self.assertRaises(RuntimeError) as caught:
            self.module.verify("20261027-041000")
        self.assertIn("row count mismatch", str(caught.exception))

    def test_verify_detects_missing_table(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        self.module.restore_schema = lambda schema, dump: "scratch"
        self.module.mysql = lambda *args, **kwargs: ""
        self.module.table_counts = lambda database: {"auth_user": 7}
        with self.assertRaises(RuntimeError) as caught:
            self.module.verify("20261027-041000")
        self.assertIn("missing tables", str(caught.exception))

    def test_verify_detects_bad_binlog_header(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        target = self.root / "backups/20261027-041000"
        (target / "binlog").mkdir()
        (target / "binlog/binlog.000001.gz").write_bytes(b"")
        import gzip as _gzip
        with _gzip.open(target / "binlog/binlog.000001.gz", "wb") as handle:
            handle.write(b"NOTABINLOG")
        manifest = json.loads((target / "manifest.json").read_text())
        manifest["binlog"]["archived"] = ["binlog.000001.gz"]
        problems = self.module.check_binlogs(target, manifest)
        self.assertTrue(any("invalid header" in problem for problem in problems))

    def test_verify_accepts_valid_binlog_header(self):
        target = self.root / "backups/x"
        (target / "binlog").mkdir(parents=True)
        import gzip as _gzip
        with _gzip.open(target / "binlog/binlog.000001.gz", "wb") as handle:
            handle.write(bytes([0xFE]) + b"bin" + b"payload")
        problems = self.module.check_binlogs(target, {"binlog": {"archived": ["binlog.000001.gz"]}})
        self.assertEqual(problems, [])

    def test_verify_detects_missing_secrets_passphrase(self):
        self.module.PASSPHRASE_FILE = self.root / "absent.passphrase"
        target = self.root / "backups/x"
        (target).mkdir(parents=True, exist_ok=True)
        (target / "secrets.tar.gz.enc").write_bytes(b"x")
        problems = self.module.check_secrets(target, {"secrets_included": True})
        self.assertTrue(any("passphrase" in problem for problem in problems))

    def test_verify_detects_corrupted_artifact(self):
        self.module.backup(datetime(2026, 10, 27, 4, 10, 0))
        target = self.root / "backups/20261027-041000"
        (target / "db/yunanexus_auth.sql.gz").write_bytes(b"tampered")
        self.module.restore_schema = lambda schema, dump: "scratch"
        self.module.mysql = lambda *args, **kwargs: ""
        with self.assertRaises(RuntimeError) as caught:
            self.module.verify("20261027-041000")
        self.assertIn("checksum mismatch", str(caught.exception))


if __name__ == "__main__":
    unittest.main(verbosity=2)
