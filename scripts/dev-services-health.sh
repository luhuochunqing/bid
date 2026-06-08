#!/usr/bin/env bash
# Input: reads env vars from runtime directory + health probes
# Output: JSON health summary to stdout, exit code 0 if all up
# Pos: scripts/ - aggregated health check for all dev services
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
RUNTIME_DIR="${RUNTIME_DIR:-$ROOT_DIR/.runtime/dev-services}"
CURL_TIMEOUT="${CURL_TIMEOUT:-3}"

load_env() {
  SIDECAR_PORT="${SIDECAR_PORT:-8000}"
  BACKEND_PORT="${BACKEND_PORT:-18080}"
  FRONTEND_PORT="${FRONTEND_PORT:-1314}"
  SIDECAR_URL="http://127.0.0.1:${SIDECAR_PORT}/health"
  BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}/actuator/health"
  FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}/"
}

check_service() {
  local name="$1" url="$2" port="$3"
  local status="down" http_code="" pid=""

  # Check port listener
  pid=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR>1{print $2; exit}' || true)

  # Check HTTP
  http_code=$(curl --connect-timeout "$CURL_TIMEOUT" --max-time "$CURL_TIMEOUT" -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || echo "")

  if [[ -n "$http_code" ]]; then
    status="up"
  elif [[ -n "$pid" ]]; then
    status="starting"
  fi

  printf '  "%s": {"status":"%s","http_code":"%s","pid":%s,"port":%d}' \
    "$name" "$status" "${http_code:-null}" "${pid:-null}" "$port"
}

check_workspace() {
  local name="$1" expected_cwd="$2" port="$3"
  local pid cwd match="false"

  pid=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | awk 'NR>1{print $2; exit}' || true)
  if [[ -n "$pid" ]]; then
    cwd=$(lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -1 || true)
    case "$cwd" in
      "$expected_cwd"|"$expected_cwd/"*) match="true" ;;
    esac
  fi
  printf '"%s_workspace_match":%s' "$name" "$match"
}

main() {
  load_env
  echo "{"
  echo '  "timestamp": "'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'",'

  local first=true
  for service in sidecar backend frontend; do
    $first || echo ","
    first=false
    case "$service" in
      sidecar) check_service "sidecar" "$SIDECAR_URL" "$SIDECAR_PORT"
               printf ","
               check_workspace "sidecar" "$ROOT_DIR/document-converter-sidecar" "$SIDECAR_PORT"
               ;;
      backend) check_service "backend" "$BACKEND_URL" "$BACKEND_PORT"
               printf ","
               check_workspace "backend" "$ROOT_DIR/backend" "$BACKEND_PORT"
               ;;
      frontend) check_service "frontend" "$FRONTEND_URL" "$FRONTEND_PORT"
                printf ","
                check_workspace "frontend" "$ROOT_DIR" "$FRONTEND_PORT"
                ;;
    esac
  done
  echo ""
  echo "}"
}

main
