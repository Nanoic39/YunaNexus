#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp"
exec python3 "$ROOT/ops/acme-ip.py"
