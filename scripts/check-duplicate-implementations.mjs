#!/usr/bin/env node
// Input: git commit range (--base/--head, 默认 origin/main...HEAD)、src/ 与 backend Java 源码
// Output: PASS/FAIL 报告——PR 新增 Component/Hook/Service/API/Function/Utils 是否与仓库已有实现重复
// Pos: scripts/ - 防重复造轮子的 PR/push 门禁
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// 维护声明: 若匹配阈值、扫描目录、豁免注释格式或种类规则变化，请同步更新本脚本、spec、RELIABILITY.md 与 scripts/README.md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptFile = fileURLToPath(import.meta.url)

export const MIN_SCORE = 0.75
export const MIN_BODY_IDS = 6
export const REUSE_OK_PATTERN = /REUSE-OK:\s*(\S+)/i

const SOURCE_PREFIXES = ['src/', 'backend/src/main/java/']
const SOURCE_EXTENSIONS = new Set(['.js', '.ts', '.vue', '.java'])

const GENERIC_NAMES = new Set([
  'index', 'main', 'setup', 'init', 'load', 'save', 'run', 'handle', 'process',
  'execute', 'render', 'close', 'open', 'start', 'stop', 'reset', 'clear',
  'update', 'create', 'delete', 'remove', 'fetch', 'submit', 'confirm', 'cancel',
  'apply', 'parse', 'format', 'validate', 'transform', 'convert', 'build', 'make',
  'get', 'set', 'add', 'has', 'is', 'to', 'from', 'of', 'now', 'today', 'empty',
  'copy', 'clone', 'valueof', 'tojson', 'tomap', 'frommap', 'tostring', 'equals',
  'hashcode', 'canequal', 'builder', 'detail', 'list', 'form', 'dialog', 'modal',
  'drawer', 'card', 'table', 'panel', 'item', 'header', 'footer', 'layout', 'app',
  'page', 'view', 'data', 'value', 'result', 'config', 'options', 'params',
])

const GENERIC_FILENAMES = new Set([
  'index', 'detail', 'list', 'form', 'dialog', 'modal', 'drawer', 'card',
  'table', 'panel', 'item', 'header', 'footer', 'layout', 'app', 'main',
])

const NAME_PREFIX_TOKENS = new Set(['use', 'get', 'set', 'is', 'has', 'on', 'handle', 'do'])
const NAME_SUFFIX_TOKENS = new Set([
  'service', 'impl', 'helper', 'util', 'utils', 'manager', 'handler', 'adapter',
  'component', 'composable', 'factory', 'controller', 'repository', 'store', 'api',
])

const JS_JAVA_KEYWORDS = new Set([
  'abstract', 'arguments', 'async', 'await', 'boolean', 'break', 'byte', 'case',
  'catch', 'char', 'class', 'const', 'continue', 'debugger', 'default', 'delete',
  'do', 'double', 'else', 'enum', 'export', 'extends', 'false', 'final', 'finally',
  'float', 'for', 'function', 'goto', 'if', 'implements', 'import', 'in',
  'instanceof', 'int', 'interface', 'let', 'long', 'native', 'new', 'null',
  'package', 'private', 'protected', 'public', 'return', 'short', 'static',
  'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient',
  'true', 'try', 'typeof', 'undefined', 'var', 'void', 'volatile', 'while',
  'with', 'yield', 'of', 'from', 'as',
])

const GENERIC_IDENTIFIERS = new Set([
  ...JS_JAVA_KEYWORDS,
  'map', 'filter', 'reduce', 'then', 'catch', 'length', 'value', 'data', 'item',
  'list', 'result', 'params', 'options', 'config', 'type', 'name', 'id', 'key',
  'msg', 'err', 'error', 'req', 'res', 'ctx', 'log', 'logger', 'console',
  'window', 'document', 'object', 'string', 'number', 'boolean', 'array',
  'optional', 'override', 'nullable', 'notnull', 'spring', 'autowired',
  'component', 'service', 'repository', 'controller', 'request', 'response',
  'entity', 'table', 'column', 'join', 'query', 'where', 'select',
])

const JAVA_SKIP_CLASS_SUFFIX = /(Entity|DTO|Request|Response|VO|PO|Enum|Properties|Config|Controller|Repository|Specification|Mapper|Exception|Event|Listener|Aspect|Filter|Interceptor|Advice)$/

const KIND_COMPAT = {
  component: ['component'],
  hook: ['hook'],
  service: ['service'],
  api: ['api'],
  util: ['util', 'function'],
  function: ['function', 'util'],
}

export function parseArgs(argv) {
  const options = { base: null, head: 'HEAD', json: false }
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--base') {
      options.base = argv[i + 1]
      i += 1
      continue
    }
    if (arg === '--head') {
      options.head = argv[i + 1]
      i += 1
      continue
    }
    if (arg === '--json') {
      options.json = true
      continue
    }
    if (arg === '--help' || arg === '-h') {
      options.help = true
      continue
    }
    throw new Error(`Unknown argument: ${arg}`)
  }
  return options
}

export function isSkippablePath(relPath) {
  const norm = relPath.replace(/\\/g, '/')
  if (norm.includes('node_modules/') || norm.includes('/target/') || norm.includes('/dist/')) {
    return true
  }
  if (norm.includes('/__tests__/') || norm.includes('/test/') || norm.includes('/e2e/')) {
    return true
  }
  if (/\.(spec|test)\.(js|ts|mjs|cjs)$/.test(norm)) return true
  if (/(Test|Tests|IT)\.java$/.test(norm)) return true
  if (norm.includes('/generated/') || norm.endsWith('.d.ts')) return true
  return false
}

export function isSourcePath(relPath) {
  const norm = relPath.replace(/\\/g, '/')
  if (!SOURCE_PREFIXES.some((prefix) => norm.startsWith(prefix))) return false
  return SOURCE_EXTENSIONS.has(path.extname(norm))
}

export function isPageVue(relPath) {
  const norm = relPath.replace(/\\/g, '/')
  return norm.endsWith('.vue') && norm.includes('/views/') && !norm.includes('/components/')
}

export function languageOf(relPath) {
  return relPath.replace(/\\/g, '/').endsWith('.java') ? 'java' : 'js'
}

export function tokenizeName(name) {
  if (!name) return []
  const parts = name
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .toLowerCase()
    .split(/\s+/)
    .filter((part) => part.length > 1)
  return parts.filter((part, index) => {
    if (index === 0 && NAME_PREFIX_TOKENS.has(part)) return false
    if (index === parts.length - 1 && NAME_SUFFIX_TOKENS.has(part) && parts.length > 1) {
      return false
    }
    return true
  })
}

export function normalizeName(name) {
  return tokenizeName(name).join('')
}

export function isGenericName(name) {
  const lower = String(name || '').toLowerCase()
  if (GENERIC_NAMES.has(lower)) return true
  const tokens = tokenizeName(name)
  if (tokens.length === 0) return true
  return tokens.every((token) => GENERIC_NAMES.has(token))
}

export function jaccard(a, b) {
  const left = a instanceof Set ? a : new Set(a)
  const right = b instanceof Set ? b : new Set(b)
  if (left.size === 0 && right.size === 0) return 0
  let inter = 0
  for (const item of left) {
    if (right.has(item)) inter += 1
  }
  return inter / (left.size + right.size - inter)
}

export function kindsCompatible(a, b) {
  return (KIND_COMPAT[a] || []).includes(b)
}

export function classifyKind(filePath, name) {
  const norm = filePath.replace(/\\/g, '/')
  const base = path.basename(norm)
  if (norm.endsWith('.vue') && /^[A-Z]/.test(name)) return 'component'
  if (/^use[A-Z]/.test(name) || /\/composables\//.test(norm) || /^use[A-Z].*\.(js|ts)$/.test(base)) {
    return 'hook'
  }
  if (/(^|\/)src\/api\//.test(norm)) return 'api'
  if (/Service\.(js|ts|java)$/.test(base) || /\/service\//i.test(norm) || /Service$/.test(name)) {
    return 'service'
  }
  if (
    /\/utils?\//.test(norm)
    || /Util(s)?\.(js|ts|java)$/.test(base)
    || /Helper\.(js|ts|java)$/.test(base)
    || /Utils?$/.test(name)
    || /Helper$/.test(name)
  ) {
    return 'util'
  }
  return 'function'
}

export function isCatalogClass(className, filePath) {
  const norm = filePath.replace(/\\/g, '/')
  if (!norm.endsWith('.java')) return true
  if (JAVA_SKIP_CLASS_SUFFIX.test(className)) return false
  if (/\/(entity|dto|config|bootstrap|controller|repository)\//.test(norm)) return false
  return /(Service|Util|Utils|Helper|Facade|Support|Policy|Resolver|Assembler)$/.test(className)
    || /\/(service|util|support|core)\//.test(norm)
}

function stripCommentsAndStrings(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (block) => block.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:])\/\/.*$/gm, '$1')
    .replace(/(['"`])(?:\\.|(?!\1).)*\1/g, ' ')
}

export function extractIdentifiers(body) {
  if (!body) return new Set()
  const cleaned = stripCommentsAndStrings(body)
  const ids = new Set()
  const re = /\b[A-Za-z_][A-Za-z0-9_]{2,}\b/g
  let match
  while ((match = re.exec(cleaned)) !== null) {
    const token = match[0]
    if (GENERIC_IDENTIFIERS.has(token.toLowerCase())) continue
    ids.add(token)
  }
  return ids
}

function lineNumberAt(content, index) {
  if (index <= 0) return 1
  return content.slice(0, index).split('\n').length
}

function extractBlock(content, fromIndex) {
  const brace = content.indexOf('{', fromIndex)
  const parenEnd = content.indexOf(')', fromIndex)
  if (brace < 0 || (parenEnd >= 0 && content.slice(parenEnd, brace).includes('=>') && !content.slice(parenEnd, brace).includes('{'))) {
    const arrow = content.indexOf('=>', fromIndex)
    if (arrow >= 0) {
      const nextSemi = content.indexOf(';', arrow)
      const nextNl = content.indexOf('\n', arrow)
      const end = [nextSemi, nextNl].filter((n) => n >= 0).sort((a, b) => a - b)[0]
      return content.slice(arrow, end == null ? arrow + 180 : end)
    }
  }
  if (brace < 0) return ''
  let depth = 0
  for (let i = brace; i < content.length; i += 1) {
    const ch = content[i]
    if (ch === '{') depth += 1
    else if (ch === '}') {
      depth -= 1
      if (depth === 0) return content.slice(brace, i + 1)
    }
  }
  return content.slice(brace, Math.min(content.length, brace + 2500))
}

function collectReuseOk(content) {
  const targets = []
  const re = /REUSE-OK:\s*(\S+)/gi
  let match
  while ((match = re.exec(content)) !== null) {
    targets.push(match[1].replace(/[,;]+$/, ''))
  }
  return targets
}

function symbolNearReuseOk(content, index) {
  const lineStart = content.lastIndexOf('\n', Math.max(0, index - 1)) + 1
  const prevStart = content.lastIndexOf('\n', Math.max(0, lineStart - 2)) + 1
  const window = content.slice(Math.max(0, prevStart - 200), index + 80)
  return collectReuseOk(window)
}

function vueScriptContent(content) {
  const blocks = []
  const re = /<script\b[^>]*>([\s\S]*?)<\/script>/gi
  let match
  while ((match = re.exec(content)) !== null) {
    blocks.push(match[1])
  }
  return blocks.join('\n')
}

function pushSymbol(symbols, payload) {
  if (!payload?.name) return
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(payload.name)) return
  if (payload.name.length < 3) return
  symbols.push(payload)
}

export function extractSymbols(content, filePath) {
  const symbols = []
  const norm = filePath.replace(/\\/g, '/')
  const language = languageOf(norm)
  const fileReuse = collectReuseOk(content)

  if (norm.endsWith('.vue') && !isPageVue(norm)) {
    const base = path.basename(norm, '.vue')
    if (!GENERIC_FILENAMES.has(base.toLowerCase())) {
      const nameMatch = content.match(/defineOptions\(\s*\{\s*name:\s*['"`]([^'"`]+)['"`]/)
        || content.match(/defineComponent\(\s*\{\s*name:\s*['"`]([^'"`]+)['"`]/)
        || content.match(/export\s+default\s+\{[\s\S]{0,180}?name:\s*['"`]([^'"`]+)['"`]/)
      const componentName = nameMatch?.[1] || base
      pushSymbol(symbols, {
        name: componentName,
        kind: 'component',
        file: norm,
        line: 1,
        language: 'js',
        identifiers: extractIdentifiers(vueScriptContent(content)),
        reuseOk: fileReuse,
      })
    }
  }

  const code = norm.endsWith('.vue') ? vueScriptContent(content) : content
  const offsetInFile = norm.endsWith('.vue') ? content.indexOf(code) : 0
  const searchable = code

  if (language === 'java') {
    extractJavaSymbols(searchable, norm, symbols, fileReuse, offsetInFile, content)
    return dedupeSymbols(symbols)
  }

  extractJsSymbols(searchable, norm, symbols, fileReuse, offsetInFile, content)
  return dedupeSymbols(symbols)
}

function extractJsSymbols(code, filePath, symbols, fileReuse, offset, original) {
  const patterns = [
    { re: /export\s+(?:async\s+)?function\s+([A-Za-z_][\w]*)\s*\(/g, kindHint: null },
    { re: /export\s+const\s+([A-Za-z_][\w]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_][\w]*)\s*=>/g, kindHint: null },
    { re: /export\s+const\s+([A-Za-z_][\w]*)\s*=\s*(?:async\s+)?function\b/g, kindHint: null },
    { re: /export\s+default\s+(?:async\s+)?function(?:\s+([A-Za-z_][\w]*))?/g, kindHint: null },
  ]

  for (const { re } of patterns) {
    re.lastIndex = 0
    let match
    while ((match = re.exec(code)) !== null) {
      const name = match[1]
      if (!name || /^[A-Z0-9_]+$/.test(name)) continue
      const absIndex = Math.max(0, offset) + match.index
      const body = extractBlock(code, match.index)
      const reuseOk = [...fileReuse, ...symbolNearReuseOk(original, absIndex)]
      pushSymbol(symbols, {
        name,
        kind: classifyKind(filePath, name),
        file: filePath,
        line: lineNumberAt(original, absIndex),
        language: 'js',
        identifiers: extractIdentifiers(body),
        reuseOk,
      })
    }
  }
}

function extractJavaSymbols(code, filePath, symbols, fileReuse, offset, original) {
  const classRe = /\b(?:public|protected)\s+(?:final\s+)?(?:abstract\s+)?class\s+([A-Z][\w]*)/g
  let classMatch
  let className = null
  while ((classMatch = classRe.exec(code)) !== null) {
    className = classMatch[1]
    if (!isCatalogClass(className, filePath)) {
      return
    }
    const absIndex = Math.max(0, offset) + classMatch.index
    pushSymbol(symbols, {
      name: className,
      kind: classifyKind(filePath, className),
      file: filePath,
      line: lineNumberAt(original, absIndex),
      language: 'java',
      identifiers: extractIdentifiers(extractBlock(code, classMatch.index)),
      reuseOk: [...fileReuse, ...symbolNearReuseOk(original, absIndex)],
    })
    break
  }
  if (!className) return

  const methodRe = /(?:^|\n)[ \t]*(?:@\w+(?:\([^)]*\))?[ \t]*)*(?:public|protected)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?(?:<[^>]+>\s+)?[\w.<>,\[\]?]+\s+([a-z][\w]*)\s*\(/g
  let methodMatch
  while ((methodMatch = methodRe.exec(code)) !== null) {
    const name = methodMatch[1]
    if (/^(get|set|is)[A-Z]/.test(name) && classifyKind(filePath, className) === 'service') {
      continue
    }
    if (['equals', 'hashCode', 'toString', 'canEqual', 'builder', 'build', 'clone'].includes(name)) {
      continue
    }
    const windowStart = Math.max(0, methodMatch.index - 80)
    if (/@Override\b/.test(code.slice(windowStart, methodMatch.index))) continue
    const absIndex = Math.max(0, offset) + methodMatch.index
    const body = extractBlock(code, methodMatch.index)
    pushSymbol(symbols, {
      name,
      kind: classifyKind(filePath, className),
      file: filePath,
      line: lineNumberAt(original, absIndex),
      language: 'java',
      identifiers: extractIdentifiers(body),
      reuseOk: [...fileReuse, ...symbolNearReuseOk(original, absIndex)],
    })
  }
}

function dedupeSymbols(symbols) {
  const seen = new Set()
  const result = []
  for (const symbol of symbols) {
    const key = `${symbol.file}::${symbol.kind}::${symbol.name}`
    if (seen.has(key)) continue
    seen.add(key)
    result.push(symbol)
  }
  return result
}

export function isSuppressed(symbol, existing) {
  const targets = symbol.reuseOk || []
  if (targets.some((target) => target === '*' || target === 'true')) return true
  return targets.some((target) => {
    const cleaned = target.replace(/[(),]+$/, '')
    return existing.file === cleaned
      || existing.file.endsWith(cleaned)
      || cleaned.endsWith(existing.file)
      || existing.file.includes(cleaned)
  })
}

function namesContain(a, b) {
  if (!a || !b) return false
  if (a === b) return false
  if (a.length < 10 || b.length < 10) return false
  return a.includes(b) || b.includes(a)
}

export function scoreCandidate(newbie, existing) {
  if (!newbie || !existing) return null
  if (newbie.file === existing.file) return null
  if (newbie.language !== existing.language) return null
  if (!kindsCompatible(newbie.kind, existing.kind)) return null

  const exact = newbie.name.toLowerCase() === existing.name.toLowerCase()
  const newbieNorm = normalizeName(newbie.name)
  const existingNorm = normalizeName(existing.name)
  const normEqual = Boolean(newbieNorm) && newbieNorm === existingNorm && newbieNorm.length >= 8
  const tokenSim = jaccard(tokenizeName(newbie.name), tokenizeName(existing.name))
  const bodyReady = newbie.identifiers.size >= MIN_BODY_IDS && existing.identifiers.size >= MIN_BODY_IDS
  const bodySim = bodyReady ? jaccard(newbie.identifiers, existing.identifiers) : 0
  const javaServicePair = newbie.language === 'java' && newbie.kind === 'service' && existing.kind === 'service'

  if (exact && isGenericName(newbie.name)) {
    if (bodySim >= 0.55) {
      return { score: 0.9, reason: `通用名称但函数体高度相似（实现=${bodySim.toFixed(2)}）` }
    }
    return null
  }

  if (exact) {
    if (javaServicePair && bodySim < 0.4) return null
    return { score: 1, reason: '名称完全相同' }
  }

  if (normEqual) {
    if (bodySim >= 0.3 || (!javaServicePair && tokenSim >= 0.9)) {
      return {
        score: 0.85,
        reason: `名称归一化相同（${newbie.name} ≈ ${existing.name}）`,
      }
    }
  }

  if (namesContain(newbieNorm, existingNorm) && bodySim >= 0.45) {
    return {
      score: 0.8,
      reason: `名称包含关系且实现相近（${existing.name}，实现=${bodySim.toFixed(2)}）`,
    }
  }

  if (tokenSim >= 0.8 && bodySim >= 0.5) {
    return {
      score: 0.75,
      reason: `名称不同但能力相同（token=${tokenSim.toFixed(2)}, 实现=${bodySim.toFixed(2)}）`,
    }
  }

  return null
}

export function reusePriority(filePath) {
  const norm = String(filePath || '').replace(/\\/g, '/')
  if (norm.startsWith('src/utils/') || /\/util(s)?\//.test(norm) || /Util(s)?\.java$/.test(norm)) return 0
  if (norm.startsWith('src/composables/')) return 1
  if (norm.startsWith('src/components/')) return 2
  if (norm.startsWith('src/api/')) return 3
  if (/\/service\//.test(norm) || /Service\.java$/.test(norm)) return 4
  return 9
}

function isBetterMatch(candidate, current) {
  if (!current) return true
  if (candidate.score !== current.score) return candidate.score > current.score
  return reusePriority(candidate.existing.file) < reusePriority(current.existing.file)
}

export function findDuplicates(newSymbols, catalog, minScore = MIN_SCORE) {
  const findings = []
  for (const newbie of newSymbols) {
    let best = null
    for (const existing of catalog) {
      if (isSuppressed(newbie, existing)) continue
      const scored = scoreCandidate(newbie, existing)
      if (!scored || scored.score < minScore) continue
      const candidate = { ...scored, existing }
      if (isBetterMatch(candidate, best)) {
        best = candidate
      }
    }
    if (best) {
      findings.push({
        kind: newbie.kind,
        name: newbie.name,
        file: newbie.file,
        line: newbie.line,
        existingFile: best.existing.file,
        existingName: best.existing.name,
        existingLine: best.existing.line,
        reason: best.reason,
        score: best.score,
      })
    }
  }
  findings.sort((a, b) => b.score - a.score || a.file.localeCompare(b.file))
  return findings
}

export function formatReport(result) {
  const lines = ['=== 重复实现检查 ===']
  if (result.skipped) {
    lines.push('PASS: 跳过重复实现检查（DUPLICATE_IMPL_SKIP=1）')
    return lines.join('\n')
  }
  if (result.findings.length === 0) {
    const detail = result.checked === 0
      ? '无新增 Component/Hook/Service/API/Function/Utils'
      : `已检查 ${result.checked} 个新增符号`
    lines.push(`PASS: 没有发现重复实现（${detail}）`)
    return lines.join('\n')
  }
  lines.push(`FAIL: 发现 ${result.findings.length} 处重复实现，应复用现有代码。`)
  lines.push('')
  result.findings.forEach((finding, index) => {
    lines.push(`${index + 1}. [${finding.kind}] ${finding.name}`)
    lines.push(`   新增: ${finding.file}:${finding.line}`)
    lines.push(`   应复用: ${finding.existingFile}:${finding.existingLine} (${finding.existingName})`)
    lines.push(`   原因: ${finding.reason}`)
    lines.push('')
  })
  lines.push('豁免（确认并非重复后）: 在新增声明上一行添加')
  lines.push('  // REUSE-OK: <existing-path> — <具体原因>')
  lines.push('逃生阀: DUPLICATE_IMPL_SKIP=1')
  return lines.join('\n')
}

function git(cwd, args) {
  return execFileSync('git', args, {
    cwd,
    encoding: 'utf8',
    maxBuffer: 20 * 1024 * 1024,
  })
}

export function defaultBase(cwd) {
  try {
    return git(cwd, ['merge-base', 'origin/main', 'HEAD']).trim()
  } catch {
    try {
      return git(cwd, ['merge-base', 'main', 'HEAD']).trim()
    } catch {
      return 'HEAD'
    }
  }
}

function parseNameStatus(line) {
  if (!line.trim()) return null
  const fields = line.split('\t')
  const statusCode = fields[0][0]
  if (statusCode === 'R' || statusCode === 'C') {
    return { statusCode, path: fields[2] || fields[1] }
  }
  return { statusCode, path: fields[1] }
}

function showFile(cwd, rev, relPath) {
  try {
    return git(cwd, ['show', `${rev}:${relPath}`])
  } catch {
    const abs = path.join(cwd, relPath)
    if (rev === 'HEAD' && fs.existsSync(abs)) {
      return fs.readFileSync(abs, 'utf8')
    }
    return ''
  }
}

function listTrackedSource(cwd, rev) {
  const out = git(cwd, ['ls-tree', '-r', '--name-only', rev])
  return out
    .split('\n')
    .map((line) => line.trim())
    .filter((rel) => rel && isSourcePath(rel) && !isSkippablePath(rel))
}

function filenameSymbol(relPath, content) {
  const norm = relPath.replace(/\\/g, '/')
  const isVueComponent = norm.endsWith('.vue') && !isPageVue(norm)
  const isComposableFile = /\/composables\/use[A-Z]/.test(norm) || /^use[A-Z]/.test(path.basename(norm, path.extname(norm)))
  if (!isVueComponent && !isComposableFile) return null
  if (isPageVue(norm)) return null
  const ext = path.extname(norm)
  const base = path.basename(norm, ext)
  if (GENERIC_FILENAMES.has(base.toLowerCase())) return null
  if (!/^[A-Za-z_][\w]*$/.test(base)) return null
  return {
    name: base,
    kind: classifyKind(norm, base),
    file: norm,
    line: 1,
    language: languageOf(norm),
    identifiers: extractIdentifiers(content),
    reuseOk: collectReuseOk(content),
  }
}

export function collectNewSymbols(changedFiles, readHead, readBase) {
  const newSymbols = []
  for (const change of changedFiles) {
    const rel = change.path.replace(/\\/g, '/')
    if (!isSourcePath(rel) || isSkippablePath(rel)) continue
    const headContent = readHead(rel)
    if (!headContent) continue
    const headSymbols = extractSymbols(headContent, rel)
    if (change.statusCode === 'A') {
      const fileSym = filenameSymbol(rel, headContent)
      if (fileSym && !headSymbols.some((symbol) => symbol.name === fileSym.name)) {
        headSymbols.push(fileSym)
      }
      newSymbols.push(...headSymbols)
      continue
    }
    const baseContent = readBase(rel)
    const baseNames = new Set(extractSymbols(baseContent, rel).map((symbol) => `${symbol.kind}:${symbol.name}`))
    for (const symbol of headSymbols) {
      if (!baseNames.has(`${symbol.kind}:${symbol.name}`)) {
        newSymbols.push(symbol)
      }
    }
  }
  return dedupeSymbols(newSymbols)
}

export function buildCatalog(files, readFile, exclude) {
  const skip = new Set((exclude || []).map((symbol) => `${symbol.file}::${symbol.kind}::${symbol.name}`))
  const catalog = []
  for (const rel of files) {
    const norm = rel.replace(/\\/g, '/')
    if (!isSourcePath(norm) || isSkippablePath(norm)) continue
    const content = readFile(norm)
    if (!content) continue
    for (const symbol of extractSymbols(content, norm)) {
      const key = `${symbol.file}::${symbol.kind}::${symbol.name}`
      if (skip.has(key)) continue
      catalog.push(symbol)
    }
  }
  return catalog
}

export function runCheck({ cwd = process.cwd(), base, head = 'HEAD' } = {}) {
  if (process.env.DUPLICATE_IMPL_SKIP === '1') {
    return { verdict: 'PASS', checked: 0, findings: [], skipped: true }
  }

  const resolvedBase = base || defaultBase(cwd)
  const nameStatus = git(cwd, ['diff', '--name-status', '--find-renames', `${resolvedBase}...${head}`])
  const changedFiles = nameStatus
    .split('\n')
    .map(parseNameStatus)
    .filter((entry) => entry && (entry.statusCode === 'A' || entry.statusCode === 'M' || entry.statusCode === 'C'))

  const newSymbols = collectNewSymbols(
    changedFiles,
    (rel) => showFile(cwd, head, rel),
    (rel) => showFile(cwd, resolvedBase, rel),
  )

  if (newSymbols.length === 0) {
    return { verdict: 'PASS', checked: 0, findings: [], skipped: false, base: resolvedBase, head }
  }

  const tracked = listTrackedSource(cwd, head)
  const catalog = buildCatalog(tracked, (rel) => showFile(cwd, head, rel), newSymbols)
  const findings = findDuplicates(newSymbols, catalog)
  return {
    verdict: findings.length ? 'FAIL' : 'PASS',
    checked: newSymbols.length,
    findings,
    skipped: false,
    base: resolvedBase,
    head,
  }
}

function printHelp() {
  console.log(`Usage: node scripts/check-duplicate-implementations.mjs [--base <rev>] [--head <rev>] [--json]

检查当前 PR（默认 origin/main...HEAD）新增的 Component / Hook / Service / API / Function / Utils
是否与仓库已有实现重复。

PASS: 没有发现重复实现
FAIL: 发现重复实现，并指出应该复用的现有代码

豁免: // REUSE-OK: <existing-path> — <原因>
逃生阀: DUPLICATE_IMPL_SKIP=1`)
}

function main(argv) {
  const options = parseArgs(argv)
  if (options.help) {
    printHelp()
    return 0
  }
  const result = runCheck({
    cwd: process.cwd(),
    base: options.base,
    head: options.head,
  })
  if (options.json) {
    console.log(JSON.stringify(result, (key, value) => {
      if (value instanceof Set) return [...value]
      return value
    }, 2))
  } else {
    console.log(formatReport(result))
  }
  return result.verdict === 'FAIL' ? 1 : 0
}

if (process.argv[1] && path.resolve(process.argv[1]) === scriptFile) {
  try {
    process.exit(main(process.argv.slice(2)))
  } catch (error) {
    console.error(`重复实现检查失败: ${error.message}`)
    process.exit(1)
  }
}
