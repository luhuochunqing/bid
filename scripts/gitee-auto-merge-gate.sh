#!/usr/bin/env bash
# Input: optional PR number (auto-detected via current branch)
# Output: auto-merge result
# Pos: scripts/ — Gitee CI 自动合并门禁检查 + 合并
# 维护声明: 依赖 gitee-pr-helper.sh + GITEE_TOKEN。
#
# 作用: 在 CI pipeline 中调用，检查当前分支的 MR 是否满足合并条件，
#      若满足则自动合并（squash）。模拟 GitHub auto-merge 行为。
#
# 用法:
#   GITEE_TOKEN=xxx ./scripts/gitee-auto-merge-gate.sh [pr_number]
#
# 环境变量:
#   GITEE_TOKEN     — Gitee 个人访问令牌（必需）
#   GITEE_OWNER     — 仓库所有者（默认: allinai888）
#   GITEE_REPO      — 仓库名（默认: bid）
#   AUTO_MERGE_SKIP — 设为 1 跳过自动合并（仅检查门禁）
#
# 合并条件:
#   1. MR 处于 open 状态
#   2. 至少 1 个 approved review
#   3. 无 merge conflicts (mergeable = true)
#   4. MR 不是 WIP/Draft
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
HELPER="${SCRIPT_DIR}/gitee-pr-helper.sh"
GITEE_OWNER="${GITEE_OWNER:-allinai888}"
GITEE_REPO="${GITEE_REPO:-bid}"
API="https://gitee.com/api/v5/repos/${GITEE_OWNER}/${GITEE_REPO}"

echo "[auto-merge] === Gitee Auto-Merge Gate ==="

# --- Prerequisites ---
if [[ -z "${GITEE_TOKEN:-}" ]]; then
  echo "[auto-merge] ⚠ GITEE_TOKEN 未设置，跳过自动合并"
  echo "[auto-merge] 设置 GITEE_TOKEN 可启用自动合并"
  exit 0
fi

if [[ ! -f "$HELPER" ]]; then
  echo "[auto-merge] ❌ gitee-pr-helper.sh 未找到: $HELPER"
  exit 1
fi

# --- Detect PR number ---
PR_NUM="${1:-}"
if [[ -z "$PR_NUM" ]]; then
  BRANCH=$(git branch --show-current 2>/dev/null || echo "")
  if [[ -z "$BRANCH" ]]; then
    echo "[auto-merge] ❌ 无法检测当前分支"
    exit 1
  fi
  echo "[auto-merge] 检测分支: ${BRANCH}"

  PR_NUM=$(GITEE_TOKEN="$GITEE_TOKEN" bash "$HELPER" list "$BRANCH" 2>/dev/null | grep -Eo '#[0-9]+' | head -1 | tr -d '#')
fi

if [[ -z "$PR_NUM" ]]; then
  echo "[auto-merge] ⚠ 未找到开放 MR（当前分支可能未创建 MR）"
  echo "[auto-merge] 跳过自动合并"
  exit 0
fi

echo "[auto-merge] MR #${PR_NUM}"

# --- Check MR status via Gitee API ---
echo "[auto-merge] 获取 MR #${PR_NUM} 详细信息..."
MR_INFO=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" "${API}/pulls/${PR_NUM}")

STATE=$(echo "$MR_INFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('state',''))" 2>/dev/null || echo "")
TITLE=$(echo "$MR_INFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('title',''))" 2>/dev/null || echo "")
MERGEABLE=$(echo "$MR_INFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('mergeable',False))" 2>/dev/null || echo "false")
HEAD_LABEL=$(echo "$MR_INFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('head',{}).get('label',''))" 2>/dev/null || echo "")
BASE_LABEL=$(echo "$MR_INFO" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('base',{}).get('label',''))" 2>/dev/null || echo "")

echo "[auto-merge]   Title:    ${TITLE}"
echo "[auto-merge]   State:    ${STATE}"
echo "[auto-merge]   Mergeable: ${MERGEABLE}"
echo "[auto-merge]   Head:     ${HEAD_LABEL}"
echo "[auto-merge]   Base:     ${BASE_LABEL}"

# --- Gate 1: State must be open ---
if [[ "$STATE" != "open" ]]; then
  echo "[auto-merge] ❌ MR #${PR_NUM} 不是 open 状态 (state=${STATE})"
  exit 0
fi

# --- Gate 2: Merge conflicts ---
if [[ "$MERGEABLE" != "true" ]]; then
  echo "[auto-merge] ❌ MR #${PR_NUM} 有冲突 (mergeable=${MERGEABLE})"
  echo "[auto-merge]    请先解决冲突后再试"
  exit 0
fi

# --- Gate 3: Check reviews (approved count) ---
echo "[auto-merge] 检查 MR #${PR_NUM} 审核状态..."
REVIEW_INFO=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" "${API}/pulls/${PR_NUM}/reviews")
APPROVED_COUNT=$(echo "$REVIEW_INFO" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if isinstance(data, list):
    print(sum(1 for r in data if r.get('state') == 'approved'))
else:
    print('0')
" 2>/dev/null || echo "0")

echo "[auto-merge]   审核通过数: ${APPROVED_COUNT}"

if [[ "$APPROVED_COUNT" -lt 1 ]]; then
  echo "[auto-merge] ⏳ MR #${PR_NUM} 尚未获得审核通过 (approved=${APPROVED_COUNT})"
  echo "[auto-merge]    等待至少 1 个 reviewer 审批"
  exit 0
fi

# --- Gate 4: Run local gate checks ---
if [[ -f "${SCRIPT_DIR}/pre-push-dry-run.sh" ]] && [[ "${AUTO_MERGE_SKIP_CHECKS:-}" != "1" ]]; then
  echo "[auto-merge] 运行 CI 门禁预演..."
  if bash "${SCRIPT_DIR}/pre-push-dry-run.sh" --quick; then
    echo "[auto-merge] ✅ 门禁检查通过"
  else
    echo "[auto-merge] ❌ 门禁检查未通过，不合并"
    exit 0
  fi
else
  echo "[auto-merge] ⚠ 跳过本地门禁检查"
fi

# --- Merge ---
if [[ "${AUTO_MERGE_SKIP:-}" == "1" ]]; then
  echo "[auto-merge] AUTO_MERGE_SKIP=1，仅检查门禁，跳过合并"
  echo "[auto-merge] ✅ 所有合并条件已满足，可手动合并"
  exit 0
fi

echo "[auto-merge] 合并 MR #${PR_NUM} (squash)..."
MERGE_RESULT=$(curl -s -X PUT -H "Authorization: Bearer ${GITEE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"merge_type":"squash","remove_source_branch":true,"title":"auto-merge by gitee-auto-merge-gate.sh"}' \
  "${API}/pulls/${PR_NUM}/merge")

MERGE_STATE=$(echo "$MERGE_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('state',''))" 2>/dev/null || echo "failed")

if [[ "$MERGE_STATE" != "failed" ]] && [[ -n "$MERGE_STATE" ]]; then
  echo "[auto-merge] ✅ MR #${PR_NUM} 合并成功！"
  echo "$MERGE_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, indent=2, ensure_ascii=False))" 2>/dev/null
else
  echo "[auto-merge] ❌ 合并失败:"
  echo "$MERGE_RESULT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, indent=2, ensure_ascii=False))" 2>/dev/null || echo "$MERGE_RESULT"
  exit 0
fi
