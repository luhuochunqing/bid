#!/usr/bin/env bash
# Input: 无参数。本文件**不单独执行**，由 exit-code-self-test.sh 在开头 source；
#        单独执行只会打印一句提示并以 exit 2 退出（避免被误当成一个"跑不起来的自检"）。
# Output: 定义好环境替身与断言工具后返回：SANDBOX / STUB / BADNODE / BROKENNODE / SHADOW、
#         check() / run_dir_of() / run_runner() / log_has()，以及 PASS / FAIL / N。
# Pos: scripts/performance/ - 退出码回归的**夹具层**："把外部世界换掉"只在这里做一次。
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：本层原先与断言同在一个文件里，涨到 360+ 行，超过项目 300 行的硬上限
# （scripts/line-budget.config.json 的 maxLines，注：该配置按扩展名/前缀过滤，`.sh` 与
# `scripts/` 都在过滤之外——所以这是可读性上限，不是门禁红线）。拆开后"造替身"与"断言"
# 各自可读：断言文件只剩"期望什么"，改替身不会碰到任何一条断言。
#
# 本层存在的理由（也是它必须"健全"的理由）：一条自检如果自己去调宿主的 top/uptime/sysctl、
# 或者依赖宿主的 node 能不能跑，那么它的结论就随机器变红变绿，回归会变成噪声。
# 因此这里把所有外部命令都换成固定输出的替身，并在 fixture_self_check() 里先证明
# **替身自己**不会自递归——否则"用例挂死"会被读成环境问题，而不是夹具缺陷。

set -uo pipefail

_FIX_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${_FIX_DIR}/../.." && pwd)"
PERF_DIR="${_FIX_DIR}"
RUNNER="${PERF_DIR}/run-stress-test.sh"

SANDBOX="$(mktemp -d "${TMPDIR:-/tmp}/xs-exit-codes.XXXXXX")"
trap 'rm -rf "${SANDBOX}"' EXIT

PASS=0
FAIL=0
N=0
check() { # <用例名> <期望> <实际>
  N=$((N + 1))
  if [[ "$2" == "$3" ]]; then
    printf '  [PASS] %s（实测 %s）\n' "$1" "$3"
    PASS=$((PASS + 1))
  else
    printf '  [FAIL] %s  期望 %s，实际 %s\n' "$1" "$2" "$3"
    FAIL=$((FAIL + 1))
  fi
}

# 一次运行的证据目录是 <--out>/<RUN_ID>/，RUN_ID 里带时间戳——测试不能假设完整路径。
run_dir_of() {
  local m
  m="$(find "$1" -maxdepth 2 -name manifest.json -print 2>/dev/null | head -1)"
  [[ -n "$m" ]] && dirname "$m"
}

# ── 替身：任何用例都不产生真实负载、不连任何服务 ──────────────────────────────
STUB="${SANDBOX}/bin"
mkdir -p "${STUB}"
cat > "${STUB}/k6" <<'STUB_K6'
#!/usr/bin/env bash
# k6 替身：按环境变量决定"写不写产物"与"以什么码退出"，用于制造 12 / k6 码两条分支。
if [[ "${1:-}" == "version" ]]; then echo "k6 v1.8.0 (exit-code-stub)"; exit 0; fi
summary=""; csv=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary-export=*) summary="${1#--summary-export=}" ;;
    --out) csv="${2#csv=}"; shift ;;
  esac
  shift
done
if [[ "${STUB_WRITE_ARTIFACTS:-1}" == "1" ]]; then
  [[ -n "$summary" ]] && printf '{"metrics":{"http_reqs":{"count":1}},"setup_data":{}}\n' > "$summary"
  [[ -n "$csv" ]] && printf 'metric_name,timestamp,metric_value\nhttp_req_duration,1,1\n' > "$csv"
fi
echo "     stub-k6: BASE_URL=${BASE_URL:-<unset>}"
exit "${STUB_K6_EXIT:-0}"
STUB_K6
for b in curl mysql redis-cli; do printf '#!/usr/bin/env bash\nexit 0\n' > "${STUB}/${b}"; done

# 监控侧的替身：值全部**固定**，且形状是 monitor-parse.sh 的判据认得的那几种
# （CPU 三元组、load 二元组、hw.memsize、vm_stat 页数），因此"这次采样算不算有效行"
# 不再取决于跑它的机器。
cat > "${STUB}/uname" <<'STUB_UNAME'
#!/usr/bin/env bash
# 固定成 Darwin：system 读取器按 uname 分叉，不固定的话同一份替身在不同平台上走不同分支。
echo Darwin
STUB_UNAME
cat > "${STUB}/top" <<'STUB_TOP'
#!/usr/bin/env bash
echo "CPU usage: 2.5% user, 5.0% sys, 92.5% idle"
STUB_TOP
cat > "${STUB}/uptime" <<'STUB_UPTIME'
#!/usr/bin/env bash
echo "14:03  up 3 days,  2 users,  load averages: 2.11 3.02 3.41"
STUB_UPTIME
cat > "${STUB}/sysctl" <<'STUB_SYSCTL'
#!/usr/bin/env bash
# 调用形态是 `sysctl -n <key>`（见 monitor-readers.sh 的 read_system）
case "${2:-}" in
  hw.memsize) echo 34359738368 ;;
  hw.pagesize) echo 16384 ;;
esac
STUB_SYSCTL
cat > "${STUB}/vm_stat" <<'STUB_VMSTAT'
#!/usr/bin/env bash
# 三段都必须出现：parse_vm_used_mb 只命中两段时返回空串（缺的那段当 0 相加会写出一个偏小的合法数字）
cat <<'OUT'
Mach Virtual Memory Statistics: (page size of 16384 bytes)
Pages active:                          35000.
Pages wired down:                      20000.
Pages occupied by compressor:          15000.
OUT
STUB_VMSTAT

chmod +x "${STUB}/k6" "${STUB}/curl" "${STUB}/mysql" "${STUB}/redis-cli" \
         "${STUB}/uname" "${STUB}/top" "${STUB}/uptime" "${STUB}/sysctl" "${STUB}/vm_stat"

# 替身目录在**最前面**，且要在建"无 node 的影子 PATH"之前生效：影子路径是从当前 PATH
# 逐项软链出来的，先放入替身目录，用例 5/5b 才同样跑在"外部命令全是替身、只差 node"的
# 环境里（否则那些用例会退回宿主真实的 k6 去连一个不存在的目标）。
export PATH="${STUB}:${PATH}"

# 坏扫描器：node 还在（替身自己就是 node），但调用脱敏/扫描脚本时自身崩掉（137 = 被 kill）。
# 这条路径对应"扫描器无法启动"，必须报 1 而不是 12。
#
# 替身里的"真实 node"必须是**绝对路径**，不能写成 `exec "$(command -v node)"`：
# `command -v` 对 shell 函数/别名返回的是**裸名**，一旦宿主把 node 定义成函数（某些沙箱与
# 版本管理器会这么做），这行就退化成 `exec node`，而此时的 PATH 首选正是替身自己 —— 变成
# 无限 exec 自身，整条用例挂死到超时（实测：`node(){ … }; export -f node` 之后构造替身，
# 替身内容为 `exec "node" "$@"`，调用它 8s 后仍不返回、按 124 上报）。
# 因此这里直接扫 PATH 上的可执行文件，绕开函数/别名/builtin，只认真正的程序。
find_real_node() {
  local d
  local old_ifs="$IFS"
  IFS=:
  for d in $PATH; do
    # 空段在 PATH 里表示当前目录，这里不认它（与"必须能找到真正的 node"这一前提不符）。
    if [[ -n "$d" && -x "${d}/node" && ! -d "${d}/node" ]]; then
      IFS="$old_ifs"
      printf '%s' "${d}/node"
      return 0
    fi
  done
  IFS="$old_ifs"
  return 1
}

BADNODE="${SANDBOX}/badnode"
REAL_NODE="$(find_real_node || true)"
if [[ -z "$REAL_NODE" ]]; then
  # 不静默退化：没有真实 node 时，"扫描器崩掉"与"根本没有 node"两种失败无法区分，
  # 用例 6 会变成一条看不出所以然的失败。直接拒绝执行，把前提说清楚。
  echo "exit-code-self-test: 宿主 PATH 上找不到可执行的 node 程序，无法构造「扫描器崩溃」用例。" >&2
  echo "                    本回归需要真实 node 来跑脱敏/清单脚本；缺少它时用例 6 无法成立。" >&2
  exit 1
fi
mkdir -p "${BADNODE}"
cat > "${BADNODE}/node" <<STUB_NODE
#!/usr/bin/env bash
# 只拦脱敏/扫描脚本；其余调用转给真实 node（绝对路径，理由见创建处的注释）。
case "\$*" in *scrub-secrets.mjs*) exit 137 ;; esac
exec "${REAL_NODE}" "\$@"
STUB_NODE
chmod +x "${BADNODE}/node"

# "node 找得到但跑不动"的替身：能被执行、一跑就失败（版本管理器卸载后残留的 shim 就是这样）。
# 刻意**不做**任何转发——转发会引入"exec 回自己"的递归（BADNODE 踩过这个坑，见上面的注释）。
# 退出码取 3：与 runner 自己的 1/2/12 都不同，万一被透传出来一眼认得出来。
BROKENNODE="${SANDBOX}/brokennode"
mkdir -p "${BROKENNODE}"
cat > "${BROKENNODE}/node" <<'STUB_BROKEN_NODE'
#!/usr/bin/env bash
echo "node: 这是一个坏掉的 shim（自检替身）" >&2
exit 3
STUB_BROKEN_NODE
chmod +x "${BROKENNODE}/node"

# 无 node 的 PATH：把真实 PATH 里的每一项都软链过去，唯独漏掉 node。
# 不用"删掉某个目录"的做法——node 所在的目录里还住着几百个 runner 需要的工具。
build_shadow_path() {
  local shadow="${SANDBOX}/no-node" d f b
  mkdir -p "$shadow"
  local old_ifs="$IFS"
  IFS=:
  for d in $PATH; do
    [[ -d "$d" ]] || continue
    for f in "$d"/*; do
      b="${f##*/}"
      [[ "$b" == node ]] && continue
      [[ -d "$f" ]] && continue
      [[ -x "$f" ]] || continue
      if [[ ! -e "${shadow}/${b}" && ! -L "${shadow}/${b}" ]]; then ln -s "$f" "${shadow}/${b}"; fi
    done
  done
  IFS="$old_ifs"
  printf '%s' "$shadow"
}
SHADOW="$(build_shadow_path)"

# 夹具自检：替身自身必须健全，否则"扫描器崩掉"这条用例会以挂死（124）而不是 1 的形式失败，
# 而 124 看上去像环境问题、不像缺陷。两条断言分别钉住：内嵌的是绝对路径，且它在
# **与用例 6 相同的调用环境**（替身目录在 PATH 最前）下真的有界返回 —— 换成裸名的替身时，
# 第一条报 not-absolute，第二条会在这里拿到 <TIMEOUT>。
fixture_self_check() {
  check "坏扫描器替身内嵌绝对路径（不依赖 command -v 的裸名）" "yes" \
    "$(grep -qE '^exec "/' "${BADNODE}/node" && echo yes || echo no)"
  local out="${SANDBOX}/stub-probe-out.txt"
  local pid value ticks=0
  PATH="${BADNODE}:${PATH}" "${BADNODE}/node" -e 'process.stdout.write("stub-ok")' > "$out" 2>&1 &
  pid=$!
  while kill -0 "$pid" 2>/dev/null && [[ "$ticks" -lt 20 ]]; do
    sleep 0.5
    ticks=$((ticks + 1))
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null
    wait "$pid" 2>/dev/null
    value="<TIMEOUT：替身在 10s 内没有返回，很可能是 exec 回了自己>"
  else
    wait "$pid" 2>/dev/null
    value="$(cat "$out")"
  fi
  check "坏扫描器替身能把非扫描调用转给真实 node" "stub-ok" "$value"
}

# ── 运行一个用例并打印退出码 ────────────────────────────────────────────────
# 环境变量（XIYU_STRESS_CONFIRMED / PATH / STUB_* 等）由调用方在子 shell 里设置。
#
# 每个用例都有**上界**（CASE_TIMEOUT 秒）：一条挂住的回归会拖住整轮 S5，而"没有输出的等待"
# 与"正在跑"在日志里长得一模一样。超时按 124 上报（与 `timeout(1)` 的约定一致），并打印到
# stderr —— 让它以 [FAIL] 的形式立刻暴露，而不是让整条命令卡住。
#
# 取值必须**大于 runner 自己的合法上界**，否则会把"慢"误报成"挂"：
# runner 内部有两处各 30s 的有界等待（等采集器 .ready 30s + 等采集器退出 30s，
# 见 monitor-lifecycle.sh），合计可达 60s；再叠上 k6 与归档的正常耗时。
# 45s（上一版的取值）落在 60s 之内 —— 沙箱/负载下进程启动慢一点就会稳定误报 124，
# 那正是"回归随环境变红变绿"的另一种形态。120s ≈ 两倍余量。
CASE_TIMEOUT="${CASE_TIMEOUT:-120}"
run_runner() { # <用例名> <runner 脚本> <参数...>
  local _label="$1" _runner="$2"
  shift 2
  local _dir="${SANDBOX}/runs/${_label}"
  rm -rf "$_dir"
  bash "$_runner" "$@" --out "$_dir" --label "$_label" > "${SANDBOX}/log-${_label}.txt" 2>&1 &
  local _pid=$! _ticks=0 _max=$((CASE_TIMEOUT * 2))
  while kill -0 "$_pid" 2>/dev/null; do
    if [[ "$_ticks" -ge "$_max" ]]; then
      # 先 TERM 给 runner 机会停掉它的监控子进程，再 KILL。直接 -9 会留下一只还在采样的孤儿。
      kill -TERM "$_pid" 2>/dev/null
      sleep 2
      kill -9 "$_pid" 2>/dev/null
      wait "$_pid" 2>/dev/null
      echo "  [warn] 用例 ${_label} 超过 ${CASE_TIMEOUT}s 未结束，已终止（按超时上报）" >&2
      printf '124'
      return 0
    fi
    sleep 0.5
    _ticks=$((_ticks + 1))
  done
  wait "$_pid"
  printf '%s' "$?"
}
log_has() { grep -qF "$2" "${SANDBOX}/log-$1.txt"; }

# 被直接执行时（而不是被 source）给一句明确的提示：本文件没有断言，单独跑只会让人以为自检坏了。
if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "self-test-fixtures: 这是 exit-code-self-test.sh 的夹具层，不单独执行。" >&2
  echo "                    请运行：bash scripts/performance/exit-code-self-test.sh" >&2
  exit 2
fi
