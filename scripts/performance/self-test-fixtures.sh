#!/usr/bin/env bash
# Input: 无参数。本文件**不单独执行**，由 exit-code-self-test.sh 在开头 source；
#        单独执行只会打印一句提示并以 exit 2 退出（避免被误当成一个"跑不起来的自检"）。
# Output: 定义好环境替身与断言工具后返回：SANDBOX / STUB / BADNODE / BROKENNODE / HANGNODE /
#         REALNODE_DIR / SHADOW、check() / run_dir_of() / run_runner() / log_has()，以及 PASS / FAIL / N。
# Pos: scripts/performance/ - 退出码回归的**夹具层**："把外部世界换掉"只在这里做一次。
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：本层原先与断言同在一个文件里，涨到 360+ 行（`scripts/line-budget.config.json` 按
# 扩展名/前缀过滤，`.sh` 与 `scripts/` 都在过滤之外，所以 300 行不是门禁红线，是本仓库对 `src/` 与
# 后端源码的可读性口径；下面按同一口径自觉收在 300 以内）。拆开后各自可读：断言文件只剩"期望什么"。
# 本层存在的理由（也是它必须"健全"的理由）：一条自检如果去调宿主的 top/uptime/sysctl、或依赖宿主的
# node 能不能跑，结论就随机器变红变绿。因此外部命令全部换成固定输出的替身、node 由夹具自带，并在
# fixture_self_check() 里先证明**替身自己**不会自递归。

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

# 监控侧的替身：值全部**固定**，形状是 monitor-parse.sh 判据认得的那几种（CPU 三元组、load 二元组、hw.memsize、vm_stat 页数），因此"这次采样算不算有效行"不取决于跑它的机器。
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
# 三段都必须出现：parse_vm_used_mb 只命中两段时返回空串（缺的那段当 0 相加会写出偏小的合法数字）
cat <<'OUT'
Mach Virtual Memory Statistics: (page size of 16384 bytes)
Pages active:                          35000.
Pages wired down:                      20000.
Pages occupied by compressor:          15000.
OUT
STUB_VMSTAT

chmod +x "${STUB}/k6" "${STUB}/curl" "${STUB}/mysql" "${STUB}/redis-cli" \
         "${STUB}/uname" "${STUB}/top" "${STUB}/uptime" "${STUB}/sysctl" "${STUB}/vm_stat"

# 替身目录在**最前面**，且要先于"无 node 的影子 PATH"建立：影子按当前 PATH 逐项软链，先放入替身目录，用例 5/5b 才同样跑在"外部命令全是替身、只差 node"的环境里。
export PATH="${STUB}:${PATH}"

# 有界地跑一条命令并返回它的退出码；超时按 124（与 `timeout(1)` 一致），输出留在 ${SANDBOX}/probe-<标签>.txt；必须先于 find_real_node 定义——探测候选 node 也要有界。
bounded_rc() { # <标签> <秒> <命令...>
  local _label="$1" _secs="$2"
  shift 2
  local _pid _ticks=0 _max=$((_secs * 2))
  "$@" > "${SANDBOX}/probe-${_label}.txt" 2>&1 &
  _pid=$!
  while kill -0 "$_pid" 2>/dev/null; do
    if [[ "$_ticks" -ge "$_max" ]]; then
      kill -9 "$_pid" 2>/dev/null
      wait "$_pid" 2>/dev/null
      printf '124'
      return 0
    fi
    sleep 0.5
    _ticks=$((_ticks + 1))
  done
  wait "$_pid"
  printf '%s' "$?"
}

# 宿主可能把 node 定义成**导出的 shell 函数**（沙箱与版本管理器会这么做）：函数遮蔽整个 PATH 查找，
# "夹具自带的 node 置于 PATH 首位"与"影子 PATH 里没有 node"两条构造会同时失效（实测 10/32 失败）。
# unset -f 之后连子进程也不再继承它（实测子 shell 里 `command -v node` 从 `node` 变回真实二进制）。
unset -f node 2>/dev/null || true
unalias node 2>/dev/null || true

# 替身里的"真实 node"必须是**绝对路径**，不能写成 `exec "$(command -v node)"`：`command -v` 对 shell
# 函数/别名返回的是**裸名**，宿主把 node 定义成函数时这行就退化成 `exec node`，而此时的 PATH 首选正是
# 替身自己——无限 exec 自身，整条用例挂死（实测：`node(){ … }; export -f node` 后 8s 仍不返回）。因此
# 直接扫 PATH 上的可执行文件，绕开函数/别名/builtin。但**绝对路径 ≠ 不是 shim**：按 PATH 再解析 node 的
# shim，exec 它同样回到自己。所以拿到候选后**问它自己**（`process.execPath` = 当前真正在跑的那个二进制），
# 且这一问也必须**有界**：答不上来就换下一个。
find_real_node() {
  local d cand
  local old_ifs="$IFS"
  IFS=:
  for d in $PATH; do
    # 空段在 PATH 里表示当前目录，这里不认它（与"必须能找到真正的 node"这一前提不符）。
    if [[ -n "$d" && -x "${d}/node" && ! -d "${d}/node" ]]; then
      cand="${d}/node"
      if [[ "$(bounded_rc nodeprobe 10 "$cand" -e 'process.stdout.write(process.execPath)')" == "0" ]]; then
        IFS="$old_ifs"
        printf '%s' "$(cat "${SANDBOX}/probe-nodeprobe.txt")"
        return 0
      fi
    fi
  done
  IFS="$old_ifs"
  return 1
}

# 坏扫描器："扫描器无法启动"必须报 1（工具链不可用）而不是 12（证据不齐）——补采永远补不齐它。
BADNODE="${SANDBOX}/badnode"
REAL_NODE="$(find_real_node || true)"
if [[ -z "$REAL_NODE" ]]; then
  # 不静默退化：没有真实 node 时，"扫描器崩掉"与"根本没有 node"无法区分，用例 6 会变成一条看不出所以然的失败。
  echo "exit-code-self-test: 宿主 PATH 上找不到可执行的 node 程序，无法构造「扫描器崩溃」用例。" >&2
  echo "                    本回归需要真实 node 来跑脱敏/清单脚本；缺少它时用例 6 无法成立。" >&2
  exit 1
fi

# 夹具**自带**一个能跑的 node 并置于 PATH 首位：宿主的 node 可能是坏的（shim 按 PATH 解析回自己、软链
# 指向已删二进制、被定义成 shell 函数），那与本回归要钉的退出码契约无关，却会让每个用例各自撞在宿主的
# 病上——结论随机器变红变绿，正是本层要消灭的东西。用的是 find_real_node 走 `process.execPath` 问出来的
# **验证过能跑**的那一个，且只放 node 一个名字，不遮蔽替身。
REALNODE_DIR="${SANDBOX}/realnode"
mkdir -p "$REALNODE_DIR"
ln -s "$REAL_NODE" "${REALNODE_DIR}/node"
export PATH="${REALNODE_DIR}:${PATH}"
mkdir -p "${BADNODE}"
cat > "${BADNODE}/node" <<STUB_NODE
#!/usr/bin/env bash
# 只拦脱敏/扫描脚本；其余调用转给真实 node（绝对路径，理由见创建处的注释）。
case "\$*" in *scrub-secrets.mjs*) exit 137 ;; esac
# 递归保险丝：本替身只允许被进入一次。exec 目标若又落回自己（裸名与 shim 两个老坑），第二次进入立刻自报并 exit 3——否则那是**永不返回**的 exec 链。
if [[ -n "\${XIYU_BADNODE_ENTERED:-}" ]]; then
  echo "badnode stub: 检测到自递归（exec 又回到了自己）" >&2
  exit 3
fi
export XIYU_BADNODE_ENTERED=1
exec "${REAL_NODE}" "\$@"
STUB_NODE
chmod +x "${BADNODE}/node"

# "node 找得到但跑不动"的替身：能被执行、一跑就失败（版本管理器卸载后残留的 shim 就是这样）。刻意
# **不做**任何转发——转发会引入"exec 回自己"的递归（BADNODE 踩过这个坑）。退出码取 3，与 runner 自己
# 的 1/2/12 都不同，万一被透传出来一眼认得出来。
BROKENNODE="${SANDBOX}/brokennode"
mkdir -p "${BROKENNODE}"
cat > "${BROKENNODE}/node" <<'STUB_BROKEN_NODE'
#!/usr/bin/env bash
echo "node: 这是一个坏掉的 shim（自检替身）" >&2
exit 3
STUB_BROKEN_NODE
chmod +x "${BROKENNODE}/node"

# "node 找得到、但调用它就挂住"的替身。与 BROKENNODE 是**两种不同的病**：那个立刻以 3 失败（runner 报告即可），这个永不返回（runner 必须自己有上界，否则一个退出码都给不出来）。
HANGNODE="${SANDBOX}/hangnode"
mkdir -p "${HANGNODE}"
cat > "${HANGNODE}/node" <<'STUB_HANG_NODE'
#!/usr/bin/env bash
echo "node: 这是一个永不返回的 shim（自检替身）" >&2
while :; do :; done
STUB_HANG_NODE
chmod +x "${HANGNODE}/node"

# 无 node 的 PATH：把真实 PATH 里的每一项都软链过去，唯独漏掉 node（不用"删掉某个目录"的做法——那里还住着几百个 runner 需要的工具）。
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

# 夹具自检：替身自身必须健全，否则"扫描器崩掉"这条用例会以挂死（124）而不是 1 失败，而 124 看上去像环境问题、不像缺陷。四条断言都不依赖计时。
fixture_self_check() {
  check "夹具自带的 node 在 PATH 首位（宿主 node 的好坏不参与结论）" "${REALNODE_DIR}/node" "$(command -v node)"
  check "坏扫描器替身内嵌绝对路径（不依赖 command -v 的裸名）" "yes" \
    "$(grep -qE '^exec "/' "${BADNODE}/node" && echo yes || echo no)"
  # 造一个**真的**会 exec 回自己的替身：把 exec 目标换成裸名 node，此时 PATH 首选正是它自己。期望 3（保险丝自报），不是 124（挂住）。
  local rec="${SANDBOX}/recnode"
  mkdir -p "$rec"
  sed 's|^exec ".*" "\$@"$|exec node "$@"|' "${BADNODE}/node" > "${rec}/node"
  chmod +x "${rec}/node"
  check "自递归的替身立刻自报（不靠超时发现）" "3" \
    "$(PATH="${rec}:${PATH}" bounded_rc rec 20 "${rec}/node" -e 'process.exit(0)')"
  # 上界给 60s：本机若连"起一个 node 子进程"都要超时，下面每个用例（各自还要再起 bash + 若干替身）都只会给出与缺陷无关的 124——那是**宿主**起不来进程，不是替身的失败。
  local rc
  rc="$(PATH="${BADNODE}:${PATH}" bounded_rc stub 60 "${BADNODE}/node" -e 'process.stdout.write("stub-ok")')"
  if [[ "$rc" == "124" ]]; then
    echo "exit-code-self-test: 本机 60s 内没能跑完「起一个 node 子进程」这一步；按「工具链在本机" >&2
    echo "                    不可用」记 exit 1，请换机器或解除进程数/负载限制后重跑。" >&2
    exit 1
  fi
  check "坏扫描器替身能把非扫描调用转给真实 node" "stub-ok" "$(cat "${SANDBOX}/probe-stub.txt")"
}

# ── 运行一个用例并打印退出码（环境变量由调用方在子 shell 里设置）─────────────────
# 每个用例都有**上界**（CASE_TIMEOUT 秒）：一条挂住的回归会拖住整轮 S5，而"没有输出的等待"与"正在跑"
# 在日志里长得一模一样。超时按 124 上报（与 `timeout(1)` 一致）并打印到 stderr，让它以 [FAIL] 立刻暴露。
# 取值必须**大于 runner 自己的合法上界**：runner 内部有一处 30s 的 node 探测上界（run-preflight.sh）
# 加两处各 30s 的有界等待（等采集器 .ready + 等采集器退出，见 monitor-lifecycle.sh），合计可达 90s；
# 45s（更早一版的取值）落在其中，沙箱/负载下进程启动慢一点就稳定误报 124——那正是"回归随环境
# 变红变绿"的另一种形态。180s ≈ 两倍余量；用例 5g 会真的用掉 30s（探测超时），也在其内。
CASE_TIMEOUT="${CASE_TIMEOUT:-180}"
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
