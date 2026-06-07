#!/usr/bin/env bash
# Input: optional dev-services command arguments; defaults to stable local service start
# Output: delegates legacy one-command startup to the identity-checked dev service manager
# Pos: root - compatibility entrypoint for local real-API development
# 一旦我被更新，务必更新 README.md 中的启动说明。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ $# -eq 0 ]]; then
  set -- start
fi

ORIGINAL_BACKEND_PORT="${BACKEND_PORT:-}"
ORIGINAL_FRONTEND_PORT="${FRONTEND_PORT:-}"
ORIGINAL_SIDECAR_PORT="${SIDECAR_PORT:-}"
ORIGINAL_DB_NAME="${DB_NAME:-}"
ORIGINAL_REDIS_DB="${REDIS_DB:-}"

if [[ -f "$ROOT_DIR/scripts/dev-env.sh" ]]; then
  cd "$ROOT_DIR"
  # Auto-detect main checkout vs. agent worktree isolation before delegating.
  # Explicit caller-provided values still win for ad-hoc local overrides.
  source "$ROOT_DIR/scripts/dev-env.sh"
  [[ -n "$ORIGINAL_BACKEND_PORT" ]] && export BACKEND_PORT="$ORIGINAL_BACKEND_PORT"
  [[ -n "$ORIGINAL_FRONTEND_PORT" ]] && export FRONTEND_PORT="$ORIGINAL_FRONTEND_PORT"
  [[ -n "$ORIGINAL_SIDECAR_PORT" ]] && export SIDECAR_PORT="$ORIGINAL_SIDECAR_PORT"
  [[ -n "$ORIGINAL_DB_NAME" ]] && export DB_NAME="$ORIGINAL_DB_NAME"
  [[ -n "$ORIGINAL_REDIS_DB" ]] && export REDIS_DB="$ORIGINAL_REDIS_DB"
  export SPRING_DATA_REDIS_DATABASE="${SPRING_DATA_REDIS_DATABASE:-$REDIS_DB}"
fi

echo "[dev] start.sh delegates to scripts/dev-services.sh $*"
exec "$ROOT_DIR/scripts/dev-services.sh" "$@"
