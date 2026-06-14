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
  export DB_HOST="127.0.0.1"
  export DB_PORT=13306
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
else
  # Default for main project root (/Users/user/xiyu/xiyu-bid-poc/)
  export FRONTEND_PORT=1314
  export BACKEND_PORT=18080
  export SIDECAR_PORT=8000
  export DB_NAME="xiyu_bid_main"
  export REDIS_DB=0
fi

# Apply branch-based database suffix only for agent worktrees.
# Main checkout always uses the base DB_NAME without suffix.
IS_WORKTREE=""
[[ "$CURRENT_DIR" == *"/worktrees/"* ]] && IS_WORKTREE=1

# Apply branch-based database suffix if in a git repo and not on protected/anchor branches
if [[ -n "$IS_WORKTREE" ]] && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  CURRENT_BRANCH="$(git symbolic-ref --short HEAD 2>/dev/null || echo "")"
  if [[ -n "$CURRENT_BRANCH" ]]; then
    case "$CURRENT_BRANCH" in
      main|master|integrate/baseline|*-init)
        # Skip suffixing for protected/anchor branches
        ;;
      *)
        # Extract last part or strip common prefixes: agent/<name>/
        # e.g., agent/gemini/feat-login -> feat-login
        CLEAN_BRANCH="$(echo "$CURRENT_BRANCH" | sed -E 's/^agent\/[a-zA-Z0-9_-]+\///')"
        # Lowercase, convert '/' and '-' to '_', remove non-safe characters
        CLEAN_BRANCH="$(echo "$CLEAN_BRANCH" | tr '[:upper:]' '[:lower:]' | tr '/-' '_' | tr -cd 'a-z0-9_')"
        # Limit suffix to 30 characters
        CLEAN_BRANCH="${CLEAN_BRANCH:0:30}"
        # Remove leading/trailing underscores
        CLEAN_BRANCH="$(echo "$CLEAN_BRANCH" | sed -e 's/^_*//' -e 's/_*$//')"
        if [[ -n "$CLEAN_BRANCH" ]]; then
          export DB_NAME="${DB_NAME}_${CLEAN_BRANCH}"
        fi
        ;;
    esac
  fi
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

# ── Session GATE：锚点分支阻断 + worktree 互斥 ──
if [[ -z "${CHAT_ONLY:-}" || "${CHAT_ONLY:-}" != "1" ]]; then
  source "$SCRIPT_DIR/session-gate.sh"
fi
