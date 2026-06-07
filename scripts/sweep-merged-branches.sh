#!/usr/bin/env bash
# Input: none (reads git state)
# Output: deletes local branches whose commits are all reachable from origin/main
# Pos: scripts/清理已合入 main 的本地残留分支
# 维护声明: 仅删除已完全合入 origin/main 的分支（git branch -d 安全保护）。
#           若 origin/main 不存在回退为本地 main；排除 main、agent/* 锚点分支。
set -euo pipefail

DRY_RUN="${SWEEP_DRY_RUN:-0}"

# Resolve base ref: prefer origin/main, fallback to local main
BASE_REF="origin/main"
if ! git rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  BASE_REF="main"
fi

echo "[sweep] checking for merged branches against $BASE_REF"

# Collect candidates: merged into BASE_REF, exclude main and agent/* anchor branches
CANDIDATES=$(git branch --merged "$BASE_REF" --format='%(refname:short)' \
  | grep -v '^main$' \
  | grep -v '^agent/' \
  || true)

if [[ -z "$CANDIDATES" ]]; then
  echo "[sweep] no merged branches to clean up"
  exit 0
fi

echo "[sweep] candidates:"
echo "$CANDIDATES" | sed 's/^/  /'

if [[ "$DRY_RUN" == "1" ]]; then
  echo "[sweep] DRY_RUN — no branches deleted. Re-run with SWEEP_DRY_RUN=0 to execute."
  exit 0
fi

DELETED=0
SKIPPED=0
while IFS= read -r branch; do
  if git branch -d "$branch" 2>/dev/null; then
    echo "[sweep] deleted: $branch"
    ((DELETED++)) || true
  else
    echo "[sweep] skipped: $branch (not fully merged or has unmerged work)"
    ((SKIPPED++)) || true
  fi
done <<< "$CANDIDATES"

echo "[sweep] done: $DELETED deleted, $SKIPPED skipped"
