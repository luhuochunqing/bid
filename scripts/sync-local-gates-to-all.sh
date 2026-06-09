#!/usr/bin/env bash
# Input: current worktree with new .githooks/*-wrapper.sh, scripts/check-local-gates.sh
# Output: all worktrees in this repo get the same files + git alias config
# Pos: scripts/ - 同步门禁体系到所有 Worktree (2026-06-09)
# 维护声明: 同步门禁体系到所有 Worktree；新增门禁脚本时请同步脚本目录说明。
# 用法: bash scripts/sync-local-gates-to-all.sh
# 安全: 不会 git commit/push，不修改工作区内容，幂等

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_GITHOOKS="$ROOT_DIR/.githooks"
SOURCE_SCRIPTS="$ROOT_DIR/scripts"

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  🔄 同步本地门禁体系到所有 Worktree                         ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

SYNCED=0
SKIPPED=0

# 收集所有 worktree（排除当前 worktree）
WORKTREES=$(git worktree list | awk '{print $1}')

while IFS= read -r wt_path; do
  [ -z "$wt_path" ] && continue
  [ "$wt_path" = "$ROOT_DIR" ] && continue

  echo "── $wt_path ──"

  # 检查 worktree 是否有效
  if [ ! -d "$wt_path/.git" ] && [ ! -f "$wt_path/.git" ]; then
    echo "  ⚠ 不是有效 git worktree，跳过"
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  # 创建 .githooks/ 目录（如果不存在）
  mkdir -p "$wt_path/.githooks"

  # 复制 wrapper 脚本
  for f in git-push-wrapper.sh git-commit-wrapper.sh; do
    if [ -f "$SOURCE_GITHOOKS/$f" ]; then
      cp "$SOURCE_GITHOOKS/$f" "$wt_path/.githooks/$f"
      chmod +x "$wt_path/.githooks/$f"
      echo "  ✓ .githooks/$f"
    fi
  done

  # 复制自检脚本
  if [ -f "$SOURCE_SCRIPTS/check-local-gates.sh" ]; then
    cp "$SOURCE_SCRIPTS/check-local-gates.sh" "$wt_path/scripts/check-local-gates.sh"
    chmod +x "$wt_path/scripts/check-local-gates.sh"
    echo "  ✓ scripts/check-local-gates.sh"
  fi

  # 配置 git alias（在目标 worktree 中，仅首次设置）
  (cd "$wt_path"
    if ! git config alias.push 2>/dev/null | grep -q 'git-push-wrapper'; then
      git config alias.push '!bash .githooks/git-push-wrapper.sh'
      echo "  ✓ alias.push 已配置"
    else
      echo "  ⊙ alias.push 已存在"
    fi
    if ! git config alias.commit 2>/dev/null | grep -q 'git-commit-wrapper'; then
      git config alias.commit '!bash .githooks/git-commit-wrapper.sh'
      echo "  ✓ alias.commit 已配置"
    else
      echo "  ⊙ alias.commit 已存在"
    fi
  )

  SYNCED=$((SYNCED + 1))
  echo ""
done <<< "$WORKTREES"

echo "─────────────────────────"
echo "已同步: $SYNCED  worktrees"
echo "跳过:   $SKIPPED"
echo "─────────────────────────"
echo ""
echo "验证: 在各 worktree 中执行 bash scripts/check-local-gates.sh"
