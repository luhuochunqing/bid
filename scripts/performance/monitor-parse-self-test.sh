#!/usr/bin/env bash
# Input: 无参数。本文件**不单独执行**：由 monitor-parse.sh 在 `--self-test` 分支里 source
#        （那时上面那些纯解析函数已经定义好）。
# Output: 定义 monitor_parse_self_test()；跑完打印"自检通过（N/N）"并以 0/1 返回。
# Pos: scripts/performance/ - 采集解析层的**自检层**：把 monitor-parse.sh 每个"曾经静默错过的输入"钉成断言。
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：monitor-parse.sh 涨到 330+ 行，其中一半是断言。拆开后"解析"与"断言"各自可读，
# 与本目录 exit-code-self-test.sh / self-test-fixtures.sh 的拆法一致。仓库的 line-budget 门禁按
# 扩展名/前缀过滤，`.sh` 与 `scripts/` 都在过滤之外，300 行不是门禁红线，是本仓库对 `src/` 与后端
# 源码的可读性口径；本文件按同一口径自觉收在 300 以内。
#
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
  # Linux 的 top 在数值达到 100.0 时不补分隔符后的空格："0.0 ni,100.0 id"。
  # 按"标签前一个字段"取值会拿到 "ni,100.0"，写进 CSV 就在那个逗号处裂成两列、整行作废
  # （实测宿主上 6 次采样废 3 次）。这里钉住"拼接到上一位的数值仍要被取出来"。
  _check "parse_cpu_triple Linux 100.0 紧贴上一位标签" "0.0,0.0,100.0" \
    "$(parse_cpu_triple '%Cpu(s):  0.0 us,  0.0 sy,  0.0 ni,100.0 id,  0.0 wa,  0.0 hi,  0.0 si,  0.0 st')"
  _check "parse_cpu_triple Linux 首字段也紧贴" "100.0,0.0,0.0" \
    "$(parse_cpu_triple '%Cpu(s):100.0 us,  0.0 sy,  0.0 id,  0.0 wa')"
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
  # ps 的两列：取不到值时同样必须留空（`NF { $1/1024 }` 会把 "foo" 算成 0，而 RSS=0 是合法数值，
  # read_jvm 拿堆/线程/RSS 三者判有效行 → 一条 RSS 解析失败的采样会被计入 USABLE_JVM）
  _check "parse_ps_rss_mb" "121" "$(parse_ps_rss_mb '123456')"
  _check "parse_ps_rss_mb 前后空白照常" "2" "$(parse_ps_rss_mb '  2048  ')"
  _check "parse_ps_rss_mb 非数值留空" "" "$(parse_ps_rss_mb 'foo')"
  _check "parse_ps_rss_mb 空输入留空" "" "$(parse_ps_rss_mb '')"
  _check "parse_ps_pct" "3.5" "$(parse_ps_pct '3.5')"
  _check "parse_ps_pct 非数值留空" "" "$(parse_ps_pct 'foo')"
  # jstat：G1 实测表头（Java 21），按表头名取 YGC/FGC；堆取 S0U+S1U+EU+OU = 3072K = 3MB，
  # 提交取 S0C+S1C+EC+OC = 14336K = 14MB
  _check "parse_jstat_gc G1" "12,1.230,3,4.560,,,5.790,3,14" \
    "$(parse_jstat_gc ' S0C    S1C    S0U    S1U      EC       EU        OC         OU       MC     MU    CCSC   CCSU   YGC     YGCT    FGC    FGCT    CGC    CGCT     GCT
1024.0 1024.0   0.0    0.0   4096.0   2048.0   8192.0     1024.0   51200.0 49200.0 6144.0 5888.0     12    1.230     3    4.560    -       -    5.790')"
  # jstat：ZGC 在 Java 21 下 YGC/FGC 恒为 "-"（必须留空，不能写 0），真实回收在 CGC
  _check "parse_jstat_gc ZGC" ",,,,7,0.890,1.230,2,8" \
    "$(parse_jstat_gc ' S0C    S1C    S0U    S1U      EC       EU        OC         OU       MC     MU    CCSC   CCSU   YGC     YGCT    FGC    FGCT    CGC    CGCT     GCT
   -      -      -      -       -        -     8192.0     2048.0   51200.0 49200.0 6144.0 5888.0     -        -      -        -     7    0.890    1.230')"
  # 堆字段非数值时必须**整项留空**：`kb()` 里的 `x + 0` 会把 "foo" 变成 0，而 0 是合法数字、
  # read_jvm 的 is_num 放行 → 一条解析失败的 jstat 被记成"堆已用 0MB"并计入有效采样。
  # 前 7 个 GC 字段本来就是原样透传（由 is_num 把关），这里钉的是 heap_used / heap_committed
  # 这两列——它们决定了 USABLE_JVM 是否自增。
  _check "parse_jstat_gc 堆字段非数值留空" ",,,,,,,," \
    "$(parse_jstat_gc ' S0C S1C S0U S1U EC EU OC OU YGC YGCT FGC FGCT CGC CGCT GCT
foo foo foo foo foo foo foo foo - - - - - - -')"
  _check "parse_jstat_gc 堆字段混入非数值留空" "1,2,3,4,5,6,7,," \
    "$(parse_jstat_gc ' S0C S1C S0U S1U EC EU OC OU YGC YGCT FGC FGCT CGC CGCT GCT
foo foo foo foo foo foo foo foo 1 2 3 4 5 6 7')"
  # 求和里只要有**一项**坏，该列就整列留空（不做"跳过坏项"的部分求和——那等于把 200MB
  # 的堆报成 100MB，比留空更危险）；两列各算各的：已用里混了 "n/a" → 留空，提交四项都好 → 照给。
  # read_jvm 只要求 heap_used 是数值，所以留空的已用列已足以让这行不算有效采样。
  _check "parse_jstat_gc 单个坏项整列留空" ",,,,,,,,14" \
    "$(parse_jstat_gc ' S0C S1C S0U S1U EC EU OC OU YGC YGCT FGC FGCT CGC CGCT GCT
1024 1024 0 0 4096 2048 8192 n/a - - - - - - -')"
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
  # 带口令时 redis-cli 的输出是"OK(AUTH) + OK(SELECT) + INFO 正文"混在一起（read_redis 用
  # 2>&1 把 stderr 也并了进来）。这四例把"什么才算报错原文"钉死：正常输出必须空手而归，
  # 报错必须原样透出——滤错方向决定的是"有数据却报错"还是"没数据却静默"。
  _check "parse_redis_error 正常输出不报错" "" \
    "$(parse_redis_error 'OK
OK
# Server
redis_version:6.2.19
connected_clients:3
used_memory:1610864')"
  # Keyspace 段是 db0:keys=…，键名里的数字必须算字段行；只收字母下划线的写法会把它当报错，
  # 于是一台只是"有键"的正常 Redis 每次采样都被判失败。
  _check "parse_redis_error Keyspace 数字键名不报错" "" \
    "$(parse_redis_error '# Keyspace
db0:keys=120,expires=0,avg_ttl=0')"
  _check "parse_redis_error NOAUTH 透出" "NOAUTH Authentication required." \
    "$(parse_redis_error 'NOAUTH Authentication required.')"
  _check "parse_redis_error 连不上透出" "Could not connect to Redis at 10.0.0.1:6379: Connection refused" \
    "$(parse_redis_error 'Could not connect to Redis at 10.0.0.1:6379: Connection refused')"
  # 空白行不算报错。上一版漏了这条：调用方拿 parse_redis_error 当判据，redis-cli 输出里
  # 一行空白就把一次**已经取到全部字段**的正常采样写成空行并计为失败（实测宿主 7 次全废，
  # component-status.csv 的原因列写着 `redis 采集失败:`，冒号后面什么都没有）。
  _check "parse_redis_error 空白行不报错" "" "$(parse_redis_error '

')"
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

# 被直接执行时（而不是被 source）给一句明确的提示：本文件只有断言，单独跑找不到被测函数，
# 报出来的会是一串 command not found，看上去像自检坏了。
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "monitor-parse-self-test: 这是 monitor-parse.sh 的自检层，不单独执行。" >&2
  echo "                        请运行：bash scripts/performance/monitor-parse.sh --self-test" >&2
  exit 2
fi
