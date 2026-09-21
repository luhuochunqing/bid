#!/usr/bin/env bash
# Input: 应用宿主上的 java 进程、MySQL 8.0 与 Redis 连接参数、采样间隔与时长、必需组件清单
# Output: monitoring/ 目录下的 system/app-jvm/mysql/redis 四份 CSV 时间序列 + component-status.csv + monitoring.log 可读快照
# Pos: scripts/performance/ - 压测期间系统资源与中间件监控采集器（替代手工截图，产出可归档的原始监控数据）
# 维护声明: 若压测新增被监控组件（如 Nginx/Sidecar），同步更新本脚本的采集块、monitor-spec.sh 的组件集合与表头、以及 scripts/performance/README.md。
#
# 用途：在 k6 压测运行期间持续采样，产出压测报告"系统资源与中间件稳定性分析"章节的原始数据。
# 调用：由 run-stress-test.sh 自动拉起；也可单独运行做长稳监控。
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
#   2  用法错误（缺 --out、非法数值入参、未知组件名、缺 MYSQL_PASSWORD）
#   3  采样结束，但某个必需组件一行有效数据都没有（调用方必须据此判证据不完整）

set -uo pipefail

# 固定 C locale：逗号小数 locale（de_DE 等）下 `uptime` 会输出
# "load averages: 82,26 26,31 24,44"（解析成 82 / 26），`top` 会输出
# "2,5% user, 5,0% sys"（逗号混进 CSV 导致整行列错位）。压测机 locale 不可控，
# 这里必须自己钉死，且必须在任何解析之前。
export LC_ALL=C
export LANG=C

# 监控证据的契约（组件集合 / CSV 表头 / 有效行判定列号）只存在于 monitor-spec.sh 一处，
# 本脚本写这些列、runner 复算这些列，两边都 source 它——各写一份时它们只是碰巧一致。
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/monitor-spec.sh"
if ! monitor_assert_spec; then
  echo "capture-monitoring: 监控规格自检失败（${SCRIPT_DIR}/monitor-spec.sh），拒绝执行" >&2
  exit 2
fi

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
    --out) OUT_DIR="$2"; shift 2 ;;
    --interval) INTERVAL="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --app-pid) APP_PID="$2"; shift 2 ;;
    --app-port) APP_PORT="$2"; shift 2 ;;
    --mysql-host) MYSQL_HOST="$2"; shift 2 ;;
    --mysql-port) MYSQL_PORT="$2"; shift 2 ;;
    --mysql-user) MYSQL_USER="$2"; shift 2 ;;
    --mysql-db) MYSQL_DB="$2"; shift 2 ;;
    --redis-host) REDIS_HOST="$2"; shift 2 ;;
    --redis-port) REDIS_PORT="$2"; shift 2 ;;
    --required-components) REQUIRED_COMPONENTS="$2"; shift 2 ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
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

# 组件名必须白名单校验：写错一个名字（system 写成 sys）时，下面按名字取值的逻辑会把它
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
# 只在真的要求 mysql 时拒绝，才能让"只采系统资源"这条路径名副其实。
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

# 每个组件的"有效采样行数"与失败原因。判定标准是**数据行里的必需字段齐备且为数值**，
# 而不是"文件非空"——采集器在 mysql 客户端缺失、Redis 不可达、应用进程找不到时，
# 留下的正是"只有表头"或"整行空值"的文件，它们撑不起报告第五章的任何结论，
# 却能让上一版的"非空即完整"判定把整包标成证据完整。这只是采集器侧的自报，
# run-stress-test.sh 会独立复算一遍（见那边的 csv_usable_rows）。
# 两边判"哪些列要齐备"用的都是 monitor-spec.sh 里的 monitor_cols_for —— 不是各写一份。
USABLE_SYSTEM=0
USABLE_JVM=0
USABLE_MYSQL=0
USABLE_REDIS=0
AVAIL_SYSTEM=1
AVAIL_JVM=0
AVAIL_MYSQL=0
AVAIL_REDIS=0
REASON_SYSTEM=""
REASON_JVM=""
REASON_MYSQL=""
REASON_REDIS=""

is_num() { case "$1" in ''|*[!0-9.-]*) return 1 ;; *) [[ "$1" =~ ^-?[0-9]+(\.[0-9]+)?$ ]] ;; esac; }

# 把原因里的逗号换成 ';'：component-status.csv 是逗号分隔的，原因里混进逗号会串列，
# 而这份文件正是给调用方与第三方读的。
clean_reason() { printf '%s' "$1" | tr ',' ';' | tr -d '\n'; }

# 组件名 → "可用|有效行数|原因"。bash 3.2 没有关联数组，这个 case 就是最小可行形式；
# 状态表与"哪些必需组件没采到"两处判定都走它。原先这两处各写一份 dispatch，
# 新增组件时只改一处不会报错，只会让两份产物对同一个组件给出不同的结论。
component_state() {
  case "$1" in
    system)  printf '%s|%s|%s' "$AVAIL_SYSTEM" "$USABLE_SYSTEM" "$REASON_SYSTEM" ;;
    app-jvm) printf '%s|%s|%s' "$AVAIL_JVM" "$USABLE_JVM" "$REASON_JVM" ;;
    mysql)   printf '%s|%s|%s' "$AVAIL_MYSQL" "$USABLE_MYSQL" "$REASON_MYSQL" ;;
    redis)   printf '%s|%s|%s' "$AVAIL_REDIS" "$USABLE_REDIS" "$REASON_REDIS" ;;
    *) return 1 ;;
  esac
}

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

# 进程定位的两种"不正常"要分别记下来，最后都要出现在 component-status.csv 的 reason 列里：
# 指定了端口却查不到监听进程（此时**不兜底**）、以及没指定目标而按特征兜底（可能选错进程）。
APP_PID_LOOKUP_FAILED=""
APP_PID_LOOKUP_FALLBACK=""

# 自动定位应用进程：优先显式 --app-pid；其次按监听端口反查（最准确）；
# 最后按 main class / 启动脚本特征匹配（同机多 java 进程时可能误配，故仅作兜底）。
#
# 关键约束：**只有在调用方没指定端口/进程时才走 pgrep 兜底**。指定了 --app-port 却没查到
# 监听进程，不能退化成"随便挑一个 java 进程"——那会把同机另一个 JVM（例如另开的 IDE 或
# 另一个 worktree 的后端）的堆与 GC 当成被压应用的，写进 app-jvm.csv 且看不出异常。
# 实测过：--app-port 1（无监听）时 pgrep 兜底抓到了本机 dev 后端，usable 记为 1。
if [[ -z "$APP_PID" ]] && [[ -n "$APP_PORT" ]] && command -v lsof >/dev/null 2>&1; then
  APP_PID="$(lsof -ti "tcp:${APP_PORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
  if [[ -z "$APP_PID" ]]; then
    APP_PID_LOOKUP_FAILED="端口 ${APP_PORT} 上没有监听进程（lsof 未查到）"
  fi
elif [[ -z "$APP_PID" ]] && [[ -z "$APP_PORT" ]]; then
  APP_PID="$(pgrep -f 'bid-platform|XiyuBidApplication|spring-boot:run' | head -1 || true)"
  if [[ -n "$APP_PID" ]]; then
    APP_PID_LOOKUP_FALLBACK="未指定 --app-pid/--app-port，按进程特征兜底选中 pid=${APP_PID}（同机多 java 进程时可能选错）"
  fi
fi

has_cmd() { command -v "$1" >/dev/null 2>&1; }

# 把 jcmd 输出里的 "关键字 数值单位" 归一化成 MB。
# 必须同时支持 K 与 M：G1 输出 "total 524288K, used 123456K"，ZGC 输出 "used 368M, capacity 512M"，
# 只认 K 会让 ZGC 环境下的堆占用静默变成 0。
parse_size_mb() {
  local line="$1" key="$2" val unit
  [[ -n "$line" ]] || return 0
  # 先按逗号切段，再在**每段内部**找 "<key> <数值><单位>"，命中第一段即返回。
  # 不能对整行做贪婪正则：ZGC 的 "used 364M, capacity 420M, max capacity 512M" 里
  # `.*capacity` 会贪婪匹配到 "max capacity 512M"，把当前容量报成上限容量（=Xmx）。
  val="$(printf '%s' "$line" | tr ',' '\n' | awk -v k="$key" '
    { for (i = 1; i < NF; i++) if ($i == k) { print $(i + 1); exit } }')"
  [[ -n "$val" ]] || return 0
  unit="$(printf '%s' "$val" | sed -nE 's/^[0-9.]+([A-Za-z])$/\1/p')"
  val="$(printf '%s' "$val" | sed -nE 's/^([0-9.]+).*/\1/p')"
  [[ -n "$val" ]] || return 0
  awk -v v="$val" -v u="$unit" 'BEGIN {
    if (u == "K" || u == "k") printf "%.0f", v / 1024;
    else if (u == "G" || u == "g") printf "%.0f", v * 1024;
    else printf "%.0f", v;
  }'
}

mysql_ready=0
if has_cmd mysql; then mysql_ready=1; fi
redis_ready=0
if has_cmd redis-cli; then redis_ready=1; fi
jcmd_ready=0
if has_cmd jcmd; then jcmd_ready=1; fi

AVAIL_MYSQL="$mysql_ready"
AVAIL_REDIS="$redis_ready"
[[ -z "$REASON_MYSQL" ]] && [[ "$mysql_ready" != "1" ]] && REASON_MYSQL="mysql 客户端不可用"
[[ -z "$REASON_REDIS" ]] && [[ "$redis_ready" != "1" ]] && REASON_REDIS="redis-cli 不可用"
if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" 2>/dev/null; then
  AVAIL_JVM=1
  [[ -n "$APP_PID_LOOKUP_FALLBACK" ]] && REASON_JVM="$APP_PID_LOOKUP_FALLBACK"
else
  REASON_JVM="应用进程不可用（${APP_PID_LOOKUP_FAILED:-app_pid=${APP_PID:-<未找到>}}）"
fi

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
  echo "  app_pid=${APP_PID:-<未找到>} app_port=${APP_PORT:-<未指定>}  jcmd=${jcmd_ready}  mysql=${mysql_ready}  redis-cli=${redis_ready}"
  [[ -n "$APP_PID_LOOKUP_FAILED" ]] && echo "  !! ${APP_PID_LOOKUP_FAILED}；按约束不退回 pgrep 兜底，app-jvm 记为未采集"
  [[ -n "$APP_PID_LOOKUP_FALLBACK" ]] && echo "  !! ${APP_PID_LOOKUP_FALLBACK}"
  echo "  mysql=${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}  redis=${REDIS_HOST}:${REDIS_PORT} db=${REDIS_DB}"
} | tee -a "$HUMAN_LOG"

read_system() {
  # macOS 与 Linux 的 top 参数不同，分别取 CPU 行，再统一交给 awk 解析
  # （不用 sed 提取：两端字段名与分隔符不一致，且 greedy 匹配在 BSD sed 上易失配）
  local cpu_line cpu_triple
  if [[ "$(uname -s)" == "Darwin" ]]; then
    cpu_line="$(top -l 1 -n 0 2>/dev/null | grep -E '^CPU usage' | head -1)"
  else
    cpu_line="$(top -bn 1 -d 1 2>/dev/null | grep -E '^%?Cpu' | head -1)"
  fi
  cpu_triple="$(printf '%s\n' "$cpu_line" | awk '{
    u=""; s=""; d="";
    for (i = 1; i <= NF; i++) {
      t = $i; gsub(",", "", t);
      if (t == "user" || t == "us") u = $(i - 1);
      else if (t == "sys" || t == "sy") s = $(i - 1);
      else if (t == "idle" || t == "id") d = $(i - 1);
    }
    gsub("%", "", u); gsub("%", "", s); gsub("%", "", d);
    print u "," s "," d
  }')"
  local user_pct sys_pct idle_pct
  user_pct="$(printf '%s' "$cpu_triple" | cut -d, -f1)"
  sys_pct="$(printf '%s' "$cpu_triple" | cut -d, -f2)"
  idle_pct="$(printf '%s' "$cpu_triple" | cut -d, -f3)"

  local loads load1 load5
  loads="$(uptime | sed -nE 's/.*load averages?: *([0-9.]+),? *([0-9.]+).*/\1 \2/p')"
  load1="$(printf '%s' "$loads" | awk '{print $1}')"
  load5="$(printf '%s' "$loads" | awk '{print $2}')"

  local mem_total_mb mem_used_mb
  if [[ "$(uname -s)" == "Darwin" ]]; then
    mem_total_mb="$(sysctl -n hw.memsize | awk '{printf "%.0f", $1/1048576}')"
    # vm_stat 输出的是"页数"，必须乘以真实页大小才是字节数。
    # Apple Silicon 页大小是 16384 而非 4096，写死 4096 会让内存用量恒定偏小 4 倍
    # （本机实测：写死 4096 → 5148MB，真实 → 20571MB，32GB 机器上把 63% 写成 16%）。
    # 优先读 vm_stat 首行自报的页大小（最权威），退而求其次问 sysctl，最后才退回 4096。
    local page_size
    page_size="$(vm_stat | sed -nE 's/.*page size of ([0-9]+) bytes.*/\1/p' | head -1)"
    if [[ -z "$page_size" ]]; then
      page_size="$(sysctl -n hw.pagesize 2>/dev/null || true)"
    fi
    [[ -n "$page_size" ]] || page_size=4096
    mem_used_mb="$(vm_stat | awk -v ps="$page_size" \
      '/Pages active/ {a=$3} /Pages wired/ {w=$4} /Pages occupied by compressor/ {c=$5} END {printf "%.0f", (a+w+c)*ps/1048576}')"
  else
    mem_total_mb="$(awk '/MemTotal/ {printf "%.0f", $2/1024}' /proc/meminfo)"
    mem_used_mb="$(awk '/MemTotal/ {t=$2} /MemAvailable/ {a=$2} END {printf "%.0f", (t-a)/1024}' /proc/meminfo)"
  fi

  printf '%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$TS" "${user_pct:-}" "${sys_pct:-}" "${idle_pct:-}" "${load1:-}" "${load5:-}" \
    "${mem_used_mb:-}" "${mem_total_mb:-}" >> "$SYSTEM_CSV"
  SUMMARY+=("cpu_user=${user_pct:-?}% cpu_sys=${sys_pct:-?}% load1=${load1:-?} mem=${mem_used_mb:-?}/${mem_total_mb:-?}MB")

  # 有效采样的判据：这一行里后续分析要用的字段全都取到了数值。
  # 只判"写了行"是不够的——top/uptime/sysctl 解析失败时写出的正是一行逗号占位，
  # 非空、有行数，却什么都说明不了。
  if is_num "$user_pct" && is_num "$idle_pct" && is_num "$load1" \
     && is_num "$mem_used_mb" && is_num "$mem_total_mb"; then
    USABLE_SYSTEM=$((USABLE_SYSTEM + 1))
  else
    REASON_SYSTEM="系统采样字段取不到数值（top/uptime/sysctl 解析失败）"
  fi
}

read_jvm() {
  if [[ -z "$APP_PID" ]] || ! kill -0 "$APP_PID" 2>/dev/null; then
    { printf '%s,%s' "$TS" "${APP_PID:-}"; for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13; do printf ','; done; printf '\n'; } >> "$JVM_CSV"
    SUMMARY+=("app_pid=${APP_PID:-<未找到>} (进程不可用)")
    REASON_JVM="应用进程不可用（${APP_PID_LOOKUP_FAILED:-app_pid=${APP_PID:-<未找到>}}）"
    return
  fi
  local cpu_pct rss_mb
  cpu_pct="$(ps -o %cpu= -p "$APP_PID" 2>/dev/null | tr -d ' ')"
  rss_mb="$(ps -o rss= -p "$APP_PID" 2>/dev/null | awk '{printf "%.0f", $1/1024}')"

  # 线程数走 OS 接口：比 jcmd Thread.print 便宜，压测期间不会自己成为负担
  local threads
  if [[ "$(uname -s)" == "Darwin" ]]; then
    threads="$(ps -M "$APP_PID" 2>/dev/null | wc -l | tr -d ' ')"
    if [[ -n "$threads" ]] && [[ "$threads" -gt 0 ]]; then threads=$((threads - 1)); fi
  else
    threads="$(awk '/^Threads:/{print $2}' "/proc/${APP_PID}/status" 2>/dev/null)"
  fi

  # GC 计数：按 jstat 表头名字取值，而不是按列序号——
  # G1 与 ZGC 的列含义不同（ZGC 在 Java 21 下 YGC/FGC 恒为 "-"，真实回收在 CGC 列），
  # 写死列号会把 ZGC 的 GC 次数全部读成空。
  local gc_fields heap_from_jstat
  if has_cmd jstat; then
    gc_fields="$(jstat -gc "$APP_PID" 2>/dev/null | awk '
      function num(x) { return (x == "" || x == "-") ? "" : x }
      function kb(x) { return (x == "" || x == "-") ? 0 : x + 0 }
      NR == 1 { for (i = 1; i <= NF; i++) h[i] = $i; next }
      NR == 2 {
        for (i = 1; i <= NF; i++) v[h[i]] = $i;
        printf "%s,%s,%s,%s,%s,%s,%s", num(v["YGC"]), num(v["YGCT"]), num(v["FGC"]), num(v["FGCT"]),
                                       num(v["CGC"]), num(v["CGCT"]), num(v["GCT"]);
        printf ",%.0f,%.0f",
               (kb(v["S0U"]) + kb(v["S1U"]) + kb(v["EU"]) + kb(v["OU"])) / 1024,
               (kb(v["S0C"]) + kb(v["S1C"]) + kb(v["EC"]) + kb(v["OC"])) / 1024;
      }' | head -1)"
  fi
  local gc_vals=()
  if [[ -n "$gc_fields" ]]; then
    IFS=',' read -r -a gc_vals <<< "$gc_fields"
  fi

  local heap_used heap_committed metaspace_used
  if [[ "$jcmd_ready" == "1" ]]; then
    local heap_info heap_line
    heap_info="$(jcmd "$APP_PID" GC.heap_info 2>/dev/null || true)"
    # 只认"整堆"行：G1 是 ` garbage-first heap ...`，ZGC 是 ` ZHeap ...`（两者都有前导空格）。
    # 不能写 grep 'used' | head -1 —— ParallelGC/SerialGC 的第一条含 used 的行是**新生代**行
    # （PSYoungGen total 153088K ≈ 149MB，而整堆约 491MB），会把 committed 读小约 3 倍。
    # 这两类收集器因此不走 jcmd 分支，交给下面 jstat 的 S0U+S1U+EU+OU 整堆求和兜底；
    # 两者都拿不到时宁可留空，也不写一个局部值冒充整堆。
    heap_line="$(printf '%s\n' "$heap_info" | grep -E '^[[:space:]]*(garbage-first heap|ZHeap)' | head -1)"
    heap_used="$(parse_size_mb "$heap_line" 'used')"
    heap_committed="$(parse_size_mb "$heap_line" 'total')"
    if [[ -z "$heap_committed" ]]; then heap_committed="$(parse_size_mb "$heap_line" 'capacity')"; fi
    metaspace_used="$(parse_size_mb "$(printf '%s\n' "$heap_info" | grep -E '^[[:space:]]*Metaspace' | head -1)" 'used')"
  fi
  # jcmd 不可用时退回 jstat 的 S0U+S1U+EU+OU（ZGC 下等价于 OU）
  if [[ -z "${heap_used:-}" ]] && [[ -n "$gc_fields" ]]; then
    heap_used="${gc_vals[7]:-}"
    heap_committed="${gc_vals[8]:-}"
  fi

  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "$TS" "$APP_PID" "${heap_used:-}" "${heap_committed:-}" "${metaspace_used:-}" "${threads:-}" \
    "${gc_vals[0]:-}" "${gc_vals[1]:-}" "${gc_vals[2]:-}" "${gc_vals[3]:-}" \
    "${gc_vals[4]:-}" "${gc_vals[5]:-}" "${gc_vals[6]:-}" \
    "${cpu_pct:-}" "${rss_mb:-}" >> "$JVM_CSV"
  SUMMARY+=("app_pid=${APP_PID} heap=${heap_used:-?}/${heap_committed:-?}MB meta=${metaspace_used:-?}MB threads=${threads:-?} cpu=${cpu_pct:-?}% rss=${rss_mb:-?}MB YGC=${gc_vals[0]:-.}(${gc_vals[1]:-.}s) FGC=${gc_vals[2]:-.}(${gc_vals[3]:-.}s) CGC=${gc_vals[4]:-.}(${gc_vals[5]:-.}s)")

  # 进程在、但堆/线程/RSS 一个都取不到，同样不算一次有效采样：
  # 那说明 jcmd 与 jstat 两条路径都没成，该行落到报告里只会被读成"没有数据"。
  # GC 计数**不**参与判定：ZGC 在 Java 21 下 YGC/FGC 恒为 "-"，把它们当必需项会把
  # 正确的 ZGC 采样判成无效（这正是本文件在别处防过的"按列号取 GC"的同一类错误）。
  if is_num "$heap_used" && is_num "$threads" && is_num "$rss_mb"; then
    USABLE_JVM=$((USABLE_JVM + 1))
  else
    REASON_JVM="堆/线程/RSS 取不到数值（jcmd 与 jstat 均失败）"
  fi
}

read_mysql() {
  if [[ "$mysql_ready" != "1" ]]; then
    SUMMARY+=("mysql client 不可用，跳过")
    REASON_MYSQL="mysql 客户端不可用"
    return
  fi
  # 两条查询路径都输出 "变量名,值"，最后按**变量名**取值落到固定列序。
  # 绝不能按行序取值：SHOW GLOBAL STATUS 是按变量名字母序返回的
  # （实测顺序 Aborted_connects, Innodb_row_lock_waits, Questions, Slow_queries,
  #  Threads_connected, Threads_running），按位置对应会把 Aborted_connects 写进
  # threads_connected、把 Questions（示例库 32 万+）写进 slow_queries —— 静默产出
  # 错得离谱的"慢查询 31 万次"，而且不报错。
  local query raw out fallback=0
  query="SELECT CONCAT(VARIABLE_NAME, ',', VARIABLE_VALUE) FROM performance_schema.global_status
         WHERE VARIABLE_NAME IN ('Threads_connected','Threads_running','Slow_queries','Innodb_row_lock_waits','Aborted_connects','Questions');"
  raw="$(MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
      --batch --skip-column-names -e "$query" 2>&1)"
  out="$(printf '%s\n' "$raw" | awk -F',' 'NF >= 2 { print $1 "," $2 }')"
  if [[ -z "$out" ]]; then
    # performance_schema 不可用时回退到 SHOW GLOBAL STATUS（同样是"变量名,值"形态）
    fallback=1
    raw="$(MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        --batch --skip-column-names -e "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Threads_running','Slow_queries','Innodb_row_lock_waits','Aborted_connects','Questions');" 2>&1)"
    out="$(printf '%s\n' "$raw" | awk -F'\t' 'NF >= 2 { print $1 "," $2 }')"
  fi
  if [[ -z "$out" ]]; then
    # 采集不到就把错误原文留痕，而不是静默写一行空值
    SUMMARY+=("mysql 采集失败: $(printf '%s' "$raw" | head -1)")
    REASON_MYSQL="mysql 查询失败: $(printf '%s' "$raw" | head -1)"
    printf '%s,,,,,,\n' "$TS" >> "$MYSQL_CSV"
    return
  fi
  local cols via=""
  cols="$(printf '%s\n' "$out" | awk -F',' '
    NF >= 2 { v[$1] = $2 }
    END { printf "%s,%s,%s,%s,%s,%s", v["Threads_connected"], v["Threads_running"], v["Slow_queries"], v["Innodb_row_lock_waits"], v["Aborted_connects"], v["Questions"] }')"
  [[ "$fallback" == "1" ]] && via=" [回退路径 SHOW GLOBAL STATUS]"
  printf '%s,%s\n' "$TS" "$cols" >> "$MYSQL_CSV"
  SUMMARY+=("mysql conn=$(printf '%s' "$cols" | cut -d, -f1) running=$(printf '%s' "$cols" | cut -d, -f2) slow=$(printf '%s' "$cols" | cut -d, -f3) lock_waits=$(printf '%s' "$cols" | cut -d, -f4)${via}")

  # 查询成功但某几个变量没返回（权限不足的账号看不到 Slow_queries 等）时，
  # 那一列会是空字符串。只判"out 非空"会让这种半残的行也算有效采样。
  local _ok=1 _f
  for _f in 1 2 3 4 5 6; do
    is_num "$(printf '%s' "$cols" | cut -d, -f"$_f")" || _ok=0
  done
  unset _f
  if [[ "$_ok" == "1" ]]; then
    USABLE_MYSQL=$((USABLE_MYSQL + 1))
  else
    REASON_MYSQL="mysql 返回缺少必需状态变量: ${cols}"
  fi
}

read_redis() {
  if [[ "$redis_ready" != "1" ]]; then
    SUMMARY+=("redis-cli 不可用，跳过")
    REASON_REDIS="redis-cli 不可用"
    return
  fi
  local info
  if ! info="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" INFO 2>/dev/null)"; then
    SUMMARY+=("redis 不可达，跳过")
    REASON_REDIS="redis 不可达（${REDIS_HOST}:${REDIS_PORT} db=${REDIS_DB}）"
    return
  fi
  local clients mem ops hits misses
  clients="$(printf '%s' "$info" | sed -nE 's/^connected_clients:([0-9]+).*/\1/p')"
  mem="$(printf '%s' "$info" | sed -nE 's/^used_memory:([0-9]+).*/\1/p' | awk '{printf "%.1f", $1/1048576}')"
  ops="$(printf '%s' "$info" | sed -nE 's/^instantaneous_ops_per_sec:(-?[0-9]+).*/\1/p')"
  hits="$(printf '%s' "$info" | sed -nE 's/^keyspace_hits:([0-9]+).*/\1/p')"
  misses="$(printf '%s' "$info" | sed -nE 's/^keyspace_misses:([0-9]+).*/\1/p')"
  printf '%s,%s,%s,%s,%s,%s\n' "$TS" \
    "${clients:-}" "${mem:-}" "${ops:-}" "${hits:-}" "${misses:-}" >> "$REDIS_CSV"
  SUMMARY+=("redis clients=${clients:-?} mem=${mem:-?}MB ops=${ops:-?}/s hits=${hits:-?} misses=${misses:-?}")

  if is_num "$clients" && is_num "$mem" && is_num "$ops" && is_num "$hits" && is_num "$misses"; then
    USABLE_REDIS=$((USABLE_REDIS + 1))
  else
    REASON_REDIS="redis INFO 返回缺少必需字段（客户端=${clients:-空} 内存=${mem:-空} ops=${ops:-空}）"
  fi
}

START_EPOCH="$(date +%s)"
SAMPLE_COUNT=0
while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  # 四个采集器各自把可读摘要**追加**进 SUMMARY，日志行再按数组顺序拼接。
  # 不要退回「四个全局变量 + printf 按位取值」：那样新增第五个采集器时，漏改清空列表或
  # printf 的位序都不会报错，只会让日志列静默串行错位——与本文件在 read_mysql 里防的
  # 「按返回行序取值」是同一类错误。数组下"每个采集器恰好追加一次"由数量自然保证。
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

# ── 组件级结论：机器可读状态 + 退出码 ───────────────────────────────────────
# 这一块是本脚本对"监控证据到底有没有用"的唯一交代。上一版没有它，于是
# 「mysql 客户端没装 / Redis 连不上 / 应用进程找不到」都会留下非空（只有表头或整行空值）
# 的 CSV 并 exit 0，调用方的完整性判定只查 -s，最后交付出一份"监控完整"的空壳。
# 判据是**有效数据行数**：必需字段齐备且为数值的行才算数（见各 read_* 末尾）。
STATUS_CSV="${MON_DIR}/component-status.csv"
{
  echo "component,required,available,usable_samples,total_samples,reason"
  for _c in $MONITOR_COMPONENTS; do
    IFS='|' read -r _a _u _r <<< "$(component_state "$_c")"
    _req=0
    case " ${REQUIRED_COMPONENTS} " in *" ${_c} "*) _req=1 ;; esac
    printf '%s,%s,%s,%s,%s,%s\n' "$_c" "$_req" "$_a" "$_u" "$SAMPLE_COUNT" "$(clean_reason "$_r")"
  done
} > "$STATUS_CSV"
unset _c _a _u _r _req

FAILED_COMPONENTS=""
for _c in $REQUIRED_COMPONENTS; do
  IFS='|' read -r _a _u _r <<< "$(component_state "$_c")"
  if [[ "$_u" -eq 0 ]]; then FAILED_COMPONENTS="${FAILED_COMPONENTS} ${_c}"; fi
done
unset _c _a _u _r

{
  echo "capture-monitoring: 采样结束（共 ${SAMPLE_COUNT} 次）"
  echo "  组件有效采样行: system=${USABLE_SYSTEM} app-jvm=${USABLE_JVM} mysql=${USABLE_MYSQL} redis=${USABLE_REDIS}（每次采样 1 行，有效 = 必需字段齐备）"
  echo "  组件状态明细: ${STATUS_CSV}"
} | tee -a "$HUMAN_LOG"

if [[ -n "$FAILED_COMPONENTS" ]]; then
  echo "capture-monitoring: 以下必需组件一行有效数据都没有：${FAILED_COMPONENTS}" >&2
  echo "                 这份 monitoring/ 不能作为压测证据交付，调用方应据此判定证据不完整。" >&2
  echo "                 原因见 ${STATUS_CSV} 的 reason 列与 ${HUMAN_LOG}。" >&2
  echo "                 若目标环境确实没有该组件，请显式声明范围（run-stress-test.sh 的" >&2
  echo "                 --monitor-required-components），不要让它以「完整」的名义交付。" >&2
  exit 3
fi
exit 0
