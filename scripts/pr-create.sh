#!/usr/bin/env bash
# 统一创建 PR 脚本，自动适配 GitHub / Gitee。
# 用法: ./scripts/pr-create.sh <title> [body_file]
# body_file 可选，不传则从 stdin 读取。

set -euo pipefail

title="${1:?用法: pr-create.sh <title> [body_file]}"
body_file="${2:-}"

if [[ -n "$body_file" ]]; then
  body="$(cat "$body_file")"
else
  body="$(cat)"
fi

remote_url="$(git remote get-url origin)"
branch="$(git branch --show-current)"

case "$remote_url" in
  *github.com*)
    gh pr create --base main --head "$branch" --title "$title" --body "$body"
    ;;
  *gitee.com*)
    if [[ -z "${GITEE_TOKEN:-}" ]]; then
      echo "ERROR: GITEE_TOKEN 未设置" >&2
      exit 1
    fi
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
  *)
    echo "ERROR: 不支持的远端: $remote_url" >&2
    exit 1
    ;;
esac
