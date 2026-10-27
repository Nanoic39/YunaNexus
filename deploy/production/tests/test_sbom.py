#!/usr/bin/env python3
"""Dependency inventory: fat-jar discovery, coordinate parsing and release diffs."""

import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
PRODUCTION = HERE.parent


def make_nested(name, group="org.example", version="1.0.0"):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        archive.writestr("META-INF/maven/%s/%s/pom.properties" % (group, name),
                         "groupId=%s\nartifactId=%s\nversion=%s\n" % (group, name, version))
    return buffer.getvalue()


def make_fat_jar(path, libraries):
    """libraries: list of (file name, nested jar bytes or None)."""
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("BOOT-INF/classes/application.yaml", "server: {}\n")
        for filename, blob in libraries:
            if blob is None:
                archive.writestr("BOOT-INF/lib/" + filename, b"not a zip")
            else:
                archive.writestr("BOOT-INF/lib/" + filename, blob)
    return path


def load(root):
    os.environ["YUNANEXUS_PLATFORM_ROOT"] = str(root)
    name = "sbom_%s" % abs(hash(str(root)))
    spec = importlib.util.spec_from_file_location(name, PRODUCTION / "sbom.py")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    module.log.handlers.clear()
    module.SNAPSHOTS = Path(root) / "runtime/sbom"
    return module


class SbomTests(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.release = self.root / "releases/backend/abc123"
        self.release.mkdir(parents=True)
        self.module = load(self.root)

    def build_release(self, revision, libraries):
        directory = self.root / "releases/backend" / revision
        directory.mkdir(parents=True, exist_ok=True)
        for service in ("auth", "user"):
            make_fat_jar(directory / ("yunanexus-%s.jar" % service), libraries)
        return directory

    def test_library_name_and_version_are_split(self):
        self.assertEqual(self.module.parse_library("spring-core-6.1.4.jar"), ("spring-core", "6.1.4"))
        self.assertEqual(self.module.parse_library("my-lib-1.0.0-SNAPSHOT.jar"),
                         ("my-lib", "1.0.0-SNAPSHOT"))
        self.assertEqual(self.module.parse_library("weird.jar"), ("weird", ""))

    def test_pom_properties_win_over_the_file_name(self):
        make_fat_jar(self.release / "yunanexus-auth.jar",
                     [("renamed-9.9.9.jar", make_nested("real-name", "com.acme", "1.2.3"))])
        components = self.module.scan_jar(self.release / "yunanexus-auth.jar")
        self.assertEqual(list(components), ["com.acme:real-name"])
        self.assertIn("1.2.3", components["com.acme:real-name"]["versions"])

    def test_broken_nested_jar_falls_back_to_the_file_name(self):
        make_fat_jar(self.release / "yunanexus-auth.jar", [("fallback-2.5.jar", None)])
        components = self.module.scan_jar(self.release / "yunanexus-auth.jar")
        self.assertEqual(list(components), ["fallback"])
        self.assertIn("2.5", components["fallback"]["versions"])

    def test_build_merges_services_and_flags_conflicting_versions(self):
        directory = self.build_release("rev1", [
            ("shared-1.0.jar", make_nested("shared", "org.example", "1.0")),
        ])
        # one service carries a different version of the same artifact
        make_fat_jar(directory / "yunanexus-user.jar", [
            ("shared-2.0.jar", make_nested("shared", "org.example", "2.0")),
        ])
        document = self.module.build("rev1")
        shared = next(c for c in document["components"] if c["name"] == "shared")
        self.assertEqual(shared["services"], ["yunanexus-auth", "yunanexus-user"])
        self.assertTrue(any("several versions" in warning for warning in document["warnings"]))

    def test_compare_reports_added_removed_and_changed(self):
        before = {"release": "a", "components": [
            {"key": "x", "version": "1"}, {"key": "y", "version": "1"}]}
        after = {"release": "b", "components": [
            {"key": "x", "version": "2"}, {"key": "z", "version": "1"}]}
        difference = self.module.compare(before, after)
        self.assertEqual([c["key"] for c in difference["added"]], ["z"])
        self.assertEqual([c["key"] for c in difference["removed"]], ["y"])
        self.assertEqual(difference["changed"], [{"key": "x", "from": "1", "to": "2"}])

    def test_snapshot_roundtrip_and_digest_is_stable(self):
        self.build_release("rev2", [("a-1.jar", make_nested("a", "org.example", "1"))])
        document = self.module.build("rev2")
        path = self.module.save(document)
        self.assertTrue(path.exists())
        again = self.module.build("rev2")
        self.assertEqual(self.module.digest(document), self.module.digest(again))
        self.assertEqual(self.module.load(path)["count"], document["count"])

    def test_missing_service_jars_is_an_error(self):
        with self.assertRaises(RuntimeError):
            self.module.build("does-not-exist")


if __name__ == "__main__":
    unittest.main()
