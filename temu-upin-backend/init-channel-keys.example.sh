#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   cp backend/.secrets.env.example backend/.secrets.env
#   # fill in keys in backend/.secrets.env
#   bash backend/init-channel-keys.example.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECRETS_FILE="$SCRIPT_DIR/.secrets.env"

if [[ ! -f "$SECRETS_FILE" ]]; then
  echo "Missing secrets file: $SECRETS_FILE" >&2
  echo "Create it from backend/.secrets.env.example" >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$SECRETS_FILE"

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

if [[ -z "${STABILITY_API_KEY:-}" ]]; then
  echo "STABILITY_API_KEY is empty" >&2
  exit 1
fi

if [[ -z "${VOLCENGINE_API_KEY:-}" || -z "${VOLCENGINE_API_SECRET:-}" ]]; then
  echo "VOLCENGINE_API_KEY or VOLCENGINE_API_SECRET is empty" >&2
  exit 1
fi

echo "Initializing Stability credentials into DB..."
curl -sS -X POST "$API_BASE_URL/api/channels/credentials/platform/stability" \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\":\"$STABILITY_API_KEY\",\"overwriteExisting\":true}" \
  | sed -n '1,120p'

echo "Initializing Volcengine credentials into DB (also Jimeng)..."
curl -sS -X POST "$API_BASE_URL/api/channels/credentials/platform/volcengine" \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\":\"$VOLCENGINE_API_KEY\",\"apiSecret\":\"$VOLCENGINE_API_SECRET\",\"overwriteExisting\":true}" \
  | sed -n '1,120p'

echo "Verifying channel flags..."
curl -sS "$API_BASE_URL/api/channels" | sed -n '1,200p'
