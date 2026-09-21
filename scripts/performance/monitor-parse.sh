#!/usr/bin/env bash
# Input: 宿主/JVM/MySQL/Redis 命令的原始输出文本（top / uptime / vm_stat / sysctl / jstat / jcmd / mysql / redis-cli INFO）
# Output: 归一化后的数值字段（函数以 stdout 返回；取不到时返回空串，由调用方决定这次采样算不算有效）
# Pos: scripts/performance/ - 监控采样的**纯解析层**：输入文本 → 输出数值，不碰进程、不连服务、不写文件
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：这些函数是"采到的字节 → 报告里那个数字"的唯一通道，也是本工具最容易
# **静默出错**的地方（ZGC 的堆容量被读成 Xmx、Apple Silicon 页大小写死 4096 让内存偏小 4 倍、
# SHOW GLOBAL STATUS 按行序取值把 Questions 写进 slow_queries）。它们原先长在采集器内部，
# 只能靠"起一个真进程采一遍"才能验证。抽出来之后，`--self-test` 用合成文本就能把这些坑
# 逐条钉死——无服务、无网络、无副作用，可以在任何环境里跑。
#
# 契约：
#   - 只读参数与 stdin，不依赖任何全局状态（因此可单测）；
#   - 取不到值时返回**空串**，而不是 0——"没有数据"和"数据是 0"在报告里是两件事；
#   - 解析不出来时不猜：宁可留空让调用方判为无效采样，也不写一个局部值/错位值冒充。

is_num() { case "$1" in ''|*[!0-9.-]*) return 1 ;; *) [[ "$1" =~ ^-?[0-9]+(\.[0-9]+)?$ ]] ;; esac; }

# 原因里的逗号换成 ';'：component-status.csv 是逗号分隔的，原因里混进逗号会串列，
# 而这份文件正是给调用方与第三方读的。
clean_reason() { printf '%s' "$1" | tr ',' ';' | tr -d '\n'; }

has_cmd() { command -v "$1" >/dev/null 2>&1; }

# jcmd 的 "关键字 数值单位" → MB。必须同时支持 K 与 M：G1 输出
# "total 524288K, used 123456K"，ZGC 输出 "used 368M, capacity 512M"，
# 只认 K 会让 ZGC 环境下的堆占用静默变成 0。
# 单位**只认 K/M/G**（大小写都收）。此前没有兜底分支，任何别的单位都落到"按 MB 原值输出"，
# 于是 `used 12X` 这种解析不出来的输入会安静地写出一个合法数字 12——本层的契约是
# "不猜"，所以未知单位（含没有单位）一律返回空串，由调用方把这次采样判为无效。
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
  case "$unit" in
    K|k|M|m|G|g) ;;
    *) return 0 ;;
  esac
  val="$(printf '%s' "$val" | sed -nE 's/^([0-9.]+).*/\1/p')"
  [[ -n "$val" ]] || return 0
  awk -v v="$val" -v u="$unit" 'BEGIN {
    if (u == "K" || u == "k") printf "%.0f", v / 1024;
    else if (u == "G" || u == "g") printf "%.0f", v * 1024;
    else printf "%.0f", v;
  }'
}

# top 的一行 CPU 汇总 → "user,sys,idle"（百分号已去掉）。
# macOS 是 "CPU usage: 2.5% user, 5.0% sys, 92.5% idle"，
# Linux 是 "%Cpu(s):  1.2 us,  0.3 sy, 98.5 id"，字段名与分隔符都不同，
# 因此按**字段名**（user/us 等）定位数值，而不是按位置。
# 逗号小数 locale 下（"2,5% user"）这里会得到非数值，调用方据此把该行判为无效采样——
# 失败方向是"不冒充有效数据"，而不是错位解析（采集器已把 LC_ALL 钉成 C，这只是第二道保险）。
parse_cpu_triple() {
  printf '%s\n' "$1" | awk '{
    u=""; s=""; d="";
    for (i = 1; i <= NF; i++) {
      t = $i; gsub(",", "", t);
      if (t == "user" || t == "us") u = $(i - 1);
      else if (t == "sys" || t == "sy") s = $(i - 1);
      else if (t == "idle" || t == "id") d = $(i - 1);
    }
    gsub("%", "", u); gsub("%", "", s); gsub("%", "", d);
    print u "," s "," d
  }'
}

# uptime 的一行 → "load1 load5"（中间一个空格；两个都取不到时输出空串）
parse_load_pair() {
  printf '%s\n' "$1" | sed -nE 's/.*load averages?: *([0-9.]+),? *([0-9.]+).*/\1 \2/p'
}

# vm_stat 首行自报的页大小（字节）。Apple Silicon 是 16384 而非 4096，
# 写死 4096 会让内存用量恒定偏小 4 倍（实测：63% 被写成 16%）。
parse_vm_page_size() {
  printf '%s\n' "$1" | sed -nE 's/.*page size of ([0-9]+) bytes.*/\1/p' | head -1
}

# vm_stat 全量输出 + 页大小 → 已用 MB（active + wired + compressor 的页数 × 页大小）。
# 三段都必须是**数值**且都命中，页大小也必须是数值，缺一就返回空串：
#   - 早先只判"至少命中一段"（seen），`vm_stat` 被裁成两行时会把缺的两段当 0 相加，
#     写出一个偏小的合法数字，那行还会被判为有效采样；
#   - 页大小传空（vm_stat 表头没解析出来、sysctl 也失败）时，awk 里 `ps` 空串按 0 参与
#     乘法 → 输出 "0"，一份"内存用了 0MB"的合法数值。Apple Silicon 的页大小是 16384，
#     猜 4096 会让内存偏小 4 倍——所以这里对页大小同样只接受数值，不给任何默认值。
parse_vm_used_mb() {
  is_num "$2" || return 0
  printf '%s\n' "$1" | awk -v ps="$2" '
    function ok(x) { return x ~ /^[0-9]+\.?$/ }
    /Pages active/ {a=$3; na=ok(a)}
    /Pages wired/ {w=$4; nw=ok(w)}
    /Pages occupied by compressor/ {c=$5; nc=ok(c)}
    END { if (na && nw && nc) printf "%.0f", (a+w+c)*ps/1048576 }'
}

# sysctl -n hw.memsize 的字节数 → MB。空输入（sysctl 不可用、键不存在）时输出空串，
# 而不是让 awk 把空记录当 0 算出一个"总内存 0MB"。
# 只接受**单个纯十进制整数**：`NF { $1/1048576 }` 这种写法会把 "foo" 算成 0 并输出 "0"，
# 而 0 是合法数字——调用方的 is_num 放行它，于是一份解析失败的 `sysctl hw.memsize`
# 会被当成"总内存 0MB"写进证据包，而且是有效采样行。多字段（"123 abc"）同样拒绝：
# 那说明命令根本不是我们以为的那条，取第一个字段等于猜。
parse_memsize_mb() {
  printf '%s\n' "$1" | awk 'NF == 1 && $1 ~ /^[0-9]+$/ { printf "%.0f", $1/1048576 }'
}

# jstat -gc 输出 → "YGC,YGCT,FGC,FGCT,CGC,CGCT,GCT,堆已用MB,堆已提交MB"。
# 按**表头名**取值而不是按列序号：G1 与 ZGC 的列含义不同（ZGC 在 Java 21 下 YGC/FGC 恒为
# "-"，真实回收在 CGC 列），写死列号会把 ZGC 的 GC 次数全部读成空。
# 堆 = S0U+S1U+EU+OU，提交 = S0C+S1C+EC+OC（ZGC 下等价于 OU/OC）。
parse_jstat_gc() {
  printf '%s\n' "$1" | awk '
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
    }' | head -1
}

# mysql 输出（每行 "变量名,值"）→ "threads_connected,threads_running,slow_queries,
# innodb_row_lock_waits,aborted_connects,questions"。
# 按**变量名**取值落到固定列序，绝不按行序：SHOW GLOBAL STATUS 是按变量名字母序返回的
# （实测顺序 Aborted_connects, Innodb_row_lock_waits, Questions, Slow_queries,
#  Threads_connected, Threads_running），按位置对应会把 Aborted_connects 写进
# threads_connected、把 Questions（示例库 32 万+）写进 slow_queries——静默产出
# "慢查询 31 万次"，且不报错。
parse_mysql_status_cols() {
  printf '%s\n' "$1" | awk -F',' '
    NF >= 2 { v[$1] = $2 }
    END { printf "%s,%s,%s,%s,%s,%s", v["Threads_connected"], v["Threads_running"], v["Slow_queries"], v["Innodb_row_lock_waits"], v["Aborted_connects"], v["Questions"] }'
}

# redis INFO 输出（"字段:值" 逐行）→ "connected_clients,used_memory_mb,instantaneous_ops_per_sec,keyspace_hits,keyspace_misses"
parse_redis_info_cols() {
  local info="$1" clients mem ops hits misses
  clients="$(printf '%s\n' "$info" | sed -nE 's/^connected_clients:([0-9]+).*/\1/p')"
  mem="$(printf '%s\n' "$info" | sed -nE 's/^used_memory:([0-9]+).*/\1/p' | awk '{printf "%.1f", $1/1048576}')"
  ops="$(printf '%s\n' "$info" | sed -nE 's/^instantaneous_ops_per_sec:(-?[0-9]+).*/\1/p')"
  hits="$(printf '%s\n' "$info" | sed -nE 's/^keyspace_hits:([0-9]+).*/\1/p')"
  misses="$(printf '%s\n' "$info" | sed -nE 's/^keyspace_misses:([0-9]+).*/\1/p')"
  printf '%s,%s,%s,%s,%s' "${clients:-}" "${mem:-}" "${ops:-}" "${hits:-}" "${misses:-}"
}

# ── 自检：把上面每个"曾经静默错过的输入"钉成断言 ────────────────────────────
# 用合成文本跑，不需要服务、网络或真进程，因此可以在门禁与任何机器上执行：
#   bash scripts/performance/monitor-parse.sh --self-test
# 每个断言都对应一个真实踩过的坑，注释写明是哪个，避免以后有人"顺手简化"回去。
monitor_parse_self_test() {
  local _fail=0 _n=0
  _check() { # <用例名> <期望> <实际>
    _n=$((_n + 1))
    if [[ "$2" == "$3" ]]; then
      printf '  [PASS] %s\n' "$1"
    else
      printf '  [FAIL] %s  期望=[%s] 实际=[%s]\n' "$1" "$2" "$3"
      _fail=$((_fail + 1))
    fi
  }

  # ZGC 的堆行：used 必须取 364，committed 取 capacity 420，而不是 max capacity 512
  _check "parse_size_mb ZGC used" "364" \
    "$(parse_size_mb ' ZHeap           used 364M, capacity 420M, max capacity 512M' 'used')"
  _check "parse_size_mb ZGC capacity" "420" \
    "$(parse_size_mb ' ZHeap           used 364M, capacity 420M, max capacity 512M' 'capacity')"
  # G1 的 K 单位
  _check "parse_size_mb G1 K" "121" \
    "$(parse_size_mb ' garbage-first heap   total 524288K, used 123456K' 'used')"
  _check "parse_size_mb 空行" "" "$(parse_size_mb '' 'used')"
  # 未知单位（jcmd 只可能给 K/M/G，出现别的就是没解析对）不按 MB 原值输出，留空不猜
  _check "parse_size_mb 未知单位留空" "" "$(parse_size_mb ' ZHeap used 12X, capacity 20M' 'used')"
  _check "parse_size_mb 无单位留空" "" "$(parse_size_mb ' ZHeap used 12, capacity 20M' 'used')"
  _check "parse_size_mb G 单位" "1024" "$(parse_size_mb ' ZHeap used 1G' 'used')"
  # top 的两种平台写法
  _check "parse_cpu_triple Darwin" "2.5,5.0,92.5" \
    "$(parse_cpu_triple 'CPU usage: 2.5% user, 5.0% sys, 92.5% idle')"
  _check "parse_cpu_triple Linux" "1.2,0.3,98.5" \
    "$(parse_cpu_triple '%Cpu(s):  1.2 us,  0.3 sy, 98.5 id,  0.0 wa,  0.0 hi,  0.0 si,  0.0 st')"
  # 逗号小数 locale：宁可得到非数值（调用方判该次采样无效），也不能错位成 25
  _check "parse_cpu_triple 逗号小数不冒充数值" "2,5,5,0,92,5" \
    "$(parse_cpu_triple 'CPU usage: 2,5% user, 5,0% sys, 92,5% idle')"
  _check "is_num 拒逗号小数" "1" "$(is_num '2,5' && echo 0 || echo 1)"
  # uptime 两种措辞
  _check "parse_load_pair 常规" "2.11 3.02" \
    "$(parse_load_pair '14:03  up 3 days,  2 users,  load averages: 2.11 3.02 3.41')"
  _check "parse_load_pair Linux" "0.52 0.58" \
    "$(parse_load_pair ' 14:03:01 up 3 days,  2 users,  load average: 0.52, 0.58, 0.59')"
  _check "parse_load_pair 无匹配" "" "$(parse_load_pair 'no load here')"
  # Apple Silicon 页大小
  _check "parse_vm_page_size 16384" "16384" \
    "$(parse_vm_page_size 'Mach Virtual Memory Statistics: (page size of 16384 bytes)')"
  _check "parse_vm_used_mb" "1094" \
    "$(parse_vm_used_mb 'Pages active: 35000.
Pages wired down: 20000.
Pages occupied by compressor: 15000.' 16384)"
  # 三段都没匹配上时留空，而不是写出"用了 0MB"这种合法数值
  _check "parse_vm_used_mb 无匹配留空" "" "$(parse_vm_used_mb 'nothing here' 16384)"
  # 只命中两段（vm_stat 输出被裁）同样留空：把缺的那段当 0 相加会得到一个偏小的合法数字
  _check "parse_vm_used_mb 缺一段留空" "" \
    "$(parse_vm_used_mb 'Pages active: 35000.
Pages wired down: 20000.' 16384)"
  # 页大小为空（vm_stat 表头没解析出来、sysctl 也失败）→ 留空。
  # 空串在 awk 里按 0 参与乘法，会写出"内存用了 0MB"；猜 4096 则让 Apple Silicon 偏小 4 倍。
  _check "parse_vm_used_mb 页大小为空留空" "" \
    "$(parse_vm_used_mb 'Pages active: 35000.
Pages wired down: 20000.
Pages occupied by compressor: 15000.' '')"
  _check "parse_vm_used_mb 计数非数值留空" "" \
    "$(parse_vm_used_mb 'Pages active: -.
Pages wired down: 20000.
Pages occupied by compressor: 15000.' 16384)"
  _check "parse_memsize_mb" "32768" "$(parse_memsize_mb '34359738368')"
  _check "parse_memsize_mb 空输入留空" "" "$(parse_memsize_mb '')"
  # 非数值输入必须留空。`NF { $1/1048576 }` 会把 "foo" 算成 0 并输出 "0"，而 0 是合法数字、
  # is_num 放行 → 一份解析失败的 sysctl 被记成"总内存 0MB"且算有效采样。
  _check "parse_memsize_mb 非数值留空" "" "$(parse_memsize_mb 'foo')"
  # 多字段说明命令不是我们以为的那条（例如把键名一起打了出来），取第一个字段等于猜
  _check "parse_memsize_mb 多字段留空" "" "$(parse_memsize_mb 'hw.memsize: 34359738368')"
  _check "parse_memsize_mb 带单位留空" "" "$(parse_memsize_mb '34359738368B')"
  # jstat：G1 实测表头（Java 21），按表头名取 YGC/FGC；堆取 S0U+S1U+EU+OU = 3072K = 3MB，
  # 提交取 S0C+S1C+EC+OC = 14336K = 14MB
  _check "parse_jstat_gc G1" "12,1.230,3,4.560,,,5.790,3,14" \
    "$(parse_jstat_gc ' S0C    S1C    S0U    S1U      EC       EU        OC         OU       MC     MU    CCSC   CCSU   YGC     YGCT    FGC    FGCT    CGC    CGCT     GCT
1024.0 1024.0   0.0    0.0   4096.0   2048.0   8192.0     1024.0   51200.0 49200.0 6144.0 5888.0     12    1.230     3    4.560    -       -    5.790')"
  # jstat：ZGC 在 Java 21 下 YGC/FGC 恒为 "-"（必须留空，不能写 0），真实回收在 CGC
  _check "parse_jstat_gc ZGC" ",,,,7,0.890,1.230,2,8" \
    "$(parse_jstat_gc ' S0C    S1C    S0U    S1U      EC       EU        OC         OU       MC     MU    CCSC   CCSU   YGC     YGCT    FGC    FGCT    CGC    CGCT     GCT
   -      -      -      -       -        -     8192.0     2048.0   51200.0 49200.0 6144.0 5888.0     -        -      -        -     7    0.890    1.230')"
  # SHOW GLOBAL STATUS 是字母序返回的：按行序取值会把 Questions 写进 slow_queries
  _check "parse_mysql_status_cols 按名取值" "5,2,7,0,1,330000" \
    "$(parse_mysql_status_cols 'Aborted_connects,1
Innodb_row_lock_waits,0
Questions,330000
Slow_queries,7
Threads_connected,5
Threads_running,2')"
  _check "parse_mysql_status_cols 缺变量留空" "5,2,,,,330000" \
    "$(parse_mysql_status_cols 'Questions,330000
Threads_connected,5
Threads_running,2')"
  # redis INFO
  _check "parse_redis_info_cols" "3,12.5,150,900,100" \
    "$(parse_redis_info_cols '# Server
redis_version:7.2.0
connected_clients:3
used_memory:13107200
instantaneous_ops_per_sec:150
keyspace_hits:900
keyspace_misses:100')"
  _check "parse_redis_info_cols 缺字段留空" ",,150,," \
    "$(parse_redis_info_cols 'instantaneous_ops_per_sec:150')"
  _check "clean_reason 逗号转分号" "a;b" "$(clean_reason 'a,b

')"
  _check "has_cmd 存在" "0" "$(has_cmd sh && echo 0 || echo 1)"
  _check "has_cmd 不存在" "1" "$(has_cmd definitely-not-a-command-xyz && echo 0 || echo 1)"

  if [[ "$_fail" == "0" ]]; then
    printf 'monitor-parse: 自检通过（%d/%d）\n' "$_n" "$_n"
    return 0
  fi
  printf 'monitor-parse: 自检失败 %d/%d\n' "$_fail" "$_n" >&2
  return 1
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  case "${1:-}" in
    --self-test|"")
      monitor_parse_self_test
      exit $?
      ;;
    *) echo "monitor-parse: 用法 monitor-parse.sh [--self-test]" >&2; exit 2 ;;
  esac
fi
