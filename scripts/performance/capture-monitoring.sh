#!/usr/bin/env bash
# Input: 应用宿主上的 java 进程、MySQL 8.0 与 Redis 连接参数、采样间隔与时长、必需组件清单
# Output: monitoring/ 目录下的 system/app-jvm/mysql/redis 四份 CSV 时间序列 + component-status.csv + monitoring.log 可读快照
# Pos: scripts/performance/ - 压测期间系统资源与中间件监控采集器的**入口与生命周期**（契约/解析/状态三层见下方文件清单）
# 维护声明: 若压测新增被监控组件（如 Nginx/Sidecar），同步更新 monitor-readers.sh 的采集块、
#           monitor-spec.sh 的组件集合与表头、以及 scripts/performance/README.md。
#
# 用途：在 k6 压测运行期间持续采样，产出压测报告"系统资源与中间件稳定性分析"章节的原始数据。
# 调用：由 run-stress-test.sh 自动拉起；也可单独运行做长稳监控。
#
# 文件分工（每个文件都可在无服务环境下单独检查）：
#   monitor-spec.sh     组件集合 / CSV 表头 / 有效行判定列号 —— 与 runner 共用的唯一契约源
#   monitor-parse.sh    纯解析层：原始文本 → 数值，`--self-test` 用合成输入自检（无需服务）
#   monitor-readers.sh  四个读取器：调命令 → 解析 → 写行 → 累加状态
#   monitor-status.sh   状态与结论层：组件状态的唯一持有者、component-status.csv、退出码判定
#   cli-guards.sh       带值选项缺值时的稳定用法错误（exit 2）
#
# 示例：
#   bash scripts/performance/capture-monitoring.sh \
#     --out docs/testing/stress-test-artifacts/20260921T110000Z-standard \
#     --app-port 18089 --interval 5 --duration 300 \
#     --required-components system,app-jvm,mysql,redis
#
# 应用进程定位顺序：--app-pid > --app-port（lsof 反查监听端口）> pgrep 特征兜底。
# 同机存在多个 java 进程时务必传 --app-port 或 --app-pid，否则可能采到别的进程。
# 指定了 --app-port 却查不到监听进程时**不退回 pgrep**（那会静默采到另一个 JVM），
# 而是把 app-jvm 记为未采集并让退出码报出来。
#
# 停止：--duration 到期自动退出，或 `touch <out>/monitoring/.stop` 立即结束采样。
#
# --required-components 单独运行时默认只要求 system：拿它做纯资源长稳监控时，目标机上
# 未必有 MySQL 客户端或 Redis，缺组件不该把一次资源监控判成失败。run-stress-test.sh
# 拉起它时会自动传入自己声称的那套（默认 system,app-jvm,mysql,redis），要求更严。
#
# 退出码：
#   0  采样结束，且 required-components 里每个组件都至少有一行**有效**数据
#   1  规格自检失败（monitor-spec.sh 的表头与列号不自洽）
#   2  用法错误（缺 --out、非法数值入参、未知组件名、缺 MYSQL_PASSWORD、选项缺值）
#   3  采样结束，但某个必需组件一行有效数据都没有（调用方必须据此判证据不完整）

set -uo pipefail

# 固定 C locale：逗号小数 locale（de_DE 等）下 `uptime` 会输出
# "load averages: 82,26 26,31 24,44"（解析成 82 / 26），`top` 会输出
# "2,5% user, 5,0% sys"（逗号混进 CSV 导致整行列错位）。压测机 locale 不可控，
# 这里必须自己钉死，且必须在任何解析之前。
export LC_ALL=C
export LANG=C

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/monitor-spec.sh"
if ! monitor_assert_spec; then
  echo "capture-monitoring: 监控规格自检失败（${SCRIPT_DIR}/monitor-spec.sh），拒绝执行" >&2
  exit 1
fi
source "${SCRIPT_DIR}/cli-guards.sh"
source "${SCRIPT_DIR}/monitor-parse.sh"
source "${SCRIPT_DIR}/monitor-status.sh"
source "${SCRIPT_DIR}/monitor-readers.sh"

OUT_DIR=""
INTERVAL=5
DURATION=0
APP_PID=""
APP_PORT=""
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-xiyu_user}"
# 这里**不提供默认口令**。原实现内置了本地 dev 口令做兜底，方便但把一份可用凭证
# 提交进了仓库；"它是本地默认值"不等于它不该出现在新增源码里。改为一律要求显式提供
# （本地 dev 口令见 backend/start.sh 与 CLAUDE.md，由操作者从环境中注入）。
MYSQL_PASSWORD="${MYSQL_PASSWORD:-}"
MYSQL_DB="${MYSQL_DB:-xiyu_bid_main}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DB="${REDIS_DB:-0}"
# 哪些组件必须采到数据才算成功。默认只要求 system：单独运行本脚本的人可能只想看 CPU/内存，
# 不该因为同机没装 mysql 客户端就判定失败。run-stress-test.sh 会显式传全四个 ——
# "证据包声称监控完整"这件事由调用方声明，不由采集器替它假定。
REQUIRED_COMPONENTS="system"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out) require_arg "$@"; OUT_DIR="$2"; shift 2 ;;
    --interval) require_arg "$@"; INTERVAL="$2"; shift 2 ;;
    --duration) require_arg "$@"; DURATION="$2"; shift 2 ;;
    --app-pid) require_arg "$@"; APP_PID="$2"; shift 2 ;;
    --app-port) require_arg "$@"; APP_PORT="$2"; shift 2 ;;
    --mysql-host) require_arg "$@"; MYSQL_HOST="$2"; shift 2 ;;
    --mysql-port) require_arg "$@"; MYSQL_PORT="$2"; shift 2 ;;
    --mysql-user) require_arg "$@"; MYSQL_USER="$2"; shift 2 ;;
    --mysql-db) require_arg "$@"; MYSQL_DB="$2"; shift 2 ;;
    --redis-host) require_arg "$@"; REDIS_HOST="$2"; shift 2 ;;
    --redis-port) require_arg "$@"; REDIS_PORT="$2"; shift 2 ;;
    --required-components) require_arg "$@"; REQUIRED_COMPONENTS="$2"; shift 2 ;;
    -h|--help) sed -n '2,39p' "$0"; exit 0 ;;
    *) echo "capture-monitoring: 未知参数 $1" >&2; exit 2 ;;
  esac
done

if [[ -z "$OUT_DIR" ]]; then
  echo "capture-monitoring: 必须通过 --out 指定输出目录" >&2
  exit 2
fi

# 数值入参必须在这里一次性校验。它们会参与 `[[ -gt ]]` 算术求值，而 macOS 自带的是
# bash 3.2：在 `set -u` 下对非数值操作数会打印 "abc: unbound variable" 并**中止脚本，
# 却以 0 退出**——采到一半的证据包被截断，调用方看到的却是"成功"。这正是本脚本
# 其余部分一直在防的那类失败（静默缺数据 + 成功退出码），不能在入口处漏掉。
for _v in "$INTERVAL" "$DURATION"; do
  if [[ ! "$_v" =~ ^[0-9]+$ ]]; then
    echo "capture-monitoring: --interval/--duration 必须是非负整数（收到 '${_v}'）" >&2
    exit 2
  fi
done
unset _v
# interval=0 会让采样循环退化成忙等（sleep 0 不自旋让出），单独挡掉；
# duration=0 是合法语义，表示"不设时长上限，只等 .stop"。
if [[ "$INTERVAL" -lt 1 ]]; then
  echo "capture-monitoring: --interval 必须 ≥ 1 秒（收到 '${INTERVAL}'）" >&2
  exit 2
fi

# 组件名必须白名单校验：写错一个名字（system 写成 sys）时，下面的状态层会把它
# 当成"未知组件"永远判为未达标、或者更糟——被当成"没有这个要求"而静默放行。
# 需要的效果是入口即拒绝，而不是让一份缺组件的证据包以成功退出码交付出去。
# 白名单与集合定义来自 monitor-spec.sh，不在本文件另抄一份。
_NORMALIZED_REQUIRED=""
for _c in $(printf '%s' "$REQUIRED_COMPONENTS" | tr ',' ' '); do
  case " ${MONITOR_COMPONENTS} " in
    *" ${_c} "*) ;;
    *)
      echo "capture-monitoring: --required-components 只支持 ${MONITOR_COMPONENTS// /|}（收到 '${_c}'）" >&2
      exit 2
      ;;
  esac
  _NORMALIZED_REQUIRED="${_NORMALIZED_REQUIRED} ${_c}"
done
unset _c
REQUIRED_COMPONENTS="${_NORMALIZED_REQUIRED# }"
unset _NORMALIZED_REQUIRED

# MySQL 口令的校验**必须在组件集合确定之后**，且只在 mysql 属于必需组件时才要求：
# 头部注释与 README 都承诺"单独运行默认只要求 system，目标机没装 MySQL 也不该失败"，
# 而这里的守卫原先是无条件的——那条承诺的路径在采样开始前就 exit 2，等于不存在。
_NEEDS_MYSQL=0
for _c in $REQUIRED_COMPONENTS; do
  if [[ "$_c" == "mysql" ]]; then _NEEDS_MYSQL=1; fi
done
unset _c
if [[ "$_NEEDS_MYSQL" == "1" ]] && [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "capture-monitoring: 声明了 mysql 为必需组件，必须显式提供 MYSQL_PASSWORD（本脚本不内置任何默认口令）" >&2
  echo "                 本地 dev 口令见 backend/start.sh / CLAUDE.md，请从环境注入：" >&2
  echo "                 MYSQL_PASSWORD='<口令>' bash scripts/performance/capture-monitoring.sh ..." >&2
  echo "                 不需要 MySQL 时请收窄声明：--required-components system,app-jvm" >&2
  exit 2
fi
unset _NEEDS_MYSQL

monitor_state_init

MON_DIR="${OUT_DIR%/}/monitoring"
mkdir -p "$MON_DIR"
STOP_FILE="${MON_DIR}/.stop"
rm -f "$STOP_FILE"
# 就绪握手：调用方（run-stress-test.sh）等到这个标记出现才开始压测。
# 上面那行 rm 与调用方的 touch .stop 之间存在真实竞态 —— 采集器启动慢（后面还有 lsof、
# top、jcmd），而 k6 完全可能秒退（脚本异常 exit 107、目标不可达）。此时调用方在 rm 之前
# 就 touch 了 .stop，rm 随即把它删掉，采集器便永远等不到停止信号：它采满 900s 被强杀，
# component-status.csv 根本不会生成，调用方拿到的是"产物缺失"而不是"采集器没停"。
# 实测过这条路径（探针用秒退的 k6 替身，采集器被强杀，component-status.csv 未生成）。
touch "${MON_DIR}/.ready"

SYSTEM_CSV="${MON_DIR}/$(monitor_csv_for system)"
JVM_CSV="${MON_DIR}/$(monitor_csv_for app-jvm)"
MYSQL_CSV="${MON_DIR}/$(monitor_csv_for mysql)"
REDIS_CSV="${MON_DIR}/$(monitor_csv_for redis)"
HUMAN_LOG="${MON_DIR}/monitoring.log"

monitor_resolve_app_pid
monitor_probe_tools

# 表头一律从 monitor-spec.sh 取：runner 复算"有效采样行"用的列号就是按这些表头定的，
# 在这里另写一份字面量，改字段时两边只会"碰巧一致"（写多一列、写少一列都不会报错）。
monitor_header_for system  > "$SYSTEM_CSV"
monitor_header_for app-jvm > "$JVM_CSV"
monitor_header_for mysql   > "$MYSQL_CSV"
monitor_header_for redis   > "$REDIS_CSV"

{
  echo "capture-monitoring: 开始采样"
  echo "  out=${MON_DIR}"
  echo "  interval=${INTERVAL}s duration=${DURATION}s"
  echo "  required_components=${REQUIRED_COMPONENTS}"
  echo "  app_pid=${APP_PID:-<未找到>} app_port=${APP_PORT:-<未指定>}  jcmd=${MONITOR_JCMD_READY}  mysql=${MONITOR_MYSQL_READY}  redis-cli=${MONITOR_REDIS_READY}"
  [[ -n "$APP_PID_LOOKUP_FAILED" ]] && echo "  !! ${APP_PID_LOOKUP_FAILED}；按约束不退回 pgrep 兜底，app-jvm 记为未采集"
  [[ -n "$APP_PID_LOOKUP_FALLBACK" ]] && echo "  !! ${APP_PID_LOOKUP_FALLBACK}"
  echo "  mysql=${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}  redis=${REDIS_HOST}:${REDIS_PORT} db=${REDIS_DB}"
} | tee -a "$HUMAN_LOG"

START_EPOCH="$(date +%s)"
SAMPLE_COUNT=0
while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  # 四个采集器各自把可读摘要**追加**进 SUMMARY，日志行再按数组顺序拼接。
  # 不要退回「四个全局变量 + printf 按位取值」：那样新增第五个采集器时，漏改清空列表或
  # printf 的位序都不会报错，只会让日志列静默串行错位——与按返回行序取值是同一类错误。
  # 数组下"每个采集器恰好追加一次"由数量自然保证。
  SUMMARY=()
  read_system
  read_jvm
  read_mysql
  read_redis
  SAMPLE_COUNT=$((SAMPLE_COUNT + 1))
  SUMMARY_LINE=""
  for _s in "${SUMMARY[@]}"; do SUMMARY_LINE="${SUMMARY_LINE}${SUMMARY_LINE:+ | }${_s}"; done
  unset _s
  printf '[%s] #%d | %s\n' "$TS" "$SAMPLE_COUNT" "$SUMMARY_LINE" >> "$HUMAN_LOG"

  if [[ -f "$STOP_FILE" ]]; then
    echo "capture-monitoring: 收到停止信号，采样结束（共 ${SAMPLE_COUNT} 次）" | tee -a "$HUMAN_LOG"
    break
  fi
  if [[ "$DURATION" -gt 0 ]] && [[ $(( $(date +%s) - START_EPOCH )) -ge "$DURATION" ]]; then
    echo "capture-monitoring: 达到时长上限 ${DURATION}s，采样结束（共 ${SAMPLE_COUNT} 次）" | tee -a "$HUMAN_LOG"
    break
  fi
  sleep "$INTERVAL"
done

# 组件级结论（状态表 + 退出码）由 monitor-status.sh 给出：必需组件一行有效数据都没有 → 3。
# 那条路径下调用方必须判证据不完整，而不是看"脚本跑完了"就当成采到了监控。
STATUS_CSV="${MON_DIR}/component-status.csv"
monitor_verdict "$STATUS_CSV" "$SAMPLE_COUNT"
exit $?
