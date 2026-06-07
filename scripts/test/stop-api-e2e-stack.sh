#!/usr/bin/env bash
# Input: local pid/state files and Playwright-managed state marker
# Output: stopped local API-backed E2E stack only when this run started it
# Pos: scripts/test/ - Playwright and API-backed test baseline helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Test-only placeholders so rehearsal-env.sh's secret fail-fast doesn't block
# the e2e teardown path; never used to reach real systems.
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-e2e-test-mysql-root}"
export PLATFORM_ENCRYPTION_KEY="${PLATFORM_ENCRYPTION_KEY:-e2e-test-platform-encryption-key-32}"
export UAT_TEST_PASSWORD="${UAT_TEST_PASSWORD:-e2e-test-uat-pass}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-e2e-test-admin-pass}"
export DB_PASSWORD="${DB_PASSWORD:-e2e-test-db-pass}"
export JWT_SECRET="${JWT_SECRET:-e2e-test-jwt-secret-for-hs512-needs-64-chars-minimum-padding-!!x}"

source "$ROOT_DIR/scripts/release/rehearsal-env.sh"

MARKER_FILE="$STATE_DIR/playwright-api-stack.started"

terminate_pid_file() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    local pid
    pid="$(cat "$pid_file")"
    kill "$pid" >/dev/null 2>&1 || true
    for _ in {1..10}; do
      if ! kill -0 "$pid" >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
    kill -9 "$pid" >/dev/null 2>&1 || true
    rm -f "$pid_file"
  fi
}

cleanup_port_listener() {
  local port="$1"
  local pids
  pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    kill $pids >/dev/null 2>&1 || true
    sleep 1
    pids="$(lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      kill -9 $pids >/dev/null 2>&1 || true
    fi
  fi
}

if [[ ! -f "$MARKER_FILE" ]]; then
  printf 'No Playwright-managed API E2E stack to stop.\n'
  exit 0
fi

terminate_pid_file "$STATE_DIR/frontend.pid"
terminate_pid_file "$STATE_DIR/backend.pid"
cleanup_port_listener "$FRONTEND_PORT"
cleanup_port_listener "$BACKEND_PORT"
rm -f "$MARKER_FILE"

printf 'Playwright-managed API E2E stack stopped.\n'
