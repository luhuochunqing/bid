#!/usr/bin/env node
// Input: wiki pages with violations
// Output: auto-fix suggestions and optional auto-fix execution
// Pos: scripts/ - Doc-gardening tool for automated wiki maintenance
// 维护声明: 当 wiki:check 报错时，运行此脚本获取修复建议或自动修复。

import fs from 'node:fs'
import path from 'node:path'
import {
  catalogRoot,
  ensureWikiDirs,
  parseFrontmatter,
  readJsonOrDefault,
  relToRepo,
  walkFiles,
  wikiRoot,
} from './wiki-common.mjs'

const sourceCatalogJson = path.join(catalogRoot, 'source-catalog.json')
const pageCatalogJson = path.join(catalogRoot, 'page-catalog.json')

const requiredFrontmatterFields = ['title', 'space', 'category', 'sources', 'updated', 'health_checked']

function todayStr() {
  return new Date().toISOString().slice(0, 10)
}

function dateDaysDiff(dateText) {
  const date = new Date(dateText)
  if (Number.isNaN(date.getTime())) return Number.POSITIVE_INFINITY
  return Math.floor((Date.now() - date.getTime()) / (24 * 60 * 60 * 1000))
}

function existsInRepo(repoRelativePath) {
  if (!repoRelativePath) return false
  return fs.existsSync(path.join(process.cwd(), repoRelativePath))
}

function fixFrontmatter(pageAbsPath, content, dryRun) {
  const { frontmatter, body } = parseFrontmatter(content)
  let fixed = content
  let changed = false

  // Fix missing health_checked
  if (!frontmatter.health_checked) {
    const today = todayStr()
    if (fixed.startsWith('---')) {
      fixed = fixed.replace('---', `---\nhealth_checked: ${today}`)
    } else {
      fixed = `---\nhealth_checked: ${today}\n---\n${fixed}`
    }
    changed = true
  }

  // Fix stale health_checked
  if (frontmatter.health_checked && dateDaysDiff(frontmatter.health_checked) > 7) {
    const today = todayStr()
    fixed = fixed.replace(
      new RegExp(`health_checked:\\s*${frontmatter.health_checked}`),
      `health_checked: ${today}`
    )
    changed = true
  }

  // Fix missing updated
  if (!frontmatter.updated) {
    const today = todayStr()
    if (fixed.startsWith('---')) {
      fixed = fixed.replace('---', `---\nupdated: ${today}`)
    } else {
      fixed = `---\nupdated: ${today}\n---\n${fixed}`
    }
    changed = true
  }

  // Note on stale updated (>30 days):
  // According to wiki-check.mjs & .wiki/WIKI.md §3.4:
  // An updated date > 30 days is only stale if health_checked is ALSO stale (> 7 days).
  // Once health_checked is refreshed to today, updated > 30 days is valid (content reviewed).
  // We deliberately do NOT overwrite updated when content hasn't changed.

  if (changed && !dryRun) {
    fs.writeFileSync(pageAbsPath, fixed, 'utf8')
  }

  return changed
}

function fixMissingSources(pageAbsPath, content, dryRun) {
  const { frontmatter } = parseFrontmatter(content)
  if (!Array.isArray(frontmatter.sources)) return false

  const missingSources = frontmatter.sources.filter(s => !existsInRepo(s))
  if (missingSources.length === 0) return false

  let fixed = content
  for (const source of missingSources) {
    // Remove missing source from list
    fixed = fixed.replace(new RegExp(`\\s*-\\s*${source.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\n?`), '\n')
  }

  // Clean up empty sources array
  fixed = fixed.replace(/sources:\s*\n\s*\n/s, 'sources: []\n')

  if (!dryRun) {
    fs.writeFileSync(pageAbsPath, fixed, 'utf8')
  }

  return true
}

function main() {
  const args = process.argv.slice(2)
  const dryRun = args.includes('--dry-run')
  const autoFix = args.includes('--auto-fix')

  if (!autoFix && !dryRun) {
    console.log('Usage: node scripts/wiki-fix.mjs [--auto-fix] [--dry-run]')
    console.log('')
    console.log('Options:')
    console.log('  --auto-fix    Automatically fix violations where possible')
    console.log('  --dry-run     Show what would be fixed without making changes')
    console.log('')
    console.log('Run "npm run wiki:check" first to see violations.')
    process.exit(0)
  }

  ensureWikiDirs()

  const pageAbsPaths = walkFiles(path.join(wikiRoot, 'pages')).filter(p => p.endsWith('.md'))
  let fixedCount = 0
  let report = []

  for (const pageAbsPath of pageAbsPaths) {
    const content = fs.readFileSync(pageAbsPath, 'utf8')
    const relPath = relToRepo(pageAbsPath)
    let pageFixed = false

    // Fix frontmatter issues
    if (fixFrontmatter(pageAbsPath, content, dryRun)) {
      report.push(`${relPath}: fixed frontmatter dates`)
      pageFixed = true
    }

    // Fix missing sources
    if (fixMissingSources(pageAbsPath, content, dryRun)) {
      report.push(`${relPath}: removed missing source references`)
      pageFixed = true
    }

    if (pageFixed) fixedCount++
  }

  if (report.length === 0) {
    console.log('No auto-fixable violations found.')
  } else {
    console.log(`${dryRun ? 'Would fix' : 'Fixed'} ${report.length} issue(s):\n`)
    for (const msg of report) {
      console.log(`  ✓ ${msg}`)
    }
    if (dryRun) {
      console.log('\nRun with --auto-fix to apply changes.')
    }
  }
}

main()
