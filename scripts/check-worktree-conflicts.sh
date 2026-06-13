#!/usr/bin/env bash
# Input: none (reads current git diff + worktree list)
# Output: overlap report or OK status
# Pos: scripts/ — 项目自动化检查套件
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。


# ============================================================
# check-worktree-conflicts.sh
#
# 跨 Worktree 冲突预防检测
#
# 解决 #449 被 #448 tree 覆盖式合入问题。在 agent-start-task
# 或 PR 创建前，检测当前分支与已合入 PR 之间的文件重叠情况。
#
# 用法：
#   bash scripts/check-worktree-conflicts.sh        # 检查当前分支
#   bash scripts/check-worktree-conflicts.sh --diff  # 仅显示重叠差异
#   bash scripts/check-worktree-conflicts.sh --ci    # CI 模式（退出码）
#
# 退出码：
#   0 - 无冲突
#   1 - 有重叠文件（需评估冲突风险）
# ============================================================

set -euo pipefail

RED='[0;31m'
YELLOW='[1;33m'
GREEN='[0;32m'
NC='[0m'

CI_MODE=false
DIFF_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --ci) CI_MODE=true ;;
    --diff) DIFF_ONLY=true ;;
  esac
done

# --- 检测当前分支 ---
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "detached")
if [ "$CURRENT_BRANCH" = "detached" ] || [ "$CURRENT_BRANCH" = "main" ]; then
  echo -e "${GREEN}[OK]${NC} 当前为 $CURRENT_BRANCH，无需冲突检测"
  exit 0
fi

# --- 获取基准分支 ---
BASE_BRANCH="${CI_MERGE_REQUEST_TARGET_BRANCH_NAME:-origin/main}"
echo -e "🔍 检测分支: ${CURRENT_BRANCH}"
echo -e "📐 基准分支: ${BASE_BRANCH}"
echo ""

# --- 获取当前分支修改的文件（相对基准） ---
CHANGED_FILES=$(git diff --name-only --diff-filter=AMR "$BASE_BRANCH"..."HEAD" 2>/dev/null || true)
if [ -z "$CHANGED_FILES" ]; then
  echo -e "${GREEN}[OK]${NC} 当前分支与基准无新增/修改文件"
  exit 0
fi

# --- 检查 worktree 目录下的其他活跃分支 ---
WORKTREE_DIR=$(git rev-parse --git-common-dir 2>/dev/null)
if [ -z "$WORKTREE_DIR" ] || [ ! -d "$WORKTREE_DIR" ]; then
  echo -e "${GREEN}[SKIP]${NC} 无法确定 git common dir，跳过 worktree 检查"
  exit 0
fi

echo "📋 正在检查其他 worktree 的活跃分支..."
echo ""

HAS_OVERLAP=false
OVERLAP_REPORT=""

while IFS= read -r wt_line; do
  # 每行格式: <path> <hash> [<branch>]
  WT_PATH=$(echo "$wt_line" | awk '{print $1}')
  WT_BRANCH=$(echo "$wt_line" | awk '{for(i=3;i<=NF;i++) printf "%s ", $i; print ""}' | sed 's/\[//g; s/\]//g' | xargs)

  # 跳过当前 worktree 和 main
  [ "$WT_PATH" = "$(pwd)" ] && continue
  [ "$WT_BRANCH" = "main" ] || [ -z "$WT_BRANCH" ] && continue

  # 获取该 worktree 的最晚 commit 修改的文件
  WT_FILES=$(git -C "$WT_PATH" diff --name-only --diff-filter=AMR "HEAD~3..HEAD" 2>/dev/null || true)
  if [ -z "$WT_FILES" ]; then
    continue
  fi

  # 找交集
  OVERLAP=$(comm -12 <(echo "$CHANGED_FILES" | sort -u) <(echo "$WT_FILES" | sort -u) 2>/dev/null || true)
  if [ -n "$OVERLAP" ]; then
    HAS_OVERLAP=true
    OVERLAP_REPORT+="
  ⚠️ 冲突风险: ${WT_PATH} (${WT_BRANCH})
"
    while IFS= read -r file; do
      OVERLAP_REPORT+="    - ${file}
"
    done <<< "$OVERLAP"
  fi
done < <(git worktree list 2>/dev/null)

if [ "$HAS_OVERLAP" = true ]; then
  echo -e "${RED}[冲突风险]${NC} 与其他 worktree 存在文件重叠："
  echo -e "$OVERLAP_REPORT"
  echo ""
  echo "💡 建议："
  echo "  1. 与对应 worktree 的开发分支协调合入顺序"
  echo "  2. 使用 --in-place 模式合并后再推送"
  echo "  3. 合入后手动解决冲突时，注意不要覆盖另一方的更改"

  if [ "$CI_MODE" = true ]; then
    echo ""
    echo -e "${RED}CI 门禁: 存在潜在冲突，请协调后重新推送${NC}"
    exit 1
  fi
  exit 1
else
  echo -e "${GREEN}[OK]${NC} 当前分支与其他 worktree 的活跃分支无文件重叠"
  exit 0
fi
