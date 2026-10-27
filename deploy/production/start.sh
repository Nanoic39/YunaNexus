#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp"
mountpoint -q /YunaNexus || exit 1
python3 "$ROOT/ops/start-stack.py"
for task in backend frontend maintenance logship; do
  pidfile="$ROOT/runtime/$task.pid"
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then continue; fi
  case "$task" in
    maintenance) nohup python3 "$ROOT/ops/maintenance.py" >>"$ROOT/logs/maintenance.log" 2>&1 </dev/null & ;;
    logship)     nohup python3 "$ROOT/ops/logship.py"     >>"$ROOT/logs/logship.log"     2>&1 </dev/null & ;;
    *)           nohup python3 "$ROOT/ops/poll-releases.py" --kind "$task" >>"$ROOT/logs/cd-$task.log" 2>&1 </dev/null & ;;
  esac
  echo $! >"$pidfile"
done
