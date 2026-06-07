#!/usr/bin/env bash
# Input: optional local Docker stack command and environment overrides for ports, database, Redis, and credentials
# Output: starts/stops local MySQL/Redis containers, writes .runtime/local-docker/local-docker.env, and can launch real-API dev services
# Pos: scripts/ - macOS local Docker bootstrap for running the real API path without host MySQL
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

# --- DEV-ONLY GUARD ---------------------------------------------
# This script bakes in convenience defaults for JWT/DB credentials.
# The guard fires only for subcommands that bring up containers / propagate
# those secrets to running processes (up/dev). Read-only / teardown
# subcommands (status/logs/stop) load the variable defaults harmlessly
# without forwarding them, so they remain safe to run without an opt-in.
_env_lower() { echo "${1:-}" | tr '[:upper:]' '[:lower:]'; }
_is_nonprod_value() {
  case "$(_env_lower "$1")" in
    *prod*|*production*|*staging*|*stg*|*release*|*live*|*uat*|*canary*) return 1;;
    *) return 0;;
  esac
}
_dev_guard_subcommand_requires_opt_in() {
  # No-args defaults to `up` per the dispatcher (line further below); treat
  # that as a mutating invocation so the empty-args path can't bypass the
  # guard. Any positional arg matching a mutating subcommand also fires it.
  if [[ $# -eq 0 ]]; then
    return 0
  fi
  for a in "$@"; do
    case "$a" in
      up|dev|env)
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
RUNTIME_DIR="$ROOT_DIR/.runtime/local-docker"
ENV_FILE="$RUNTIME_DIR/local-docker.env"

MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-xiyu-bid-local-mysql}"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-xiyu-bid-local-redis}"
MYSQL_VOLUME_NAME="${MYSQL_VOLUME_NAME:-xiyu-bid-local-mysql-data}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"

BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-1314}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-xiyu_bid_main}"
DB_USERNAME="${DB_USERNAME:-xiyu_user}"
DB_PASSWORD="${DB_PASSWORD:-XiyuDB!2026}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-XiyuRoot!2026}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DB="${REDIS_DB:-0}"
SPRING_DATA_REDIS_DATABASE="${SPRING_DATA_REDIS_DATABASE:-$REDIS_DB}"
JWT_SECRET="${JWT_SECRET:-xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-90}"

usage() {
  cat <<'EOF'
Usage: scripts/local-docker-stack.sh <up|dev|stop|status|logs|env>

Commands: up, dev, stop, status, logs, env.
Common overrides:
  DB_PORT=13306 REDIS_PORT=16379 scripts/local-docker-stack.sh dev
  DB_NAME=xiyu_bid_main DB_PASSWORD='...' scripts/local-docker-stack.sh up
EOF
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    printf '[local-docker] missing required command: docker\n' >&2
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    printf '[local-docker] Docker is not running. Start Docker Desktop and retry.\n' >&2
    exit 1
  fi
}

container_exists() {
  docker container inspect "$1" >/dev/null 2>&1
}

container_running() {
  [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || true)" == "true" ]]
}

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

ensure_port_free_for_new_container() {
  local name="$1"
  local port="$2"
  local override_hint="$3"
  if port_in_use "$port"; then
    printf '[local-docker] port %s is already in use; cannot create %s.\n' "$port" "$name" >&2
    printf '[local-docker] Retry with %s, or stop the conflicting service.\n' "$override_hint" >&2
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2 || true
    exit 1
  fi
}

mapped_host_port() {
  local container="$1"
  local container_port="$2"
  docker port "$container" "$container_port/tcp" 2>/dev/null | awk -F: 'NR == 1 {print $NF}'
}

align_ports_from_existing_containers() {
  local mapped
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    return
  fi
  if container_exists "$MYSQL_CONTAINER_NAME"; then
    mapped="$(mapped_host_port "$MYSQL_CONTAINER_NAME" 3306)"
    if [[ -n "$mapped" && "$mapped" != "$DB_PORT" ]]; then
      printf '[local-docker] existing MySQL container uses host port %s; using DB_PORT=%s.\n' "$mapped" "$mapped"
      DB_PORT="$mapped"
    fi
  fi
  if container_exists "$REDIS_CONTAINER_NAME"; then
    mapped="$(mapped_host_port "$REDIS_CONTAINER_NAME" 6379)"
    if [[ -n "$mapped" && "$mapped" != "$REDIS_PORT" ]]; then
      printf '[local-docker] existing Redis container uses host port %s; using REDIS_PORT=%s.\n' "$mapped" "$mapped"
      REDIS_PORT="$mapped"
    fi
  fi
}

start_mysql() {
  if container_exists "$MYSQL_CONTAINER_NAME"; then
    if container_running "$MYSQL_CONTAINER_NAME"; then
      printf '[local-docker] MySQL already running: %s\n' "$MYSQL_CONTAINER_NAME"
    else
      printf '[local-docker] starting existing MySQL container: %s\n' "$MYSQL_CONTAINER_NAME"
      docker start "$MYSQL_CONTAINER_NAME" >/dev/null
    fi
    return
  fi

  ensure_port_free_for_new_container "$MYSQL_CONTAINER_NAME" "$DB_PORT" "DB_PORT=13306"
  printf '[local-docker] creating MySQL container %s on 127.0.0.1:%s\n' "$MYSQL_CONTAINER_NAME" "$DB_PORT"
  docker run -d \
    --name "$MYSQL_CONTAINER_NAME" \
    -e MYSQL_DATABASE="$DB_NAME" \
    -e MYSQL_USER="$DB_USERNAME" \
    -e MYSQL_PASSWORD="$DB_PASSWORD" \
    -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
    -p "${DB_PORT}:3306" \
    -v "${MYSQL_VOLUME_NAME}:/var/lib/mysql" \
    "$MYSQL_IMAGE" \
    --character-set-server=utf8mb4 \
    --collation-server=utf8mb4_unicode_ci \
    >/dev/null
}

start_redis() {
  if container_exists "$REDIS_CONTAINER_NAME"; then
    if container_running "$REDIS_CONTAINER_NAME"; then
      printf '[local-docker] Redis already running: %s\n' "$REDIS_CONTAINER_NAME"
    else
      printf '[local-docker] starting existing Redis container: %s\n' "$REDIS_CONTAINER_NAME"
      docker start "$REDIS_CONTAINER_NAME" >/dev/null
    fi
    return
  fi

  ensure_port_free_for_new_container "$REDIS_CONTAINER_NAME" "$REDIS_PORT" "REDIS_PORT=16379"
  printf '[local-docker] creating Redis container %s on 127.0.0.1:%s\n' "$REDIS_CONTAINER_NAME" "$REDIS_PORT"
  docker run -d \
    --name "$REDIS_CONTAINER_NAME" \
    -p "${REDIS_PORT}:6379" \
    "$REDIS_IMAGE" \
    >/dev/null
}

wait_for_mysql() {
  local start
  start="$(date +%s)"
  while true; do
    if docker exec "$MYSQL_CONTAINER_NAME" mysqladmin ping -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" --silent >/dev/null 2>&1 &&
      docker exec "$MYSQL_CONTAINER_NAME" mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" -Nse 'select 1;' >/dev/null 2>&1; then
      printf '[local-docker] MySQL is ready: %s:%s/%s\n' "$DB_HOST" "$DB_PORT" "$DB_NAME"
      return
    fi
    if (( "$(date +%s)" - start >= WAIT_TIMEOUT_SECONDS )); then
      printf '[local-docker] MySQL did not become ready within %ss. Recent logs:\n' "$WAIT_TIMEOUT_SECONDS" >&2
      docker logs --tail 80 "$MYSQL_CONTAINER_NAME" >&2 || true
      exit 1
    fi
    sleep 2
  done
}

wait_for_redis() {
  local start
  start="$(date +%s)"
  while true; do
    if docker exec "$REDIS_CONTAINER_NAME" redis-cli ping >/dev/null 2>&1; then
      printf '[local-docker] Redis is ready: %s:%s/%s\n' "$REDIS_HOST" "$REDIS_PORT" "$REDIS_DB"
      return
    fi
    if (( "$(date +%s)" - start >= WAIT_TIMEOUT_SECONDS )); then
      printf '[local-docker] Redis did not become ready within %ss. Recent logs:\n' "$WAIT_TIMEOUT_SECONDS" >&2
      docker logs --tail 80 "$REDIS_CONTAINER_NAME" >&2 || true
      exit 1
    fi
    sleep 1
  done
}

write_env_file() {
  mkdir -p "$RUNTIME_DIR"
  {
    printf '# Generated by scripts/local-docker-stack.sh. Do not commit.\n'
    printf 'export BACKEND_PORT=%q\n' "$BACKEND_PORT"
    printf 'export FRONTEND_PORT=%q\n' "$FRONTEND_PORT"
    printf 'export DB_HOST=%q\n' "$DB_HOST"
    printf 'export DB_PORT=%q\n' "$DB_PORT"
    printf 'export DB_NAME=%q\n' "$DB_NAME"
    printf 'export DB_USERNAME=%q\n' "$DB_USERNAME"
    printf 'export DB_PASSWORD=%q\n' "$DB_PASSWORD"
    printf 'export REDIS_HOST=%q\n' "$REDIS_HOST"
    printf 'export REDIS_PORT=%q\n' "$REDIS_PORT"
    printf 'export REDIS_DB=%q\n' "$REDIS_DB"
    printf 'export SPRING_DATA_REDIS_DATABASE=%q\n' "$SPRING_DATA_REDIS_DATABASE"
    printf 'export JWT_SECRET=%q\n' "$JWT_SECRET"
    printf 'export VITE_API_MODE=api\n'
    printf 'export VITE_API_BASE_URL=%q\n' "http://127.0.0.1:${BACKEND_PORT}"
    printf 'export MYSQL_CONTAINER_NAME=%q\n' "$MYSQL_CONTAINER_NAME"
    printf 'export REDIS_CONTAINER_NAME=%q\n' "$REDIS_CONTAINER_NAME"
  } >"$ENV_FILE"
  chmod 600 "$ENV_FILE"
}

print_env_hint() {
  printf '[local-docker] env file: %s\n' "$ENV_FILE"
  printf '[local-docker] use it manually with: source "%s"\n' "$ENV_FILE"
}

up_stack() {
  require_docker
  start_mysql
  start_redis
  align_ports_from_existing_containers
  wait_for_mysql
  wait_for_redis
  write_env_file
  print_env_hint
}

start_dev_services() {
  up_stack
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  exec "$ROOT_DIR/scripts/dev-services.sh" start
}

stop_stack() {
  [[ -f "$ENV_FILE" ]] && source "$ENV_FILE"
  "$ROOT_DIR/scripts/dev-services.sh" stop || true
  require_docker
  for container in "$REDIS_CONTAINER_NAME" "$MYSQL_CONTAINER_NAME"; do
    if container_exists "$container"; then
      printf '[local-docker] stopping %s\n' "$container"
      docker stop "$container" >/dev/null || true
    fi
  done
}

status_stack() {
  [[ -f "$ENV_FILE" ]] && source "$ENV_FILE"
  require_docker
  printf '[local-docker] variables: DB=%s:%s/%s user=%s Redis=%s:%s/%s frontend=%s backend=%s\n' \
    "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USERNAME" "$REDIS_HOST" "$REDIS_PORT" "$REDIS_DB" "$FRONTEND_PORT" "$BACKEND_PORT"
  docker ps -a --filter "name=${MYSQL_CONTAINER_NAME}" --filter "name=${REDIS_CONTAINER_NAME}" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
  "$ROOT_DIR/scripts/dev-services.sh" status || true
}

logs_stack() {
  [[ -f "$ENV_FILE" ]] && source "$ENV_FILE"
  require_docker
  for container in "$MYSQL_CONTAINER_NAME" "$REDIS_CONTAINER_NAME"; do
    if container_exists "$container"; then
      printf '\n=== %s ===\n' "$container"
      docker logs --tail 120 "$container" || true
    fi
  done
  printf '\n=== dev services ===\n'
  "$ROOT_DIR/scripts/dev-services.sh" logs || true
}

case "${1:-up}" in
  up)
    up_stack
    ;;
  dev)
    start_dev_services
    ;;
  stop)
    stop_stack
    ;;
  status)
    status_stack
    ;;
  logs)
    logs_stack
    ;;
  env)
    align_ports_from_existing_containers
    write_env_file
    print_env_hint
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage >&2
    exit 1
    ;;
esac
