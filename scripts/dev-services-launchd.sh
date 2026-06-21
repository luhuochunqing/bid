#!/usr/bin/env bash
# Input: launchd command argument, optional runtime environment variables
# Output: launchd-managed sidecar/backend/frontend lifecycle actions, child-process cleanup, local secret-file handoff, current-code restart, and bounded status checks
# Pos: scripts/ - macOS launchd wrapper for dev-services watchdog
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

# --- DEV-ONLY GUARD ---------------------------------------------
# This script bakes in convenience defaults for JWT/DB credentials.
# The guard fires only for subcommands that propagate those secrets to
# running processes (install/start/restart). Read-only / teardown
# subcommands (status/logs/stop/uninstall) load the variable defaults
# harmlessly without forwarding them, so they remain safe to run without
# an opt-in.
_env_lower() { echo "${1:-}" | tr '[:upper:]' '[:lower:]'; }
_is_nonprod_value() {
  case "$(_env_lower "$1")" in
    *prod*|*production*|*staging*|*stg*|*release*|*live*|*uat*|*canary*) return 1;;
    *) return 0;;
  esac
}
_dev_guard_subcommand_requires_opt_in() {
  case "${1:-start}" in
    install|start|restart)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}
if _dev_guard_subcommand_requires_opt_in "${1:-start}"; then
  _all_clean=0
  for v in "${SPRING_PROFILES_ACTIVE:-}" "${XIYU_ENV:-}" "${NODE_ENV:-}" "${ENV:-}" "${ENVIRONMENT:-}"; do
    if ! _is_nonprod_value "$v"; then
      _all_clean=1
    fi
  done
  if [[ "$_all_clean" == "1" ]] || [[ "${XIYU_DEV_CONFIRMED:-}" != "1" ]]; then
    echo "ERROR: $(basename "$0") is dev-only tooling and must not run in production-adjacent environments." >&2
    echo "       Detected: SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-} XIYU_ENV=${XIYU_ENV:-} NODE_ENV=${NODE_ENV:-} ENV=${ENV:-} ENVIRONMENT=${ENVIRONMENT:-}" >&2
    echo "       To run locally, set XIYU_DEV_CONFIRMED=1 and ensure no prod-like env vars are set." >&2
    exit 1
  fi
fi
# ----------------------------------------------------------------

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_DIR="$ROOT_DIR/.runtime/dev-services"
mkdir -p "$RUNTIME_DIR"

# 主工作区守卫：只有 trae 工作区允许 install/start/restart launchd 守护进程
# uninstall/stop/status/logs 等只读/清理命令允许在任何工作区执行（用于清理旧配置）
case "${1:-}" in
  install|start|restart)
    if [[ "$ROOT_DIR" != *"/worktrees/trae" ]]; then
      echo "❌ 拒绝安装 launchd 守护进程：当前工作区 $(basename "$ROOT_DIR") 不是主工作区（trae）。"
      echo "   开发环境已统一到主工作区：/Users/user/xiyu/worktrees/trae"
      echo "   其他 worktree 不再允许安装 launchd 守护进程。"
      echo "   请切换到主工作区后重试：cd /Users/user/xiyu/worktrees/trae"
      exit 1
    fi
    # 主工作区固定使用 com.xiyu.bid.dev-services.main 标签
    LAUNCHD_LABEL="${LAUNCHD_LABEL:-com.xiyu.bid.dev-services.main}"
    ;;
esac

BACKEND_PROFILE="${BACKEND_PROFILE:-dev,mysql}"
WATCHDOG_INTERVAL_SECONDS="${WATCHDOG_INTERVAL_SECONDS:-5}"
LAUNCHD_LABEL="${LAUNCHD_LABEL:-com.xiyu.bid.dev-services}"
LAUNCHD_DOMAIN="gui/$(id -u)"
LAUNCHD_PATH="${LAUNCHD_PATH:-/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin}"
JWT_SECRET="${JWT_SECRET:-xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-xiyu_bid_main}"
DB_USERNAME="${DB_USERNAME:-xiyu_user}"
DB_PASSWORD="${DB_PASSWORD:-XiyuDB!2026}"
DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}"
DEEPSEEK_API_KEY_FILE="${DEEPSEEK_API_KEY_FILE:-$RUNTIME_DIR/deepseek.api-key}"
REDIS_HOST="${REDIS_HOST:-localhost}"
DEFAULT_REDIS_PORT="6379"
FALLBACK_REDIS_PORT="16379"
REDIS_PORT="${REDIS_PORT:-}"
REDIS_DB="${REDIS_DB:-0}"
SPRING_DATA_REDIS_DATABASE="${SPRING_DATA_REDIS_DATABASE:-$REDIS_DB}"
SPRING_JPA_HIBERNATE_DDL_AUTO="${SPRING_JPA_HIBERNATE_DDL_AUTO:-validate}"
SIDECAR_HOST="${SIDECAR_HOST:-127.0.0.1}"
SIDECAR_PORT="${SIDECAR_PORT:-8000}"
SIDECAR_SHARED_KEY_FILE="${SIDECAR_SHARED_KEY_FILE:-$RUNTIME_DIR/sidecar.shared-key}"
PLIST_PATH="${HOME}/Library/LaunchAgents/${LAUNCHD_LABEL}.plist"
LAUNCHD_STDOUT_LOG="$RUNTIME_DIR/launchd.out.log"
LAUNCHD_STDERR_LOG="$RUNTIME_DIR/launchd.err.log"
BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-1314}"
SIDECAR_HEALTH_URL="http://${SIDECAR_HOST}:${SIDECAR_PORT}/health"
FRONTEND_HEALTH_SCRIPT="$ROOT_DIR/scripts/dev-frontend-health.sh"
DEV_SERVICES_SCRIPT="$ROOT_DIR/scripts/dev-services.sh"
SIDECAR_START_TIMEOUT_SECONDS="${SIDECAR_START_TIMEOUT_SECONDS:-60}"
BACKEND_START_TIMEOUT_SECONDS="${BACKEND_START_TIMEOUT_SECONDS:-300}"
FRONTEND_START_TIMEOUT_SECONDS="${FRONTEND_START_TIMEOUT_SECONDS:-90}"
CURL_CONNECT_TIMEOUT_SECONDS="${CURL_CONNECT_TIMEOUT_SECONDS:-1}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-3}"

usage() {
  cat <<'EOF'
Usage: scripts/dev-services-launchd.sh <install|start|stop|restart|status|logs|uninstall>

Environment variables:
  BACKEND_PROFILE            Spring profile for backend (default: dev,mysql)
  WATCHDOG_INTERVAL_SECONDS  Watchdog interval seconds (default: 5)
  LAUNCHD_LABEL              launchd service label (default: com.xiyu.bid.dev-services)
  LAUNCHD_PATH               PATH for launchd process (default includes Homebrew)
  JWT_SECRET                 JWT secret passed to backend process
  DB_HOST/DB_PORT/DB_NAME    MySQL connection target
  DB_USERNAME/DB_PASSWORD    MySQL credentials
  DEEPSEEK_API_KEY           DeepSeek API key copied to a local 0600 file for backend process
  DEEPSEEK_API_KEY_FILE      Local runtime file used by dev-services.sh for DeepSeek API key
  REDIS_HOST/REDIS_PORT      Redis connection target
  REDIS_DB                   Redis logical database for this agent
  SPRING_JPA_HIBERNATE_DDL_AUTO
                             Hibernate schema action passed to backend
  SIDECAR_HOST/SIDECAR_PORT  Document converter sidecar bind target
  SIDECAR_SHARED_KEY_FILE    Local runtime file used by dev-services.sh for sidecar auth
  SIDECAR_START_TIMEOUT_SECONDS/BACKEND_START_TIMEOUT_SECONDS/FRONTEND_START_TIMEOUT_SECONDS
                             Startup wait budgets (defaults: 60/300/90)
  CURL_CONNECT_TIMEOUT_SECONDS/CURL_MAX_TIME_SECONDS
                             Health probe network budgets (defaults: 1/3)
EOF
}

service_target() {
  printf "%s/%s" "$LAUNCHD_DOMAIN" "$LAUNCHD_LABEL"
}

is_loaded() {
  launchctl print "$(service_target)" >/dev/null 2>&1
}

is_port_listening() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

curl_health() {
  local url="$1"
  curl --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --max-time "$CURL_MAX_TIME_SECONDS" -fsS "$url" >/dev/null 2>&1
}

resolve_redis_port() {
  if [[ -n "$REDIS_PORT" ]]; then
    return 0
  fi

  if is_port_listening "$DEFAULT_REDIS_PORT"; then
    REDIS_PORT="$DEFAULT_REDIS_PORT"
  elif is_port_listening "$FALLBACK_REDIS_PORT"; then
    REDIS_PORT="$FALLBACK_REDIS_PORT"
  else
    REDIS_PORT="$DEFAULT_REDIS_PORT"
  fi
}

write_deepseek_api_key_file() {
  local previous_umask
  if [[ -z "$DEEPSEEK_API_KEY" && -f "$PLIST_PATH" && -x /usr/libexec/PlistBuddy ]]; then
    DEEPSEEK_API_KEY="$(/usr/libexec/PlistBuddy -c 'Print :EnvironmentVariables:DEEPSEEK_API_KEY' "$PLIST_PATH" 2>/dev/null || true)"
  fi
  if [[ -z "$DEEPSEEK_API_KEY" ]]; then
    return 0
  fi

  mkdir -p "$(dirname "$DEEPSEEK_API_KEY_FILE")"
  previous_umask="$(umask)"
  umask 077
  printf '%s\n' "$DEEPSEEK_API_KEY" >"$DEEPSEEK_API_KEY_FILE"
  umask "$previous_umask"
  chmod 600 "$DEEPSEEK_API_KEY_FILE" >/dev/null 2>&1 || true
}

write_plist() {
  resolve_redis_port
  write_deepseek_api_key_file
  mkdir -p "$(dirname "$PLIST_PATH")"
  cat >"$PLIST_PATH" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LAUNCHD_LABEL}</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>WorkingDirectory</key>
  <string>${ROOT_DIR}</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string>
    <string>-c</string>
    <string>cd "${ROOT_DIR}" &amp;&amp; exec "${ROOT_DIR}/scripts/dev-services.sh" --profile "${BACKEND_PROFILE}" watch-run</string>
  </array>
  <key>EnvironmentVariables</key>
  <dict>
    <key>PATH</key>
    <string>${LAUNCHD_PATH}</string>
    <key>XIYU_DEV_CONFIRMED</key>
    <string>1</string>
    <key>BACKEND_PROFILE</key>
    <string>${BACKEND_PROFILE}</string>
    <key>WATCHDOG_INTERVAL_SECONDS</key>
    <string>${WATCHDOG_INTERVAL_SECONDS}</string>
    <key>BACKEND_START_TIMEOUT_SECONDS</key>
    <string>${BACKEND_START_TIMEOUT_SECONDS}</string>
    <key>SIDECAR_START_TIMEOUT_SECONDS</key>
    <string>${SIDECAR_START_TIMEOUT_SECONDS}</string>
    <key>FRONTEND_START_TIMEOUT_SECONDS</key>
    <string>${FRONTEND_START_TIMEOUT_SECONDS}</string>
    <key>CURL_CONNECT_TIMEOUT_SECONDS</key>
    <string>${CURL_CONNECT_TIMEOUT_SECONDS}</string>
    <key>CURL_MAX_TIME_SECONDS</key>
    <string>${CURL_MAX_TIME_SECONDS}</string>
    <key>JWT_SECRET</key>
    <string>${JWT_SECRET}</string>
    <key>DB_HOST</key>
    <string>${DB_HOST}</string>
    <key>DB_PORT</key>
    <string>${DB_PORT}</string>
    <key>DB_NAME</key>
    <string>${DB_NAME}</string>
    <key>DB_USERNAME</key>
    <string>${DB_USERNAME}</string>
    <key>DB_PASSWORD</key>
    <string>${DB_PASSWORD}</string>
    <key>DEEPSEEK_API_KEY_FILE</key>
    <string>${DEEPSEEK_API_KEY_FILE}</string>
    <key>REDIS_HOST</key>
    <string>${REDIS_HOST}</string>
    <key>REDIS_PORT</key>
    <string>${REDIS_PORT}</string>
    <key>REDIS_DB</key>
    <string>${REDIS_DB}</string>
    <key>SPRING_DATA_REDIS_DATABASE</key>
    <string>${SPRING_DATA_REDIS_DATABASE}</string>
    <key>SPRING_JPA_HIBERNATE_DDL_AUTO</key>
    <string>${SPRING_JPA_HIBERNATE_DDL_AUTO}</string>
    <key>BACKEND_PORT</key>
    <string>${BACKEND_PORT}</string>
    <key>FRONTEND_PORT</key>
    <string>${FRONTEND_PORT}</string>
    <key>SIDECAR_HOST</key>
    <string>${SIDECAR_HOST}</string>
    <key>SIDECAR_PORT</key>
    <string>${SIDECAR_PORT}</string>
    <key>SIDECAR_SHARED_KEY_FILE</key>
    <string>${SIDECAR_SHARED_KEY_FILE}</string>
    <key>SKIP_SESSION_GATE</key>
    <string>1</string>
  </dict>
  <key>StandardOutPath</key>
  <string>${LAUNCHD_STDOUT_LOG}</string>
  <key>StandardErrorPath</key>
  <string>${LAUNCHD_STDERR_LOG}</string>
</dict>
</plist>
EOF
}

cleanup_child_services() {
  if [[ -f "$DEV_SERVICES_SCRIPT" ]]; then
    "$DEV_SERVICES_SCRIPT" --profile "$BACKEND_PROFILE" stop || true
  fi
  # Force-clean any remaining processes on our ports (ghost processes without pid files)
  local port
  for port in "$SIDECAR_PORT" "$BACKEND_PORT" "$FRONTEND_PORT"; do
    local pids
    pids="$(lsof -ti tcp:$port 2>/dev/null || true)"
    if [[ -n "$pids" ]]; then
      echo "[cleanup] force-killing processes on port $port: $pids" >&2
      kill -TERM $pids 2>/dev/null || true
      sleep 1
      kill -KILL $pids 2>/dev/null || true
    fi
  done
}

install_service() {
  write_plist
  if is_loaded; then
    launchctl bootout "$(service_target)" >/dev/null 2>&1 || true
  fi
  cleanup_child_services
  launchctl bootstrap "$LAUNCHD_DOMAIN" "$PLIST_PATH"
  launchctl kickstart -k "$(service_target)"
  echo "installed and started: $(service_target)"
  echo "plist: $PLIST_PATH"
}

start_service() {
  write_plist
  if is_loaded; then
    launchctl bootout "$(service_target)" >/dev/null 2>&1 || true
  fi
  cleanup_child_services
  launchctl bootstrap "$LAUNCHD_DOMAIN" "$PLIST_PATH"
  launchctl kickstart -k "$(service_target)"
  echo "started: $(service_target)"
}

stop_service() {
  if is_loaded; then
    launchctl bootout "$(service_target)"
    echo "stopped: $(service_target)"
  else
    echo "already stopped: $(service_target)"
  fi
  cleanup_child_services
}

status_service() {
  local bstate="down"
  local fstate="down"
  local sstate="down"
  local bhttp="down"
  local fhttp="down"
  local shttp="down"

  if is_loaded; then
    echo "launchd: up ($(service_target))"
  else
    echo "launchd: down ($(service_target))"
  fi

  if [[ -x "$DEV_SERVICES_SCRIPT" ]]; then
    "$DEV_SERVICES_SCRIPT" --profile "$BACKEND_PROFILE" status
    return
  fi

  if lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    bstate="up(port=$BACKEND_PORT)"
  fi
  if lsof -nP -iTCP:"$FRONTEND_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    fstate="up(port=$FRONTEND_PORT)"
  fi
  if lsof -nP -iTCP:"$SIDECAR_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    sstate="up(port=$SIDECAR_PORT)"
  fi
  if curl_health "http://127.0.0.1:${BACKEND_PORT}/actuator/health"; then
    bhttp="ok"
  fi
  if curl_health "$SIDECAR_HEALTH_URL"; then
    shttp="ok"
  fi
  if ROOT_DIR="$ROOT_DIR" FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}/" BACKEND_PORT="$BACKEND_PORT" "$FRONTEND_HEALTH_SCRIPT" >/dev/null 2>&1; then
    fhttp="ok"
  fi

  echo "sidecar: $sstate http=$shttp url=$SIDECAR_HEALTH_URL"
  echo "backend: $bstate http=$bhttp url=http://127.0.0.1:${BACKEND_PORT}/actuator/health"
  echo "frontend: $fstate http=$fhttp url=http://127.0.0.1:${FRONTEND_PORT}/"
}

logs_service() {
  echo "=== launchd stdout ($LAUNCHD_STDOUT_LOG) ==="
  tail -n 80 "$LAUNCHD_STDOUT_LOG" 2>/dev/null || true
  echo
  echo "=== launchd stderr ($LAUNCHD_STDERR_LOG) ==="
  tail -n 80 "$LAUNCHD_STDERR_LOG" 2>/dev/null || true
}

uninstall_service() {
  if is_loaded; then
    launchctl bootout "$(service_target)" >/dev/null 2>&1 || true
  fi
  cleanup_child_services
  rm -f "$PLIST_PATH"
  echo "uninstalled: $(service_target)"
}

CMD="${1:-status}"
case "$CMD" in
  install)
    install_service
    ;;
  start)
    start_service
    ;;
  stop)
    stop_service
    ;;
  restart)
    stop_service
    start_service
    ;;
  status)
    status_service
    ;;
  logs)
    logs_service
    ;;
  uninstall)
    uninstall_service
    ;;
  *)
    usage
    exit 1
    ;;
esac
