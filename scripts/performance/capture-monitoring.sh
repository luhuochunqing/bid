#!/usr/bin/env bash
# Input: 应用宿主上的 java 进程、MySQL 8.0 与 Redis 连接参数、采样间隔与时长
# Output: monitoring/ 目录下的 system/app-jvm/mysql/redis 四份 CSV 时间序列 + monitoring.log 可读快照
# Pos: scripts/performance/ - 压测期间系统资源与中间件监控采集器（替代手工截图，产出可归档的原始监控数据）
# 维护声明: 若压测新增被监控组件（如 Nginx/Sidecar），同步更新本脚本的采集块与 scripts/performance/README.md。
#
# 用途：在 k6 压测运行期间持续采样，产出压测报告"系统资源与中间件稳定性分析"章节的原始数据。
# 调用：由 run-stress-test.sh 自动拉起；也可单独运行做长稳监控。
#
# 示例：
#   bash scripts/performance/capture-monitoring.sh \
#     --out docs/testing/stress-test-artifacts/20260921T110000Z-standard \
#     --app-port 18089 --interval 5 --duration 300
#
# 应用进程定位顺序：--app-pid > --app-port（lsof 反查监听端口）> pgrep 特征兜底。
# 同机存在多个 java 进程时务必传 --app-port 或 --app-pid，否则可能采到别的进程。
#
# 停止：--duration 到期自动退出，或 `touch <out>/monitoring/.stop` 立即结束采样。

set -uo pipefail

# 固定 C locale：逗号小数 locale（de_DE 等）下 `uptime` 会输出
# "load averages: 82,26 26,31 24,44"（解析成 82 / 26），`top` 会输出
# "2,5% user, 5,0% sys"（逗号混进 CSV 导致整行列错位）。压测机 locale 不可控，
# 这里必须自己钉死，且必须在任何解析之前。
export LC_ALL=C
export LANG=C

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
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
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

# 无口令则 MySQL 采集直接失败——与其采到一半留一份半残 CSV，不如在入口就拒绝，
# 让调用方（run-stress-test.sh）的完整性判定走"未采集"而不是"采到了空值"。
if [[ -z "$MYSQL_PASSWORD" ]]; then
  echo "capture-monitoring: 必须显式提供 MYSQL_PASSWORD（本脚本不再内置任何默认口令）" >&2
  echo "                 本地 dev 口令见 backend/start.sh / CLAUDE.md，请从环境注入：" >&2
  echo "                 MYSQL_PASSWORD='<口令>' bash scripts/performance/capture-monitoring.sh ..." >&2
  exit 2
fi

MON_DIR="${OUT_DIR%/}/monitoring"
mkdir -p "$MON_DIR"
STOP_FILE="${MON_DIR}/.stop"
rm -f "$STOP_FILE"

SYSTEM_CSV="${MON_DIR}/system.csv"
JVM_CSV="${MON_DIR}/app-jvm.csv"
MYSQL_CSV="${MON_DIR}/mysql.csv"
REDIS_CSV="${MON_DIR}/redis.csv"
HUMAN_LOG="${MON_DIR}/monitoring.log"

# 自动定位应用进程：优先显式 --app-pid；其次按监听端口反查（最准确）；
# 最后按 main class / 启动脚本特征匹配（同机多 java 进程时可能误配，故仅作兜底）
if [[ -z "$APP_PID" ]] && [[ -n "$APP_PORT" ]] && command -v lsof >/dev/null 2>&1; then
  APP_PID="$(lsof -ti "tcp:${APP_PORT}" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
fi
if [[ -z "$APP_PID" ]]; then
  APP_PID="$(pgrep -f 'bid-platform|XiyuBidApplication|spring-boot:run' | head -1 || true)"
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

echo "timestamp,cpu_user_pct,cpu_sys_pct,cpu_idle_pct,load1,load5,mem_used_mb,mem_total_mb" > "$SYSTEM_CSV"
echo "timestamp,pid,heap_used_mb,heap_committed_mb,metaspace_used_mb,threads,ygc,ygct_s,fgc,fgct_s,cgc,cgct_s,gc_total_s,cpu_pct,rss_mb" > "$JVM_CSV"
echo "timestamp,threads_connected,threads_running,slow_queries,innodb_row_lock_waits,aborted_connects,questions" > "$MYSQL_CSV"
echo "timestamp,connected_clients,used_memory_mb,instantaneous_ops_per_sec,keyspace_hits,keyspace_misses" > "$REDIS_CSV"

{
  echo "capture-monitoring: 开始采样"
  echo "  out=${MON_DIR}"
  echo "  interval=${INTERVAL}s duration=${DURATION}s"
  echo "  app_pid=${APP_PID:-<未找到>} app_port=${APP_PORT:-<未指定>}  jcmd=${jcmd_ready}  mysql=${mysql_ready}  redis-cli=${redis_ready}"
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
}

read_jvm() {
  if [[ -z "$APP_PID" ]] || ! kill -0 "$APP_PID" 2>/dev/null; then
    { printf '%s,%s' "$TS" "${APP_PID:-}"; for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13; do printf ','; done; printf '\n'; } >> "$JVM_CSV"
    SUMMARY+=("app_pid=${APP_PID:-<未找到>} (进程不可用)")
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
}

read_mysql() {
  if [[ "$mysql_ready" != "1" ]]; then
    SUMMARY+=("mysql client 不可用，跳过")
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
}

read_redis() {
  if [[ "$redis_ready" != "1" ]]; then
    SUMMARY+=("redis-cli 不可用，跳过")
    return
  fi
  local info
  if ! info="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" INFO 2>/dev/null)"; then
    SUMMARY+=("redis 不可达，跳过")
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
