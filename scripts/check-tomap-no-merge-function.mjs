#!/usr/bin/env node
// Input: backend/src/main/java/**/*.java
// Output: violation report for Collectors.toMap 2-arg calls
// Pos: scripts/ - pre-push guardrail against Constitution v2.0.0 Principle VII
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Constitution v2.0.0 Principle VII: Collectors.toMap without merge function
// throws IllegalStateException on duplicate keys. New code must use the 3-arg
// form toMap(k, v, (a, b) -> a). Existing 2-arg calls are tracked in
// scripts/tomap-exemptions.json and must be removed as part of US2 migration.

import fs from 'node:fs'
import path from 'node:path'

const ROOT = 'backend/src/main/java'
const EXEMPTIONS_FILE = 'scripts/tomap-exemptions.json'

// 载入豁免清单: "file:line" -> true
const exemptions = new Set()
try {
  const data = JSON.parse(fs.readFileSync(EXEMPTIONS_FILE, 'utf8'))
  for (const e of data.exemptions || []) exemptions.add(`${e.file}:${e.line}`)
} catch (err) {
  console.error(`[tomap-no-merge] FATAL: cannot load ${EXEMPTIONS_FILE}: ${err.message}`)
  process.exit(1)
}

function* walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'target' || e.name === 'node_modules') continue
    const full = path.join(dir, e.name)
    if (e.isDirectory()) yield* walk(full)
    else if (e.name.endsWith('.java')) yield full
  }
}

// 在 src 中找到所有 `Collectors.toMap(` 调用，返回 [{ line, argCount }]
// 顶层逗号计数：跳过初始 '(' 后从 depth=0 开始，嵌套 () 进入 depth>0，
// ')' 在 depth=0 时结束调用。注释/字符串/字符字面量被忽略。
function findTomapCalls(src) {
  const calls = []
  let i = 0
  while (true) {
    const idx = src.indexOf('Collectors.toMap(', i)
    if (idx === -1) break
    const line = src.slice(0, idx).split('\n').length
    let j = idx + 'Collectors.toMap('.length  // 位置在 '(' 之后
    let depth = 0, topCommas = 0
    let inStr = false, inChar = false, inLine = false, inBlock = false
    while (j < src.length) {
      const c = src[j], n = src[j + 1]
      if (inLine) { if (c === '\n') inLine = false; j++; continue }
      if (inBlock) { if (c === '*' && n === '/') { inBlock = false; j += 2; continue } j++; continue }
      if (inStr) { if (c === '\\') { j += 2; continue } if (c === '"') inStr = false; j++; continue }
      if (inChar) { if (c === '\\') { j += 2; continue } if (c === "'") inChar = false; j++; continue }
      if (c === '/' && n === '/') { inLine = true; j += 2; continue }
      if (c === '/' && n === '*') { inBlock = true; j += 2; continue }
      if (c === '"') { inStr = true; j++; continue }
      if (c === "'") { inChar = true; j++; continue }
      if (c === '(') { depth++; j++; continue }
      if (c === ')') { if (depth === 0) break; depth--; j++; continue }
      if (c === ',' && depth === 0) { topCommas++; j++; continue }
      j++
    }
    calls.push({ line, argCount: topCommas + 1 })
    i = j + 1
  }
  return calls
}

let violations = 0, exempted = 0
for (const file of walk(ROOT)) {
  const rel = file.split(path.sep).join('/')
  const relPath = rel.replace(/^backend\/src\/main\/java\//, '')
  const src = fs.readFileSync(file, 'utf8')
  for (const call of findTomapCalls(src)) {
    if (call.argCount !== 2) continue
    if (exemptions.has(`${relPath}:${call.line}`)) { exempted++; continue }
    console.error(`[tomap-no-merge] ${rel}:${call.line}  Collectors.toMap 2-arg without merge function`)
    violations++
  }
}

if (violations > 0) {
  console.error(`\n检测到 ${violations} 处 Collectors.toMap 2-arg 调用，违反 Constitution v2.0.0 Principle VII。`)
  console.error(`修复：第 3 参数加 (a, b) -> a。豁免登记：scripts/tomap-exemptions.json`)
  console.error(`详见：scripts/check-tomap-no-merge-function.mjs`)
  process.exit(1)
}

console.log(`toMap no-merge-function check passed (${exempted} exempted sites).`)
