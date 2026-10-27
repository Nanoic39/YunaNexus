#!/usr/bin/env python3
"""Expose the Prometheus actuator endpoint on every service config.

The configs under config/*.yaml are loaded by the running jars, so this is a
configuration change only - no rebuild. Each file is backed up first and the
script is idempotent.
"""
import json
import shutil
import sys
from datetime import datetime
from pathlib import Path

ROOT = Path("/YunaNexus/platform")
CONFIG = ROOT / "config"
WANTED = "health,info,prometheus"
STAMP = datetime.now().strftime("%Y%m%d-%H%M%S")


def patch_mapping(data):
    management = data.setdefault("management", {})
    exposure = management.setdefault("endpoints", {}).setdefault("web", {}).setdefault("exposure", {})
    current = exposure.get("include", "")
    names = [part.strip() for part in current.split(",") if part.strip()]
    if "prometheus" in names:
        return False
    names.append("prometheus")
    exposure["include"] = ",".join(names)
    return True


def patch_json(path, text):
    data = json.loads(text)
    if not patch_mapping(data):
        return None
    return json.dumps(data, indent=2, ensure_ascii=False) + "\n"


def patch_yaml(path, text):
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip().startswith("include:") and "health" in line:
            if "prometheus" in line:
                return None
            indent = line[: len(line) - len(line.lstrip())]
            lines[index] = "%sinclude: %s" % (indent, WANTED)
            return "\n".join(lines) + "\n"
    raise RuntimeError("no management exposure block found")


def main():
    changed, skipped = [], []
    for path in sorted(CONFIG.glob("*.yaml")):
        if ".bak-" in path.name:
            continue
        text = path.read_text()
        try:
            updated = patch_json(path, text)
        except json.JSONDecodeError:
            updated = patch_yaml(path, text)
        if updated is None:
            skipped.append(path.name)
            continue
        shutil.copy2(path, path.with_name(path.name + ".bak-" + STAMP))
        path.write_text(updated)
        changed.append(path.name)
    print("patched:", ", ".join(changed) or "none")
    print("already ok:", ", ".join(skipped) or "none")
    return 0


if __name__ == "__main__":
    sys.exit(main())
