#!/usr/bin/env bash
# Input: dev-env.sh 调用本 wrapper；通过 source 引入 session-gate.sh 的 trap cleanup
# Output: 在调用方 shell 启用 EXIT trap + 锚点分支检查 + session 互斥
# Pos: scripts/ - session-gate 的 source-mode 入口（被 dev-env.sh source）
# 维护声明: session-gate 主逻辑修改时同步更新本 wrapper 的 source 路径与 trap 注册。
# Wrapper: sources session-gate.sh in current shell to get trap cleanup
# Usage: source scripts/session-gate-wrapper.sh
# Should only be called from dev-env.sh

source "$(dirname "${BASH_SOURCE[0]}")/session-gate.sh"
