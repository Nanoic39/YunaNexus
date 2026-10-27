#!/usr/bin/env python3
"""Database health: structure, indexes, capacity, and what we cannot see.

The platform has 51 tables across ten schemas and nothing watches them. This reports
what is checkable without a restart (engine, collation, keys, redundancy, connections,
buffer pool, datadir composition, growth) and, just as loudly, what is not checkable at
all: performance_schema and the slow query log are both off, so there is currently zero
query-level visibility.

Read-only by construction: every statement here is a SELECT or SHOW.
"""
import argparse
import importlib.util
import json
import logging
import os
import sys
import time
from pathlib import Path

R = Path(os.environ.get("YUNANEXUS_PLATFORM_ROOT", "/YunaNexus/platform"))
WORK = R / "runtime/dbhealth"
HISTORY = WORK / "history.jsonl"
TEXTFILE = R / "runtime/node_exporter_textfile/db.prom"
MIB = 1024 ** 2
GIB = 1024 ** 3
HISTORY_DAYS = 90
MIN_SPAN = 3600

CONNECTION_WARN = 0.80
HIT_RATIO_WARN = 0.98
GROWTH_WARN = 200 * MIB       # per schema, per day

log = logging.getLogger("dbhealth")
log.setLevel(logging.INFO)
if not log.handlers:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    log.addHandler(handler)


def _module(name, filename):
    spec = importlib.util.spec_from_file_location(name, R / "ops" / filename)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    if hasattr(module, "log"):
        module.log.handlers.clear()
    return module


def robust_slope(points, minimum=3, span=MIN_SPAN):
    """Reuse the capacity fit so both tools agree on what a trend is."""
    try:
        return _module("capacity_for_db", "capacity.py").robust_slope(
            points, minimum=minimum, span=span)
    except Exception:                                    # noqa: BLE001 - fall back
        if len(points) < minimum or points[-1][0] - points[0][0] < span:
            return None
        deltas = sorted((b[1] - a[1]) / (b[0] - a[0])
                        for a, b in zip(points, points[1:]) if b[0] > a[0])
        if not deltas:
            return None
        middle = len(deltas) // 2
        median = deltas[middle] if len(deltas) % 2 else (deltas[middle - 1] + deltas[middle]) / 2
        return median * 86400


# ------------------------------------------------------------------ queries


STATEMENTS = {
    "server": "SELECT VERSION(), @@max_connections, "
              "(SELECT VARIABLE_VALUE FROM performance_schema.global_status "
              " WHERE VARIABLE_NAME='Uptime')",
    "settings": "SHOW VARIABLES WHERE Variable_name IN "
                "('performance_schema','slow_query_log','long_query_time','log_bin',"
                "'binlog_expire_logs_seconds','innodb_redo_log_capacity','log_output')",
    "counters": "SHOW GLOBAL STATUS WHERE Variable_name IN "
                "('Threads_connected','Threads_running','Max_used_connections',"
                "'Aborted_connects','Innodb_buffer_pool_reads',"
                "'Innodb_buffer_pool_read_requests','Slow_queries','Questions')",
    "tables": "SELECT table_schema, table_name, IFNULL(engine,'-'), "
              "IFNULL(table_collation,'-'), IFNULL(table_rows,0), "
              "IFNULL(data_length,0)+IFNULL(index_length,0) "
              "FROM information_schema.tables WHERE table_schema LIKE 'yunanexus%' "
              "AND table_type='BASE TABLE' ORDER BY 6 DESC",
    "no_primary_key": "SELECT t.table_schema, t.table_name FROM information_schema.tables t "
                      "WHERE t.table_schema LIKE 'yunanexus%' AND t.table_type='BASE TABLE' "
                      "AND NOT EXISTS (SELECT 1 FROM information_schema.statistics s "
                      "WHERE s.table_schema=t.table_schema AND s.table_name=t.table_name "
                      "AND s.index_name='PRIMARY')",
    "redundant": "SELECT table_schema, table_name, redundant_index_name, dominant_index_name "
                 "FROM sys.schema_redundant_indexes",
    "datadir": "SELECT file_name, IFNULL(ROUND(total_extents*extent_size,0),0) "
               "FROM information_schema.files WHERE file_name LIKE '%ib%' "
               "OR file_name LIKE '%binlog%' OR file_name LIKE '%undo%' "
               "ORDER BY 2 DESC LIMIT 20",
}


def rows(query, sql):
    """Run a statement and split tab separated output into lists."""
    output = query(sql)
    parsed = []
    for line in (output or "").splitlines():
        if not line.strip():
            continue
        parsed.append(line.split("\t"))
    return parsed


def integer(text, default=0):
    try:
        return int(float(text))
    except (TypeError, ValueError):
        return default


def collect(query):
    """Everything measurable, in one pass."""
    measurement = {"at": int(time.time())}
    version = rows(query, STATEMENTS["server"])
    if version:
        measurement["version"] = version[0][0]
        measurement["max_connections"] = integer(version[0][1])
        measurement["uptime"] = integer(version[0][2])
    measurement["settings"] = {row[0]: row[1] for row in rows(query, STATEMENTS["settings"])
                               if len(row) >= 2}
    measurement["counters"] = {row[0]: integer(row[1])
                               for row in rows(query, STATEMENTS["counters"]) if len(row) >= 2}
    tables = []
    for row in rows(query, STATEMENTS["tables"]):
        if len(row) < 6:
            continue
        tables.append({"schema": row[0], "table": row[1], "engine": row[2],
                       "collation": row[3], "rows": integer(row[4]),
                       "bytes": integer(row[5])})
    measurement["tables"] = tables
    measurement["no_primary_key"] = [{"schema": row[0], "table": row[1]}
                                     for row in rows(query, STATEMENTS["no_primary_key"])
                                     if len(row) >= 2]
    measurement["redundant"] = [{"schema": row[0], "table": row[1],
                                 "redundant": row[2], "dominant": row[3]}
                                for row in rows(query, STATEMENTS["redundant"])
                                if len(row) >= 4]
    datadir = []
    for row in rows(query, STATEMENTS["datadir"]):
        if len(row) >= 2:
            datadir.append({"file": row[0], "bytes": integer(row[1])})
    measurement["datadir"] = datadir
    measurement["schemas"] = schema_totals(tables)
    return measurement


def schema_totals(tables):
    totals = {}
    for table in tables:
        entry = totals.setdefault(table["schema"], {"bytes": 0, "tables": 0, "rows": 0})
        entry["bytes"] += table["bytes"]
        entry["rows"] += table["rows"]
        entry["tables"] += 1
    return totals


def hit_ratio(counters):
    reads = counters.get("Innodb_buffer_pool_reads", 0)
    requests = counters.get("Innodb_buffer_pool_read_requests", 0)
    if not requests:
        return None
    return round(1 - reads / requests, 5)


def connection_ratio(counters, max_connections):
    if not max_connections:
        return None
    return round(counters.get("Threads_connected", 0) / max_connections, 3)


def query_visibility(settings):
    """Whether any query-level evidence exists at all."""
    performance_schema = settings.get("performance_schema", "OFF").upper() in ("ON", "1")
    slow_log = settings.get("slow_query_log", "OFF").upper() in ("ON", "1")
    return performance_schema or slow_log


def collations(measurement):
    seen = {}
    for table in measurement.get("tables", []):
        seen.setdefault(table["collation"], []).append(table)
    return seen


# ------------------------------------------------------------------ findings


def findings(measurement):
    """[(severity, name, detail)] - what a reviewer would raise."""
    found = []
    counters = measurement.get("counters", {})
    settings = measurement.get("settings", {})
    if not measurement.get("version"):
        return [("critical", "unreachable", "无法连接数据库或查询失败")]

    if not query_visibility(settings):
        found.append(("high", "no_query_visibility",
                      "performance_schema 与慢查询日志均为 OFF，查询层完全没有可见性"))
    if settings.get("slow_query_log", "OFF").upper() in ("OFF", "0"):
        found.append(("medium", "slow_log_off",
                      "慢查询日志关闭（long_query_time=%s）" % settings.get("long_query_time", "?")))

    for table in measurement.get("no_primary_key", []):
        found.append(("critical", "missing_primary_key",
                      "%s.%s 无主键" % (table["schema"], table["table"])))
    for table in measurement.get("tables", []):
        if table["engine"].upper() != "INNODB":
            found.append(("high", "non_innodb",
                          "%s.%s 引擎为 %s" % (table["schema"], table["table"], table["engine"])))

    seen = collations(measurement)
    if len(seen) > 1:
        detail = "、".join("%s × %d" % (name, len(items)) for name, items in sorted(seen.items()))
        found.append(("high", "collation_mismatch",
                      "同一平台混用 %d 种排序规则：%s（跨表 JOIN 会走不上索引）"
                      % (len(seen), detail)))

    if measurement.get("redundant"):
        found.append(("medium", "redundant_index",
                      "%d 个冗余索引" % len(measurement["redundant"])))

    ratio = connection_ratio(counters, measurement.get("max_connections"))
    if ratio is not None and ratio >= CONNECTION_WARN:
        found.append(("high", "connections_high",
                      "连接占用 %.0f%%（%d/%d）" % (ratio * 100, counters.get("Threads_connected", 0),
                                                measurement.get("max_connections"))))
    hit = hit_ratio(counters)
    if hit is not None and hit < HIT_RATIO_WARN:
        found.append(("medium", "buffer_pool_hit_low", "缓冲池命中率 %.3f" % hit))
    return found


def totals(measurement):
    return {"tables": len(measurement.get("tables", [])),
            "schemas": len(measurement.get("schemas", {})),
            "bytes": sum(entry["bytes"] for entry in measurement.get("schemas", {}).values()),
            "rows": sum(entry["rows"] for entry in measurement.get("schemas", {}).values())}


# ------------------------------------------------------------------ history


def append_history(measurement, path=None):
    path = Path(path or HISTORY)
    path.parent.mkdir(parents=True, exist_ok=True)
    record = {"at": measurement["at"],
              "schemas": {name: entry["bytes"]
                          for name, entry in measurement.get("schemas", {}).items()},
              "datadir": sum(item["bytes"] for item in measurement.get("datadir", [])),
              "tables": len(measurement.get("tables", []))}
    with path.open("a", encoding="utf-8") as sink:
        sink.write(json.dumps(record, sort_keys=True) + "\n")
    return path


def load_history(path=None, now=None, days=HISTORY_DAYS):
    path = Path(path or HISTORY)
    now = now or time.time()
    if not path.exists():
        return []
    records = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            record = json.loads(line)
        except ValueError:
            continue
        if isinstance(record.get("at"), (int, float)) and record["at"] >= now - days * 86400:
            records.append(record)
    records.sort(key=lambda item: item["at"])
    return records


def growth(history):
    """{schema: bytes per day} plus datadir total."""
    rates = {}
    names = {name for record in history for name in (record.get("schemas") or {})}
    for name in names:
        points = [(record["at"], float(record["schemas"][name]))
                  for record in history if name in (record.get("schemas") or {})]
        rate = robust_slope(points)
        if rate is not None:
            rates[name] = round(rate, 1)
    datadir = [(record["at"], float(record["datadir"]))
               for record in history if record.get("datadir") is not None]
    datadir_rate = robust_slope(datadir)
    return rates, (round(datadir_rate, 1) if datadir_rate is not None else None)


# ------------------------------------------------------------------ output


def render_metrics(measurement, found, rates, datadir_rate, ok=True, now=None):
    now = int(time.time() if now is None else now)
    lines = ["# HELP yunanexus_db_up Whether the database answered.",
             "# TYPE yunanexus_db_up gauge",
             "yunanexus_db_up %d" % (1 if measurement.get("version") else 0)]
    counters = measurement.get("counters", {})
    lines += ["# HELP yunanexus_db_connections Current connections.",
              "# TYPE yunanexus_db_connections gauge"]
    for state, key in (("connected", "Threads_connected"), ("running", "Threads_running"),
                       ("max_used", "Max_used_connections")):
        if key in counters:
            lines.append('yunanexus_db_connections{state="%s"} %d' % (state, counters[key]))
    if measurement.get("max_connections"):
        lines.append('yunanexus_db_connections{state="max"} %d'
                     % measurement["max_connections"])
    ratio = connection_ratio(counters, measurement.get("max_connections"))
    if ratio is not None:
        lines += ["# HELP yunanexus_db_connection_ratio Fraction of max_connections in use.",
                  "# TYPE yunanexus_db_connection_ratio gauge",
                  "yunanexus_db_connection_ratio %s" % ratio]
    hit = hit_ratio(counters)
    if hit is not None:
        lines += ["# HELP yunanexus_db_buffer_pool_hit_ratio InnoDB buffer pool hit ratio.",
                  "# TYPE yunanexus_db_buffer_pool_hit_ratio gauge",
                  "yunanexus_db_buffer_pool_hit_ratio %s" % hit]
    lines += ["# HELP yunanexus_db_schema_bytes Data plus index bytes per schema.",
              "# TYPE yunanexus_db_schema_bytes gauge"]
    for name, entry in sorted(measurement.get("schemas", {}).items()):
        lines.append('yunanexus_db_schema_bytes{schema="%s"} %d' % (name, entry["bytes"]))
    lines += ["# HELP yunanexus_db_schema_growth_bytes_per_day Observed growth per schema.",
              "# TYPE yunanexus_db_schema_growth_bytes_per_day gauge"]
    for name, rate in sorted(rates.items()):
        lines.append('yunanexus_db_schema_growth_bytes_per_day{schema="%s"} %s' % (name, rate))
    lines += ["# HELP yunanexus_db_datadir_bytes Total size of the InnoDB data files.",
              "# TYPE yunanexus_db_datadir_bytes gauge",
              "yunanexus_db_datadir_bytes %d"
              % sum(item["bytes"] for item in measurement.get("datadir", []))]
    if datadir_rate is not None:
        lines += ["# HELP yunanexus_db_datadir_growth_bytes_per_day Observed growth.",
                  "# TYPE yunanexus_db_datadir_growth_bytes_per_day gauge",
                  "yunanexus_db_datadir_growth_bytes_per_day %s" % datadir_rate]
    lines += ["# HELP yunanexus_db_query_visibility Whether query level evidence exists.",
              "# TYPE yunanexus_db_query_visibility gauge",
              "yunanexus_db_query_visibility %d"
              % (1 if query_visibility(measurement.get("settings", {})) else 0),
              "# HELP yunanexus_db_redundant_indexes Redundant index count.",
              "# TYPE yunanexus_db_redundant_indexes gauge",
              "yunanexus_db_redundant_indexes %d" % len(measurement.get("redundant", [])),
              "# HELP yunanexus_db_findings Findings by severity.",
              "# TYPE yunanexus_db_findings gauge"]
    for severity in ("critical", "high", "medium"):
        lines.append('yunanexus_db_findings{severity="%s"} %d'
                     % (severity, sum(1 for item in found if item[0] == severity)))
    lines += ["# HELP yunanexus_db_success Whether the last run completed.",
              "# TYPE yunanexus_db_success gauge",
              "yunanexus_db_success %d" % (1 if ok else 0),
              "# HELP yunanexus_db_timestamp_seconds When the measurement was taken.",
              "# TYPE yunanexus_db_timestamp_seconds gauge",
              "yunanexus_db_timestamp_seconds %d" % now]
    return "\n".join(lines) + "\n"


def write(text, target=None):
    target = Path(target or TEXTFILE)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(".prom.tmp")
    temporary.write_text(text, encoding="utf-8")
    os.chmod(temporary, 0o644)
    temporary.replace(target)
    return target


def render_table(measurement, found, rates, datadir_rate):
    summary = totals(measurement)
    lines = [
        "database  %s   tables %d   schemas %d   data %.1f MiB   rows %d"
        % (measurement.get("version", "unknown"), summary["tables"], summary["schemas"],
           summary["bytes"] / MIB, summary["rows"]),
        "",
        "%-22s %10s %8s %12s" % ("schema", "size", "tables", "per day"),
    ]
    for name, entry in sorted(measurement.get("schemas", {}).items(),
                              key=lambda item: -item[1]["bytes"]):
        rate = rates.get(name)
        lines.append("%-22s %9.1fM %8d %12s" % (
            name, entry["bytes"] / MIB, entry["tables"],
            "%.1fM" % (rate / MIB) if rate is not None else "-"))
    lines.append("")
    lines.append("datadir %.1f MiB (per day %s)" % (
        sum(item["bytes"] for item in measurement.get("datadir", [])) / MIB,
        "%.1fM" % (datadir_rate / MIB) if datadir_rate is not None else "-"))
    lines.append("")
    if not found:
        lines.append("no findings")
    for severity, name, detail in sorted(found):
        lines.append("[%-8s] %-22s %s" % (severity, name, detail))
    return "\n".join(lines)


def main(argv=None):
    parser = argparse.ArgumentParser(description="database health report")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--no-snapshot", action="store_true")
    args = parser.parse_args(argv)
    backup = _module("backup_for_db", "backup.py")
    ok = True
    measurement = {"at": int(time.time())}
    try:
        backup.ensure_credentials()
        try:
            measurement = collect(backup.mysql)
        finally:
            backup.drop_credentials()
    except Exception as error:                           # noqa: BLE001 - report, never crash
        ok = False
        log.error("cannot read the database: %s", error)
    found = findings(measurement)
    if not args.no_snapshot and measurement.get("version"):
        append_history(measurement)
    history = load_history()
    rates, datadir_rate = growth(history)
    write(render_metrics(measurement, found, rates, datadir_rate, ok=ok))
    if args.json:
        print(json.dumps({"measurement": measurement, "findings": found,
                          "growth": rates, "datadir_growth": datadir_rate,
                          "history_points": len(history)}, indent=2, sort_keys=True))
    else:
        print(render_table(measurement, found, rates, datadir_rate))
    for severity, name, detail in found:
        if severity in ("critical", "high"):
            log.warning("%s: %s", name, detail)
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
