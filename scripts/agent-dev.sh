#!/usr/bin/env bash
# Input: agent worktree command for local dev services
# Output: one-command SOP sync and launchd-managed sidecar/frontend/backend lifecycle
# Pos: scripts/多 Agent 本地开发服务统一入口
# 维护声明: 若 dev-env.sh 的资源映射或 dev-services-launchd.sh 参数变化，请同步更新本脚本与 scripts/README.md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="${XIYU_MAIN_ROOT:-/Users/user/xiyu/xiyu-bid-poc}"
CMD="${1:-status}"

usage() {
  cat <<'USAGE'
Usage: scripts/agent-dev.sh <morning|up|restart|start|stop|status|logs|uninstall>

Commands:
  morning    run SOP sync, refresh env files, and restart launchd services
  up         install/start launchd-managed services for this worktree
  restart    restart launchd-managed services for this worktree
  start      start an already installed launchd service
  stop       stop launchd service and child processes
  status     show launchd, sidecar, backend, and frontend status
  logs       show launchd logs
  uninstall  remove launchd service and stop child processes
USAGE
}

if [[ "$CMD" == "-h" || "$CMD" == "--help" ]]; then
  usage
  exit 0
fi

source "$ROOT_DIR/scripts/dev-env.sh"

agent_key() {
  case "$FRONTEND_PORT" in
    1315) echo "claude" ;;
    1316) echo "codex" ;;
    1317) echo "gemini" ;;
    1318) echo "cursor" ;;
    1319) echo "integrator" ;;
    *) echo "main" ;;
  esac
}

AGENT_KEY="$(agent_key)"
export FRONTEND_PORT BACKEND_PORT SIDECAR_PORT DB_NAME REDIS_DB
export SPRING_DATA_REDIS_DATABASE="${SPRING_DATA_REDIS_DATABASE:-$REDIS_DB}"
export LAUNCHD_LABEL="${LAUNCHD_LABEL:-com.xiyu.bid.dev-services.${AGENT_KEY}}"
SIDECAR_HEALTH_URL="http://127.0.0.1:${SIDECAR_PORT}/health"
BACKEND_HEALTH_URL="http://127.0.0.1:${BACKEND_PORT}/actuator/health"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}/"

run_launchd() {
  bash "$ROOT_DIR/scripts/dev-services-launchd.sh" "$1"
}

wait_ready() {
  local timeout="${AGENT_DEV_START_TIMEOUT_SECONDS:-360}"
  local start
  start="$(date +%s)"
  echo "[agent-dev] waiting for sidecar, backend and frontend readiness"
  while true; do
    if curl -fsS "$SIDECAR_HEALTH_URL" >/dev/null 2>&1 && \
      curl -fsS "$BACKEND_HEALTH_URL" >/dev/null 2>&1 && \
      ROOT_DIR="$ROOT_DIR" \
      FRONTEND_URL="$FRONTEND_URL" \
      FRONTEND_PORT="$FRONTEND_PORT" \
      BACKEND_PORT="$BACKEND_PORT" \
      "$ROOT_DIR/scripts/dev-frontend-health.sh" >/dev/null 2>&1; then
      return 0
    fi
    if (( $(date +%s) - start >= timeout )); then
      echo "[agent-dev] services did not become ready within ${timeout}s" >&2
      run_launchd status >&2 || true
      return 1
    fi
    sleep 2
  done
}

sync_baseline() {
  echo "[agent-dev] syncing git baseline against origin/main"
  git fetch origin --prune
  git rebase --autostash origin/main
  echo "[agent-dev] syncing environment files"
  if [[ "$MAIN_ROOT" != "$ROOT_DIR" && -x "$MAIN_ROOT/scripts/sync-env.sh" ]]; then
    "$MAIN_ROOT/scripts/sync-env.sh" "$ROOT_DIR"
  else
    "$ROOT_DIR/scripts/sync-env.sh" "$ROOT_DIR"
  fi
}

case "$CMD" in
  morning)
    sync_baseline
    echo "[agent-dev] sweeping merged branches"
    "$ROOT_DIR/scripts/sweep-merged-branches.sh" || true
    run_launchd install
    wait_ready
    run_launchd status
    ;;
  up)
    run_launchd install
    wait_ready
    run_launchd status
    ;;
  restart)
    run_launchd restart
    wait_ready
    run_launchd status
    ;;
  start)
    run_launchd start
    wait_ready
    run_launchd status
    ;;
  stop|status|logs|uninstall)
    run_launchd "$CMD"
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
