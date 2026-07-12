#!/usr/bin/env node
// Input: src/ directory
// Output: list of files where onMounted calls a load function whose catch block uses ElMessage.error
// Pos: scripts/ - prioritize 429 toast overrides on page load
// Usage: node scripts/scan-load-on-mount-429.mjs [src/]
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

function findFunctionBlocks(source) {
  const blocks = []
  const fnRe = /(?:async\s+)?function\s+(\w+)\s*\([^)]*\)\s*\{/g
  let m
  while ((m = fnRe.exec(source)) !== null) {
    const name = m[1]
    const start = m.index + m[0].length
    let depth = 1
    let i = start
    while (i < source.length && depth > 0) {
      if (source[i] === '{') depth++
      else if (source[i] === '}') depth--
      i++
    }
    if (depth === 0) {
      blocks.push({ name, start, end: i - 1, body: source.slice(start, i - 1) })
    }
  }
  return blocks
}

function findArrowFunctionBlocks(source) {
  const blocks = []
  const re = /(?:const|let|var)\s+(\w+)\s*=\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{/g
  let m
  while ((m = re.exec(source)) !== null) {
    const name = m[1]
    const start = m.index + m[0].length
    let depth = 1
    let i = start
    while (i < source.length && depth > 0) {
      if (source[i] === '{') depth++
      else if (source[i] === '}') depth--
      i++
    }
    if (depth === 0) {
      blocks.push({ name, start, end: i - 1, body: source.slice(start, i - 1) })
    }
  }
  return blocks
}

function hasApiCallAndElMessageError(body) {
  return /await\s+\w+Api\.|await\s+\w+Api\(|axios\.|http\.|fetch\(|api\./i.test(body) &&
    /ElMessage\.error\s*\(/.test(body)
}

const files = walk(root)
let total = 0
const results = []

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8')
  const fns = [...findFunctionBlocks(source), ...findArrowFunctionBlocks(source)]
  const loadFns = fns.filter(fn => /load|fetch|get|query|refresh/i.test(fn.name) && hasApiCallAndElMessageError(fn.body))
  if (loadFns.length === 0) continue

  const sourceWithoutFns = fns.reduce((acc, fn) => {
    return acc.slice(0, fn.start - fn.name.length - 20) + ' '.repeat(fn.end - fn.start + fn.name.length + 20) + acc.slice(fn.end)
  }, source)

  for (const fn of loadFns) {
    const callRe = new RegExp(`(?:onMounted|created|setup|watch|watchEffect)\\s*\\(\\s*(?:async\\s*\\(\\s*\\)\\s*=>\\s*\\{\\s*)?${fn.name}\\s*\\(`, 'i')
    if (callRe.test(sourceWithoutFns)) {
      total++
      results.push(`${file} -> ${fn.name}`)
      break
    }
  }
}

const unique = [...new Set(results.map(r => r.split(' -> ')[0]))].sort()
console.log(`Found ${total} load-on-mount functions with API catch + ElMessage.error across ${unique.length} files`)
for (const r of results) {
  console.log(r)
}
process.exit(total > 0 ? 1 : 0)
