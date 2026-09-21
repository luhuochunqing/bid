#!/usr/bin/env bash
# Input: 采集器上下文（APP_PID/APP_PORT/MYSQL_*/REDIS_*/REQUIRED_COMPONENTS/OUT_DIR）与各 read_* 累计的组件状态
# Output: monitoring/component-status.csv（逐组件 required/available/usable_samples/reason）与"这次采集算不算成功"的判定（必需组件 0 行有效数据 → 3）
# Pos: scripts/performance/ - 采集器的**状态与结论层**：组件状态的唯一持有者，也是"这次采集成不成功"的唯一裁判
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：组件状态（可用性、有效采样行数、失败原因）此前散落在采集器各处——
# 探测工具可用性写一遍、结论段再读一遍，"哪些必需组件没采到"两处各有一份 dispatch。
# 新增组件时只改一处不会报错，只会让 component-status.csv 与退出码对同一个组件给出
# 不同结论。现在这一层是状态的唯一持有者：read_* 只累加，结论只从这里出。
#
# 状态契约（本文件拥有，read_* 与采集器只读）：
#   USABLE_SYSTEM/USABLE_JVM/USABLE_MYSQL/USABLE_REDIS  各组件"有效采样行数"
#   AVAIL_*                                            该组件本次是否可采（不可采时 REASON_* 说明原因）
#   REASON_*                                           失败原因（写入 component-status.csv 的 reason 列）
#   APP_PID_LOOKUP_FAILED / APP_PID_LOOKUP_FALLBACK     进程定位的两种"不正常"
# "有效采样行"的判据是**数据行里的必需字段齐备且为数值**（列号见 monitor-spec.sh），
# 不是"文件非空"——采集器在 mysql 客户端缺失、Redis 不可达、应用进程找不到时留下的正是
# "只有表头"或"整行空值"的文件，它们撑不起报告第五章的任何结论。
# 这只是采集器侧的自报；run-stress-test.sh 会独立复算一遍（清单不给自己发合格证）。

monitor_state_init() {
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
  # 指定了端口却查不到监听进程（此时**不兜底**）、以及没指定目标而按特征兜底（可能选错进程）
  APP_PID_LOOKUP_FAILED=""
  APP_PID_LOOKUP_FALLBACK=""
}

# 自动定位应用进程：优先显式 --app-pid；其次按监听端口反查（最准确）；
# 最后按 main class / 启动脚本特征匹配（同机多 java 进程时可能误配，故仅作兜底）。
#
# 关键约束：**只有在调用方没指定端口/进程时才走 pgrep 兜底**。指定了 --app-port 却没查到
# 监听进程，不能退化成"随便挑一个 java 进程"——那会把同机另一个 JVM（例如另开的 IDE 或
# 另一个 worktree 的后端）的堆与 GC 当成被压应用的，写进 app-jvm.csv 且看不出异常。
# 实测过：--app-port 1（无监听）时 pgrep 兜底抓到了本机 dev 后端，usable 记为 1。
monitor_resolve_app_pid() {
  local pid=""
  if [[ -n "$APP_PID" ]]; then
    return 0
  fi
  if [[ -n "$APP_PORT" ]] && has_cmd lsof; then
    pid="$(lsof -ti "tcp:${APP_PORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
    if [[ -n "$pid" ]]; then
      APP_PID="$pid"
    else
      APP_PID_LOOKUP_FAILED="端口 ${APP_PORT} 上没有监听进程（lsof 未查到）"
    fi
    return 0
  fi
  if [[ -z "$APP_PORT" ]]; then
    pid="$(pgrep -f 'bid-platform|XiyuBidApplication|spring-boot:run' | head -1 || true)"
    if [[ -n "$pid" ]]; then
      APP_PID="$pid"
      APP_PID_LOOKUP_FALLBACK="未指定 --app-pid/--app-port，按进程特征兜底选中 pid=${APP_PID}（同机多 java 进程时可能选错）"
    fi
  fi
}

# 采集依赖与组件可用性探测。按监听端口但机器上没有 lsof 时，保留 APP_PORT 让
# read_jvm 走"进程不可用"的如实路径，而不是静默换成兜底匹配。
monitor_probe_tools() {
  local mysql_ready=0 redis_ready=0 jcmd_ready=0
  if has_cmd mysql; then mysql_ready=1; fi
  if has_cmd redis-cli; then redis_ready=1; fi
  if has_cmd jcmd; then jcmd_ready=1; fi
  # read_jvm 用 jcmd_ready 决定是否走 GC.heap_info；作为全局暴露给读取层
  MONITOR_JCMD_READY="$jcmd_ready"
  MONITOR_MYSQL_READY="$mysql_ready"
  MONITOR_REDIS_READY="$redis_ready"

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
}

# 组件名 → "可用|有效行数|原因"。bash 3.2 没有关联数组，这个 case 就是最小可行形式；
# 状态表与"哪些必需组件没采到"两处判定都走它。
component_state() {
  case "$1" in
    system)  printf '%s|%s|%s' "$AVAIL_SYSTEM" "$USABLE_SYSTEM" "$REASON_SYSTEM" ;;
    app-jvm) printf '%s|%s|%s' "$AVAIL_JVM" "$USABLE_JVM" "$REASON_JVM" ;;
    mysql)   printf '%s|%s|%s' "$AVAIL_MYSQL" "$USABLE_MYSQL" "$REASON_MYSQL" ;;
    redis)   printf '%s|%s|%s' "$AVAIL_REDIS" "$USABLE_REDIS" "$REASON_REDIS" ;;
    *) return 1 ;;
  esac
}

# 逐组件状态表。这份文件是调用方与第三方判断"采到什么、采到没有"的唯一入口，
# 因此每个必需组件即使一行有效数据都没有，也必须在表里有一行、并写明原因。
monitor_write_status_csv() {
  local status_csv="$1" sample_count="$2" _c _a _u _r _req
  {
    echo "component,required,available,usable_samples,total_samples,reason"
    for _c in $MONITOR_COMPONENTS; do
      IFS='|' read -r _a _u _r <<< "$(component_state "$_c")"
      _req=0
      case " ${REQUIRED_COMPONENTS} " in *" ${_c} "*) _req=1 ;; esac
      printf '%s,%s,%s,%s,%s,%s\n' "$_c" "$_req" "$_a" "$_u" "$sample_count" "$(clean_reason "$_r")"
    done
  } > "$status_csv"
}

# 结论：必需组件里任一组件一行有效数据都没有 → 3（调用方必须据此判证据不完整）。
monitor_verdict() {
  local status_csv="$1" sample_count="$2" _c _a _u _r failed=""
  monitor_write_status_csv "$status_csv" "$sample_count"
  for _c in $REQUIRED_COMPONENTS; do
    IFS='|' read -r _a _u _r <<< "$(component_state "$_c")"
    if [[ "$_u" -eq 0 ]]; then failed="${failed} ${_c}"; fi
  done
  {
    echo "capture-monitoring: 采样结束（共 ${sample_count} 次）"
    echo "  组件有效采样行: system=${USABLE_SYSTEM} app-jvm=${USABLE_JVM} mysql=${USABLE_MYSQL} redis=${USABLE_REDIS}（每次采样 1 行，有效 = 必需字段齐备）"
    echo "  组件状态明细: ${status_csv}"
  } | tee -a "${HUMAN_LOG:-/dev/null}"

  if [[ -n "$failed" ]]; then
    echo "capture-monitoring: 以下必需组件一行有效数据都没有：${failed}" >&2
    echo "                 这份 monitoring/ 不能作为压测证据交付，调用方应据此判定证据不完整。" >&2
    echo "                 原因见 ${status_csv} 的 reason 列与 ${HUMAN_LOG}。" >&2
    echo "                 若目标环境确实没有该组件，请显式声明范围（run-stress-test.sh 的" >&2
    echo "                 --monitor-required-components），不要让它以「完整」的名义交付。" >&2
    return 3
  fi
  return 0
}
