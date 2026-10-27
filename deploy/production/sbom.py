#!/usr/bin/env python3
"""Dependency inventory for a YunaNexus backend release.

Every service jar is a Spring Boot fat jar, so the truth about what ships sits in
`BOOT-INF/lib/*.jar`. Reading those entries gives an exact, offline bill of
materials; comparing two releases answers the question a release review actually
asks - "what changed in the dependency tree because of this commit?".

Exact coordinates are taken from the nested `META-INF/maven/**/pom.properties`
when present, otherwise from the file name (group is then unknown, which the
reporter marks rather than guessing).
"""
import argparse
import hashlib
import io
import json
import logging
import os
import re
import sys
import time
import zipfile
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
SNAPSHOTS = R / "runtime/sbom"
SERVICES = ["meta", "auth", "gateway", "user", "permission", "file", "notice", "audit", "analytics"]
LIB_PREFIX = "BOOT-INF/lib/"
NAME_PATTERN = re.compile(r"^(?P<name>.+?)-(?P<version>\d[^-]*(?:[-.][^-]*)*)$")

log = logging.getLogger("sbom")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


def parse_library(filename):
    """Turn `spring-core-6.1.4.jar` into (name, version)."""
    stem = filename[:-4] if filename.endswith(".jar") else filename
    match = NAME_PATTERN.match(stem)
    if not match:
        return stem, ""
    return match.group("name"), match.group("version")


def read_coordinates(archive, entry):
    """Prefer pom.properties inside the nested jar; it carries groupId and version."""
    try:
        blob = archive.read(entry)
    except (KeyError, OSError, zipfile.BadZipFile):
        return None
    try:
        nested = zipfile.ZipFile(io.BytesIO(blob))
    except zipfile.BadZipFile:
        return None
    for name in nested.namelist():
        if name.startswith("META-INF/maven/") and name.endswith("pom.properties"):
            properties = {}
            try:
                text = nested.read(name).decode("utf-8", "replace")
            except (KeyError, OSError):
                continue
            for line in text.splitlines():
                if "=" in line and not line.strip().startswith("#"):
                    key, value = line.split("=", 1)
                    properties[key.strip()] = value.strip()
            if properties.get("artifactId"):
                return {
                    "group": properties.get("groupId", ""),
                    "name": properties["artifactId"],
                    "version": properties.get("version", ""),
                }
    return None


def scan_jar(path, read_nested=True):
    """Components of one fat jar: {key: {name, version, group, services: set}}."""
    components = {}
    with zipfile.ZipFile(path) as archive:
        for entry in archive.namelist():
            if not entry.startswith(LIB_PREFIX) or not entry.endswith(".jar"):
                continue
            filename = entry[len(LIB_PREFIX):]
            if "/" in filename:
                continue
            coordinates = read_coordinates(archive, entry) if read_nested else None
            if coordinates is None:
                name, version = parse_library(filename)
                coordinates = {"group": "", "name": name, "version": version}
            key = "%s:%s" % (coordinates["group"], coordinates["name"]) if coordinates["group"] \
                else coordinates["name"]
            record = components.setdefault(key, {
                "group": coordinates["group"],
                "name": coordinates["name"],
                "versions": set(),
                "services": set(),
            })
            record["versions"].add(coordinates["version"])
            record["services"].add(path.stem)
    return components


def release_dir(revision=None):
    root = R / "releases/backend"
    if revision:
        return root / revision
    current = root / "current"
    return current.resolve() if current.exists() else root


def build(revision=None, read_nested=True):
    directory = release_dir(revision)
    jars = sorted(path for path in directory.glob("yunanexus-*.jar")
                  if path.stem[len("yunanexus-"):] in SERVICES)
    if not jars:
        raise RuntimeError("no service jars in %s" % directory)
    merged = {}
    for jar in jars:
        for key, record in scan_jar(jar, read_nested=read_nested).items():
            target = merged.setdefault(key, {
                "group": record["group"], "name": record["name"],
                "versions": set(), "services": set(),
            })
            target["versions"] |= record["versions"]
            target["services"] |= record["services"]
    release = directory.resolve().name
    components = []
    warnings = []
    for key in sorted(merged):
        record = merged[key]
        versions = sorted(record["versions"])
        if len(versions) > 1:
            warnings.append("%s appears with several versions: %s" % (key, ", ".join(versions)))
        components.append({
            "key": key,
            "group": record["group"],
            "name": record["name"],
            "version": versions[0] if len(versions) == 1 else "|".join(versions),
            "services": sorted(record["services"]),
            "services_total": len(record["services"]),
        })
    return {
        "release": release,
        "revision": release,
        "generated_at": int(time.time()),
        "jars": [jar.name for jar in jars],
        "components": components,
        "count": len(components),
        "warnings": warnings,
    }


def digest(document):
    payload = json.dumps({key: document[key] for key in ("release", "components")},
                         sort_keys=True).encode()
    return hashlib.sha256(payload).hexdigest()


def compare(before, after):
    """Added, removed and version-changed components between two inventories."""
    def index(document):
        return {component["key"]: component for component in document["components"]}

    old, new = index(before), index(after)
    added = [new[key] for key in sorted(set(new) - set(old))]
    removed = [old[key] for key in sorted(set(old) - set(new))]
    changed = [{"key": key, "from": old[key]["version"], "to": new[key]["version"]}
               for key in sorted(set(old) & set(new))
               if old[key]["version"] != new[key]["version"]]
    return {"added": added, "removed": removed, "changed": changed}


def summary(document):
    lines = ["release %s: %d components from %d jars"
             % (document["release"], document["count"], len(document["jars"]))]
    versions = {}
    for component in document["components"]:
        if component["services_total"] == len(SERVICES):
            versions["all services"] = versions.get("all services", 0) + 1
    for warning in document["warnings"]:
        lines.append("  warning: " + warning)
    return "\n".join(lines)


def save(document, directory=None):
    target = Path(directory or SNAPSHOTS)
    target.mkdir(parents=True, exist_ok=True)
    path = target / ("%s.json" % document["release"])
    path.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    os.chmod(path, 0o644)
    return path


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def main(argv=None):
    parser = argparse.ArgumentParser(description="dependency inventory for a release")
    parser.add_argument("--revision", help="release directory name (default: current)")
    parser.add_argument("--json", action="store_true", help="print the full inventory")
    parser.add_argument("--save", action="store_true", help="store a snapshot under runtime/sbom")
    parser.add_argument("--compare", metavar="SNAPSHOT", help="diff against a snapshot file")
    parser.add_argument("--no-nested", action="store_true",
                        help="skip reading pom.properties (faster, names from files only)")
    args = parser.parse_args(argv)

    document = build(args.revision, read_nested=not args.no_nested)
    if args.json:
        print(json.dumps(document, indent=2, sort_keys=True))
    else:
        print(summary(document))
    if args.compare:
        difference = compare(load(args.compare), document)
        print("\ncompared with %s" % args.compare)
        print("  added:   %d" % len(difference["added"]))
        for component in difference["added"][:40]:
            print("    + %s %s" % (component["key"], component["version"]))
        print("  removed: %d" % len(difference["removed"]))
        for component in difference["removed"][:40]:
            print("    - %s %s" % (component["key"], component["version"]))
        print("  changed: %d" % len(difference["changed"]))
        for component in difference["changed"][:40]:
            print("    ~ %s %s -> %s" % (component["key"], component["from"], component["to"]))
    if args.save:
        path = save(document)
        log.info("snapshot written to %s (%s)", path, digest(document)[:16])
    return 0


if __name__ == "__main__":
    sys.exit(main())
