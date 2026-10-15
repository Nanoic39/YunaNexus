#!/usr/bin/env bash
set -euo pipefail
ROOT=/YunaNexus/platform
export HOME="$ROOT/runtime/home" TMPDIR="$ROOT/tmp" PIP_CACHE_DIR="$ROOT/cache/pip" PYTHONPATH="$ROOT/runtime/certbot"
mkdir -p "$ROOT/data/acme-webroot" "$ROOT/data/acme" "$ROOT/cache/acme" "$ROOT/logs/acme"
python3 -m certbot renew --config-dir "$ROOT/data/acme" --work-dir "$ROOT/cache/acme" --logs-dir "$ROOT/logs/acme" --no-random-sleep-on-renew --quiet
if [ -f "$ROOT/data/acme/live/103.39.66.135/fullchain.pem" ]; then
 install -m 640 -g 101 "$ROOT/data/acme/live/103.39.66.135/fullchain.pem" "$ROOT/secrets/tls/server.crt"
 install -m 640 -g 101 "$ROOT/data/acme/live/103.39.66.135/privkey.pem" "$ROOT/secrets/tls/server.key"
 "$ROOT/ops/compose.sh" exec -T edge nginx -s reload
fi
