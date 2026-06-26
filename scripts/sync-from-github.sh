#!/usr/bin/env bash
# Input: GitHub 上的 commit SHA 或分支名（可以指定多个）
# Output: 在本地基于 Gitee main 创建同步分支，cherry-pick GitHub 的改动
# Pos: scripts/ - GitHub → Gitee 增量同步脚本 (2026-06-26)
# 维护声明: 只能 cherry-pick 增量同步，绝对不能 merge 或 force push 覆盖 Gitee main
# 用法: bash scripts/sync-from-github.sh <commit-or-branch> [commit2 ...]
# 安全:
#   - 永远从 Gitee main 切分支，绝不基于 GitHub main
#   - 只使用 cherry-pick，绝不 merge GitHub main
#   - 冲突时立即停止，绝不自动选边
#   - 推送时只能推送到 agent/* 任务分支，绝不能推到 Gitee main
#
# 背景:
#   双远程架构铁律：Gitee main 是唯一真值。
#   GitHub 的改动必须经过 cherry-pick + PR 审查才能进入 Gitee，
#   绝对不允许整分支覆盖或直接 merge GitHub main。

set -euo pipefail

# ─── helpers ─────────────────────────────────────────────────────────────────

info()  { echo "sync-from-github: $*" ; }
warn()  { echo "sync-from-github: WARNING: $*" >&2; }
die()   { echo "sync-from-github: ERROR: $*" >&2; exit 1; }

usage() {
  cat <<EOF
用法: bash scripts/sync-from-github.sh <commit-or-branch> [commit2 ...]

GitHub → Gitee 增量同步工具。只能 cherry-pick 单个或多个 commit。

选项:
  -b, --branch <name>   指定同步分支名（默认 agent/{agent}/github-sync-{N}）
  -h, --help            显示帮助

示例:
  # cherry-pick 一个 commit
  bash scripts/sync-from-github.sh abc1234

  # cherry-pick 多个 commit（按顺序）
  bash scripts/sync-from-github.sh abc1234 def5678

  # cherry-pick 整个分支（会解析为该分支相对 GitHub main 的独有 commit）
  bash scripts/sync-from-github.sh trae/agent-xxx

铁律:
  1. Gitee main 是唯一真值，永不被 GitHub 覆盖
  2. 只 cherry-pick，不 merge GitHub main
  3. 冲突必须人工解决，禁止自动选边
  4. 推送到 agent/* 分支，走 PR 审查合入
EOF
}

# ─── 解析参数 ───────────────────────────────────────────────────────────────

BRANCH_NAME=""
COMMITS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -b|--branch)
      BRANCH_NAME="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      die "未知选项: $1"
      ;;
    *)
      COMMITS+=("$1")
      shift
      ;;
  esac
done

if [[ ${#COMMITS[@]} -eq 0 ]]; then
  usage
  exit 1
fi

# ─── pre-flight checks ───────────────────────────────────────────────────────

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# 检查 remotes
if ! git remote get-url origin &>/dev/null; then
  die "未找到 'origin' remote（Gitee 主仓库）。"
fi
if ! git remote get-url github &>/dev/null; then
  die "未找到 'github' remote。请先配置 GitHub 远程仓库。"
fi

# 确保工作区干净
if ! git diff --quiet HEAD -- || ! git diff --cached --quiet; then
  die "工作区有未提交的改动，请先提交或 stash。"
fi

# 拉取两边最新
info "拉取 Gitee + GitHub 最新..."
git fetch origin main --prune
git fetch github --prune

GITEE_MAIN=$(git rev-parse origin/main)
info "  Gitee main: $GITEE_MAIN"

# ─── 解析目标 commit ────────────────────────────────────────────────────────

resolve_commits() {
  local target="$1"
  local resolved

  # 尝试解析为 GitHub 上的 commit
  if resolved=$(git rev-parse "github/$target" 2>/dev/null); then
    # 是分支名（如 trae/agent-xxx），找出它相对 GitHub main 的独有 commit
    local base
    base=$(git merge-base "github/main" "github/$target" 2>/dev/null || echo "")
    if [[ -n "$base" && "$base" != "$resolved" ]]; then
      # 返回该分支的独有 commit 列表（从旧到新）
      git rev-list --reverse "$base..$resolved"
      return 0
    fi
    echo "$resolved"
    return 0
  fi

  # 尝试直接解析为 commit SHA
  if resolved=$(git rev-parse "$target" 2>/dev/null); then
    echo "$resolved"
    return 0
  fi

  die "无法解析: ${target}（既不是 GitHub 分支名，也不是有效 commit）"
}

# 收集所有要 cherry-pick 的 commit
ALL_COMMITS=()
for target in "${COMMITS[@]}"; do
  while IFS= read -r sha; do
    [[ -n "$sha" ]] && ALL_COMMITS+=("$sha")
  done < <(resolve_commits "$target")
done

if [[ ${#ALL_COMMITS[@]} -eq 0 ]]; then
  die "没有可 cherry-pick 的 commit。"
fi

info "共 ${#ALL_COMMITS[@]} 个 commit 待 cherry-pick："
for sha in "${ALL_COMMITS[@]}"; do
  local_subj=$(git log -1 --format="%s" "$sha" 2>/dev/null || echo "unknown")
  info "  $sha  $local_subj"
done
echo ""

# ─── 创建同步分支 ───────────────────────────────────────────────────────────

# 自动生成分支名
if [[ -z "$BRANCH_NAME" ]]; then
  WORKTREE_NAME=$(basename "$ROOT_DIR")
  BRANCH_NUM=$(date +%Y%m%d%H%M%S)
  BRANCH_NAME="agent/${WORKTREE_NAME}/github-sync-${BRANCH_NUM}"
fi

info "创建同步分支: ${BRANCH_NAME}（基于 origin/main）"
git checkout -b "$BRANCH_NAME" origin/main
echo ""

# ─── 逐 commit cherry-pick ──────────────────────────────────────────────────

PICKED=0
FAILED=0

for sha in "${ALL_COMMITS[@]}"; do
  subj=$(git log -1 --format="%s" "$sha" 2>/dev/null || echo "unknown")
  info "[$((PICKED+1))/${#ALL_COMMITS[@]}] cherry-pick: ${sha:0:8}  $subj"

  if git cherry-pick --no-commit "$sha" 2>/dev/null; then
    PICKED=$((PICKED+1))
    info "  ✓ 成功"
  else
    FAILED=$((FAILED+1))
    warn "  ✗ 冲突！冲突文件："
    git diff --name-only --diff-filter=U 2>/dev/null | sed 's/^/    /' >&2
    echo ""
    warn "  请手动解决冲突后:"
    warn "    git add <files>"
    warn "    git cherry-pick --continue"
    warn "  或放弃本次 cherry-pick:"
    warn "    git cherry-pick --abort"
    echo ""
    die "cherry-pick 冲突，已停止。请人工解决后继续。"
  fi
done

echo ""

# ─── 全部成功 → 提交 ────────────────────────────────────────────────────────

if [[ $FAILED -eq 0 ]]; then
  info "全部 ${#ALL_COMMITS[@]} 个 commit cherry-pick 成功！"
  echo ""

  # 生成 commit message
  COMMIT_MSG="feat: cherry-pick GitHub ${#ALL_COMMITS[@]} commit(s)

来源: GitHub
commit 数: ${#ALL_COMMITS[@]}
$(for sha in "${ALL_COMMITS[@]}"; do
  s=$(git log -1 --format="%s" "$sha" 2>/dev/null || echo "unknown")
  echo "  - ${sha:0:8}  $s"
done)

由 scripts/sync-from-github.sh 自动生成。
请审查后推送到远端并创建 PR。"

  git commit -m "$COMMIT_MSG"
  echo ""
  info "✅ 同步完成！"
  info "  分支: $BRANCH_NAME"
  info "  commit: $(git rev-parse --short HEAD)"
  info "  下一步: git push origin $BRANCH_NAME"
  info "         然后在 Gitee 创建 PR 审查合入"
fi
