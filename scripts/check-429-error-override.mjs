#!/usr/bin/env node
// Input: git commit range (defaults to merge-base origin/main..HEAD)
// Output: fail if the diff adds new ElMessage.error calls inside API catch blocks
// Pos: scripts/ - prevent recurrence of 429 toast override (engineering-discipline)
// Usage: node scripts/check-429-error-override.mjs [base-ref]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   Global axios interceptor already shows a friendly 429 message. Business-layer
//   catch blocks that call ElMessage.error override it, exposing raw AxiosError
//   to users. This check blocks newly-introduced occurrences in the commit range.
//
// Existing debt is intentionally NOT counted; the scan only looks at ADDED lines.

import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'

const ROOT = process.cwd()
const BASE = process.argv[2] || getDefaultBase()

function getDefaultBase() {
  try {
    return execSync('git merge-base origin/main HEAD', { cwd: ROOT, encoding: 'utf8' }).trim()
  } catch {
    return 'origin/main'
  }
}

function exec(cmd) {
  try {
    return execSync(cmd, { cwd: ROOT, encoding: 'utf8' }).trim()
  } catch (e) {
    return ''
  }
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

function hasApiCallBeforeCatch(source, catchStart, window = 500) {
  const snippet = source.slice(Math.max(0, catchStart - window), catchStart)
  return /await\s+\w+Api\.|await\s+\w+Api\(|axios\.|http\.|fetch\(|api\./i.test(snippet)
}

function lineNumber(source, idx) {
  return source.slice(0, idx).split('\n').length
}

function getAddedLineNumbers(diff) {
  const added = []
  let newLine = null
  for (const line of diff.split('\n')) {
    if (line.startsWith('@@')) {
      const m = line.match(/\+(\d+)(?:,(\d+))?/)
      if (m) {
        newLine = parseInt(m[1], 10)
      }
    } else if (line.startsWith('+++') || line.startsWith('---')) {
      // header lines, ignore
    } else if (newLine !== null) {
      if (line.startsWith('+')) {
        added.push(newLine)
        newLine++
      } else if (line.startsWith('-')) {
        // removed line does not advance new line number
      } else if (line.startsWith('\\')) {
        // "\ No newline at end of file"
      } else {
        newLine++
      }
    }
  }
  return added
}

function getChangedFiles() {
  const out = exec(`git diff --name-only --diff-filter=AM "${BASE}"..HEAD -- src/`)
  if (!out) return []
  return out.split('\n').filter(f => /\.(vue|js|ts|jsx|tsx)$/.test(f))
}

function getDiffForFile(file) {
  return exec(`git diff --unified=0 "${BASE}"..HEAD -- "${file}"`)
}

const changedFiles = getChangedFiles()
let violations = 0

for (const file of changedFiles) {
  const fullPath = path.join(ROOT, file)
  if (!fs.existsSync(fullPath)) continue
  const source = fs.readFileSync(fullPath, 'utf8')
  const blocks = findCatchBlocks(source)
  if (blocks.length === 0) continue

  const diff = getDiffForFile(file)
  const addedLines = getAddedLineNumbers(diff)
  if (addedLines.length === 0) continue

  for (const block of blocks) {
    if (!/ElMessage\.error\s*\(/.test(block.body)) continue
    if (!hasApiCallBeforeCatch(source, block.start)) continue

    const blockStartLine = lineNumber(source, block.start)
    const blockEndLine = lineNumber(source, block.end)

    const hasAddedLineInBlock = addedLines.some(ln => ln >= blockStartLine && ln <= blockEndLine)
    if (hasAddedLineInBlock) {
      console.error(`[429-override] ${file}:${blockStartLine} 新增的 catch 块中 ElMessage.error 会覆盖全局 429 友好提示`)
      violations++
    }
  }
}

if (violations > 0) {
  console.error('')
  console.error(`检测到 ${violations} 处新增 API catch 块中的 ElMessage.error，违反 429 友好提示治理。`)
  console.error('处理方式：')
  console.error('  1. 将 ElMessage.error 替换为 notifyErrorUnlessRateLimit(error, "友好fallback")')
  console.error('  2. 若该 catch 块不处理 HTTP 错误（如表单校验），可在 catch 块上方加 // SAFE: 非 API 错误')
  console.error('参考：src/api/error-utils.js')
  process.exit(1)
}

console.log('429 error-override check passed.')
