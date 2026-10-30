#!/usr/bin/env python3
"""Normalize edge forwarding headers; dry-run unless --apply is supplied.

Only scopes already declaring X-Forwarded-For are changed. Every proxy_pass
must have such a declaration in its own scope, otherwise no file is written.
The original file is backed up with root-only access before an in-place write
that preserves the inode used by Docker's single-file bind mount. This script
does not reload nginx or change container networking.
"""

import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import sys
import time


HEADERS = {
    "X-Forwarded-For": "$remote_addr",
    "Forwarded": '""',
    "X-Forwarded-Prefix": '""',
    "X-Forwarded-Ssl": '""',
    # Preserve the public 3000/3001 port instead of exposing nginx's 8080/9080.
    "X-Forwarded-Host": "$http_host",
    "X-Forwarded-Proto": "$scheme",
    "X-Forwarded-Port": '\"\"',
}
CANONICAL = {name.lower(): name for name in HEADERS}


def tokens(source):
    """Yield nginx words/delimiters with offsets, respecting comments and quotes."""
    index = 0
    while index < len(source):
        char = source[index]
        if char.isspace():
            index += 1
            continue
        if char == "#":
            end = source.find("\n", index)
            index = len(source) if end < 0 else end + 1
            continue
        start = index
        if char in ";{}":
            yield char, start, index + 1
            index += 1
            continue
        quote = None
        while index < len(source):
            char = source[index]
            if char == "\\":
                index += 2
                continue
            if quote:
                if char == quote:
                    quote = None
                index += 1
                continue
            if char in "\"'":
                quote = char
                index += 1
                continue
            if source.startswith("${", index):
                end = source.find("}", index + 2)
                if end < 0:
                    raise ValueError("Unclosed nginx variable")
                index = end + 1
                continue
            if char.isspace() or char in ";{}#":
                break
            index += 1
        if quote:
            raise ValueError("Unclosed quoted nginx word")
        if index > len(source):
            raise ValueError("Incomplete nginx escape")
        yield source[start:index], start, index


def scopes(source):
    result = [{"line": 1, "directives": []}]
    stack = [0]
    pending = []
    for word, start, end in tokens(source):
        if word == "{":
            if not pending:
                raise ValueError("Nginx block without a name")
            result.append({"line": source.count("\n", 0, pending[0][1]) + 1,
                           "directives": []})
            stack.append(len(result) - 1)
            pending = []
        elif word == "}":
            if pending or len(stack) == 1:
                raise ValueError("Unexpected nginx block ending")
            stack.pop()
        elif word == ";":
            if not pending:
                raise ValueError("Empty nginx directive")
            result[stack[-1]]["directives"].append(
                ([item[0] for item in pending], pending[0][1], end)
            )
            pending = []
        else:
            pending.append((word, start, end))
    if pending or len(stack) != 1:
        raise ValueError("Incomplete nginx configuration")
    return result


def header_name(directive):
    words = directive[0]
    if words[0] != "proxy_set_header":
        return None
    if len(words) != 3:
        raise ValueError("Invalid proxy_set_header directive")
    return words[1].strip("\"'").lower()


def normalize(source):
    groups = scopes(source)
    missing = []
    proxy_scopes = 0
    changes = []
    xff_scopes = 0
    newline = "\r\n" if "\r\n" in source else "\n"
    for group in groups:
        directives = group["directives"]
        headers = [directive for directive in directives if header_name(directive) is not None]
        xff = [directive for directive in headers if header_name(directive) == "x-forwarded-for"]
        if any(directive[0][0] == "proxy_pass" for directive in directives):
            proxy_scopes += 1
            if not xff:
                missing.append(group["line"])
        if not xff:
            continue
        xff_scopes += 1
        seen = set()
        for directive in headers:
            name = CANONICAL.get(header_name(directive))
            if name is None:
                continue
            _, start, end = directive
            replacement = "" if name in seen else "proxy_set_header %s %s;" % (name, HEADERS[name])
            seen.add(name)
            changes.append((start, end, replacement))
        _, first_start, first_end = xff[0]
        line_start = source.rfind("\n", 0, first_start) + 1
        prefix = source[line_start:first_start]
        indent = prefix if not prefix.strip() else re.match(r"[ \t]*", prefix).group() + "  "
        additions = "".join(newline + indent + "proxy_set_header %s %s;" % (name, value)
                            for name, value in HEADERS.items() if name not in seen)
        if additions:
            changes.append((first_end, first_end, additions))
    if missing:
        raise ValueError("proxy_pass scopes missing their own X-Forwarded-For at lines: "
                         + ", ".join(map(str, missing)))
    updated = source
    # Insertions at a directive's end must be applied before replacing that directive.
    for start, end, replacement in sorted(changes, key=lambda item: (item[0], item[1]), reverse=True):
        updated = updated[:start] + replacement + updated[end:]
    return updated, {"xff_scopes": xff_scopes, "proxy_pass_scopes": proxy_scopes,
                     "missing_xff_scopes": [], "changed": updated != source}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path("/YunaNexus/platform"))
    parser.add_argument("--apply", action="store_true", help="back up and write in place; do not reload")
    args = parser.parse_args(argv)
    path = args.root / "config/nginx.conf"
    if path.is_symlink():
        raise ValueError("Refusing to write through a symlink")
    if args.apply and os.geteuid() != 0:
        raise ValueError("--apply requires root to create root-only backups")
    with path.open("r+b" if args.apply else "rb") as handle:
        if args.apply:
            fcntl.flock(handle, fcntl.LOCK_EX)
        original = handle.read()
        updated, summary = normalize(original.decode("utf-8"))
        summary["mode"] = "apply" if args.apply else "dry-run"
        if args.apply and summary["changed"]:
            backup_dir = args.root / "backups/nginx-forwarded-headers"
            backup_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
            if backup_dir.is_symlink() or backup_dir.stat().st_uid != 0:
                raise ValueError("Backup directory must be owned by root and not a symlink")
            backup_dir.chmod(0o700)
            backup = backup_dir / ("nginx.conf." + str(time.time_ns()))
            descriptor = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "wb") as output:
                output.write(original)
                output.flush()
                os.fsync(output.fileno())
            # Do not rename: the running container binds this file's existing inode.
            handle.seek(0)
            handle.write(updated.encode("utf-8"))
            handle.truncate()
            handle.flush()
            os.fsync(handle.fileno())
            summary["backup"] = str(backup)
        print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print("Configuration unchanged or backup retained: " + str(error), file=sys.stderr)
        sys.exit(1)
