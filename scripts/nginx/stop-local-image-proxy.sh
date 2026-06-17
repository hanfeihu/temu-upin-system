#!/usr/bin/env bash
set -euo pipefail

ROOT="/Users/a1/Desktop/TEMU上品系统-新仓库"
PID_FILE="$ROOT/.local/nginx/nginx.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "pid file not found: $PID_FILE"
  exit 0
fi

PID="$(cat "$PID_FILE")"
if [[ -z "$PID" ]]; then
  echo "empty pid file: $PID_FILE"
  exit 1
fi

kill "$PID"
echo "local nginx image proxy stopped"
