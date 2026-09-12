#!/usr/bin/env bash
# Input: Sidecar base URL (arg1 / SIDECAR_BASE_URL, default http://127.0.0.1:8000),
#        v1 golden fixture path (arg2 / SIDECAR_SMOKE_FIXTURE, default 1371_tables_v1.json)
# Output: sidecar 发布冒烟结果（/health + 版本指纹 + 1371 回放 + /extract-tables 双版本探针）
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 用法：
#   bash scripts/release/smoke-sidecar.sh                                    # 服务器默认 8000
#   bash scripts/release/smoke-sidecar.sh http://127.0.0.1:8009              # 本地开发 sidecar
#   SIDECAR_BASE_URL=http://127.0.0.1:8000 bash scripts/release/smoke-sidecar.sh
# 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 设计说明（发布闭环 Stage D）：
# - 只用 curl + python3，mac / linux 通用；本地 8009 与服务器 8000 靠参数切换。
# - fixture 回放只断言 v1 契约形态（tables/rows/cell 四键），不碰评分语义——
#   语义归 Task1 fixture 门禁所有，本脚本与之正交。
# - /dev/null 空 docx 探针：零测试数据即可验证版本路由（v1 头→v1 形，默认→v2 形）。
set -euo pipefail

SIDECAR_BASE="${1:-${SIDECAR_BASE_URL:-http://127.0.0.1:8000}}"
SIDECAR_BASE="${SIDECAR_BASE%/}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
FIXTURE="${2:-${SIDECAR_SMOKE_FIXTURE:-$ROOT_DIR/backend/src/test/resources/scoreparse-golden/1371_tables_v1.json}}"

PASS=0
FAIL=0
WARN=0

pass() { echo "  ✅ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ❌ $1"; FAIL=$((FAIL + 1)); }
warn() { echo "  ⚠️  $1"; WARN=$((WARN + 1)); }
section() { echo ""; echo "━━━ $1 ━━━"; }

echo "============================================"
echo "  Sidecar Release Smoke"
echo "  Target:  $SIDECAR_BASE"
echo "  Fixture: $FIXTURE"
echo "  Time:    $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "============================================"

TMPDIR_SMOKE="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_SMOKE"' EXIT

# ── P0: /health + 版本指纹 ──
section "P0: Health + Schema Fingerprint"
HEALTH_BODY="$TMPDIR_SMOKE/health.body"
HEALTH_HEAD="$TMPDIR_SMOKE/health.head"
HEALTH_CODE="$(curl -sS --connect-timeout 5 --max-time 15 -D "$HEALTH_HEAD" -o "$HEALTH_BODY" \
  -w '%{http_code}' "$SIDECAR_BASE/health" 2>/dev/null || true)"
[[ "$HEALTH_CODE" == "000" || -z "$HEALTH_CODE" ]] && HEALTH_CODE="DOWN"
if [[ "$HEALTH_CODE" == "200" ]] && grep -q '"up"' "$HEALTH_BODY" 2>/dev/null; then
  pass "GET /health → 200 UP"
else
  fail "GET /health → ${HEALTH_CODE}（期望 200 UP，sidecar 未存活则阻断发布）"
fi
HEALTH_SCHEMA="$(grep -i '^X-Schema-Version:' "$HEALTH_HEAD" 2>/dev/null | tr -d '\r' | awk '{print $2}' || true)"
if [[ -n "$HEALTH_SCHEMA" ]]; then
  pass "健康响应头 X-Schema-Version=$HEALTH_SCHEMA"
else
  fail "健康响应缺少 X-Schema-Version 头（sidecar 版本不可指纹，阻断发布）"
fi

# ── P0: 1371_tables_v1.json 回放（仅 v1 契约形态）──
section "P0: Fixture Replay (1371_tables_v1.json, v1 shape only)"
if [[ ! -f "$FIXTURE" ]]; then
  fail "fixture 不存在：$FIXTURE"
else
  REPLAY_OUT="$(python3 - "$FIXTURE" <<'EOF' 2>&1 || true
import json, sys
p = sys.argv[1]
try:
    doc = json.load(open(p, encoding="utf-8"))
except Exception as e:
    print(f"FAIL fixture 不是合法 JSON: {e}")
    sys.exit(0)
tables = doc.get("tables")
if not isinstance(tables, list) or not tables:
    print("FAIL fixture tables 为空或非数组")
    sys.exit(0)
cells = 0
score_tables = 0
for t in tables:
    if not isinstance(t.get("tableIndex"), int): print("FAIL table 缺 tableIndex:int"); sys.exit(0)
    if not isinstance(t.get("rows"), list): print("FAIL table 缺 rows:list"); sys.exit(0)
    if not isinstance(t.get("isScoreTable"), bool): print("FAIL table 缺 isScoreTable:bool"); sys.exit(0)
    if t["isScoreTable"]: score_tables += 1
    for row in t["rows"]:
        for c in row:
            for k in ("text", "rowspan", "colspan", "isMergedContinue"):
                if k not in c: print(f"FAIL cell 缺键 {k}"); sys.exit(0)
            cells += 1
if "schemaVersion" in doc or "artifact" in doc:
    print("FAIL fixture 混入 v2 字段（schemaVersion/artifact），v1 金标被污染")
    sys.exit(0)
print(f"OK tables={len(tables)} cells={cells} scoreTables={score_tables}")
EOF
)"
  if [[ "$REPLAY_OUT" == OK* ]]; then
    pass "fixture v1 形态回放通过（${REPLAY_OUT#OK }）"
  else
    fail "fixture 回放失败：$REPLAY_OUT"
  fi
fi

# ── P1: /extract-tables 双版本探针（空 docx，零测试数据）──
section "P1: /extract-tables Version Probes (empty docx)"
probe() {
  local label="$1"; shift
  local body="$TMPDIR_SMOKE/probe.body"
  local head="$TMPDIR_SMOKE/probe.head"
  local code
  code="$(curl -sS --connect-timeout 5 --max-time 30 -D "$head" -o "$body" \
    -w '%{http_code}' "$@" "$SIDECAR_BASE/extract-tables" 2>/dev/null || true)"
  [[ "$code" == "000" || -z "$code" ]] && code="DOWN"
  printf '%s|%s|%s' "$label" "$code" "$(grep -i '^X-Schema-Version:' "$head" 2>/dev/null | tr -d '\r' | awk '{print $2}' || true)"
}
V1_RESULT="$(probe "v1" -F "file=@/dev/null;filename=empty.docx" -H "X-Schema-Version: 1.0")"
V1_CODE="${V1_RESULT#v1|}"; V1_CODE="${V1_CODE%%|*}"; V1_HEAD="${V1_RESULT##*|}"
if [[ "$V1_CODE" == "404" || "$V1_CODE" == "DOWN" || "$V1_CODE" == "000" ]]; then
  fail "v1 探针 → ${V1_CODE}（/extract-tables 不存在，发布后会全量降级 Markdown）"
elif [[ "$V1_CODE" == "200" ]] && grep -q '"tables"' "$TMPDIR_SMOKE/probe.body" 2>/dev/null \
  && [[ "$V1_HEAD" == 1.* ]]; then
  pass "v1 探针 → 200 v1 形态（tables[]，响应头 X-Schema-Version=${V1_HEAD}）"
else
  fail "v1 探针 → ${V1_CODE}（期望 200 + tables[] + 1.x 响应头，实际头=${V1_HEAD}）"
fi

V2_RESULT="$(probe "v2" -F "file=@/dev/null;filename=empty.docx")"
V2_CODE="${V2_RESULT#v2|}"; V2_CODE="${V2_CODE%%|*}"; V2_HEAD="${V2_RESULT##*|}"
if [[ "$V2_CODE" == "200" ]] && grep -q '"schemaVersion"' "$TMPDIR_SMOKE/probe.body" 2>/dev/null \
  && [[ "$V2_HEAD" == 2.* ]]; then
  V2_SCHEMA="$(python3 -c "import json;print(json.load(open('$TMPDIR_SMOKE/probe.body')).get('schemaVersion','?'))" 2>/dev/null || echo '?')"
  pass "v2 探针 → 200 DocumentArtifact（schemaVersion=${V2_SCHEMA}，响应头=${V2_HEAD}）"
else
  fail "v2 探针 → ${V2_CODE}（期望 200 + schemaVersion + 2.x 响应头，实际头=${V2_HEAD}）"
fi

# ── Summary ──
section "Summary"
TOTAL=$((PASS + FAIL + WARN))
echo ""
echo "  ✅ 通过: ${PASS}"
echo "  ❌ 失败: ${FAIL}"
echo "  ⚠️  警告: ${WARN}"
echo "  总计: ${TOTAL} 项"
echo ""

if [[ "$FAIL" -gt 0 ]]; then
  echo "结论: ❌ SIDECAR SMOKE FAILED — 阻断发布"
  exit 1
elif [[ "$WARN" -gt 0 ]]; then
  echo "结论: ⚠️  SIDECAR SMOKE PASSED WITH WARNINGS"
else
  echo "结论: ✅ SIDECAR SMOKE PASSED"
fi
echo "============================================"
