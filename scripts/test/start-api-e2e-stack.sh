#!/usr/bin/env bash
# Input: E2E environment defaults, local pid/state files, listener ports, and current health state
# Output: a ready mock-AI API-backed E2E stack or a single actionable startup/port-cleanup failure
# Pos: scripts/test/ - Playwright and API-backed test baseline helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Provide test-only placeholder values for secrets that rehearsal-env.sh
# now requires via ${VAR:?...}. The e2e stack uses an in-memory backend
# (`--spring.flyway.enabled=false --ai.provider=mock`) so these placeholders
# are never used to access real systems; CI may still override any of them.
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-e2e-test-mysql-root}"
export PLATFORM_ENCRYPTION_KEY="${PLATFORM_ENCRYPTION_KEY:-e2e-test-platform-encryption-key-32}"
export UAT_TEST_PASSWORD="${UAT_TEST_PASSWORD:-e2e-test-uat-pass}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-e2e-test-admin-pass}"
export DB_PASSWORD="${DB_PASSWORD:-e2e-test-db-pass}"
export JWT_SECRET="${JWT_SECRET:-e2e-test-jwt-secret-for-hs512-needs-64-chars-minimum-padding-!!x}"

source "$ROOT_DIR/scripts/release/rehearsal-env.sh"

MARKER_FILE="$STATE_DIR/playwright-api-stack.started"
BACKEND_PID_FILE="$STATE_DIR/backend.pid"
FRONTEND_PID_FILE="$STATE_DIR/frontend.pid"
BACKEND_LOG_FILE="$STATE_DIR/backend.log"
FRONTEND_LOG_FILE="$STATE_DIR/frontend.log"

is_http_ready() {
  local url="$1"
  curl -fsS "$url" >/dev/null 2>&1
}

is_pid_alive() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" >/dev/null 2>&1
}

is_port_listening() {
  local port="$1"
  lsof -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

is_managed_stack_ready() {
  [[ -f "$MARKER_FILE" ]] &&
    is_pid_alive "$BACKEND_PID_FILE" &&
    is_pid_alive "$FRONTEND_PID_FILE" &&
    is_http_ready "$UAT_API_BASE_URL/actuator/health" &&
    is_http_ready "$UAT_WEB_BASE_URL"
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

require_port_clear() {
  local port="$1"
  local name="$2"

  for _ in {1..10}; do
    if ! is_port_listening "$port"; then
      return 0
    fi
    sleep 1
  done

  # 端口仍被占用，给出精确的操作引导
  # 工程背景(2026-05-21): 本地 agent dev 服务占用端口导致 Playwright 无法启动，
  # 开发者只能反复推 CI 试错。详见 .wiki/pages/lessons-learned.md §三
  local occupying_process
  occupying_process="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | tail -n +2 | awk '{print $1, $2}' | head -1 || true)"

  printf '\n❌ %s 端口 %s 被占用，无法启动 E2E 测试栈。\n' "$name" "$port" >&2
  if [[ -n "$occupying_process" ]]; then
    printf '   占用进程: %s\n' "$occupying_process" >&2
  fi
  printf '\n💡 如果您在本地运行 agent dev 服务（npm run agent:up），请先执行：\n' >&2
  printf '     export XIYU_DEV_CONFIRMED=1 && npm run agent:stop\n' >&2
  printf '   然后再运行：\n' >&2
  printf '     npx playwright test\n' >&2
  printf '   测试完成后，恢复开发环境：\n' >&2
  printf '     export XIYU_DEV_CONFIRMED=1 && npm run agent:up\n\n' >&2
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >&2 || true
  return 1
}

wait_for_health() {
  local url="$1"
  local pid_file="$2"
  local log_file="$3"
  local name="$4"

  for _ in {1..90}; do
    if [[ -n "$pid_file" ]] && [[ -f "$pid_file" ]] && ! is_pid_alive "$pid_file"; then
      printf '%s failed before becoming healthy.\n' "$name" >&2
      if [[ -f "$log_file" ]]; then
        printf -- '--- %s log tail ---\n' "$name" >&2
        tail -n 80 "$log_file" >&2 || true
      fi
      return 1
    fi

    if is_http_ready "$url"; then
      return 0
    fi

    sleep 2
  done

  printf '%s did not become healthy in time.\n' "$name" >&2
  if [[ -f "$log_file" ]]; then
    printf -- '--- %s log tail ---\n' "$name" >&2
    tail -n 80 "$log_file" >&2 || true
  fi
  return 1
}

wait_for_backend_ready() {
  local url="$1"
  local pid_file="$2"
  local log_file="$3"

  for _ in {1..150}; do
    if [[ -f "$pid_file" ]] && ! is_pid_alive "$pid_file"; then
      printf 'Backend failed before becoming healthy.\n' >&2
      if [[ -f "$log_file" ]]; then
        printf -- '--- Backend log tail ---\n' >&2
        tail -n 80 "$log_file" >&2 || true
      fi
      return 1
    fi

    if [[ -f "$log_file" ]] && grep -q "Tomcat started on port $BACKEND_PORT" "$log_file" && is_http_ready "$url"; then
      return 0
    fi

    sleep 2
  done

  printf 'Backend did not become healthy in time.\n' >&2
  if [[ -f "$log_file" ]]; then
    printf -- '--- Backend log tail ---\n' >&2
    tail -n 80 "$log_file" >&2 || true
  fi
  return 1
}

seed_default_user() {
  local username="$1"
  local full_name="$2"
  local email="$3"
  local role="$4"

  curl -fsS -X POST "$UAT_API_BASE_URL/api/auth/register" \
    -H 'Content-Type: application/json' \
    -d "$(cat <<EOF
{"username":"$username","password":"XiyuDemo!2026","email":"$email","fullName":"$full_name","role":"$role"}
EOF
)" >/dev/null 2>&1 || true
}

if is_managed_stack_ready; then
  printf 'Playwright API stack already healthy at %s and %s\n' "$UAT_API_BASE_URL" "$UAT_WEB_BASE_URL"
  exit 0
fi

printf 'Preparing lightweight Playwright API-backed E2E stack\n'
rm -f "$MARKER_FILE"
cleanup_port_listener "$BACKEND_PORT"
cleanup_port_listener "$FRONTEND_PORT"
require_port_clear "$BACKEND_PORT" "Backend"
require_port_clear "$FRONTEND_PORT" "Frontend"
rm -f "$BACKEND_PID_FILE" "$FRONTEND_PID_FILE"

if is_http_ready "$UAT_API_BASE_URL/actuator/health"; then
  printf 'Backend health is still reachable after cleanup; refusing to reuse a non-managed service on %s.\n' "$UAT_API_BASE_URL" >&2
  exit 1
fi

if ! is_http_ready "$UAT_API_BASE_URL/actuator/health"; then
  printf '==> Building frontend assets for API mode\n'
  cd "$ROOT_DIR"
  VITE_API_BASE_URL="$UAT_API_BASE_URL" npm run build:api >/dev/null

  printf '==> Starting backend on %s with e2e profile\n' "$BACKEND_PORT"
  cd "$BACKEND_DIR"
  nohup env \
  SPRING_PROFILES_ACTIVE="e2e" \
  AI_PROVIDER="mock" \
  OPENAI_API_KEY="" \
  DEEPSEEK_API_KEY="" \
  DASHSCOPE_API_KEY="" \
  QWEN_API_KEY="" \
  ARK_API_KEY="" \
  DOUBAO_API_KEY="" \
  VOLCENGINE_API_KEY="" \
  JWT_SECRET="$JWT_SECRET" \
  CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS" \
  mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT} --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=update --management.health.redis.enabled=false --ai.provider=mock" \
    > "$BACKEND_LOG_FILE" 2>&1 < /dev/null &
  echo $! > "$BACKEND_PID_FILE"

  printf '==> Waiting for backend health\n'
  wait_for_backend_ready "$UAT_API_BASE_URL/actuator/health" "$BACKEND_PID_FILE" "$BACKEND_LOG_FILE"

  printf '==> Seeding default Playwright users\n'
  seed_default_user "xiaowang" "小王" "xiaowang@example.com" "STAFF"
  seed_default_user "zhangjingli" "张经理" "zhang.manager@example.com" "MANAGER"
  seed_default_user "lizong" "李总" "li.admin@example.com" "ADMIN"
  seed_default_user "ligong" "李工" "li.engineer@example.com" "STAFF"
fi

if ! is_http_ready "$UAT_WEB_BASE_URL"; then
  printf '==> Starting frontend preview on %s\n' "$FRONTEND_PORT"
  cd "$ROOT_DIR"
  nohup npm run preview -- --host 127.0.0.1 --port "$FRONTEND_PORT" > "$FRONTEND_LOG_FILE" 2>&1 < /dev/null &
  echo $! > "$FRONTEND_PID_FILE"

  printf '==> Waiting for frontend preview\n'
  wait_for_health "$UAT_WEB_BASE_URL" "$FRONTEND_PID_FILE" "$FRONTEND_LOG_FILE" "Frontend"
fi

touch "$MARKER_FILE"
printf 'Playwright API-backed E2E stack ready.\n'
