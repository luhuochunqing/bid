#!/usr/bin/env node
// Input: src/ directory
// Output: list of catch blocks that call ElMessage.error after API calls
// Pos: scripts/ - detect business-layer 429 toast overrides
// Usage: node scripts/scan-429-catch.mjs [src/]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2] || path.resolve(process.cwd(), 'src')

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
  const catchRe = /catch\s*\(\s*(?:\w+\s*)?\)\s*\{/g
  let m
  while ((m = catchRe.exec(source)) !== null) {
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

function isApiCallBeforeCatch(source, catchStart, window = 400) {
  const snippet = source.slice(Math.max(0, catchStart - window), catchStart)
  return /await\s+\w+Api\.|await\s+\w+Api\(|axios\.|http\.|fetch\(|api\./i.test(snippet)
}

function hasElMessageError(body) {
  return /ElMessage\.error\s*\(/.test(body)
}

const files = walk(root)
let total = 0
const results = []

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8')
  const blocks = findCatchBlocks(source)
  for (const block of blocks) {
    if (hasElMessageError(block.body) && isApiCallBeforeCatch(source, block.start)) {
      total++
      results.push(file)
      break
    }
  }
}

const unique = [...new Set(results)].sort()
console.log(`Found ${total} API-like catch blocks with ElMessage.error across ${unique.length} files`)
for (const file of unique) {
  console.log(file)
}
process.exit(total > 0 ? 1 : 0)
