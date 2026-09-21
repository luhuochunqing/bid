#!/usr/bin/env bash
# Input: 压测上下文（SCRIPT_DIR / RUN_DIR / RUN_ID / PROFILE / BASE_URL / K6_EXIT / MISSING_ARTIFACTS / DELIVERY_BLOCKERS ... 见 archive_write_manifest）
# Output: 归档三步（控制台规范化 → 凭证脱敏 → 残留扫描）的结果，以及 manifest.json 与 MANIFEST_OK / MANIFEST_SELF_NONDELIVERABLE
# Pos: scripts/performance/ - 压测证据的**归档层**：产物落盘之后、对外交付之前的加工与自证
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：这三步的**顺序本身就是契约**——脱敏必须在扫描之前、扫描必须在写清单
# 之前（清单里记的是各产物脱敏后的 sha256，顺序反了清单就对不上），而"顺序"这种东西
# 夹在 500 行的入口脚本里是看不出来的。这里每一步都写清"为什么必须在这一步"。
#
# 凭证这条链上的三件事，缺一件就有一整类缺陷无人发现：
#   1. 脱敏（archive_redact）：k6 的 --summary-export 会把 setup() 的返回值原样写进
#      setup_data，而负载脚本的 setup() 正在那里登录——不脱敏就是把 bearer token 交付出去；
#   2. 扫描（archive_scan）：只脱敏不扫描时，"脱敏覆盖到了哪些文件"就只是本脚本的一句声明，
#      新增一份产物而忘了加进 SCRUB_FILES，凭证会原样留在包里（产物一个不少、sha256 也对得上）；
#   3. 清单复核（archive_write_manifest）：runner 说的和清单各自独立算的那份对不上时，
#      以"不可交付"收敛，而不是以 runner 自己的结论收敛。

# k6 自己输出的横幅与指标行带行尾空白，不改的话每一次归档都会让 `git diff --check` 常红，
# 而这条门禁红灯久了就没人看了。只删行尾空白，不动任何可见字符与行序。
archive_normalize_console() {
  local _log="$1/k6-console.log"
  [[ -f "$_log" ]] || return 0
  sed -i '' -e 's/[[:space:]]*$//' "$_log" 2>/dev/null \
    || sed -i -e 's/[[:space:]]*$//' "$_log" 2>/dev/null \
    || echo "run-stress-test: 警告 —— k6-console.log 行尾空白规范化失败（不影响证据内容）" >&2
}

# 凭证脱敏。设置 REDACTED_COUNT。
# 脱敏必须排在 archive_scan 之前：先扫后脱等于扫描的是未处理的原件，会把必然存在的
# 命中当成"残留"，也把脱敏的效果排除在结论之外。
archive_redact() {
  local _run_dir="$1" _out=""
  REDACTED_COUNT=0
  if ! command -v node >/dev/null 2>&1; then return 0; fi
  SCRUB_FILES=("${_run_dir}/k6-summary.json" "${_run_dir}/k6-console.log")
  if [[ -f "${_run_dir}/k6-raw-samples.csv" ]]; then SCRUB_FILES+=("${_run_dir}/k6-raw-samples.csv"); fi
  if [[ -d "${_run_dir}/monitoring" ]]; then SCRUB_FILES+=("${_run_dir}/monitoring/monitoring.log"); fi
  if _out="$(node "${SCRIPT_DIR}/scrub-secrets.mjs" "${SCRUB_FILES[@]}" 2>&1)"; then
    REDACTED_COUNT="$(printf '%s\n' "$_out" | sed -nE 's/^redacted=([0-9]+)$/\1/p' | tail -1)"
    REDACTED_COUNT="${REDACTED_COUNT:-0}"
    echo "run-stress-test: 凭证脱敏完成（替换 ${REDACTED_COUNT} 处）"
  else
    echo "run-stress-test: 凭证脱敏失败，证据包可能含可用凭证：${_out}" >&2
  fi
}

# 脱敏之后再扫一遍残留，且用**同一个脚本、同一组检测器**（scrub-secrets.mjs --scan）。
# 设置 SECRET_SCAN_STATUS / SECRET_SCAN_DETAIL / SECRET_SCAN_UNSCANNED，并打印命中明细。
#
# 状态取**两路来源的交集**：扫描器的退出码（0=passed / 1=failed / 3=incomplete）与它
# stdout 上的 `scan_status=` 机器可读行。两者只要有一路缺失或彼此不一致，就收敛到
# unverified 并说明是哪一路 —— 这台机器上前者可以由包装层截断（`| head`、supervisor
# 只记 rc）、后者可以由输出被重定向而丢失；任何一路单独都可能是"看上去正常"的假信号。
# 不这样做的话，一个被截断/被篡改的扫描输出就能让本包自称"已确认无残留"。
archive_scan() {
  local _run_dir="$1" _out="" _rc=0 _exit_status="" _line_status=""
  SECRET_SCAN_STATUS="unverified"
  SECRET_SCAN_DETAIL="未检测到 node，无法证明本包不含凭证残留"
  SECRET_SCAN_UNSCANNED=""
  if ! command -v node >/dev/null 2>&1; then return 0; fi

  _out="$(node "${SCRIPT_DIR}/scrub-secrets.mjs" --scan "$_run_dir" 2>&1)"
  _rc=$?
  case "$_rc" in
    0) _exit_status="passed" ;;
    1) _exit_status="failed" ;;
    3) _exit_status="incomplete" ;;
    *) _exit_status="exit_${_rc}" ;;
  esac
  _line_status="$(printf '%s\n' "$_out" | sed -nE 's/^scan_status=([a-z_]+)$/\1/p' | tail -1)"
  SECRET_SCAN_UNSCANNED="$(printf '%s\n' "$_out" | sed -nE 's/^scan_unscanned_files=(.*)$/\1/p' | tail -1)"

  SECRET_SCAN_DETAIL="$(printf '%s' "$_out" | tr '\n' ' ')"
  if [[ -z "$_line_status" ]]; then
    SECRET_SCAN_DETAIL="扫描器未输出可解析的 scan_status（退出码 ${_rc}）；原始输出：${SECRET_SCAN_DETAIL}"
  elif [[ "$_line_status" != "$_exit_status" ]]; then
    SECRET_SCAN_DETAIL="扫描器退出码 ${_rc}（判定 ${_exit_status}）与 stdout 的 scan_status=${_line_status} 不一致；原始输出：${SECRET_SCAN_DETAIL}"
  else
    SECRET_SCAN_STATUS="$_line_status"
  fi

  case "$SECRET_SCAN_STATUS" in
    passed) echo "run-stress-test: 凭证残留扫描通过（$(printf '%s' "$SECRET_SCAN_DETAIL" | sed -n '1p'))" ;;
    *)
      echo "run-stress-test: 凭证残留扫描未通过（status=${SECRET_SCAN_STATUS}），本包不可交付：" >&2
      printf '%s\n' "$_out" >&2
      ;;
  esac
}

# 生成 manifest.json。设置 MANIFEST_OK / MANIFEST_SELF_NONDELIVERABLE，并在清单缺失时
# 追加进 MISSING_ARTIFACTS、在清单独立判定不可交付时追加交付阻塞项。
#
# 退出码 0 与 12 都算"清单已写出"：12 是清单生成器**自己的判断**（它独立复算了缺失产物、
# 有效采样行与凭证扫描），不是失败。把 12 当成失败会让 runner 一边说"清单生成失败"、
# 一边留下一个结论是"不可交付"的清单，读者无从取舍。
#
# 但"清单写出来了"不等于"runner 可以按自己的结论收尾"：runner 侧算出的结论与清单侧独立
# 算出的结论必须**同时**成立才叫可交付。runner 少算了什么（例如某份产物是二进制、扫描
# 无法覆盖），只有清单会先发现——所以这里把清单的判断并回 runner 的结论，而不是相反。
archive_write_manifest() {
  local _out="" _rc=0 _n
  MANIFEST_OK=0
  MANIFEST_SELF_NONDELIVERABLE=0
  if ! command -v node >/dev/null 2>&1; then
    echo "run-stress-test: 未检测到 node，跳过 manifest.json 生成（证据包不完整）" >&2
    MISSING_ARTIFACTS="${MISSING_ARTIFACTS} manifest.json"
    return 0
  fi

  # 逐项显式传入，不用 `export`：本脚本的 ENABLE_WRITES 是 0/1，而 k6 那一侧要的是
  # true/false；export 会把两个语义混成一份，manifest 里的 enable_writes 从此恒为 false
  # 且没有任何一处会报错（实测踩过）。
  # MONITOR_REQUIRED_COMPONENTS 传的是**声明的集合**而不是"该有哪些监控 CSV"：清单侧自己
  # 去 monitor-spec.sh --print-json 取组件与列号，避免 runner 透传一份可能漏传的清单。
  # 这里把 shell 内部用的空格分隔换成**对外约定的逗号分隔**（与 --monitor-required-components
  # 的写法一致）：两种写法都表示同一件事，但越过进程边界时只应有一种，否则两边各认一种就
  # 会像实测那样把整串当成一个组件名。清单侧同时也容忍空白，两处注释互相指向。
  local _required_csv
  _required_csv="$(printf '%s' "$MONITOR_REQUIRED_COMPONENTS" | tr ' ' ',')"
  _out="$(RUN_DIR="$RUN_DIR" RUN_ID="$RUN_ID" PROFILE="$PROFILE" BASE_URL="$BASE_URL" \
     ENABLE_WRITES="$ENABLE_WRITES" K6_EXIT="$K6_EXIT" STARTED_AT="$STARTED_AT" ENDED_AT="$ENDED_AT" \
     DURATION_SEC="$DURATION_SEC" COMMIT_SHA="$COMMIT_SHA" BRANCH_NAME="$BRANCH_NAME" GIT_DIRTY="$GIT_DIRTY" \
     K6_VERSION="$K6_VERSION" K6_SCRIPT="$K6_SCRIPT" MONITOR_INTERVAL="$MONITOR_INTERVAL" \
     TARGET_HOST="$TARGET_HOST" MONITOR_SCOPE="$MONITOR_SCOPE" \
     MONITOR_REQUIRED_COMPONENTS="$_required_csv" REDACTED_COUNT="$REDACTED_COUNT" \
     MISSING_ARTIFACTS="$MISSING_ARTIFACTS" MONITOR_ISSUES="$MONITOR_ISSUES" \
     DELIVERY_BLOCKERS="$DELIVERY_BLOCKERS" SECRET_SCAN_STATUS="$SECRET_SCAN_STATUS" \
     SECRET_SCAN_DETAIL="$SECRET_SCAN_DETAIL" \
     node "${SCRIPT_DIR}/write-run-manifest.mjs" 2>&1)"
  _rc=$?

  case "$_rc" in
    0|12) MANIFEST_OK=1 ;;
    *)
      echo "run-stress-test: manifest.json 生成失败（退出码 ${_rc}），证据包不完整（缺 sha256 清单与门禁判定）" >&2
      [[ -n "$_out" ]] && printf '%s\n' "$_out" >&2
      MISSING_ARTIFACTS="${MISSING_ARTIFACTS} manifest.json"
      return 0
      ;;
  esac
  echo "run-stress-test: 已生成 ${RUN_DIR}/manifest.json"
  # 退出码 12 时把清单侧自己的结论原样转给操作者（滤掉上面已读的机器可读行）：
  # 只说"已生成清单"会让人以为一切正常，而清单刚判过"不可交付"。
  if [[ "$_rc" == "12" ]]; then
    printf '%s\n' "$_out" | grep -vE '^(manifest_[a-z_]+=|write-run-manifest: manifest\.json 已写入)$' >&2 || true
  fi

  # 清单侧的机器可读结论。计数 > 0 或退出码 12 都表示"清单独立复核后判定本包不可交付"。
  # 用计数而不是解析 JSON：runner 侧没有 JSON 解析器，用 grep 去抠字段是对清单结构的
  # 隐式依赖（清单多一个同名字段就会静默取错）。
  _n="$(printf '%s\n' "$_out" | sed -nE 's/^manifest_missing_artifact_count=([0-9]+)$/\1/p' | tail -1)"
  if [[ "$_rc" == "12" ]] || [[ -n "$_n" && "$_n" -gt 0 ]]; then
    MANIFEST_SELF_NONDELIVERABLE=1
    add_delivery_blocker manifest_verified_nondeliverable
  fi
}
