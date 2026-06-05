#!/usr/bin/env bash
# Input: subcommand + branch name + optional GITEE_TOKEN
# Output: PR info or merge result
# Pos: scripts/ — Gitee PR 操作辅助工具
# 维护声明: 依赖 curl + GITEE_TOKEN 环境变量。Gitee API v5。
#
# 用法:
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh list          # 列出当前分支的 PR
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh create        # 创建 PR (当前分支→main)
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh check <num>   # 检查 PR 状态
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh merge <num>   # 合并 PR (squash)
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh approve <num> # 查看 PR 审核状态
#
# 环境变量:
#   GITEE_TOKEN     — Gitee 个人访问令牌（必需）
#   GITEE_OWNER     — 仓库所有者（默认: allinai888）
#   GITEE_REPO      — 仓库名（默认: bid）
#   GITEE_BASE_BRANCH — 目标分支（默认: main）
set -euo pipefail

GITEE_OWNER="${GITEE_OWNER:-allinai888}"
GITEE_REPO="${GITEE_REPO:-bid}"
GITEE_BASE="${GITEE_BASE_BRANCH:-main}"
API="https://gitee.com/api/v5/repos/${GITEE_OWNER}/${GITEE_REPO}"

usage() {
  echo "用法: $0 {list|create|check|merge|approve} [pr_number]"
  exit 1
}

check_token() {
  if [[ -z "${GITEE_TOKEN:-}" ]]; then
    echo "[gitee-pr] 错误: 需要 GITEE_TOKEN 环境变量" >&2
    echo "[gitee-pr] 请先在 https://gitee.com/profile/personal_access_tokens 创建令牌" >&2
    exit 1
  fi
}

get_current_branch() {
  git branch --show-current 2>/dev/null || echo ""
}

list_prs() {
  check_token
  local branch="${1:-$(get_current_branch)}"
  if [[ -z "$branch" ]]; then
    echo "[gitee-pr] 错误: 无法获取当前分支" >&2
    exit 1
  fi

  echo "[gitee-pr] 查询 PR (head: ${branch}, base: ${GITEE_BASE})..."
  local result
  result=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" \
    "${API}/pulls?state=open&head=${branch//\//%2F}&base=${GITEE_BASE}" 2>/dev/null)

  local count
  count=$(echo "$result" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")

  if [[ "$count" == "0" ]]; then
    echo "[gitee-pr] 未找到开放 PR，建议创建: $0 create"
    return 0
  fi

  echo "[gitee-pr] 找到 ${count} 个开放 PR:"
  echo "$result" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for pr in data:
    num = pr.get('number', '?')
    title = pr.get('title', '?')
    state = pr.get('state', '?')
    mergeable = pr.get('mergeable', '?')
    html_url = pr.get('html_url', '?')
    print(f'  #{num} [{state}] {title}')
    print(f'        mergeable={mergeable} URL={html_url}')
"
}

create_pr() {
  check_token
  local branch="${1:-$(get_current_branch)}"
  if [[ -z "$branch" ]]; then
    echo "[gitee-pr] 错误: 无法获取当前分支" >&2
    exit 1
  fi

  # 检查分支是否已推送
  local remote
  remote=$(git config "branch.${branch}.remote" 2>/dev/null || echo "origin")
  if ! git ls-remote --heads "$remote" "$branch" 2>/dev/null | grep -q .; then
    echo "[gitee-pr] ⚠ 分支 '${branch}' 尚未推送到远端" >&2
    echo "[gitee-pr]   建议: git push -u origin ${branch}" >&2
    exit 1
  fi

  # 检查是否已有开放 PR
  local existing
  existing=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" \
    "${API}/pulls?state=open&head=${branch//\//%2F}&base=${GITEE_BASE}" 2>/dev/null)

  local count
  count=$(echo "$existing" | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data))" 2>/dev/null || echo "0")

  if [[ "$count" != "0" ]]; then
    local pr_num
    pr_num=$(echo "$existing" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['number'])" 2>/dev/null)
    echo "[gitee-pr] ⚠ 已存在 PR #${pr_num}，跳过创建" >&2
    return 0
  fi

  echo "[gitee-pr] 创建 PR: ${branch} → ${GITEE_BASE}..."
  local body
  body=$(cat <<BODY
{
  "title": "$(echo "${branch}" | sed 's|.*/||'): Automation skill-progression-map update",
  "head": "${branch}",
  "base": "${GITEE_BASE}",
  "description": "Automated PR by gitee-pr-helper.sh",
  "prune_source_branch": true
}
BODY
)

  local result
  result=$(curl -s -X POST -H "Authorization: Bearer ${GITEE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$body" "${API}/pulls" 2>/dev/null)

  local pr_num
  pr_num=$(echo "$result" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('number','?'))" 2>/dev/null || echo "?")

  if [[ "$pr_num" != "?" ]]; then
    echo "[gitee-pr] ✅ PR #${pr_num} 创建成功"
    echo "$result" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'  URL: {d.get(\"html_url\",\"?\")}')"
  else
    echo "[gitee-pr] ❌ 创建失败:" >&2
    echo "$result" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, indent=2, ensure_ascii=False))" 2>/dev/null || echo "$result" >&2
    exit 1
  fi
}

check_pr() {
  check_token
  local pr_num="${1:-}"
  if [[ -z "$pr_num" ]]; then
    echo "[gitee-pr] 错误: 需要 PR 编号" >&2
    usage
  fi

  echo "[gitee-pr] 检查 PR #${pr_num} 状态..."
  local result
  result=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" \
    "${API}/pulls/${pr_num}" 2>/dev/null)

  echo "$result" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print(f'  Title:     {d.get(\"title\",\"?\")}')
print(f'  State:     {d.get(\"state\",\"?\")}')
print(f'  Mergeable: {d.get(\"mergeable\",\"?\")}')
print(f'  Head:      {d.get(\"head\",{}).get(\"label\",\"?\")}')
print(f'  Base:      {d.get(\"base\",{}).get(\"label\",\"?\")}')
print(f'  Comments:  {d.get(\"comments\",0)}')
print(f'  URL:       {d.get(\"html_url\",\"?\")}')
"
}

check_approve() {
  check_token
  local pr_num="${1:-}"
  if [[ -z "$pr_num" ]]; then
    echo "[gitee-pr] 错误: 需要 PR 编号" >&2
    usage
  fi

  echo "[gitee-pr] 检查 PR #${pr_num} 审核状态..."
  local result
  result=$(curl -s -H "Authorization: Bearer ${GITEE_TOKEN}" \
    "${API}/pulls/${pr_num}/reviews" 2>/dev/null)

  local reviews
  reviews=$(echo "$result" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if isinstance(data, list):
    for r in data:
        user = r.get('user', {}).get('login', '?')
        state = r.get('state', '?')
        submitted = r.get('submitted_at', '?')
        print(f'  {user}: {state} ({submitted})')
    total_approved = sum(1 for r in data if r.get('state') == 'approved')
    print(f'  ---')
    print(f'  Approved: {total_approved}')
else:
    print(f'  (no reviews)')
" 2>/dev/null || echo "  (failed to parse review data)")

  echo "$reviews"
}

merge_pr() {
  check_token
  local pr_num="${1:-}"
  if [[ -z "$pr_num" ]]; then
    echo "[gitee-pr] 错误: 需要 PR 编号" >&2
    usage
  fi

  echo "[gitee-pr] 合并 PR #${pr_num} (squash)..."

  # Gitee API 合并：PUT /repos/{owner}/{repo}/pulls/{number}/merge
  # merge_type: 'merge' | 'squash' | 'rebase'
  local body='{"merge_type":"squash","remove_source_branch":true,"title":"auto-merge by gitee-pr-helper.sh"}'

  local result
  result=$(curl -s -X PUT -H "Authorization: Bearer ${GITEE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$body" "${API}/pulls/${pr_num}/merge" 2>/dev/null)

  local status
  status=$(echo "$result" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('title','?'))" 2>/dev/null || echo "error")

  if [[ "$status" != "error" ]]; then
    echo "[gitee-pr] ✅ PR #${pr_num} 合并成功"
    echo "$result" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, indent=2, ensure_ascii=False))" 2>/dev/null
  else
    echo "[gitee-pr] ❌ 合并失败:" >&2
    echo "$result" | python3 -c "import sys,json; d=json.load(sys.stdin); print(json.dumps(d, indent=2, ensure_ascii=False))" 2>/dev/null || echo "$result" >&2
    exit 1
  fi
}

# --- main ---
SUBCOMMAND="${1:-}"
shift 1 2>/dev/null || true

case "$SUBCOMMAND" in
  list)
    list_prs "${1:-}"
    ;;
  create)
    create_pr "${1:-}"
    ;;
  check)
    check_pr "${1:-}"
    ;;
  approve)
    check_approve "${1:-}"
    ;;
  merge)
    merge_pr "${1:-}"
    ;;
  *)
    if [[ -n "$SUBCOMMAND" ]]; then
      echo "[gitee-pr] 未知子命令: $SUBCOMMAND" >&2
    fi
    usage
    ;;
esac
