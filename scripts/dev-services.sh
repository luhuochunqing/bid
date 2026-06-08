#!/usr/bin/env bash
# Input: local dev environment, backend profile options, and repository startup commands
# Output: stable daemon-like start/stop/status/log control for sidecar/backend/frontend with current-code identity checks, secret-file handoff, Vite cache reset, and bounded health probes
# Pos: scripts/ - local service lifecycle management
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

# --- DEV-ONLY GUARD ---------------------------------------------
# This script bakes in convenience defaults for JWT/DB credentials.
# The guard fires only for subcommands that propagate those secrets to
# running processes (start/restart/watch-start/daemon:install/daemon:start
# /daemon:restart). Read-only subcommands (status/logs/stop/...) load the
# variable defaults harmlessly without forwarding them, so they remain
# safe to run without an opt-in.
_env_lower() { echo "${1:-}" | tr '[:upper:]' '[:lower:]'; }
_is_nonprod_value() {
  case "$(_env_lower "$1")" in
    *prod*|*production*|*staging*|*stg*|*release*|*live*|*uat*|*canary*) return 1;;
    *) return 0;;
  esac
}
_dev_guard_subcommand_requires_opt_in() {
  # Scan every positional arg so option-first invocations (e.g.
  # `dev-services.sh --profile e2e start`) cannot bypass the guard.
  # `watch-run` is included because it drives the watchdog into start_*.
  for a in "$@"; do
    case "$a" in
      start|restart|watch-start|watch-run|daemon:install|daemon:start|daemon:restart)
        return 0
        ;;
    esac
  done
  return 1
}
if _dev_guard_subcommand_requires_opt_in "$@"; then
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

# 加载 worktree 专属端口和数据库配置
if [[ -f "$ROOT_DIR/scripts/dev-env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/scripts/dev-env.sh"
fi

BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-1314}"
SIDECAR_PORT="${SIDECAR_PORT:-8000}"
BACKEND_HEALTH_URL="http://127.0.0.1:${BACKEND_PORT}/actuator/health"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}/"
SIDECAR_HOST="${SIDECAR_HOST:-127.0.0.1}"
SIDECAR_URL="http://${SIDECAR_HOST}:${SIDECAR_PORT}"
SIDECAR_HEALTH_URL="${SIDECAR_URL}/health"
SIDECAR_DIR="$ROOT_DIR/document-converter-sidecar"
SIDECAR_VENV_BIN="${SIDECAR_VENV_BIN:-$SIDECAR_DIR/.venv/bin}"
SIDECAR_UVICORN="${SIDECAR_UVICORN:-$SIDECAR_VENV_BIN/uvicorn}"
SIDECAR_MAX_UPLOAD_MB="${SIDECAR_MAX_UPLOAD_MB:-30}"
SIDECAR_SHARED_KEY="${SIDECAR_SHARED_KEY:-}"
SIDECAR_SHARED_KEY_FILE="${SIDECAR_SHARED_KEY_FILE:-$RUNTIME_DIR/sidecar.shared-key}"

SIDECAR_PID_FILE="$RUNTIME_DIR/sidecar.pid"
BACKEND_PID_FILE="$RUNTIME_DIR/backend.pid"
FRONTEND_PID_FILE="$RUNTIME_DIR/frontend.pid"
SIDECAR_ID_FILE="$RUNTIME_DIR/sidecar.identity"
BACKEND_ID_FILE="$RUNTIME_DIR/backend.identity"
FRONTEND_ID_FILE="$RUNTIME_DIR/frontend.identity"
SIDECAR_LOG="$RUNTIME_DIR/sidecar.log"
BACKEND_LOG="$RUNTIME_DIR/backend.log"
FRONTEND_LOG="$RUNTIME_DIR/frontend.log"
WATCHDOG_PID_FILE="$RUNTIME_DIR/watchdog.pid"
WATCHDOG_LOG="$RUNTIME_DIR/watchdog.log"
BACKEND_FAIL_STATE="$RUNTIME_DIR/backend.fail-state"
WATCHDOG_BACKEND_MAX_FAILURES="${WATCHDOG_BACKEND_MAX_FAILURES:-10}"
WATCHDOG_INTERVAL_SECONDS="${WATCHDOG_INTERVAL_SECONDS:-5}"
SIDECAR_START_TIMEOUT_SECONDS="${SIDECAR_START_TIMEOUT_SECONDS:-60}"
BACKEND_START_TIMEOUT_SECONDS="${BACKEND_START_TIMEOUT_SECONDS:-300}"
FRONTEND_START_TIMEOUT_SECONDS="${FRONTEND_START_TIMEOUT_SECONDS:-90}"
CURL_CONNECT_TIMEOUT_SECONDS="${CURL_CONNECT_TIMEOUT_SECONDS:-1}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-3}"
VITE_RESET_CACHE_ON_START="${VITE_RESET_CACHE_ON_START:-true}"
DEFAULT_BACKEND_PROFILE="${BACKEND_PROFILE:-dev,mysql}"
FRONTEND_HEALTH_SCRIPT="$ROOT_DIR/scripts/dev-frontend-health.sh"
BACKEND_PROFILE_OVERRIDE=""
ACTIVE_BACKEND_PROFILE=""
JWT_SECRET="${JWT_SECRET:-xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-$(resolve_worktree_db)}"
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

is_valid_command() {
  case "$1" in
    start|stop|restart|status|logs|healthcheck|watch-start|watch-stop|watch-status|watch-run)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

parse_args() {
  local cmd_seen=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -p|--profile)
        if [[ $# -lt 2 ]]; then
          echo "missing value for $1" >&2
          usage
          exit 1
        fi
        BACKEND_PROFILE_OVERRIDE="$2"
        shift 2
        ;;
      --profile=*)
        BACKEND_PROFILE_OVERRIDE="${1#--profile=}"
        shift
        ;;
      *)
        if is_valid_command "$1"; then
          if [[ -n "$cmd_seen" ]]; then
            echo "multiple commands specified: $cmd_seen and $1" >&2
            usage
            exit 1
          fi
          cmd_seen="$1"
          shift
        else
          echo "unknown argument: $1" >&2
          usage
          exit 1
        fi
        ;;
    esac
  done

  if [[ -n "$BACKEND_PROFILE_OVERRIDE" ]]; then
    ACTIVE_BACKEND_PROFILE="$BACKEND_PROFILE_OVERRIDE"
  else
    ACTIVE_BACKEND_PROFILE="$DEFAULT_BACKEND_PROFILE"
  fi

  if [[ -n "$cmd_seen" ]]; then
    CMD="$cmd_seen"
  else
    CMD="status"
  fi
}

profile_args() {
  printf '%s\n%s\n' "--profile" "$ACTIVE_BACKEND_PROFILE"
}

is_pid_running() {
  local pid="$1"
  [[ -n "${pid:-}" ]] && kill -0 "$pid" >/dev/null 2>&1
}

is_port_listening() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

port_listener_pids() {
  local port="$1"
  lsof -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
}

process_command() {
  local pid="$1"
  ps -p "$pid" -o command= 2>/dev/null || true
}

process_cwd() {
  local pid="$1"
  lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | head -n 1
}

hash_stream() {
  shasum -a 256 | awk '{print $1}'
}

load_deepseek_api_key() {
  if [[ -n "$DEEPSEEK_API_KEY" ]]; then
    export DEEPSEEK_API_KEY
    return 0
  fi
  if [[ -r "$DEEPSEEK_API_KEY_FILE" ]]; then
    DEEPSEEK_API_KEY="$(tr -d '\r\n' <"$DEEPSEEK_API_KEY_FILE")"
    export DEEPSEEK_API_KEY
  elif [[ -f "$DEEPSEEK_API_KEY_FILE" ]]; then
    echo "[backend] cannot read DEEPSEEK_API_KEY_FILE: $DEEPSEEK_API_KEY_FILE" >&2
    return 1
  fi
}

load_sidecar_shared_key() {
  if [[ -n "$SIDECAR_SHARED_KEY" ]]; then
    export SIDECAR_SHARED_KEY
    return 0
  fi
  if [[ -f "$SIDECAR_SHARED_KEY_FILE" ]]; then
    SIDECAR_SHARED_KEY="$(tr -d '\r\n' <"$SIDECAR_SHARED_KEY_FILE")"
    export SIDECAR_SHARED_KEY
  fi
}

generate_sidecar_shared_key() {
  if ! command -v openssl >/dev/null 2>&1; then
    echo "[sidecar] openssl is required to generate SIDECAR_SHARED_KEY" >&2
    return 1
  fi
  openssl rand -hex 32
}

ensure_sidecar_shared_key() {
  local generated_key
  local previous_umask
  load_sidecar_shared_key
  if [[ -n "$SIDECAR_SHARED_KEY" ]]; then
    return 0
  fi

  generated_key="$(generate_sidecar_shared_key)"
  previous_umask="$(umask)"
  umask 077
  printf '%s\n' "$generated_key" >"$SIDECAR_SHARED_KEY_FILE"
  umask "$previous_umask"
  chmod 600 "$SIDECAR_SHARED_KEY_FILE" >/dev/null 2>&1 || true
  SIDECAR_SHARED_KEY="$generated_key"
  export SIDECAR_SHARED_KEY
}

sidecar_shared_key_hash() {
  load_sidecar_shared_key
  if [[ -z "$SIDECAR_SHARED_KEY" ]]; then
    printf 'unset\n'
    return 0
  fi
  printf '%s' "$SIDECAR_SHARED_KEY" | hash_stream
}

deepseek_api_key_hash() {
  load_deepseek_api_key
  if [[ -z "$DEEPSEEK_API_KEY" ]]; then
    printf 'unset\n'
    return 0
  fi
  printf '%s' "$DEEPSEEK_API_KEY" | hash_stream
}

workspace_fingerprint() {
  if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    local pathspec=(-- . ':(exclude)node_modules' ':(exclude)backend/target' ':(exclude).runtime' ':(exclude)dist' ':(exclude)coverage')
    {
      git -C "$ROOT_DIR" rev-parse HEAD
      git -C "$ROOT_DIR" status --porcelain=v1 --untracked-files=all "${pathspec[@]}"
      git -C "$ROOT_DIR" diff --binary --no-ext-diff HEAD "${pathspec[@]}"
      git -C "$ROOT_DIR" diff --cached --binary --no-ext-diff "${pathspec[@]}"
    } | hash_stream
  else
    printf 'no-git:%s\n' "$ROOT_DIR" | hash_stream
  fi
}

sidecar_expected_identity() {
  {
    printf 'kind=sidecar\n'
    printf 'root=%s\n' "$ROOT_DIR"
    printf 'workspace=%s\n' "$(workspace_fingerprint)"
    printf 'sidecar_dir=%s\n' "$SIDECAR_DIR"
    printf 'sidecar_host=%s\n' "$SIDECAR_HOST"
    printf 'sidecar_port=%s\n' "$SIDECAR_PORT"
    printf 'sidecar_max_upload_mb=%s\n' "$SIDECAR_MAX_UPLOAD_MB"
    printf 'sidecar_key_hash=%s\n' "$(sidecar_shared_key_hash)"
  } | hash_stream
}

backend_expected_identity() {
  {
    printf 'kind=backend\n'
    printf 'root=%s\n' "$ROOT_DIR"
    printf 'workspace=%s\n' "$(workspace_fingerprint)"
    printf 'profile=%s\n' "$ACTIVE_BACKEND_PROFILE"
    printf 'backend_port=%s\n' "$BACKEND_PORT"
    printf 'frontend_port=%s\n' "$FRONTEND_PORT"
    printf 'db=%s:%s/%s\n' "$DB_HOST" "$DB_PORT" "$DB_NAME"
    printf 'db_user=%s\n' "$DB_USERNAME"
    printf 'redis=%s:%s\n' "$REDIS_HOST" "$REDIS_PORT"
    printf 'sidecar_url=%s\n' "$SIDECAR_URL"
    printf 'sidecar_key_hash=%s\n' "$(sidecar_shared_key_hash)"
    printf 'deepseek_key_hash=%s\n' "$(deepseek_api_key_hash)"
  } | hash_stream
}

frontend_expected_identity() {
  {
    printf 'kind=frontend\n'
    printf 'root=%s\n' "$ROOT_DIR"
    printf 'workspace=%s\n' "$(workspace_fingerprint)"
    printf 'frontend_port=%s\n' "$FRONTEND_PORT"
    printf 'backend_port=%s\n' "$BACKEND_PORT"
    printf 'api_base=%s\n' "http://127.0.0.1:${BACKEND_PORT}"
  } | hash_stream
}

write_identity_file() {
  local file="$1"
  local pid="$2"
  local identity="$3"
  {
    printf 'pid=%s\n' "$pid"
    printf 'identity=%s\n' "$identity"
  } >"$file"
}

identity_file_matches() {
  local file="$1"
  local pid="$2"
  local identity="$3"
  [[ -f "$file" ]] || return 1
  grep -Fxq "pid=${pid}" "$file" || return 1
  grep -Fxq "identity=${identity}" "$file" || return 1
}

curl_health() {
  local url="$1"
  curl --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --max-time "$CURL_MAX_TIME_SECONDS" -fsS "$url" >/dev/null 2>&1
}

backend_matches_workspace() {
  local pid cwd
  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    cwd="$(process_cwd "$pid")"
    case "$cwd" in
      "$ROOT_DIR/backend"|"$ROOT_DIR/backend/"*)
        return 0
        ;;
    esac
  done < <(port_listener_pids "$BACKEND_PORT")
  return 1
}

backend_current_for_pid() {
  local pid="$1"
  local identity="$2"
  is_pid_running "$pid" || return 1
  identity_file_matches "$BACKEND_ID_FILE" "$pid" "$identity"
}

sidecar_matches_workspace() {
  local pid cwd
  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    cwd="$(process_cwd "$pid")"
    case "$cwd" in
      "$SIDECAR_DIR"|"$SIDECAR_DIR/"*)
        return 0
        ;;
    esac
  done < <(port_listener_pids "$SIDECAR_PORT")
  return 1
}

sidecar_current_for_pid() {
  local pid="$1"
  local identity="$2"
  is_pid_running "$pid" || return 1
  identity_file_matches "$SIDECAR_ID_FILE" "$pid" "$identity"
}

print_sidecar_mismatch() {
  echo "[sidecar] port $SIDECAR_PORT is occupied by another service" >&2
  echo "[sidecar] expected workspace marker: $SIDECAR_DIR" >&2
  lsof -nP -iTCP:"$SIDECAR_PORT" -sTCP:LISTEN >&2 || true
  echo "[sidecar] stop the conflicting service or choose another SIDECAR_PORT" >&2
}

print_backend_mismatch() {
  echo "[backend] port $BACKEND_PORT is occupied by another service" >&2
  echo "[backend] expected workspace marker: $ROOT_DIR/backend" >&2
  lsof -nP -iTCP:"$BACKEND_PORT" -sTCP:LISTEN >&2 || true
  echo "[backend] stop the conflicting service or run: npm run dev:stable:stop" >&2
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

resolve_worktree_db() {
  # 优先使用环境变量 DB_NAME（显式设置优先）
  if [[ -n "${DB_NAME:-}" ]]; then
    echo "$DB_NAME"
    return 0
  fi

  # 尝试从 git worktree 自动检测
  # 从 ROOT_DIR 路径推断 worktree 名称（ROOT_DIR 即当前 worktree 根目录）
  local worktree_name
  case "$ROOT_DIR" in
    *"/worktrees/"*)
      worktree_name="$(basename "$ROOT_DIR")"
      ;;
    *)
      worktree_name="main"
      ;;
  esac
  if [[ -n "$worktree_name" ]]; then
    case "$worktree_name" in
      claude)    echo "xiyu_bid_claude" ;;
      codex)     echo "xiyu_bid_codex" ;;
      gemini)    echo "xiyu_bid_gemini" ;;
      cursor)    echo "xiyu_bid_cursor" ;;
      integrator) echo "xiyu_bid_integrator" ;;
      qoder)     echo "xiyu_bid_qoder" ;;
      main)      echo "xiyu_bid_main" ;;
      *)         echo "xiyu_bid_${worktree_name}" ;;
    esac
  else
    # 回退到 main 分支数据库
    echo "xiyu_bid_main"
  fi
}

read_pid() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  tr -d '[:space:]' <"$file"
}

wait_http() {
  local url="$1"
  local timeout="${2:-90}"
  local start="$(date +%s)"
  while true; do
    if curl_health "$url"; then
      return 0
    fi
    if (( $(date +%s) - start >= timeout )); then
      return 1
    fi
    sleep 1
  done
}

frontend_matches_workspace() {
  local pid cwd
  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    cwd="$(process_cwd "$pid")"
    case "$cwd" in
      "$ROOT_DIR"|"$ROOT_DIR/"*)
        return 0
        ;;
    esac
  done < <(port_listener_pids "$FRONTEND_PORT")
  return 1
}

frontend_current_for_pid() {
  local pid="$1"
  local identity="$2"
  is_pid_running "$pid" || return 1
  identity_file_matches "$FRONTEND_ID_FILE" "$pid" "$identity"
}

print_frontend_mismatch() {
  local pid cwd
  echo "[frontend] port $FRONTEND_PORT is occupied by another service or not ready" >&2
  echo "[frontend] expected workspace: $ROOT_DIR" >&2
  lsof -nP -iTCP:"$FRONTEND_PORT" -sTCP:LISTEN >&2 || true
}

wait_frontend() {
  local timeout="${1:-90}"
  local start elapsed
  start="$(date +%s)"
  local pid identity
  pid="$(read_pid "$FRONTEND_PID_FILE" 2>/dev/null || true)"
  identity="$(frontend_expected_identity)"
  # Phase 1: wait for port + cwd match ("our" process owns the port)
  # Accept: cwd match (frontend_matches_workspace) OR pid file + identity match
  while true; do
    if frontend_matches_workspace; then
      break
    fi
    if is_pid_running "$pid" && identity_file_matches "$FRONTEND_ID_FILE" "$pid" "$identity"; then
      break
    fi
    elapsed=$(( $(date +%s) - start ))
    if (( elapsed >= timeout )); then
      return 1
    fi
    sleep 1
  done
  # Phase 2: wait for actual HTTP 200
  while true; do
    if curl --connect-timeout 2 --max-time 3 -fsS -o /dev/null "$FRONTEND_URL"; then
      return 0
    fi
    elapsed=$(( $(date +%s) - start ))
    if (( elapsed >= timeout )); then
      return 1
    fi
    sleep 1
  done
}

start_sidecar() {
  local pid=""
  local identity=""
  ensure_sidecar_shared_key
  identity="$(sidecar_expected_identity)"
  pid="$(read_pid "$SIDECAR_PID_FILE" 2>/dev/null || true)"

  if [[ ! -d "$SIDECAR_DIR" ]]; then
    echo "[sidecar] directory not found: $SIDECAR_DIR" >&2
    return 1
  fi
  if [[ ! -x "$SIDECAR_UVICORN" ]]; then
    echo "[sidecar] uvicorn not found: $SIDECAR_UVICORN" >&2
    echo "[sidecar] run: cd document-converter-sidecar && python -m venv .venv && .venv/bin/pip install -r requirements.txt" >&2
    return 1
  fi

  if is_pid_running "$pid"; then
    if sidecar_current_for_pid "$pid" "$identity"; then
      if is_port_listening "$SIDECAR_PORT"; then
        if sidecar_matches_workspace; then
          echo "[sidecar] already running (pid=$pid, port=$SIDECAR_PORT)"
        else
          print_sidecar_mismatch
          return 1
        fi
      else
        echo "[sidecar] process is running (pid=$pid), waiting for port $SIDECAR_PORT to become ready"
      fi
      return 0
    fi
    echo "[sidecar] stale tracked process detected, restarting for current code/config"
    stop_one "sidecar" "$SIDECAR_PID_FILE"
  fi

  if is_port_listening "$SIDECAR_PORT"; then
    if sidecar_matches_workspace; then
      echo "[sidecar] untracked or stale workspace process detected on port $SIDECAR_PORT, restarting"
      stop_one "sidecar" "$SIDECAR_PID_FILE"
    else
      echo "[sidecar] forcing cleanup of conflicting process on port $SIDECAR_PORT" >&2
      local_conflicting_pids="$(lsof -ti tcp:$SIDECAR_PORT 2>/dev/null || true)"
      if [[ -n "$local_conflicting_pids" ]]; then
        kill -TERM $local_conflicting_pids 2>/dev/null || true
      fi
      local _wait=0
      while is_port_listening "$SIDECAR_PORT" && (( _wait < 8 )); do
        sleep 1
        _wait=$(( _wait + 1 ))
        if (( _wait == 2 )); then
          local_conflicting_pids="$(lsof -ti tcp:$SIDECAR_PORT 2>/dev/null || true)"
          [[ -n "$local_conflicting_pids" ]] && kill -KILL $local_conflicting_pids 2>/dev/null || true
        fi
      done
    fi
    if is_port_listening "$SIDECAR_PORT"; then
      print_sidecar_mismatch
      return 1
    fi
  fi

  echo "[sidecar] starting on ${SIDECAR_URL}"
  : >"$SIDECAR_LOG"
  (
    cd "$SIDECAR_DIR"
    nohup env \
      SIDECAR_HOST="$SIDECAR_HOST" \
      SIDECAR_PORT="$SIDECAR_PORT" \
      SIDECAR_SHARED_KEY="$SIDECAR_SHARED_KEY" \
      SIDECAR_MAX_UPLOAD_MB="$SIDECAR_MAX_UPLOAD_MB" \
      "$SIDECAR_UVICORN" app:app --host "$SIDECAR_HOST" --port "$SIDECAR_PORT" \
      >>"$SIDECAR_LOG" 2>&1 < /dev/null &
    local started_pid=$!
    echo "$started_pid" >"$SIDECAR_PID_FILE"
    write_identity_file "$SIDECAR_ID_FILE" "$started_pid" "$identity"
  )
}

start_backend() {
  local pid=""
  local identity=""
  resolve_redis_port
  ensure_sidecar_shared_key
  load_deepseek_api_key
  identity="$(backend_expected_identity)"
  pid="$(read_pid "$BACKEND_PID_FILE" 2>/dev/null || true)"
  if is_pid_running "$pid"; then
    if backend_current_for_pid "$pid" "$identity"; then
      if is_port_listening "$BACKEND_PORT"; then
        if backend_matches_workspace; then
          echo "[backend] already running (pid=$pid, port=$BACKEND_PORT)"
        else
          print_backend_mismatch
          return 1
        fi
      else
        echo "[backend] process is running (pid=$pid), waiting for port $BACKEND_PORT to become ready"
      fi
      return 0
    fi
    echo "[backend] stale tracked process detected, restarting for current code/config"
    stop_one "backend" "$BACKEND_PID_FILE"
  fi

  if is_port_listening "$BACKEND_PORT"; then
    if backend_matches_workspace; then
      echo "[backend] untracked or stale workspace process detected on port $BACKEND_PORT, restarting"
      stop_one "backend" "$BACKEND_PID_FILE"
    else
      echo "[backend] forcing cleanup of conflicting process on port $BACKEND_PORT" >&2
      local_conflicting_pids="$(lsof -ti tcp:$BACKEND_PORT 2>/dev/null || true)"
      if [[ -n "$local_conflicting_pids" ]]; then
        kill -TERM $local_conflicting_pids 2>/dev/null || true
      fi
      local _wait=0
      while is_port_listening "$BACKEND_PORT" && (( _wait < 8 )); do
        sleep 1
        _wait=$(( _wait + 1 ))
        if (( _wait == 2 )); then
          local_conflicting_pids="$(lsof -ti tcp:$BACKEND_PORT 2>/dev/null || true)"
          [[ -n "$local_conflicting_pids" ]] && kill -KILL $local_conflicting_pids 2>/dev/null || true
        fi
      done
    fi
    if is_port_listening "$BACKEND_PORT"; then
      print_backend_mismatch
      return 1
    fi
  fi

  echo "[backend] starting on :$BACKEND_PORT"
  echo "[backend] profile: $ACTIVE_BACKEND_PROFILE"
  echo "[backend] database: MySQL at ${DB_HOST}:${DB_PORT}/${DB_NAME}"
  echo "[backend] redis: ${REDIS_HOST}:${REDIS_PORT}/${SPRING_DATA_REDIS_DATABASE}"
  : >"$BACKEND_LOG"

  # Pre-flight: mvn compile check (fail fast before nohup)
  # Pre-flight: mvn package（编译 + 打包 jar，fail fast）
  echo "[backend] running mvn package -DskipTests (pre-flight) ..."
  if ! (cd "$ROOT_DIR/backend" && JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}" mvn package -DskipTests -Dmaven.test.skip=true -q); then
    echo "[backend] COMPILATION FAILED. Fix errors first, then retry." >&2
    return 1
  fi
  echo "[backend] compile + package ok."

  # Re-check port after long mvn build (external processes may have reclaimed it)
  while IFS= read -r _p; do
    kill -TERM "$_p" 2>/dev/null || true
  done < <(port_listener_pids "$BACKEND_PORT")
  sleep 1
  while IFS= read -r _p; do
    kill -KILL "$_p" 2>/dev/null || true
  done < <(port_listener_pids "$BACKEND_PORT")

  # 启动后端（编译后直接 java -jar，比 mvn spring-boot:run 更快更稳定）
  local jar_file="$ROOT_DIR/backend/target/bid-poc-1.0.3.jar"
  if [[ ! -f "$jar_file" ]]; then
    echo "[backend] jar not found: $jar_file" >&2
    return 1
  fi

  (
    cd "$ROOT_DIR/backend"
    nohup env \
      JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk}" \
      SPRING_PROFILES_ACTIVE="$ACTIVE_BACKEND_PROFILE" \
      JWT_SECRET="$JWT_SECRET" \
      DB_HOST="$DB_HOST" \
      DB_PORT="$DB_PORT" \
      DB_NAME="$DB_NAME" \
      DB_USERNAME="$DB_USERNAME" \
      DB_PASSWORD="$DB_PASSWORD" \
      DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY" \
      REDIS_HOST="$REDIS_HOST" \
      REDIS_PORT="$REDIS_PORT" \
      REDIS_DB="$REDIS_DB" \
      SPRING_DATA_REDIS_DATABASE="$SPRING_DATA_REDIS_DATABASE" \
      APP_CONVERTER_SIDECAR_URL="$SIDECAR_URL" \
      APP_CONVERTER_SIDECAR_SHARED_KEY="$SIDECAR_SHARED_KEY" \
      APP_DOC_INSIGHT_SIDECAR_URL="$SIDECAR_URL" \
      APP_DOC_INSIGHT_SIDECAR_SHARED_KEY="$SIDECAR_SHARED_KEY" \
      CORS_ALLOWED_ORIGINS="http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}" \
      SERVER_PORT="$BACKEND_PORT" \
      "${JAVA_HOME:-/opt/homebrew/opt/openjdk}/bin/java" \
      -XX:+UseZGC -Xms256m -Xmx512m \
      -jar "$jar_file" \
      >>"$BACKEND_LOG" 2>&1 < /dev/null &
    local started_pid=$!
    echo "$started_pid" >"$BACKEND_PID_FILE"
    write_identity_file "$BACKEND_ID_FILE" "$started_pid" "$identity"
  )
}

start_frontend() {
  local pid=""
  local identity=""
  local frontend_cache_dir="$RUNTIME_DIR/vite-cache"
  identity="$(frontend_expected_identity)"
  pid="$(read_pid "$FRONTEND_PID_FILE" 2>/dev/null || true)"
  if is_pid_running "$pid"; then
    if frontend_current_for_pid "$pid" "$identity"; then
      if is_port_listening "$FRONTEND_PORT"; then
        if frontend_matches_workspace; then
          echo "[frontend] already running (pid=$pid, port=$FRONTEND_PORT)"
        else
          echo "[frontend] process is running (pid=$pid), waiting for API-mode workspace probe to become ready"
        fi
      else
        echo "[frontend] process is running (pid=$pid), waiting for port $FRONTEND_PORT to become ready"
      fi
      return 0
    fi
    echo "[frontend] stale tracked process detected, restarting for current code/config"
    stop_one "frontend" "$FRONTEND_PID_FILE"
  fi

  if is_port_listening "$FRONTEND_PORT"; then
    if frontend_matches_workspace; then
      echo "[frontend] untracked or stale workspace process detected on port $FRONTEND_PORT, restarting"
      stop_one "frontend" "$FRONTEND_PID_FILE"
    else
      echo "[frontend] forcing cleanup of conflicting process on port $FRONTEND_PORT" >&2
      local_conflicting_pids="$(lsof -ti tcp:$FRONTEND_PORT 2>/dev/null || true)"
      if [[ -n "$local_conflicting_pids" ]]; then
        kill -TERM $local_conflicting_pids 2>/dev/null || true
      fi
      local _wait=0
      while is_port_listening "$FRONTEND_PORT" && (( _wait < 8 )); do
        sleep 1
        _wait=$(( _wait + 1 ))
        if (( _wait == 2 )); then
          local_conflicting_pids="$(lsof -ti tcp:$FRONTEND_PORT 2>/dev/null || true)"
          [[ -n "$local_conflicting_pids" ]] && kill -KILL $local_conflicting_pids 2>/dev/null || true
        fi
      done
    fi
    if is_port_listening "$FRONTEND_PORT"; then
      echo "[frontend] port $FRONTEND_PORT is still occupied after forced cleanup" >&2
      print_frontend_mismatch
      return 1
    fi
  fi

  echo "[frontend] starting on :$FRONTEND_PORT"
  : >"$FRONTEND_LOG"
  (
    cd "$ROOT_DIR"
    if [[ "$VITE_RESET_CACHE_ON_START" == "true" ]]; then
      rm -rf "$frontend_cache_dir"
    fi
    nohup env \
      VITE_CACHE_DIR="$frontend_cache_dir" \
      VITE_API_MODE=api \
      VITE_API_BASE_URL="http://127.0.0.1:${BACKEND_PORT}" \
      npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT" --force \
      >>"$FRONTEND_LOG" 2>&1 < /dev/null &
    local started_pid=$!
    echo "$started_pid" >"$FRONTEND_PID_FILE"
    write_identity_file "$FRONTEND_ID_FILE" "$started_pid" "$identity"
  )
}

stop_one() {
  local name="$1"
  local pid_file="$2"
  local pid=""
  pid="$(read_pid "$pid_file" 2>/dev/null || true)"
  if is_pid_running "$pid"; then
    echo "[$name] stopping pid=$pid"
    kill "$pid" >/dev/null 2>&1 || true
    sleep 1
    if is_pid_running "$pid"; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  else
    echo "[$name] no tracked pid running, trying port-based cleanup"
  fi

  if [[ "$name" == "frontend" ]]; then
    while IFS= read -r p; do kill "$p" >/dev/null 2>&1 || true; done < <(port_listener_pids "$FRONTEND_PORT")
    sleep 1
    while IFS= read -r p; do kill -9 "$p" >/dev/null 2>&1 || true; done < <(port_listener_pids "$FRONTEND_PORT")
  elif [[ "$name" == "backend" ]]; then
    while IFS= read -r p; do kill "$p" >/dev/null 2>&1 || true; done < <(port_listener_pids "$BACKEND_PORT")
    sleep 1
    while IFS= read -r p; do kill -9 "$p" >/dev/null 2>&1 || true; done < <(port_listener_pids "$BACKEND_PORT")
  elif [[ "$name" == "sidecar" ]]; then
    while IFS= read -r p; do kill "$p" >/dev/null 2>&1 || true; done < <(port_listener_pids "$SIDECAR_PORT")
    sleep 1
    while IFS= read -r p; do kill -9 "$p" >/dev/null 2>&1 || true; done < <(port_listener_pids "$SIDECAR_PORT")
  fi

  rm -f "$pid_file"
  if [[ "$name" == "frontend" ]]; then
    rm -f "$FRONTEND_ID_FILE"
  elif [[ "$name" == "backend" ]]; then
    rm -f "$BACKEND_ID_FILE"
  elif [[ "$name" == "sidecar" ]]; then
    rm -f "$SIDECAR_ID_FILE"
  fi
}

status() {
  local spid bpid fpid
  spid="$(read_pid "$SIDECAR_PID_FILE" 2>/dev/null || true)"
  bpid="$(read_pid "$BACKEND_PID_FILE" 2>/dev/null || true)"
  fpid="$(read_pid "$FRONTEND_PID_FILE" 2>/dev/null || true)"
  local sstate="down" bstate="down" fstate="down"
  local shttp="down" bhttp="down" fhttp="down"
  local sidentity="missing" bidentity="missing" fidentity="missing"
  local expected_sidecar_identity expected_backend_identity expected_frontend_identity
  resolve_redis_port
  load_sidecar_shared_key
  expected_sidecar_identity="$(sidecar_expected_identity)"
  expected_backend_identity="$(backend_expected_identity)"
  expected_frontend_identity="$(frontend_expected_identity)"

  if is_port_listening "$SIDECAR_PORT"; then
    if sidecar_matches_workspace; then
      if is_pid_running "$spid"; then
        sstate="up(pid=$spid)"
      else
        sstate="up(port=$SIDECAR_PORT)"
      fi
      if identity_file_matches "$SIDECAR_ID_FILE" "$spid" "$expected_sidecar_identity"; then
        sidentity="current"
      else
        sidentity="stale"
      fi
    else
      sstate="up(port=$SIDECAR_PORT,mismatch)"
      sidentity="mismatch"
    fi
  fi
  if is_port_listening "$BACKEND_PORT"; then
    if backend_matches_workspace; then
      if is_pid_running "$bpid"; then
        bstate="up(pid=$bpid)"
      else
        bstate="up(port=$BACKEND_PORT)"
      fi
      if identity_file_matches "$BACKEND_ID_FILE" "$bpid" "$expected_backend_identity"; then
        bidentity="current"
      else
        bidentity="stale"
      fi
    else
      bstate="up(port=$BACKEND_PORT,mismatch)"
      bidentity="mismatch"
    fi
  fi
  if is_port_listening "$FRONTEND_PORT"; then
    if is_pid_running "$fpid"; then
      if frontend_matches_workspace; then
        fstate="up(pid=$fpid)"
      else
        fstate="up(pid=$fpid,mismatch)"
      fi
    else
      if frontend_matches_workspace; then
        fstate="up(port=$FRONTEND_PORT)"
      else
        fstate="up(port=$FRONTEND_PORT,mismatch)"
      fi
    fi
    if identity_file_matches "$FRONTEND_ID_FILE" "$fpid" "$expected_frontend_identity"; then
      fidentity="current"
    elif [[ -n "$fpid" ]] && is_pid_running "$fpid"; then
      fidentity="stale"
    else
      fidentity="mismatch"
    fi
  fi
  if curl_health "$BACKEND_HEALTH_URL"; then
    bhttp="ok"
  fi
  if curl_health "$SIDECAR_HEALTH_URL"; then
    shttp="ok"
  fi
  if curl --connect-timeout 2 --max-time 3 -fsS -o /dev/null "$FRONTEND_URL"; then
    fhttp="ok"
  fi

  echo "sidecar: $sstate http=$shttp identity=$sidentity url=$SIDECAR_HEALTH_URL"
  echo "backend: $bstate http=$bhttp identity=$bidentity url=$BACKEND_HEALTH_URL"
  echo "frontend: $fstate http=$fhttp identity=$fidentity url=$FRONTEND_URL"
}

logs() {
  echo "=== sidecar ($SIDECAR_LOG) ==="
  tail -n 80 "$SIDECAR_LOG" 2>/dev/null || true
  echo
  echo "=== backend ($BACKEND_LOG) ==="
  tail -n 80 "$BACKEND_LOG" 2>/dev/null || true
  echo
  echo "=== frontend ($FRONTEND_LOG) ==="
  tail -n 80 "$FRONTEND_LOG" 2>/dev/null || true
}

watchdog_pid() {
  read_pid "$WATCHDOG_PID_FILE" 2>/dev/null || true
}

watchdog_process_pids() {
  pgrep -f "${ROOT_DIR}/scripts/dev-services.sh.*watch-run" 2>/dev/null | while IFS= read -r pid; do
    [[ "$pid" != "$$" ]] || continue
    printf '%s\n' "$pid"
  done
}

watchdog_running() {
  local pid="$(watchdog_pid)"
  if is_pid_running "$pid"; then
    return 0
  fi
  [[ -n "$(watchdog_process_pids)" ]]
}

watchdog_loop() {
  echo "$$" >"$WATCHDOG_PID_FILE"
  trap 'rm -f "$WATCHDOG_PID_FILE"' EXIT
  echo "[$(date '+%F %T')] watchdog loop started interval=${WATCHDOG_INTERVAL_SECONDS}s"
  local backend_failures=0
  local backend_next_retry=0
  while true; do
    if ! curl_health "$SIDECAR_HEALTH_URL"; then
      echo "[$(date '+%F %T')] sidecar unhealthy, attempting restart"
      if start_sidecar; then
        wait_http "$SIDECAR_HEALTH_URL" "$SIDECAR_START_TIMEOUT_SECONDS" >/dev/null 2>&1 || true
      else
        echo "[$(date '+%F %T')] sidecar restart skipped because port identity check failed"
      fi
    fi

    # Backend with exponential backoff + max-failure stop. See ericforai/bidding#232.
    if [[ -f "$BACKEND_FAIL_STATE" ]]; then
      : # Stopped state, skip until human intervention removes the file
    elif ! curl_health "$BACKEND_HEALTH_URL"; then
      local now_epoch=$(date +%s)
      if (( now_epoch < backend_next_retry )); then
        : # In backoff window, skip
      else
        echo "[$(date '+%F %T')] backend unhealthy, attempting restart (failure #$((backend_failures + 1)))"
        if start_backend; then
          if wait_http "$BACKEND_HEALTH_URL" "$BACKEND_START_TIMEOUT_SECONDS" >/dev/null 2>&1; then
            backend_failures=0
            backend_next_retry=0
            echo "[$(date '+%F %T')] backend restart succeeded, failure counter reset"
          else
            backend_failures=$((backend_failures + 1))
          fi
        else
          backend_failures=$((backend_failures + 1))
          echo "[$(date '+%F %T')] backend restart skipped because port identity check failed"
        fi

        if (( backend_failures >= WATCHDOG_BACKEND_MAX_FAILURES )); then
          local last_error_line=$(grep -E "^\[ERROR\]|Caused by|Migrations have failed|FAILED" "$BACKEND_LOG" 2>/dev/null | tail -1 || echo "(no error line found in backend.log)")
          {
            echo "last_error_line: ${last_error_line}"
            echo "failures: ${backend_failures}"
            echo "stopped_at: $(date '+%F %T')"
            echo "resume_with: rm \"${BACKEND_FAIL_STATE}\" && \"${ROOT_DIR}/scripts/dev-services.sh\" start"
          } >"$BACKEND_FAIL_STATE"
          echo "[$(date '+%F %T')] backend failed ${backend_failures} times, giving up. State written to ${BACKEND_FAIL_STATE}"
        elif (( backend_failures >= 1 )); then
          # Exponential backoff: 30s, 2m, 10m, then cap at 30m
          local backoff_seconds
          case "$backend_failures" in
            1) backoff_seconds=30 ;;
            2) backoff_seconds=120 ;;
            3) backoff_seconds=600 ;;
            *) backoff_seconds=1800 ;;
          esac
          backend_next_retry=$((now_epoch + backoff_seconds))
          echo "[$(date '+%F %T')] backend backoff: next retry in ${backoff_seconds}s"
        fi
      fi
    elif (( backend_failures > 0 )); then
      backend_failures=0
      backend_next_retry=0
      echo "[$(date '+%F %T')] backend recovered, failure counter reset"
    fi

    if ! frontend_matches_workspace; then
      echo "[$(date '+%F %T')] frontend unhealthy, attempting restart"
      if start_frontend; then
        wait_frontend "$FRONTEND_START_TIMEOUT_SECONDS" >/dev/null 2>&1 || true
      else
        echo "[$(date '+%F %T')] frontend restart skipped because port identity check failed"
      fi
    fi

    sleep "$WATCHDOG_INTERVAL_SECONDS"
  done
}

watchdog_start() {
  if watchdog_running; then
    echo "[watchdog] already running (pid=$(watchdog_pid))"
    return 0
  fi

  : >"$WATCHDOG_LOG"
  nohup bash -lc "cd \"$ROOT_DIR\" && \"$0\" --profile \"$ACTIVE_BACKEND_PROFILE\" watch-run" >>"$WATCHDOG_LOG" 2>&1 < /dev/null &
  echo $! >"$WATCHDOG_PID_FILE"
  sleep 1
  if watchdog_running; then
    echo "[watchdog] started pid=$(watchdog_pid), interval=${WATCHDOG_INTERVAL_SECONDS}s, profile=${ACTIVE_BACKEND_PROFILE}"
  else
    echo "[watchdog] failed to start"
    tail -n 80 "$WATCHDOG_LOG" 2>/dev/null || true
    exit 1
  fi
}

watchdog_stop() {
  local pid stopped=false
  pid="$(watchdog_pid)"
  if is_pid_running "$pid"; then
    echo "[watchdog] stopping pid=$pid"
    kill "$pid" >/dev/null 2>&1 || true
    stopped=true
    sleep 1
    if is_pid_running "$pid"; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  fi
  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    echo "[watchdog] stopping untracked pid=$pid"
    kill "$pid" >/dev/null 2>&1 || true
    stopped=true
    sleep 1
    if is_pid_running "$pid"; then
      kill -9 "$pid" >/dev/null 2>&1 || true
    fi
  done < <(watchdog_process_pids)
  if [[ "$stopped" == false ]]; then
    echo "[watchdog] not running"
  fi
  rm -f "$WATCHDOG_PID_FILE"
}

watchdog_status() {
  local state="down"
  if watchdog_running; then
    local pid="$(watchdog_pid)"
    if is_pid_running "$pid"; then
      state="up(pid=$pid, interval=${WATCHDOG_INTERVAL_SECONDS}s)"
    else
      state="up(untracked=$(watchdog_process_pids | tr '\n' ',' | sed 's/,$//'), interval=${WATCHDOG_INTERVAL_SECONDS}s)"
    fi
  fi
  echo "watchdog: $state"
  if [[ -f "$BACKEND_FAIL_STATE" ]]; then
    echo ""
    echo "backend: STOPPED (watchdog gave up after repeated failures)"
    sed 's/^/  /' "$BACKEND_FAIL_STATE"
  fi
}

usage() {
  cat <<'EOF'
Usage: scripts/dev-services.sh [--profile <spring_profiles>] <start|stop|restart|status|logs|watch-start|watch-stop|watch-status|healthcheck>

Examples:
  scripts/dev-services.sh start
  scripts/dev-services.sh --profile e2e restart
  scripts/dev-services.sh --profile dev,mysql watch-start

Environment:
  DB_HOST/DB_PORT/DB_NAME    MySQL connection target
  DB_USERNAME/DB_PASSWORD    MySQL credentials
  REDIS_HOST/REDIS_PORT      Redis connection target
  SIDECAR_HOST/SIDECAR_PORT  Document converter sidecar bind target
  JWT_SECRET                 JWT secret for local auth
  SIDECAR_START_TIMEOUT_SECONDS/BACKEND_START_TIMEOUT_SECONDS/FRONTEND_START_TIMEOUT_SECONDS
                             Startup wait budgets (defaults: 60/300/90)
  VITE_RESET_CACHE_ON_START  Remove runtime Vite optimizer cache before frontend start (default: true)
EOF
}

CMD=""
parse_args "$@"

# ──────────────────────────────────────────────
# healthcheck — Docker 模式自愈检查
# ──────────────────────────────────────────────
healthcheck() {
  local exit_code=0
  local action_taken=false

  echo "======================================"
  echo " 服务健康检查 & 自愈 - $(date '+%Y-%m-%d %H:%M:%S')"
  echo "======================================"

  # ── Step 1: Docker daemon ──
  echo ""
  echo "【1/4】检查 Docker daemon..."
  if ! docker ps >/dev/null 2>&1; then
    echo "  ⚠️  Docker daemon 未运行，正在启动 Docker Desktop..."
    open -a Docker
    echo "  ⏳ 等待 Docker socket..."
    local i=0
    while [ $i -lt 30 ]; do
      sleep 2
      if docker ps >/dev/null 2>&1; then
        echo "  ✅ Docker daemon 已就绪（$((i*2))秒）"
        action_taken=true
        break
      fi
      i=$((i+1))
    done
    if ! docker ps >/dev/null 2>&1; then
      echo "  ❌ Docker daemon 启动超时"
      exit_code=1
    fi
  else
    echo "  ✅ Docker daemon 运行中"
  fi

  # ── Step 2: Docker 容器 ──
  echo ""
  echo "【2/4】检查 Docker 容器..."
  local container_names="xiyu-bid-mysql xiyu-bid-redis xiyu-bid-backend"
  local all_containers_up=true

  for name in $container_names; do
    local state="$(docker inspect --format '{{.State.Status}}' "$name" 2>/dev/null || echo 'missing')"
    case "$state" in
      running)
        echo "  ✅ $name - running"
        ;;
      missing)
        echo "  ⚠️  $name - 容器不存在，执行 docker compose up -d..."
        all_containers_up=false
        ;;
      *)
        echo "  ⚠️  ${name} - 状态=${state}，重新启动..."
        docker start "$name" >/dev/null 2>&1 && echo "  ✅ ${name} 已重启" || echo "  ❌ ${name} 重启失败"
        all_containers_up=false
        action_taken=true
        ;;
    esac
  done

  if [ "$all_containers_up" = false ]; then
    echo "  ⏳ 正在启动 Docker Compose 服务..."
    (cd "$ROOT_DIR" && XIYU_DEV_CONFIRMED=1 docker compose up -d 2>/dev/null)
    action_taken=true
  fi

  # ── Step 3: 后端健康 ──
  echo ""
  echo "【3/4】检查后端服务..."
  if curl_health "$BACKEND_HEALTH_URL"; then
    echo "  ✅ 后端 - http://127.0.0.1:${BACKEND_PORT}/actuator/health → ok"
  else
    echo "  ⚠️  后端不健康，等待 30 秒..."
    if wait_http "$BACKEND_HEALTH_URL" 30; then
      echo "  ✅ 后端已恢复"
      action_taken=true
    else
      echo "  ⚠️  后端仍未就绪，尝试 docker compose restart backend..."
      (cd "$ROOT_DIR" && docker compose restart backend 2>/dev/null)
      if wait_http "$BACKEND_HEALTH_URL" 60; then
        echo "  ✅ 后端已恢复"
        action_taken=true
      else
        echo "  ❌ 后端无法启动，请检查日志：docker compose logs backend"
        exit_code=1
      fi
    fi
  fi

  # ── Step 4: 前端 + sidecar ──
  echo ""
  echo "【4/4】检查前端 & sidecar..."
  # Sidecar
  if curl_health "$SIDECAR_HEALTH_URL"; then
    echo "  ✅ sidecar - ${SIDECAR_HEALTH_URL} → ok"
  else
    echo "  ⚠️  sidecar 不可用，尝试启动..."
    local spid="$(read_pid "$SIDECAR_PID_FILE" 2>/dev/null || true)"
    if is_pid_running "$spid"; then
      echo "  ⚠️  PID ${spid} 存在但端口不通，杀掉重启..."
      kill "$spid" 2>/dev/null || true
    fi
    start_sidecar
    if wait_http "$SIDECAR_HEALTH_URL" "$SIDECAR_START_TIMEOUT_SECONDS"; then
      echo "  ✅ sidecar 已启动"
      action_taken=true
    else
      echo "  ❌ sidecar 启动失败"
      exit_code=1
    fi
  fi

  # 前端
  if curl --connect-timeout 2 --max-time 3 -fsS -o /dev/null "$FRONTEND_URL"; then
    echo "  ✅ 前端 - ${FRONTEND_URL} → ok"
  else
    echo "  ⚠️  前端不可用，尝试重启..."
    local fpid="$(read_pid "$FRONTEND_PID_FILE" 2>/dev/null || true)"
    if is_pid_running "$fpid"; then
      kill "$fpid" 2>/dev/null || true
      sleep 1
    fi
    # 杀掉遗留 screen
    screen -S main-frontend -X quit 2>/dev/null || true
    start_frontend
    if wait_frontend "$FRONTEND_START_TIMEOUT_SECONDS"; then
      echo "  ✅ 前端已启动"
      action_taken=true
    else
      echo "  ❌ 前端启动失败"
      exit_code=1
    fi
  fi

  # ── 总结 ──
  echo ""
  echo "======================================"
  if [ "$exit_code" -eq 0 ]; then
    if [ "$action_taken" = true ]; then
      echo " ✅ 所有服务已恢复"
    else
      echo " ✅ 所有服务运行正常，无需修复"
    fi
  else
    echo " ❌ 部分服务异常，请手动检查"
  fi
  echo "======================================"
  echo ""
  status
  return $exit_code
}


case "$CMD" in
  start)
    if [[ -f "$BACKEND_FAIL_STATE" ]]; then
      echo "[start] backend is in stopped state from a previous run:"
      sed 's/^/  /' "$BACKEND_FAIL_STATE"
      echo ""
      echo "Investigate the last_error_line above, then resume with:"
      echo "  rm \"$BACKEND_FAIL_STATE\" && \"$0\" start"
      exit 1
    fi
    # Pre-clean all ports before starting any service to avoid conflicts
    # from other worktree processes (e.g. trae watchdog restarting vite on 1314)
    for _svc in frontend backend sidecar; do
      case "$_svc" in
        frontend) _port="$FRONTEND_PORT" ;;
        backend)  _port="$BACKEND_PORT" ;;
        sidecar)  _port="$SIDECAR_PORT" ;;
      esac
      if is_port_listening "$_port"; then
        echo "[start] pre-cleaning port $_port for $_svc"
        while IFS= read -r _p; do
          kill -TERM "$_p" 2>/dev/null || true
        done < <(port_listener_pids "$_port")
        sleep 1
        while IFS= read -r _p; do
          kill -KILL "$_p" 2>/dev/null || true
        done < <(port_listener_pids "$_port")
      fi
    done

    # Sidecar: pre-clean port before start (handles external watchdog restarts during mvn)
    while IFS= read -r _p; do
      kill -TERM "$_p" 2>/dev/null || true
    done < <(port_listener_pids "$SIDECAR_PORT")
    sleep 1
    while IFS= read -r _p; do
      kill -KILL "$_p" 2>/dev/null || true
    done < <(port_listener_pids "$SIDECAR_PORT")
    start_sidecar
    if ! wait_http "$SIDECAR_HEALTH_URL" "$SIDECAR_START_TIMEOUT_SECONDS"; then
      echo "[sidecar] failed to become healthy. See logs:"
      logs
      exit 1
    fi
    # Backend: pre-clean port before mvn package (mvn takes minutes, external processes may reclaim)
    while IFS= read -r _p; do
      kill -TERM "$_p" 2>/dev/null || true
    done < <(port_listener_pids "$BACKEND_PORT")
    sleep 1
    while IFS= read -r _p; do
      kill -KILL "$_p" 2>/dev/null || true
    done < <(port_listener_pids "$BACKEND_PORT")
    start_backend
    if ! wait_http "$BACKEND_HEALTH_URL" "$BACKEND_START_TIMEOUT_SECONDS"; then
      echo "[backend] failed to become healthy. See logs:"
      logs
      exit 1
    fi
    # Frontend: pre-clean port before starting vite (narrow window after mvn)
    while IFS= read -r _p; do
      kill -TERM "$_p" 2>/dev/null || true
    done < <(port_listener_pids "$FRONTEND_PORT")
    sleep 1
    while IFS= read -r _p; do
      kill -KILL "$_p" 2>/dev/null || true
    done < <(port_listener_pids "$FRONTEND_PORT")
    start_frontend
    if ! wait_frontend "$FRONTEND_START_TIMEOUT_SECONDS"; then
      echo "[frontend] failed to become healthy. See logs:"
      print_frontend_mismatch
      logs
      exit 1
    fi
    echo "services started successfully."
    echo "backend profile: $ACTIVE_BACKEND_PROFILE"
    echo ""
    echo "starting watchdog (auto-heal, interval=${WATCHDOG_INTERVAL_SECONDS:-30}s) ..."
    if ! watchdog_running; then
      watchdog_start
    fi
    status
    echo ""
    echo "use \`${0} watch-stop\` to stop watchdog"
    echo "use \`${0} healthcheck\` to run one-shot check"
    ;;
  stop)
    watchdog_stop
    stop_one "frontend" "$FRONTEND_PID_FILE"
    stop_one "backend" "$BACKEND_PID_FILE"
    stop_one "sidecar" "$SIDECAR_PID_FILE"
    ;;
  restart)
    "$0" --profile "$ACTIVE_BACKEND_PROFILE" stop
    "$0" --profile "$ACTIVE_BACKEND_PROFILE" start
    ;;
  status)
    status
    watchdog_status
    ;;
  logs)
    logs
    echo
    echo "=== watchdog ($WATCHDOG_LOG) ==="
    tail -n 80 "$WATCHDOG_LOG" 2>/dev/null || true
    ;;
  watch-start)
    watchdog_start
    ;;
  watch-stop)
    watchdog_stop
    ;;
  watch-status)
    watchdog_status
    ;;
  watch-run)
    watchdog_loop
    ;;
  healthcheck)
    healthcheck
    ;;
  *)
    usage
    exit 1
    ;;
esac
