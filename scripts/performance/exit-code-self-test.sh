#!/usr/bin/env bash
# Input: 无（纯离线：不连网络、不连数据库、不向任何环境施加负载）
#        **k6 / curl / mysql / redis-cli / top / uptime / sysctl / vm_stat / uname 全部是替身**——
#        监控侧也要替身：正向用例要求"监控有有效采样行"，而 system 读取器会去调宿主真实的
#        `top`/`uptime`/`sysctl`/`vm_stat`。只替身 k6 那一侧的话，本自检的结论就取决于跑它的
#        那台机器与那个 shell（沙箱里 `top` 取不到、Linux 上又走 `/proc` 分支），一条"回归"
#        会随环境变红变绿——那正是它上一版的实际缺陷（实测：受限沙箱里正向用例得到 12）。
# Output: 逐用例 PASS/FAIL 与汇总计数；全部通过 exit 0，任一失败 exit 1
# Pos: scripts/performance/ - runner **退出码分层**的离线回归：0 / k6 自身码 / 1（工具链不可用）/ 12（证据不齐）的边界
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么需要它：本节契约里最容易退化的不是"判得对不对"，而是**判错了报哪个码**。
# 三类失败指向完全不同的处置动作——
#   1  = 拒绝执行：压测机上没有 node / 扫描器或清单生成器起不来 / 结论不可解析 → 换环境重跑；
#   12 = 证据不齐：k6 跑完了，但产物缺、没有有效数据行、扫描发现残留 → 按缺件补采；
#   k6 自身码 = 证据本身没问题，只是门禁没过 → 看报告。
# 把 1 报成 12（早先的行为）会让操作者反复补采一个永远补不齐的包。这三种码必须各有一条用例钉住。
#
# 用例里对 TSV 的破坏一律发生在**沙箱副本**上：本脚本绝不修改仓库内的 delivery-blockers.tsv。

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PERF_DIR="${ROOT}/scripts/performance"
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

# 坏扫描器：node 还在（`command -v node` 成功），但调用脱敏/扫描脚本时自身崩掉（137 = 被 kill）。
# 这条路径对应"扫描器无法启动"，必须报 1 而不是 12。
BADNODE="${SANDBOX}/badnode"
mkdir -p "${BADNODE}"
cat > "${BADNODE}/node" <<STUB_NODE
#!/usr/bin/env bash
case "\$*" in *scrub-secrets.mjs*) exit 137 ;; esac
exec "$(command -v node)" "\$@"
STUB_NODE
chmod +x "${BADNODE}/node"

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

# ── 运行一个用例并打印退出码 ────────────────────────────────────────────────
# 环境变量（XIYU_STRESS_CONFIRMED / PATH / STUB_* 等）由调用方在子 shell 里设置。
#
# 每个用例都有**上界**（CASE_TIMEOUT 秒，实测每个用例数秒，45 秒是宽松兜底）：runner 内部
# 虽有监控子进程的有界等待，但一条挂住的回归会拖住整轮 S5，而"没有输出的等待"与"正在跑"
# 在日志里长得一模一样。超时按 124 上报（与 `timeout(1)` 的约定一致），并打印到 stderr——
# 让它以 [FAIL] 的形式立刻暴露，而不是让整条命令卡住。
CASE_TIMEOUT="${CASE_TIMEOUT:-45}"
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

export XIYU_STRESS_CONFIRMED=1
export XIYU_K6_USERS='[{"username":"probe","password":"probe-secret-value"}]'
LOOPBACK=(--profile smoke --base-url http://127.0.0.1:18999 --monitor-required-components system --monitor-interval 1)
REMOTE=(--profile smoke --base-url http://203.0.113.7:8080)

echo "== 1. 正向：证据齐全 → 0 =="
RC="$(run_runner ok "$RUNNER" "${LOOPBACK[@]}")"
check "正向端到端" 0 "$RC"
RUNDIR="$(run_dir_of "${SANDBOX}/runs/ok")"
MANIFEST="${RUNDIR}/manifest.json"
# 命中次数而不是"有没有这句话"：真出现两份 manifest 时，计数会立刻暴露出来。
check "manifest evidence_complete=true" "1" "$(grep -c '"evidence_complete": true' "$MANIFEST")"
check "扫描结论 passed" "1" "$(grep -c '"status": "passed"' "$MANIFEST")"
check "无交付阻塞项" "1" "$(grep -c '"delivery_blockers": \[\]' "$MANIFEST")"

echo "== 2. k6 门禁击穿但证据齐全 → k6 自身码 99 =="
export STUB_K6_EXIT=99
RC="$(run_runner k6fail "$RUNNER" "${LOOPBACK[@]}")"
check "k6 自身码透传" 99 "$RC"
unset STUB_K6_EXIT

echo "== 3. k6 没写出产物 → 12（证据不齐，工具链正常）=="
export STUB_WRITE_ARTIFACTS=0
RC="$(run_runner noart "$RUNNER" "${LOOPBACK[@]}")"
check "缺产物" 12 "$RC"
unset STUB_WRITE_ARTIFACTS

echo "== 4. --no-monitor → 12（交付阻塞项，产物齐全）=="
RC="$(run_runner nomon "$RUNNER" "${REMOTE[@]}" --no-monitor)"
check "交付阻塞项" 12 "$RC"

echo "== 5. 无 node → 1（工具链不可用，优先于 12）=="
# 前面不能写成 `PATH="$SHADOW" RC="$(...)"`：那是对**空命令**的两个赋值，PATH 会永久留在
# 当前 shell 里，后面所有用例都会在"没有 node"的环境里跑。
# 这里先 unset 掉账号池：带账号池时前置层就会因"无法校验 JSON"拒绝（见 5b），
# 而本用例要钉的是**归档层**那条工具链失败路径。
RC="$(unset XIYU_K6_USERS; PATH="$SHADOW" run_runner nonode "$RUNNER" "${LOOPBACK[@]}")"
check "无 node" 1 "$RC"
check "报的是工具链不可用" 1 "$(log_has nonode '工具链不可用' && echo 1 || echo 0)"

echo "== 5b. 无 node 且带账号池 → 1（前置层拒绝，不静默回退到默认账号池）=="
RC="$(PATH="$SHADOW" run_runner nonode-users "$RUNNER" "${LOOPBACK[@]}")"
check "无 node 无法校验账号池" 1 "$RC"
check "说明是校验不了账号池" 1 "$(log_has nonode-users '无法校验账号池' && echo 1 || echo 0)"

echo "== 5c. 账号池含省略号模板 → 2（k6 会静默回退到默认账号池）=="
# 赋值放在命令替换的子 shell 里执行：`VAR=x func` 这种前缀写法对**函数**而言是否会被还原
# 依赖 bash 的模式，写清楚不依赖它。
RC="$(export XIYU_K6_USERS='[{...},…]'; run_runner badusers "$RUNNER" "${LOOPBACK[@]}")"
check "非法账号池" 2 "$RC"
check "非法账号池未创建证据目录" "no" "$([[ -e "${SANDBOX}/runs/badusers" ]] && echo yes || echo no)"

echo "== 5d. 账号池**显式为空**（XIYU_K6_USERS 设为空串）→ 2（不许被当成「没提供」）=="
# 与环境变量未设置是两件事：未设置 = 操作者没打算给（文档已写明这时 k6 会用自己的默认池）；
# 设成空串 = 提供了但没提供成（CI secret 没注入、配置被清空），放行同样是静默回退。
RC="$(export XIYU_K6_USERS=''; run_runner emptyusers "$RUNNER" "${LOOPBACK[@]}")"
check "空环境变量" 2 "$RC"
check "空环境变量未创建证据目录" "no" "$([[ -e "${SANDBOX}/runs/emptyusers" ]] && echo yes || echo no)"

echo "== 5e. 账号池 --users 传空串 → 2（命令行侧同一条判据）=="
RC="$(run_runner emptyflag "$RUNNER" "${LOOPBACK[@]}" --users '')"
check "空 --users" 2 "$RC"

echo "== 6. 扫描器无法启动 → 1 =="
RC="$(PATH="${BADNODE}:${PATH}" run_runner badscan "$RUNNER" "${LOOPBACK[@]}")"
check "扫描器崩溃" 1 "$RC"
check "报的是工具链不可用" 1 "$(log_has badscan '工具链不可用' && echo 1 || echo 0)"

echo "== 7. 文案表重复 code → 1（且不创建证据目录）=="
DUP="${SANDBOX}/perf-dup"
cp -R "$PERF_DIR" "$DUP"
printf 'external_monitoring_required\t重复定义\n' >> "${DUP}/delivery-blockers.tsv"
RC="$(run_runner tsvdup "${DUP}/run-stress-test.sh" "${LOOPBACK[@]}")"
check "重复 code 拒绝执行" 1 "$RC"
check "重复 code 未创建证据目录" "no" "$([[ -e "${SANDBOX}/runs/tsvdup" ]] && echo yes || echo no)"

echo "== 8. 文案表缺 TAB → 1 =="
NOTAB="${SANDBOX}/perf-notab"
cp -R "$PERF_DIR" "$NOTAB"
printf 'no_tab_at_all\n' >> "${NOTAB}/delivery-blockers.tsv"
RC="$(run_runner tsvnotab "${NOTAB}/run-stress-test.sh" "${LOOPBACK[@]}")"
check "缺 TAB 拒绝执行" 1 "$RC"

echo "== 9. 注入表外 blocker code → 清单生成器 exit 1（文案不再可被外部文本顶替）=="
RUN_DIR="$RUNDIR" MONITOR_SCOPE=local-target MONITOR_REQUIRED_COMPONENTS=system \
  DELIVERY_BLOCKER_CODES='not_in_the_catalog' \
  node "${PERF_DIR}/write-run-manifest.mjs" > "${SANDBOX}/log-inject.txt" 2>&1
check "表外 code 拒绝生成清单" 1 "$?"
check "报的是表外 code" 1 "$(grep -qF '在 delivery-blockers.tsv 中没有对应文案' "${SANDBOX}/log-inject.txt" && echo 1 || echo 0)"

echo
if [[ "$FAIL" == "0" ]]; then
  printf 'exit-code-self-test: 全部通过（%d/%d）\n' "$N" "$N"
  exit 0
fi
printf 'exit-code-self-test: 失败 %d/%d（沙箱 ${SANDBOX} 的日志见 ${SANDBOX}/log-*.txt）\n' "$FAIL" "$N" >&2
exit 1
