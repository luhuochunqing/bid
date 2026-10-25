#!/usr/bin/env bash
# Input: 受管 Worktree 目录与期望的 canonical common gitdir（可用参数覆盖默认值）
# Output: 缺失、非 Git 目录或 common gitdir 分叉的错误汇总，非零退出码阻断 push
# Pos: scripts/ - Git Worktree 完整性预检
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# ============================================================
# check-worktree-protection.sh — 持久 Worktree 完整性检查
# 在 pre-push / pre-commit 门禁中调用，防止误删 Agent Worktree
# ============================================================
set -euo pipefail

WORKTREE_BASE="/Users/user/xiyu/worktrees"
EXPECTED_COMMON_DIR=""
ERRORS=0

usage() {
    cat <<'EOF'
Usage: check-worktree-protection.sh [--worktree-base DIR] [--expected-common-dir DIR]
EOF
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --worktree-base)
            [ "$#" -ge 2 ] || { echo "❌ --worktree-base 缺少目录参数"; exit 2; }
            WORKTREE_BASE="$2"
            shift 2
            ;;
        --expected-common-dir)
            [ "$#" -ge 2 ] || { echo "❌ --expected-common-dir 缺少目录参数"; exit 2; }
            EXPECTED_COMMON_DIR="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *)
            echo "❌ 未知参数: $1"
            usage
            exit 2
            ;;
    esac
done

canonical_dir() {
    (cd "$1" 2>/dev/null && pwd -P)
}

if [ ! -d "$WORKTREE_BASE" ]; then
    echo "❌ 受管 Worktree 目录不存在: $WORKTREE_BASE"
    exit 1
fi

WORKTREE_BASE="$(canonical_dir "$WORKTREE_BASE")"

if [ -z "$EXPECTED_COMMON_DIR" ]; then
    EXPECTED_COMMON_DIR="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
fi

if [ -z "$EXPECTED_COMMON_DIR" ] || [ ! -d "$EXPECTED_COMMON_DIR" ]; then
    echo "❌ 无法确定期望的 Git common dir: ${EXPECTED_COMMON_DIR:-<空>}"
    exit 1
fi

EXPECTED_COMMON_DIR="$(canonical_dir "$EXPECTED_COMMON_DIR")"

echo "🔍 检查持久 Worktree 完整性与共享 gitdir..."
echo "   受管目录: $WORKTREE_BASE"
echo "   期望 common dir: $EXPECTED_COMMON_DIR"

while IFS= read -r -d '' wt; do
    case "$wt" in
        "$WORKTREE_BASE"/*) ;;
        *) continue ;;
    esac
    if [ ! -d "$wt" ]; then
        name=$(basename "$wt")
        echo "❌ 持久 Worktree 缺失: $wt"
        echo "   恢复命令: git worktree add $WORKTREE_BASE/$name origin/agent/${name}-init && cd $WORKTREE_BASE/$name && git checkout agent/${name}-init"
        ERRORS=$((ERRORS + 1))
    fi
done < <(git worktree list --porcelain | grep -E '^worktree ' | sed 's/^worktree //' | tr '\n' '\0')

for wt in "$WORKTREE_BASE"/*; do
    [ -e "$wt" ] || continue
    [ -d "$wt" ] || continue

    actual_common_dir="$(git -C "$wt" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
    if [ -z "$actual_common_dir" ] || [ ! -d "$actual_common_dir" ]; then
        echo "❌ 受管目录不是有效 Git worktree: $wt"
        ERRORS=$((ERRORS + 1))
        continue
    fi

    actual_common_dir="$(canonical_dir "$actual_common_dir")"
    if [ "$actual_common_dir" != "$EXPECTED_COMMON_DIR" ]; then
        echo "❌ Worktree 未共享期望的 gitdir: $wt"
        echo "   期望: $EXPECTED_COMMON_DIR"
        echo "   实际: $actual_common_dir"
        ERRORS=$((ERRORS + 1))
    fi
done

if [ $ERRORS -gt 0 ]; then
    echo "❌ 发现 $ERRORS 个持久 Worktree 完整性问题，请先修复再继续！"
    exit 1
fi

echo "✅ 所有受管 Worktree 完整且共享同一 gitdir"
exit 0
