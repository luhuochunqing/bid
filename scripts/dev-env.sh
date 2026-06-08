#!/usr/bin/env bash
# Input: current working directory inside a main checkout or agent worktree
# Output: exported frontend, backend, database, and Redis isolation variables
# Pos: scripts/多 Agent 本地环境识别脚本
# 维护声明: 仅维护本地协作环境端口与资源映射；调整 worktree 分配时请同步 SOP 与脚本目录说明。
# Do not use set -e because this is meant to be sourced

# === System-level git safety wrapper ===
# Prepend scripts/ to PATH so our 'git' wrapper (which blocks --no-verify)
# takes precedence over the system git.
# This is the primary enforcement mechanism against bypassing pre-commit /
# pre-push gates (locks, E2E selectors, token coverage, blueprint gaps, etc.).
# Resolve this file's directory in both bash and zsh when sourced.
if [[ -n "${BASH_SOURCE[0]:-}" ]]; then
  _XIYU_DEV_ENV_SOURCE="${BASH_SOURCE[0]}"
elif [[ -n "${ZSH_VERSION:-}" ]]; then
  _XIYU_DEV_ENV_SOURCE="${(%):-%N}"
else
  _XIYU_DEV_ENV_SOURCE="$0"
fi
SCRIPT_DIR="$(cd "$(dirname "$_XIYU_DEV_ENV_SOURCE")" && pwd)"
unset _XIYU_DEV_ENV_SOURCE

# Prepend only if not already present (avoid endless duplication on re-source)
case ":$PATH:" in
  *":$SCRIPT_DIR:"*) : ;;
  *) export PATH="$SCRIPT_DIR:$PATH" ;;
esac

# Refresh command lookup cache so shells like zsh stop resolving the old /usr/bin/git
# after PATH has been prepended during `source scripts/dev-env.sh`.
if [[ -n "${ZSH_VERSION:-}" ]]; then
  rehash 2>/dev/null || true
else
  hash -r 2>/dev/null || true
fi

# Guard repeated noisy output + set marker for other scripts
if [[ -z "${XIYU_DEV_ENV_SOURCED:-}" ]]; then
  export XIYU_DEV_ENV_SOURCED=1
fi

CURRENT_DIR="$(pwd)"

if [[ "$CURRENT_DIR" == *"worktrees/claude"* ]]; then
  export FRONTEND_PORT=1315
  export BACKEND_PORT=18081
  export SIDECAR_PORT=8001
  export DB_NAME="xiyu_bid_claude"
  export REDIS_DB=1
elif [[ "$CURRENT_DIR" == *"worktrees/codex"* ]]; then
  export FRONTEND_PORT=1316
  export BACKEND_PORT=18082
  export SIDECAR_PORT=8002
  export DB_NAME="xiyu_bid_codex"
  export REDIS_DB=2
elif [[ "$CURRENT_DIR" == *"worktrees/gemini"* ]]; then
  export FRONTEND_PORT=1317
  export BACKEND_PORT=18083
  export SIDECAR_PORT=8003
  export DB_NAME="xiyu_bid_gemini"
  export REDIS_DB=3
elif [[ "$CURRENT_DIR" == *"worktrees/cursor"* ]]; then
  export FRONTEND_PORT=1318
  export BACKEND_PORT=18084
  export SIDECAR_PORT=8004
  export DB_NAME="xiyu_bid_cursor"
  export REDIS_DB=4
elif [[ "$CURRENT_DIR" == *"worktrees/integrator"* ]]; then
  export FRONTEND_PORT=1319
  export BACKEND_PORT=18085
  export SIDECAR_PORT=8005
  export DB_NAME="xiyu_bid_integrator"
  export REDIS_DB=5
elif [[ "$CURRENT_DIR" == *"worktrees/qoder"* ]]; then
  export FRONTEND_PORT=1320
  export BACKEND_PORT=18086
  export SIDECAR_PORT=8006
  export DB_NAME="xiyu_bid_qoder"
  export REDIS_DB=6
elif [[ "$CURRENT_DIR" == *"worktrees/trae"* ]]; then
  export FRONTEND_PORT=1321
  export BACKEND_PORT=18087
  export SIDECAR_PORT=8007
  export DB_NAME="xiyu_bid_trae"
  export REDIS_DB=7
else
  # Default for main project root (/Users/user/xiyu/xiyu-bid-poc/)
  export FRONTEND_PORT=1314
  export BACKEND_PORT=18080
  export SIDECAR_PORT=8000
  export DB_NAME="xiyu_bid_main"
  export REDIS_DB=0
fi

echo "Environment detected: $(basename "$CURRENT_DIR")"
echo "Frontend Port: $FRONTEND_PORT"
echo "Backend Port: $BACKEND_PORT"
echo "Sidecar Port: $SIDECAR_PORT"
echo "DB Name: $DB_NAME"
echo "Redis DB: $REDIS_DB"

# --- Verify git wrapper is active (system-level --no-verify prohibition) ---
GIT_PATH="$(command -v git 2>/dev/null || echo '')"
if [[ "$GIT_PATH" == *"/scripts/git" ]]; then
  : # good
else
  echo "⚠️  WARNING: 'git' resolves to '$GIT_PATH' instead of project scripts/git wrapper."
  echo "    --no-verify prohibition may not be active. Re-source or check PATH."
fi
