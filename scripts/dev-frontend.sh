#!/usr/bin/env bash
# Input: optional Vite CLI arguments and local API endpoint environment
# Output: starts the frontend dev server in the single supported real-API mode
# Pos: scripts/ - frontend dev bootstrap entrypoint
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-18080}"

export VITE_API_MODE="api"
export VITE_ENABLE_MOCK_LOGIN="false"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://127.0.0.1:${BACKEND_PORT}}"

if [[ -x "$ROOT_DIR/node_modules/.bin/vite" ]]; then
  exec "$ROOT_DIR/node_modules/.bin/vite" "$@"
fi

PATH="$ROOT_DIR/node_modules/.bin:$PATH" exec vite "$@"
