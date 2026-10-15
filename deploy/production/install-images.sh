#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp" DOCKER_HOST="unix://$ROOT/runtime/docker.sock" DOCKER_CONFIG="$ROOT/runtime/docker-client"
# Reuse the host's already installed Compose; no system files are installed.
if command -v docker-compose >/dev/null 2>&1; then cp "$(command -v docker-compose)" "$ROOT/bin/docker-compose"; fi
"$ROOT/ops/compose.sh" config --quiet
"$ROOT/ops/compose.sh" pull
