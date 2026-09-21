#!/usr/bin/env bash
# Input: k6 脚本 + 目标环境 BASE_URL + 压测档位；可选监控采集器与 MySQL/Redis 连接参数
# Output: 单次压测的完整证据包（k6-summary.json / k6-raw-samples.csv / k6-console.log / monitoring/ / manifest.json）
# Pos: scripts/performance/ - 压测执行与原始证据归档**入口**（压测报告"原始测试数据 + 结果文件 + 监控数据"的唯一产出通道）
# 维护声明: 若产出物清单变化（如新增 JVM/GC 专项或 Nginx 日志归档），同步更新
#           evidence-completeness.sh 的 evidence_required_artifacts、write-run-manifest.mjs 的产物清单
#           与 scripts/performance/README.md 三处。
#
# 用途：一条命令跑完压测并把原始证据落盘，避免手工拼命令导致证据缺失。
#
# 本文件已按"参数解析 / 监控子进程 / 完整性判定 / 归档"拆成四层，本文件只负责把它们串起来。
# 每一层都可以在没有服务、没有网络的机器上单独检查：
#   monitor-spec.sh          组件集合 / CSV 表头 / 有效行判定列号 —— 采集器与 runner 共用的唯一契约源
#   cli-guards.sh            带值选项缺值时的稳定用法错误（exit 2）
#   run-preflight.sh         参数解析与全部前置校验（用法错误 exit 2 / 拒绝执行 exit 1）
#   monitor-lifecycle.sh     监控采集子进程的启停（.ready 握手 / 有界等待 / 兜底强杀）
#   evidence-completeness.sh 完整性判定与交付出口（缺产物 vs 交付阻塞项，须分开判）
#   evidence-archive.sh      归档三步：控制台规范化 → 凭证脱敏 → 残留扫描，以及 manifest.json
#   capture-monitoring.sh    采集器本身（本文件以子进程方式拉起）
#
# 上面的用法正文（本行以下到第一个空行）就是 `--help` 的输出：run-preflight.sh 用
# `sed -n '1,/^$/p' "$0"` 回印它，因此**头部注释内不要插入空行**——那会让 --help 静默截断。
#
# 示例（在 172.16.38.78 这台应用宿主上跑标准阶梯 10→50→100 VU，含受控写入，与 2026-08-28 报告口径一致）：
#   XIYU_STRESS_CONFIRMED=1 XIYU_K6_USERS='[...]' bash scripts/performance/run-stress-test.sh \
#     --profile standard \
#     --base-url http://172.16.38.78:8080 \
#     --monitor-local-target \
#     --enable-writes
#   （--monitor-local-target 只有在本机就是 172.16.38.78 时才可加；压测机不是目标时改用
#     --no-monitor，并另外在目标机上跑 capture-monitoring.sh —— 见 docs/testing/stress-test-artifacts/README.md）
#
# 示例（本机回环地址，只读复现，不向环境写入业务数据）：
#   XIYU_STRESS_CONFIRMED=1 bash scripts/performance/run-stress-test.sh \
#     --profile standard --base-url http://127.0.0.1:18089
#
# 监控宿主必须二选一（--no-monitor 或 --monitor-local-target，不可同时给）：
#   目标不是本机（base-url 非回环）时必须显式二选一，否则 exit 2；
#   目标是本机（base-url 为回环地址）时不必给，按地址直接判定为"本机就是目标"——
#   这是机器可核验的事实，不是替操作者猜。两个开关同时给出是自相矛盾的调用，
#   一律以 exit 2 拒绝，而不是让其中一个静默生效。
#
# 说明：
#   - 默认关闭业务写入（--enable-writes 才开启），避免污染共享测试环境与触发真实业务通知。
#   - 必须显式导出 XIYU_STRESS_CONFIRMED=1，且 BASE_URL 命中生产特征时直接拒绝执行。
#   - 证据目录内的产物在归档前会做凭证脱敏（scrub-secrets.mjs）：k6 summary 的 setup_data
#     带着登录得到的 bearer token，不能原样交付给第三方。
#   - 账号池优先取环境变量 XIYU_K6_USERS（JSON 数组，格式同 --users）；--users 是等效的显式形式。
#     命令行参数会出现在本机 `ps` 的 argv 里，共享压测机上请用环境变量，不要把口令写进命令行。
#
# 退出码：
#   0        k6 全部门禁通过，且证据包完整
#   k6 自身码 k6 未全通过（99 = 有门禁被击穿；其余非 0 见 k6-console.log），证据包完整
#   1        拒绝执行（未确认环境 / 命中生产特征 / 缺 k6 或脚本 / 监控规格或阻塞项文案表自检失败 /
#            **工具链不可用**：没有 node、扫描器或 manifest 生成器起不来、它们的结论不可解析）
#   2        用法错误（未知参数、选项缺值、缺 --base-url、非法档位或间隔、监控宿主歧义或
#            两个开关同时给出、证据目录已存在、--base-url 带 userinfo）
#   12       k6 已跑完，但**证据包不可交付**：缺原始结果/监控产物、监控产物无有效数据行、
#            凭证残留扫描未通过，或本次用了 --no-monitor（缺监控原始数据）。优先于 k6 退出码上报。
#
# 1 与 12 的分界是**结论有没有被算出来**：12 表示结论算出来了、结论是"本包不齐"，调用方按缺件
# 去补采即可；1 表示工具链没能给出结论（补采多少次都不会有变化）。因此 1 优先于 12 上报。

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
K6_SCRIPT="${REPO_ROOT}/k6-tests/load-test.js"

# 契约与各层都在 source 之后即可用；任何一层的规格自检失败都必须在**创建证据目录之前**
# 拒绝执行——否则机器上会留下一批名为证据、实则无人保证其完整性的目录。
source "${SCRIPT_DIR}/monitor-spec.sh"
if ! monitor_assert_spec; then
  echo "run-stress-test: 监控规格自检失败（${SCRIPT_DIR}/monitor-spec.sh），拒绝执行" >&2
  exit 1
fi
source "${SCRIPT_DIR}/cli-guards.sh"
source "${SCRIPT_DIR}/run-preflight.sh"
source "${SCRIPT_DIR}/monitor-lifecycle.sh"
source "${SCRIPT_DIR}/evidence-completeness.sh"
source "${SCRIPT_DIR}/evidence-archive.sh"

# 交付阻塞项文案表的整表校验。与 monitor_assert_spec 同理：这是**工具链自检**，
# 必须在创建证据目录之前、在任何判定发生之前完成——表格式坏了以后，"本包为何不可交付"
# 这句话就没了唯一来源，而 shell 与清单生成器会各自读出一个不同的文案还不报错。
blocker_catalog_assert

# 参数解析与全部前置校验都在这一句里（"这次运行合法吗"），它不做任何有副作用的动作，
# 出错时直接以 exit 2 / exit 1 结束；返回即代表下面所有取值都可用。
run_preflight_parse "$@"

# 档位与标签都会拼进目录名，必须先净化：否则 --label '../../../../tmp/x'
# 会把证据包写到证据树之外，且脱离本目录 .gitignore 的约束。
RUN_TAG="$(printf '%s' "${PROFILE}${RUN_LABEL:+-$RUN_LABEL}" | tr -c 'A-Za-z0-9._-' '-')"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-${RUN_TAG}"

# 仓库状态必须在创建证据目录**之前**采集：mkdir 出来的未跟踪目录本身就会让
# `git status --porcelain` 非空，GIT_DIRTY 恒为 "dirty"，溯源价值归零。
STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
STARTED_EPOCH="$(date +%s)"
COMMIT_SHA="$(git -C "$REPO_ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
BRANCH_NAME="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
K6_VERSION="$(k6 version 2>/dev/null || echo unknown)"
GIT_DIRTY="clean"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain 2>/dev/null)" ]]; then GIT_DIRTY="dirty"; fi

RUN_DIR="${OUT_ROOT%/}/${RUN_ID}"
# 同秒内重复运行（同档位同标签）会得到完全相同的 RUN_DIR，而 mkdir -p 对已存在目录不报错、
# tee 默认截断——结果是上一轮的证据被静默覆盖。压测机常被多人共用，这不是纯理论风险。
# 证据目录的唯一性必须在此处把住，宁可让操作者换个 label 重跑。
if [[ -e "$RUN_DIR" ]]; then
  echo "run-stress-test: 证据目录已存在，拒绝覆盖上一轮证据：$RUN_DIR" >&2
  echo "               请稍等一秒再跑，或用 --label 区分本次运行。" >&2
  exit 2
fi
# 连 monitoring/ 一起预建：采样器自己才 mkdir，若它尚未起来就在 .stop 上 touch 会失败，
# 而那时整轮压测已进入收尾阶段，不该因为缺一个目录而中断、让退出码失真。
mkdir -p "${RUN_DIR}/monitoring"

# 提前探测环境可达性，避免只留下一个连接失败的空证据包
if ! curl -fsS -o /dev/null --max-time 10 "${BASE_URL%/}/actuator/health"; then
  echo "run-stress-test: 警告 —— ${BASE_URL%/}/actuator/health 不可达，仍将继续尝试压测" >&2
fi

echo "run-stress-test: 目标=$BASE_URL 档位=$PROFILE 写入=$([[ $ENABLE_WRITES == 1 ]] && echo on || echo off)"
echo "run-stress-test: 证据目录=$RUN_DIR"

# 采集侧问题（组件一行有效数据都没有、采集器非 0 退出、未在 30s 内就绪）都追加到这里，
# 最后写进 manifest.json 的 monitoring_issues —— 光说"缺 monitoring/mysql.csv"不解释为什么，
# 拿包的人无从判断是环境没有这个组件，还是采集器坏了。
MONITOR_ISSUES=""
monitor_collector_start

# 传给 k6 的方式是**在受控子 shell 里导出环境变量**，而不是
# `env "K6_USERS=..." k6 run`：env 进程在启动窗口内的 argv 对本机任何账号都可读
# （`ps aux`），而文档承诺的正是"口令只存在于调用者的环境里、不进入任何进程的 argv"。
# 子 shell 还有一个作用：它改的这些变量（BASE_URL 去掉尾斜杠、ENABLE_WRITES 转成
# k6 要的 true/false）不会污染本脚本自己的变量 —— 本脚本的 ENABLE_WRITES 是 0/1，
# 下面写进 manifest 的也是它；直接 export 会把这两个语义覆盖掉，之后 manifest 里
# enable_writes 恒为 false，而没有任何一处会报错。
set +e
(
  export BASE_URL="${BASE_URL%/}"
  export STRESS_PROFILE="$PROFILE"
  export ENABLE_WRITES=$([[ $ENABLE_WRITES == 1 ]] && echo true || echo false)
  if [[ -n "$K6_USERS_JSON" ]]; then export K6_USERS="$K6_USERS_JSON"; fi
  k6 run \
    --summary-export="${RUN_DIR}/k6-summary.json" \
    --out "csv=${RUN_DIR}/k6-raw-samples.csv" \
    --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
    "$K6_SCRIPT" 2>&1 | tee "${RUN_DIR}/k6-console.log"
  exit "${PIPESTATUS[0]}"
)
K6_EXIT=$?
# 这里**不要**再 `set -e`。本脚本开头是 `set -uo pipefail`（无 -e），若在此处打开 errexit，
# 之后任意一条后处理命令失败都会让脚本立即退出：既吞掉下面的汇总输出，又把该命令的退出码
# 冒充成本脚本的退出码，破坏末尾的退出码契约（实测：k6 全通过=0，后处理里 node 失败时
# 脚本退出码变成 3）。后处理阶段的每一步都要自己判错并显式告警。

echo "$K6_EXIT" > "${RUN_DIR}/k6-exit-code"
ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
DURATION_SEC=$(( $(date +%s) - STARTED_EPOCH ))

monitor_collector_stop

# 归档三步的**顺序即契约**（规范化 → 脱敏 → 扫描 → 写清单），理由见 evidence-archive.sh
# 的头部注释：清单记的是各产物**脱敏后**的 sha256，先写清单再脱敏，清单就对不上了。
archive_normalize_console "$RUN_DIR"
archive_redact "$RUN_DIR"
archive_scan "$RUN_DIR"

# 判定分两步且顺序固定：先算缺产物（清单要把它记进去），再算交付阻塞项。
# 两者的区别见 evidence-completeness.sh 的头部注释——只判前者时，--no-monitor 的包会天然
# "缺 0 个文件"并被判证据完整，与 README"这类包不算证据齐全"正好相反。
evidence_collect_missing
evidence_collect_blockers

# 清单最后写：它要记的缺失产物 / 阻塞项 / 脱敏计数 / 扫描结论都已在上面确定。
# 清单侧会独立复算一遍同样的判据，并把结论并回这里（见 archive_write_manifest）。
archive_write_manifest

evidence_finalize
