#!/usr/bin/env bash
# Input: rehearsal environment variables, MySQL 8.0 selection, and runtime pid/state files
# Output: stopped MySQL 8.0 rehearsal services and cleaned up local state
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/release/rehearsal-env.sh"

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

terminate_pid_file "$STATE_DIR/frontend.pid"
terminate_pid_file "$STATE_DIR/backend.pid"
cleanup_port_listener "$FRONTEND_PORT"
cleanup_port_listener "$BACKEND_PORT"

docker rm -f "$REDIS_CONTAINER_NAME" >/dev/null 2>&1 || true
docker rm -f "$MYSQL_CONTAINER_NAME" >/dev/null 2>&1 || true

printf 'Rehearsal stack stopped.\n'
