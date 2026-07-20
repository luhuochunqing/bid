#!/usr/bin/env node
// Input: git commit range (defaults to merge-base origin/main..HEAD)
// Output: fail if the diff adds new "## N." sections to docs/lessons/lessons-learned.md
//         whose section number already exists (or is lower than origin/main's max) in origin/main.
// Pos: scripts/ - prevent parallel weekly-archive PRs from section-number collision
// Usage:
//   node scripts/check-lessons-section-conflict.mjs [base-ref]
//   node scripts/check-lessons-section-conflict.mjs --audit-existing
//
// Background:
//   Multiple agents (gemini/cursor/claude/codex) may run "每周知识归档" in parallel.
//   Each branches off main at different times, picks the next "## N." section number,
//   and opens a PR. The first PR to merge wins; later PRs either撞号 (same N) or
//   create gaps/duplicates. PR !2130 vs !2131/!2132 was the real incident:
//   gemini opened a PR with §63-§65, but cursor had already merged §63-§66 to main.
//
// History:
//   2026-07-20: original implementation. Blocks new PRs whose added "## N." section
//   numbers conflict with (≤) the max section number already on origin/main.

import fs from 'node:fs'
import path from 'node:path'
import { execSync } from 'node:child_process'

const ROOT = process.cwd()
const args = process.argv.slice(2)
const AUDIT_EXISTING = args.includes('--audit-existing')

const LESSONS_FILE = 'docs/lessons/lessons-learned.md'
// Match "## 63." / "## 64." etc. (## space digits dot)
const SECTION_PATTERN = /^##\s+(\d+)\.\s/

function getAddedSectionNumbers(baseRef) {
  // Get added lines starting with "## N." from lessons-learned.md in the diff
  const rel = LESSONS_FILE
  const cmd = `git diff ${baseRef}..HEAD -- ${rel}`
  let out
  try {
    out = execSync(cmd, { encoding: 'utf8', cwd: ROOT })
  } catch {
    return []  // file not changed or base ref missing
  }
  const addedSections = []
  for (const line of out.split('\n')) {
    if (line.startsWith('+') && !line.startsWith('+++')) {
      const m = line.slice(1).match(SECTION_PATTERN)
      if (m) addedSections.push(parseInt(m[1], 10))
    }
  }
  return addedSections
}

function getOriginMainMaxSection() {
  // Read lessons-learned.md from origin/main, return max section number
  let content
  try {
    content = execSync(`git show origin/main:${LESSONS_FILE}`, { encoding: 'utf8', cwd: ROOT })
  } catch {
    return 0  // file missing on origin/main, nothing to conflict
  }
  let max = 0
  for (const line of content.split('\n')) {
    const m = line.match(SECTION_PATTERN)
    if (m) {
      const n = parseInt(m[1], 10)
      if (n > max) max = n
    }
  }
  return max
}

function getOriginMainSectionSet() {
  // Read lessons-learned.md from origin/main, return Set of section numbers
  let content
  try {
    content = execSync(`git show origin/main:${LESSONS_FILE}`, { encoding: 'utf8', cwd: ROOT })
  } catch {
    return new Set()
  }
  const set = new Set()
  for (const line of content.split('\n')) {
    const m = line.match(SECTION_PATTERN)
    if (m) set.add(parseInt(m[1], 10))
  }
  return set
}

// Exported for unit tests
export {
  SECTION_PATTERN,
  getAddedSectionNumbers,
  getOriginMainMaxSection,
  getOriginMainSectionSet,
}

function main() {
  if (AUDIT_EXISTING) {
    // Show current state of origin/main (info only, exit 0)
    const max = getOriginMainMaxSection()
    const set = getOriginMainSectionSet()
    console.log(`✓ origin/main ${LESSONS_FILE} current state:`)
    console.log(`  max section: §${max}`)
    console.log(`  total sections: ${set.size}`)
    console.log(`  next available: §${max + 1}`)
    return 0
  }

  // Default mode: block if added section numbers conflict with origin/main
  const baseRef = args.find(a => !a.startsWith('--')) || 'origin/main'
  let baseCommit
  try {
    baseCommit = execSync(`git merge-base ${baseRef} HEAD`, { encoding: 'utf8', cwd: ROOT }).trim()
  } catch {
    baseCommit = baseRef
  }

  // Ensure we have fresh origin/main
  try {
    execSync('git fetch origin main --prune', { encoding: 'utf8', cwd: ROOT, stdio: 'pipe' })
  } catch {
    // offline or no remote — use local origin/main if available
  }

  const added = getAddedSectionNumbers(baseCommit)
  if (added.length === 0) {
    console.log('✓ No new "## N." sections added to lessons-learned.md')
    return 0
  }

  const originMax = getOriginMainMaxSection()
  const originSet = getOriginMainSectionSet()

  const conflicts = []
  for (const n of added) {
    if (originSet.has(n)) {
      conflicts.push({ num: n, reason: `§${n} 已存在于 origin/main（编号撞号）` })
    } else if (n <= originMax) {
      conflicts.push({ num: n, reason: `§${n} 低于 origin/main 最大编号 §${originMax}（编号回退/间隙）` })
    }
  }

  if (conflicts.length === 0) {
    console.log(`✓ 新增章节 §${added.join('/§')} 与 origin/main 最大 §${originMax} 无冲突`)
    return 0
  }

  console.error(`✗ lessons-learned.md 章节编号与 origin/main 冲突：`)
  for (const c of conflicts) {
    console.error(`  ${c.reason}`)
  }
  console.error('')
  console.error(`origin/main 当前最大编号: §${originMax}`)
  console.error(`本次新增编号: §${added.join('/§')}`)
  console.error('')
  console.error('根因：多 agent 并行做每周知识归档时，各自从旧 base 拉分支取编号，先合入的占号。')
  console.error('解决：')
  console.error('  1. git fetch origin main && git rebase origin/main')
  console.error('  2. 重新取下一个可用编号: §' + (originMax + 1))
  console.error('  3. 检查内容是否已被 origin/main 覆盖（避免重复归档）')
  console.error('  4. 如已重复，关闭本 PR')
  console.error('')
  console.error('详见 docs/lessons/lessons-learned.md §72（分支基线过期导致 PR diff 静默回退/删除他人文件）')
  console.error('逃生阀：LESSONS_SECTION_CONFLICT_SKIP=1')
  return 1
}

const exitCode = main()
if (import.meta.url === `file://${process.argv[1]}`) {
  process.exit(exitCode)
}
