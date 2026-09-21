#!/usr/bin/env bash
# Input: 被调用方（run-stress-test.sh / capture-monitoring.sh）的命令行位置参数 "$@"
# Output: 带值选项缺值/空值时的稳定用法错误（打印选项名并以 2 退出）；参数齐备时静默返回
# Pos: scripts/performance/ - 两个入口脚本共用的命令行参数守卫
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一个文件：两个入口都是 `--opt) VAR="$2"; shift 2` 的形式，在 `set -u` 下
# 选项后面没跟值时，`$2` 直接展开会打印 `$2: unbound variable` 并**以 1 中止**——
# 而两个脚本的头部注释都把"参数错误"定义为 exit 2。调用方与 CI 因此分不清"用法错误"
# 与"拒绝执行（环境未确认 / 命中生产特征）"这两件完全不同的事，拿到的还是一句 shell
# 内部异常而不是用法说明。实测：`bash scripts/performance/run-stress-test.sh --profile`
# 与 `capture-monitoring.sh --interval` 都是 exit 1 + `$2: unbound variable`。
#
# 用法（每个带值选项的第一行，必须在 shift 之前调用）：
#   --profile) require_arg "$@"; PROFILE="$2"; shift 2 ;;
# `$@` 的第一位就是选项名本身，require_arg 只读它和第二位。
require_arg() {
  local _opt="${1:-<未知选项>}"
  if [[ $# -lt 2 ]] || [[ -z "${2:-}" ]]; then
    echo "$(basename "${0}"): 选项 ${_opt} 缺少参数值（用法见 --help）" >&2
    exit 2
  fi
}
