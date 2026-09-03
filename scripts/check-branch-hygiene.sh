#!/usr/bin/env bash
# Input: none (reads current git branch)
# Output: warning if branch has unpushed commits or no PR
# Pos: scripts/pre-commit 辅助 — 提醒开发者 push + 开 PR
# 维护声明: 非阻塞检查，仅输出提醒。依赖 gh CLI 查询 PR。
set -euo pipefail


BRANCH=$(git branch --show-current)

# Skip main and agent/*-init anchor branches — task branches (agent/codex/xxx) get reminders
if [[ "$BRANCH" == "main" || "$BRANCH" == "master" ]]; then
  exit 0
fi
REMOTE=$(git config "branch.${BRANCH}.remote" 2>/dev/null || echo "")
case "$BRANCH" in
  agent/*-init|integrate/baseline) exit 0 ;;
esac
REMOTE_EXISTS=""
if [[ -n "$REMOTE" ]]; then
  git ls-remote --heads "$REMOTE" "$BRANCH" 2>/dev/null | grep -q . && REMOTE_EXISTS="yes" || REMOTE_EXISTS=""
fi

# Check 1: unpushed commits
if [[ -z "$REMOTE_EXISTS" ]]; then
  echo "[branch-hygiene] ⚠ 分支 '$BRANCH' 尚未 push 到远端"
  echo "[branch-hygiene]   建议: git push -u origin $BRANCH"
  ISSUES=1
fi

# Check 2: no PR — try GitHub CLI first, then Gitee API
PR_NUM=""
if command -v gh &>/dev/null; then
  PR_NUM=$(gh pr list --head "$BRANCH" --state open --json number -q '.[0].number // ""' 2>/dev/null || echo "")
fi

# Fallback: Gitee API via gitee-pr-helper when gh not available or no PR found
# token 走 gitee-token.sh 统一解析链（lessons §215：agent 进程树多为 bash，不读 ~/.zshenv）
_REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -z "$PR_NUM" ]] && [[ -n "$_REPO_ROOT" ]] && [[ -f "${_REPO_ROOT}/scripts/gitee-pr-helper.sh" ]]; then
  if [[ -z "${GITEE_TOKEN:-}" ]] && [[ -f "${_REPO_ROOT}/scripts/gitee-token.sh" ]]; then
    GITEE_TOKEN="$(bash "${_REPO_ROOT}/scripts/gitee-token.sh" 2>/dev/null || true)"
  fi
  if [[ -n "${GITEE_TOKEN:-}" ]]; then
    PR_NUM=$(GITEE_TOKEN="$GITEE_TOKEN" bash "${_REPO_ROOT}/scripts/gitee-pr-helper.sh" list "$BRANCH" 2>/dev/null | grep -Eo '#[0-9]+' | head -1 | tr -d '#')
  fi
fi

if [[ -z "$PR_NUM" ]]; then
  if command -v gh &>/dev/null; then
    echo "[branch-hygiene] ⚠ 分支 '$BRANCH' 尚未创建 PR"
    echo "[branch-hygiene]   建议: gh pr create --base main --head $BRANCH"
  elif [[ -n "${GITEE_TOKEN:-}" ]]; then
    echo "[branch-hygiene] ⚠ 分支 '$BRANCH' 尚未创建 PR"
    echo "[branch-hygiene]   建议: GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh create $BRANCH"
  else
    echo "[branch-hygiene] ⚠ 分支 '$BRANCH' 尚未创建 PR"
    echo "[branch-hygiene]   提示: 未找到 GITEE_TOKEN（修复: 写入 ~/.config/xiyu/credentials.env 的 export GITEE_TOKEN='...'）"
    echo "[branch-hygiene]   建议: git push → Gitee UI 创建 PR"
  fi
  ISSUES=1
fi

if [[ -n "${ISSUES:-}" ]]; then
  echo "[branch-hygiene] 以上为提醒，不阻断提交。"
fi
exit 0
