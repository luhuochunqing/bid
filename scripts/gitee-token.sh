#!/usr/bin/env bash
# Input: git credential fill + GITEE_TOKEN env + macOS Keychain
# Output: Gitee Personal Access Token to stdout (and exports GITEE_TOKEN)
# Pos: scripts/ - Gitee PAT retrieval helper for agent PR/merge workflows
# 维护声明: 仅维护 Gitee Personal Access Token 解析逻辑；调整优先级或新增来源时同步 SOP。
# scripts/gitee-token.sh — 获取 Gitee Personal Access Token。
#
# 优先级:
#   1. git credential fill（~/.git-credentials / macOS Keychain）
#   2. $GITEE_TOKEN 环境变量
#
# 用法:
#   source scripts/gitee-token.sh
#   token="$(get_gitee_token)" || echo "获取失败"
#
#      或直接执行:
#   ./scripts/gitee-token.sh

get_gitee_token() {
  local token
  token="$(echo -e "protocol=https\nhost=gitee.com" | git credential fill 2>/dev/null | \
    sed -n 's/^password=//p' | head -1)"
  if [[ -n "$token" ]]; then
    echo "$token"
    return 0
  fi
  if [[ -n "${GITEE_TOKEN:-}" ]]; then
    echo "$GITEE_TOKEN"
    return 0
  fi
  return 1
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if token="$(get_gitee_token)"; then
    echo "$token"
  else
    echo "无法获取 Gitee Token。请设置: export GITEE_TOKEN='your_token'" >&2
    exit 1
  fi
fi
