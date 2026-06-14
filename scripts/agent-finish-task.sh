#!/usr/bin/env bash
# Input: current branch name (auto-detected) or explicit branch
# Output: switch to init branch, pull latest main, delete local task branch, clean up locks
# Pos: scripts/多 Agent 任务收尾
# 维护声明: 若工作区根目录、分支前缀变化，请同步更新 scripts/README.md。
set -euo pipefail

# ─── 颜色 ──────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}✓${NC} $*"; }
warn()  { echo -e "${YELLOW}⚠${NC} $*"; }
error() { echo -e "${RED}✗${NC} $*" >&2; }

# ─── 参数解析 ──────────────────────────────────────────────────────────────────
usage() {
  cat <<'USAGE'
Usage:
  scripts/agent-finish-task.sh [branch-name]

Options:
  branch-name    要清理的任务分支（默认：当前分支）

Examples:
  scripts/agent-finish-task.sh                    # 清理当前分支
  scripts/agent-finish-task.sh agent/mimo/fix-bug # 清理指定分支
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

# ─── 环境检测 ──────────────────────────────────────────────────────────────────
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
if [[ -z "$REPO_ROOT" ]]; then
  error "Not a git repository"
  exit 1
fi
cd "$REPO_ROOT"

CURRENT_BRANCH="$(git branch --show-current 2>/dev/null || true)"
TASK_BRANCH="${1:-$CURRENT_BRANCH}"

# 从分支名推断 agent 名和 init 分支
# 格式: agent/<name>/<task> → init 分支: agent/<name>-init
if [[ "$TASK_BRANCH" =~ ^agent/([^/]+)/([^/]+)$ ]]; then
  AGENT_NAME="${BASH_REMATCH[1]}"
  INIT_BRANCH="agent/${AGENT_NAME}-init"
elif [[ "$TASK_BRANCH" =~ ^agent/([^/]+)-init$ ]]; then
  error "Cannot finish an init branch: $TASK_BRANCH"
  exit 1
else
  error "Branch '$TASK_BRANCH' does not match pattern agent/<name>/<task>"
  error "Expected format: agent/<agent-name>/<task-slug>"
  exit 1
fi

TASK_SLUG="${BASH_REMATCH[2]}"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  🧹 Agent 任务收尾                                          ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo
echo "  分支: $TASK_BRANCH"
echo "  Agent: $AGENT_NAME"
echo "  Task: $TASK_SLUG"
echo "  Init: $INIT_BRANCH"
echo

# ─── Step 1: 检查未提交的变更 ─────────────────────────────────────────────────
echo "── Step 1: 检查未提交变更 ──"
if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
  warn "当前分支有未提交的变更："
  git status --short
  echo
  read -p "是否丢弃这些变更？(y/N) " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    git checkout -- .
    git clean -fd
    info "已丢弃未提交变更"
  else
    error "请先提交或丢弃变更后再执行收尾"
    exit 1
  fi
fi

# ─── Step 2: 检查远端 PR 状态 ─────────────────────────────────────────────────
echo
echo "── Step 2: 检查远端 PR 状态 ──"

# 检查分支是否已推送到远端
if git ls-remote --exit-code origin "refs/heads/$TASK_BRANCH" >/dev/null 2>&1; then
  info "分支已推送到远端"
else
  warn "分支未推送到远端，跳过 PR 状态检查"
fi

# ─── Step 3: 清理本地 agent-locks ─────────────────────────────────────────────
echo
echo "── Step 3: 清理 agent-locks ──"
LOCK_FILE=".agent-locks/${TASK_BRANCH//\//-}.yml"
if [[ -f "$LOCK_FILE" ]]; then
  info "删除锁文件: $LOCK_FILE"
  rm -f "$LOCK_FILE"
else
  info "无锁文件需要清理"
fi

# ─── Step 4: 切换到 init 分支并拉取最新 ───────────────────────────────────────
echo
echo "── Step 4: 切换到 $INIT_BRANCH 并拉取最新 ──"

# 确保 init 分支存在
if ! git rev-parse --verify --quiet "$INIT_BRANCH" >/dev/null 2>&1; then
  warn "init 分支 $INIT_BRANCH 不存在，从 origin/main 创建"
  git fetch origin
  git checkout -b "$INIT_BRANCH" origin/main
  info "已创建并切换到 $INIT_BRANCH"
else
  git checkout "$INIT_BRANCH"
  info "已切换到 $INIT_BRANCH"
fi

git pull origin main --rebase 2>/dev/null || git pull origin main
info "已拉取最新 main"

# ─── Step 5: 删除本地任务分支 ─────────────────────────────────────────────────
echo
echo "── Step 5: 删除本地任务分支 ──"

if git branch --list "$TASK_BRANCH" | grep -q "$TASK_BRANCH"; then
  git branch -D "$TASK_BRANCH"
  info "已删除本地分支: $TASK_BRANCH"
else
  info "本地分支 $TASK_BRANCH 不存在（可能已删除）"
fi

# ─── Step 6: 清理 worktree（如果存在）──────────────────────────────────────────
echo
echo "── Step 6: 清理 worktree ──"
WORKTREE_PATH="/Users/user/xiyu/worktrees/$(basename "$REPO_ROOT")"
if git worktree list | grep -q "$WORKTREE_PATH"; then
  info "Worktree 仍在使用，跳过清理"
else
  info "无 worktree 需要清理"
fi

# ─── 完成 ──────────────────────────────────────────────────────────────────────
echo
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅ 任务收尾完成                                             ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo
echo "  当前分支: $(git branch --show-current)"
echo "  最新提交: $(git log -1 --oneline)"
echo
echo "  下一步："
echo "    scripts/agent-start-task.sh $AGENT_NAME <new-task> origin/main --in-place"
echo
