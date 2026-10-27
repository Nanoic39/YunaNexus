#!/usr/bin/env python3
"""Evaluate alert rules against VictoriaMetrics and deliver notifications.

Runs every couple of minutes from maintenance.py. Deliberately dependency-free:
no Alertmanager (saves ~80 MiB), and rule evaluation is just a PromQL query plus
a comparison, which is enough for the handful of conditions that matter here.

Delivery target: YUNANEXUS_ALERT_WEBHOOK (a chat-robot or relay URL). When it is
unset, alerts are still logged and kept in runtime/alerts-state.json so nothing
is silently lost, they simply do not leave the host.
"""
import json
import logging
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
VM_URL = os.environ.get("YUNANEXUS_VM_URL", "http://127.0.0.1:8428")
WEBHOOK = os.environ.get("YUNANEXUS_ALERT_WEBHOOK", "").strip()
STATE = R / "runtime/alerts-state.json"
LOG_FILE = R / "logs/alerts.log"
REPEAT_SECONDS = {"critical": 1800, "warning": 3600}
MIB = 1024 * 1024
GIB = 1024 * MIB

log = logging.getLogger("alerts")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


# Each rule yields series of (labels, value); the key is used for de-duplication.
RULES = [
    # SLO rules only fire on windows the evaluator could actually judge: `and on(...)`
    # with the confidence gauge keeps a thin-data window from paging anyone.
    {
        "name": "slo_budget_exhausted",
        "severity": "critical",
        "query": ('yunanexus_slo_error_budget_remaining_ratio < 0'
                  ' and on (service, window) yunanexus_slo_confident == 1'),
        "compare": (">", 0),
        "summary": "服务错误预算已超支（可用性低于目标）",
        "runbook": "runtime/slo/report.json 定位服务；查 5xx 来源与最近发布，必要时回滚",
    },
    {
        "name": "slo_burn_fast",
        "severity": "critical",
        "query": ('yunanexus_slo_burn_rate{window="1h"} > 14.4'
                  ' and on (service, window) yunanexus_slo_confident == 1'),
        "compare": (">", 0),
        "summary": "服务在 1 小时内烧掉超过 2% 的月度错误预算（快速燃烧）",
        "runbook": "对照 HTTP 5xx 速率与日志错误速率，确认是否为一次发布或下游故障",
    },
    {
        "name": "slo_burn_slow",
        "severity": "warning",
        "query": ('yunanexus_slo_burn_rate{window="6h"} > 6'
                  ' and on (service, window) yunanexus_slo_confident == 1'),
        "compare": (">", 0),
        "summary": "服务持续以 6 倍速率消耗错误预算（缓慢燃烧）",
        "runbook": "6 小时内按当前速率将耗尽预算；排查持续性错误而非瞬时抖动",
    },
    {
        "name": "slo_evaluation_failed",
        "severity": "warning",
        "query": "yunanexus_slo_success",
        "compare": ("<", 1),
        "summary": "SLO 评估最近一次执行失败",
        "runbook": "python3 ops/slo.py 手动确认；检查 VictoriaMetrics 可用性",
    },
    {
        "name": "runtime_baseline_critical",
        "severity": "critical",
        "query": 'yunanexus_runtime_findings{severity="critical"}',
        "compare": (">", 0),
        "summary": "容器运行时基线出现严重偏离（特权容器 / 挂载 docker socket）",
        "runbook": "runtime/runtimeaudit/report.json 查看；docker inspect 核对后立刻回退该容器配置",
    },
    {
        "name": "runtime_baseline_high",
        "severity": "warning",
        "query": 'yunanexus_runtime_findings{severity="high"}',
        "compare": (">", 18),
        "summary": "容器运行时基线高危偏离数量上升（基线 16 条，多为环境变量传密钥）",
        "runbook": "对比上一份 runtime/runtimeaudit/report.json，确认是否有新容器未走 compose",
    },
    {
        "name": "runtimeaudit_failed",
        "severity": "warning",
        "query": "yunanexus_runtimeaudit_success",
        "compare": ("<", 1),
        "summary": "容器运行时基线审计最近一次执行失败",
        "runbook": "python3 ops/runtimeaudit.py 手动确认",
    },
    {
        "name": "image_vulnerability_critical",
        "severity": "critical",
        "query": 'yunanexus_image_vulnerabilities{severity="critical"}',
        "compare": (">", 0),
        "summary": "运行中容器的基础镜像含严重漏洞（OS 包）",
        "runbook": "runtime/imagescan/report.json 查看；重建基础镜像并 apt/apk upgrade 后重新发布",
    },
    {
        "name": "image_unmanaged_containers",
        "severity": "warning",
        "query": "yunanexus_image_unmanaged_containers",
        "compare": (">", 0),
        "summary": "存在不在受管 compose 项目内的容器（绕过发布链路）",
        "runbook": "docker ps 确认；确认无用后停用，避免旧版本继续访问生产库",
    },
    {
        "name": "imagescan_failed",
        "severity": "warning",
        "query": "yunanexus_imagescan_success",
        "compare": ("<", 1),
        "summary": "容器镜像扫描最近一次执行失败",
        "runbook": "python3 ops/imagescan.py 手动确认",
    },
    {
        "name": "vulnerability_critical",
        "severity": "critical",
        "query": 'yunanexus_vulnerabilities{severity="critical"}',
        "compare": (">", 0),
        "summary": "已部署版本含未接受的严重漏洞（CVSS 9.0+）",
        "runbook": "runtime/vulnscan/report-*.json 查看详情；升级依赖或写入 ops/vuln-ignore.txt 并说明理由",
    },
    {
        "name": "vulnerability_high",
        "severity": "warning",
        "query": 'yunanexus_vulnerabilities{severity="high"}',
        "compare": (">", 0),
        "summary": "已部署版本含未接受的高危漏洞（CVSS 7.0-8.9）",
        "runbook": "runtime/vulnscan/report-*.json 查看；按修复版本升级依赖",
    },
    {
        "name": "vulnscan_failed",
        "severity": "warning",
        "query": "yunanexus_vulnscan_success",
        "compare": ("<", 1),
        "summary": "漏洞扫描最近一次执行失败（结果不可信）",
        "runbook": "python3 ops/vulnscan.py 手动确认；检查 OSV 可达性与依赖清单",
    },
    {
        "name": "vulnscan_stale",
        "severity": "warning",
        "query": "time() - yunanexus_vulnscan_timestamp_seconds",
        "compare": (">", 8 * 24 * 3600),
        "summary": "漏洞扫描超过 8 天未运行",
        "runbook": "检查 maintenance.py 与 ops/vulnscan.py",
    },
    {
        "name": "migration_drift",
        "severity": "critical",
        "query": "yunanexus_migration_drift",
        "compare": (">", 0),
        "summary": "已应用的迁移文件被改动（库结构与版本历史不一致）",
        "runbook": "ops/migrate.py --status 查看；禁止直接改已应用文件，应新增版本",
    },
    {
        "name": "migration_pending",
        "severity": "warning",
        "query": "yunanexus_migration_pending",
        "compare": (">", 0),
        "summary": "存在未应用的数据库迁移（发布将被闸门拦下）",
        "runbook": "ops/migrate.py --status 确认后执行 --apply",
    },
    {
        "name": "migration_check_stale",
        "severity": "warning",
        "query": "time() - yunanexus_migration_check_timestamp_seconds",
        "compare": (">", 7200),
        "summary": "迁移巡检超过 2 小时未运行",
        "runbook": "检查 maintenance.py 是否在运行、ops/migrate.py 是否可用",
    },
    {
        "name": "host_memory_low",
        "severity": "critical",
        "query": "node_memory_MemAvailable_bytes / %d" % MIB,
        "compare": ("<", 300),
        "summary": "可用内存不足 300 MiB",
    },
    {
        "name": "host_swap_pressure",
        "severity": "warning",
        "query": "(1 - node_memory_SwapFree_bytes / node_memory_SwapTotal_bytes) * 100",
        "compare": (">", 90),
        "summary": "swap 使用率超过 90%，内存已长期超额分配",
    },
    {
        # Two different filesystems, two different budgets: the root disk is only
        # 20 GiB while the platform disk is 98 GiB, so one shared threshold would
        # either fire forever or never.
        "name": "disk_space_critical",
        "severity": "critical",
        "query": ('node_filesystem_avail_bytes{mountpoint="/"} / %d < 2 or '
                  'node_filesystem_avail_bytes{mountpoint="/YunaNexus"} / %d < 5' % (GIB, GIB)),
        "compare": (">", 0),
        "summary": "磁盘剩余空间低于安全线（/ < 2 GiB 或 /YunaNexus < 5 GiB）",
    },
    {
        "name": "disk_space_warning",
        "severity": "warning",
        "query": ('(1 - node_filesystem_avail_bytes{mountpoint=~"/|/YunaNexus"} '
                  '/ node_filesystem_size_bytes{mountpoint=~"/|/YunaNexus"}) * 100'),
        "compare": (">", 85),
        "summary": "磁盘使用率超过 85%",
    },
    {
        "name": "service_down",
        "severity": "critical",
        "query": 'up{job="yunanexus-services"}',
        "compare": ("==", 0),
        "summary": "服务指标抓取失败，服务可能已停止",
    },
    {
        "name": "jvm_heap_high",
        "severity": "warning",
        "query": ('sum by (service) (jvm_memory_used_bytes{area="heap"})'
                  ' / sum by (service) (jvm_memory_max_bytes{area="heap"}) * 100'),
        "compare": (">", 90),
        "summary": "JVM 堆占用超过 90%，存在 OOM 风险",
    },
    {
        "name": "container_memory_high",
        "severity": "warning",
        "query": "yunanexus_container_memory_ratio * 100",
        "compare": (">", 90),
        "summary": "容器内存接近上限，可能被 OOM 杀掉",
    },
    {
        "name": "host_metrics_down",
        "severity": "critical",
        "query": 'up{job="host"}',
        "compare": ("==", 0),
        "summary": "宿主机指标断层，node_exporter 或抓取链路已失效",
    },
    {
        "name": "log_collector_stale",
        "severity": "critical",
        "query": "time() - yunanexus_logship_last_cycle_timestamp_seconds",
        "compare": (">", 300),
        "summary": "日志采集器已停止上报，日志盲区",
    },
    {
        "name": "log_error_spike",
        "severity": "warning",
        "query": ('sum by (service) (increase(yunanexus_log_lines_total'
                  '{level=~"ERROR|FATAL"}[15m]))'),
        "compare": (">", 5),
        "summary": "15 分钟内错误日志超过 5 条",
    },
    {
        "name": "container_restarted",
        "severity": "warning",
        "query": "increase(yunanexus_container_restarts_total[15m])",
        "compare": (">", 0),
        "summary": "容器在最近 15 分钟内重启过",
    },
]


# Rules that must stay quiet until a counter-based signal has settled: a collector
# restart drops its counters, and Prometheus semantics then report the whole
# previous value as a spike inside the increase() window.
SETTLE = {
    "log_error_spike": ("yunanexus_logship_uptime_seconds", 1800),
}


def settled(rule_name, cache):
    requirement = SETTLE.get(rule_name)
    if not requirement:
        return True
    metric, minimum = requirement
    if metric not in cache:
        try:
            series = query(metric)
            cache[metric] = float(series[0]["value"][1]) if series else 0.0
        except (urllib.error.URLError, OSError, ValueError, RuntimeError, IndexError):
            cache[metric] = 0.0
    return cache[metric] >= minimum


def local_checks(now):
    """Checks that read the filesystem instead of the metrics database."""
    findings = []
    newest, stamp = None, None
    for entry in sorted((R / "backups").glob("*/manifest.json")):
        modified = entry.stat().st_mtime
        if newest is None or modified > newest:
            newest, stamp = modified, entry.parent.name
    if newest is None:
        findings.append(({"check": "backup_missing"}, 0, "从未成功完成过备份"))
    else:
        age_hours = (now - newest) / 3600
        if age_hours > 26:
            findings.append(({"check": "backup_stale", "stamp": stamp},
                             round(age_hours, 1), "最近一次备份已超过 26 小时"))
    state_file = R / "runtime/cd-state.json"
    if state_file.is_file():
        try:
            state = json.loads(state_file.read_text())
        except ValueError:
            state = {}
        for kind, detail in state.items():
            if isinstance(detail, dict) and detail.get("blocked"):
                findings.append(({"check": "deploy_blocked", "kind": kind}, 1,
                                 "自动部署已熔断，需要人工处理"))
    certificates = R / "config/certs"
    if certificates.is_dir():
        for path in certificates.glob("*.pem"):
            try:
                result = subprocess.run(["openssl", "x509", "-enddate", "-noout", "-in", str(path)],
                                        capture_output=True, text=True, timeout=10)
            except (OSError, subprocess.SubprocessError):
                continue
            text = result.stdout.strip()
            if "=" not in text:
                continue
            try:
                expiry = datetime.strptime(text.split("=", 1)[1].strip(), "%b %d %H:%M:%S %Y %Z")
            except ValueError:
                continue
            days = (expiry.replace(tzinfo=timezone.utc) - datetime.now(timezone.utc)).days
            if days < 14:
                findings.append(({"check": "cert_expiring", "file": path.name}, days,
                                 "证书将在 %d 天后过期" % days))
    return findings


def query(expression):
    url = "%s/api/v1/query?%s" % (VM_URL, urllib.parse.urlencode({"query": expression}))
    with urllib.request.urlopen(url, timeout=15) as response:
        payload = json.loads(response.read())
    if payload.get("status") != "success":
        raise RuntimeError("query failed: %s" % expression)
    return payload["data"]["result"]


def matches(operator, threshold, value):
    if operator == "<":
        return value < threshold
    if operator == ">":
        return value > threshold
    if operator == "==":
        return value == threshold
    raise ValueError("unsupported operator %s" % operator)


def evaluate(now=None):
    now = now or time.time()
    firing = {}
    failures = 0
    attempted = 0
    settle_cache = {}
    for rule in RULES:
        if not settled(rule["name"], settle_cache):
            log.info("rule %s settling in; skipped until its signal is stable", rule["name"])
            continue
        attempted += 1
        try:
            series = query(rule["query"])
        except (urllib.error.URLError, OSError, ValueError, RuntimeError) as error:
            failures += 1
            log.warning("rule %s skipped: %s", rule["name"], error)
            continue
        for sample in series:
            try:
                value = float(sample["value"][1])
            except (KeyError, IndexError, ValueError):
                continue
            if not matches(rule["compare"][0], rule["compare"][1], value):
                continue
            labels = {k: v for k, v in sample.get("metric", {}).items() if k not in ("__name__",)}
            key = "%s|%s" % (rule["name"], json.dumps(labels, sort_keys=True))
            firing[key] = {
                "key": key,
                "name": rule["name"],
                "severity": rule["severity"],
                "summary": rule["summary"],
                "labels": labels,
                "value": round(value, 2),
            }
    if attempted and failures >= attempted:
        # Every query failed: the alerting path is blind, which is itself the
        # incident worth reporting. Delivery does not depend on VictoriaMetrics.
        firing["monitoring_backend_down|{}"] = {
            "key": "monitoring_backend_down|{}",
            "name": "monitoring_backend_down",
            "severity": "critical",
            "summary": "VictoriaMetrics 查询全部失败，指标与告警已失去依据",
            "labels": {"check": "monitoring_backend_down"},
            "value": failures,
        }
    for labels, value, summary in local_checks(now):
        key = "%s|%s" % (labels.get("check"), json.dumps(labels, sort_keys=True))
        firing[key] = {
            "key": key,
            "name": labels.get("check"),
            "severity": "critical",
            "summary": summary,
            "labels": labels,
            "value": value,
        }
    return firing


def load_state():
    if STATE.is_file():
        try:
            return json.loads(STATE.read_text())
        except ValueError:
            pass
    return {}


def save_state(state):
    STATE.parent.mkdir(parents=True, exist_ok=True)
    pending = STATE.with_suffix(".tmp")
    pending.write_text(json.dumps(state, indent=2, sort_keys=True))
    pending.replace(STATE)


def deliver(alert, status):
    record = {
        "source": "yunanexus",
        "status": status,
        "alert": alert["name"],
        "severity": alert["severity"],
        "summary": alert["summary"],
        "value": alert["value"],
        "labels": alert["labels"],
        "time": datetime.now().isoformat(timespec="seconds"),
    }
    line = "%s %s %s | %s | value=%s | %s" % (
        record["time"], status.upper(), record["severity"], record["alert"],
        record["value"], record["summary"])
    log.log(logging.ERROR if record["severity"] == "critical" else logging.WARNING, line)
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    with LOG_FILE.open("a") as handle:
        handle.write(line + "\n")
    if not WEBHOOK:
        return
    body = json.dumps({"msg_type": "text", "content": {"text": line}, **record}).encode()
    request = urllib.request.Request(WEBHOOK, data=body,
                                     headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            response.read()
    except (urllib.error.URLError, OSError) as error:
        log.warning("webhook delivery failed: %s", error)


def main():
    state = load_state()
    now = time.time()
    firing = evaluate(now)
    for key, alert in firing.items():
        previous = state.get(key)
        repeat = REPEAT_SECONDS.get(alert["severity"], 3600)
        if previous and now - float(previous.get("last_sent", 0)) < repeat:
            previous["last_seen"] = now
            continue
        deliver(alert, "firing")
        state[key] = {
            "first_seen": (previous or {}).get("first_seen", now),
            "last_seen": now,
            "last_sent": now,
            "alert": alert,
        }
    for key in list(state):
        if key in firing:
            continue
        deliver(state[key]["alert"], "resolved")
        state.pop(key)
    save_state(state)
    log.info("evaluated %d rules: %d firing, %d tracked", len(RULES), len(firing), len(state))
    return 0


if __name__ == "__main__":
    sys.exit(main())
