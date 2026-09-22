#!/usr/bin/env bash
# Input: 被调用方（run-stress-test.sh / capture-monitoring.sh）的命令行位置参数 "$@"
# Output: 带值选项缺值/空值、以及**监控组件集合非法或归一化后为空**时的稳定用法错误（打印选项名并以 2 退出）；
#         参数齐备时静默返回（组件集合写成"选项值 → 目标变量"）
# Pos: scripts/performance/ - 两个入口脚本共用的命令行参数与组件集合守卫
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 前置：本文件的两个函数都依赖 `MONITOR_COMPONENTS`（组件白名单，来自 monitor-spec.sh），
# 因此入口必须先 source monitor-spec.sh 再 source 本文件。
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

# 监控组件集合的入口判据：白名单 + 归一化 + **归一化后不得为空**。
# 用法（带值选项的 case 分支里，紧跟 require_arg 之后）：
#   --required-components) require_arg "$@"; require_component_set "$1" "$2" REQUIRED_COMPONENTS; shift 2 ;;
# 第 4 个参数是可选的补充提示，只用在"归一化后为空"那条上（两个入口能给的下一步动作不同）。
#
# 为什么把它从两个入口里提出来：这段判据原先在 run-stress-test 与 capture-monitoring 各有一份，
# 于是"空白集合"这条只长在其中一份上——另一份收到 `--required-components '   '`（纯空白，或
# `','`、`, ,`）时 `for _c in $(...)` 一个词都切不出来，循环一次都不跑，集合从"声明了一个非法
# 集合"悄悄变成**空集**。空集让结论层的判据真空成立（"每个必需组件都至少有一行有效数据"在没有
# 必需组件时为真），采集器于是以 0 退出、留下一份四个组件全是 0 行有效采样的 monitoring/：
# 文件都在、退出码成功、一行有效数据都没有。两份判据合并到一处以后，缺一条就是两个入口一起缺，
# 不会再出现"只补了一处"的漂移。
#
# 结果写进调用方指定的变量（而不是 echo 出去）：`REQ="$(require_component_set …)"` 会让
# `exit 2` 只结束那个命令替换的子 shell，外层脚本拿着空值继续往下跑——正是本目录一直在防的
# "看起来走通了、其实没校验"。这里直接 exit，与 require_arg 同一口径。
require_component_set() {
  local _opt="${1:-<未知选项>}" _raw="${2:-}" _dst="${3:?require_component_set: 缺少结果变量名}" _hint="${4:-}"
  local _c _norm=""
  for _c in $(printf '%s' "$_raw" | tr ',' ' '); do
    case " ${MONITOR_COMPONENTS} " in
      *" ${_c} "*) ;;
      *)
        echo "$(basename "${0}"): ${_opt} 只支持 ${MONITOR_COMPONENTS// /|}（收到 '${_c}'）" >&2
        exit 2
        ;;
    esac
    _norm="${_norm} ${_c}"
  done
  if [[ -z "${_norm// /}" ]]; then
    echo "$(basename "${0}"): ${_opt} 归一化后为空（收到 '${_raw}'），至少要声明一个必需组件${_hint:+；${_hint}}" >&2
    exit 2
  fi
  printf -v "$_dst" '%s' "${_norm# }"
}
