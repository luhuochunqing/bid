#!/usr/bin/env node
// Input: src/ and e2e/ source trees, forbidden literal regex list, skip list
// Output: violation report for hardcoded task-status literal comparisons
// Pos: scripts/ - Repository maintenance guardrail for dictionary-driven task status
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Forbid hardcoded task-status literal comparisons in src/ and e2e/.
// Task statuses are dictionary-driven (task_status_dict); business code
// must judge on the `category === 'CLOSED'` semantic, or use a terminal
// set resolved from the dict at runtime. See:
//   docs/plans/2026-05-01-task-board-customization-design.md
//   docs/plans/2026-05-01-task-board-customization.md (Phase G)

import fs from 'node:fs'
import path from 'node:path'

const ROOTS = ['src', 'e2e']
const SKIP_FILES = new Set([
  'src/views/Project/project-utils.js', // legacy-bridge mapping table is allowed here
])
const FORBIDDEN = [
  // === 'todo' / === 'doing' / === 'review' / === 'done'
  /status\s*===\s*['"`](todo|doing|review|done)['"`]/,
  /status\s*!==\s*['"`](todo|doing|review|done)['"`]/,
  // status: 'todo' object literal patterns (likely filter targets)
  /status:\s*['"`](todo|doing|review|done)['"`]/,
]
const SKIP_DIRS = new Set(['node_modules', 'dist', '.vite', '.nuxt', '__snapshots__'])

function* walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) yield* walk(full)
    else if (/\.(vue|js|mjs|ts)$/.test(entry.name)) yield full
  }
}

let violations = 0
for (const root of ROOTS) {
  if (!fs.existsSync(root)) continue
  for (const file of walk(root)) {
    const norm = file.split(path.sep).join('/')
    if (SKIP_FILES.has(norm)) continue
    const src = fs.readFileSync(file, 'utf8')
    const lines = src.split('\n')
    lines.forEach((line, i) => {
      for (const pat of FORBIDDEN) {
        if (pat.test(line)) {
          console.error(`[task-status-literal] ${norm}:${i + 1}  ${line.trim()}`)
          violations++
        }
      }
    })
  }
}

if (violations > 0) {
  console.error('')
  console.error(`禁止硬编码任务状态字面量（todo/doing/review/done）。`)
  console.error(`改用字典驱动：category === 'CLOSED' 或 statuses.find(s => s.terminal)。`)
  console.error(`治理说明见 docs/plans/2026-05-01-task-board-customization-design.md 第 3 节。`)
  process.exit(1)
}

console.log('Task status literal check passed.')
