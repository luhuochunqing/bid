#!/usr/bin/env node
// Input: .wiki/pages/ 全量页面
// Output: 仅对 archive: true 的历史档案页批量刷新 health_checked（不改 updated）；非 archive 页一律拒绝
// Pos: scripts/ - Archive-only health_checked backfill（2026-10-26 收紧适用范围）
// 维护声明: 活跃文档（非 archive）的 health_checked 只能来自逐页人工核实（走正常 Edit/PR 流程），
//   本脚本不允许批量刷新——否则会架空 WIKI.md §3.4 的"health_checked 7 天内豁免"条款。
//   真实事故：2026-10-25 commit 111803b91 用旧版硬编码清单批量刷 133 页，
//   掩盖了 41 页 74 天未核实的内容（wiki:check 全绿但实质过期）。改动本脚本前先读这段。

import fs from 'node:fs'
import path from 'node:path'

const repoRoot = process.cwd()
const wikiPagesRoot = path.join(repoRoot, '.wiki', 'pages')

function todayStr() {
  // 本地日期（东八区），不能用 toISOString()——那是 UTC，每天有 8 小时会写成"昨天"
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

// frontmatter 解析：只认文件头部的 --- ... --- 块，避免正文误伤
function parseFrontmatter(content) {
  const m = content.match(/^---\r?\n([\s\S]*?)\r?\n---/)
  return m ? m[1] : null
}

function isArchivePage(content) {
  const fm = parseFrontmatter(content)
  return fm !== null && /^archive:\s*true\s*$/m.test(fm)
}

function updateHealthChecked(content, today) {
  const pattern = /health_checked:\s*(\d{4}-\d{2}-\d{2})/
  if (!pattern.test(content)) {
    return { changed: false, content }
  }
  const newContent = content.replace(pattern, `health_checked: ${today}`)
  return { changed: true, content: newContent }
}

function listPageFiles(dir) {
  const out = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const abs = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      out.push(...listPageFiles(abs))
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      out.push(abs)
    }
  }
  return out
}

function main() {
  const args = process.argv.slice(2)
  const dryRun = args.includes('--dry-run')
  const today = todayStr()

  console.log(`# Wiki 存量回填 ${dryRun ? '[DRY RUN]' : '[EXECUTE]'}`)
  console.log(`# 日期: ${today}`)
  console.log(`# 范围: 仅 archive: true 历史档案页（活跃文档不允许批量刷 health_checked）`)
  console.log('')

  if (!fs.existsSync(wikiPagesRoot)) {
    console.log(`  ✗ 未找到 ${wikiPagesRoot}（请在仓库根目录运行）`)
    process.exit(1)
  }

  let changed = 0
  let refused = 0
  let skipped = 0

  for (const absPath of listPageFiles(wikiPagesRoot)) {
    const relPath = path.relative(wikiPagesRoot, absPath)
    const content = fs.readFileSync(absPath, 'utf8')

    if (!isArchivePage(content)) {
      console.log(`  ✗ 拒绝（非 archive 页面，需逐页人工核实）: ${relPath}`)
      refused++
      continue
    }

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
  console.log(`总计: ${changed} 个 archive 页更新 health_checked, ${refused} 个非 archive 页拒绝, ${skipped} 个跳过`)
  if (refused > 0) {
    console.log('活跃文档的 health_checked 只能逐页人工核实后更新，不允许批量刷新（见脚本头维护声明）。')
  }
  if (dryRun) {
    console.log('Run without --dry-run to apply changes.')
  }
}

main()
