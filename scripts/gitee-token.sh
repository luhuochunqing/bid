#!/usr/bin/env bash
# Input: none（解析链自动探测）
# Output: Gitee Personal Access Token to stdout（并可通过 source 导出 GITEE_TOKEN）
# Pos: scripts/ — Gitee PAT 统一解析器（所有 Gitee API 脚本的唯一 token 入口）
# 维护声明: 仅维护 Gitee Personal Access Token 解析逻辑；调整优先级或新增来源时
#           同步更新 scripts/test/gitee-token-contract.sh。
#
# 解析优先级（高 → 低）:
#   1. $GITEE_TOKEN 环境变量          — 显式注入永远最优先（GITEE_TOKEN=xxx ./script 用法兼容）
#   2. ~/.config/xiyu/credentials.env — 推荐的唯一真相源（chmod 600，bash/zsh/GUI 通用）
#   3. ~/.zshenv 中的 export 行       — 历史兼容（bash 不读 zshenv，故靠本脚本 grep 兜底）
#   4. git credential fill            — 最后手段（~/.git-credentials / Keychain）
#
# 为什么 credential fill 排最后：
#   ~/.git-credentials 会积累多条历史条目（含过期 PAT），`git credential fill` 只返回
#   第一条匹配。2026-09-03 实测：本机存有 3 条 gitee 条目，fill 返回的是 401 死 token，
#   而环境变量里的 token 是 200 有效——旧版把 fill 排第一，导致"兜底工具自己兜了死底"。
#
# 用法:
#   # 在其他脚本中解析（推荐写法）:
#   [[ -z "${GITEE_TOKEN:-}" ]] && GITEE_TOKEN="$(bash "$(dirname "$0")/gitee-token.sh" 2>/dev/null || true)"
#
#   # 直接执行（stdout 输出 token，stderr 输出错误）:
#   ./scripts/gitee-token.sh || echo "获取失败"
#
#   # 调试（stderr 打印命中来源，不打印 token 值）:
#   GITEE_TOKEN_DEBUG=1 ./scripts/gitee-token.sh >/dev/null

# 解析 token 并回显到 stdout；失败时 return 1（source 场景）或 exit 1（直接执行场景）。
get_gitee_token() {
  local token="" source=""

  # 1. 环境变量
  if [[ -n "${GITEE_TOKEN:-}" ]]; then
    token="$GITEE_TOKEN"
    source="环境变量 \$GITEE_TOKEN"
  fi

  # 2. ~/.config/xiyu/credentials.env（唯一真相源推荐位）
  if [[ -z "$token" && -f "${HOME}/.config/xiyu/credentials.env" ]]; then
    token="$(grep -E '^[[:space:]]*(export[[:space:]]+)?GITEE_TOKEN=' "${HOME}/.config/xiyu/credentials.env" 2>/dev/null \
      | head -1 | sed -E 's/^[[:space:]]*(export[[:space:]]+)?GITEE_TOKEN=//' | tr -d "\"'")"
    [[ -n "$token" ]] && source="${HOME}/.config/xiyu/credentials.env"
  fi

  # 3. ~/.zshenv（历史兼容：bash 子进程不读它，这里手动 grep）
  if [[ -z "$token" && -f "${HOME}/.zshenv" ]]; then
    token="$(grep -E '^[[:space:]]*(export[[:space:]]+)?GITEE_TOKEN=' "${HOME}/.zshenv" 2>/dev/null \
      | head -1 | sed -E 's/^[[:space:]]*(export[[:space:]]+)?GITEE_TOKEN=//' | tr -d "\"'")"
    [[ -n "$token" ]] && source="${HOME}/.zshenv"
  fi

  # 4. git credential fill（最后手段，原因见文件头注释；禁用交互避免 agent 挂死）
  if [[ -z "$token" ]]; then
    token="$(printf 'protocol=https\nhost=gitee.com\n\n' \
      | GIT_TERMINAL_PROMPT=0 GIT_ASKPASS=/bin/true git credential fill 2>/dev/null \
      | sed -n 's/^password=//p' | head -1)"
    [[ -n "$token" ]] && source="git credential fill（~/.git-credentials / Keychain）"
  fi

  if [[ -z "$token" ]]; then
    return 1
  fi

  if [[ "${GITEE_TOKEN_DEBUG:-0}" == "1" ]]; then
    echo "[gitee-token] 命中来源: ${source}" >&2
  fi

  printf '%s' "$token"
}

# 直接执行：stdout 输出 token；失败输出修复指引并 exit 1。
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if token="$(get_gitee_token)"; then
    printf '%s\n' "$token"
  else
    {
      echo "无法获取 Gitee Token。解析链（按优先级）均未命中："
      echo "  1. \$GITEE_TOKEN 环境变量"
      echo "  2. ~/.config/xiyu/credentials.env 中的 export GITEE_TOKEN='...'"
      echo "  3. ~/.zshenv 中的 export GITEE_TOKEN='...'"
      echo "  4. git credential fill（~/.git-credentials / Keychain）"
      echo "修复方式（推荐 2）：mkdir -p ~/.config/xiyu && echo \"export GITEE_TOKEN='你的令牌'\" >> ~/.config/xiyu/credentials.env && chmod 600 ~/.config/xiyu/credentials.env"
      echo "申请令牌: https://gitee.com/profile/personal_access_tokens"
    } >&2
    exit 1
  fi
fi
