// Input: 证据目录内的产物文件路径（脱敏模式）或一个证据目录（扫描模式）
// Output: 脱敏模式下就地替换凭证并打印 `redacted=<N>`；扫描模式用**同一组检测器**（JWT / 敏感键值 / Bearer）
//         逐文件计数并打印覆盖范围，发现文本残留或读不到的文件时以 1 退出（二进制产物记为未覆盖，需人工确认）
// Pos: scripts/performance/ - 压测证据包归档前的凭证脱敏与残留扫描（run-stress-test.sh 在写 manifest 前两者都跑，残留即判本包不可交付）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 为什么需要它：k6 的 --summary-export 会把 setup() 的返回值原样写进 setup_data，
// 而负载脚本的 setup() 正是在那里登录并拿到一组 bearer token。于是"原始结果文件"
// 里必然带着可直接使用的身份令牌——一份给第三方复核的证据包不该包含任何可用凭证。
// 本脚本在**归档前**就地脱敏，且只改凭证文本本身，不做 parse/重新序列化：
// 重排 JSON 会把"原始产物"变成"我们的产物"，逐字节保真是这个目录的唯一卖点。

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
  if (skipped.length > 0) {
    console.log(`scrub-secrets: ${skipped.length} 个二进制产物未扫描（本脚本不解析二进制）：${skipped.join(', ')}`)
    console.log('scrub-secrets: 若这些产物是截图等人工材料，请单独确认其中不含凭证')
  }
  if (hits.length > 0) {
    console.error('scrub-secrets: 归档产物中仍存在疑似凭证残留：')
    for (const line of hits) console.error(`  ${line}`)
    process.exit(1)
  }
  console.log('scrub-secrets: 扫描通过，未发现凭证残留')
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
