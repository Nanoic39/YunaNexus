#!/usr/bin/env python3
"""Match a release's dependency inventory against known vulnerabilities (OSV).

Scanning lives here, not in a heavyweight local database: OSV is queried with the
exact Maven coordinates from `sbom.py`, so a finding always names the artifact the
build actually shipped. One batch call covers a whole release; CVSS base scores are
computed locally from the vector, which keeps severity ordering honest instead of
trusting a vendor label.

Policy lives in `ops/vuln-ignore.txt`: an accepted risk must be written down with a
reason, and anything not listed there counts. `--gate` then answers a single
question for the release pipeline: may this build ship?
"""
import argparse
import json
import logging
import math
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
SNAPSHOTS = R / "runtime/sbom"
WORK = R / "runtime/vulnscan"
IGNORE_FILE = R / "ops/vuln-ignore.txt"
TEXTFILE = R / "runtime/node_exporter_textfile/vulnerabilities.prom"
OSV_API = "https://api.osv.dev"
BATCH = 200
TIMEOUT = 60
SEVERITY_ORDER = ["critical", "high", "medium", "low", "unknown"]

log = logging.getLogger("vulnscan")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)

# CVSS v3.1 base metric weights
AV = {"N": 0.85, "A": 0.62, "L": 0.55, "P": 0.2}
AC = {"L": 0.77, "H": 0.44}
PR_UNCHANGED = {"N": 0.85, "L": 0.62, "H": 0.27}
PR_CHANGED = {"N": 0.85, "L": 0.68, "H": 0.5}
UI = {"N": 0.85, "R": 0.62}
CIA = {"H": 0.56, "L": 0.22, "N": 0.0}


def cvss_roundup(value):
    """CVSS rounds up to one decimal, not to nearest (see spec appendix A)."""
    scaled = int(round(value * 100000))
    if scaled % 10000 == 0:
        return scaled / 100000
    return (math.floor(scaled / 10000) + 1) / 10.0


def cvss3_base_score(vector):
    """Base score from a CVSS:3.x vector, or None when the vector is unusable."""
    if not vector:
        return None
    metrics = {}
    for part in vector.split("/"):
        if ":" in part:
            key, value = part.split(":", 1)
            metrics[key.strip().upper()] = value.strip().upper()
    try:
        scope_changed = metrics["S"] == "C"
        impact_sub = 1 - ((1 - CIA[metrics["C"]]) * (1 - CIA[metrics["I"]]) * (1 - CIA[metrics["A"]]))
        if scope_changed:
            impact = 7.52 * (impact_sub - 0.029) - 3.25 * (impact_sub - 0.02) ** 15
        else:
            impact = 6.42 * impact_sub
        exploitability = (8.22 * AV[metrics["AV"]] * AC[metrics["AC"]]
                          * (PR_CHANGED if scope_changed else PR_UNCHANGED)[metrics["PR"]]
                          * UI[metrics["UI"]])
    except KeyError:
        return None
    if impact <= 0:
        return 0.0
    if scope_changed:
        return cvss_roundup(min(1.08 * (impact + exploitability), 10))
    return cvss_roundup(min(impact + exploitability, 10))


def severity_of(score, label=""):
    if isinstance(score, (int, float)):
        if score >= 9.0:
            return "critical"
        if score >= 7.0:
            return "high"
        if score >= 4.0:
            return "medium"
        if score > 0:
            return "low"
        return "unknown"
    label = (label or "").strip().lower()
    return label if label in SEVERITY_ORDER else "unknown"


VENDOR_TYPES = ("ubuntu", "debian", "alpine", "red hat", "rocky linux", "almalinux", "suse")


def severity_from_vulnerability(vulnerability):
    """Best available severity plus what justified it.

    Linux distributions publish their own rating in the `severity` array (type
    `Ubuntu`, `Alpine`, ...) instead of `database_specific.severity`. Reading only the
    latter turned a third of the OS-package findings into "unknown".
    """
    scores = []
    labels = []
    for entry in vulnerability.get("severity") or []:
        kind = (entry.get("type") or "").lower()
        if kind.startswith("cvss_v3"):
            score = cvss3_base_score(entry.get("score", ""))
            if score is not None:
                scores.append(score)
        elif kind in VENDOR_TYPES or kind.startswith("cvss_v4"):
            labels.append(entry.get("score", ""))
    if scores:
        return severity_of(max(scores)), max(scores), "cvss"
    labels = [label for label in labels if label] + [
        (vulnerability.get("database_specific") or {}).get("severity") or ""]
    ranked = [severity_of(None, label) for label in labels]
    ranked = [item for item in ranked if item != "unknown"]
    if ranked:
        best = min(ranked, key=SEVERITY_ORDER.index)
        return best, None, "vendor"
    return "unknown", None, "none"


def affected_versions(vulnerability, component):
    """True when the advisory actually covers the shipped artifact and version.

    The batch endpoint already filtered by coordinate, but a batched answer can still
    carry advisories about a sibling artifact, a different ecosystem, or a version
    that only a range covers - each of those must not be reported as this build's bug.
    """
    name = component["name"]
    affected_list = vulnerability.get("affected") or []
    if not affected_list:
        return True         # nothing to cross-check: trust the API match
    for affected in affected_list:
        package = (affected.get("package") or {})
        if package.get("ecosystem") != "Maven":
            continue
        full = package.get("name", "")
        if full and full.split(":")[-1] != name and full != name:
            continue
        versions = affected.get("versions")
        ranges = affected.get("ranges") or []
        if versions and component["version"] not in versions:
            if any(component["version"] in item for item in versions):
                return True
            if not ranges:
                continue
        return True
    return False            # affected, but never for this ecosystem/artifact


def load_ignore(path=None):
    """Accepted risks, one per line: `<ID> [reason]`."""
    path = Path(path or IGNORE_FILE)
    if not path.exists():
        return {}
    accepted = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        accepted[parts[0]] = parts[1].strip() if len(parts) > 1 else ""
    return accepted


def latest_snapshot(revision=None):
    if revision:
        path = SNAPSHOTS / ("%s.json" % revision)
        if path.exists():
            return json.loads(path.read_text(encoding="utf-8"))
        raise RuntimeError("no inventory snapshot for %s; run ops/sbom.py --revision %s --save"
                           % (revision, revision))
    candidates = sorted(SNAPSHOTS.glob("*.json"), key=lambda path: path.stat().st_mtime)
    if not candidates:
        raise RuntimeError("no inventory snapshot; run ops/sbom.py --save first")
    return json.loads(candidates[-1].read_text(encoding="utf-8"))


def query_batch(packages, timeout=TIMEOUT, ecosystem="Maven"):
    """Ask OSV about many coordinates at once (any OSV ecosystem)."""
    payload = {"queries": [
        {"package": {"name": name, "ecosystem": ecosystem}, "version": version}
        for name, version in packages
    ]}
    request = urllib.request.Request(
        OSV_API + "/v1/querybatch",
        data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode()).get("results", [])


def query_vulnerability(identifier, timeout=TIMEOUT):
    with urllib.request.urlopen(OSV_API + "/v1/vulns/" + identifier, timeout=timeout) as response:
        return json.loads(response.read().decode())


def details(identifiers, cache=None):
    """Full advisories for the given ids, reusing a cache keyed by id."""
    cache = {} if cache is None else cache
    missing = [identifier for identifier in identifiers if identifier not in cache]
    for identifier in missing:
        try:
            cache[identifier] = query_vulnerability(identifier)
        except (urllib.error.URLError, OSError, ValueError) as error:
            log.warning("could not fetch %s: %s", identifier, error)
            cache[identifier] = {"id": identifier, "summary": "details unavailable",
                                 "database_specific": {}}
    return {identifier: cache[identifier] for identifier in identifiers}


def maven_name(component):
    """OSV wants group:artifact when the group is known."""
    if component.get("group"):
        return "%s:%s" % (component["group"], component["name"])
    return component["name"]


def scan(document, cache=None, timeout=TIMEOUT):
    components = [item for item in document["components"] if item.get("version")]
    packages = [(maven_name(item), item["version"]) for item in components]
    results = []
    for start in range(0, len(packages), BATCH):
        chunk = packages[start:start + BATCH]
        try:
            results.extend(query_batch(chunk, timeout=timeout))
        except (urllib.error.URLError, OSError, ValueError) as error:
            raise RuntimeError("OSV query failed: %s" % error)
    findings = []
    identifiers = []
    for component, result in zip(components, results):
        for entry in (result or {}).get("vulns") or []:
            identifiers.append(entry["id"])
    advisories = details(sorted(set(identifiers)), cache)
    for component, result in zip(components, results):
        for entry in (result or {}).get("vulns") or []:
            advisory = advisories.get(entry["id"], {})
            if not affected_versions(advisory, component):
                continue
            severity, score, source = severity_from_vulnerability(advisory)
            findings.append({
                "id": entry["id"],
                "aliases": advisory.get("aliases", []),
                "component": maven_name(component),
                "version": component["version"],
                "severity": severity,
                "score": score,
                "score_source": source,
                "summary": (advisory.get("summary") or "").strip()[:300],
                "services": component.get("services", []),
                "fixed": fixed_versions(advisory, component),
            })
    findings.sort(key=lambda item: (SEVERITY_ORDER.index(item["severity"]),
                                    -(item["score"] or 0), item["component"]))
    return findings


def fixed_versions(advisory, component):
    """Versions that fix the issue, when the advisory says so."""
    fixes = []
    for affected in advisory.get("affected") or []:
        package = (affected.get("package") or {})
        if package.get("ecosystem") != "Maven":
            continue
        for entry in affected.get("ranges") or []:
            for event in entry.get("events") or []:
                if "fixed" in event:
                    fixes.append(event["fixed"])
    return sorted(set(fixes))


def counts(findings, ignored=None):
    ignored = ignored or {}
    summary = {severity: 0 for severity in SEVERITY_ORDER}
    for finding in findings:
        if finding["id"] in ignored:
            continue
        summary[finding["severity"]] = summary.get(finding["severity"], 0) + 1
    return summary


def report(document, findings, ignored, now=None):
    now = int(time.time() if now is None else now)
    return {
        "release": document.get("release"),
        "generated_at": now,
        "components_scanned": document.get("count"),
        "findings": findings,
        "ignored": sorted(ignored),
        "counts": counts(findings, ignored),
        "totals": {"findings": len(findings), "ignored": len([f for f in findings
                                                              if f["id"] in ignored])},
    }


def render_metrics(document, findings, ignored, ok=True, now=None):
    now = int(time.time() if now is None else now)
    summary = counts(findings, ignored)
    lines = [
        "# HELP yunanexus_vulnerabilities Known vulnerabilities per severity in the deployed release.",
        "# TYPE yunanexus_vulnerabilities gauge",
    ]
    for severity in SEVERITY_ORDER:
        lines.append('yunanexus_vulnerabilities{severity="%s"} %d' % (severity, summary[severity]))
    lines += [
        "# HELP yunanexus_vulnerability_ignored Accepted risks excluded from the counts.",
        "# TYPE yunanexus_vulnerability_ignored gauge",
        "yunanexus_vulnerability_ignored %d" % len([f for f in findings if f["id"] in ignored]),
        "# HELP yunanexus_vulnscan_success Whether the last scan completed.",
        "# TYPE yunanexus_vulnscan_success gauge",
        "yunanexus_vulnscan_success %d" % (1 if ok else 0),
        "# HELP yunanexus_vulnscan_timestamp_seconds When the last scan ran.",
        "# TYPE yunanexus_vulnscan_timestamp_seconds gauge",
        "yunanexus_vulnscan_timestamp_seconds %d" % now,
        "# HELP yunanexus_sbom_components Components in the deployed release inventory.",
        "# TYPE yunanexus_sbom_components gauge",
        "yunanexus_sbom_components %d" % int(document.get("count") or 0),
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


def summarise(report_document, limit=25):
    summary = report_document["counts"]
    lines = ["release %s: %s components, %d findings (%s)" % (
        report_document["release"], report_document["components_scanned"],
        report_document["totals"]["findings"],
        ", ".join("%s %d" % (severity, summary[severity]) for severity in SEVERITY_ORDER
                  if summary.get(severity)))]
    for finding in report_document["findings"][:limit]:
        marker = "ignored" if finding["id"] in report_document["ignored"] else finding["severity"]
        lines.append("  [%s] %s %s %s%s" % (
            marker, finding["id"], finding["component"], finding["version"],
            (" (fixed in %s)" % ", ".join(finding["fixed"])) if finding["fixed"] else ""))
    if len(report_document["findings"]) > limit:
        lines.append("  ... %d more" % (len(report_document["findings"]) - limit))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="scan the release inventory for known CVEs")
    parser.add_argument("--revision", help="release id (default: newest snapshot)")
    parser.add_argument("--json", action="store_true", help="print the report as JSON")
    parser.add_argument("--gate", action="store_true",
                        help="exit 2 when unignored critical/high findings exist")
    parser.add_argument("--ignore-file", help="accepted risks (default ops/vuln-ignore.txt)")
    args = parser.parse_args(argv)

    try:
        document = latest_snapshot(args.revision)
    except RuntimeError as error:
        log.error("%s", error)
        write(render_metrics({"count": 0}, [], {}, ok=False))
        return 2
    ignored = load_ignore(args.ignore_file)
    cache_path = WORK / "osv-cache.json"
    cache = {}
    if cache_path.exists():
        try:
            cache = json.loads(cache_path.read_text(encoding="utf-8"))
        except ValueError:
            cache = {}
    try:
        findings = scan(document, cache)
    except RuntimeError as error:
        log.error("%s", error)
        write(render_metrics(document, [], ignored, ok=False))
        return 2
    WORK.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(json.dumps(cache, sort_keys=True) + "\n", encoding="utf-8")
    document_report = report(document, findings, ignored)
    target = WORK / ("report-%s.json" % (document.get("release") or "unknown"))
    target.write_text(json.dumps(document_report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write(render_metrics(document, findings, ignored))
    print(json.dumps(document_report, indent=2, sort_keys=True) if args.json
          else summarise(document_report))
    blocking = [item for item in findings
                if item["severity"] in ("critical", "high") and item["id"] not in ignored]
    if args.gate and blocking:
        log.error("%d unignored critical/high finding(s); gate refuses this release", len(blocking))
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
