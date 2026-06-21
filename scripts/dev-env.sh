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

# ─────────────────────────────────────────────────────────────────────────────
# 开发环境统一到主工作区（trae）
# ─────────────────────────────────────────────────────────────────────────────
# 自 2026-06-21 起，所有开发环境（前端/后端/sidecar/数据库/Redis）统一在主工作区
# /Users/user/xiyu/worktrees/trae 启动。其他 worktree 不再分配独立端口和数据库，
# 也不允许启动开发环境（start-frontend.sh / start-backend.sh / dev-services.sh
# 均有守卫拒绝执行）。
#
# 历史背景：此前每个 agent worktree 都有独立端口（1315~1323）和数据库
# （xiyu_bid_claude/codex/...），导致资源浪费、launchd 守护进程繁多、数据库膨胀。
# 现在统一到主工作区，其他 worktree 仅用于代码编辑和 git 操作。
# ─────────────────────────────────────────────────────────────────────────────

XIYU_MAIN_WORKTREE="/Users/user/xiyu/worktrees/trae"

if [[ "$CURRENT_DIR" == *"worktrees/trae"* ]]; then
  # 主工作区（trae）：唯一允许启动开发环境的工作区
  export FRONTEND_PORT=1323
  export BACKEND_PORT=18089
  export SIDECAR_PORT=8009
  export DB_NAME="xiyu_bid_main"
  export REDIS_DB=0
  export XIYU_IS_MAIN_WORKTREE=1
else
  # 非主工作区：不设置开发环境变量，start-*.sh / dev-services.sh 会拒绝执行
  export XIYU_IS_MAIN_WORKTREE=0
  echo "⚠️  当前目录 $(basename "$CURRENT_DIR") 不是主工作区（trae）。"
  echo "    开发环境已统一到主工作区：$XIYU_MAIN_WORKTREE"
  echo "    其他 worktree 不再分配独立端口/数据库，也不允许启动开发环境。"
  echo "    如需启动开发环境，请：cd $XIYU_MAIN_WORKTREE"
fi

echo "Environment detected: $(basename "$CURRENT_DIR")"
if [[ "${XIYU_IS_MAIN_WORKTREE:-0}" == "1" ]]; then
  echo "Frontend Port: $FRONTEND_PORT"
  echo "Backend Port: $BACKEND_PORT"
  echo "Sidecar Port: $SIDECAR_PORT"
  echo "DB Name: $DB_NAME"
  echo "Redis DB: $REDIS_DB"
fi

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
