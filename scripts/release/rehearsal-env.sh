#!/usr/bin/env bash
# Input: release environment variables, MySQL 8.0 rehearsal configuration, admin bootstrap credentials, and optional rehearsal overrides
# Output: shared MySQL 8.0 defaults for release scripts
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
BACKEND_DIR="${BACKEND_DIR:-$ROOT_DIR/backend}"
resolve_path() {
  local path="$1"
  if [[ "$path" = /* ]]; then
    printf '%s\n' "$path"
  else
    printf '%s\n' "$ROOT_DIR/$path"
  fi
}

validate_port() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || (( value < 1 || value > 65535 )); then
    printf 'Invalid %s: %s. Must be an integer between 1 and 65535.\n' "$name" "$value" >&2
    exit 1
  fi
}

REPORT_DIR="$(resolve_path "${REPORT_DIR:-$ROOT_DIR/docs/reports}")"
STATE_DIR="$(resolve_path "${STATE_DIR:-$ROOT_DIR/.rehearsal}")"

DB_ENGINE="${DB_ENGINE:-mysql}"
MYSQL_CONTAINER_NAME="${MYSQL_CONTAINER_NAME:-xiyu-bid-rehearsal-mysql}"
REDIS_CONTAINER_NAME="${REDIS_CONTAINER_NAME:-xiyu-bid-rehearsal-redis}"
MYSQL_PORT="${MYSQL_PORT:-53306}"
REDIS_PORT="${REDIS_PORT:-56379}"
BACKEND_PORT="${BACKEND_PORT:-18080}"
FRONTEND_PORT="${FRONTEND_PORT:-1314}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_NAME="${DB_NAME:-xiyu_bid}"
DB_USER="${DB_USER:-${DB_USERNAME:-xiyu_user}}"
DB_USERNAME="${DB_USERNAME:-$DB_USER}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD must be set for rehearsal}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD must be set for rehearsal}"
JWT_SECRET="${JWT_SECRET:?JWT_SECRET must be set for rehearsal (32+ chars)}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://127.0.0.1:${FRONTEND_PORT},http://localhost:${FRONTEND_PORT}}"
PLATFORM_ENCRYPTION_KEY="${PLATFORM_ENCRYPTION_KEY:?PLATFORM_ENCRYPTION_KEY must be set for rehearsal (16+ chars)}"
UAT_TEST_PASSWORD="${UAT_TEST_PASSWORD:?UAT_TEST_PASSWORD must be set for rehearsal}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?ADMIN_PASSWORD must be set for rehearsal}"

UAT_API_BASE_URL="${UAT_API_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"
UAT_WEB_BASE_URL="${UAT_WEB_BASE_URL:-http://127.0.0.1:${FRONTEND_PORT}}"
PLAYWRIGHT_API_BASE_URL="${PLAYWRIGHT_API_BASE_URL:-$UAT_API_BASE_URL}"

case "$DB_ENGINE" in
  mysql)
    DB_PORT="${DB_PORT:-$MYSQL_PORT}"
    DEFAULT_SPRING_PROFILES_ACTIVE="prod,mysql"
    DEFAULT_DB_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    ;;
  *)
    printf 'Unsupported DB_ENGINE: %s. Use mysql.\n' "$DB_ENGINE" >&2
    exit 1
    ;;
esac

SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-$DEFAULT_SPRING_PROFILES_ACTIVE}"
DB_URL="${DB_URL:-$DEFAULT_DB_URL}"

validate_port "MYSQL_PORT" "$MYSQL_PORT"
validate_port "REDIS_PORT" "$REDIS_PORT"
validate_port "BACKEND_PORT" "$BACKEND_PORT"
validate_port "FRONTEND_PORT" "$FRONTEND_PORT"
validate_port "DB_PORT" "$DB_PORT"

export ROOT_DIR BACKEND_DIR REPORT_DIR STATE_DIR
export DB_ENGINE MYSQL_CONTAINER_NAME REDIS_CONTAINER_NAME MYSQL_PORT REDIS_PORT BACKEND_PORT FRONTEND_PORT
export DB_HOST DB_PORT DB_NAME DB_USER DB_USERNAME DB_PASSWORD MYSQL_ROOT_PASSWORD DB_URL JWT_SECRET SPRING_PROFILES_ACTIVE REDIS_HOST
export CORS_ALLOWED_ORIGINS
export PLATFORM_ENCRYPTION_KEY UAT_TEST_PASSWORD ADMIN_PASSWORD UAT_API_BASE_URL UAT_WEB_BASE_URL PLAYWRIGHT_API_BASE_URL

mkdir -p "$REPORT_DIR" "$STATE_DIR"
