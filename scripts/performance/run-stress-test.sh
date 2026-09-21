#!/usr/bin/env bash
# Input: k6 脚本 + 目标环境 BASE_URL + 压测档位；可选监控采集器与 MySQL/Redis 连接参数
# Output: 单次压测的完整证据包（k6-summary.json / k6-raw-samples.csv / k6-console.log / monitoring/ / manifest.json）
# Pos: scripts/performance/ - 压测执行与原始证据归档入口（压测报告"原始测试数据 + 结果文件 + 监控数据"的唯一产出通道）
# 维护声明: 若产出物清单变化（如新增 JVM/GC 专项或 Nginx 日志归档），同步更新本脚本的 manifest 段与 scripts/performance/README.md。
#
# 用途：一条命令跑完压测并把原始证据落盘，避免手工拼命令导致证据缺失。
#
# 示例（测试环境标准阶梯 10→50→100 VU，含受控写入，与 2026-08-28 报告口径一致）：
#   XIYU_STRESS_CONFIRMED=1 bash scripts/performance/run-stress-test.sh \
#     --profile standard \
#     --base-url http://172.16.38.78:8080 \
#     --enable-writes
#
# 示例（只读复现，不向环境写入业务数据）：
#   XIYU_STRESS_CONFIRMED=1 bash scripts/performance/run-stress-test.sh \
#     --profile standard --base-url http://127.0.0.1:18089
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
#   1        拒绝执行（未确认环境 / 命中生产特征 / 缺 k6 或脚本）
#   2        用法错误（未知参数、缺 --base-url、非法档位或间隔、证据目录已存在、监控宿主歧义）
#   12       k6 已跑完但**证据包不完整**（缺原始结果/监控产物，或监控产物无有效数据行），
#            优先于 k6 退出码上报

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
K6_SCRIPT="${REPO_ROOT}/k6-tests/load-test.js"

PROFILE="standard"
BASE_URL=""
OUT_ROOT="${REPO_ROOT}/docs/testing/stress-test-artifacts"
ENABLE_WRITES=0
RUN_MONITOR=1
MONITOR_LOCAL_TARGET=0
MONITOR_INTERVAL=5
# 本包声称"监控证据完整"就必须说清完整到什么程度：这四个组件各至少要有一行有效采样。
# 由调用方声明而不是采集器替它假定——目标环境确实没有 Redis 时，操作者应当显式
# 收窄成 system,app-jvm,mysql，而不是让一份缺组件的包以"完整"的名义交付出去。
MONITOR_REQUIRED_COMPONENTS="system,app-jvm,mysql,redis"
RUN_LABEL=""
# 账号池默认从环境变量取，而不是只认命令行：`--users '[{...,"password":"..."}]'` 会把
# 测试环境口令写进进程 argv，同机任何账号 `ps aux` 就能读到。文档因此无法给出一个
# 既真实可跑、又不泄露口令的 --users 示例。XIYU_K6_USERS 让口令只存在于调用者的环境里。
K6_USERS_JSON="${XIYU_K6_USERS:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) PROFILE="$2"; shift 2 ;;
    --base-url) BASE_URL="$2"; shift 2 ;;
    --out) OUT_ROOT="$2"; shift 2 ;;
    --label) RUN_LABEL="$2"; shift 2 ;;
    --enable-writes) ENABLE_WRITES=1; shift ;;
    --no-monitor) RUN_MONITOR=0; shift ;;
    --monitor-local-target) MONITOR_LOCAL_TARGET=1; shift ;;
    --monitor-interval) MONITOR_INTERVAL="$2"; shift 2 ;;
    --monitor-required-components) MONITOR_REQUIRED_COMPONENTS="$2"; shift 2 ;;
    --k6-script) K6_SCRIPT="$2"; shift 2 ;;
    --users) K6_USERS_JSON="$2"; shift 2 ;;
    -h|--help) sed -n '2,33p' "$0"; exit 0 ;;
    *) echo "run-stress-test: 未知参数 $1" >&2; exit 2 ;;
  esac
done

if [[ "${XIYU_STRESS_CONFIRMED:-}" != "1" ]]; then
  cat >&2 <<'EOF'
run-stress-test: 拒绝执行 —— 压测会向目标环境施加真实负载。
请先确认目标环境并显式导出：
  export XIYU_STRESS_CONFIRMED=1
EOF
  exit 1
fi

if [[ -z "$BASE_URL" ]]; then
  echo "run-stress-test: 必须通过 --base-url 指定目标环境（例如 http://172.16.38.78:8080）" >&2
  exit 2
fi

# 档位必须白名单校验。k6-tests/load-test.js 用的是 `STAGE_CONFIGS[PROFILE] || STAGE_CONFIGS.standard`，
# 拼错档位不会报错，而是**静默按 standard 跑**——而 manifest 记的是操作者输入的那个错误档位，
# 于是"实际负载曲线"和"证据里写的档位"对不上，第三方按它做容量结论就是错的。
case "$PROFILE" in
  smoke|standard|stress|endurance) ;;
  *)
    echo "run-stress-test: --profile 仅支持 smoke|standard|stress|endurance（收到 '${PROFILE}'）" >&2
    echo "                未校验时 k6 会静默回退到 standard，而 manifest 仍记录你输入的值。" >&2
    exit 2
    ;;
esac

TARGET_HOST="$(printf '%s' "$BASE_URL" | sed -nE 's#^[a-zA-Z][a-zA-Z0-9+.-]*://(\[[^]]*\]|[^:/?#]+).*#\1#p')"
if [[ -z "$TARGET_HOST" ]]; then
  echo "run-stress-test: 无法从 --base-url 解析出主机名：$BASE_URL" >&2
  exit 2
fi
# 监控采集器采的是**它自己所在这台机器**的 CPU/内存/JVM，以及它连得到的 MySQL/Redis。
# 目标不在本机时，把这些数据放进以远端 base_url 命名的证据包里，等于用压测机的负载
# 冒充目标环境的负载——报告第五章那些"目标系统资源稳定"的结论会因此完全失真，
# 而证据包看上去毫无异常。所以本机不是目标时必须由操作者显式声明，不存在默认值。
TARGET_IS_LOCAL=0
case "$TARGET_HOST" in
  127.0.0.1|localhost|::1|\[::1\]|0.0.0.0) TARGET_IS_LOCAL=1 ;;
esac

MONITOR_SCOPE=""
if [[ "$RUN_MONITOR" == "0" ]]; then
  MONITOR_SCOPE="skipped"
elif [[ "$TARGET_IS_LOCAL" == "1" ]]; then
  MONITOR_SCOPE="local-target"
elif [[ "$MONITOR_LOCAL_TARGET" == "1" ]]; then
  MONITOR_SCOPE="local-target-asserted"
else
  # 变量一律写成 ${VAR} 带花括号：本仓库脚本正文是中文，裸变量名紧跟全角字符时
  # （例如变量名后面直接跟一个全角左括号），多字节字符在 UTF-8 locale 下会被 bash
  # 视作标识符字符吞进变量名，于是报 `TARGET_HOST?: unbound variable` 并中止——实测踩过。
  cat >&2 <<EOF
run-stress-test: 监控宿主不明确，拒绝执行。
  --base-url 指向 ${TARGET_HOST}（非本机），但监控采集器只能采**本机**资源。
  直接把本机数据写进这个证据包，会把压测机的负载当成 ${TARGET_HOST} 的负载交付。
请明确二选一：
  --monitor-local-target   本机就是被压的目标宿主机（此时才允许本机采集）
  --no-monitor             本包不含监控证据；请改在目标机上单独跑 capture-monitoring.sh
EOF
  exit 2
fi

# 监控间隔在**启动压测之前**校验。采集器自己是后台进程，它那侧校验失败只会往日志里写一行
# 然后退出，而 k6 照跑不误——跑完一轮、monitoring/ 却是空的，操作者看到的仍是"压测结束"。
# 实测过这条路径：--monitor-interval abc 会产出 5 个监控产物全为 null 的证据包。
if [[ ! "$MONITOR_INTERVAL" =~ ^[0-9]+$ ]] || [[ "$MONITOR_INTERVAL" -lt 1 ]]; then
  echo "run-stress-test: --monitor-interval 必须是 ≥1 的整数秒（收到 '${MONITOR_INTERVAL}'）" >&2
  exit 2
fi

# 必需组件名同样白名单校验：拼错一个名字（sys 而不是 system）会让下面的逐组件判定
# 找不到对应规格 —— 那本该是"这一项没采到"，却会静默退化成"没有这项要求"。
MONITOR_REQUIRED_NORMALIZED=""
for _c in $(printf '%s' "$MONITOR_REQUIRED_COMPONENTS" | tr ',' ' '); do
  case " system app-jvm mysql redis " in
    *" ${_c} "*) ;;
    *)
      echo "run-stress-test: --monitor-required-components 只支持 system|app-jvm|mysql|redis（收到 '${_c}'）" >&2
      exit 2
      ;;
  esac
  MONITOR_REQUIRED_NORMALIZED="${MONITOR_REQUIRED_NORMALIZED} ${_c}"
done
unset _c
MONITOR_REQUIRED_COMPONENTS="${MONITOR_REQUIRED_NORMALIZED# }"
unset MONITOR_REQUIRED_NORMALIZED
if [[ -z "$MONITOR_REQUIRED_COMPONENTS" ]]; then
  echo "run-stress-test: --monitor-required-components 不能为空；不含监控证据请改用 --no-monitor" >&2
  exit 2
fi

# 独立复算监控 CSV 里"有效采样行"的条数 —— **不复用采集器自报的 component-status.csv**。
# 采集器的退出码和状态文件都是它自己的说法；"这份包能不能交付"必须由交付方自己算一遍，
# 否则就退回到"清单自己声明自己完整"那个被审查否掉的形态。
# 判据与该 CSV 里下游分析真正要用的列一致（列号从 1 起，第 1 列是 timestamp）。
# 只查 -s 是不够的：采集器在 mysql 客户端缺失、Redis 不可达、应用进程找不到时留下的
# 正是"只有表头"或"整行空值"的文件，非空、有行数、却撑不起报告第五章的任何结论。
csv_usable_rows() {
  local file="$1" cols="$2"
  if [[ ! -f "$file" ]]; then printf '0'; return; fi
  awk -v cols="$cols" -F',' '
    NR == 1 { want = split(cols, w, ","); next }
    {
      ok = 1
      for (i = 1; i <= want; i++) {
        v = $(w[i] + 0)
        if ($(w[i]) == "" || $(w[i]) !~ /^-?[0-9]+(\.[0-9]+)?$/) { ok = 0; break }
      }
      if (ok) n++
    }
    END { printf "%d", n + 0 }' "$file"
}

# 组件名 → `<CSV 文件名>:<可用判定列号>` 的**唯一**映射。
# 必需产物清单与逐 CSV 复算都从这里派生：只要把组件集合写死在两处，
# `--monitor-required-components` 就只对采集器生效、runner 照旧按四件套硬判——
# 声明了"这台机器没有 Redis"仍会 exit 12，声明等于没声明（实测过）。
monitor_spec_for() {
  case "$1" in
    system) printf 'system.csv:2,4,5,7,8' ;;
    app-jvm) printf 'app-jvm.csv:3,6,15' ;;
    mysql) printf 'mysql.csv:2,3,4,5,6,7' ;;
    redis) printf 'redis.csv:2,3,4,5,6' ;;
  esac
}

# 大小写不敏感后再匹配：只写 *prod*|*Prod*|*PROD* 会让 pRODuction-host、PrOd-host 直接放行。
# 匹配整个 URL（含路径）而不是只匹配 host，宁可多拦也不漏拦。
BASE_URL_LC="$(printf '%s' "$BASE_URL" | tr '[:upper:]' '[:lower:]')"
case "$BASE_URL_LC" in
  *prod*|*xiyu-bid.com*|*bid.xiyu*)
    echo "run-stress-test: 拒绝执行 —— BASE_URL 命中生产特征：$BASE_URL" >&2
    exit 1
    ;;
esac

if ! command -v k6 >/dev/null 2>&1; then
  echo "run-stress-test: 未安装 k6（macOS: brew install k6）" >&2
  exit 1
fi

if [[ ! -f "$K6_SCRIPT" ]]; then
  echo "run-stress-test: k6 脚本不存在：$K6_SCRIPT" >&2
  exit 1
fi

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

MONITOR_PID=""
# 采集侧问题（组件一行有效数据都没有、采集器非 0 退出）都追加到这里，最后写进
# manifest.json 的 monitoring_issues —— 光说"缺 monitoring/mysql.csv"不解释为什么，
# 拿包的人无从判断是环境没有这个组件，还是采集器坏了。
MONITOR_ISSUES=""
if [[ "$MONITOR_SCOPE" != "skipped" ]]; then
  # 走到这里意味着"本机就是被压的目标宿主机"（回环地址，或操作者用 --monitor-local-target 声明）。
  # 按监听端口反查本机应用进程；同机多 java 进程时端口是最准的线索。
  APP_PORT="$(printf '%s' "$BASE_URL" | sed -nE 's#^[a-z]+://[^:/]+:([0-9]+).*#\1#p')"
  MONITOR_ARGS=(--out "$RUN_DIR" --interval "$MONITOR_INTERVAL" --duration 900
                --required-components "$MONITOR_REQUIRED_COMPONENTS")
  if [[ -n "$APP_PORT" ]]; then MONITOR_ARGS+=(--app-port "$APP_PORT"); fi
  bash "${SCRIPT_DIR}/capture-monitoring.sh" "${MONITOR_ARGS[@]}" &
  MONITOR_PID=$!
  # Ctrl-C / TERM 时不要把采样器留成孤儿进程继续采到 900s。
  # 必须带 exit：trap 处理器返回后 bash 会从下一条命令继续执行，只 kill 不 exit 的话
  # 信号被吞掉——k6 照常把负载跑完，脚本还输出"✅ 全部阈值通过"并以 0 退出，
  # 监督者（CI / timeout / supervisor）看到的是"成功"而非"被中止"。
  # 143 = 128 + SIGTERM，符合"被 TERM 终止"的通行约定。
  trap 'kill "$MONITOR_PID" 2>/dev/null || true; exit 143' INT TERM
  echo "run-stress-test: 监控采集已启动 (pid=${MONITOR_PID}, interval=${MONITOR_INTERVAL}s, 采集口径=${MONITOR_SCOPE}, app_port=${APP_PORT:-<未解析>})"
fi

K6_ENV=(
  "BASE_URL=${BASE_URL%/}"
  "STRESS_PROFILE=${PROFILE}"
  "ENABLE_WRITES=$([[ $ENABLE_WRITES == 1 ]] && echo true || echo false)"
)
if [[ -n "$K6_USERS_JSON" ]]; then K6_ENV+=("K6_USERS=${K6_USERS_JSON}"); fi

set +e
env "${K6_ENV[@]}" k6 run \
  --summary-export="${RUN_DIR}/k6-summary.json" \
  --out "csv=${RUN_DIR}/k6-raw-samples.csv" \
  --summary-trend-stats="avg,min,med,p(90),p(95),p(99),max" \
  "$K6_SCRIPT" 2>&1 | tee "${RUN_DIR}/k6-console.log"
K6_EXIT="${PIPESTATUS[0]}"
# 这里**不要**再 `set -e`。本脚本开头是 `set -uo pipefail`（无 -e），若在此处打开 errexit，
# 之后任意一条后处理命令失败都会让脚本立即退出：既吞掉下面的汇总输出，又把该命令的退出码
# 冒充成本脚本的退出码，破坏末尾 `exit "$K6_EXIT"` 这个对外契约（实测：k6 全通过=0，
# 后处理里 node 失败时脚本退出码变成 3）。后处理阶段的每一步都要自己判错并显式告警。

echo "$K6_EXIT" > "${RUN_DIR}/k6-exit-code"
ENDED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
DURATION_SEC=$(( $(date +%s) - STARTED_EPOCH ))

if [[ -n "$MONITOR_PID" ]]; then
  touch "${RUN_DIR}/monitoring/.stop" 2>/dev/null || true
  # 采样器上限 900s；wait 是无界的，采集器一旦卡住整轮压测就永久挂起，
  # 因此有界等待后再兜底 kill。
  MONITOR_WAIT=0
  MONITOR_FORCED=0
  while kill -0 "$MONITOR_PID" 2>/dev/null && [[ $MONITOR_WAIT -lt 30 ]]; do
    sleep 1
    MONITOR_WAIT=$((MONITOR_WAIT + 1))
  done
  if kill -0 "$MONITOR_PID" 2>/dev/null; then
    echo "run-stress-test: 监控采集未在 30s 内退出，强制结束（pid=${MONITOR_PID}）" >&2
    kill "$MONITOR_PID" 2>/dev/null || true
    MONITOR_FORCED=1
  fi
  MONITOR_EXIT=0
  wait "$MONITOR_PID" 2>/dev/null || MONITOR_EXIT=$?
  rm -f "${RUN_DIR}/monitoring/.stop"
  echo "run-stress-test: 监控采集已停止"

  # 采集器可能启动即失败：interval 非法、mysql/redis-cli 缺失、宿主命令不可用。
  # 它的报错只落在自己的 stderr 里，而 k6 的负载已经照常跑完了——不在这里显式检查的话，
  # 交付出去的就是一个 monitoring/ 全空、却仍然打印"压测结束 ✅"的证据包。
  # 实测过这条路径（--monitor-interval abc）：5 个监控产物全为 null，runner 不告警。
  # 被强制结束的那次非 0 退出已在上面单独说明过，不重复告警。
  if [[ "$MONITOR_FORCED" != "1" ]] && [[ "$MONITOR_EXIT" != "0" ]]; then
    echo "run-stress-test: 监控采集器以退出码 ${MONITOR_EXIT} 结束，证据包缺少可用的监控数据" >&2
    if [[ "$MONITOR_EXIT" == "3" ]]; then
      MONITOR_ISSUES="${MONITOR_ISSUES}采集器判定必需组件未采到有效数据（exit 3），明细见 monitoring/component-status.csv"$'\n'
    else
      MONITOR_ISSUES="${MONITOR_ISSUES}监控采集器以退出码 ${MONITOR_EXIT} 结束（非组件判定失败），明细见 monitoring/monitoring.log"$'\n'
    fi
  fi
  # 缺哪些监控文件、哪些监控文件没有有效数据行，不在这里报——统一由下面的完整性判定汇总，
  # 避免同一件事告警两遍。
fi

# 归档前先规范化 k6 控制台日志的行尾空白。k6 自己输出的横幅与指标行带行尾空格，
# 不改的话每一次归档都会让 `git diff --check` 常红，而这条门禁红灯久了就没人看了。
# 只删行尾空白，不动任何可见字符与行序；机器可读的那两份产物（summary / raw samples）不受影响。
if [[ -f "${RUN_DIR}/k6-console.log" ]]; then
  sed -i '' -e 's/[[:space:]]*$//' "${RUN_DIR}/k6-console.log" 2>/dev/null \
    || sed -i -e 's/[[:space:]]*$//' "${RUN_DIR}/k6-console.log" 2>/dev/null \
    || echo "run-stress-test: 警告 —— k6-console.log 行尾空白规范化失败（不影响证据内容）" >&2
fi

# 凭证脱敏必须在生成 manifest 之前：manifest 记的是各产物**脱敏后**的 sha256，
# 顺序反了清单就对不上。k6 的 --summary-export 会把 setup() 的返回值原样写进
# setup_data，而负载脚本的 setup() 正在那里登录——不脱敏就是把这组 bearer token
# 连同一份"可归档证据包"一起提交进仓库。
REDACTED_COUNT=0
if command -v node >/dev/null 2>&1; then
  SCRUB_FILES=("${RUN_DIR}/k6-summary.json" "${RUN_DIR}/k6-console.log")
  if [[ -f "${RUN_DIR}/k6-raw-samples.csv" ]]; then SCRUB_FILES+=("${RUN_DIR}/k6-raw-samples.csv"); fi
  if [[ -d "${RUN_DIR}/monitoring" ]]; then SCRUB_FILES+=("${RUN_DIR}/monitoring/monitoring.log"); fi
  if SCRUB_OUT="$(node "${SCRIPT_DIR}/scrub-secrets.mjs" "${SCRUB_FILES[@]}" 2>&1)"; then
    REDACTED_COUNT="$(printf '%s\n' "$SCRUB_OUT" | sed -nE 's/^redacted=([0-9]+)$/\1/p' | tail -1)"
    REDACTED_COUNT="${REDACTED_COUNT:-0}"
    echo "run-stress-test: 凭证脱敏完成（替换 ${REDACTED_COUNT} 处）"
  else
    echo "run-stress-test: 凭证脱敏失败，证据包可能含可用凭证：${SCRUB_OUT}" >&2
  fi
fi

# ── 完整性判定：把"跑完了"和"证据齐了"分开 ──────────────────────────────
# 此前只是逐条告警，脚本仍以 k6 的退出码结束：k6 返回 0 时，调用方拿到的是
# "成功" + 一份缺原始结果或缺监控的证据包。请求方要的正是原始数据、结果文件、
# 监控记录三样俱全，缺任何一样都不能算交付成功——所以这里 fail closed。
REQUIRED_ARTIFACTS=(k6-summary.json k6-console.log k6-exit-code k6-raw-samples.csv)
if [[ "$MONITOR_SCOPE" != "skipped" ]]; then
  # 这两份是无条件要求的：它们记录"采了什么、采到没有"，缺了就无法判断上面几份的成色。
  REQUIRED_ARTIFACTS+=(monitoring/monitoring.log monitoring/component-status.csv)
  # 逐组件 CSV 只按**声明的范围**要求 —— 声明的范围就是这份包声称覆盖的范围。
  # （不用 ${arr[-1]} 这类负下标：macOS 自带 bash 3.2 不支持。）
  for _c in $MONITOR_REQUIRED_COMPONENTS; do
    _spec="$(monitor_spec_for "$_c")"
    REQUIRED_ARTIFACTS+=("monitoring/${_spec%%:*}")
  done
  unset _c _spec
fi
MISSING_ARTIFACTS=""
for _f in "${REQUIRED_ARTIFACTS[@]}"; do
  if [[ ! -s "${RUN_DIR}/${_f}" ]]; then MISSING_ARTIFACTS="${MISSING_ARTIFACTS} ${_f}"; fi
done
unset _f

# 存在且非空，不等于"有数据"。逐组件复算可用的数据行数：`<文件名>:<必需列号>`，
# 列号与 capture-monitoring.sh 里各 read_* 判定"这次采样算不算数"的字段一一对应。
if [[ "$MONITOR_SCOPE" != "skipped" ]]; then
  for _c in $MONITOR_REQUIRED_COMPONENTS; do
    _spec="$(monitor_spec_for "$_c")"
    _name="${_spec%%:*}"
    _cols="${_spec#*:}"
    _rows="$(csv_usable_rows "${RUN_DIR}/monitoring/${_name}" "$_cols")"
    if [[ "${_rows:-0}" -lt 1 ]]; then
      MONITOR_ISSUES="${MONITOR_ISSUES}monitoring/${_name} 没有一行可用采样（第 ${_cols} 列需齐备且为数值，实测 ${_rows:-0} 行）"$'\n'
      case " ${MISSING_ARTIFACTS} " in
        *" monitoring/${_name} "*) ;;
        *) MISSING_ARTIFACTS="${MISSING_ARTIFACTS} monitoring/${_name}" ;;
      esac
    fi
  done
  unset _c _spec _name _cols _rows
fi

MANIFEST="${RUN_DIR}/manifest.json"
# node 存在但执行失败（脚本自身报错、磁盘满、环境变量畸形）时，此前完全没有告警路径：
# 脚本照常打印"压测结束"，却留下一个没有 sha256 清单、无法第三方复核的不完整证据包。
MANIFEST_OK=0
if command -v node >/dev/null 2>&1; then
  if RUN_DIR="$RUN_DIR" RUN_ID="$RUN_ID" PROFILE="$PROFILE" BASE_URL="$BASE_URL" \
     ENABLE_WRITES="$ENABLE_WRITES" K6_EXIT="$K6_EXIT" STARTED_AT="$STARTED_AT" ENDED_AT="$ENDED_AT" \
     DURATION_SEC="$DURATION_SEC" COMMIT_SHA="$COMMIT_SHA" BRANCH_NAME="$BRANCH_NAME" GIT_DIRTY="$GIT_DIRTY" \
     K6_VERSION="$K6_VERSION" K6_SCRIPT="$K6_SCRIPT" MONITOR_INTERVAL="$MONITOR_INTERVAL" \
     TARGET_HOST="$TARGET_HOST" MONITOR_SCOPE="$MONITOR_SCOPE" REDACTED_COUNT="$REDACTED_COUNT" \
     MISSING_ARTIFACTS="$MISSING_ARTIFACTS" MONITOR_ISSUES="$MONITOR_ISSUES" \
     node "${SCRIPT_DIR}/write-run-manifest.mjs"; then
    MANIFEST_OK=1
    echo "run-stress-test: 已生成 ${MANIFEST}"
  else
    echo "run-stress-test: manifest.json 生成失败，证据包不完整（缺 sha256 清单与门禁判定）" >&2
  fi
else
  echo "run-stress-test: 未检测到 node，跳过 manifest.json 生成（证据包不完整）" >&2
fi
if [[ "$MANIFEST_OK" != "1" ]]; then MISSING_ARTIFACTS="${MISSING_ARTIFACTS} manifest.json"; fi

echo
echo "run-stress-test: 压测结束，k6 退出码=${K6_EXIT}"
if [[ "$K6_EXIT" == "0" ]]; then
  echo "  ✅ 全部门禁通过"
elif [[ "$K6_EXIT" == "99" ]]; then
  # 实测 k6 v1.8.0：门禁被击穿、以及 abortOnFail 提前中止，退出码都是 99
  echo "  ⚠️ 存在阈值未达标（退出码 99），被击穿指标见 manifest.json 的 thresholds_crossed 字段"
else
  echo "  ⚠️ k6 以非 0 退出码 ${K6_EXIT} 结束（非阈值相关），请核对 k6-console.log" >&2
fi
echo "  证据目录: $RUN_DIR"
if [[ "$MONITOR_SCOPE" == "skipped" ]]; then
  echo "  ⚠️ 本包不含监控证据（--no-monitor）：请另行提供目标机上的监控记录" >&2
fi

# 证据不完整时以 12 退出，且**优先于** k6 的退出码：k6 返回 0 只说明门禁通过，
# 不说明这份证据包能交付。k6 的原始退出码仍完整保留在 k6-exit-code 与 manifest 里。
if [[ -n "$MISSING_ARTIFACTS" ]]; then
  echo "run-stress-test: 证据包不完整，以下必需产物缺失、为空或没有可用数据行：${MISSING_ARTIFACTS}" >&2
  if [[ -n "$MONITOR_ISSUES" ]]; then
    printf '  监控侧明细：\n' >&2
    printf '%s' "$MONITOR_ISSUES" | while IFS= read -r _line; do
      [[ -n "$_line" ]] && echo "    - ${_line}" >&2
    done
    unset _line
  fi
  echo "                 k6 退出码 ${K6_EXIT}（已记录在 k6-exit-code / manifest.json），但本包不可交付。" >&2
  exit 12
fi
exit "$K6_EXIT"
