#!/usr/bin/env python3
"""Service level objectives, error budgets and burn rates.

Alerting so far answered "is something broken right now". This answers the question a
production owner actually gets asked: "how much of our reliability promise is left this
month?" Every request is either good or bad, the objective says how many bad ones are
affordable, and the budget is what remains.

SLIs come from the actuator counters already scraped (no new instrumentation):

    availability = 1 - (5xx / all requests)
    latency      = mean request duration

Latency is a mean, not a percentile, because the services do not publish histograms.
That is a real limitation, stated in the gap list rather than papered over: a mean hides
a slow minority.

Windows follow the Google SRE workbook: short windows catch fast burns, long windows
catch slow ones, and both are needed because each alone either pages on blips or sleeps
through a slow bleed.
"""
import argparse
import json
import logging
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
VM_URL = os.environ.get("YUNANEXUS_VM_URL", "http://127.0.0.1:8428")
WORK = R / "runtime/slo"
TEXTFILE = R / "runtime/node_exporter_textfile/slo.prom"
JOB = "yunanexus-services"
# Scrapes are not user traffic. On this host /actuator/* was 62% of all counted
# requests, so leaving it in would let monitoring volume drown the signal.
USER_TRAFFIC = 'uri!~"/actuator/.*"'
# A ratio is meaningless on a handful of requests: with a 99.5% objective, one failure
# only becomes visible at 200 requests. Below this a window is "insufficient data"
# rather than a breach.
MIN_REQUESTS = 200

# One objective for every service: interactive APIs, so 99.5% is the floor a user
# would notice. A payment or auth-critical path would warrant 99.9%.
TARGETS = {"availability": 0.995, "latency_mean_seconds": 0.25}
WINDOWS = {"5m": "5m", "15m": "15m", "1h": "1h", "6h": "6h", "24h": "24h",
           "7d": "7d", "28d": "28d"}
# Burn rate 1.0 spends the budget exactly at the rate that exhausts it over the window.
BURN_WINDOWS = ("1h", "6h")
# Synthetic probes publish one sample a minute, so a 30 minute window already holds 30
# samples - enough to judge availability where the API itself sees too few requests.
PROBE_PREFIX = "probe:"
PROBE_SECONDS = 60


def probe_minimum(window):
    """How many samples a probe window needs before its ratio means anything.

    Absolute thresholds would demand 30 samples from a 5 minute window that can only
    hold five. Requiring most of the window to be covered is the honest rule.
    """
    expected = max(1, window_seconds(window) / PROBE_SECONDS)
    # The floor must sit below a fully covered short window: `increase()` extrapolates and
    # a 5 minute window can legitimately report 4.4 probes, so requiring 5 would make it
    # permanently unjudgeable.
    return max(3, int(expected * 0.6))

log = logging.getLogger("slo")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


def query(expression, timeout=20, moment=None):
    """PromQL against VictoriaMetrics; returns the raw result list."""
    parameters = {"query": expression}
    if moment is not None:
        parameters["time"] = repr(float(moment))
    url = "%s/api/v1/query?%s" % (VM_URL, urllib.parse.urlencode(parameters))
    with urllib.request.urlopen(url, timeout=timeout) as response:
        payload = json.loads(response.read())
    if payload.get("status") != "success":
        raise RuntimeError("query failed: %s" % expression)
    return payload["data"]["result"]


def window_seconds(window):
    """"90m" / "6h" / "7d" -> seconds; used to compare a window with real history."""
    units = {"s": 1, "m": 60, "h": 3600, "d": 86400, "w": 604800}
    total = 0
    for value, unit in re.findall(r"(\d+)([smhdw])", window):
        total += int(value) * units[unit]
    return total


def has_data(offset_seconds, timeout=15):
    """Any request samples at that point in the past?"""
    try:
        series = query("count(count_over_time(http_server_requests_seconds_count[10m]))",
                       timeout=timeout, moment=time.time() - offset_seconds)
    except (urllib.error.URLError, OSError, RuntimeError, ValueError):
        return False
    return any(float(sample["value"][1]) > 0 for sample in series)


def history_seconds(probe=has_data, limit=32 * 86400):
    """How far back the metrics actually go.

    A 28-day SLO is meaningless on 90 minutes of data, and `increase[28d]` will happily
    return a number anyway by extrapolating what it has. Finding the real coverage lets
    the report say "cannot judge" instead of printing a confident lie.
    """
    if not probe(0):
        return 0
    low, high = 0, limit
    while high - low > 300:
        middle = (low + high) // 2
        if probe(middle):
            low = middle
        else:
            high = middle
    return low


def by_service(series, key="instance"):
    """{service: value} from a single-valued result set."""
    values = {}
    for sample in series:
        try:
            values[sample["metric"].get(key) or "unknown"] = float(sample["value"][1])
        except (KeyError, IndexError, ValueError):
            continue
    return values


def requests_total(window):
    return by_service(query(
        'sum(increase(http_server_requests_seconds_count{job="%s",%s}[%s])) by (instance)'
        % (JOB, USER_TRAFFIC, window)))


def requests_failed(window):
    return by_service(query(
        'sum(increase(http_server_requests_seconds_count{job="%s",%s,status=~"5.."}[%s]))'
        ' by (instance)' % (JOB, USER_TRAFFIC, window)))


def latency_mean(window):
    """Mean latency = total time spent / request count."""
    expression = (
        'sum(increase(http_server_requests_seconds_sum{job="%s",%s}[%s])) by (instance)'
        ' / sum(increase(http_server_requests_seconds_count{job="%s",%s}[%s])) by (instance)'
        % (JOB, USER_TRAFFIC, window, JOB, USER_TRAFFIC, window))
    return by_service(query(expression))


def budget(target, total, failed):
    """Error budget arithmetic for one service and window.

    A service that served nothing has spent nothing: returning None keeps "no traffic"
    from looking like a perfect score or a division error.
    """
    if not total:
        return None
    allowed_fraction = 1 - target
    bad_fraction = failed / total
    allowed = total * allowed_fraction
    remaining = (allowed - failed) / allowed if allowed else 0.0
    return {
        "requests": round(total, 1),
        "failed": round(failed, 1),
        "availability": round(1 - bad_fraction, 6),
        "allowed_failures": round(allowed, 1),
        # Unclamped on purpose: -1 means "just broke the objective", -9 means "spent ten
        # times the budget". Clamping at -1 would flatten that difference.
        "budget_remaining": round(remaining, 4),
        "burn_rate": round(bad_fraction / allowed_fraction, 3) if allowed_fraction else 0.0,
    }


def evaluate(windows=None, targets=None, reads=None, minimum=None, coverage=None):
    """Per-service, per-window SLO state.

    `reads` lets a test supply its own metric readers instead of a live backend.
    """
    windows = windows or WINDOWS
    targets = targets or TARGETS
    minimum = MIN_REQUESTS if minimum is None else minimum
    reads = reads or {
        "total": requests_total, "failed": requests_failed, "latency": latency_mean,
        "history": history_seconds,
    }
    if coverage is None:
        # Partial readers (tests, custom backfills) may not supply a history probe.
        coverage = reads["history"]() if "history" in reads else 0
    services = {}
    for label, window in windows.items():
        totals = reads["total"](window)
        failures = reads["failed"](window)
        latencies = reads["latency"](window)
        for service in sorted(set(totals) | set(failures)):
            total = totals.get(service, 0.0)
            failed = failures.get(service, 0.0)
            state = budget(targets["availability"], total, failed)
            if state is None:
                continue
            state["window"] = label
            state["service"] = service
            state["latency_mean"] = round(latencies.get(service, 0.0), 4)
            state["availability_target"] = targets["availability"]
            state["latency_target"] = targets["latency_mean_seconds"]
            state["coverage"] = coverage
            # None means "not enough traffic to judge", which must never be reported
            # as either compliant or breached.
            state["minimum"] = minimum
            enough = state["requests"] >= minimum
            # A window longer than the retained history is not a measurement.
            covered = window_seconds(window) <= coverage * 0.95
            state["confident"] = enough and covered
            state["covered"] = covered
            judgeable = enough and covered
            state["availability_met"] = (
                state["availability"] >= targets["availability"]) if judgeable else None
            state["latency_met"] = (
                state["latency_mean"] <= targets["latency_mean_seconds"]) if judgeable else None
            services.setdefault(service, {})[label] = state
    return services


def probe_readers(window):
    """(success ratio, probe count) per target for one window.

    Ratios come from the prober's counters, not from the `up` gauge: the gauge is
    rewritten every cycle and scraped several times per cycle, so averaging it would
    weight probes by scrape frequency and counting it would count scrapes.
    """
    probes = by_service(query('increase(yunanexus_probe_probes_total[%s])' % window),
                        key="target")
    successes = by_service(query('increase(yunanexus_probe_success_total[%s])' % window),
                           key="target")
    count = {}
    ratio = {}
    for target, total in probes.items():
        if total <= 0:
            continue
        count[target] = total
        ratio[target] = min(1.0, successes.get(target, 0.0) / total)
    return ratio, count


def evaluate_probes(windows=None, targets=None, coverage=None, reads=None, minimum=None):
    """Availability of the public path, scored with the same budget arithmetic.

    Keys are prefixed so a probe target never collides with a service instance name.
    """
    windows = windows or WINDOWS
    targets = targets or TARGETS
    reads = reads or probe_readers
    if coverage is None:
        coverage = 0
    scored = {}
    for label, window in windows.items():
        minimum = probe_minimum(window) if minimum is None else minimum
        ratios, counts = reads(window)
        for target in sorted(ratios):
            total = counts.get(target, 0.0)
            if not total:
                continue
            ratio = ratios[target]
            state = budget(targets["availability"], total, total * (1 - ratio))
            if state is None:
                continue
            state.update({
                "window": label,
                "service": PROBE_PREFIX + target,
                "latency_mean": 0.0,
                "availability_target": targets["availability"],
                "latency_target": targets["latency_mean_seconds"],
                "coverage": coverage,
            })
            state["minimum"] = minimum
            enough = state["requests"] >= minimum
            covered = window_seconds(window) <= coverage * 0.95
            state["confident"] = enough and covered
            state["covered"] = covered
            judgeable = enough and covered
            state["availability_met"] = (
                state["availability"] >= targets["availability"]) if judgeable else None
            # Probes say nothing about latency accuracy; leave it out of the verdict.
            state["latency_met"] = None
            scored.setdefault(state["service"], {})[label] = state
    return scored


def summarise(services, window="24h"):
    """Rows for the console table, worst first."""
    rows = []
    for service, windows in services.items():
        state = windows.get(window)
        if not state:
            continue
        rows.append(state)
    # Breaches first, then untested windows, then the healthiest.
    def rank(item):
        if item["confident"] and not item["availability_met"]:
            return (0, item["budget_remaining"])
        if not item["confident"]:
            return (1, item["budget_remaining"])
        return (2, item["budget_remaining"])

    rows.sort(key=rank)
    return rows


def document_history(services):
    """Coverage stored by evaluate() so the metric renderer needs no extra query."""
    for windows in services.values():
        for state in windows.values():
            return int(state.get("coverage", 0))
    return 0


def render_metrics(services, ok=True, now=None):
    now = int(time.time() if now is None else now)
    lines = [
        "# HELP yunanexus_slo_target_ratio Availability objective per service.",
        "# TYPE yunanexus_slo_target_ratio gauge",
    ]
    for service in sorted(services):
        target = next(iter(services[service].values()))["availability_target"]
        lines.append('yunanexus_slo_target_ratio{service="%s"} %s' % (service, target))
    blocks = [
        ("yunanexus_slo_availability_ratio", "availability",
         "Observed availability (1 - 5xx ratio)."),
        ("yunanexus_slo_error_budget_remaining_ratio", "budget_remaining",
         "Error budget left; negative means the objective is already broken."),
        ("yunanexus_slo_burn_rate", "burn_rate",
         "Budget spend rate; 1.0 spends exactly the whole budget over the window."),
        ("yunanexus_slo_requests", "requests", "Requests counted in the window."),
        ("yunanexus_slo_failures", "failed", "Failed requests (5xx) in the window."),
        ("yunanexus_slo_latency_mean_seconds", "latency_mean",
         "Mean request duration (no histograms published, so not a percentile)."),
    ]
    for metric, field, help_text in blocks:
        lines += ["# HELP %s %s" % (metric, help_text), "# TYPE %s gauge" % metric]
        for service in sorted(services):
            for label, state in sorted(services[service].items()):
                lines.append('%s{service="%s",window="%s"} %s'
                             % (metric, service, label, state[field]))
    lines += [
        "# HELP yunanexus_slo_met Whether the availability objective holds per window.",
        "# TYPE yunanexus_slo_met gauge",
    ]
    for service in sorted(services):
        for label, state in sorted(services[service].items()):
            # Only confident windows publish a verdict; -1 keeps an untested window
            # from looking like an outage in a dashboard.
            met = state["availability_met"]
            lines.append('yunanexus_slo_met{service="%s",window="%s"} %s'
                         % (service, label, -1 if met is None else (1 if met else 0)))
    lines += [
        "# HELP yunanexus_slo_history_seconds Metric history actually available.",
        "# TYPE yunanexus_slo_history_seconds gauge",
        "yunanexus_slo_history_seconds %d" % document_history(services),
        "# HELP yunanexus_slo_confident Whether the window had enough traffic to judge.",
        "# TYPE yunanexus_slo_confident gauge",
    ]
    for service in sorted(services):
        for label, state in sorted(services[service].items()):
            lines.append('yunanexus_slo_confident{service="%s",window="%s"} %d'
                         % (service, label, 1 if state["confident"] else 0))
    lines += [
        "# HELP yunanexus_slo_success Whether the last SLO evaluation completed.",
        "# TYPE yunanexus_slo_success gauge",
        "yunanexus_slo_success %d" % (1 if ok else 0),
        "# HELP yunanexus_slo_timestamp_seconds When the last SLO evaluation ran.",
        "# TYPE yunanexus_slo_timestamp_seconds gauge",
        "yunanexus_slo_timestamp_seconds %d" % now,
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


def render_table(rows, window="24h"):
    if not rows:
        return "no traffic in the %s window; nothing to score" % window
    lines = ["%-24s %10s %10s %10s %8s" % (
        "service", "avail %", "budget %", "burn", "latency ms")]
    for row in rows:
        if not row.get("covered", True):
            note = "  <- history only %.1fh, window too long" % (row["coverage"] / 3600.0)
        elif not row["confident"]:
            note = "  <- insufficient data (%d samples, need %d)" % (
                row["requests"], row.get("minimum", MIN_REQUESTS))
        elif not row["availability_met"]:
            note = "  <- below objective"
        elif row["latency_met"] is False:
            note = "  <- latency above target"
        else:
            note = ""
        lines.append("%-24s %10.3f %10.1f %10.2f %8.0f%s" % (
            row["service"], row["availability"] * 100,
            max(row["budget_remaining"], -100) * 100, row["burn_rate"],
            row["latency_mean"] * 1000, note))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="compute SLOs and error budgets")
    parser.add_argument("--json", action="store_true", help="print the full report")
    parser.add_argument("--window", default="24h", choices=sorted(WINDOWS),
                        help="window to show in the console table")
    args = parser.parse_args(argv)
    try:
        coverage = history_seconds()
        services = evaluate(coverage=coverage)
        # Probes exist because the API's own request volume cannot support an objective.
        services.update(evaluate_probes(coverage=coverage))
    except (urllib.error.URLError, OSError, RuntimeError, ValueError) as error:
        log.error("cannot evaluate SLOs: %s", error)
        write(render_metrics({}, ok=False))
        return 2
    WORK.mkdir(parents=True, exist_ok=True)
    document = {"generated_at": int(time.time()), "targets": TARGETS,
                "job": JOB, "services": services}
    (WORK / "report.json").write_text(json.dumps(document, indent=2, sort_keys=True) + "\n",
                                      encoding="utf-8")
    write(render_metrics(services))
    if args.json:
        print(json.dumps(document, indent=2, sort_keys=True))
    else:
        print(render_table(summarise(services, args.window), args.window))
    return 0


if __name__ == "__main__":
    sys.exit(main())
