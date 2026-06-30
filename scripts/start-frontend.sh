#!/usr/bin/env bash
# Input: detected agent worktree environment from scripts/dev-env.sh
# Output: starts the frontend dev server on the assigned isolated port
# Pos: scripts/多 Agent 前端启动脚本
# 维护声明: 仅维护本地前端启动端口注入；端口分配或真实 API 启动口径变化时请同步协作 SOP。
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

# Pin VITE_API_BASE_URL to 127.0.0.1（与 dev-services.sh start_frontend 保持一致）.
# 必须与前端 dev server 的 --host 127.0.0.1 同 host:
#   1. vite --host 127.0.0.1 只监听 IPv4, 避免 localhost 解析到 IPv6 (::1)
#   2. 浏览器访问 http://127.0.0.1:1323, axios 调用 http://127.0.0.1:18089,
#      前端页面 host 与 API host 同为 127.0.0.1, HttpOnly cookie 可正常发送.
#   3. 若用 localhost, 浏览器可能解析到 IPv6, 而后端返回的 Set-Cookie host=localhost
#      在 IPv4 访问时不会被回传, 导致登录后所有 API 请求都是 anonymous -> 403.
export VITE_API_MODE="${VITE_API_MODE:-api}"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"

echo "Starting frontend on port $FRONTEND_PORT (API -> $VITE_API_BASE_URL)..."
# --host 127.0.0.1 与 dev-services.sh start_frontend 一致, 避免 IPv6 cookie 跨 host 问题
npm run dev -- --host 127.0.0.1 --port "$FRONTEND_PORT"
