#!/usr/bin/env bash
# Input: 无（纯离线：不连网络、不连数据库、不向任何环境施加负载）。
#        外部命令全部由夹具层 self-test-fixtures.sh 换成固定输出的替身（k6 / curl / mysql /
#        redis-cli，以及监控侧的 top / uptime / sysctl / vm_stat / uname），node 侧另有三个
#        坏替身（**坏扫描器**：node 在、脱敏脚本崩；**坏 shim**：node"找得到但跑不动"；
#        **挂死的 shim**：node"找得到、但调用它永不返回"）。另有三个**只坏脱敏这一次调用**的
#        替身（崩溃 / 机器行被吞 / 机器行重复），由本文件的用例 6b–6d 自己在沙箱里构造——
#        它们只服务那三条断言，不是每个用例都要用的通用替身。
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
# 本文件只写"期望什么"：造替身、影子 PATH、子进程上界都在 self-test-fixtures.sh（夹具层）。
# 一条自检如果依赖宿主的 top/node 能不能跑，结论就会随机器变红变绿——那层因此必须独立、
# 且自己先证明"替身不会自递归"（用例 0）。
#
# 用例里对 TSV 的破坏一律发生在**沙箱副本**上：本脚本绝不修改仓库内的 delivery-blockers.tsv。

set -uo pipefail

# shellcheck source=self-test-fixtures.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/self-test-fixtures.sh"

export XIYU_STRESS_CONFIRMED=1
export XIYU_K6_USERS='[{"username":"probe","password":"probe-secret-value"}]'
LOOPBACK=(--profile smoke --base-url http://127.0.0.1:18999 --monitor-required-components system --monitor-interval 1)
REMOTE=(--profile smoke --base-url http://203.0.113.7:8080)

echo "== 0. 自检夹具自身的健全性（坏扫描器替身不得自递归）=="
fixture_self_check

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

# node"找得到"与"跑得动"是两件事：版本管理器卸载后残留的 shim、指向已删二进制的软链、
# 把 node 定义成函数/别名的 shell 都能让 `command -v node` 通过，而调用它随即失败。
# 那次失败必须报 1（去修环境），不能报 2（去改账号池）——否则操作者会拿着一条
# "账号池不合法"去改一个本来就对的账号池，而真正的坏 node 一直在那儿。
echo '== 5f. node 找得到但跑不动 → 1（不是"账号池不合法"的 2）=='
RC="$(PATH="${BROKENNODE}:${PATH}" run_runner brokennode "$RUNNER" "${LOOPBACK[@]}")"
check "坏 node 按工具链拒绝" 1 "$RC"
check "未误报成账号池不合法" "no" "$(log_has brokennode '账号池不合法' && echo yes || echo no)"

# 与 5f 是两种病：那个 node 立刻以 3 失败，这个**永不返回**。此时 runner 必须自己有上界，
# 否则它一个退出码都不给（"挂住"比任何错码都糟：操作者拿不到任何指向"去修环境"的线索）。
echo '== 5g. node 挂死（永不返回）→ 1（有界，不是 124）=='
RC="$(PATH="${HANGNODE}:${PATH}" run_runner hangnode "$RUNNER" "${LOOPBACK[@]}")"
check "挂死的 node 按工具链拒绝" 1 "$RC"
check "报的是探测超时（上界真的生效了）" 1 "$(log_has hangnode '内没有返回' && echo 1 || echo 0)"
check "未误报成账号池不合法" "no" "$(log_has hangnode '账号池不合法' && echo yes || echo no)"

echo "== 6. 扫描器无法启动 → 1 =="
RC="$(PATH="${BADNODE}:${PATH}" run_runner badscan "$RUNNER" "${LOOPBACK[@]}")"
check "扫描器崩溃" 1 "$RC"
check "报的是工具链不可用" 1 "$(log_has badscan '工具链不可用' && echo 1 || echo 0)"

echo '== 6b–6d. 脱敏器自己没跑成 → 1（扫描不能替代这一步的结论）=='
# 与用例 6 是两种不同的坏：那个 node 连扫描都起不来（脱敏、扫描两处都崩），于是"归到 1"其实
# 是被**扫描分支**兜住的——脱敏分支坏没坏，那一条看不出来。下面三条让**只有脱敏这一次调用**
# 出问题，而扫描与清单生成照常可运行，从而把脱敏这一环单独钉住。
# 替身按**所在目录名**选行为（一份脚本三种病）；用例专属的构造留在用例侧，与 7/8 往沙箱副本里
# 塞畸形行同一口径——夹具层只放"每个用例都要用"的替身。
for _mode in crash noout dupout; do
  _d="${SANDBOX}/redact-${_mode}"
  mkdir -p "$_d"
  cat > "${_d}/node" <<STUB_REDACT
#!/usr/bin/env bash
# 只拦**脱敏**这一次调用（不带 --scan 的那次）；扫描与其余调用一律转给真实 node（绝对路径）。
case "\$*" in *scrub-secrets.mjs*--scan*) exec "${REAL_NODE}" "\$@" ;; esac
case "\$*" in
  *scrub-secrets.mjs*)
    case "${_mode}" in
      crash) echo "scrub: 替身崩溃（自检）" >&2; exit 137 ;;
      noout) "${REAL_NODE}" "\$@" | grep -v '^redacted='; exit 0 ;;
      *) "${REAL_NODE}" "\$@"; echo 'redacted=0' ;;
    esac ;;
esac
exec "${REAL_NODE}" "\$@"
STUB_REDACT
  chmod +x "${_d}/node"
done

RC="$(PATH="${SANDBOX}/redact-crash:${PATH}" run_runner redactcrash "$RUNNER" "${LOOPBACK[@]}")"
check "脱敏器崩溃" 1 "$RC"
check "报的是脱敏这一步没跑成" 1 "$(log_has redactcrash '凭证脱敏未完成' && echo 1 || echo 0)"
# 这一条是本组的关键：本轮**扫描与清单生成都正常**（清单里 scan_status 仍是 passed）。
# 没有它，上面两条也可能只是被用例 6 那条"扫描起不来"的路径顺带兜住的。
RUNDIR_REDACT="$(run_dir_of "${SANDBOX}/runs/redactcrash")"
check "扫描本身仍通过（证明 1 只由脱敏触发）" 1 "$(grep -c '"status": "passed"' "${RUNDIR_REDACT}/manifest.json")"

RC="$(PATH="${SANDBOX}/redact-noout:${PATH}" run_runner redactnoout "$RUNNER" "${LOOPBACK[@]}")"
check "脱敏成功但机器行被吞" 1 "$RC"
check "报的是输出不可解析" 1 "$(log_has redactnoout '机器输出不可解析' && echo 1 || echo 0)"

# 重复行与缺失行由同一条判据拦下（期望**恰好一行**）：两行说明机器输出被拼了两份，
# 取第一行或最后一行都是猜——早先的实现取最后一行，于是这里会静默采信第二个值。
RC="$(PATH="${SANDBOX}/redact-dupout:${PATH}" run_runner redactdup "$RUNNER" "${LOOPBACK[@]}")"
check "脱敏机器行重复" 1 "$RC"

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

echo "== 10. 经由**软链接目录**调用扫描器/脱敏器 → 结论不变（不许「静默不跑」）=="
# 与 runner 内部无关，单钉工具自己的 CLI 入口判据：若它拿"调用方给出的路径"与"node 解析出的
# 真实路径"直接比，一处软链就够让 CLI **静默不执行**——退出码 0、一行输出都没有。而扫描模式下
# "没有任何命中"与"根本没扫描"长得一模一样，这正是本工具链最忌讳的形态。
# 不是理论风险：macOS 上 /tmp 与 /var 都是软链，本文件的沙箱（mktemp -d）就在 /var 下，
# 测这条路径不需要额外构造什么。
mkdir -p "${SANDBOX}/scan-sample"
printf 'clean\n' > "${SANDBOX}/scan-sample/a.txt"
ln -sfn "$PERF_DIR" "${SANDBOX}/linked-perf"
LINKED_SCAN="$(node "${SANDBOX}/linked-perf/scrub-secrets.mjs" --scan "${SANDBOX}/scan-sample" 2>&1)"
check "软链目录下扫描器仍给出结论" 1 "$(printf '%s\n' "$LINKED_SCAN" | grep -c '^scan_status=passed$')"
LINKED_REDACT="$(node "${SANDBOX}/linked-perf/scrub-secrets.mjs" "${SANDBOX}/scan-sample/a.txt" 2>&1)"
check "软链目录下脱敏器仍给出机器行" 1 "$(printf '%s\n' "$LINKED_REDACT" | grep -c '^redacted=0$')"

echo
if [[ "$FAIL" == "0" ]]; then
  printf 'exit-code-self-test: 全部通过（%d/%d）\n' "$N" "$N"
  exit 0
fi
printf 'exit-code-self-test: 失败 %d/%d（沙箱 ${SANDBOX} 的日志见 ${SANDBOX}/log-*.txt）\n' "$FAIL" "$N" >&2
exit 1
