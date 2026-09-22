// Input: 证据目录（k6-summary.json / k6-console.log / k6-exit-code）与注入的 K6_EXIT / K6_SCRIPT
// Output: 头部指标、声明的门禁、被击穿的门禁、退出码（含与文件不一致的标记）、k6 脚本的仓库内相对路径
// Pos: scripts/performance/ - 清单生成器的**k6 侧产物解释层**：把 k6 的三份产物读成可写进清单的字段
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 为什么单独一层：这里的每一处都踩过"静默给出错误信息"的坑，且都关乎第三方对本包门禁结论的
// 采信方式——退出码语义（99 既可能是击穿也可能是 abortOnFail 提前中止）、日志措辞漂移
// （have been / were crossed）、summary 字段格式漂移（平铺 vs values）、以及 k6 脚本路径
// 在 --out 指向仓库外时的退化。它们和"装配清单对象"混在一个文件里时，改一处看不清影响面。

import fs from 'node:fs'
import path from 'node:path'

// 兼容两种 summary 结构：旧格式聚合值平铺在 metric 上，新格式包在 values 里。
// 头部指标全部走这一个小函数，避免每加一个指标就各写一次兼容分支。
function pickValues(metrics, name) {
  const metric = metrics[name]
  if (!metric) return null
  return metric.values ?? metric
}

// 头部指标 + 力压脚本声明的门禁项。summary 解析失败时返回 parse_error，
// 而不是抛出去让整份清单写不出来——清单的价值有一部分正在于记录"summary 坏了"。
export function readK6Summary(dir) {
  const summaryPath = path.join(dir, 'k6-summary.json')
  if (!fs.existsSync(summaryPath)) return { headline: null, declaredThresholds: [] }
  try {
    const summary = JSON.parse(fs.readFileSync(summaryPath, 'utf8'))
    const metrics = summary.metrics || {}
    const declaredThresholds = []
    for (const [name, metric] of Object.entries(metrics)) {
      if (metric && metric.thresholds) {
        for (const expr of Object.keys(metric.thresholds)) {
          declaredThresholds.push({ metric: name, threshold: expr })
        }
      }
    }
    const v = (name, ...fields) => {
      const values = pickValues(metrics, name)
      if (!values) return null
      for (const field of fields) {
        if (values[field] !== undefined && values[field] !== null) return values[field]
      }
      return null
    }
    const headline = {
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
    return { headline, declaredThresholds }
  } catch (error) {
    return { headline: { parse_error: String(error) }, declaredThresholds: [] }
  }
}

// 退出码以 run-stress-test.sh 注入的 K6_EXIT 为准；缺失或非数字时回退读同目录的
// k6-exit-code 文件——该文件此前只被哈希、从不被读取，导致环境变量一缺清单就写 null。
// 两者不一致时留下显式标记，"退出码 0 但门禁全挂"这类矛盾不能无人发现。
export function resolveExitCode(dir, envExit) {
  const file = path.join(dir, 'k6-exit-code')
  const fromEnv = Number.parseInt(envExit ?? '', 10)
  const fromFile = fs.existsSync(file) ? Number.parseInt(fs.readFileSync(file, 'utf8').trim(), 10) : Number.NaN
  const exitCode = Number.isNaN(fromEnv) ? fromFile : fromEnv
  const mismatch =
    !Number.isNaN(fromEnv) && !Number.isNaN(fromFile) && fromEnv !== fromFile
      ? `K6_EXIT=${fromEnv} 与 k6-exit-code 文件中的 ${fromFile} 不一致`
      : undefined
  return { exitCode, mismatch, fromEnv, fromFile }
}

// 被击穿的门禁项，从控制台日志抓取。两种措辞都必须认，否则恰好最严重的场景会落到空数组：
//   门禁被击穿但测试跑完     → "thresholds on metrics 'x' have been crossed"
//   命中 abortOnFail 提前中止 → "thresholds on metrics 'x' were crossed; at least one has
//                              abortOnFail enabled, stopping test prematurely"
// 判定为未通过却一个指标名都没抓到 → 日志措辞又变了。显式告警，而不是给读者一个空数组。
export function extractCrossedThresholds(dir, thresholdsPassed) {
  const consolePath = path.join(dir, 'k6-console.log')
  let crossed = []
  if (fs.existsSync(consolePath)) {
    const log = fs.readFileSync(consolePath, 'utf8')
    const matches = log.match(/thresholds on metrics '([^']+)' (?:have been|were) crossed/g) || []
    crossed = matches.flatMap((line) => {
      const inner = line.match(/'([^']+)'/)
      return inner ? inner[1].split(/,\s*/) : []
    })
    crossed = [...new Set(crossed)]
  }
  const warning =
    thresholdsPassed === false && crossed.length === 0
      ? 'thresholds_passed=false 但未能从 k6-console.log 解析出被击穿指标名，请人工核对原始日志'
      : undefined
  return { crossed, warning }
}

// k6_script 记成「相对仓库根」的路径，第三方 clone 后可直接定位。
// 仓库根从 **k6 脚本自身所在目录** 向上找，而不是从证据输出目录（dir）向上找：
// 该字段回答的是"这个 k6 脚本在仓库里的什么位置"，与证据包被写到哪儿无关。
// 运维把证据归档到外部盘（--out /mnt/evidence/xxx）是常规做法，此时 dir 在仓库外，
// 从 dir 找根的写法会把字段退化成压测机本地的绝对路径——第三方 clone 后无法定位，
// 而脚本明明就在仓库里、完全给得出相对路径。
// 脚本本身就不在仓库里（例如被单独拷到压测机上）时退化为绝对路径，
// 而不是给一个相对但无意义的路径（如 ../Users/...）。
export function resolveK6ScriptPath(k6ScriptEnv) {
  if (!k6ScriptEnv) return null
  const abs = path.resolve(k6ScriptEnv)
  let cur = path.dirname(abs)
  let root = null
  for (;;) {
    // worktree 下 .git 是文件而非目录，existsSync 对两者都成立
    if (fs.existsSync(path.join(cur, '.git'))) {
      root = cur
      break
    }
    const parent = path.dirname(cur)
    if (parent === cur) break
    cur = parent
  }
  if (!root) return abs
  const rel = path.relative(root, abs)
  return !rel.startsWith('..') && !path.isAbsolute(rel) ? rel : abs
}
