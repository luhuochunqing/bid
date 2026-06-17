#!/usr/bin/env bash
# ============================================================
# check-worktree-protection.sh — 持久 Worktree 完整性检查
# 在 pre-push / pre-commit 门禁中调用，防止误删 Agent Worktree
# ============================================================
set -euo pipefail

WORKTREE_BASE="/Users/user/xiyu/worktrees"
ERRORS=0

echo "🔍 检查持久 Worktree 完整性..."

while IFS= read -r -d '' wt; do
    name=$(basename "$wt")
    if [ ! -d "$wt" ]; then
        echo "❌ 持久 Worktree 缺失: $wt"
        echo "   恢复命令: git worktree add $WORKTREE_BASE/$name origin/agent/${name}-init && cd $WORKTREE_BASE/$name && git checkout agent/${name}-init"
        ERRORS=$((ERRORS + 1))
    fi
done < <(git worktree list --porcelain | grep -E '^worktree ' | sed 's/^worktree //' | tr '\n' '\0')

if [ $ERRORS -gt 0 ]; then
    echo "❌ 发现 $ERRORS 个持久 Worktree 缺失，请立即恢复！"
    exit 1
fi

echo "✅ 所有持久 Worktree 完好无损"
exit 0
