#!/usr/bin/env node
// Input: src/ directory (or path passed as argv[2])
// Output: list of files containing N+1 list-detail anti-pattern
// Pos: scripts/ - prevent recurrence of N+1 anti-pattern (spec 035)
// Usage: node scripts/check-list-endpoint-n1.mjs [src/]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   spec 034（限流提示友好化）已落地 6 次反复修后仍不能根除 429，根因是
//   list 端点不返回完整 DTO，迫使前端对每行 getDetail（N+1）。本脚本检测
//   反模式防止回归：
//     1. Promise.all(list.map(row => *.getDetail(...)))
//     2. loadDetailsInBatches / loadDetailsSequentially 函数名
//     3. await list.forEach 内含 getDetail 调用
//
// 修复建议：list 端点应返回完整业务 DTO（password 等敏感字段走单独端点）。

import fs from 'node:fs'
import path from 'node:path'

const root = process.argv[2] || path.resolve(process.cwd(), 'src')

const N1_PATTERNS = [
  {
    name: 'Promise.all(map(getDetail))',
    regex: /Promise\.all\s*\(\s*[\w.]+\.map\s*\(\s*(?:async\s*)?\(?\s*\w+\s*\)?\s*=>\s*[\w.]+\.getDetail\s*\(/,
    description: 'Promise.all(map(... => *.getDetail(...))) 是典型 N+1 反模式',
  },
  {
    name: 'loadDetailsInBatches/loadDetailsSequentially',
    regex: /(?:async\s+)?(?:function|const)\s+(loadDetailsInBatches|loadDetailsSequentially|loadAllDetails|fetchDetailsInLoop)\s*[=(]/,
    description: '函数名暗示了批量 N+1 加载 — list 端点应返回完整 DTO',
  },
  {
    name: 'forEach + getDetail',
    regex: /\bforEach\s*\(\s*(?:async\s*)?\(?\s*\w+\s*\)?\s*=>\s*[^{]*\{[^}]*\.getDetail\s*\(/,
    description: 'forEach 内调用 getDetail — 同步触发 N 个请求',
  },
]

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

function findViolations(source) {
  const violations = []
  const lines = source.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    // 跳过注释行（// / <!-- ... -->）
    if (/^\s*(\/\/|\*|<!--)/.test(line)) continue

    for (const pattern of N1_PATTERNS) {
      if (pattern.regex.test(line)) {
        violations.push({
          line: i + 1,
          pattern: pattern.name,
          description: pattern.description,
          snippet: line.trim().slice(0, 120),
        })
      }
    }
  }
  return violations
}

const files = walk(root)
let totalViolations = 0
const results = []

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8')
  const violations = findViolations(source)
  if (violations.length > 0) {
    totalViolations += violations.length
    results.push({ file, violations })
  }
}

const unique = results.map(r => r.file).sort()
console.log(`Found ${totalViolations} N+1 anti-pattern occurrences across ${unique.length} files`)
for (const r of results) {
  console.log(`\n${r.file}`)
  for (const v of r.violations) {
    console.log(`  L${v.line} [${v.pattern}] ${v.snippet}`)
    console.log(`    → ${v.description}`)
  }
}

if (totalViolations > 0) {
  console.error('')
  console.error('=== 修复建议 ===')
  console.error('list 端点应返回完整业务 DTO（contactPerson / registrant / registerPhone / registerEmail / hasCa / remarks / borrower / dueAt / lastUsed 等）')
  console.error('password 等敏感字段走独立 /api/.../{id}/password 端点')
  console.error('参考：specs/035-root-account-429/spec.md §FR-A-01')
  console.error('参考：.wiki/pages/frontend-pitfalls.md §12 "根治 vs 防御"')
  process.exit(1)
}

console.log('N+1 anti-pattern check passed.')
