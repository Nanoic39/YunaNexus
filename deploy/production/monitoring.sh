#!/usr/bin/env bash
# Wrapper for the monitoring stack (VictoriaMetrics + Grafana).
#
# Kept apart from compose.sh so monitoring never shares a lifecycle with the
# application release: `monitoring.sh up -d` cannot recreate application services
# and a failed application deploy cannot take monitoring down with it.
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp" \
       DOCKER_HOST="unix://$ROOT/runtime/docker.sock" \
       DOCKER_CONFIG="$ROOT/runtime/docker-client"
exec "$ROOT/bin/docker-compose" \
  --project-directory "$ROOT" \
  --project-name yunanexus-monitoring \
  --env-file "$ROOT/secrets/monitoring.env" \
  -f "$ROOT/ops/compose-monitoring.yaml" "$@"
