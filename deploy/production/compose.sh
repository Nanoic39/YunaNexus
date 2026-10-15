#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp" DOCKER_HOST="unix://$ROOT/runtime/docker.sock" DOCKER_CONFIG="$ROOT/runtime/docker-client"
exec "$ROOT/bin/docker-compose" --project-directory "$ROOT" --env-file "$ROOT/secrets/compose.env" -f "$ROOT/ops/compose.yaml" "$@"
