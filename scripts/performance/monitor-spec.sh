#!/usr/bin/env bash
# Input: 组件名（system | app-jvm | mysql | redis）
# Output: 该组件的 CSV 文件名、表头、以及"这次采样算不算数"所需的列号
# Pos: scripts/performance/ - 监控证据的**唯一契约源**：组件集合、CSV 表头、有效行判定列
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一个文件：采集器"写"这些列、runner"复算"这些列，两边各写一份列号时，
# 它们只是碰巧一致——任何一侧改了字段顺序或增删列，另一侧不会报错，只会算出不同的
# 有效行数（一方说 6/6、另一方说 0/6），而两份产物看上去都正常。把表头与列号放进
# 同一个文件、由双方 source，这个漂移就不再可能发生；`--self-check` 让它在无服务的
# 情况下也能被门禁检查。
#
# 用法：
#   source scripts/performance/monitor-spec.sh
#   monitor_csv_for system        -> system.csv
#   monitor_cols_for app-jvm      -> 3,6,15
#   monitor_spec_for mysql        -> mysql.csv:2,3,4,5,6,7
#   monitor_header_for redis      -> timestamp,connected_clients,...（**含结尾换行**，可直接重定向为 CSV 表头）
#   monitor_assert_spec           -> 规格自检，不一致时非 0 退出
#
# 非 shell 调用方（write-run-manifest.mjs）读**同一份数据**的机器可读形态：
#   bash scripts/performance/monitor-spec.sh --print-json
#   -> {"system":{"csv":"system.csv","cols":[2,4,5,7,8]},...}
#
# 列号从 1 起，第 1 列固定是 timestamp。判定"有效采样行"的含义是：
# 这些列在该行里**齐备且为数值**——这正是采集器在 mysql 客户端缺失、Redis 不可达、
# 应用进程找不到时留下的那类"有行数、没数据"文件的反面。

# 组件全集。顺序即 component-status.csv 的行序、也是逐组件复算的遍历序。
MONITOR_COMPONENTS="system app-jvm mysql redis"

monitor_csv_for() {
  case "$1" in
    system)  printf 'system.csv' ;;
    app-jvm) printf 'app-jvm.csv' ;;
    mysql)   printf 'mysql.csv' ;;
    redis)   printf 'redis.csv' ;;
    *) return 1 ;;
  esac
}

monitor_header_for() {
  # 每个表头都以换行结尾：采集器是 `monitor_header_for system > system.csv` 直接把它写进
  # CSV 的，少了这个换行，表头会和第一行数据粘成一行 —— 文件有表头、有数据、行数却少一行，
  # 采集器自己按采样次数统计仍报"有效 N 行"，而 runner 复算（awk 跳表头）得到 0 行。
  # 两侧判据一致也救不了这种"写的时候就没分行"，只有复算能发现（实测踩过）。
  case "$1" in
    system)  printf 'timestamp,cpu_user_pct,cpu_sys_pct,cpu_idle_pct,load1,load5,mem_used_mb,mem_total_mb\n' ;;
    app-jvm) printf 'timestamp,pid,heap_used_mb,heap_committed_mb,metaspace_used_mb,threads,ygc,ygct_s,fgc,fgct_s,cgc,cgct_s,gc_total_s,cpu_pct,rss_mb\n' ;;
    mysql)   printf 'timestamp,threads_connected,threads_running,slow_queries,innodb_row_lock_waits,aborted_connects,questions\n' ;;
    redis)   printf 'timestamp,connected_clients,used_memory_mb,instantaneous_ops_per_sec,keyspace_hits,keyspace_misses\n' ;;
    *) return 1 ;;
  esac
}

# 判定"这次采样算不算数"要看哪几列。选的是每个组件里"能支撑报告中对应结论"的那几列，
# 不是全部列：app-jvm 挑堆/线程/RSS（GC 次数在 ZGC 下是 '-'，不能当有效行判据），
# system 挑 CPU/load/内存（cpu_sys_pct 与 load5 不参与判定，但仍逐行写在 CSV 里供复核）。
monitor_cols_for() {
  case "$1" in
    system)  printf '2,4,5,7,8' ;;
    app-jvm) printf '3,6,15' ;;
    mysql)   printf '2,3,4,5,6,7' ;;
    redis)   printf '2,3,4,5,6' ;;
    *) return 1 ;;
  esac
}

monitor_spec_for() {
  local _c="${1:-}" _csv=""
  _csv="$(monitor_csv_for "$_c")" || return 1
  printf '%s:%s' "$_csv" "$(monitor_cols_for "$_c")"
}

# 规格自检（不依赖任何服务）：组件集合、表头、列号三者必须自洽。
# 列号越界是最典型的破坏方式——例如 mysql 表头从 7 列减到 6 列而列号还写着 7，
# 那样每一行都会被判为"无效"，采集器与 runner 会一致地报 0 行，看上去像"环境没数据"。
monitor_assert_spec() {
  local _c _header _ncols _cols _col _bad=0
  for _c in $MONITOR_COMPONENTS; do
    _header="$(monitor_header_for "$_c")" || { echo "monitor-spec: 未知组件 ${_c}" >&2; return 1; }
    _ncols="$(printf '%s' "$_header" | awk -F, '{print NF}')"
    _cols="$(monitor_cols_for "$_c")" || { echo "monitor-spec: ${_c} 缺列号规格" >&2; return 1; }
    for _col in $(printf '%s' "$_cols" | tr ',' ' '); do
      if [[ ! "$_col" =~ ^[0-9]+$ ]] || [[ "$_col" -lt 2 ]] || [[ "$_col" -gt "$_ncols" ]]; then
        echo "monitor-spec: ${_c} 的列号 ${_col} 超出表头列数 ${_ncols}（表头 ${_header}）" >&2
        _bad=1
      fi
    done
  done
  unset _c _header _ncols _cols _col
  if [[ "$_bad" != "0" ]]; then return 1; fi
  return 0
}

# 机器可读形态，供**非 shell 调用方**读取：清单生成器要自己复算监控完整性，
# 而它没有 shell 的函数可用。两条路都通不过去：让 JS 去 parse 本文件的 shell 语法
# （语法一改就静默失效），或由 runner 把列号当环境变量透传（调用方一漏传，清单就
# 少一层校验）。由本文件自己吐 JSON，则 shell 侧与 JS 侧读到的**必然**是同一份规格。
monitor_spec_json() {
  local _c _csv _cols _first=1
  printf '{'
  for _c in $MONITOR_COMPONENTS; do
    _csv="$(monitor_csv_for "$_c")" || return 1
    _cols="$(monitor_cols_for "$_c")" || return 1
    [[ "$_first" == "1" ]] || printf ','
    _first=0
    printf '"%s":{"csv":"%s","cols":[%s]}' "$_c" "$_csv" "$_cols"
  done
  printf '}\n'
}

# 直接执行本文件时跑自检，供门禁/CI 在无服务环境下调用：
#   bash scripts/performance/monitor-spec.sh --self-check
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  case "${1:-}" in
    --self-check|"")
      if monitor_assert_spec; then
        echo "monitor-spec: 自检通过（${MONITOR_COMPONENTS} 的表头与有效列号自洽）"
        exit 0
      fi
      exit 1
      ;;
    --print-json)
      monitor_assert_spec || exit 1
      monitor_spec_json
      exit 0
      ;;
    *) echo "monitor-spec: 用法 monitor-spec.sh [--self-check|--print-json]" >&2; exit 2 ;;
  esac
fi
