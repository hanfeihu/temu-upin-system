#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-https://chatbot.tminos.com}"
API_KEY="${API_KEY:-}"

if [ -z "$API_KEY" ]; then
  printf 'Please set API_KEY before running, for example:\n'
  printf '  API_KEY=your_key_here bash ai-gateway-test.sh\n'
  exit 1
fi

request() {
  local model="$1"
  curl -sS -X POST "$BASE_URL/v1/chat/completions" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    -H "Authorization: Bearer $API_KEY" \
    -d "{
      \"model\": \"$model\",
      \"messages\": [
        {
          \"role\": \"system\",
          \"content\": \"You are a helpful assistant. Return strict JSON only.\"
        },
        {
          \"role\": \"user\",
          \"content\": \"Return exactly this JSON and nothing else: {\\\"ok\\\":true,\\\"source\\\":\\\"$model-test\\\"}\"
        }
      ],
      \"max_tokens\": 200
    }"
}

printf '\n===== Testing gpt-5.5 =====\n'
request "gpt-5.5"

printf '\n\n===== Testing gpt-5.2 =====\n'
request "gpt-5.2"

printf '\n'
