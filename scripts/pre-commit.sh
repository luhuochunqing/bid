#!/usr/bin/env bash
# Input: git staged diff and working tree state
# Output: BLOCKER or WARNING if blueprint gates fail
# Pos: scripts/ - Pre-commit hook for blueprint-driven development
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Installed via: ln -sf ../../scripts/pre-commit.sh .git/hooks/pre-commit
# Gated checks:
#   1. Blueprint gap table exists for §4.x.x touched files
#   2. New Vue/JS files >200 lines warn about line-budget
#   3. Placeholder anti-patterns (fake success messages)

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
SCRIPTS_DIR="${REPO_ROOT}/scripts"
cd "$REPO_ROOT"

# ── Gate 1: Blueprint Gap Table ──────────────────────────────────────────────
echo "🔍 [gate-1/3] Checking blueprint gap tables..."
node "${SCRIPTS_DIR}/check-blueprint-gaps.mjs" --staged 2>/dev/null || {
  echo "   FAILED — blueprint-touched files need a gap table first"
  echo "   Run: node scripts/check-blueprint-gaps.mjs --staged --verbose"
  echo ""
  echo "   Gap table must contain:"
  echo "     ## 1. Gap analysis table"
  echo "     ## 3. Task breakdown"
  echo "     ## 5. TODO checklist [ ]"
  echo "     ## 6. Acceptance criteria"
  exit 1
}
echo "   ✅ Gap table gate passed"

# ── Gate 2: New large Vue/JS files → suggest excludeFiles ──────────────────
echo "🔍 [gate-2/3] Checking new large files for line-budget..."
NEW_LARGE_FILES=$(git diff --cached --name-only --diff-filter=ACM | \
  grep -E '\.(vue|js)$' | \
  while read -r file; do
    if git show ":$file" 2>/dev/null | grep -c . 2>/dev/null | \
      awk '{exit ($1 > 200 ? 0 : 1)}'; then
      echo "$file"
    fi
  done || true)

if [[ -n "$NEW_LARGE_FILES" ]]; then
  echo "   ⚠️  New files >200 lines detected:"
  echo "$NEW_LARGE_FILES" | while read -r f; do
    lines=$(git show ":$f" 2>/dev/null | grep -c . || echo "?")
    echo "   • $f ($lines lines)"
  done
  echo ""
  echo "   If this file is intentionally large (>300 lines), add it to"
  echo "   scripts/line-budget.config.json → excludeFiles before committing:"
  echo '   "excludeFiles": ["src/path/YourBigFile.vue"]'
  echo ""
  echo "   ⚠️  Continuing — line-budget gate will enforce in CI"
fi
echo "   ✅ Large file gate passed"

# ── Gate 3: Fake success warnings ───────────────────────────────────────────
echo "🔍 [gate-3/3] Scanning for placeholder anti-patterns..."
FAKE_SUCCESS=$(git diff --cached --name-only --diff-filter=ACM | \
  xargs -I{} git show ":{}" 2>/dev/null | \
  grep -n "ElMessage\.success.*通知管理员\|ElMessage\.success.*已通知" || true)

if [[ -n "$FAKE_SUCCESS" ]]; then
  echo "   ⚠️  Possible fake-success pattern detected:"
  echo "$FAKE_SUCCESS" | head -5
  echo ""
  echo "   Pattern: ElMessage.success() called but no API call made"
  echo "   Fix: either implement the API call or remove the success message"
  echo "   This is a warning only — not blocking commit"
fi
echo "   ✅ Placeholder gate passed"

echo ""
echo "✅ All pre-commit gates passed"
exit 0
