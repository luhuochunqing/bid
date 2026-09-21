// Input: 单次压测证据目录（k6-summary.json / k6-console.log / monitoring/*）+ 由 run-stress-test.sh 注入的运行上下文环境变量
// Output: 该证据目录的 manifest.json —— 运行元数据、SLA 门禁声明与击穿项、头部指标、各产物 sha256
// Pos: scripts/performance/ - 压测证据包清单生成器（只读解析既有产物，不修改原始证据）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 说明：
//   - k6 --summary-export 里 metrics.<name>.thresholds 的语义是"该表达式是否被击穿"
//     （true = 击穿）。实测 k6 v1.8.0：失败运行 http_req_failed = {"rate<0.01": true}，
//     通过运行同一指标 = {"rate<0.01": false}。所以它并非不可用，但旧格式只给布尔、
//     拿不到实测值，且字段形态随 k6 版本漂移。本脚本因此仍以**退出码**为主判据：
//       声明项 = thresholds_declared（仅记录压测脚本写了哪些门禁）
//       通过与否 = k6 退出码（0 全通过 / 99 有击穿，abortOnFail 提前中止也是 99）
//       击穿项  = 从 k6 控制台日志抓取 "thresholds on metrics '...' have been crossed"
//                 或 abortOnFail 变体 "... were crossed"（两种措辞都要认，见下方正则）
//   - 聚合值同时兼容旧格式（平铺在 metric 对象上）与新格式（包在 values 里）。

import fs from 'node:fs'
import path from 'node:path'
import crypto from 'node:crypto'

const dir = process.env.RUN_DIR
if (!dir) {
  console.error('write-run-manifest: 缺少 RUN_DIR 环境变量')
  process.exit(2)
}

const ARTIFACT_FILES = [
  'k6-summary.json',
  'k6-raw-samples.csv',
  'k6-console.log',
  'k6-exit-code',
  'monitoring/system.csv',
  'monitoring/app-jvm.csv',
  'monitoring/mysql.csv',
  'monitoring/redis.csv',
  'monitoring/monitoring.log',
  'monitoring/component-status.csv',
]

const artifacts = {}
for (const rel of ARTIFACT_FILES) {
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

let headline = null
const declaredThresholds = []
const summaryPath = path.join(dir, 'k6-summary.json')
if (fs.existsSync(summaryPath)) {
  try {
    const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'))
    const metrics = summary.metrics || {}
    // 兼容两种 summary 结构：旧格式聚合值平铺在 metric 上，新格式包在 values 里
    const pick = (name) => {
      const metric = metrics[name]
      if (!metric) return null
      return metric.values ?? metric
    }
    for (const [name, metric] of Object.entries(metrics)) {
      if (metric && metric.thresholds) {
        for (const expr of Object.keys(metric.thresholds)) {
          declaredThresholds.push({ metric: name, threshold: expr })
        }
      }
    }
    const v = (name, ...fields) => {
      const values = pick(name)
      if (!values) return null
      for (const field of fields) {
        if (values[field] !== undefined && values[field] !== null) return values[field]
      }
      return null
    }
    headline = {
      total_http_requests: v('http_reqs', 'count'),
      http_req_duration_avg_ms: v('http_req_duration', 'avg'),
      http_req_duration_p95_ms: v('http_req_duration', 'p(95)'),
      http_req_duration_p99_ms: v('http_req_duration', 'p(99)'),
      // Rate 指标：新格式字段名是 rate，旧 --summary-export 格式落到 value
      http_req_failed_rate: v('http_req_failed', 'rate', 'value'),
      xiyu_error_rate: v('xiyu_error_rate', 'rate', 'value'),
      xiyu_total_requests: v('xiyu_total_requests', 'count'),
      xiyu_read_ops: v('xiyu_read_ops', 'count'),
      xiyu_write_ops: v('xiyu_write_ops', 'count'),
      xiyu_login_avg_ms: v('xiyu_login_duration', 'avg'),
      xiyu_tender_list_p95_ms: v('xiyu_tender_list_duration', 'p(95)'),
      xiyu_tender_detail_p95_ms: v('xiyu_tender_detail_duration', 'p(95)'),
      xiyu_tender_create_p95_ms: v('xiyu_tender_create_duration', 'p(95)'),
      xiyu_project_list_p95_ms: v('xiyu_project_list_duration', 'p(95)'),
      xiyu_project_detail_p95_ms: v('xiyu_project_detail_duration', 'p(95)'),
      xiyu_knowledge_search_p95_ms: v('xiyu_knowledge_search_duration', 'p(95)'),
      xiyu_dashboard_p95_ms: v('xiyu_dashboard_duration', 'p(95)'),
    }
  } catch (error) {
    headline = { parse_error: String(error) }
  }
}

// 退出码以 run-stress-test.sh 注入的 K6_EXIT 为准；缺失或非数字时回退读同目录的
// k6-exit-code 文件——该文件此前只被哈希、从不被读取，导致环境变量一缺清单就写 null。
// 两者不一致时留下显式标记，"退出码 0 但门禁全挂"这类矛盾不能无人发现。
const exitFile = path.join(dir, 'k6-exit-code')
const exitFromEnv = Number.parseInt(process.env.K6_EXIT ?? '', 10)
const exitFromFile = fs.existsSync(exitFile)
  ? Number.parseInt(fs.readFileSync(exitFile, 'utf8').trim(), 10)
  : Number.NaN
const exitCode = Number.isNaN(exitFromEnv) ? exitFromFile : exitFromEnv
const exitCodeMismatch =
  !Number.isNaN(exitFromEnv) && !Number.isNaN(exitFromFile) && exitFromEnv !== exitFromFile
    ? `K6_EXIT=${exitFromEnv} 与 k6-exit-code 文件中的 ${exitFromFile} 不一致`
    : undefined
const consolePath = path.join(dir, 'k6-console.log')
let crossed = []
if (fs.existsSync(consolePath)) {
  const log = fs.readFileSync(consolePath, 'utf8')
  // 两种措辞都必须认，否则恰好最严重的场景会落到空数组：
  //   门禁被击穿但测试跑完   → "thresholds on metrics 'x' have been crossed"
  //   命中 abortOnFail 提前中止 → "thresholds on metrics 'x' were crossed; at least one has
  //                             abortOnFail enabled, stopping test prematurely"
  const matches = log.match(/thresholds on metrics '([^']+)' (?:have been|were) crossed/g) || []
  crossed = matches.flatMap((line) => {
    const inner = line.match(/'([^']+)'/)
    return inner ? inner[1].split(/,\s*/) : []
  })
  crossed = [...new Set(crossed)]
}
// 退出码语义（本文件内唯一真相来源，run-stress-test.sh 的文案与之对齐）：
//   0  = 全部门禁通过
//   99 = 有门禁被击穿。**门禁被击穿但测试跑完、与命中 abortOnFail 提前中止，退出码都是 99**
//        （k6 v1.8.0 实测两次）。仓库内 k6-tests/load-test.js 当前未启用 abortOnFail
//        （`grep -rn abortOnFail k6-tests/` 无命中），所以没有单独的 abortOnFail 退出码可判。
//   其余非 0（如 1）视为"未判定"（null），而不是"未通过"——k6 崩溃与门禁击穿是两回事。
const thresholdsPassed = exitCode === 0 ? true : exitCode === 99 ? false : null
// 判定为未通过却一个指标名都没抓到 → 日志措辞又变了。显式告警，而不是给读者一个空数组。
const crossedParseWarning =
  thresholdsPassed === false && crossed.length === 0
    ? 'thresholds_passed=false 但未能从 k6-console.log 解析出被击穿指标名，请人工核对原始日志'
    : undefined

// k6_script 记成「相对仓库根」的路径，第三方 clone 后可直接定位。
// 不能假设 dir 一定是 <artifacts>/<run-id>：--out 指到仓库外（例如 /tmp）时，
// 老实现 `path.relative(dir + '/../..', K6_SCRIPT)` 会产出 `../Users/...` 这种指向
// 不存在位置的路径，而且不告警——清单里一个静默错误字段，正是本工具最不该有的东西。
function repoRootOf(start) {
  let cur = path.resolve(start)
  for (;;) {
    // worktree 下 .git 是文件而非目录，existsSync 对两者都成立
    if (fs.existsSync(path.join(cur, '.git'))) return cur
    const parent = path.dirname(cur)
    if (parent === cur) return null
    cur = parent
  }
}

const k6ScriptAbs = process.env.K6_SCRIPT ? path.resolve(process.env.K6_SCRIPT) : null
// 仓库根从 **k6 脚本自身所在目录** 向上找，而不是从证据输出目录（dir）向上找。
// 该字段回答的是"这个 k6 脚本在仓库里的什么位置"，与证据包被写到哪儿无关：
// 运维把证据归档到外部盘（--out /mnt/evidence/xxx）是常规做法，此时 dir 在仓库外，
// 从 dir 找根的写法会把字段退化成压测机本地的绝对路径——第三方 clone 后无法定位，
// 而脚本明明就在仓库里、完全给得出相对路径。README 里"与运行目录位置无关"这句
// 描述的是本实现，不是从 dir 找根的那个版本。
const repoRoot = k6ScriptAbs ? repoRootOf(path.dirname(k6ScriptAbs)) : null
let k6ScriptPath = null
if (k6ScriptAbs) {
  const rel = repoRoot ? path.relative(repoRoot, k6ScriptAbs) : null
  // 脚本本身就不在仓库里（例如被单独拷到压测机上）时退化为绝对路径，
  // 而不是给一个相对但无意义的路径
  k6ScriptPath = rel && !rel.startsWith('..') && !path.isAbsolute(rel) ? rel : k6ScriptAbs
}

// 证据完整性：由 run-stress-test.sh 判定后注入（它才知道 monitor_scope，也就知道
// monitoring/* 是不是本次运行的必要产物）。这里只做记录与派生，不重复判定逻辑。
// 关键点是它与 thresholds_passed **不是一回事**：k6 退出码 0 只说明门禁通过，
// 说明不了这份包能交付。两者分开陈述，第三方才不会把"门禁过了"读成"证据齐了"。
const missingArtifacts = (process.env.MISSING_ARTIFACTS ?? '')
  .split(/\s+/)
  .map((s) => s.trim())
  .filter((s) => s.length > 0)
const evidenceComplete = missingArtifacts.length === 0
const redactionCount = Number.parseInt(process.env.REDACTED_COUNT ?? '0', 10) || 0
// 采集侧的具体原因（哪个组件一行有效数据都没有、采集器为何非 0 退出）。
// 只写 missing_artifacts 会让人以为是"文件没生成"，而真实情况多半是"文件生成了、
// 里面没有数据"——这两件事的处置方式完全不同。
const monitorIssues = (process.env.MONITOR_ISSUES ?? '')
  .split('\n')
  .map((s) => s.trim())
  .filter((s) => s.length > 0)

// 逐组件状态：直接读采集器写的 component-status.csv，但**只作为记录**，不作为判定。
// 判定（这份包能不能交付）由 run-stress-test.sh 独立复算 CSV 数据行得出并注入
// missing_artifacts；两边都算一遍是刻意的，清单不给自己发合格证。
const componentStatus = []
const statusPath = path.join(dir, 'monitoring/component-status.csv')
if (fs.existsSync(statusPath)) {
  const lines = fs.readFileSync(statusPath, 'utf8').split('\n').filter((l) => l.trim() !== '')
  const header = (lines.shift() ?? '').split(',')
  for (const line of lines) {
    // reason 列可能为空，且采集器已把原因里的逗号替换成 ';'，所以定长切分是安全的
    const cells = line.split(',')
    const row = {}
    header.forEach((key, i) => {
      row[key] = cells[i] ?? ''
    })
    componentStatus.push({
      component: row.component,
      required: row.required === '1',
      available: row.available === '1',
      usable_samples: Number.parseInt(row.usable_samples ?? '', 10) || 0,
      total_samples: Number.parseInt(row.total_samples ?? '', 10) || 0,
      reason: row.reason || undefined,
    })
  }
}

const manifest = {
  run_id: process.env.RUN_ID,
  started_at_utc: process.env.STARTED_AT,
  ended_at_utc: process.env.ENDED_AT,
  duration_seconds: Number(process.env.DURATION_SEC),
  target_base_url: process.env.BASE_URL,
  target_host: process.env.TARGET_HOST ?? null,
  profile: process.env.PROFILE,
  enable_writes: process.env.ENABLE_WRITES === '1',
  // K6_SCRIPT 缺失时不再抛 TypeError；路径已在上面解析为「相对仓库根 or 绝对路径」
  k6_script: k6ScriptPath,
  k6_version: process.env.K6_VERSION,
  monitor_interval_seconds: Number(process.env.MONITOR_INTERVAL),
  // 监控数据采自哪台机器：local-target = 本机即被压目标；local-target-asserted = 目标非回环
  // 地址、但操作者显式声明本机就是目标宿主；skipped = 本包不含监控。采集器只能采本机，
  // 这个字段是第三方判断"monitoring/*.csv 到底描述谁"的唯一依据，不能省。
  monitor_scope: process.env.MONITOR_SCOPE ?? null,
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
    : '门禁判定仅反映 k6 退出码；本次证据包不完整（见 missing_artifacts），不可作为交付证据引用',
  evidence_complete: evidenceComplete,
  missing_artifacts: missingArtifacts,
  // 逐组件的有效采样行数。文件存在 ≠ 有数据：采集器在 mysql 客户端缺失、Redis 不可达、
  // 应用进程找不到时会留下只有表头或整行空值的 CSV，"证据完整"的结论只能建立在这里的
  // usable_samples 上，交付前请按 required=true 的组件逐个确认。
  monitoring_components: componentStatus,
  monitoring_issues: monitorIssues,
  deliverable_ready: evidenceComplete,
  // 归档前被替换掉的凭证个数（k6 summary 的 setup_data 带着登录得到的 bearer token）。
  // 记下来是为了让"这份包被改过"这件事本身也是可核验的，而不是一个只有工具知道的隐式步骤。
  credential_redactions: redactionCount,
  headline_metrics: headline,
  artifacts,
  note: '原始证据包由 scripts/performance/run-stress-test.sh 生成；sha256 用于校验归档件未被替换。',
}

fs.writeFileSync(path.join(dir, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`)
console.log('write-run-manifest: manifest.json 已写入')
