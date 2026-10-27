#!/usr/bin/env python3
"""Container image inventory and OS-package vulnerability scan.

`sbom.py` answers "what Java code ships". This answers the other half: what the
container actually runs - base distribution, package versions, whether the image is
pinned to a digest, and whether the container belongs to a managed compose project.

Packages are read from inside the running container (no image pull, no local
database) and matched through OSV with the distribution's own ecosystem name, so a
finding always names the package version that is live right now.
"""
import argparse
import importlib.util
import json
import logging
import os
import re
import subprocess
import sys
import time
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
WORK = R / "runtime/imagescan"
TEXTFILE = R / "runtime/node_exporter_textfile/images.prom"
IGNORE_FILE = R / "ops/vuln-ignore.txt"
MIB = 1024 * 1024
MANAGED_PROJECTS = {"yunanexus-production", "yunanexus-monitoring"}
BATCH = 200
SEVERITY_ORDER = ["critical", "high", "medium", "low", "unknown"]

log = logging.getLogger("imagescan")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)

# OSV ecosystem naming: Ubuntu/Debian use the point release, Alpine the v-prefixed branch.
ECOSYSTEMS = {
    "ubuntu": lambda version: "Ubuntu:%s" % version,
    "debian": lambda version: "Debian:%s" % version.split(".")[0],
    "alpine": lambda version: "Alpine:v%s" % version,
}
# Distributions OSV has no package database for; reported, never silently dropped.
UNSUPPORTED = {"ol": "Oracle Linux", "rhel": "Red Hat", "centos": "CentOS", "rocky": "Rocky Linux"}


_BACKUP = None


def backup():
    """Reuse the tested docker wrapper (socket pinning lives there)."""
    global _BACKUP
    if _BACKUP is None:
        spec = importlib.util.spec_from_file_location("yunanexus_backup_for_imagescan",
                                                     R / "ops/backup.py")
        _BACKUP = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(_BACKUP)
        _BACKUP.log.handlers.clear()
    return _BACKUP


def docker(*args, timeout=120):
    """Raw docker CLI: backup.docker() is an `exec` helper bound to the MySQL container."""
    module = backup()
    command = ["docker"] + list(args)
    result = subprocess.run(command, capture_output=True, text=True, timeout=timeout,
                            env=module.docker_env())
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout).strip()[:300])
    return result.stdout


def vulnscan():
    """Reuse the Maven scanner: CVSS scoring, ignore list and OSV plumbing live there."""
    global _VULNSCAN
    if _VULNSCAN is None:
        spec = importlib.util.spec_from_file_location("yunanexus_vulnscan_for_imagescan",
                                                     R / "ops/vulnscan.py")
        _VULNSCAN = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(_VULNSCAN)
        _VULNSCAN.log.handlers.clear()
    return _VULNSCAN


_VULNSCAN = None


# ------------------------------------------------------------------ inventory


def containers():
    """Running containers with the facts a supply-chain review needs."""
    raw = docker("ps", "--format", "{{json .}}")
    entries = []
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            entries.append(json.loads(line))
        except ValueError:
            continue
    inventory = []
    for entry in entries:
        name = entry.get("Names")
        try:
            image = docker("inspect", "--format",
                           "{{.Config.Image}}|{{.Image}}|{{.State.StartedAt}}|"
                           "{{index .Config.Labels \"com.docker.compose.project\"}}", name).strip()
        except RuntimeError as error:
            log.warning("cannot inspect %s: %s", name, error)
            continue
        reference, image_id, started, project = (image.split("|") + ["", "", "", ""])[:4]
        inventory.append({
            "container": name,
            "image": reference,
            "image_id": image_id,
            "started_at": started[:19],
            "project": project or "",
            "managed": project in MANAGED_PROJECTS,
            "pinned": "@sha256:" in reference,
            "digest": reference.split("@sha256:")[-1] if "@sha256:" in reference else "",
        })
    return inventory


def os_release(container):
    """Base distribution of the running container."""
    try:
        text = docker("exec", container, "sh", "-c",
                      "cat /etc/os-release 2>/dev/null || true")
    except RuntimeError:
        return {}
    values = {}
    for line in text.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return {"id": values.get("ID", ""), "version": values.get("VERSION_ID", ""),
            "pretty": values.get("PRETTY_NAME", "")}


def ecosystem_for(release):
    """(ecosystem, reason) for OSV; reason set when the distro is not covered."""
    identifier = (release.get("id") or "").lower()
    version = release.get("version") or ""
    if not identifier:
        return None, "distribution unknown (no /etc/os-release)"
    if identifier in UNSUPPORTED:
        return None, "%s has no OSV package database" % UNSUPPORTED[identifier]
    builder = ECOSYSTEMS.get(identifier)
    if not builder:
        return None, "unsupported distribution %s" % identifier
    if not version:
        return None, "missing VERSION_ID for %s" % identifier
    return builder(version), ""


def packages(container):
    """[(name, version)] from dpkg/apk/rpm, whichever the image ships."""
    attempts = [
        ("dpkg-query", ["dpkg-query", "-W", "-f=${Package}\\t${Version}\\n"]),
        ("apk", ["apk", "info", "-v"]),
        ("rpm", ["rpm", "-qa", "--qf", "%{NAME}\\t%{VERSION}-%{RELEASE}\\n"]),
    ]
    for kind, command in attempts:
        try:
            raw = docker("exec", container, *command)
        except RuntimeError:
            continue
        parsed = parse_packages(kind, raw)
        if parsed:
            return kind, parsed
    return "", []


def parse_packages(kind, raw):
    """Normalise package listings into (name, version)."""
    entries = []
    for line in raw.splitlines():
        line = line.strip()
        if not line:
            continue
        if kind == "apk":
            # `apk info -v` prints name-version-rN with no separator; split from the right.
            match = re.match(r"^(?P<name>.+)-(?P<version>\d[^-]*-r\d+)$", line)
            if not match:
                continue
            entries.append((match.group("name"), match.group("version")))
            continue
        if "\t" in line:
            name, version = line.split("\t", 1)
            if name and version:
                entries.append((name.strip(), version.strip()))
    return entries


def inventory(include_stopped=False):
    """Everything the report needs, one dict per container."""
    details = []
    for entry in containers():
        release = os_release(entry["container"])
        ecosystem, reason = ecosystem_for(release)
        kind, found = packages(entry["container"])
        details.append(dict(entry, os=release, ecosystem=ecosystem,
                            ecosystem_reason=reason, package_manager=kind,
                            package_count=len(found), packages=found))
    return details


# ------------------------------------------------------------------ scanning


def scan(details, timeout=None):
    """Find advisories for every container's packages, grouped per ecosystem."""
    scanner = vulnscan()
    timeout = timeout or scanner.TIMEOUT
    cache = load_cache()
    by_ecosystem = {}
    for entry in details:
        if not entry["ecosystem"]:
            continue
        bucket = by_ecosystem.setdefault(entry["ecosystem"], set())
        bucket.update(entry["packages"])
    results = {}
    for ecosystem, packages_set in sorted(by_ecosystem.items()):
        ordered = sorted(packages_set)
        for start in range(0, len(ordered), BATCH):
            chunk = ordered[start:start + BATCH]
            try:
                answers = scanner.query_batch(chunk, timeout=timeout, ecosystem=ecosystem)
            except Exception as error:
                raise RuntimeError("OSV query failed for %s: %s" % (ecosystem, error))
            for package, answer in zip(chunk, answers):
                for advisory in (answer or {}).get("vulns") or []:
                    results.setdefault(ecosystem, {}).setdefault(package[0], set()).add(
                        advisory["id"])
    identifiers = sorted({identifier for ecosystem in results.values()
                          for ids in ecosystem.values() for identifier in ids})
    advisories = scanner.details(identifiers, cache)
    save_cache(cache)
    findings = []
    for entry in details:
        if not entry["ecosystem"]:
            continue
        for name, version in entry["packages"]:
            for identifier in results.get(entry["ecosystem"], {}).get(name, set()):
                advisory = advisories.get(identifier, {})
                if not covers(advisory, entry["ecosystem"], name, version):
                    continue
                severity, score, source = scanner.severity_from_vulnerability(advisory)
                findings.append({
                    "id": identifier,
                    "package": name,
                    "version": version,
                    "ecosystem": entry["ecosystem"],
                    "container": entry["container"],
                    "image": entry["image"].split("@")[0],
                    "severity": severity,
                    "score": score,
                    "score_source": source,
                    "summary": (advisory.get("summary") or "").strip()[:300],
                    "fixed": scanner.fixed_versions(advisory, {"name": name, "version": version}),
                })
    findings.sort(key=lambda item: (SEVERITY_ORDER.index(item["severity"]),
                                    -(item["score"] or 0), item["package"]))
    return findings


def covers(advisory, ecosystem, name, version):
    """Reject advisories that name another package or another version plain-list."""
    affected_list = advisory.get("affected") or []
    if not affected_list:
        return True
    matched_ecosystem = False
    for affected in affected_list:
        package = affected.get("package") or {}
        if package.get("ecosystem") != ecosystem:
            continue
        matched_ecosystem = True
        if package.get("name") and package["name"] != name:
            continue
        versions = affected.get("versions")
        if versions and version not in versions and not affected.get("ranges"):
            continue
        return True
    return not matched_ecosystem


def cache_path():
    return WORK / "osv-packages-cache.json"


def load_cache():
    path = cache_path()
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except ValueError:
        return {}


def save_cache(cache):
    WORK.mkdir(parents=True, exist_ok=True)
    cache_path().write_text(json.dumps(cache, sort_keys=True) + "\n", encoding="utf-8")


# ------------------------------------------------------------------ reporting


def dedupe(findings):
    """One row per (advisory, package, version) with the containers that carry it."""
    grouped = {}
    for finding in findings:
        key = (finding["id"], finding["package"], finding["version"], finding["ecosystem"])
        record = grouped.setdefault(key, dict(finding, containers=[]))
        record["containers"].append(finding["container"])
    rows = list(grouped.values())
    for row in rows:
        row["containers"] = sorted(set(row["containers"]))
    rows.sort(key=lambda item: (SEVERITY_ORDER.index(item["severity"]),
                                -(item["score"] or 0), item["package"]))
    return rows


def counts(findings, ignored):
    summary = {severity: 0 for severity in SEVERITY_ORDER}
    for finding in findings:
        if finding["id"] in ignored:
            continue
        summary[finding["severity"]] = summary.get(finding["severity"], 0) + 1
    return summary


def report(details, findings, ignored, now=None):
    now = int(time.time() if now is None else now)
    rows = dedupe(findings)
    return {
        "generated_at": now,
        "containers": [{
            "container": entry["container"],
            "image": entry["image"].split("@")[0],
            "digest": entry["digest"],
            "pinned": entry["pinned"],
            "managed": entry["managed"],
            "project": entry["project"],
            "os": entry["os"].get("pretty") or entry["os"].get("id", ""),
            "ecosystem": entry["ecosystem"],
            "ecosystem_reason": entry["ecosystem_reason"],
            "package_manager": entry["package_manager"],
            "package_count": entry["package_count"],
        } for entry in details],
        "findings": rows,
        "ignored": sorted(ignored),
        "counts": counts(rows, ignored),
        "hygiene": {
            "unmanaged": sorted(entry["container"] for entry in details if not entry["managed"]),
            "unpinned": sorted(entry["container"] for entry in details if not entry["pinned"]),
            "unscanned": sorted(entry["container"] for entry in details if not entry["ecosystem"]),
        },
    }


def render_metrics(document, ok=True, now=None):
    now = int(time.time() if now is None else now)
    summary = document["counts"]
    lines = [
        "# HELP yunanexus_image_vulnerabilities OS-package vulnerabilities per severity.",
        "# TYPE yunanexus_image_vulnerabilities gauge",
    ]
    for severity in SEVERITY_ORDER:
        lines.append('yunanexus_image_vulnerabilities{severity="%s"} %d'
                     % (severity, summary.get(severity, 0)))
    lines += [
        "# HELP yunanexus_image_unmanaged_containers Containers outside the managed projects.",
        "# TYPE yunanexus_image_unmanaged_containers gauge",
        "yunanexus_image_unmanaged_containers %d" % len(document["hygiene"]["unmanaged"]),
        "# HELP yunanexus_image_unscanned_containers Containers whose OS could not be matched.",
        "# TYPE yunanexus_image_unscanned_containers gauge",
        "yunanexus_image_unscanned_containers %d" % len(document["hygiene"]["unscanned"]),
        "# HELP yunanexus_imagescan_success Whether the last image scan completed.",
        "# TYPE yunanexus_imagescan_success gauge",
        "yunanexus_imagescan_success %d" % (1 if ok else 0),
        "# HELP yunanexus_imagescan_timestamp_seconds When the last image scan ran.",
        "# TYPE yunanexus_imagescan_timestamp_seconds gauge",
        "yunanexus_imagescan_timestamp_seconds %d" % now,
    ]
    return "\n".join(lines) + "\n"


def write(text, target=None):
    target = Path(target or TEXTFILE)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".prom.tmp")
    temporary.write_text(text, encoding="utf-8")
    os.chmod(temporary, 0o644)
    temporary.replace(target)
    return target


def summarise(document, limit=25):
    counts_text = ", ".join("%s %d" % (severity, document["counts"][severity])
                            for severity in SEVERITY_ORDER if document["counts"].get(severity))
    lines = ["%d containers scanned, %d findings (%s)"
             % (len(document["containers"]), len(document["findings"]), counts_text or "none")]
    for finding in document["findings"][:limit]:
        marker = "ignored" if finding["id"] in document["ignored"] else finding["severity"]
        lines.append("  [%s] %s %s %s (%s)%s" % (
            marker, finding["id"], finding["package"], finding["version"],
            finding["ecosystem"].split(":")[0],
            (" fix %s" % ",".join(finding["fixed"][:2])) if finding["fixed"] else ""))
    if len(document["findings"]) > limit:
        lines.append("  ... %d more" % (len(document["findings"]) - limit))
    hygiene = document["hygiene"]
    if hygiene["unmanaged"]:
        lines.append("  unmanaged containers: %s" % ", ".join(hygiene["unmanaged"]))
    if hygiene["unpinned"]:
        lines.append("  unpinned images: %s" % ", ".join(hygiene["unpinned"]))
    for entry in document["containers"]:
        if entry["ecosystem_reason"]:
            lines.append("  not scanned %s: %s" % (entry["container"], entry["ecosystem_reason"]))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="scan container images for OS-package CVEs")
    parser.add_argument("--json", action="store_true", help="print the full report")
    parser.add_argument("--gate", action="store_true",
                        help="exit 2 when unignored critical/high findings exist")
    parser.add_argument("--ignore-file", help="accepted risks (default ops/vuln-ignore.txt)")
    args = parser.parse_args(argv)

    ignored = vulnscan().load_ignore(args.ignore_file or str(IGNORE_FILE))
    try:
        details = inventory()
        findings = scan(details) if details else []
    except RuntimeError as error:
        log.error("%s", error)
        write(render_metrics({"counts": {s: 0 for s in SEVERITY_ORDER},
                              "hygiene": {"unmanaged": [], "unpinned": [], "unscanned": []}},
                             ok=False))
        return 2
    document = report(details, findings, ignored)
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "report.json").write_text(json.dumps(document, indent=2, sort_keys=True) + "\n",
                                      encoding="utf-8")
    write(render_metrics(document))
    print(json.dumps(document, indent=2, sort_keys=True) if args.json else summarise(document))
    blocking = [row for row in document["findings"]
                if row["severity"] in ("critical", "high") and row["id"] not in ignored]
    if args.gate and blocking:
        log.error("%d unignored critical/high OS-package finding(s)", len(blocking))
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
