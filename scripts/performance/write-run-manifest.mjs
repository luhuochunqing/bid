// Input: 单次压测证据目录（k6-summary.json / k6-console.log / monitoring/*）+ 由 run-stress-test.sh 注入的运行上下文环境变量
// Output: 该证据目录的 manifest.json —— 运行元数据、SLA 门禁声明与击穿项、头部指标、各产物 sha256、
//         完整性判定（missing_artifacts / delivery_blockers / external_monitoring_required / secret_scan），
//         以及供 runner 读取的机器可读结论行（manifest_*）
// Pos: scripts/performance/ - 压测证据包清单生成器（装配清单对象并写盘；读取与复核在同目录另两个模块）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 三个文件的分工（都可单独 node --check，也能在没有服务的机器上跑）：
//   manifest-verify.mjs  独立复核：产物是否齐 / 监控 CSV 有没有数据 / 包内还有没有凭证残留 / 阻塞项文案
//   k6-evidence.mjs      k6 侧三份产物的解释：头部指标、声明的与击穿的门禁、退出码、脚本路径
//   本文件               装配清单对象 + 写盘 + 告诉 runner 自己的结论
//
// 关于门禁判定（本文件内唯一真相来源，run-stress-test.sh 的文案与之对齐）：
//   - k6 --summary-export 里 metrics.<name>.thresholds 的语义是"该表达式是否被击穿"
//     （true = 击穿）。实测 k6 v1.8.0：失败运行 http_req_failed = {"rate<0.01": true}，
//     通过运行同一指标 = {"rate<0.01": false}。所以它并非不可用，但旧格式只给布尔、
//     拿不到实测值，且字段形态随 k6 版本漂移。因此仍以**退出码**为主判据：
//       声明项 = thresholds_declared（仅记录压测脚本写了哪些门禁）
//       通过与否 = k6 退出码（0 全通过 / 99 有击穿，abortOnFail 提前中止也是 99）
//       击穿项  = 从 k6 控制台日志抓取（两种措辞都认，见 k6-evidence.mjs）
//   - 完整性判定**不照抄**调用方注入的值：产物是否齐、监控 CSV 有没有数据、包内还有没有
//     凭证残留，都在 manifest-verify.mjs 里各复算一遍，再与注入值取并集。
//
//   - 凭证判定：整包扫描由 manifest-verify.mjs 独立跑一遍；**清单自身**在写盘前过同一组
//     检测器（scrubText 从 scrub-secrets.mjs import），因为整包扫描排在它写盘之前。
//
// 退出码：
//   0   清单已写出，且本脚本独立复核后判定本包可交付
//   12  清单已写出，但本脚本独立复核后判定本包不可交付（详见 manifest.json 的
//       missing_artifacts / delivery_blockers）。调用方必须把 0 与 12 都当成"清单已写出"
//   2   用法错误（注入的组件声明不合法）—— 见 manifest-verify.mjs 的 UsageError
//   1   工具链自身不可用（规格读不出来、阻塞项文案缺失、清单脱敏后仍残留凭证）—— 见 ToolchainError

import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'
import { fileURLToPath } from 'node:url'
import {
  ToolchainError,
  UsageError,
  collectDeliveryBlockers,
  readBlockerTexts,
  readMonitorSpec,
  requiredArtifacts,
  resolveRequiredComponents,
  runSecretScan,
  verifyArtifacts,
} from './manifest-verify.mjs'
import { scrubText } from './scrub-secrets.mjs'
import { extractCrossedThresholds, readK6Summary, resolveExitCode, resolveK6ScriptPath } from './k6-evidence.mjs'

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url))
const dir = process.env.RUN_DIR
if (!dir) {
  console.error('write-run-manifest: 缺少 RUN_DIR 环境变量')
  process.exit(2)
}

let spec, required, files, verified, scan, blockerTexts
try {
  spec = readMonitorSpec(SCRIPT_DIR)
  const monitorScope = process.env.MONITOR_SCOPE ?? ''
  required = resolveRequiredComponents(process.env.MONITOR_REQUIRED_COMPONENTS, monitorScope, spec)
  files = requiredArtifacts(monitorScope, required, spec)
  verified = verifyArtifacts(dir, files, monitorScope, required, spec)
  scan = runSecretScan(dir, SCRIPT_DIR)
  blockerTexts = readBlockerTexts(SCRIPT_DIR)
} catch (error) {
  if (error instanceof UsageError) {
    console.error(`write-run-manifest: ${error.message}`)
    process.exit(2)
  }
  if (error instanceof ToolchainError) {
    console.error(`write-run-manifest: ${error.message}`)
    process.exit(1)
  }
  throw error
}

// 逐产物 sha256。清单记的是各产物**脱敏后**的内容，因此本脚本必须排在脱敏之后运行。
const artifacts = {}
for (const rel of files) {
  const abs = path.join(dir, rel)
  if (!fs.existsSync(abs)) {
    artifacts[rel] = null
    continue
  }
  const buf = fs.readFileSync(abs)
  artifacts[rel] = {
    bytes: buf.length,
    sha256: crypto.createHash('sha256').update(buf).digest('hex'),
  }
}

const { headline, declaredThresholds } = readK6Summary(dir)
const { exitCode, mismatch: exitCodeMismatch } = resolveExitCode(dir, process.env.K6_EXIT)
//   0  = 全部门禁通过
//   99 = 有门禁被击穿。**门禁被击穿但测试跑完、与命中 abortOnFail 提前中止，退出码都是 99**
//        （k6 v1.8.0 实测两次）。仓库内 k6-tests/load-test.js 当前未启用 abortOnFail
//        （`grep -rn abortOnFail k6-tests/` 无命中），所以没有单独的 abortOnFail 退出码可判。
//   其余非 0（如 1）视为"未判定"（null），而不是"未通过"——k6 崩溃与门禁击穿是两回事。
const thresholdsPassed = exitCode === 0 ? true : exitCode === 99 ? false : null
const { crossed, warning: crossedParseWarning } = extractCrossedThresholds(dir, thresholdsPassed)
const k6ScriptPath = resolveK6ScriptPath(process.env.K6_SCRIPT)

// ── 完整性判定：注入值 ∪ 本脚本独立复核的结果 ────────────────────────────────
const splitLines = (raw) =>
  String(raw ?? '')
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
const injectMissing = String(process.env.MISSING_ARTIFACTS ?? '')
  .split(/\s+/)
  .map((s) => s.trim())
  .filter((s) => s.length > 0)
// 并集而非覆盖：调用方可能记着本脚本看不到的原因（采集器异常、上一轮残留），
// 本脚本也可能发现调用方漏算的（新增产物没进它的清单、CSV 只有表头）。
const missingArtifacts = [...new Set([...injectMissing, ...verified.missing])]
// 交付阻塞项与"缺产物"是两回事：这里是**产物一个不少、但这份包仍然不能交付**的原因
// （例如 --no-monitor 缺监控原始数据、凭证残留扫描未通过）。只判 missing_artifacts 时，
// `--no-monitor` 会让这个字段天然为空，于是清单对外宣称 evidence_complete=true —— 与
// README"这类包不算证据齐全"的说法正好相反。
const deliveryBlockers = collectDeliveryBlockers({
  texts: blockerTexts,
  injected: splitLines(process.env.DELIVERY_BLOCKERS),
  monitorScope: process.env.MONITOR_SCOPE ?? '',
  scan,
  runnerScanStatus: process.env.SECRET_SCAN_STATUS,
})
const externalMonitoringRequired = process.env.MONITOR_SCOPE === 'skipped'
const evidenceComplete = missingArtifacts.length === 0 && deliveryBlockers.length === 0
const redactionCount = Number.parseInt(process.env.REDACTED_COUNT ?? '0', 10) || 0
// 脱敏之后的残留扫描结论。**覆盖范围要写清楚**：整包扫描发生在 manifest.json 写盘之前，
// 因此那次扫描扫的是"本包内除 manifest.json 外的全部产物"。清单自身的凭证由下面
// scrubManifestSelf() 用**同一组检测器**（import 自 scrub-secrets.mjs，不是另写一份）
// 补一次，次数记在 manifest_self_redactions，所以覆盖范围是"整包"而不是"整包少一个文件"。
// status 取**本脚本自己扫出来的**那份；runner 注入的那份并列记录，两者不一致时
// collectDeliveryBlockers 已按"不可采信"追加了阻塞项。
const secretScan = {
  status: scan.status,
  status_source: 'write-run-manifest.mjs 自行执行 scrub-secrets.mjs --scan',
  runner_reported_status: process.env.SECRET_SCAN_STATUS ?? 'not_run',
  detector_agreement:
    (process.env.SECRET_SCAN_STATUS ?? 'not_run') === scan.status ? 'match' : 'mismatch（已按不可交付处理）',
  unscanned_files: scan.unscanned,
  detectors: ['jwt', 'sensitive_key_value', 'bearer', 'url_userinfo'],
  covers: '本包全部产物：除 manifest.json 外的全部产物由 scrub-secrets.mjs --scan 逐文件扫描；manifest.json 的写盘内容由本脚本用同一组检测器再脱敏一次（见 manifest_self_redactions）',
  manifest_self_redactions: 0,
  detail: scan.detail || (process.env.SECRET_SCAN_DETAIL ?? '').trim() || undefined,
}

// 逐组件状态：采集器自报的数字（component-status.csv）与本脚本独立复算的数字并列记录。
// 两个数字都是**记录**，判定（能不能交付）已经落在上面的 missing_artifacts 里；
// 并排放是刻意的——采集器说 6 行、复核说 0 行时，这种矛盾必须在清单里看得见。
const claimed = new Map()
const statusPath = path.join(dir, 'monitoring/component-status.csv')
if (fs.existsSync(statusPath)) {
  const lines = fs
    .readFileSync(statusPath, 'utf8')
    .split('\n')
    .filter((l) => l.trim() !== '')
  const header = (lines.shift() ?? '').split(',')
  for (const line of lines) {
    // reason 列可能为空，且采集器已把原因里的逗号替换成 ';'，所以定长切分是安全的
    const cells = line.split(',')
    const row = {}
    header.forEach((key, i) => {
      row[key] = cells[i] ?? ''
    })
    claimed.set(row.component, {
      required: row.required === '1',
      available: row.available === '1',
      usable_samples: Number.parseInt(row.usable_samples ?? '', 10) || 0,
      total_samples: Number.parseInt(row.total_samples ?? '', 10) || 0,
      reason: row.reason || undefined,
    })
  }
}
const monitoringComponents = verified.components.map((c) => ({
  ...c,
  ...(claimed.get(c.component) ?? {}),
}))
// 采集侧的具体原因（哪个组件一行有效数据都没有、采集器为何非 0 退出）。
// 只写 missing_artifacts 会让人以为是"文件没生成"，而真实情况多半是"文件生成了、
// 里面没有数据"——这两件事的处置方式完全不同。
const monitorIssues = [...new Set([...splitLines(process.env.MONITOR_ISSUES), ...verified.issues])]

const manifest = {
  run_id: process.env.RUN_ID,
  started_at_utc: process.env.STARTED_AT,
  ended_at_utc: process.env.ENDED_AT,
  duration_seconds: Number(process.env.DURATION_SEC),
  target_base_url: process.env.BASE_URL,
  target_host: process.env.TARGET_HOST ?? null,
  profile: process.env.PROFILE,
  enable_writes: process.env.ENABLE_WRITES === '1',
  // K6_SCRIPT 缺失时不再抛 TypeError；路径已解析为「相对仓库根 or 绝对路径」
  k6_script: k6ScriptPath,
  k6_version: process.env.K6_VERSION,
  monitor_interval_seconds: Number(process.env.MONITOR_INTERVAL),
  // 监控数据采自哪台机器：local-target = 本机即被压目标；local-target-asserted = 目标非回环
  // 地址、但操作者显式声明本机就是目标宿主；skipped = 本包不含监控。采集器只能采本机，
  // 这个字段是第三方判断"monitoring/*.csv 到底描述谁"的唯一依据，不能省。
  // skipped 时 external_monitoring_required=true，本包不可独立交付（见 delivery_blockers）。
  monitor_scope: process.env.MONITOR_SCOPE ?? null,
  monitor_required_components: required,
  repository: {
    commit_sha: process.env.COMMIT_SHA,
    branch: process.env.BRANCH_NAME,
    working_tree: process.env.GIT_DIRTY,
  },
  k6_exit_code: Number.isNaN(exitCode) ? null : exitCode,
  exit_code_mismatch: exitCodeMismatch,
  thresholds_passed: thresholdsPassed,
  thresholds_declared: declaredThresholds,
  thresholds_crossed: crossed,
  thresholds_crossed_parse_warning: crossedParseWarning,
  thresholds_passed_caveat: evidenceComplete
    ? undefined
    : '门禁判定仅反映 k6 退出码；本次证据包不可交付（见 missing_artifacts / delivery_blockers），不可作为交付证据引用',
  evidence_complete: evidenceComplete,
  missing_artifacts: missingArtifacts,
  // 产物齐全但仍不可交付的原因。第三方判断"这份包能不能直接用"必须同时看这两处。
  delivery_blockers: deliveryBlockers,
  // true = 监控原始数据不在这份包里，必须由目标机上的 capture-monitoring.sh 单独产出、
  // 与本包一并交付。此时 evidence_complete / deliverable_ready 均为 false。
  external_monitoring_required: externalMonitoringRequired,
  secret_scan: secretScan,
  // 逐组件的有效采样行数。文件存在 ≠ 有数据：采集器在 mysql 客户端缺失、Redis 不可达、
  // 应用进程找不到时会留下只有表头或整行空值的 CSV，"证据完整"的结论只能建立在这些数字上，
  // 交付前请按 required=true 的组件逐个确认（verified_usable_rows 是复核值，
  // usable_samples 是采集器自报值，两者不一致时以复核值为准）。
  monitoring_components: monitoringComponents,
  monitoring_issues: monitorIssues,
  deliverable_ready: evidenceComplete,
  // 归档前被替换掉的凭证个数（k6 summary 的 setup_data 带着登录得到的 bearer token）。
  // 记下来是为了让"这份包被改过"这件事本身也是可核验的，而不是一个只有工具知道的隐式步骤。
  credential_redactions: redactionCount,
  headline_metrics: headline,
  artifacts,
  note: '原始证据包由 scripts/performance/run-stress-test.sh 生成；sha256 用于校验归档件未被替换。',
}

// ── 清单自身的凭证脱敏 ─────────────────────────────────────────────────────
// manifest.json 也是交付物，而整包扫描排在它写盘之前——BASE_URL 这类环境输入可以带
// userinfo（`http://user:口令@host:8080`），原样写进清单就是一份**扫描覆盖不到**的可用凭证，
// 而且同一目录的扫描照样报 passed。所以清单在写盘前必须过同一组检测器（scrubText 从
// scrub-secrets.mjs import，不另写一份判据），命中就地脱敏并把次数记进 credential_redactions
// 与 secret_scan.manifest_self_redactions —— "这份包被改过"这件事本身也要可核验。
const serialize = (m) => `${JSON.stringify(m, null, 2)}\n`
let manifestText = serialize(manifest)
const selfScrub = scrubText(manifestText)
if (selfScrub.count > 0) {
  const scrubbed = JSON.parse(selfScrub.out)
  scrubbed.credential_redactions = redactionCount + selfScrub.count
  scrubbed.secret_scan = { ...scrubbed.secret_scan, manifest_self_redactions: selfScrub.count }
  manifestText = serialize(scrubbed)
}
// 脱敏后再验一次：检测器对已替换的占位符是幂等的（正则都带 `(?!<redacted:)`），因此这里
// 仍命中就说明脱敏本身没生效。此时**不写盘**：一份声称"扫描通过"却自带凭证的清单，
// 比没有清单更危险。退出码 1 = 工具链自身不可用（与 --help 里的约定一致）。
const residual = scrubText(manifestText)
if (residual.count > 0) {
  console.error(`write-run-manifest: 清单脱敏后仍残留 ${residual.count} 处凭证形态，拒绝写出 manifest.json`)
  process.exit(1)
}

fs.writeFileSync(path.join(dir, 'manifest.json'), manifestText)

// 机器可读结论行：run-stress-test.sh 据此把清单侧的判断并回自己的退出码。
// 用计数而不是让它去解析 JSON —— runner 侧没有 JSON 解析器，用 grep 抠字段是对清单
// 结构的隐式依赖（多一个同名字段就会静默取错）。
console.log('write-run-manifest: manifest.json 已写入')
console.log('manifest_evidence_complete=' + evidenceComplete)
console.log('manifest_missing_artifact_count=' + missingArtifacts.length)
console.log('manifest_delivery_blocker_count=' + deliveryBlockers.length)

if (!evidenceComplete) {
  console.error(
    `write-run-manifest: 本脚本独立复核后判定本包不可交付（缺产物 ${missingArtifacts.length} 项、交付阻塞项 ${deliveryBlockers.length} 项）`,
  )
  process.exit(12)
}
