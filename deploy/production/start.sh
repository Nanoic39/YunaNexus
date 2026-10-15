#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp"
mountpoint -q /YunaNexus || exit 1
python3 "$ROOT/ops/start-stack.py"
for task in poll-releases maintenance; do
  pidfile="$ROOT/runtime/$task.pid"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then continue; fi
  nohup python3 "$ROOT/ops/$task.py" >>"$ROOT/logs/$task.log" 2>&1 </dev/null &
  echo $! >"$pidfile"
done
