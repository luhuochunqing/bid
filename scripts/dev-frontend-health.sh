#!/usr/bin/env bash
# Input: FRONTEND_URL, ROOT_DIR, BACKEND_PORT and expected Vite API-mode source endpoints
# Output: exits successfully only when bounded probes confirm the frontend port serves this workspace in real-API mode
# Pos: scripts/ - dev service health and identity checks
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-1314}"
FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:${FRONTEND_PORT}/}"
EXPECTED_API_BASE_URL="${EXPECTED_API_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"
CURL_CONNECT_TIMEOUT_SECONDS="${CURL_CONNECT_TIMEOUT_SECONDS:-1}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-3}"

base_url="${FRONTEND_URL%/}"
metric_source_url="${base_url}/src/views/Dashboard/components/MetricCards.vue"
config_source_url="${base_url}/src/api/config.js"
expected_metric_path="${ROOT_DIR}/src/views/Dashboard/components/MetricCards.vue"

curl_frontend() {
  local url="$1"
  curl --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --max-time "$CURL_MAX_TIME_SECONDS" -fsS "$url"
}

metric_source="$(curl_frontend "$metric_source_url" 2>/dev/null)" || {
  printf '[frontend] not reachable at %s\n' "$FRONTEND_URL" >&2
  exit 1
}

case "$metric_source" in
  *"$expected_metric_path"*) ;;
  *)
    printf '[frontend] port serves a different workspace. Expected source marker: %s\n' "$expected_metric_path" >&2
    exit 1
    ;;
esac

config_source="$(curl_frontend "$config_source_url" 2>/dev/null)" || {
  printf '[frontend] cannot read runtime API config from %s\n' "$config_source_url" >&2
  exit 1
}

case "$config_source" in
  *'"VITE_API_MODE": "api"'*|*'"VITE_API_MODE":"api"'*) ;;
  *)
    printf '[frontend] runtime is not in VITE_API_MODE=api\n' >&2
    exit 1
    ;;
esac

case "$config_source" in
  *"$EXPECTED_API_BASE_URL"*) ;;
  *)
    printf '[frontend] runtime API base does not match %s\n' "$EXPECTED_API_BASE_URL" >&2
    exit 1
    ;;
esac
