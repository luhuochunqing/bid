// Input: 证据目录内的产物文件路径（脱敏模式）或一个证据目录（扫描模式）
// Output: 脱敏模式下就地替换凭证并打印 `redacted=<N>`；扫描模式用**同一组检测器**（JWT / 敏感键值 / Bearer）
//         逐文件计数，打印覆盖范围与机器可读结论（scan_status / scan_scanned / scan_skipped /
//         scan_hits / scan_unscanned_files），并按结论给退出码：0=通过、1=有文本残留、3=存在未扫描文件
// Pos: scripts/performance/ - 压测证据包归档前的凭证脱敏与残留扫描（run-stress-test.sh 在写 manifest 前两者都跑，非通过即判本包不可交付）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 为什么需要它：k6 的 --summary-export 会把 setup() 的返回值原样写进 setup_data，
// 而负载脚本的 setup() 正是在那里登录并拿到一组 bearer token。于是"原始结果文件"
// 里必然带着可直接使用的身份令牌——一份给第三方复核的证据包不该包含任何可用凭证。
// 本脚本在**归档前**就地脱敏，且只改凭证文本本身，不做 parse/重新序列化：
// 重排 JSON 会把"原始产物"变成"我们的产物"，逐字节保真是这个目录的唯一卖点。
//
// 扫描的**能力边界**：只扫文本。含 NUL 或 UTF-8 替换字符的文件（截图、压缩包等）无法按
// 文本解析，本脚本不假装覆盖它们——但也不把它们当成"干净"：只要有这类文件，扫描结论就是
// `incomplete`（exit 3），调用方必须判本包不可交付。把它们列为"未覆盖"而不阻断交付，
// 等于留了一条可交付包内含可读凭证的路径（实测：`credential\0Bearer …` 的文件曾被跳过、
// 其余文本干净时整包以"扫描通过"交付）。要让包可交付，就把二进制产物移出证据目录。


import fs from 'node:fs'
import path from 'node:path'

// JWT 三段式（header.payload.signature，base64url）。够窄，不会误伤普通文本。
const JWT_RE = /\beyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\b/g
// 只认这些键名下的字符串值。不按"看起来像密钥"做启发式，避免误删正常指标名。
const SENSITIVE_KEY_RE =
  /("(?:token|access_token|accessToken|refresh_token|refreshToken|password|passwd|secret|client_secret|authorization|api_key|apiKey)"\s*:\s*)"(?!<redacted:)[^"]*"/g
const BEARER_RE = /\b(Bearer\s+)(?!<redacted:)[A-Za-z0-9._~+/-]{16,}=*/g

function scrubText(text) {
  let count = 0
  const bump = () => { count += 1 }
  let out = text.replace(JWT_RE, () => { bump(); return '<redacted:jwt>' })
  out = out.replace(SENSITIVE_KEY_RE, (_m, head) => { bump(); return `${head}"<redacted:credential>"` })
  out = out.replace(BEARER_RE, (_m, head) => { bump(); return `${head}<redacted:credential>` })
  return { out, count }
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const abs = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(abs, files)
    else if (entry.isFile()) files.push(abs)
  }
  return files
}

const argv = process.argv.slice(2)
if (argv.length === 0) {
  console.error('scrub-secrets: 用法 scrub-secrets.mjs <file...> | --scan <dir>')
  console.error('              扫描模式退出码：0=通过 1=有文本残留 3=存在未扫描（二进制）文件 2=用法错误')
  process.exit(2)
}

if (argv[0] === '--scan') {
  const dir = argv[1]
  if (!dir || !fs.existsSync(dir)) {
    console.error(`scrub-secrets: --scan 目标不存在：${dir ?? '(未提供)'}`)
    process.exit(2)
  }
  // 扫描与脱敏必须用**同一组检测器**。上一版只数了 JWT：一份含
  // `"password":"明文"` 或 `Authorization: Bearer …` 的证据包会被判"无残留"并 exit 0，
  // 而同一份文件交给脱敏模式却会被改掉——同一个脚本里两套判据，扫描那一侧等于没有。
  const detectors = [
    ['JWT', JWT_RE],
    ['敏感键值', SENSITIVE_KEY_RE],
    ['Bearer', BEARER_RE],
  ]
  const hits = []
  const scanned = []
  const skipped = [] // 二进制产物：本脚本不解析，必须显式声明为未覆盖，而不是默认"干净"
  for (const abs of walk(dir)) {
    const rel = path.relative(dir, abs)
    let buf
    try {
      buf = fs.readFileSync(abs)
    } catch (error) {
      console.error(`scrub-secrets: 无法读取 ${rel}: ${error.message}`)
      console.error('scrub-secrets: 读不到就无法证明它不含凭证，按扫描失败处理')
      process.exit(1)
    }
    const text = buf.toString('utf8')
    // 含 NUL 或出现替换字符 = 不是文本（例如误放进证据包的截图），单独归类
    if (buf.includes(0) || text.includes('\uFFFD')) {
      skipped.push(rel)
      continue
    }
    scanned.push(rel)
    for (const [label, re] of detectors) {
      // 正则带 /g，match 不会像 test 那样保留 lastIndex 状态，逐文件重新计数
      const n = (text.match(re) || []).length
      if (n > 0) hits.push(`${rel}: ${n} 处 ${label} 形态残留`)
    }
  }
  console.log(`scrub-secrets: 已扫描 ${scanned.length} 个文本产物；检测 JWT / 敏感键值 / Bearer 三类文本形态`)
  for (const line of hits) console.error(`  ${line}`)
  // 机器可读结论放最后：调用方按固定键名取值，不必去 parse 上面这些供人读的句子。
  // 未扫描文件同时**逐个列出**——只说"有 1 个没扫"的话，操作者还得自己去找是哪个。
  console.log(`scan_status=${hits.length > 0 ? 'failed' : skipped.length > 0 ? 'incomplete' : 'passed'}`)
  console.log(`scan_scanned=${scanned.length}`)
  console.log(`scan_skipped=${skipped.length}`)
  console.log(`scan_hits=${hits.length}`)
  console.log(`scan_unscanned_files=${skipped.join(',')}`)
  if (hits.length > 0) {
    console.error('scrub-secrets: 归档产物中仍存在疑似凭证残留（详见上方逐条命中）')
    process.exit(1)
  }
  if (skipped.length > 0) {
    // 这一类**不能**以 0 退出。二进制文件里可能存在可读凭证（例如误放进证据包的截图、
    // 压缩包、带 NUL 的日志片段），而本脚本无法证明它干净；"未覆盖"只能等于"不可交付"，
    // 不能等于"通过"。要让包可交付，就把这些文件移出证据目录（monitoring.log 与四份 CSV
    // 已经覆盖了监控证据本身，截图不是必需件）。
    console.error(
      `scrub-secrets: ${skipped.length} 个二进制产物无法按文本扫描，本包不宣称凭证扫描通过：${skipped.join(', ')}`,
    )
    console.error('scrub-secrets: 请把无法扫描的产物移出证据目录后重跑，或由人工确认后另行出具结论。')
    process.exit(3)
  }
  console.log('scrub-secrets: 扫描通过，未发现凭证残留，且全部产物都属可扫描的文本形态')
  process.exit(0)
}

let total = 0
let failed = 0
for (const rel of argv) {
  if (!fs.existsSync(rel)) continue // 产物缺失由完整性判定负责，不在脱敏阶段报错
  let text
  try {
    text = fs.readFileSync(rel, 'utf8')
  } catch (error) {
    console.error(`scrub-secrets: 读取失败 ${rel}: ${error}`)
    failed += 1
    continue
  }
  const { out, count } = scrubText(text)
  if (count > 0) {
    fs.writeFileSync(rel, out)
    total += count
  }
}

// 单行机器可读输出，供 run-stress-test.sh 取用并写进 manifest
console.log(`redacted=${total}`)
process.exit(failed > 0 ? 2 : 0)
