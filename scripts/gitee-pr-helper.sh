#!/usr/bin/env bash
# Input: subcommand + branch name + optional GITEE_TOKEN
# Output: three-skill receipt guard for create;  PR info or merge result
# Pos: scripts/ — Gitee PR 操作辅助工具
# 维护声明: 依赖 curl + GITEE_TOKEN 环境变量。Gitee API v5。
#           契约测试: scripts/test/gitee-pr-helper-contract.sh（mock API，无需真实 token）
#
# 用法:
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh list          # 列出当前分支的 PR
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh create        # 创建 PR (当前分支→main)
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh check <num>   # 检查 PR 状态
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh merge <num>   # 合并 PR (squash)
#   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh approve <num> # 查看 PR 审核状态
#
# 环境变量:
#   GITEE_TOKEN     — Gitee 个人访问令牌（推荐显式注入；未设置时自动走 gitee-token.sh 解析链:
#                     env → ~/.config/xiyu/credentials.env → ~/.zshenv → git credential fill）
#   GITEE_OWNER     — 仓库所有者（默认: allinai888）
#   GITEE_REPO      — 仓库名（默认: bid-xiyu）
#   GITEE_BASE_BRANCH — 目标分支（默认: main）
#   GITEE_API_BASE  — API 前缀（默认: https://gitee.com/api/v5；仅测试/mock 覆盖）
#   GIT_PR_TITLE    — 可选，覆盖默认从 commit message 提取的 PR 标题（与 pr-create.sh 命名对齐）
#   GIT_PR_BODY     — 可选，覆盖默认从 commit body 提取的 PR 描述（与 pr-create.sh 命名对齐）
#
# 变更历史:
#   2026-08-09 修复 Gitee API v5 字段名 bug：description → body（原 bug 导致创建 PR 时描述为空）
#              新增 GIT_PR_TITLE / GIT_PR_BODY 环境变量，与 scripts/pr-create.sh 命名对齐
#   2026-08-25 merge 子命令新增 mergeable 强制断言（PR !2399 教训：四轮审查未查
#              mergeable，合入时才发现冲突）。逃生阀：GITEE_PR_FORCE_MERGE=1
#   2026-09-03 修复 list 解析 bug：curl 响应此前不做 HTTP 状态码与 JSON 形状校验，
#              401 错误字典 {"message":...} 被 len() 计成"1 个开放 PR"（假阳性）后
#              在解析循环崩溃（AttributeError: 'str' object has no attribute 'get'）；
#              非 JSON 响应则静默计 0，把鉴权失败伪装成"无 PR"（假阴性）。
#              收敛为统一 gitee_get + require_json_response（状态码 + list/object 形状），
#              401/403 附带 token 提示；新增 GITEE_API_BASE 覆盖供契约测试 mock。
#   2026-09-03 token 不再要求"必须由环境注入"：GITEE_TOKEN 为空时自动调
#              gitee-token.sh 统一解析链（env → ~/.config/xiyu/credentials.env →
#              ~/.zshenv → git credential fill）。根因：agent 进程树多为 bash，
#              不读 ~/.zshenv，此前直接报"需要 GITEE_TOKEN 环境变量"，各 Agent
#              建 PR 时反复"找不到 token"（lessons §215）。
#   2026-10-10 merge 子命令非 2xx 时复核 PR 终态：Gitee 对已合并/已关闭 PR 的
#              重复合并返回 405（非幂等 2xx），此前仅凭状态码把已成功的合入报成
#              失败（2026-10-09 PR !2817 实证）。state=merged → 幂等放行 exit 0；
#              state=closed → 明确报"已关闭未合并" exit 1；其余维持原失败路径。
#              同分支修正 merge/create 失败路径的响应体透传缺陷（见下方行内注释）。
set -euo pipefail

GITEE_OWNER="${GITEE_OWNER:-allinai888}"
GITEE_REPO="${GITEE_REPO:-bid-xiyu}"
GITEE_BASE="${GITEE_BASE_BRANCH:-main}"
API_BASE="${GITEE_API_BASE:-https://gitee.com/api/v5}"
API="${API_BASE}/repos/${GITEE_OWNER}/${GITEE_REPO}"

usage() {
  echo "用法: $0 {list|create|check|merge|approve} [pr_number]"
  exit 1
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# token 自动兜底：环境未注入时走 gitee-token.sh 统一解析链。
# 为什么不能只靠环境变量：gitee-pr-helper 由 bash 执行，bash 不读 ~/.zshenv，
# 而 agent 进程树（bash -c / GUI / 清理过 env 的 harness）经常不带 GITEE_TOKEN。
resolve_token() {
  if [[ -z "${GITEE_TOKEN:-}" ]]; then
    GITEE_TOKEN="$(bash "${SCRIPT_DIR}/gitee-token.sh" 2>/dev/null || true)"
    if [[ -n "$GITEE_TOKEN" ]]; then
      export GITEE_TOKEN
    fi
  fi
}

check_token() {
  resolve_token
  if [[ -z "${GITEE_TOKEN:-}" ]]; then
    echo "[gitee-pr] 错误: 找不到可用的 GITEE_TOKEN（解析链: env → ~/.config/xiyu/credentials.env → ~/.zshenv → git credential fill）" >&2
    echo "[gitee-pr] 修复方式（推荐 1）:" >&2
    echo "[gitee-pr]   1. 写入 ~/.config/xiyu/credentials.env:  export GITEE_TOKEN='你的令牌'" >&2
    echo "[gitee-pr]   2. 或写入 ~/.zshenv:                     export GITEE_TOKEN='你的令牌'" >&2
    echo "[gitee-pr]   3. 或临时注入: GITEE_TOKEN=xxx $0 ${SUBCOMMAND:-list}" >&2
    echo "[gitee-pr] 申请令牌: https://gitee.com/profile/personal_access_tokens" >&2
    exit 1
  fi
}

get_current_branch() {
  git branch --show-current 2>/dev/null || echo ""
}

# 统一 GET：stdout 输出 HTTP 状态码，响应体落到 $2 指定的文件。
# 为什么走文件：多行/引号密集的 body 经 shell 变量接力再进 python 容易碎，
# 文件路径传参让 python 自己 json.load，形状校验收敛到 require_json_response。
gitee_get() {
  local url="$1" body_file="$2"
  curl -s -o "$body_file" -w "%{http_code}" \
    -H "Authorization: Bearer ${GITEE_TOKEN}" "$url" 2>/dev/null
}

# 校验响应：$1=状态码 $2=body 文件 $3=期望形状(list|object) $4=动作名（用于报错）。
# 非 2xx 或形状不符 → 输出可读错误并 exit 1；401/403 附带 token 提示。
# （根因背景：2026-09-03 修复前，401 错误字典被当成 1 条记录解析后崩溃。）
require_json_response() {
  local status="$1" body_file="$2" expect="$3" action="$4"
  if [[ ! "$status" =~ ^2 ]]; then
    echo "[gitee-pr] ❌ ${action} 失败 (HTTP ${status})" >&2
    local snippet
    snippet=$(head -c 300 "$body_file" 2>/dev/null || true)
    if [[ -n "$snippet" ]]; then
      echo "[gitee-pr] 响应: ${snippet}" >&2
    fi
    if [[ "$status" == "401" || "$status" == "403" ]]; then
      echo "[gitee-pr] 提示: GITEE_TOKEN 无效或已过期——更新令牌后重试（写入 ~/.config/xiyu/credentials.env 或 ~/.zshenv 的 export GITEE_TOKEN='...'）" >&2
    fi
    rm -f "$body_file"
    exit 1
  fi
  if ! python3 - "$body_file" "$expect" "$action" <<'PY'
import json, sys
path, expect, action = sys.argv[1], sys.argv[2], sys.argv[3]
try:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
except Exception:
    print(f"[gitee-pr] ❌ {action} 失败: 响应不是合法 JSON", file=sys.stderr)
    sys.exit(1)
ok = isinstance(data, list) if expect == "list" else isinstance(data, dict)
if not ok:
    shape = "JSON 数组" if expect == "list" else "JSON 对象"
    print(f"[gitee-pr] ❌ {action} 失败: 响应为 {type(data).__name__}，期望{shape}", file=sys.stderr)
    if isinstance(data, dict) and data.get("message"):
        print(f"[gitee-pr] API 消息: {data['message']}", file=sys.stderr)
    sys.exit(1)
PY
  then
    rm -f "$body_file"
    exit 1
  fi
}

list_prs() {
  check_token
  local branch="${1:-$(get_current_branch)}"
  if [[ -z "$branch" ]]; then
    echo "[gitee-pr] 错误: 无法获取当前分支" >&2
    exit 1
  fi

  echo "[gitee-pr] 查询 PR (head: ${branch}, base: ${GITEE_BASE})..."
  local body_file status
  body_file=$(mktemp)
  status=$(gitee_get "${API}/pulls?state=open&head=${branch//\//%2F}&base=${GITEE_BASE}" "$body_file" || echo "000")
  require_json_response "$status" "$body_file" list "查询 PR"

  local count
  count=$(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1]))))" "$body_file")

  if [[ "$count" == "0" ]]; then
    echo "[gitee-pr] 未找到开放 PR，建议创建: $0 create"
    rm -f "$body_file"
    return 0
  fi

  echo "[gitee-pr] 找到 ${count} 个开放 PR:"
  python3 - "$body_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    for pr in json.load(f):
        num = pr.get('number', '?')
        title = pr.get('title', '?')
        state = pr.get('state', '?')
        mergeable = pr.get('mergeable', '?')
        html_url = pr.get('html_url', '?')
        print(f'  #{num} [{state}] {title}')
        print(f'        mergeable={mergeable} URL={html_url}')
PY
  rm -f "$body_file"
}

create_pr() {
  check_token
  local branch="${1:-$(get_current_branch)}"
  if [[ -z "$branch" ]]; then
    echo "[gitee-pr] 错误: 无法获取当前分支" >&2
    exit 1
  fi

  local repo_root
  repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

  # 三技能必须全部通过；检查器缺失失败关闭，不接受用户级脚本降级。
  python3 "$repo_root/scripts/review-workflow.py" --project-dir "$repo_root" check --pr --branch "$branch"

  # 逃生阀：DUPLICATE_IMPL_SKIP=1
  local dup_checker="$repo_root/scripts/check-duplicate-implementations.mjs"
  if [ "${DUPLICATE_IMPL_SKIP:-0}" = "1" ]; then
    echo "[gitee-pr] ⚠ DUPLICATE_IMPL_SKIP=1 — 跳过重复实现检查"
  elif [ -f "$dup_checker" ]; then
    echo "[gitee-pr] 重复实现检查（提交 PR 自动执行）..."
    if ! node "$dup_checker"; then
      echo "[gitee-pr] ❌ 重复实现门禁未通过。请复用已有实现，或加 // REUSE-OK: <path> — <原因>" >&2
      echo "[gitee-pr] 逃生阀: DUPLICATE_IMPL_SKIP=1" >&2
      exit 1
    fi
  fi

  # 检查分支是否已推送
  local remote
  remote=$(git config "branch.${branch}.remote" 2>/dev/null || echo "origin")
  if ! git ls-remote --heads "$remote" "$branch" 2>/dev/null | grep -q .; then
    echo "[gitee-pr] ⚠ 分支 '${branch}' 尚未推送到远端" >&2
    echo "[gitee-pr]   建议: git push -u origin ${branch}" >&2
    exit 1
  fi

  # 检查是否已有开放 PR（与 list 相同的形状校验：401 字典曾把"鉴权失败"伪装成
  # "已存在 PR #"，诱导跳过创建）
  local existing_file status
  existing_file=$(mktemp)
  status=$(gitee_get "${API}/pulls?state=open&head=${branch//\//%2F}&base=${GITEE_BASE}" "$existing_file" || echo "000")
  require_json_response "$status" "$existing_file" list "查询已有 PR"

  local count
  count=$(python3 -c "import json,sys; print(len(json.load(open(sys.argv[1]))))" "$existing_file")

  if [[ "$count" != "0" ]]; then
    local pr_num
    pr_num=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1]))[0]['number'])" "$existing_file")
    echo "[gitee-pr] ⚠ 已存在 PR #${pr_num}，跳过创建" >&2
    rm -f "$existing_file"
    return 0
  fi
  rm -f "$existing_file"

  echo "[gitee-pr] 创建 PR: ${branch} → ${GITEE_BASE}..."

  # 从最新 commit message 提取标题（第一行）和描述（剩余部分）
  # commit message 格式示例：
  #   fix(CA): CO-515 修复 CA 详情页借用记录和操作日志 Tab 无信息
  #
  #   根因：...
  #   修复内容：...
  # 可用环境变量 GIT_PR_TITLE / GIT_PR_BODY 覆盖（与 scripts/pr-create.sh 命名对齐）
  # 最后一个 commit 是 docs/wiki 类辅助 commit 时尤其有用
  local commit_subject commit_body
  commit_subject=$(git log -1 --format='%s' 2>/dev/null || echo "")
  commit_body=$(git log -1 --format='%b' 2>/dev/null || echo "")

  # 标题：GIT_PR_TITLE > commit subject > 分支名
  if [[ -n "${GIT_PR_TITLE:-}" ]]; then
    pr_title="$GIT_PR_TITLE"
  elif [[ -n "$commit_subject" ]]; then
    pr_title="$commit_subject"
  else
    pr_title="${branch##*/}: update"
  fi

  # 描述：GIT_PR_BODY > commit body > 默认 fallback
  if [[ -n "${GIT_PR_BODY:-}" ]]; then
    commit_body="$GIT_PR_BODY"
  fi

  # 用 python3 构造 JSON（安全转义，避免双引号/换行破坏 JSON 结构）
  # 描述字段：Gitee API v5 的 PR 描述字段名是 'body'（不是 'description'）
  local payload
  payload=$(python3 -c "
import json, sys
title = sys.argv[1]
description = sys.argv[2] if sys.argv[2] else f'Automated PR by gitee-pr-helper.sh\n\nBranch: {sys.argv[3]}'
print(json.dumps({
    'title': title,
    'head': sys.argv[3],
    'base': sys.argv[4],
    'body': description,
    'prune_source_branch': True
}, ensure_ascii=False))
" "$pr_title" "$commit_body" "$branch" "$GITEE_BASE")

  local result_file create_status
  result_file=$(mktemp)
  create_status=$(curl -s -o "$result_file" -w "%{http_code}" -X POST \
    -H "Authorization: Bearer ${GITEE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$payload" "${API}/pulls" 2>/dev/null || echo "000")

  if [[ ! "$create_status" =~ ^20[01]$ ]]; then
    echo "[gitee-pr] ❌ 创建失败 (HTTP ${create_status})" >&2
    head -c 500 "$result_file" >&2; echo >&2
    # 既有缺陷修正：原 `2>/dev/null >&2` 顺序使 fd1 也指向 /dev/null，
    # 失败时的 API 响应体从未真正输出。与 merge_pr 失败路径同型缺陷。
    if [[ "$create_status" == "401" || "$create_status" == "403" ]]; then
      echo "[gitee-pr] 提示: GITEE_TOKEN 无效或已过期——更新令牌后重试（写入 ~/.config/xiyu/credentials.env 或 ~/.zshenv 的 export GITEE_TOKEN='...'）" >&2
    fi
    rm -f "$result_file"
    exit 1
  fi

  local pr_num
  pr_num=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1])).get('number','?'))" "$result_file")
  echo "[gitee-pr] ✅ PR #${pr_num} 创建成功"
  python3 -c "import json,sys; print(f\"  URL: {json.load(open(sys.argv[1])).get('html_url','?')}\")" "$result_file"
  rm -f "$result_file"
}

check_pr() {
  check_token
  local pr_num="${1:-}"
  if [[ -z "$pr_num" ]]; then
    echo "[gitee-pr] 错误: 需要 PR 编号" >&2
    usage
  fi

  echo "[gitee-pr] 检查 PR #${pr_num} 状态..."
  local body_file status
  body_file=$(mktemp)
  status=$(gitee_get "${API}/pulls/${pr_num}" "$body_file" || echo "000")
  require_json_response "$status" "$body_file" object "检查 PR #${pr_num}"

  python3 - "$body_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    d = json.load(f)
print(f'  Title:     {d.get("title","?")}')
print(f'  State:     {d.get("state","?")}')
print(f'  Mergeable: {d.get("mergeable","?")}')
print(f'  Head:      {d.get("head",{}).get("label","?")}')
print(f'  Base:      {d.get("base",{}).get("label","?")}')
print(f'  Comments:  {d.get("comments",0)}')
print(f'  URL:       {d.get("html_url","?")}')
PY
  rm -f "$body_file"
}

check_approve() {
  check_token
  local pr_num="${1:-}"
  if [[ -z "$pr_num" ]]; then
    echo "[gitee-pr] 错误: 需要 PR 编号" >&2
    usage
  fi

  echo "[gitee-pr] 检查 PR #${pr_num} 审核状态..."
  local body_file status
  body_file=$(mktemp)
  status=$(gitee_get "${API}/pulls/${pr_num}/reviews" "$body_file" || echo "000")
  require_json_response "$status" "$body_file" list "检查 PR #${pr_num} 审核状态"

  python3 - "$body_file" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)
for r in data:
    user = r.get('user', {}).get('login', '?')
    state = r.get('state', '?')
    submitted = r.get('submitted_at', '?')
    print(f'  {user}: {state} ({submitted})')
print('  ---')
print(f'  Approved: {sum(1 for r in data if r.get("state") == "approved")}')
PY
  rm -f "$body_file"
}

merge_pr() {
  check_token
  local pr_num="${1:-}"
  if [[ -z "$pr_num" ]]; then
    echo "[gitee-pr] 错误: 需要 PR 编号" >&2
    usage
  fi

  # 合入前强制断言 mergeable（PR !2399 教训：冲突在合入时才被发现）
  if [[ "${GITEE_PR_FORCE_MERGE:-0}" != "1" ]]; then
    local pr_file status mergeable
    pr_file=$(mktemp)
    status=$(gitee_get "${API}/pulls/${pr_num}" "$pr_file" || echo "000")
    require_json_response "$status" "$pr_file" object "检查 PR #${pr_num} 可合入状态"
    mergeable=$(python3 -c "import json,sys; print(str(json.load(open(sys.argv[1])).get('mergeable')).lower())" "$pr_file")
    rm -f "$pr_file"
    if [[ "$mergeable" != "true" ]]; then
      echo "[gitee-pr] ❌ PR #${pr_num} mergeable=${mergeable} — 合入阻断" >&2
      echo "[gitee-pr] 请先 rebase 解决冲突: git fetch origin && git rebase origin/main" >&2
      echo "[gitee-pr] 冲突定位: git merge-tree \$(git merge-base origin/main HEAD) origin/main HEAD" >&2
      echo "[gitee-pr] 逃生阀: GITEE_PR_FORCE_MERGE=1（慎用）" >&2
      exit 1
    fi
    echo "[gitee-pr] mergeable=true 断言通过"
  else
    echo "[gitee-pr] ⚠ GITEE_PR_FORCE_MERGE=1 — 跳过 mergeable 断言"
  fi

  echo "[gitee-pr] 合并 PR #${pr_num} (squash)..."

  # Gitee API 合并：PUT /repos/{owner}/{repo}/pulls/{number}/merge
  # merge_type: 'merge' | 'squash' | 'rebase'
  local body='{"merge_type":"squash","remove_source_branch":true,"title":"auto-merge by gitee-pr-helper.sh"}'

  local result_file merge_status
  result_file=$(mktemp)
  merge_status=$(curl -s -o "$result_file" -w "%{http_code}" -X PUT \
    -H "Authorization: Bearer ${GITEE_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$body" "${API}/pulls/${pr_num}/merge" 2>/dev/null || echo "000")

  if [[ ! "$merge_status" =~ ^2 ]]; then
    # Gitee 对「已合并/已关闭」的 PR 重复合并返回 405（非幂等 2xx），仅凭 HTTP 码
    # 会把已成功的合入报成失败（2026-10-09 PR !2817 实证）。以 PR 终态 state 消歧。
    local state_file state
    state_file=$(mktemp)
    state=""
    if [[ "$(gitee_get "${API}/pulls/${pr_num}" "$state_file" || echo 000)" =~ ^2 ]]; then
      state=$(python3 -c "import json,sys; print(json.load(open(sys.argv[1])).get('state') or '')" "$state_file" 2>/dev/null || echo "")
    fi
    rm -f "$state_file"
    if [[ "$state" == "merged" ]]; then
      echo "[gitee-pr] ✅ PR #${pr_num} 已合并（HTTP ${merge_status} 为重复合并响应，幂等放行）"
    else
      if [[ "$state" == "closed" ]]; then
        echo "[gitee-pr] ⚠ PR #${pr_num} 状态为 closed（已关闭但未合并）" >&2
      fi
      echo "[gitee-pr] ❌ 合并失败 (HTTP ${merge_status})" >&2
      head -c 500 "$result_file" >&2; echo >&2
      # 既有缺陷修正：原 `2>/dev/null >&2` 顺序使 fd1 也指向 /dev/null，
      # 失败时的 API 响应体从未真正输出（405 排障时看不到服务端消息的根因）。
    fi
    rm -f "$result_file"
    if [[ "$state" == "merged" ]]; then
      return 0
    fi
    exit 1
  fi

  echo "[gitee-pr] ✅ PR #${pr_num} 合并成功"
  if [[ -s "$result_file" ]]; then
    python3 -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1])), indent=2, ensure_ascii=False))" "$result_file" 2>/dev/null || true
  fi
  rm -f "$result_file"
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
