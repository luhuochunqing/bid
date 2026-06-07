#!/usr/bin/env bash
# ==============================================================================
# 统一创建 PR 脚本 — 唯一合法路径
# 所有 Agent 必须通过此脚本创建 PR，禁止手动 gh pr create 或直接 git push。
# 当前适配 Gitee，后续可扩展 GitHub。
#
# 用法:
#   ./scripts/pr-create.sh <title> [body_file]
#   echo "PR body" | ./scripts/pr-create.sh "feat: 标题"
#
# body_file 可选，不传则从 stdin 读取。
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

remote_url="$(git remote get-url origin)"
branch="$(git branch --show-current)"

case "$remote_url" in
  *gitee.com*)
    GITEE_TOKEN="$("$SCRIPT_DIR/gitee-token.sh" 2>/dev/null)" || GITEE_TOKEN=""
    if [[ -z "${GITEE_TOKEN:-}" ]]; then
      echo ""
      echo "╔══════════════════════════════════════════════════════════════╗"
      echo "║  ❌ 无法获取 Gitee Token                                  ║"
      echo "║                                                          ║"
      echo "║  设置方式:                                                ║"
      echo "║    1. git credential store（推荐）                        ║"
      echo "║       echo 'https://user:token@gitee.com' >> ~/.git-credentials  ║"
      echo "║                                                          ║"
      echo "║    2. 环境变量:                                           ║"
      echo "║       export GITEE_TOKEN='your_token'                    ║"
      echo "║                                                          ║"
      echo "║  申请 Token: https://gitee.com/profile/personal_tokens    ║"
      echo "╚══════════════════════════════════════════════════════════════╝"
      echo ""
      exit 1
    fi
    ;;
  *github.com*)
    if ! gh auth status &>/dev/null; then
      echo "❌ GitHub CLI (gh) 未登录，请执行 gh auth login" >&2
      exit 1
    fi
    ;;
  *)
    echo "❌ 不支持的远端: $remote_url" >&2
    exit 1
    ;;
esac

title="${1:?用法: pr-create.sh <title> [body_file]}"
body_file="${2:-}"

if [[ -n "$body_file" ]]; then
  body="$(cat "$body_file")"
else
  body="$(cat)"
fi

if [[ "$body" != *"X-Created-By: pr-create-script"* ]]; then
  body="${body}
<!-- X-Created-By: pr-create-script -->"
fi

case "$remote_url" in
  *gitee.com*)
    repo_path="$(echo "$remote_url" | sed -n 's|.*gitee.com[:/]\(.*\)\.git|\1|p')"
    json_payload="$(
      GITEE_TOKEN="$GITEE_TOKEN" \
      GIT_PR_TITLE="$title" \
      GIT_PR_BRANCH="$branch" \
      GIT_PR_BODY="$body" \
      python3 -c '
import json, os
payload = {
    "access_token": os.environ["GITEE_TOKEN"],
    "title": os.environ["GIT_PR_TITLE"],
    "head": os.environ["GIT_PR_BRANCH"],
    "base": "main",
    "body": os.environ["GIT_PR_BODY"],
}
print(json.dumps(payload))
'
    )"
    curl -s -X POST "https://gitee.com/api/v5/repos/${repo_path}/pulls" \
      -H "Content-Type: application/json;charset=UTF-8" \
      -d "$json_payload" | python3 -c "
import sys, json
d = json.load(sys.stdin)
print('PR #' + str(d.get('number','?')) + ': ' + d.get('html_url','?'))
"
    ;;
  *github.com*)
    gh pr create --base main --head "$branch" --title "$title" --body "$body"
    ;;
esac
