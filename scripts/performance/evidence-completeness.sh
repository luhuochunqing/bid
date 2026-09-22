#!/usr/bin/env bash
# Input: run-stress-test.sh 的上下文（RUN_DIR / MONITOR_SCOPE / MONITOR_REQUIRED_COMPONENTS / K6_EXIT / SECRET_SCAN_* / TOOLCHAIN_FAILURE*）
# Output: MISSING_ARTIFACTS（缺失/为空/无有效数据行的必需产物）、DELIVERY_BLOCKER_CODES 与 DELIVERY_BLOCKERS（产物齐全仍不可交付的原因，前者给清单生成器、后者给人看）、最终退出码（1 / 12 / k6 自身码）
# Pos: scripts/performance/ - 压测证据的**完整性判定与交付出口**：这份包能不能交付，只在这里下结论
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：判定同时决定对外交付语义（exit 12 优先于 k6 退出码）与 manifest 的
# evidence_complete / missing_artifacts / delivery_blockers 三个字段，是"跑完了 ≠ 证据齐了"
# 这句话唯一落地的地方。它此前夹在 k6 调用与收尾打印之间，改一行都不好看清影响面。
#
# 判据分两类，**不要合并**：
#   1. 缺产物（missing_artifacts）：文件不存在、为空、或存在但没有一行可用数据；
#   2. 交付阻塞项（delivery_blockers）：产物一个不少，但这份包仍然不能交付
#      （例如 --no-monitor 缺监控原始数据、凭证残留扫描未通过）。
# 只判第 1 类时，`--no-monitor` 的包会天然"缺 0 个文件"并被判证据完整——与 README
# "这类包不算证据齐全"正好相反。
#
# 独立性约定：本文件**不复用采集器自报的 component-status.csv**，逐份 CSV 自己复算一遍
# （清单不给自己发合格证）；交付阻塞项的文案取自 delivery-blockers.tsv，与 manifest 生成器
# 读的是同一份资源，避免同一件事在两处各写一句话后漂移（§7.4 跨语言契约）。
#
# 与归档层的依赖方向：归档层（evidence-archive.sh）设置 TOOLCHAIN_FAILURE /
# TOOLCHAIN_FAILURE_REASON，本层在 evidence_finalize 里**在调用时**读取（不是 source 时），
# 因此两层的 source 顺序不影响取值；入口 run-stress-test.sh 固定按「契约 → 守卫 → 前置 →
# 生命周期 → 判定 → 归档」的顺序 source。取不到时按 `:-0` 兜底成"工具链正常"，于是只
# source 本文件做单测的行为等价于旧版（12 / k6 码分流）。

# 独立复算监控 CSV 里"有效采样行"的条数。判据与该 CSV 里下游分析真正要用的列一致
# （列号从 1 起，第 1 列是 timestamp，列号来自 monitor-spec.sh）。
# 只查 `-s` 是不够的：采集器在 mysql 客户端缺失、Redis 不可达、应用进程找不到时留下的
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

# 交付阻塞项文案表（delivery-blockers.tsv）的**整表校验**。shell 侧与 JS 侧读同一份资源，
# 因此判据必须逐条一致：重复 code 时 shell 的 awk 取第一行、JS 的 Map 取最后一行，
# 两侧会给出不同文案而**都不报错**——这正是"单一来源"名存实亡的形态。
# 因此这里在**开工之前**（早于证据目录创建）整表校验一次，任何一处格式违规都以 exit 1
# 拒绝执行，而不是等某个 code 恰好被用到时才失败。
# 规则与 manifest-verify.mjs 的 readBlockerTexts() 逐条对应：非注释非空行必须恰好一个 TAB、
# code 形如 [a-z][a-z0-9_]*、文案非空且无首尾空白、code 不重复、整表至少一条。
blocker_catalog_assert() {
  local _file="${SCRIPT_DIR}/delivery-blockers.tsv"
  if [[ ! -f "$_file" ]]; then
    echo "run-stress-test: 读不到交付阻塞项文案表 ${_file}，无法判定本包为何不可交付，拒绝执行" >&2
    exit 1
  fi
  local _bad
  # 判据与 manifest-verify.mjs 的 readBlockerTexts() 逐条对应。只用 POSIX awk 语法
  # （macOS 自带的是 one-true-awk，没有 gensub 这类 gawk 扩展）。
  _bad="$(awk -F'\t' '
    { if ($0 ~ /\r/) { print NR ": 含 CR（行尾必须是 LF）"; err = 1; exit }
      if ($0 == "" || $0 ~ /^#/) next
      if (NF != 2) { print NR ": 不是恰好一个 TAB 分隔的 <code><TAB><文案>（实测 " NF - 1 " 个 TAB）"; err = 1; exit }
      if ($1 !~ /^[a-z][a-z0-9_]*$/) { print NR ": code [" $1 "] 不合规（只允许小写字母/数字/下划线，且以字母开头）"; err = 1; exit }
      if ($2 == "" || $2 ~ /^[[:space:]]/ || $2 ~ /[[:space:]]$/) { print NR "（" $1 "）: 文案为空或含首尾空白"; err = 1; exit }
      if ($1 in seen) { print NR ": code [" $1 "] 重复（文案表必须是一对一映射）"; err = 1; exit }
      seen[$1] = 1
      n++
    }
    END { if (!err && n == 0) print "0: 整表没有任何有效条目" }
  ' "$_file")"
  if [[ -n "$_bad" ]]; then
    echo "run-stress-test: 交付阻塞项文案表格式违规 —— 第 ${_bad%%:*} 行：${_bad#*:}" >&2
    echo "                 该表是「本包为何不可交付」的唯一来源；格式一旦破坏，shell 与清单生成器" >&2
    echo "                 会各自读出一个不同的文案而不报错（重复 code 时一个取首行、一个取末行），" >&2
    echo "                 因此拒绝执行。请修复 ${_file} 后重试。" >&2
    exit 1
  fi
}

# 交付阻塞项文案的唯一来源（delivery-blockers.tsv）。shell 侧与 JS 侧读同一份资源，
# 因此不存在"两处各写一句、靠注释声明逐字一致"的跨语言契约。
#
# 传给清单生成器的是 **code**，不是文案（见下面 DELIVERY_BLOCKER_CODES）：早先传文案时，
# 调用方可以把任意一串表外文本直接注入清单的 delivery_blockers，单一来源名存实亡。
#
# 取不到文案时**以 exit 1 拒绝收尾**，而不是追加一个空串：空串会被下面的过滤掉，
# 于是"本包不可交付"这件事凭空消失、脚本照常以 k6 的退出码结束——文案表被删掉一行
# 就能让整条交付阻塞静默失效。这里必须硬失败。
# 注意：不能用 `$(blocker_text ...)` 的形式调用——命令替换里 exit 只结束子 shell，
# 调用方拿到的是空串。所以本函数自己负责追加。
add_delivery_blocker() {
  local _code="$1" _text=""
  _text="$(awk -F'\t' -v k="$_code" '$1 == k { sub(/^[^\t]*\t/, ""); print; exit }' \
    "${SCRIPT_DIR}/delivery-blockers.tsv")"
  if [[ -z "$_text" ]]; then
    echo "run-stress-test: 交付阻塞项 '${_code}' 在 delivery-blockers.tsv 中没有对应文案，" >&2
    echo "                 无法说明本包为何不可交付，拒绝收尾（请检查该文件是否被改动）。" >&2
    exit 1
  fi
  # code 是给清单生成器的（它拿 code 回查同一张表得到文案）；文案是给人看的（stderr）。
  # 两者同行追加，取用方各取所需；判定"本包可不可交付"只看 code 一侧非空与否。
  DELIVERY_BLOCKER_CODES="${DELIVERY_BLOCKER_CODES}${_code}"$'\n'
  DELIVERY_BLOCKERS="${DELIVERY_BLOCKERS}${_text}"$'\n'
}

# 必需产物清单（写进 REQUIRED_ARTIFACTS）。监控侧只有 `monitoring.log` 与
# `component-status.csv` 是无条件要求的（它们记录"采了什么、采到没有"），逐组件 CSV 只按
# **声明的范围**要求——声明的范围就是这份包声称覆盖的范围。
evidence_required_artifacts() {
  REQUIRED_ARTIFACTS=(k6-summary.json k6-console.log k6-exit-code k6-raw-samples.csv)
  local _c _spec
  if [[ "$MONITOR_SCOPE" != "skipped" ]]; then
    REQUIRED_ARTIFACTS+=(monitoring/monitoring.log monitoring/component-status.csv)
    for _c in $MONITOR_REQUIRED_COMPONENTS; do
      _spec="$(monitor_spec_for "$_c")"
      REQUIRED_ARTIFACTS+=("monitoring/${_spec%%:*}")
    done
  fi
}

# 判定缺失产物：文件不存在/为空，或存在却没有一行可用数据。
evidence_collect_missing() {
  local _f _c _spec _name _cols _rows
  evidence_required_artifacts
  MISSING_ARTIFACTS=""
  for _f in "${REQUIRED_ARTIFACTS[@]}"; do
    if [[ ! -s "${RUN_DIR}/${_f}" ]]; then MISSING_ARTIFACTS="${MISSING_ARTIFACTS} ${_f}"; fi
  done

  # 存在且非空，不等于"有数据"。逐组件复算可用的数据行数：`<文件名>:<必需列号>`，
  # 列号与 capture-monitoring.sh 各 read_* 判定"这次采样算不算数"的字段一一对应。
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
  fi
}

# 判定交付阻塞项：产物一个不少，但这份包仍然不能交付的原因。
# 文案一律取自 delivery-blockers.tsv，且**不追加任何明细**：明细进 manifest 的结构化字段
# （secret_scan.* / monitoring_issues），文案保持逐字稳定 —— 清单侧也会独立派生同一批
# 阻塞项并按字符串去重，这里拼上一段动态明细，去重就永远命中不了，同一件事在清单里会
# 记成两条不同的原因。
evidence_collect_blockers() {
  DELIVERY_BLOCKERS=""
  DELIVERY_BLOCKER_CODES=""
  if [[ "$SECRET_SCAN_STATUS" != "passed" ]]; then
    # 扫描器的词汇表就是本层的词汇表（passed / failed / incomplete / unverified），
    # 不在中间再引入一套别名：两套名字意味着两处 case 分支要各自维护映射。
    case "$SECRET_SCAN_STATUS" in
      failed) add_delivery_blocker secret_scan_residue ;;
      incomplete) add_delivery_blocker secret_scan_incomplete ;;
      *) add_delivery_blocker secret_scan_unverified ;;
    esac
  fi
  if [[ "$MONITOR_SCOPE" == "skipped" ]]; then
    # --no-monitor 意味着本包**只有 k6 侧的证据**。README 与本脚本的头部注释都写着
    # "监控证据是报告第五章结论的原始数据"，缺了它就不是一份完整交付物。
    # 只把退出码留给 k6 的 0，等于告诉调用方"成功了"，而请求方要的三样里少了一样。
    add_delivery_blocker external_monitoring_required
  fi
}

# 汇总输出 + 退出码。
#
# **工具链失败优先于一切**（exit 1）：node 缺失、扫描器/manifest writer 无法执行或它们的
# 结论不可解析，都意味着"证据包的结论根本没被算出来"，而不是"算出来是不完整"。
# 早先这些路径一律先记进 MISSING_ARTIFACTS / DELIVERY_BLOCKERS，最后由这里统一报 12 ——
# 调用方看到 12（"k6 跑完了但证据不可交付"）会去补采证据，而真实原因是压测机上没有 node，
# 补多少次采都没用。README 与本脚本头部的退出码表一直写着这类情况是 1，实现必须对齐。
# 只有工具链正常（writer 退出码 0/12、扫描与清单结论均可解析）时，才进入 12 / k6 码的分流。
#
# 证据不完整**或存在交付阻塞项**时以 12 退出，且**优先于** k6 的退出码：
# k6 返回 0 只说明门禁通过，不说明这份证据包能交付。k6 的原始退出码仍完整保留在
# k6-exit-code 与 manifest 里。
evidence_finalize() {
  local _line
  echo
  echo "run-stress-test: 压测结束，k6 退出码=${K6_EXIT}"
  if [[ "$K6_EXIT" == "0" ]]; then
    # 只说 k6 的阈值门禁——"证据包能不能交付"是另一件事，见下方 exit 12 分支。
    # 这句若写成"全部门禁通过"，紧接着打印"不可交付"时读者容易停在第一句上。
    echo "  ✅ k6 门禁全部通过（本包是否可交付另行判定）"
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

  # 工具链失败：本包的结论不可信、不可补——拒绝执行（exit 1），且**不**报 12。
  # 退出码 12 的语义是"k6 已跑完、证据确实不齐"，让调用方按缺件去补采；工具链坏掉时
  # 这句话是假的（补采不会有任何变化），而 exit 1 的语义"拒绝执行"才是真的。
  if [[ "${TOOLCHAIN_FAILURE:-0}" != "0" ]]; then
    echo "run-stress-test: 工具链不可用，本包无法定论，拒绝收尾：${TOOLCHAIN_FAILURE_REASON:-未说明}" >&2
    echo "                 证据目录 ${RUN_DIR} 内的原始产物已落盘，但本次没有「本包可交付/不可交付」的结论。" >&2
    echo "                 修复工具链环境后重跑；退出码 1（拒绝执行）。" >&2
    exit 1
  fi

  if [[ -n "$MISSING_ARTIFACTS" ]] || [[ -n "$DELIVERY_BLOCKER_CODES" ]]; then
    if [[ -n "$MISSING_ARTIFACTS" ]]; then
      echo "run-stress-test: 证据包不完整，以下必需产物缺失、为空或没有可用数据行：${MISSING_ARTIFACTS}" >&2
    fi
    if [[ -n "$MONITOR_ISSUES" ]]; then
      printf '  监控侧明细：\n' >&2
      printf '%s' "$MONITOR_ISSUES" | while IFS= read -r _line; do
        [[ -n "$_line" ]] && echo "    - ${_line}" >&2
      done
    fi
    if [[ -n "$DELIVERY_BLOCKERS" ]]; then
      printf '  交付阻塞项（产物齐全也不足以交付）：\n' >&2
      printf '%s' "$DELIVERY_BLOCKERS" | while IFS= read -r _line; do
        [[ -n "$_line" ]] && echo "    - ${_line}" >&2
      done
    fi
    echo "                 k6 退出码 ${K6_EXIT}（已记录在 k6-exit-code / manifest.json），但本包不可交付。" >&2
    exit 12
  fi
  exit "$K6_EXIT"
}
