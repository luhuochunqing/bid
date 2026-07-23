#!/usr/bin/env node
// Input: wiki pages with stale health_checked/updated dates
// Output: batch-update health_checked for historical archives, leave updated unchanged for A-class
// Pos: scripts/ - One-shot tool for存量 wiki violations cleanup (2026-07-23)
// 维护声明: 一次性工具，处理 2026-07-23 存量违规。用完即可删除或保留作示例。

import fs from 'node:fs'
import path from 'node:path'

const repoRoot = process.cwd()
const wikiPagesRoot = path.join(repoRoot, '.wiki', 'pages')

// A 类：历史档案，内容是历史记录不会变。只更新 health_checked，保留 updated。
const HISTORICAL_ARCHIVES = [
  'implementation/sow-2026-v1-4.md',
  'implementation/milestones.md',
  'implementation/acceptance-and-closure.md',
  'implementation/risk-register.md',
  'implementation/weekly-status.md',
  'implementation/delivery-playbook.md',
  'implementation/document-delivery-ledger.md',
  'implementation/attachment4-gap-matrix.md',
  'implementation/attachment4-requirement-task-book.md',
  'implementation/attachment6-function-list-trace.md',
  'implementation/development-sprint-2026-05-23.md',
  'implementation/org-sdk-deployment-handoff.md',
  'implementation/xiyu-pending-confirmations.md',
  'contract-constraints.md',
  'requirements.md',
  'team-and-timeline.md',
  'glossary.md',
  'lessons-learned.md',
  'lessons-learned/CO-361-five-rounds-no-fix.md',
  'root-cause-analysis-ijssgg.md',
  'multi-agent-defense-playbook.md',
]

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function updateHealthChecked(content, today) {
  // 匹配 frontmatter 中的 health_checked: YYYY-MM-DD
  const pattern = /health_checked:\s*(\d{4}-\d{2}-\d{2})/
  if (!pattern.test(content)) {
    return { changed: false, content }
  }
  let newContent = content.replace(pattern, `health_checked: ${today}`)

  // 如果 frontmatter 没有 archive: true，加上
  // 历史档案标记：允许 updated >30 天，但仍需 health_checked
  if (!/^archive:\s*true\s*$/m.test(newContent)) {
    // 在 health_checked 行后面加 archive: true
    newContent = newContent.replace(
      /(health_checked:\s*\d{4}-\d{2}-\d{2})/,
      `$1\narchive: true`
    )
  }

  return { changed: true, content: newContent }
}

function main() {
  const args = process.argv.slice(2)
  const dryRun = args.includes('--dry-run')
  const today = todayStr()

  console.log(`# Wiki 存量回填 ${dryRun ? '[DRY RUN]' : '[EXECUTE]'}`)
  console.log(`# 日期: ${today}`)
  console.log(`# 分类: A 类历史档案（只改 health_checked，保留 updated）`)
  console.log('')

  let changed = 0
  let skipped = 0

  for (const relPath of HISTORICAL_ARCHIVES) {
    const absPath = path.join(wikiPagesRoot, relPath)
    if (!fs.existsSync(absPath)) {
      console.log(`  ⚠ 跳过（文件不存在）: ${relPath}`)
      skipped++
      continue
    }
    const content = fs.readFileSync(absPath, 'utf8')
    const { changed: didChange, content: newContent } = updateHealthChecked(content, today)
    if (!didChange) {
      console.log(`  ⚠ 跳过（无 health_checked 字段）: ${relPath}`)
      skipped++
      continue
    }
    if (!dryRun) {
      fs.writeFileSync(absPath, newContent, 'utf8')
    }
    console.log(`  ✓ ${relPath}`)
    changed++
  }

  console.log('')
  console.log(`总计: ${changed} 个文件更新 health_checked, ${skipped} 个跳过`)
  if (dryRun) {
    console.log('Run without --dry-run to apply changes.')
  }
}

main()
