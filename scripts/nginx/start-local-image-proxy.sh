#!/usr/bin/env bash
set -euo pipefail

ROOT="/Users/a1/Desktop/TEMU上品系统-新仓库"
CONF="$ROOT/scripts/nginx/local-image-proxy.conf"
NGINX_BIN="/opt/homebrew/opt/nginx/bin/nginx"

mkdir -p "$ROOT/.local/nginx/logs"

"$NGINX_BIN" -t -c "$CONF"
"$NGINX_BIN" -c "$CONF"

echo "local nginx image proxy started: http://127.0.0.1:18080/"
