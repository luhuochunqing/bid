#!/usr/bin/env bash
# Input: detected agent worktree environment from scripts/dev-env.sh
# Output: starts the frontend dev server on the assigned isolated port
# Pos: scripts/多 Agent 前端启动脚本
# 维护声明: 仅维护本地前端启动端口注入；端口分配或真实 API 启动口径变化时请同步协作 SOP。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/dev-env.sh"

# Pin VITE_API_BASE_URL to the worktree's own backend so non-main worktrees
# don't fall back to .env.api's 18080 (其它 worktree 的后端，CORS 只放行 1314)。
export VITE_API_MODE="${VITE_API_MODE:-api}"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"

echo "Starting frontend on port $FRONTEND_PORT (API -> $VITE_API_BASE_URL)..."
# vite respects --port
npm run dev -- --port "$FRONTEND_PORT"
