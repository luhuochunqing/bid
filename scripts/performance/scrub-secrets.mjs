// Input: 证据目录内的产物文件路径（脱敏模式）或一个证据目录（扫描模式）
// Output: 脱敏模式下就地替换凭证并打印 `redacted=<N>`；扫描模式用**同一组检测器**（JWT / 敏感键值 / Bearer / URL userinfo）
//         逐文件计数，打印覆盖范围与机器可读结论（scan_status / scan_scanned / scan_skipped /
//         scan_hits / scan_unscanned_files），并按结论给退出码：0=通过、1=有文本残留、3=存在未扫描文件
// Pos: scripts/performance/ - 压测证据包归档前的凭证脱敏与残留扫描（run-stress-test.sh 在写 manifest 前两者都跑，非通过即判本包不可交付）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 对外除了 CLI，还导出 scrubText / DETECTORS 给 write-run-manifest.mjs 复用：
// manifest.json 也在证据包里，也必须过同一组检测器，不能"扫描发生在它写盘之前"就免检。
//
// 为什么需要它：k6 的 --summary-export 会把 setup() 的返回值原样写进 setup_data，
// 而负载脚本的 setup() 正是在那里登录并拿到一组 bearer token。于是"原始结果文件"
// 里必然带着可直接使用的身份令牌——一份给第三方复核的证据包不该包含任何可用凭证。
// 本脚本在**归档前**就地脱敏，且只改凭证文本本身，不做 parse/重新序列化：
// 重排 JSON 会把"原始产物"变成"我们的产物"，逐字节保真是这个目录的唯一卖点。
//
// 扫描的**能力边界**：只扫普通文件的文本内容。含 NUL 或 UTF-8 替换字符的文件（截图、压缩包等）
// 无法按文本解析，本脚本不假装覆盖它们——但也不把它们当成"干净"：只要有这类文件，扫描结论就是
// `incomplete`（exit 3），调用方必须判本包不可交付。**符号链接与其它非常规文件同样计入这一类**：
// 早先 walk 只收集 `isFile()`，一条指向目录外凭证文件的软链就能让整包 `scan_scanned=0 /
// status=passed`、exit 0——链接目标里的凭证可读，而扫描器连"存在这个文件"都没报告。
// 把它们列为"未覆盖"而不阻断交付，等于留了一条可交付包内含可读凭证的路径
// （实测：`credential\0Bearer …` 的文件曾被跳过、其余文本干净时整包以"扫描通过"交付）。
// 要让包可交付，就把二进制产物与符号链接移出证据目录。


import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// JWT 三段式（header.payload.signature，base64url）。够窄，不会误伤普通文本。
const JWT_RE = /\beyJ[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\.[A-Za-z0-9_-]{4,}\b/g
// 只认这些键名下的字符串值。不按"看起来像密钥"做启发式，避免误删正常指标名。
// 键名、冒号、值的开引号这三段里，四处引号用**同一个反向引用** `\2` 钉住同一层反斜杠转义
// （`(\\*)` 是组 2，值的开引号是组 3，两处都写作 `\2"`）：`{"password":"…"}` 与
// `{\"password\":\"…\"}` 都认，不成对的写法（`\"password":`）不认。**层数不设上限**：它直接从
// 文本里读出来（组 3 的反斜杠个数就是层数），不是枚举深度、也不是写死一个上限。
// 这一形态很常见——服务端把请求体当字符串写进日志、或某个 JSON 字段的值本身又是一段 JSON。
// 判据只吃到"值的开引号"为止：值的收尾位置交给下面的状态机，因为正则做不到（见 scanValueEnd）。
const SENSITIVE_KEY_HEAD_RE =
  /((\\*)"(?:token|access_token|accessToken|refresh_token|refreshToken|password|passwd|secret|client_secret|authorization|api_key|apiKey)\2"\s*:\s*(\2"))/g

// 找出一段值文本真正的收尾位置。返回**收尾引号那个单元的起点**（不是引号本身）：替换区间恰好
// 是值文本，收尾引号连同它那一层转义原样留在原处，脱敏后的形态与输入同层；-1 = 这一层读不出
// 值边界（调用方按"不猜边界"处理，见 sensitiveValueEdits）。
//
// 为什么不能用 `[^"]*` 收尾：值里可以有 JSON 转义（`\"` `\\` `\n`）。旧判据在值内第一个转义
// 引号处截断，只替换前半段、把结构改坏，而残留的后半段已失去敏感键上下文，四个检测器一个都
// 不再命中——脱敏 exit 0，紧接着的整包扫描也报 `passed`（实测 `{"password":"abc\"def-S"}` 只
// 替换到 `abc\`）。两个真实缺口都出在这一处，所以边界必须真的读出来，而不是猜。
//
// 在 depth 层转义下，这一层的单元只有两种：解码后的一个字面反斜杠 = unit = depth+1 个反斜杠，
// 解码后的一个字面引号 = depth 个反斜杠 + `"`。于是"边解码边扫描"：把游程按 unit 拆成 k 个字面
// 反斜杠，再看后面跟的是不是引号。是不是**收尾**不能靠"游程恰好等于 depth"来判（值以一个字面
// 反斜杠结尾时，游程会把收尾引号的前缀一起并进来），判据是标准 JSON 规则：
// **引号前面有奇数个反斜杠才是被转义的**（此处 k 就是这个个数）。
//
// 收尾引号**之后**紧跟的那个字符，还得是 JSON 里值后面合法的续接（`,` `}` `]` 或空白）：
// `{"password":"abc\\"def-S"}` 这种缺一层转义的畸形文本，按 JSON 规则值在偶数个反斜杠后的引号
// 处确实结束了，可紧接着的 `def-S` 显然还是值的尾巴，只替换前半段等于把明文漏一半。
// 两条都成立才算读到边界；否则按读不出处理（截到行尾）——宁可毁掉一行的可读性，也不留半截凭证。
function followerReadable(text, at) {
  if (at >= text.length) return true // 值收尾就在文本末尾
  const ch = text[at]
  return ch === '\\' || ',}] \t\r\n'.includes(ch) // 反斜杠开头 = 转义序列/更内一层，不据此判违规
}

function scanValueEnd(text, from, depth) {
  const unit = depth + 1 // 解码后一个反斜杠在该层占的字符数
  const prefix = depth // 解码后一个引号前面的反斜杠数
  let i = from
  while (i < text.length) {
    let run = 0
    while (text[i + run] === '\\') run += 1
    const ch = text[i + run]
    if (ch === undefined) return -1 // 值没闭合
    const rem = run % unit
    if (ch === '"') {
      if (rem !== prefix) return -1 // 引号前面的反斜杠数不是本层的形态
      const k = (run - rem) / unit // 这个引号前面有几个解码后的反斜杠
      if (k % 2 !== 0) {
        i += run + 1 // 奇数 → 值内的转义引号，接着往下读
        continue
      }
      const quoteStart = i + k * unit // 偶数 → 收尾引号
      return followerReadable(text, quoteStart + unit) ? quoteStart : -1
    }
    if (rem !== 0) return -1 // 后面不是引号，游程就必须正好是若干个字面反斜杠
    i += run + 1
  }
  return -1
}

// 收集"敏感键值"里值文本的区间，按出现顺序返回（因此互不重叠）。扫描侧与脱敏侧共用这一个函数：
// 数的就是会被替换掉的那些值，"扫描没命中"与"脱敏不会动"在这里是同一件事。
function sensitiveValueEdits(text) {
  const edits = []
  for (const m of text.matchAll(SENSITIVE_KEY_HEAD_RE)) {
    const valueStart = m.index + m[0].length
    if (text.startsWith('<redacted:', valueStart)) continue // 已经脱敏过：幂等
    const depth = (m[3] ?? '"').length - 1
    const close = scanValueEnd(text, valueStart, depth)
    // 读不出边界时**不猜边界**：整行截掉（只截到本行，不跨行）。这是刻意的取舍——宁可毁掉这一行
    // 的后续内容（它本来就是日志，可读性有代价），也不能把"读不出边界的文本"当成"没有凭证"。
    // 泄出去的口令没有第二次机会，而这一行被截掉是看得见的（`redacted=<N>` 会 +1）。
    const nl = text.indexOf('\n', valueStart)
    const end = close >= 0 ? close : nl < 0 ? text.length : nl
    if (end > valueStart) edits.push([valueStart, end]) // 空值（`""`）没什么可替换
  }
  return edits
}

// 按区间就地替换。区间是升序的，从后往前改，前面的下标才不会失效。
function redactSensitiveKeys(text) {
  const edits = sensitiveValueEdits(text)
  let out = text
  for (let k = edits.length - 1; k >= 0; k -= 1) {
    const [start, end] = edits[k]
    out = out.slice(0, start) + '<redacted:credential>' + out.slice(end)
  }
  return { out, count: edits.length }
}

const BEARER_RE = /\b(Bearer\s+)(?!<redacted:)[A-Za-z0-9._~+/-]{16,}=*/g
// URL 里的 userinfo：`http://user:口令@host` / `https://令牌@host`。
// 这一类是漏网过的真实路径：`--base-url` 带上 userinfo 时，它会逐字写进 manifest.json 的
// target_base_url，而**前三类检测器一个都不匹配它**（键名不是敏感键、没有 Bearer 前缀、
// 不是 JWT），于是整包扫描照样报 passed，一封自称"扫描通过"的证据包里躺着一份可用口令。
// 只匹配 `://` 之后到第一个 `/?#` 之前的 `@`，所以普通 URL（https://host/path）与
// 邮件地址（a@b.com，前面没有 ://）都不会误伤。
const URL_USERINFO_RE = /([a-zA-Z][a-zA-Z0-9+.-]*:\/\/)(?!<redacted:)([^/?#@\s]+)@/g

// 检测器清单**只有这一份**。扫描模式与脱敏模式都从这里取（两套判据时扫描那一侧等于没有），
// write-run-manifest.mjs 也 import 同一个 scrubText 对清单自身做一次脱敏——manifest.json
// 同样躺在证据包里，凭什么它就不用过这一关。
// 计数一律走**同一个函数**（`<label> → 文本 → 命中数`）：正则那三类就是"匹配几次"，
// 敏感键值那类数的是"会被替换掉的值"，与脱敏共用 sensitiveValueEdits。
// 只给正则不给计数函数的话，扫描侧只能自己 `text.match(re)`——而敏感键值的匹配**必须**带状态机，
// 那一侧就会各自退化一次（"扫描说干净、脱敏却改了文件"正是这么发生的）。
const countMatches = (re) => (text) => (text.match(re) || []).length
export const DETECTORS = [
  ['JWT', countMatches(JWT_RE)],
  ['敏感键值', (text) => sensitiveValueEdits(text).length],
  ['Bearer', countMatches(BEARER_RE)],
  ['URL userinfo', countMatches(URL_USERINFO_RE)],
]

export function scrubText(text) {
  let count = 0
  let out = text.replace(JWT_RE, () => { count += 1; return '<redacted:jwt>' })
  const keys = redactSensitiveKeys(out)
  out = keys.out
  count += keys.count
  out = out.replace(BEARER_RE, (_m, head) => { count += 1; return `${head}<redacted:credential>` })
  out = out.replace(URL_USERINFO_RE, (_m, head) => { count += 1; return `${head}<redacted:userinfo>@` })
  return { out, count }
}

// 枚举待扫描文件。符号链接与其它非常规文件（FIFO/设备/套接字）**不算普通文件**：
// 早先只收集 isFile()，一个目录里放一条指向目录外凭证文件的软链就能让整包
// `scan_scanned=0 / scan_status=passed`（实测 exit 0）——链接目标里的凭证可读，
// 而扫描器连"有这个文件"都没报告。所以它们单独归一类，交由调用方判为未覆盖。
function walk(dir, files = [], links = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const abs = path.join(dir, entry.name)
    if (entry.isSymbolicLink()) links.push(abs)
    else if (entry.isDirectory()) walk(abs, files, links)
    else if (entry.isFile()) files.push(abs)
    // dirent 报不出来的类型（例如某些挂载点上的条目）同样按"无法证明干净"处理
    else links.push(abs)
  }
  return { files, links }
}

// CLI 只在**被直接执行**时运行：write-run-manifest.mjs 要 import 上面的 scrubText
// 对清单自身的序列化结果做同一组检测，若这里继续无条件跑 CLI，import 就会顺带把
// 参数解析和 process.exit 一起执行掉（它没有参数，于是 import 即 exit 2）。
function runCli() {
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
  const detectors = DETECTORS
  const hits = []
  const scanned = []
  // 无法按文本扫描的产物：二进制/非 UTF-8 文本（截图、压缩包、带 NUL 的日志片段）
  // 与符号链接/非常规文件（本脚本不跟链接、也不解析 FIFO）。它们必须显式声明为未覆盖，
  // 而不是默认"干净"——"未覆盖"只能等于"不可交付"。
  const skipped = []
  const { files, links } = walk(dir)
  for (const abs of links) skipped.push(path.relative(dir, abs))
  for (const abs of files) {
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
    for (const [label, count] of detectors) {
      const n = count(text)
      if (n > 0) hits.push(`${rel}: ${n} 处 ${label} 形态残留`)
    }
  }
  console.log(`scrub-secrets: 已扫描 ${scanned.length} 个文本产物；检测 JWT / 敏感键值 / Bearer / URL userinfo 四类文本形态`)
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
    // 这一类**不能**以 0 退出。二进制产物里可能存在可读凭证（例如误放进证据包的截图、
    // 压缩包、带 NUL 的日志片段），符号链接更是可以指向目录外的任意凭证文件——两者本脚本
    // 都无法证明干净；"未覆盖"只能等于"不可交付"，不能等于"通过"。要让包可交付，
    // 就把这些文件移出证据目录（monitoring.log 与四份 CSV 已经覆盖了监控证据本身，
    // 截图不是必需件）。
    console.error(
      `scrub-secrets: ${skipped.length} 个产物无法按文本扫描（二进制 / 符号链接 / 非常规文件），本包不宣称凭证扫描通过：${skipped.join(', ')}`,
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
}

// 直接执行才跑 CLI；被 import（write-run-manifest.mjs 复用 scrubText）时只导出函数。
// 判据是**两边都取 realpath 之后再比**，而不是 `pathToFileURL(argv[1]).href === import.meta.url`：
// 后者只在"调用方给出的路径恰好等于 node 解析出的真实路径"时成立，一处软链接就够让它不成立——
// macOS 上 `/tmp` 与 `/var` 都是指向 `/private/...` 的软链，仓库目录一旦经由它们被引用
// （临时副本、CI 缓存目录、`mktemp -d` 造出来的沙箱），两边 URL 不等，runCli() 就**静默不跑**：
// 退出码 0、一行输出都没有。那正是本工具链最忌讳的形态——看起来成功、其实什么都没做，
// 而扫描模式下"没有任何命中"与"根本没扫描"长得一模一样。
function isDirectRun() {
  if (!process.argv[1]) return false
  try {
    return fs.realpathSync(process.argv[1]) === fs.realpathSync(fileURLToPath(import.meta.url))
  } catch {
    return false
  }
}

if (isDirectRun()) {
  runCli()
}
