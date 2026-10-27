#!/usr/bin/env python3
"""Search the aggregated container logs.

    logquery.py --service auth --level ERROR --since 2h
    logquery.py --grep "登录失败" --since 30m --limit 50
    logquery.py --services            # what is available and how much

Reads the JSONL files written by logship.py, including the gzipped ones.
"""
import argparse
import gzip
import json
import sys
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUTPUT_DIR = ROOT / "logs/aggregated"
LEVELS = ("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL")


def parse_duration(text):
    units = {"s": 1, "m": 60, "h": 3600, "d": 86400}
    if text and text[-1] in units:
        return int(float(text[:-1]) * units[text[-1]])
    return int(text)


def open_file(path):
    if path.suffix == ".gz":
        return gzip.open(path, "rt", encoding="utf-8", errors="replace")
    return path.open(encoding="utf-8", errors="replace")


def files_for(services, since_epoch):
    if not OUTPUT_DIR.is_dir():
        return []
    selected = []
    for directory in sorted(OUTPUT_DIR.iterdir()):
        if not directory.is_dir():
            continue
        if services and directory.name not in services:
            continue
        for path in sorted(directory.iterdir()):
            if ".jsonl" not in path.name:
                continue
            if path.stat().st_mtime < since_epoch - 86400:
                continue
            selected.append(path)
    return selected


def main(argv=None):
    parser = argparse.ArgumentParser(description="Search aggregated container logs")
    parser.add_argument("--service", action="append", default=[], help="repeatable service filter")
    parser.add_argument("--level", action="append", default=[], choices=LEVELS, help="repeatable level filter")
    parser.add_argument("--grep", default=None, help="case-insensitive substring filter")
    parser.add_argument("--since", default="1h", help="window such as 30m, 2h, 1d")
    parser.add_argument("--limit", type=int, default=200, help="maximum lines to print")
    parser.add_argument("--services", action="store_true", help="list collected services and sizes")
    parser.add_argument("--json", action="store_true", help="print raw JSON lines")
    args = parser.parse_args(argv)

    if args.services:
        if not OUTPUT_DIR.is_dir():
            print("no logs collected yet")
            return 0
        for directory in sorted(OUTPUT_DIR.iterdir()):
            if not directory.is_dir():
                continue
            count = sum(p.stat().st_size for p in directory.iterdir() if p.is_file())
            days = sorted(p.name.split(".")[0] for p in directory.iterdir() if p.is_file())
            print("%-24s %7.1f KiB  %s" % (directory.name, count / 1024, ", ".join(days)))
        return 0

    since_epoch = (datetime.now() - timedelta(seconds=parse_duration(args.since))).timestamp()
    levels = {level.upper() for level in args.level}
    printed = 0
    for path in files_for(set(args.service), since_epoch):
        with open_file(path) as handle:
            for line in handle:
                if printed >= args.limit:
                    break
                try:
                    record = json.loads(line)
                except ValueError:
                    continue
                if record.get("epoch", 0) < since_epoch:
                    continue
                if levels and record.get("level") not in levels:
                    continue
                if args.grep and args.grep.lower() not in record.get("msg", "").lower():
                    continue
                printed += 1
                if args.json:
                    print(json.dumps(record, ensure_ascii=False))
                else:
                    print("%s %-5s %-14s %s" % (record.get("ts", ""), record.get("level", ""),
                                                record.get("service", ""), record.get("msg", "")))
    if not printed:
        print("no matching lines in the last %s" % args.since, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
