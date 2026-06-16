#!/usr/bin/env bash
# Input: GitHub/Gitee remote, current branch
# Output: authenticated PR creation via gh or gitee API, prints PR URL
# Pos: scripts/ - GitHub/Gitee PR creation entry point
# 维护声明:
#   - 维护人: [your-name]
#   - 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# ==============================================================================
# 统一创建 PR 脚本 — 唯一合法路径
# 所有 Agent 必须通过此脚本创建 PR，禁止手动 gh pr create 或直接 git push。
# 自动适配 GitHub / Gitee。
#
# 用法: ./scripts/pr-create.sh <title> [body_file]
# body_file 可选，不传则从 stdin 读取。
# ==============================================================================

set -euo pipefail

# ── 前置检测：远端类型 + 凭证 ──────────────────────────────────────────────
remote_url="$(git remote get-url origin)"
branch="$(git branch --show-current)"

case "$remote_url" in
  *github.com*)
    if ! gh auth status &>/dev/null; then
      echo ""
      echo "╔══════════════════════════════════════════════════════════════╗"
      echo "║  ❌ GitHub CLI (gh) 未登录                                ║"
      echo "║                                                          ║"
      echo "║  请先执行: gh auth login                                  ║"
      echo "║  或设置:   export GH_TOKEN=ghp_xxx                        ║"
      echo "╚══════════════════════════════════════════════════════════════╝"
      echo ""
      exit 1
    fi
    ;;
  *gitee.com*)
    # Fallback: 如果 bash 环境中 GITEE_TOKEN 未设置，尝试从 zshenv 读取
    # 背景：用户在 ~/.zshenv 中 export GITEE_TOKEN，但 bash 不读 .zshenv
    if [[ -z "${GITEE_TOKEN:-}" && -f "${HOME}/.zshenv" ]]; then
      _zshenv_token="$(grep -E '^export[[:space:]]+GITEE_TOKEN=' "${HOME}/.zshenv" | head -1 | sed 's/^export[[:space:]]*GITEE_TOKEN=//' | tr -d "\"'" )"
      if [[ -n "$_zshenv_token" ]]; then
        GITEE_TOKEN="$_zshenv_token"
        export GITEE_TOKEN
      fi
      unset _zshenv_token
    fi
    if [[ -z "${GITEE_TOKEN:-}" ]]; then
      echo ""
      echo "╔══════════════════════════════════════════════════════════════╗"
      echo "║  ❌ GITEE_TOKEN 未设置                                    ║"
      echo "║                                                          ║"
      echo "║  当前远端是 Gitee，必须设置 GITEE_TOKEN 才能创建 PR。     ║"
      echo "║                                                          ║"
      echo "║  设置方式:                                                ║"
      echo "║    export GITEE_TOKEN='your_personal_access_token'        ║"
      echo "║    或写入 ~/.zshenv:  export GITEE_TOKEN='...'            ║"
      echo "║    或写入 ~/.bashrc:  export GITEE_TOKEN='...'            ║"
      echo "║                                                          ║"
      echo "║  申请 Token: https://gitee.com/profile/personal_tokens    ║"
      echo "╚══════════════════════════════════════════════════════════════╝"
      echo ""
      exit 1
    fi
    ;;
  *)
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  ❌ 不支持的远端类型                                       ║"
    echo "║                                                          ║"
    echo "║  当前 remote origin: $remote_url"
    echo "║  仅支持 github.com 和 gitee.com。                         ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    exit 1
    ;;
esac

# ── 读取输入 ──────────────────────────────────────────────────────────────
title="${1:?用法: pr-create.sh <title> [body_file]}"
body_file="${2:-}"

if [[ -n "$body_file" ]]; then
  body="$(cat "$body_file")"
else
  body="$(cat)"
fi

# 注入来源标记，供 CI 门禁识别是否由本脚本创建
if [[ "$body" != *"X-Created-By: pr-create-script"* ]]; then
  body="${body}
<!-- X-Created-By: pr-create-script -->"
fi

# ── 创建 PR ───────────────────────────────────────────────────────────────
case "$remote_url" in
  *github.com*)
    gh pr create --base main --head "$branch" --title "$title" --body "$body"
    ;;
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
esac
