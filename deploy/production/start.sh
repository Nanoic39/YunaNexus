#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp"
mountpoint -q /YunaNexus || exit 1
python3 "$ROOT/ops/start-stack.py"
for task in backend frontend maintenance; do
  pidfile="$ROOT/runtime/$task.pid"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then continue; fi
  if [ "$task" = maintenance ]; then
    nohup python3 "$ROOT/ops/maintenance.py" >>"$ROOT/logs/maintenance.log" 2>&1 </dev/null &
  else
    nohup python3 "$ROOT/ops/poll-releases.py" --kind "$task" >>"$ROOT/logs/cd-$task.log" 2>&1 </dev/null &
  fi
  echo $! >"$pidfile"
done
