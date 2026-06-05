#!/usr/bin/env bash
# Input: current Git checkout and optional ALLOW_BASE_BRANCH_COMMIT override
# Output: blocks commits from base branches or shared agent bootstrap worktrees
# Pos: scripts/多 Agent 工作区安全门禁
# 维护声明: 若新增默认 Agent 工作区或分支命名规则，请同步更新本文件和 scripts/README.md。
set -euo pipefail

if [[ "${ALLOW_BASE_BRANCH_COMMIT:-}" == "1" ]]; then
  echo "agent-worktree-guard: override enabled by ALLOW_BASE_BRANCH_COMMIT=1"
  exit 0
fi

ROOT_DIR="$(git rev-parse --show-toplevel)"
BRANCH_NAME="$(git symbolic-ref --quiet --short HEAD || true)"
WORKTREE_NAME="$(basename "$ROOT_DIR")"

if [[ -z "$BRANCH_NAME" ]]; then
  echo "agent-worktree-guard: detached HEAD is not allowed for normal commits." >&2
  echo "Set ALLOW_BASE_BRANCH_COMMIT=1 only for intentional maintenance operations." >&2
  exit 1
fi

case "$BRANCH_NAME" in
  main|master|agent/*-init|integrate/baseline)
    echo "agent-worktree-guard: refusing commit on protected branch '$BRANCH_NAME'." >&2
    echo "Create an isolated task worktree with scripts/agent-start-task.sh first." >&2
    exit 1
    ;;
esac

case "$WORKTREE_NAME" in
  codex|claude|gemini|integrator|cursor|qoder)
    # Bootstrap worktrees allow task branches ($AGENT/*) to commit.
    # Only agent/*-init (anchor) branches are blocked here.
    case "$BRANCH_NAME" in
      agent/*-init)
        echo "agent-worktree-guard: refusing commit on anchor branch '$BRANCH_NAME' in bootstrap worktree." >&2
        exit 1
        ;;
    esac
    ;;
esac

# Block task worktrees without .agent-task-context (but not bootstrap worktrees)
if [[ "$ROOT_DIR" == /Users/user/xiyu/worktrees/* ]]; then
  # Skip bootstrap worktrees: they have no hyphen separator in their name
  case "$WORKTREE_NAME" in
    *-*)
      # Task worktree: must have .agent-task-context
      if [[ ! -f "$ROOT_DIR/.agent-task-context" ]]; then
        echo "agent-worktree-guard: missing .agent-task-context in task worktree." >&2
        echo "Create task worktrees with scripts/agent-start-task.sh so branch and directory are recorded." >&2
        exit 1
      fi
      ;;
    *)
      # Bootstrap worktree: skip context check (in-place mode has no .agent-task-context)
      ;;
  esac
fi

if [[ -f "$ROOT_DIR/package.json" && -f "$ROOT_DIR/scripts/check-agent-locks.mjs" ]]; then
  npm run agent:lock-check:changed
fi

echo "agent-worktree-guard: ok ($WORKTREE_NAME on $BRANCH_NAME)"
