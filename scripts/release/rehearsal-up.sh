#!/usr/bin/env bash
# Input: rehearsal environment variables, MySQL 8.0 container configuration, and backend build inputs
# Output: running MySQL 8.0 rehearsal services, backend/frontend pid files, and failure log tails
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/release/rehearsal-env.sh"

print_backend_log_tail() {
  if [[ -f "$STATE_DIR/backend.log" ]]; then
    printf '\n==> Backend log tail\n' >&2
    tail -n 200 "$STATE_DIR/backend.log" >&2 || true
  fi
}

wait_for_backend_health() {
  local pid

  printf '==> Waiting for backend health\n'
  for i in {1..180}; do
    if curl -fsS "$UAT_API_BASE_URL/actuator/health" >/dev/null 2>&1; then
      return 0
    fi

    if [[ -f "$STATE_DIR/backend.pid" ]]; then
      pid="$(cat "$STATE_DIR/backend.pid")"
      if ! kill -0 "$pid" >/dev/null 2>&1; then
        printf 'Backend process exited before health check passed\n' >&2
        print_backend_log_tail
        return 1
      fi
    fi

    sleep 2
  done

  printf 'Backend health check timed out: %s\n' "$UAT_API_BASE_URL/actuator/health" >&2
  print_backend_log_tail
  return 1
}

start_database() {
  case "$DB_ENGINE" in
    mysql)
      printf '==> Starting MySQL container %s on %s\n' "$MYSQL_CONTAINER_NAME" "$MYSQL_PORT"
      docker rm -f "$MYSQL_CONTAINER_NAME" >/dev/null 2>&1 || true
      docker run -d \
        --name "$MYSQL_CONTAINER_NAME" \
        -e MYSQL_DATABASE="$DB_NAME" \
        -e MYSQL_USER="$DB_USER" \
        -e MYSQL_PASSWORD="$DB_PASSWORD" \
        -e MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
        -p "${MYSQL_PORT}:3306" \
        mysql:8.0 \
        --character-set-server=utf8mb4 \
        --collation-server=utf8mb4_unicode_ci >/dev/null
      ;;
  esac
}

wait_for_database() {
  case "$DB_ENGINE" in
    mysql)
      printf '==> Waiting for MySQL\n'
      for i in {1..90}; do
        if docker exec "$MYSQL_CONTAINER_NAME" mysqladmin ping -h127.0.0.1 -u"$DB_USER" -p"$DB_PASSWORD" --silent >/dev/null 2>&1; then
          break
        fi
        sleep 1
      done
      docker exec "$MYSQL_CONTAINER_NAME" mysqladmin ping -h127.0.0.1 -u"$DB_USER" -p"$DB_PASSWORD" --silent >/dev/null
      docker exec "$MYSQL_CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -Nse 'select 1;' >/dev/null
      ;;
  esac
}

count_users() {
  case "$DB_ENGINE" in
    mysql)
      docker exec "$MYSQL_CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -Nse 'select count(*) from users;'
      ;;
  esac
}

start_database

printf '==> Starting Redis container %s on %s\n' "$REDIS_CONTAINER_NAME" "$REDIS_PORT"
docker rm -f "$REDIS_CONTAINER_NAME" >/dev/null 2>&1 || true
docker run -d \
  --name "$REDIS_CONTAINER_NAME" \
  -p "${REDIS_PORT}:6379" \
  redis:7-alpine >/dev/null

wait_for_database

printf '==> Waiting for Redis\n'
for i in {1..60}; do
  if docker exec "$REDIS_CONTAINER_NAME" redis-cli ping >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$REDIS_CONTAINER_NAME" redis-cli ping >/dev/null

printf '==> Building frontend assets\n'
cd "$ROOT_DIR"
VITE_API_MODE=api VITE_API_BASE_URL="$UAT_API_BASE_URL" npm run build >/dev/null

printf '==> Compiling backend\n'
cd "$BACKEND_DIR"
mvn -DskipTests compile -q

printf '==> Starting backend on %s\n' "$BACKEND_PORT"
bash "$ROOT_DIR/scripts/release/start-backend.sh"

wait_for_backend_health

printf '==> Seeding default users when database is empty\n'
USER_COUNT="$(count_users | tr -d '[:space:]')"
if [[ "${USER_COUNT:-0}" == "0" ]]; then
  seed_user() {
    local username="$1"
    local full_name="$2"
    local email="$3"
    local role="$4"

    curl -fsS -X POST "$UAT_API_BASE_URL/api/auth/register" \
      -H 'Content-Type: application/json' \
      -d "$(cat <<EOF
{"username":"$username","password":"XiyuDemo!2026","email":"$email","fullName":"$full_name","role":"$role"}
EOF
)" >/dev/null
  }

  seed_user "xiaowang" "小王" "xiaowang@example.com" "STAFF"
  seed_user "zhangjingli" "张经理" "zhang.manager@example.com" "MANAGER"
  seed_user "lizong" "李总" "li.admin@example.com" "ADMIN"
  seed_user "ligong" "李工" "li.engineer@example.com" "STAFF"
fi

printf '==> Starting frontend preview on %s\n' "$FRONTEND_PORT"
cd "$ROOT_DIR"
nohup npm run preview -- --host 127.0.0.1 --port "$FRONTEND_PORT" > "$STATE_DIR/frontend.log" 2>&1 < /dev/null &
echo $! > "$STATE_DIR/frontend.pid"

printf '==> Waiting for frontend preview\n'
for i in {1..60}; do
  if curl -fsS "$UAT_WEB_BASE_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl -fsS "$UAT_WEB_BASE_URL" >/dev/null

printf '==> Rehearsal stack is ready\n'
printf 'API: %s\n' "$UAT_API_BASE_URL"
printf 'WEB: %s\n' "$UAT_WEB_BASE_URL"
