// Input: 无（纯离线：不连网络、不连数据库、不读环境变量、不向任何环境施加负载）
// Output: 逐项 PASS/FAIL 与汇总计数；全部通过 exit 0，任一失败 exit 1
// Pos: scripts/performance/ - scrub-secrets.mjs 的**检测器矩阵与 CLI 成对回归**（脱敏 / 扫描两条路径不许各有一套判据）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 为什么需要它：脱敏与扫描共用 `DETECTORS` 这件事，此前只有"同一个 import"这一个保证——
// 检测器**判断得对不对**没有任何回归。于是出现过一个真实缺口：敏感键值判据只认未转义的
// `"password":"…"`，而日志与嵌套 JSON 里更常见的是 `{\"password\":\"明文\"}`。这类文本既不被
// 脱敏、也不被扫描命中，整包扫描照报 `passed` / exit 0 —— 一份带着可恢复明文口令的证据包拿到了
// "扫描通过"。这条路径不是推理出来的，是第十三轮审查的对手探针实际跑出来的。
//
// 本文件因此钉四类东西，缺一类都不算覆盖：
//   1. **正向**：四类检测器的每一种形态都必须命中，含反斜杠转义（1 层 / 2 层）与"JSON 当字符串
//      记进日志"的嵌套形态；
//   1b. **值内部形态**（第十五轮审查打穿的缺口）：值里带 JSON 转义（`\"` `\\` `\n`、以反斜杠结尾）
//      时，替换区间必须**逐字节**等于"把值换成占位符"的那份文本。既有的两头断言（"命中 ≥ 1"、
//      "某个明文子串消失"）都挡不住旧判据：`[^"]*` 在值内第一个转义引号处截断，仍然命中、仍然
//      让那个子串消失，却把值漏掉一半、把结构改坏。只有"逐字节等于期望"能判死它。
//   1c. **键名的等价编码**（第十六轮打穿的缺口）：合法 JSON 可以把 ASCII 写成 `\uXXXX`，
//      `{"password":"明文"}` 解析出来的键**就是** `password`。只在文本层枚举键名的字面拼写，
//      这类文本既不被脱敏、也不被扫描命中——明文完整留在包里而扫描照报 `passed`（实测 exit 1）。
//      这一节先 `JSON.parse` 证明样本语义上确实是敏感键（否则断言本身就是"为错误理由而绿"），
//      再断言整段替换、逐字节保真、输出仍是合法 JSON、幂等；
//   2. **负向**：普通指标文本、无 userinfo 的 URL、邮件地址、非敏感键、非字符串值、已脱敏占位符
//      都**不许**被改动——一个"什么都改"的检测器能通过全部正向断言，却会把证据包改烂；
//   3. **成对**：走真实 CLI 的两条路径（`--scan` 与脱敏），断言同一个样本在扫描侧是
//      `failed`/exit 1、在脱敏侧被替换成 `<redacted:credential>` 且**再扫一次变成 passed**；
//      值内带转义引号与键名带等价编码的那两份还要断言脱敏后仍是合法 JSON、解析出来的正是占位符。
//      只断言纯函数等于没测文件遍历、README 里承诺的退出码与实际输出的一致性。
//
// 与 exit-code-self-test.sh 的分工：那个文件管 runner 的**退出码分层**（0 / k6 码 / 1 / 12），
// 本文件管**检测器本身**。两者都用替身或临时目录，都不碰真实环境。

import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { execFileSync } from 'node:child_process'

import { scrubText } from './scrub-secrets.mjs'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const CLI = path.join(HERE, 'scrub-secrets.mjs')

let N = 0
let FAIL = 0
function check(name, expected, actual) {
  N += 1
  if (`${expected}` === `${actual}`) {
    console.log(`  [PASS] ${name}`)
  } else {
    FAIL += 1
    console.log(`  [FAIL] ${name}：期望 ${expected}，实际 ${actual}`)
  }
}

// 构造**转义形态**的 JSON：把每个 `"` 与 `\` 前面都加上 depth 个反斜杠，
// 也就是 `{"password":"x"}` → `{\"password\":\"x\"}`（depth=1）。
// 服务端把请求体当字符串写进日志、或某个 JSON 字段的值本身又是一段 JSON 时，落盘的就是这个形态。
const escJson = (obj, depth = 1) =>
  JSON.stringify(obj).replace(/["\\]/g, (c) => '\\'.repeat(depth) + c)

const SECRET = 'ThisIsARealPassword123!'

console.log('== 1. 正向矩阵：四类检测器的各种形态都必须命中 ==')
// 每一例自带"明文字面量"清单：只断言 hits≥1 是不够的——检测器可能命中在别处、而这一例的
// 明文原样留下（早先这里用一组全局关键字去查，转义后的 apiKey 样本恰好绕过了那组关键字，
// 于是在"判据已回退"的变异体上仍然 PASS：一条为错误理由而绿的断言）。
const positives = [
  ['JWT 三段式', '{"t":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.abcdefghij"}', ['eyJhbGciOiJIUzI1NiJ9']],
  ['明文敏感键 password', `{"password":"${SECRET}"}`, [SECRET]],
  ['明文敏感键 token', '{"token":"tok-1234567890"}', ['tok-1234567890']],
  ['明文敏感键 apiKey', '{"apiKey":"k-1234567890"}', ['k-1234567890']],
  ['明文敏感键 client_secret', '{"client_secret":"s3cr3t-value"}', ['s3cr3t-value']],
  ['Bearer 头', 'Authorization: Bearer abcdefghijklmnopqrstuvwx', ['abcdefghijklmnopqrstuvwx']],
  ['URL userinfo', 'http://apiuser:apipass@172.16.38.78:8080/api/tenders', ['apipass']],
  ['转义 JSON · password（1 层）', escJson({ password: SECRET }), [SECRET]],
  ['转义 JSON · token（1 层）', escJson({ token: 'tok-escaped-1' }), ['tok-escaped-1']],
  ['转义 JSON · apiKey（1 层）', escJson({ apiKey: 'k-escaped-1' }), ['k-escaped-1']],
  ['转义 JSON · password（2 层）', escJson({ password: SECRET }, 2), [SECRET]],
  ['嵌套：JSON 当字符串记进日志', `msg={"body":"${escJson({ password: SECRET }).replace(/"/g, '\\"')}"}`, [SECRET]],
  ['转义 JSON 里嵌 Bearer', escJson({ note: 'Authorization: Bearer abcdefghijklmnopqrstuvwx' }), ['abcdefghijklmnopqrstuvwx']],
  ['转义 JSON 里嵌 userinfo', escJson({ url: 'http://apiuser:apipass@172.16.38.78:8080/' }), ['apipass']],
]
for (const [name, text, secrets] of positives) {
  const { out, count } = scrubText(text)
  check(`${name} · 命中`, 'yes', count >= 1 ? 'yes' : 'no')
  // "命中"还不够：这一例的明文必须真的从输出里消失，且第二次脱敏不再命中
  // （否则占位符自己会被反复改写，`redacted=<N>` 就不是"替换了几处凭证"）。
  const left = secrets.filter((s) => out.includes(s))
  check(`${name} · 明文已被替换`, '无残留', left.length === 0 ? '无残留' : `残留 ${left.join(',')}`)
  check(`${name} · 幂等（再脱敏 0 命中）`, 0, scrubText(out).count)
}

console.log('== 1b. 值**内部**带 JSON 转义的敏感键值：必须整段替换，且不改坏结构 ==')
// 这一节是第十五轮审查用反例打穿的缺口：旧的 `[^"]*` 会在值内第一个转义引号处当成收尾，
// 只替换前缀、留下后半段明文、并把 JSON 改坏；而残留的后半段已失去敏感键上下文，
// 四类检测器一个都不再命中——脱敏 exit 0，紧接着的扫描也报 passed。
// 断言里"值与期望**逐字节**相同"比"明文消失"强得多：旧判据在截断后仍能给出"命中 ≥ 1"
// 与"某个子串消失"，而"逐个字节等于把值换成占位符的那份文本"它给不出来。
//
// 文本一律用 JSON.stringify 构造（逐层 stringify = 真实日志与嵌套 JSON 的层数关系），
// 不手写反斜杠——手写层数极易出错，而"测试自己写错了"会伪装成修复通过。
const PLACEHOLDER = '<redacted:credential>'
const nestJson = (text, levels) => {
  let s = text
  for (let i = 0; i < levels; i += 1) s = JSON.stringify({ msg: s })
  return s
}
const innerValues = [
  ['值内引号', 'abc"def-SECRET'],
  ['值内反斜杠', 'abc\\def-SECRET'],
  ['值内换行转义', 'abc\ndef-SECRET'],
  ['值内反斜杠+引号', 'a\\"b-SECRET'],
  ['值以反斜杠结尾', 'abc\\'],
]
for (const [name, value] of innerValues) {
  for (const levels of [0, 1, 2, 3]) {
    const text = nestJson(JSON.stringify({ password: value }), levels)
    const expect = nestJson(JSON.stringify({ password: PLACEHOLDER }), levels)
    const { out, count } = scrubText(text)
    check(`${name} · 嵌套 ${levels} 层 · 命中`, 'yes', count >= 1 ? 'yes' : 'no')
    check(`${name} · 嵌套 ${levels} 层 · 明文已整段替换`, '无残留',
      out.includes('-SECRET') ? `残留（${out}）` : '无残留')
    check(`${name} · 嵌套 ${levels} 层 · 输出恰好是"值换成占位符"那份文本`, 'yes', out === expect ? 'yes' : 'no')
    check(`${name} · 嵌套 ${levels} 层 · 幂等`, 0, scrubText(out).count)
  }
}

console.log('== 1c. 键名用等价编码写（`\\uXXXX`）：第十六轮打穿的缺口 ==')
// 合法 JSON 允许把 ASCII 写成 `\uXXXX`，所以 `{"password":"明文"}` 经 JSON.parse 后的键**就是**
// `password`。生产判据只在文本层枚举键名的字面拼写时，这类文本既不被脱敏、也不被扫描命中——
// 明文完整留在包里，紧接着的 `--scan` 仍报 `passed`/exit 0（第十六轮审查者的反例，修复前实测 exit 1）。
// 这类样本**不能**由 JSON.stringify 产生（它不会把 ASCII 键写成转义），只能手写，所以它与 1b 的
// 生成器是两套：一套管值与层数，一套管键的等价编码。
const escKeysDoc = '{"pass\\u0077ord":"K1-secret","to\\u006ben":"K2-secret","client_\\u0073ecret":"K3-secret"}'
const escKeysRedacted =
  '{"pass\\u0077ord":"<redacted:credential>","to\\u006ben":"<redacted:credential>","client_\\u0073ecret":"<redacted:credential>"}'
for (const levels of [0, 1, 2, 3]) {
  const text = nestJson(escKeysDoc, levels)
  // 先证明样本在**语义上**确实是敏感键：手写错了（例如把 `w` 写成别的码位）而判据照绿，
  // 就是这一节最该避免的"为错误理由而绿"。
  let inner = JSON.parse(text)
  for (let i = 0; i < levels; i += 1) inner = JSON.parse(inner.msg)
  check(`等价编码键 · 嵌套 ${levels} 层 · 样本语义键确为敏感键`, 'password,token,client_secret', Object.keys(inner).join(','))
  const { out, count } = scrubText(text)
  check(`等价编码键 · 嵌套 ${levels} 层 · 三处都命中`, 3, count)
  check(`等价编码键 · 嵌套 ${levels} 层 · 明文已整段替换`, '无残留',
    /K[123]-secret/.test(out) ? `残留（${out}）` : '无残留')
  check(`等价编码键 · 嵌套 ${levels} 层 · 输出恰好是"值换成占位符"那份文本`, 'yes',
    out === nestJson(escKeysRedacted, levels) ? 'yes' : 'no')
  check(`等价编码键 · 嵌套 ${levels} 层 · 幂等`, 0, scrubText(out).count)
}
// 大小写：策略**明说键名不敏感**（见 scrub-secrets.mjs 的 SENSITIVE_KEYS 注释）——漏掉一个真凭证的
// 代价远大于多脱敏一行日志，所以 `Password` 也必须命中，这条断言把该策略钉住。
const caseHits = scrubText('{"Password":"Case-secret"}')
check('等价编码键 · 大小写不敏感（Password）命中且明文消失', 'yes',
  caseHits.count === 1 && !caseHits.out.includes('Case-secret') ? 'yes' : 'no')

console.log('== 2. 负向矩阵：不许动的文本一个字节都不许动 ==')
const negatives = [
  ['普通指标文本', 'http_req_duration.........: avg=25.1ms  p(95)=120ms'],
  ['无 userinfo 的 URL', 'https://example.com/path?x=1&y=2'],
  ['邮件地址', 'contact: hello@example.com'],
  ['非敏感键', '{"duration":"1234ms","status":"passed"}'],
  ['敏感键名但值是 null', '{"password":null}'],
  ['敏感键名但值是数字', '{"password":123456}'],
  ['形近键名 passwordHint', '{"passwordHint":"请输入密码"}'],
  ['敏感词出现在值里', '{"note":"the password field is optional"}'],
  ['已脱敏占位符', '{"password":"<redacted:credential>"}'],
  ['已脱敏占位符（转义形态）', escJson({ password: '<redacted:credential>' })],
]
for (const [name, text] of negatives) {
  const { out, count } = scrubText(text)
  check(`${name} · 0 命中`, 0, count)
  check(`${name} · 原文未变`, 'yes', out === text ? 'yes' : 'no')
}

console.log('== 3. 成对回归：真实 CLI 的扫描侧与脱敏侧必须给出同一结论 ==')
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'scrub-self-test-'))
// execFileSync 在非 0 退出时抛异常，退出码在 error.status 上（与 runner 侧读法一致）。
const run = (args) => {
  try {
    return { rc: 0, out: execFileSync(process.execPath, [CLI, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }) }
  } catch (e) {
    return { rc: e.status ?? -1, out: `${e.stdout ?? ''}${e.stderr ?? ''}` }
  }
}
try {
  const dirty = path.join(tmp, 'dirty')
  const clean = path.join(tmp, 'clean')
  fs.mkdirSync(dirty, { recursive: true })
  fs.mkdirSync(clean, { recursive: true })
  fs.writeFileSync(path.join(dirty, 'escaped.log'), `${escJson({ password: SECRET })}\n`)
  fs.writeFileSync(path.join(dirty, 'nested.json'), `${JSON.stringify({ msg: escJson({ password: SECRET }) })}\n`)
  // 值**内部**带转义双引号的那一份。第十五轮的反例正是走这条路径漏过去的：旧判据只替换到
  // 值内第一个转义引号，文件留下 `…credential>"def-SECRET"}`，而这类残留后缀已无敏感键上下文，
  // 再扫一次照样 passed。这里断言的不只是"明文没了"，还有**结构没被改坏**（能 JSON.parse、
  // 且解析出来的正是占位符）——只查明文会让"整行截断"这种修复方式也过关。
  const quotedPath = path.join(dirty, 'quoted.json')
  fs.writeFileSync(quotedPath, `${JSON.stringify({ password: `abc"def-${SECRET}` })}\n`)
  // 键名用等价编码写的那一份（手写：JSON.stringify 不会把 ASCII 键写成 `\uXXXX`）。第 16 轮的反例
  // 走的就是这条路径：脱敏 `redacted=0`、`--scan` 报 `passed`，而明文完整躺在文件里。
  const unicodeKeyPath = path.join(dirty, 'unicode-key.json')
  fs.writeFileSync(unicodeKeyPath, '{"pass\\u0077ord":"R16-UNICODE-KEY-secret"}\n')
  fs.writeFileSync(path.join(clean, 'metrics.csv'), 'http_req_duration,p95\n25.1,120\n')

  const before = run(['--scan', dirty])
  check('扫描 · 转义明文被认出（exit 1）', 1, before.rc)
  check('扫描 · 结论是 failed', 'yes', before.out.includes('scan_status=failed') ? 'yes' : 'no')
  check('扫描 · 四个样本都命中', 'yes', before.out.includes('scan_hits=4') ? 'yes' : 'no')

  // 干净的目录必须给出 passed：否则"扫描一律失败"也能骗过上面三条。
  const okScan = run(['--scan', clean])
  check('扫描 · 干净目录 passed（证明不是一律失败）', 0, okScan.rc)
  check('扫描 · 干净目录 0 命中', 'yes', okScan.out.includes('scan_hits=0') ? 'yes' : 'no')

  // 脱敏侧：逐文件调用，断言机器行恰好一行且是合法数字。
  const red1 = run([path.join(dirty, 'escaped.log')])
  check('脱敏 · escaped.log 退出 0', 0, red1.rc)
  check('脱敏 · 给出恰好一行 redacted=1', 1, (red1.out.match(/^redacted=\d+$/gm) ?? []).length)
  check('脱敏 · 替换处数为 1', 'yes', red1.out.includes('redacted=1') ? 'yes' : 'no')
  const redText = fs.readFileSync(path.join(dirty, 'escaped.log'), 'utf8')
  check('脱敏 · 文件里已无明文', 'no', redText.includes(SECRET) ? 'yes' : 'no')
  check('脱敏 · 留下的是转义占位符', 'yes', redText.includes(escJson({ password: '<redacted:credential>' })) ? 'yes' : 'no')

  run([path.join(dirty, 'nested.json')])
  // 值内带转义双引号的那一份：走真实 CLI，断言"明文消失 + 结构仍有效 + 再扫描通过"三条一起来。
  // 只断言前者的话，把整行截断也能满足；只断言后两条的话，一个"什么都不改"的实现也能满足。
  const red2 = run([quotedPath])
  check('脱敏 · quoted.json 退出 0', 0, red2.rc)
  check('脱敏 · quoted.json 替换处数为 1', 'yes', red2.out.includes('redacted=1') ? 'yes' : 'no')
  const quotedText = fs.readFileSync(quotedPath, 'utf8')
  check('脱敏 · quoted.json 已无明文', 'no', quotedText.includes(SECRET) ? 'yes' : 'no')
  let quotedParsed = null
  try {
    quotedParsed = JSON.parse(quotedText)
  } catch {
    quotedParsed = null
  }
  check('脱敏 · quoted.json 仍是合法 JSON', 'yes', quotedParsed ? 'yes' : 'no')
  check('脱敏 · quoted.json 的 password 恰好是占位符', PLACEHOLDER, quotedParsed?.password)

  // 键名等价编码的那一份：断言"明文消失 + 解析后键就是 password + 值恰好是占位符 + 再扫描通过"。
  // 只断言后者的话，一个把整行删掉的实现也能满足；只断言前者的话，一个什么也不改的实现照样过关。
  const red3 = run([unicodeKeyPath])
  check('脱敏 · unicode-key.json 退出 0', 0, red3.rc)
  check('脱敏 · unicode-key.json 替换处数为 1', 'yes', red3.out.includes('redacted=1') ? 'yes' : 'no')
  const ukText = fs.readFileSync(unicodeKeyPath, 'utf8')
  check('脱敏 · unicode-key.json 已无明文', 'no', ukText.includes('R16-UNICODE-KEY-secret') ? 'yes' : 'no')
  let ukParsed = null
  try {
    ukParsed = JSON.parse(ukText)
  } catch {
    ukParsed = null
  }
  check('脱敏 · unicode-key.json 仍是合法 JSON', 'yes', ukParsed ? 'yes' : 'no')
  check('脱敏 · unicode-key.json 解析出来的键就是 password', 'password', Object.keys(ukParsed ?? {}).join(','))
  check('脱敏 · unicode-key.json 的值恰好是占位符', PLACEHOLDER, ukParsed?.password)

  const after = run(['--scan', dirty])
  check('脱敏后再扫 · 整目录 passed', 0, after.rc)
  check('脱敏后再扫 · 0 命中', 'yes', after.out.includes('scan_hits=0') ? 'yes' : 'no')
} finally {
  fs.rmSync(tmp, { recursive: true, force: true })
}

console.log()
if (FAIL === 0) {
  console.log(`scrub-secrets-self-test: 全部通过（${N}/${N}）`)
  process.exit(0)
}
console.error(`scrub-secrets-self-test: 失败 ${FAIL}/${N}`)
process.exit(1)
