#!/usr/bin/env bash
# Input: detected agent worktree environment from scripts/dev-env.sh
# Output: starts the backend service on the assigned isolated port and database
# Pos: scripts/多 Agent 后端启动脚本
# 维护声明: 仅维护本地后端启动环境注入；端口、数据库或 Redis 口径变化时请同步协作 SOP。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev-env.sh"

# 主工作区守卫：只有 trae 工作区允许启动开发环境
if [[ "${XIYU_IS_MAIN_WORKTREE:-0}" != "1" ]]; then
  echo "❌ 拒绝启动：当前工作区不是主工作区（trae）。"
  echo "   开发环境已统一到主工作区：/Users/user/xiyu/worktrees/trae"
  echo "   请切换到主工作区后重试：cd /Users/user/xiyu/worktrees/trae"
  exit 1
fi

DEFAULT_REDIS_PORT="6379"
FALLBACK_REDIS_PORT="16379"

is_port_listening() {
  local port="$1"
  lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
}

resolve_redis_port() {
  if [[ -n "${REDIS_PORT:-}" ]]; then
    return 0
  fi

  if is_port_listening "$DEFAULT_REDIS_PORT"; then
    export REDIS_PORT="$DEFAULT_REDIS_PORT"
  elif is_port_listening "$FALLBACK_REDIS_PORT"; then
    export REDIS_PORT="$FALLBACK_REDIS_PORT"
  else
    export REDIS_PORT="$DEFAULT_REDIS_PORT"
  fi
}

resolve_redis_port

export SERVER_PORT="$BACKEND_PORT"
# Map DB_NAME and REDIS_DB to SPRING_DATASOURCE_URL and SPRING_DATA_REDIS_DATABASE
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/${DB_NAME}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export SPRING_DATA_REDIS_DATABASE="$REDIS_DB"

# Also set CORS allowed origins to include the dynamically assigned frontend port
export CORS_ALLOWED_ORIGINS="http://localhost:${FRONTEND_PORT},http://127.0.0.1:${FRONTEND_PORT}"

echo "Starting backend on port $SERVER_PORT (DB: $DB_NAME, Redis: localhost:$REDIS_PORT/$REDIS_DB)..."
# Call the existing start script in the backend directory
cd "$SCRIPT_DIR/../backend"
./start.sh
