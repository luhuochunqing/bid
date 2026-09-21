#!/usr/bin/env bash
# Input: 采集器上下文（TS/SYSTEM_CSV/JVM_CSV/MYSQL_CSV/REDIS_CSV/APP_PID/MYSQL_*/REDIS_*/MONITOR_*_READY）与 monitor-parse.sh 的纯解析函数
# Output: 每次采样向四份组件 CSV 各追加一行，并向 SUMMARY 数组追加一行可读摘要；同时累加 monitor-status.sh 持有的组件状态
# Pos: scripts/performance/ - 四个组件读取器（system / app-jvm / mysql / redis）：只负责"采一次、写一行、记一次状态"
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 分层约定：
#   - 取值与解析一律交给 monitor-parse.sh 的纯函数（它们可无服务单测），本文件只做
#     "调命令 → 交给解析 → 写行 → 累加状态"；
#   - 组件状态（USABLE_* / REASON_*）由 monitor-status.sh 持有，本文件只累加、不判定；
#   - 每个读取器都必须只在**取了值**时计数：采不到就写空列并把原因留给状态层，
#     绝不写 0 冒充"采到了 0"（报告里"没有数据"与"数据是 0"是两件事）。
#
# 为什么四个读取器要待在一个文件里：它们共享同一份上下文与状态契约（上面那串变量），
# 彼此的差异只在于"读谁、怎么写一行"；拆成四个文件只会把这份契约复制四遍。

read_system() {
  # macOS 与 Linux 的 top 参数不同，分别取 CPU 行，再交给 parse_cpu_triple（按字段名定位，
  # 不按位置——两端字段名与分隔符都不一致）。
  local cpu_line cpu_triple user_pct sys_pct idle_pct
  if [[ "$(uname -s)" == "Darwin" ]]; then
    cpu_line="$(top -l 1 -n 0 2>/dev/null | grep -E '^CPU usage' | head -1)"
  else
    cpu_line="$(top -bn 1 -d 1 2>/dev/null | grep -E '^%?Cpu' | head -1)"
  fi
  cpu_triple="$(parse_cpu_triple "$cpu_line")"
  user_pct="$(printf '%s' "$cpu_triple" | cut -d, -f1)"
  sys_pct="$(printf '%s' "$cpu_triple" | cut -d, -f2)"
  idle_pct="$(printf '%s' "$cpu_triple" | cut -d, -f3)"

  local loads load1 load5
  loads="$(parse_load_pair "$(uptime)")"
  load1="$(printf '%s' "$loads" | awk '{print $1}')"
  load5="$(printf '%s' "$loads" | awk '{print $2}')"

  local mem_total_mb mem_used_mb
  if [[ "$(uname -s)" == "Darwin" ]]; then
    local page_size vm_stat_out
    mem_total_mb="$(parse_memsize_mb "$(sysctl -n hw.memsize 2>/dev/null || true)")"
    # vm_stat 输出的是"页数"，必须乘以真实页大小才是字节数。只调一次 vm_stat，
    # 页大小与用量都从同一份输出解析——两次调用之间没有信息增量，只是多一次进程开销。
    vm_stat_out="$(vm_stat 2>/dev/null || true)"
    page_size="$(parse_vm_page_size "$vm_stat_out")"
    if [[ -z "$page_size" ]]; then
      page_size="$(sysctl -n hw.pagesize 2>/dev/null || true)"
    fi
    # 页大小取不到时**不兜底成 4096**：本机是 Apple Silicon（16384），猜 4096 会让内存用量
    # 恒定偏小 4 倍，而那一行照样是合法数值、照样被判为有效采样（这正是本文件反复在防的
    # "静默错值"）。取不到就让 mem_used_mb 留空、这次采样判无效，原因写进 REASON_SYSTEM。
    mem_used_mb="$(parse_vm_used_mb "$vm_stat_out" "$page_size")"
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
  # ps 拿不到值时留空，不能让它落到 awk 的 "0" 上——那会让一行"什么都采到"的记录
  # 看起来像一次有效采样（rss=0 是合法数值）。
  rss_mb="$(printf '%s' "$(ps -o rss= -p "$APP_PID" 2>/dev/null)" | awk 'NF {printf "%.0f", $1/1024}')"

  # 线程数走 OS 接口：比 jcmd Thread.print 便宜，压测期间不会自己成为负担
  local threads
  if [[ "$(uname -s)" == "Darwin" ]]; then
    threads="$(ps -M "$APP_PID" 2>/dev/null | wc -l | tr -d ' ')"
    # ps -M 的输出含一行表头；查不到线程时 wc 仍会给出 0/1，那种情况留空而不是写 -1
    if [[ -z "$threads" ]] || [[ "$threads" -lt 1 ]]; then
      threads=""
    else
      threads=$((threads - 1))
    fi
  else
    threads="$(awk '/^Threads:/{print $2}' "/proc/${APP_PID}/status" 2>/dev/null)"
  fi

  # GC 计数与堆总量的取值全在 parse_jstat_gc 里（按 jstat 表头名取列，不按列序号）
  local gc_fields
  if has_cmd jstat; then
    gc_fields="$(parse_jstat_gc "$(jstat -gc "$APP_PID" 2>/dev/null)")"
  fi
  local gc_vals=()
  if [[ -n "$gc_fields" ]]; then
    IFS=',' read -r -a gc_vals <<< "$gc_fields"
  fi

  local heap_used heap_committed metaspace_used
  if [[ "$MONITOR_JCMD_READY" == "1" ]]; then
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
  if [[ "$MONITOR_MYSQL_READY" != "1" ]]; then
    SUMMARY+=("mysql client 不可用，跳过")
    REASON_MYSQL="mysql 客户端不可用"
    return
  fi
  # 两条查询路径都归一化成 "变量名,值" 的行，再交给 parse_mysql_status_cols 按**变量名**
  # 落到固定列序（按行序取值的后果见该函数注释）。
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
  cols="$(parse_mysql_status_cols "$out")"
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
  if [[ "$MONITOR_REDIS_READY" != "1" ]]; then
    SUMMARY+=("redis-cli 不可用，跳过")
    REASON_REDIS="redis-cli 不可用"
    return
  fi
  local info cols
  if ! info="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" INFO 2>/dev/null)"; then
    SUMMARY+=("redis 不可达，跳过")
    REASON_REDIS="redis 不可达（${REDIS_HOST}:${REDIS_PORT} db=${REDIS_DB}）"
    return
  fi
  cols="$(parse_redis_info_cols "$info")"
  printf '%s,%s\n' "$TS" "$cols" >> "$REDIS_CSV"
  SUMMARY+=("redis clients=$(printf '%s' "$cols" | cut -d, -f1) mem=$(printf '%s' "$cols" | cut -d, -f2)MB ops=$(printf '%s' "$cols" | cut -d, -f3)/s hits=$(printf '%s' "$cols" | cut -d, -f4) misses=$(printf '%s' "$cols" | cut -d, -f5)")

  local clients mem ops hits misses
  clients="$(printf '%s' "$cols" | cut -d, -f1)"
  mem="$(printf '%s' "$cols" | cut -d, -f2)"
  ops="$(printf '%s' "$cols" | cut -d, -f3)"
  hits="$(printf '%s' "$cols" | cut -d, -f4)"
  misses="$(printf '%s' "$cols" | cut -d, -f5)"
  if is_num "$clients" && is_num "$mem" && is_num "$ops" && is_num "$hits" && is_num "$misses"; then
    USABLE_REDIS=$((USABLE_REDIS + 1))
  else
    REASON_REDIS="redis INFO 返回缺少必需字段（客户端=${clients:-空} 内存=${mem:-空} ops=${ops:-空}）"
  fi
}
