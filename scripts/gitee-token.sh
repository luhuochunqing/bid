#!/usr/bin/env bash
# scripts/gitee-token.sh — 从 git credential store 获取 Gitee 访问 Token。
#
# 优先级（依次尝试，取第一个成功的结果）:
#   1. git credential fill（依次尝试 store → osxkeychain → Keychain）
#   2. $GITEE_TOKEN 环境变量
#   3. 从 remote.origin.pushurl 提取嵌入式 Token（.git/config 内联）
#
# 用法:
#   source scripts/gitee-token.sh
#   token="$(get_gitee_token)" || echo "获取失败: $?"
#
# 输出: stdout 输出 Token 字符串，exit code 0 表示成功。
#       exit code 1 表示所有来源都获取失败。

get_gitee_token() {
  # 1) git credential fill
  local token
  token="$(echo -e "protocol=https\nhost=gitee.com" | git credential fill 2>/dev/null | \
    sed -n 's/^password=//p' | head -1)"
  if [[ -n "$token" ]]; then
    echo "$token"
    return 0
  fi

  # 2) 环境变量
  if [[ -n "${GITEE_TOKEN:-}" ]]; then
    echo "$GITEE_TOKEN"
    return 0
  fi

  # 3) 从 remote.origin.pushurl 提取
  local pushurl
  pushurl="$(git config remote.origin.pushurl 2>/dev/null || true)"
  if [[ -n "$pushurl" ]]; then
    token="$(echo "$pushurl" | sed -n 's|.*://[^:]*:\([^@]*\)@.*|\1|p')"
    if [[ -n "$token" ]]; then
      echo "$token"
      return 0
    fi
  fi

  return 1
}

# 直接执行时输出 token（方便在 shell 中测试）
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if token="$(get_gitee_token)"; then
    echo "$token"
  else
    echo "无法获取 Gitee Token，请检查 credential store 或设置 GITEE_TOKEN 环境变量。" >&2
    exit 1
  fi
fi
