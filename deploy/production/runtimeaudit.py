#!/usr/bin/env python3
"""Container runtime security baseline.

The compose files already do most things right, but "already right" decays: one
`docker run` by hand or one edited compose file and a container quietly loses
`read_only`, `cap_drop` or its log rotation. This reads the live runtime config and
checks it against an explicit baseline, so a regression is a metric change instead of
a discovery during an incident.

Read-only: it inspects containers and never changes anything. Known-intentional
deviations are declared in EXCEPTIONS with the reason, so an exception is a decision
someone wrote down rather than an oversight.
"""
import argparse
import json
import logging
import os
import re
import subprocess
import sys
import time
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
WORK = R / "runtime/runtimeaudit"
TEXTFILE = R / "runtime/node_exporter_textfile/runtime.prom"
MANAGED_PROJECTS = {"yunanexus-production", "yunanexus-monitoring"}
SECRET_PATTERN = re.compile(r"(PASSWORD|PASSWD|SECRET|TOKEN|APIKEY|API_KEY|_KEY$)", re.I)
# A path is not a secret: `GF_SECURITY_ADMIN_PASSWORD__FILE=/run/secrets/x` is the fix,
# not the problem, so it must not be reported as a leaked credential.
FILE_REFERENCE = re.compile(r"(__FILE|_FILE|_PATH)$", re.I)

log = logging.getLogger("runtimeaudit")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


def backup_module():
    import importlib.util
    global _BACKUP
    if _BACKUP is None:
        spec = importlib.util.spec_from_file_location("yunanexus_backup_for_runtimeaudit",
                                                     R / "ops/backup.py")
        _BACKUP = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(_BACKUP)
        _BACKUP.log.handlers.clear()
    return _BACKUP


_BACKUP = None


def docker(*args, timeout=120):
    module = backup_module()
    result = subprocess.run(["docker"] + list(args), capture_output=True, text=True,
                            timeout=timeout, env=module.docker_env())
    if result.returncode != 0:
        raise RuntimeError((result.stderr or result.stdout).strip()[:300])
    return result.stdout


# ---------------------------------------------------------------------- baseline

# Containers that legitimately differ. Every entry needs a reason: an exception is a
# reviewed decision, not a hole someone forgot about.
EXCEPTIONS = {
    ("yunanexus-monitoring-node-exporter-1", "host_pid"): "reads /proc of the host to expose metrics",
    ("yunanexus-monitoring-node-exporter-1", "root_user"): "needs /proc and /sys access",
    ("yunanexus-monitoring-victoriametrics-1", "root_user"): "upstream image runs as root",
    ("yunanexus-production-mysql-1", "root_user"): "upstream mysql image; see official entrypoint",
    ("yunanexus-production-nacos-1", "root_user"): "upstream nacos image runs as root",
    ("yunanexus-production-redis-1", "root_user"): "upstream redis image runs as root",
    ("yunanexus-production-mysql-1", "healthcheck_missing"): "healthcheck is defined",
    ("yunanexus-production-broker-1", "capabilities_not_dropped"): "keeps NET_RAW; needs it for nameserver sockets",
    ("yunanexus-production-namesrv-1", "capabilities_not_dropped"): "keeps NET_RAW; needs it for nameserver sockets",
    ("yunanexus-production-mysql-1", "capabilities_not_dropped"): "keeps NET_RAW for network sockets",
    ("yunanexus-production-nacos-1", "capabilities_not_dropped"): "keeps NET_RAW for network sockets",
    ("yunanexus-production-redis-1", "capabilities_not_dropped"): "keeps NET_RAW for network sockets",
}


def check_privileged(container):
    if container["privileged"]:
        return "container runs privileged (full host access)"
    return None


def check_socket(container):
    if container["docker_socket"]:
        return "the docker socket is mounted into the container"
    return None


def check_no_new_privileges(container):
    # docker reports "no-new-privileges:true", so membership must be a substring match:
    # an exact comparison flags every hardened container as unprotected.
    if not any("no-new-privileges" in item for item in container["security_opts"]):
        return "no-new-privileges is not set, so setuid binaries may escalate"
    return None


def check_capabilities(container):
    dropped = {item.upper() for item in container["cap_drop"]}
    if "ALL" not in dropped:
        return "capabilities are not fully dropped (cap_drop: ALL)"
    return None


def check_memory(container):
    if not container["memory_limit"]:
        return "no memory limit: one container can exhaust the host"
    return None


def check_cpu(container):
    if not container["cpu_limit"]:
        return "no CPU limit: a single hot service can starve the others"
    return None


def check_root_user(container):
    user = (container["user"] or "").strip()
    if not user:
        return "runs as root (no user directive): a container escape is a host escape"
    if user.split(":")[0] in ("0", "root"):
        return "runs as uid 0"
    return None


def check_read_only(container):
    if not container["read_only_rootfs"]:
        return "root filesystem is writable"
    return None


def check_pids(container):
    if not container["pids_limit"]:
        return "no pids limit: a fork bomb takes the host down"
    return None


def check_log_rotation(container):
    config = container["log_config"].get("Config") or {}
    if container["log_config"].get("Type") in (None, "none"):
        return "no log driver configured"
    if not config.get("max-size"):
        return "log rotation is not configured: logs grow until the disk is full"
    return None


def check_healthcheck(container):
    if not container["healthcheck"]:
        return "no compose-level healthcheck; unhealthy containers are not restarted"
    return None


def check_restart(container):
    if container["restart_policy"] in ("", "no"):
        return "restart policy is not set"
    return None


def check_host_pid(container):
    if container["pid_mode"] == "host":
        return "shares the host pid namespace"
    return None


def check_secrets(container):
    exposed = container["env_secrets"]
    if exposed:
        return "secrets passed as environment variables: %s (visible to docker inspect)" \
               % ", ".join(sorted(exposed))
    return None


def check_managed(container):
    if not container["managed"]:
        return "container is outside the managed compose projects (%s)" % (
            container["project"] or "no project")
    return None


CHECKS = [
    ("privileged", "critical", check_privileged),
    ("docker_socket", "critical", check_socket),
    ("no_new_privileges", "high", check_no_new_privileges),
    ("capabilities_not_dropped", "high", check_capabilities),
    ("memory_limit_missing", "high", check_memory),
    ("log_rotation_missing", "high", check_log_rotation),
    ("secret_in_env", "high", check_secrets),
    ("cpu_limit_missing", "medium", check_cpu),
    ("root_user", "medium", check_root_user),
    ("read_only_rootfs_missing", "medium", check_read_only),
    ("pids_limit_missing", "medium", check_pids),
    ("healthcheck_missing", "medium", check_healthcheck),
    ("restart_policy_missing", "medium", check_restart),
    ("host_pid", "medium", check_host_pid),
    ("unmanaged_container", "warning", check_managed),
]
SEVERITY_ORDER = ["critical", "high", "medium", "warning"]


# ---------------------------------------------------------------------- inventory


def containers():
    """Live runtime configuration of every running container."""
    raw = docker("ps", "--format", "{{.Names}}")
    names = [line.strip() for line in raw.splitlines() if line.strip()]
    if not names:
        return []
    inspected = json.loads(docker("inspect", *names))
    found = []
    for entry in inspected:
        host = entry.get("HostConfig") or {}
        config = entry.get("Config") or {}
        labels = config.get("Labels") or {}
        environment = config.get("Env") or []
        found.append({
            "name": (entry.get("Name") or "").lstrip("/"),
            "image": config.get("Image") or entry.get("Image", ""),
            "project": labels.get("com.docker.compose.project", ""),
            "managed": labels.get("com.docker.compose.project", "") in MANAGED_PROJECTS,
            "privileged": bool(host.get("Privileged")),
            "read_only_rootfs": bool(host.get("ReadonlyRootfs")),
            "user": config.get("User") or "",
            "memory_limit": int(host.get("Memory") or 0),
            "cpu_limit": float(host.get("NanoCpus") or 0),
            "pids_limit": int(host.get("PidsLimit") or 0),
            "pid_mode": host.get("PidMode") or "",
            "network_mode": host.get("NetworkMode") or "",
            "restart_policy": (host.get("RestartPolicy") or {}).get("Name", "") or "",
            "security_opts": list(host.get("SecurityOpt") or []),
            "cap_add": list(host.get("CapAdd") or []),
            "cap_drop": list(host.get("CapDrop") or []),
            "log_config": host.get("LogConfig") or {},
            "healthcheck": bool((config.get("Healthcheck") or {}).get("Test")),
            "docker_socket": any("docker.sock" in (mount.get("Source") or "")
                                 for mount in entry.get("Mounts") or []),
            # Names only: the auditor never records a secret value.
            "env_secrets": sorted({item.split("=", 1)[0] for item in environment
                                   if "=" in item
                                   and SECRET_PATTERN.search(item.split("=", 1)[0])
                                   and not FILE_REFERENCE.search(item.split("=", 1)[0])}),
        })
    return found


def evaluate(containers_list, exceptions=None):
    """Findings for every check that fails, minus the declared exceptions."""
    exceptions = EXCEPTIONS if exceptions is None else exceptions
    findings = []
    for container in containers_list:
        for name, severity, check in CHECKS:
            detail = check(container)
            if not detail:
                continue
            reason = exceptions.get((container["name"], name))
            if reason:
                continue
            findings.append({
                "container": container["name"],
                "check": name,
                "severity": severity,
                "detail": detail,
            })
    findings.sort(key=lambda item: (SEVERITY_ORDER.index(item["severity"]), item["check"],
                                    item["container"]))
    return findings


def suppressed(containers_list, exceptions=None):
    """Declared exceptions that actually applied (so the list cannot rot silently)."""
    exceptions = EXCEPTIONS if exceptions is None else exceptions
    applied = []
    for container in containers_list:
        for name, _, check in CHECKS:
            reason = exceptions.get((container["name"], name))
            if reason and check(container):
                applied.append({"container": container["name"], "check": name, "reason": reason})
    return applied


def counts(findings):
    summary = {severity: 0 for severity in SEVERITY_ORDER}
    for finding in findings:
        summary[finding["severity"]] += 1
    return summary


def report(containers_list, findings, applied, now=None):
    now = int(time.time() if now is None else now)
    return {
        "generated_at": now,
        "containers": [{"name": item["name"], "image": item["image"].split("@")[0],
                        "project": item["project"]} for item in containers_list],
        "findings": findings,
        "exceptions_applied": applied,
        "counts": counts(findings),
    }


def render_metrics(document, ok=True, now=None):
    now = int(time.time() if now is None else now)
    lines = [
        "# HELP yunanexus_runtime_findings Container runtime baseline violations.",
        "# TYPE yunanexus_runtime_findings gauge",
    ]
    per_check = {}
    for finding in document["findings"]:
        key = (finding["check"], finding["severity"])
        per_check[key] = per_check.get(key, 0) + 1
    for (check, severity), value in sorted(per_check.items()):
        lines.append('yunanexus_runtime_findings{check="%s",severity="%s"} %d'
                     % (check, severity, value))
    lines += [
        "# HELP yunanexus_runtimeaudit_success Whether the last audit completed.",
        "# TYPE yunanexus_runtimeaudit_success gauge",
        "yunanexus_runtimeaudit_success %d" % (1 if ok else 0),
        "# HELP yunanexus_runtimeaudit_timestamp_seconds When the last audit ran.",
        "# TYPE yunanexus_runtimeaudit_timestamp_seconds gauge",
        "yunanexus_runtimeaudit_timestamp_seconds %d" % now,
        "# HELP yunanexus_runtime_containers Containers covered by the audit.",
        "# TYPE yunanexus_runtime_containers gauge",
        "yunanexus_runtime_containers %d" % len(document["containers"]),
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


def summarise(document, limit=40):
    summary = document["counts"]
    lines = ["%d containers, %d finding(s) (%s)" % (
        len(document["containers"]), len(document["findings"]),
        ", ".join("%s %d" % (severity, summary[severity]) for severity in SEVERITY_ORDER
                  if summary.get(severity)) or "baseline satisfied")]
    current = None
    for finding in document["findings"][:limit]:
        if finding["check"] != current:
            current = finding["check"]
            lines.append("%s [%s]" % (current, finding["severity"]))
        lines.append("  - %s: %s" % (finding["container"], finding["detail"]))
    if len(document["findings"]) > limit:
        lines.append("  ... %d more" % (len(document["findings"]) - limit))
    if document["exceptions_applied"]:
        lines.append("declared exceptions in effect: %d" % len(document["exceptions_applied"]))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="audit the container runtime baseline")
    parser.add_argument("--json", action="store_true", help="print the full report")
    parser.add_argument("--gate", action="store_true",
                        help="exit 2 when a critical finding exists")
    args = parser.parse_args(argv)
    try:
        found = containers()
    except RuntimeError as error:
        log.error("%s", error)
        write(render_metrics({"findings": [], "containers": []}, ok=False))
        return 2
    findings = evaluate(found)
    document = report(found, findings, suppressed(found))
    WORK.mkdir(parents=True, exist_ok=True)
    (WORK / "report.json").write_text(json.dumps(document, indent=2, sort_keys=True) + "\n",
                                      encoding="utf-8")
    write(render_metrics(document))
    print(json.dumps(document, indent=2, sort_keys=True) if args.json else summarise(document))
    if args.gate and any(item["severity"] == "critical" for item in findings):
        log.error("critical runtime finding; gate refuses")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
