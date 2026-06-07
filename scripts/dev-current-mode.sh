#!/usr/bin/env bash
# Input: local dev service status and /api/system/runtime-mode response
# Output: concise runtime mode diagnostics for current frontend/backend stack
# Pos: scripts/ - local diagnostics and development helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:18080}"

echo "[dev-mode] service status"
bash "$ROOT_DIR/scripts/dev-services.sh" status || true
echo
echo "[dev-mode] runtime mode payload"
curl -fsS "$API_BASE_URL/api/system/runtime-mode" | sed 's/},{/},\n{/g'
echo
