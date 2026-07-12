#!/usr/bin/env node
// Input: src/ directory
// Output: JSON report + console table — risk-graded audit of business-layer 429 exposure
// Pos: scripts/ - drive 71-existing-business-layer ElMessage.error migration (spec 035)
// Usage: node scripts/audit-existing-429-exposure.mjs [src/] [--json]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   spec 034/!2035 修复了 2 处业务层 catch 覆盖 429 的问题，但全仓仍有 71 处
//   业务层 catch 块使用裸 ElMessage.error。规范要求 429 提示统一由全局
//   axios interceptor 处理，业务层应使用 notifyErrorUnlessRateLimit。
//
//   本脚本对存量 71 处做风险评级（不阻断），输出 JSON 给 CI 消费：
//     HIGH   — catch 块在 onMounted 路径 + 调用 *.getList() + ElMessage.error
//     MEDIUM — catch 块在任意 async function + 调用 *.getList() + ElMessage.error(e.message || ...)
//     LOW    — 其他 API 调用 + 仅有 ElMessage.error('fallback') 字符串
//
// 治理顺序：先 HIGH（用户体验最差），后 MEDIUM/LOW。

import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2] || path.resolve(process.cwd(), 'src')
const jsonMode = process.argv.includes('--json')

function walk(dir) {
  const entries = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules') continue
      entries.push(...walk(full))
    } else if (/\.(vue|js|ts|jsx|tsx)$/.test(entry.name)) {
      entries.push(full)
    }
  }
  return entries
}

function findCatchBlocks(source) {
  const blocks = []
  const re = /catch\s*\(\s*(?:\w+\s*)?\)\s*\{/g
  let m
  while ((m = re.exec(source)) !== null) {
    const start = m.index + m[0].length
    let depth = 1
    let i = start
    while (i < source.length && depth > 0) {
      if (source[i] === '{') depth++
      else if (source[i] === '}') depth--
      i++
    }
    if (depth === 0) {
      blocks.push({ start, end: i - 1, body: source.slice(start, i - 1) })
    }
  }
  return blocks
}

function getLineNumber(source, idx) {
  return source.slice(0, idx).split('\n').length
}

// 检测 catch 块是否在 onMounted/setup/created 路径
function isOnMountedPath(source, catchStart) {
  const window = 1500
  const snippet = source.slice(Math.max(0, catchStart - window), catchStart)
  return /\b(?:onMounted|onActivated|created|setup|defineComponent)\s*\(/.test(snippet)
}

// 检测 catch 块是否调用了 *.getList() / *.getListXxx() / 列表型 API
function callsListApi(catchBody) {
  return /\.\s*getList(?:[A-Z]\w*)?\s*\(/.test(catchBody) ||
    /\.\s*list\s*\(/.test(catchBody)
}

// 检测 catch 块是否使用了"会暴露 AxiosError"的 ElMessage.error(e.message || ...) 模式
function usesExposingElMessageError(catchBody) {
  return /ElMessage\.error\s*\(\s*(?:e|err|error)\.message/.test(catchBody)
}

// 检测 catch 块是否仅有 ElMessage.error('静态字符串') — 不暴露 e
function usesStaticElMessageError(catchBody) {
  const matches = catchBody.match(/ElMessage\.error\s*\([^)]+\)/g) || []
  return matches.length > 0 && matches.every(m => !/e\.message|err\.message|error\.message/.test(m))
}

function classifyRisk({ isMountPath, callsList, usesExposing, hasElMessage }) {
  if (!hasElMessage) return null
  if (isMountPath && callsList && usesExposing) return 'HIGH'
  if (callsList && usesExposing) return 'MEDIUM'
  if (hasElMessage) return 'LOW'
  return null
}

const files = walk(root)
const report = { HIGH: [], MEDIUM: [], LOW: [] }

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8')
  const blocks = findCatchBlocks(source)
  for (const block of blocks) {
    const hasElMessage = /ElMessage\.error\s*\(/.test(block.body)
    const isMountPath = isOnMountedPath(source, block.start)
    const callsList = callsListApi(block.body)
    const usesExposing = usesExposingElMessageError(block.body)
    const usesStatic = usesStaticElMessageError(block.body)

    const risk = classifyRisk({ isMountPath, callsList, usesExposing, hasElMessage })
    if (!risk) continue

    const line = getLineNumber(source, block.start)
    const snippet = block.body.slice(0, 200).replace(/\s+/g, ' ').trim()

    report[risk].push({
      file,
      line,
      isMountPath,
      callsList,
      usesExposing,
      usesStatic,
      snippet,
    })
  }
}

if (jsonMode) {
  // JSON mode: only output JSON, nothing else (CI consumption)
  console.log(JSON.stringify(report, null, 2))
  // Still emit governance status to stderr so CI can pick it up separately
  const highCount = report.HIGH.length
  const mediumCount = report.MEDIUM.length
  const STATUS = highCount === 0 && mediumCount <= 5 ? 'PASS' : 'NEEDS_GOVERNANCE'
  process.stderr.write(`status: ${STATUS}\nHIGH=${highCount} (target=0)\nMEDIUM=${mediumCount} (target<=5)\nLOW=${report.LOW.length} (non-blocking)\n`)
  process.exit(0)
} else {
  const total = report.HIGH.length + report.MEDIUM.length + report.LOW.length
  console.log(`Total 429-exposure risk: ${total}`)
  console.log(`  HIGH (onMounted + getList + exposes AxiosError): ${report.HIGH.length}`)
  console.log(`  MEDIUM (any async + getList + exposes AxiosError): ${report.MEDIUM.length}`)
  console.log(`  LOW (other ElMessage.error patterns): ${report.LOW.length}`)
  console.log('')
  for (const tier of ['HIGH', 'MEDIUM', 'LOW']) {
    if (report[tier].length === 0) continue
    console.log(`\n=== ${tier} (${report[tier].length}) ===`)
    for (const item of report[tier]) {
      const mountTag = item.isMountPath ? ' [onMounted]' : ''
      console.log(`  ${item.file}:${item.line}${mountTag}`)
      console.log(`    ${item.snippet}`)
    }
  }
  console.log('')
  console.log('修复指引：')
  console.log('  HIGH/MEDIUM: catch 块改为 notifyErrorUnlessRateLimit(error, "fallback")')
  console.log('  LOW: 若不会触发 429，可加 // SAFE: 非 API 错误 注释豁免')
  console.log('参考：src/api/error-utils.js')
}

const highCount = report.HIGH.length
const mediumCount = report.MEDIUM.length

// 治理目标：HIGH 必须 = 0；MEDIUM ≤ 5
const STATUS = highCount === 0 && mediumCount <= 5 ? 'PASS' : 'NEEDS_GOVERNANCE'
console.log('')
console.log(`status: ${STATUS}`)
console.log(`  HIGH=${highCount} (目标=0)`)
console.log(`  MEDIUM=${mediumCount} (目标≤5)`)
console.log(`  LOW=${report.LOW.length} (不阻断)`)

if (highCount > 0 || mediumCount > 5) {
  process.exit(2)
}
process.exit(0)
