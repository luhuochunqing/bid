#!/usr/bin/env bash
# Input: local environment variables, start.sh bootstrap workflow, and default dev port policy
# Output: starts xiyu-bid-poc with enforced default ports and validates health endpoints
# Pos: scripts/ - Local development bootstrap entrypoint enforcing default ports
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DEFAULT_BACKEND_PORT=18080
DEFAULT_FRONTEND_PORT=1314
DEFAULT_BACKEND_HEALTH_URL="http://127.0.0.1:${DEFAULT_BACKEND_PORT}/actuator/health"
DEFAULT_FRONTEND_URL="http://127.0.0.1:${DEFAULT_FRONTEND_PORT}/"

printf '[dev-default] Force default ports: frontend=%s backend=%s\n' "$DEFAULT_FRONTEND_PORT" "$DEFAULT_BACKEND_PORT"

unset BACKEND_PORT FRONTEND_PORT VITE_API_BASE_URL

(
  cd "$ROOT_DIR"
  env \
    BACKEND_PORT="$DEFAULT_BACKEND_PORT" \
    FRONTEND_PORT="$DEFAULT_FRONTEND_PORT" \
    ./start.sh
)

if curl -fsS "$DEFAULT_BACKEND_HEALTH_URL" >/dev/null 2>&1 && curl -fsS "$DEFAULT_FRONTEND_URL" >/dev/null 2>&1; then
  printf '[dev-default] Health check passed: %s and %s\n' "$DEFAULT_FRONTEND_URL" "$DEFAULT_BACKEND_HEALTH_URL"
else
  printf '[dev-default] Health check failed for default ports.\n' >&2
  exit 1
fi
