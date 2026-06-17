#!/usr/bin/env bash
# Input: task branch (auto-detected), optional flags
# Output: safety check, then cleanup of local branch + remote branch + locks + optional worktree
# Pos: scripts/多 Agent 任务收尾 — 安全清理开发分支
# 维护声明: 若工作区根目录、分支前缀变化，请同步更新 scripts/README.md。
set -euo pipefail

# ─── 颜色 ──────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${GREEN}✓${NC} $*"; }
warn()  { echo -e "${YELLOW}⚠${NC} $*"; }
error() { echo -e "${RED}✗${NC} $*" >&2; }
hdr()   { echo -e "${CYAN}── $* ──${NC}"; }

# ─── 参数解析 ──────────────────────────────────────────────────────────────────
DRY_RUN=0
FORCE=0
AUTO_CONFIRM=0
INCLUDE_REMOTE=0

usage() {
  cat <<'USAGE'
Usage:
  scripts/agent-finish-task.sh [branch-name] [options]

Options:
  branch-name    要清理的任务分支（默认：当前分支）
  --dry-run      仅检查，不实际执行任何删除操作
  --force        跳过合入检查，强制清理（不推荐，仅在确定代码已合入但检测失败时使用）
  --include-remote  同时清理远端分支
  --yes          跳过确认提示，自动执行
  -h, --help     显示帮助信息

Examples:
  scripts/agent-finish-task.sh                           # 清理当前分支（仅本地）
  scripts/agent-finish-task.sh --include-remote          # 清理当前分支（含远端）
  scripts/agent-finish-task.sh agent/mimo/fix-bug        # 清理指定分支
  scripts/agent-finish-task.sh agent/mimo/fix-bug --dry-run  # 仅检查，不删除
  scripts/agent-finish-task.sh --force                   # 强制清理（跳过合入检查）
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

POSITIONAL_ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)       DRY_RUN=1;        shift ;;
    --force)         FORCE=1;           shift ;;
    --include-remote) INCLUDE_REMOTE=1; shift ;;
    --yes)           AUTO_CONFIRM=1;    shift ;;
    *)               POSITIONAL_ARGS+=("$1"); shift ;;
  esac
done

# ─── Git 仓库检测 ──────────────────────────────────────────────────────────────
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null)"
if [[ -z "$REPO_ROOT" ]]; then
  error "Not a git repository"
  exit 1
fi
cd "$REPO_ROOT"

CURRENT_BRANCH="$(git branch --show-current 2>/dev/null || true)"
TASK_BRANCH="${POSITIONAL_ARGS[0]:-$CURRENT_BRANCH}"

# 检查是否是受保护分支
PROTECTED_BRANCHES=("main" "master")
for pb in "${PROTECTED_BRANCHES[@]}"; do
  if [[ "$TASK_BRANCH" == "$pb" ]]; then
    error "Cannot finish a protected branch: $TASK_BRANCH"
    exit 1
  fi
done

# 从分支名解析 agent 信息
# 格式: agent/<name>/<task> 或 agent/<name>-init
if [[ "$TASK_BRANCH" =~ ^agent/([^/]+)/([^/]+)$ ]]; then
  AGENT_NAME="${BASH_REMATCH[1]}"
  TASK_NAME="${BASH_REMATCH[2]}"
  INIT_BRANCH="agent/${AGENT_NAME}-init"
  BRANCH_TYPE="task"
elif [[ "$TASK_BRANCH" =~ ^agent/([^/]+)-init$ ]]; then
  error "Cannot finish an init branch: $TASK_BRANCH"
  error "Init branches are anchor branches and must not be deleted."
  exit 1
else
  warn "Branch '$TASK_BRANCH' does not match pattern agent/<name>/<task>"
  AGENT_NAME=""
  TASK_NAME=""
  INIT_BRANCH="main"
  BRANCH_TYPE="unknown"
fi

# ─── Banner ─────────────────────────────────────────────────────────────────────
echo
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  🧹 Agent 任务收尾 — 安全清理开发分支                      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo
echo "  分支:        $TASK_BRANCH"
[[ -n "$AGENT_NAME" ]] && echo "  Agent:       $AGENT_NAME"
[[ -n "$TASK_NAME" ]] && echo "  Task:        $TASK_NAME"
echo "  Init 分支:   $INIT_BRANCH"
[[ "$DRY_RUN" == "1" ]] && echo "  模式:        🔍 DRY RUN（仅检查，不执行删除）"
echo

# ─── Step 1: 检查未提交的变更 ──────────────────────────────────────────────────
hdr "Step 1: 检查未提交变更"

if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
  warn "当前工作区有未提交的变更："
  git status --short
  echo
  if [[ "$DRY_RUN" == "1" ]]; then
    warn "[DRY RUN] 将提示丢弃未提交变更"
  elif [[ "$AUTO_CONFIRM" == "1" ]]; then
    git checkout -- .
    git clean -fd
    info "已丢弃未提交变更"
  else
    warn "请先提交或 stash 变更后再执行收尾。"
    warn "如需强制丢弃，请使用 --yes 参数。"
    exit 1
  fi
else
  info "工作区干净，无未提交变更"
fi

# ─── Step 2: 确认代码已合入 main（三重检查） ────────────────────────────────────
hdr "Step 2: 确认代码已合入 main"

git fetch origin main 2>/dev/null && info "已拉取最新 origin/main" || warn "无法拉取 origin/main"

MERGED_INTO_MAIN=0
MERGE_CHECK_DETAILS=""

# 检查 A: git branch -r --merged（最可靠，远端已合入会出现在这里）
if git branch -r --merged origin/main 2>/dev/null | grep -qwE "origin/${TASK_BRANCH}$"; then
  MERGED_INTO_MAIN=1
  MERGE_CHECK_DETAILS="远端分支已出现在 origin/main 已合入列表"
fi

# 检查 B: git merge-base --is-ancestor（适用于本地已 rebase 到 main 的分支）
if [[ "$MERGED_INTO_MAIN" == "0" ]] && git rev-parse --verify --quiet "$TASK_BRANCH" >/dev/null 2>&1; then
  # 先检查是否有 unique commits（用 cherry 判断）
  CHERRY_OUTPUT=$(git cherry origin/main "$TASK_BRANCH" 2>/dev/null)
  if [[ -z "$CHERRY_OUTPUT" ]]; then
    MERGED_INTO_MAIN=1
    MERGE_CHECK_DETAILS="git cherry 确认分支无独特提交，已完全合入 main"
  fi
fi

# 检查 C: 通过 Gitee API 查询 PR 状态
if [[ "$MERGED_INTO_MAIN" == "0" ]] && [[ -n "${GITEE_TOKEN:-}" ]]; then
  PR_LIST=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" \
    "https://gitee.com/api/v5/repos/${GITEE_OWNER:-allinai888}/${GITEE_REPO:-bid}/pulls?state=all&head=${TASK_BRANCH//\//%2F}&base=${GITEE_BASE_BRANCH:-main}" 2>/dev/null)
  PR_COUNT=$(echo "$PR_LIST" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")

  if [[ "$PR_COUNT" != "0" ]]; then
    PR_NUM=$(echo "$PR_LIST" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0].get('number',''))" 2>/dev/null)
    PR_STATE=$(echo "$PR_LIST" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0].get('state',''))" 2>/dev/null)
    PR_MERGED=$(echo "$PR_LIST" | python3 -c "import sys,json; data=json.load(sys.stdin); print(1 if data[0].get('merged') else 0)" 2>/dev/null)
    echo "  PR #$PR_NUM — 状态: $PR_STATE, 已合并: $([ "$PR_MERGED" == "1" ] && echo '是' || echo '否')"
    if [[ "$PR_MERGED" == "1" ]]; then
      MERGED_INTO_MAIN=1
      MERGE_CHECK_DETAILS="Gitee API 确认 PR #$PR_NUM 已合并"
    fi
  else
    warn "未找到 $TASK_BRANCH 的 PR（可能从未推送或已关闭无 PR）"
  fi
fi

# 最终判定
if [[ "$MERGED_INTO_MAIN" == "1" ]]; then
  info "✅ 分支已合入 main — 安全可清理 ($MERGE_CHECK_DETAILS)"
elif [[ "$FORCE" == "1" ]]; then
  warn "⚠️ 强制模式已启用，跳过合入检查！"
  warn "   请确保已手动确认代码已合入 main。"
else
  error "⛔ 分支 $TASK_BRANCH 尚未合入 main！"
  error "   请先提交 PR 并合入 main，再执行收尾。"
  error "   如需强制清理，使用 --force 参数。"
  exit 1
fi

# ─── Step 3: 列出待清理资源 ────────────────────────────────────────────────────
hdr "Step 3: 列出待清理资源"

CLEANUP_LIST=()

# 本地分支
if git branch --list "$TASK_BRANCH" | grep -q .; then
  CLEANUP_LIST+=("本地分支: $TASK_BRANCH")
fi

# 远端分支
REMOTE_BRANCH_EXISTS=0
if git ls-remote --exit-code origin "refs/heads/$TASK_BRANCH" >/dev/null 2>&1; then
  REMOTE_BRANCH_EXISTS=1
  CLEANUP_LIST+=("远端分支: origin/$TASK_BRANCH")
fi

# agent-locks 文件
LOCK_FILES=()
if [[ -n "$AGENT_NAME" && -n "$TASK_NAME" ]]; then
  LOCK_NAMESPACE="agent-${AGENT_NAME}-${TASK_NAME}"
  if [[ -d ".agent-locks" ]]; then
    while IFS= read -r -d '' lf; do
      LOCK_FILES+=("$lf")
      CLEANUP_LIST+=("锁文件: ${lf#.agent-locks/}")
    done < <(find .agent-locks -name "${LOCK_NAMESPACE}*.yml" -type f -print0 2>/dev/null || true)
  fi
fi

# worktree
WORKTREE_PATH="$HOME/xiyu/worktrees/$AGENT_NAME-$TASK_NAME"
if [[ -d "$WORKTREE_PATH" ]] && git worktree list 2>/dev/null | grep -qF "$WORKTREE_PATH"; then
  CLEANUP_LIST+=("Worktree: $WORKTREE_PATH")
fi

if [[ "${#CLEANUP_LIST[@]}" -eq 0 ]]; then
  info "没有发现需要清理的资源"
  if [[ "$DRY_RUN" == "1" ]]; then echo "  [DRY RUN] 无操作"; fi
  exit 0
fi

echo "  以下资源将被清理："
for item in "${CLEANUP_LIST[@]}"; do
  echo "    • $item"
done
echo

# ─── Step 4: 用户确认 ───────────────────────────────────────────────────────────
hdr "Step 4: 用户确认"

if [[ "$DRY_RUN" == "1" ]]; then
  echo "  [DRY RUN] 跳过确认，仅打印操作计划"
elif [[ "$AUTO_CONFIRM" == "1" ]]; then
  info "自动模式已启用，跳过确认"
else
  read -p "是否确认执行清理？(y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "已取消"
    exit 0
  fi
fi

# ─── Step 5: 清理 agent-locks ───────────────────────────────────────────────────
hdr "Step 5: 清理 agent-locks"

if [[ "$DRY_RUN" == "1" ]]; then
  for lf in "${LOCK_FILES[@]+"${LOCK_FILES[@]}"}"; do
    echo "  [DRY RUN] 删除锁文件: ${lf}"
  done
else
  if [[ "${#LOCK_FILES[@]}" -eq 0 ]]; then
    info "无锁文件需要清理"
  else
    for lf in "${LOCK_FILES[@]+"${LOCK_FILES[@]}"}"; do
      rm -f "$lf"
      info "已删除锁文件: ${lf}"
    done
  fi
fi

# ─── Step 6: 切换到 init 分支 ───────────────────────────────────────────────────
hdr "Step 6: 切换到 $INIT_BRANCH"

if [[ "$DRY_RUN" == "1" ]]; then
  echo "  [DRY RUN] git checkout $INIT_BRANCH && git pull origin main --rebase"
else
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
  info "已拉取最新 origin/main"
fi

# ─── Step 7: 删除本地任务分支 ───────────────────────────────────────────────────
hdr "Step 7: 删除本地任务分支"

if git branch --list "$TASK_BRANCH" | grep -q .; then
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [DRY RUN] git branch -D $TASK_BRANCH"
  else
    git branch -D "$TASK_BRANCH"
    info "已删除本地分支: $TASK_BRANCH"
  fi
else
  info "本地分支 $TASK_BRANCH 不存在（可能已删除）"
fi

# ─── Step 8: 删除远端任务分支 ───────────────────────────────────────────────────
hdr "Step 8: 删除远端分支"

if [[ "$REMOTE_BRANCH_EXISTS" == "1" ]]; then
  if [[ "$INCLUDE_REMOTE" == "1" ]]; then
    if [[ "$DRY_RUN" == "1" ]]; then
      echo "  [DRY RUN] git push origin --delete $TASK_BRANCH"
    else
      git push origin --delete "$TASK_BRANCH"
      info "已删除远端分支: origin/$TASK_BRANCH"
    fi
  else
    warn "远端分支存在: origin/$TASK_BRANCH"
    warn "如需删除，使用 --include-remote 参数"
    echo "    git push origin --delete $TASK_BRANCH"
  fi
else
  info "无远端分支需要清理"
fi

# ─── Step 9: 清理 worktree（仅限临时 worktree） ─────────────────────────────────
hdr "Step 9: 清理 worktree"

if [[ -d "$WORKTREE_PATH" ]] && git worktree list 2>/dev/null | grep -qF "$WORKTREE_PATH"; then
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [DRY RUN] git worktree remove $WORKTREE_PATH --force"
  else
    warn "存在对应的 worktree: $WORKTREE_PATH"
    if [[ "$AUTO_CONFIRM" != "1" ]]; then
      read -p "是否删除此 worktree？(y/N) " -n 1 -r
      echo
      if [[ $REPLY =~ ^[Yy]$ ]]; then
        git worktree remove "$WORKTREE_PATH" --force 2>/dev/null || rm -rf "$WORKTREE_PATH"
        info "已清理 worktree: $WORKTREE_PATH"
      else
        info "跳过 worktree 清理"
      fi
    else
      git worktree remove "$WORKTREE_PATH" --force 2>/dev/null || rm -rf "$WORKTREE_PATH"
      info "已清理 worktree: $WORKTREE_PATH"
    fi
  fi
else
  info "无 worktree 需要清理"
fi

# ─── 完成 ───────────────────────────────────────────────────────────────────────
echo
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅ 任务收尾完成                                             ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo
echo "  当前分支: $(git branch --show-current 2>/dev/null || echo '?')"
echo "  最新提交: $(git log -1 --oneline 2>/dev/null || echo '?')"
echo
echo "  下一步："
if [[ -n "$AGENT_NAME" ]]; then
  echo "    scripts/agent-start-task.sh $AGENT_NAME <new-task> origin/main --in-place"
else
  echo "    开始新任务：scripts/agent-start-task.sh <name> <task> origin/main"
fi
echo
