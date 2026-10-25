#!/usr/bin/env bash
# Input: scripts/wiki-backfill-active.mjs（2026-10-26 收紧为 archive-only）
# Output: PASS / FAIL — 验证脚本只刷 archive:true 页、拒绝非 archive 页、--dry-run 零写入
# Pos: scripts/test/wiki-backfill-active-contract.sh
# 维护声明: 若 wiki-backfill-active.mjs 的范围语义变化（重新放开活跃文档批量刷新），本契约必须同步失败提醒。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SCRIPT="$REPO_ROOT/scripts/wiki-backfill-active.mjs"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/.wiki/pages/testing"

TODAY="$(date +%Y-%m-%d)"

# 1. archive 页：应被刷新
cat > "$TMP/.wiki/pages/archived-rca.md" <<'EOF'
---
title: 历史 RCA 记录
updated: 2026-08-12
health_checked: 2026-09-01
archive: true
---
# 历史 RCA
EOF

# 2. 活跃文档：应被拒绝且内容零改动
cat > "$TMP/.wiki/pages/living-guide.md" <<'EOF'
---
title: 活跃指南
updated: 2026-08-12
health_checked: 2026-09-01
---
# 活跃指南
EOF

# 3. archive 页但无 health_checked 字段：应跳过
cat > "$TMP/.wiki/pages/archived-no-field.md" <<'EOF'
---
title: 无字段档案
updated: 2026-08-12
archive: true
---
# 无字段档案
EOF

# 4. 子目录里的 archive 页（递归覆盖）
mkdir -p "$TMP/.wiki/pages/lessons-learned"
cat > "$TMP/.wiki/pages/lessons-learned/nested-archived.md" <<'EOF'
---
title: 嵌套档案
health_checked: 2026-09-01
archive: true
---
# 嵌套档案
EOF

fail() { echo "FAIL: $1"; exit 1; }

# ── Case A: dry-run 零写入 ──
OUT_A="$(cd "$TMP" && node "$SCRIPT" --dry-run)"
grep -q '✓ archived-rca.md' <<<"$OUT_A" || fail "dry-run 应列出 archive 页"
grep -q '✗ 拒绝（非 archive 页面，需逐页人工核实）: living-guide.md' <<<"$OUT_A" || fail "dry-run 应拒绝活跃文档"
grep -q "health_checked: 2026-09-01" "$TMP/.wiki/pages/archived-rca.md" || fail "dry-run 不得写入"

# ── Case B: execute 模式 ──
OUT_B="$(cd "$TMP" && node "$SCRIPT")"

grep -q "总计: 2 个 archive 页更新 health_checked, 1 个非 archive 页拒绝, 1 个跳过" <<<"$OUT_B" \
  || fail "汇总行不符: $(grep '总计' <<<"$OUT_B")"

grep -q "health_checked: $TODAY" "$TMP/.wiki/pages/archived-rca.md" || fail "archive 页应刷新为今天"
grep -q "health_checked: $TODAY" "$TMP/.wiki/pages/lessons-learned/nested-archived.md" || fail "子目录 archive 页应刷新"
grep -q "health_checked: 2026-09-01" "$TMP/.wiki/pages/living-guide.md" || fail "活跃文档的 health_checked 被改动（不允许批量刷）"
grep -q "updated: 2026-08-12" "$TMP/.wiki/pages/archived-rca.md" || fail "archive 页的 updated 不得改动"
if grep -q 'health_checked' "$TMP/.wiki/pages/archived-no-field.md"; then
  fail "无字段页不得被新增 health_checked"
fi

# archive 语义不得被本脚本篡改
grep -q '^archive: true$' "$TMP/.wiki/pages/archived-rca.md" || fail "archive: true 标记不得丢失"

echo "PASS"
