// Input: 证据目录内的产物文件路径（脱敏模式）或一个目录（扫描模式）
// Output: 脱敏模式下就地替换凭证并打印 `redacted=<N>`；扫描模式只报告并以 1 退出（发现残留凭证时）
// Pos: scripts/performance/ - 压测证据包归档前的凭证脱敏与残留扫描
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
  const hits = []
  for (const abs of walk(dir)) {
    let text
    try {
      text = fs.readFileSync(abs, 'utf8')
    } catch {
      continue // 二进制或不可读产物不参与文本扫描
    }
    const n = (text.match(JWT_RE) || []).length
    if (n > 0) hits.push(`${path.relative(dir, abs)}: ${n} 处 JWT 形态字符串`)
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
