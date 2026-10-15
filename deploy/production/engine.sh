#!/usr/bin/env bash
set -euo pipefail
umask 077
ROOT=/YunaNexus/platform
mountpoint -q /YunaNexus || { echo 'Data disk is not mounted'; exit 1; }
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp" XDG_CACHE_HOME="$ROOT/cache" XDG_CONFIG_HOME="$ROOT/config"
mkdir -p "$HOME" "$TMPDIR" "$ROOT/runtime" "$ROOT/logs"
export DOCKER_HOST="unix://$ROOT/runtime/docker.sock" DOCKER_CONFIG="$ROOT/runtime/docker-client"
# Secondary engine must not overwrite the host Docker firewall chains.
# Allow forwarding only between project containers; published ports use docker-proxy.
iptables -C FORWARD -s 172.29.10.0/24 -d 172.29.10.0/24 -m comment --comment YunaNexus-project-only -j ACCEPT 2>/dev/null || \
  iptables -I FORWARD 1 -s 172.29.10.0/24 -d 172.29.10.0/24 -m comment --comment YunaNexus-project-only -j ACCEPT

if timeout 3 docker info >/dev/null 2>&1; then exit 0; fi
if [ -f "$ROOT/runtime/dockerd.pid" ] && kill -0 "$(cat "$ROOT/runtime/dockerd.pid")" 2>/dev/null; then echo 'Engine is already starting'; exit 1; fi
if ! ip link show yn-docker0 >/dev/null 2>&1; then
  ip link add yn-docker0 type bridge
  ip addr add 172.28.0.1/16 dev yn-docker0
  ip link set yn-docker0 up
fi
if [ -f "$ROOT/config/engine.env" ]; then set -a; source "$ROOT/config/engine.env"; set +a; fi
if [ ! -S "$ROOT/runtime/containerd.sock" ]; then
nohup containerd --root "$ROOT/runtime/containerd-root" --state "$ROOT/runtime/containerd-state" --address "$ROOT/runtime/containerd.sock" >"$ROOT/logs/containerd.log" 2>&1 </dev/null &
fi
for i in {1..30}; do [ -S "$ROOT/runtime/containerd.sock" ] && break; sleep 1; done
nohup dockerd --config-file "$ROOT/config/docker.json" >"$ROOT/logs/dockerd.log" 2>&1 </dev/null &
for i in {1..60}; do timeout 3 docker info >/dev/null 2>&1 && exit 0; sleep 1; done
echo 'Engine failed; inspect logs'; exit 1
