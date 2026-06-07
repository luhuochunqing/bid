// Input: line-budget.config.json + all tracked source files via git ls-files
// Output: report of files exceeding the line budget (300 lines), categorized as VIOLATION / EXEMPT
// Pos: scripts/ — Periodic oversized file scanner (full-repo, not diff-based)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
// Unlike check-line-budgets.mjs which operates on git diffs (staged / PR range),
// this scanner examines ALL tracked source files. It serves two purposes:
//   1. Catch oversized files that snuck in before the line-budget gate existed.
//   2. Audit the exemption list — exempt files that have shrunk below 300 lines
//      should be removed from the list so future growth is caught.
//
// Exit codes:
//   0 — no violations (exempt entries are reported but don't fail)
//   1 — one or more non-exempt oversized files found
//   2 — config or runtime error

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptFile = fileURLToPath(import.meta.url)
const scriptDir = path.dirname(scriptFile)
const defaultConfigPath = path.join(scriptDir, 'line-budget.config.json')

// ── helpers ─────────────────────────────────────────────────────────────────

function runGit(repoRoot, args) {
  return execFileSync('git', ['-C', repoRoot, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function countLines(content) {
  if (content.length === 0) return 0
  const newlineCount = (content.match(/\n/g) || []).length
  return content.endsWith('\n') ? newlineCount : newlineCount + 1
}

function isGuardedPath(filePath, config) {
  const ext = path.extname(filePath)
  if (!config.includeExtensions.includes(ext)) return false
  if (!config.includePrefixes.some(p => filePath.startsWith(p))) return false
  if (config.excludePrefixes.some(p => filePath.startsWith(p))) return false
  if (config.excludeSuffixes.some(s => filePath.endsWith(s))) return false
  return true
}

// ── main ────────────────────────────────────────────────────────────────────

function main() {
  const args = process.argv.slice(2)
  const jsonOutput = args.includes('--json')
  const quiet = args.includes('--quiet')
  const configPath = args.includes('--config')
    ? args[args.indexOf('--config') + 1]
    : defaultConfigPath

  if (!fs.existsSync(configPath)) {
    console.error(`scan-oversized: config not found at ${configPath}`)
    process.exit(2)
  }

  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'))
  const repoRoot = process.cwd()
  const maxLines = config.maxLines || 300

  // 1) Get all tracked source files
  const allFiles = runGit(repoRoot, ['ls-files'])
    .trim()
    .split('\n')
    .filter(Boolean)

  const guardedFiles = allFiles.filter(f => isGuardedPath(f, config))

  // 2) Count lines for each file
  const oversized = []
  for (const filePath of guardedFiles) {
    try {
      const content = fs.readFileSync(path.join(repoRoot, filePath), 'utf8')
      const lines = countLines(content)
      if (lines > maxLines) {
        const isExempt = config.excludeFiles.includes(filePath)
        oversized.push({ path: filePath, lines, exempt: isExempt })
      }
    } catch {
      // skip unreadable files
    }
  }

  oversized.sort((a, b) => b.lines - a.lines)

  // 3) Also find exempt entries that are now under budget (can be cleaned up)
  const staleExemptions = config.excludeFiles.filter(exemptPath => {
    if (!guardedFiles.includes(exemptPath)) return false
    try {
      const content = fs.readFileSync(path.join(repoRoot, exemptPath), 'utf8')
      return countLines(content) <= maxLines
    } catch {
      return false
    }
  })

  const violations = oversized.filter(f => !f.exempt)
  const exemptEntries = oversized.filter(f => f.exempt)

  // 4) Output
  if (jsonOutput) {
    console.log(JSON.stringify({
      maxLines,
      totalGuarded: guardedFiles.length,
      oversizedCount: oversized.length,
      violationCount: violations.length,
      exemptCount: exemptEntries.length,
      staleExemptionCount: staleExemptions.length,
      violations,
      exemptEntries,
      staleExemptions,
    }, null, 2))
    process.exit(violations.length > 0 ? 1 : 0)
  }

  // Human-readable output
  if (!quiet) {
    console.log(`scan-oversized: scanned ${guardedFiles.length} guarded files, limit=${maxLines} lines`)
    console.log(`  oversized: ${oversized.length} | violations: ${violations.length} | exempt: ${exemptEntries.length}`)

    if (violations.length > 0) {
      console.log(`\n── VIOLATIONS (${violations.length}) ──`)
      for (const v of violations) {
        console.log(`  ${v.lines.toString().padStart(5)} lines  ${v.path}`)
      }
    }

    if (exemptEntries.length > 0) {
      console.log(`\n── EXEMPT (${exemptEntries.length}) — in baseline, for audit only ──`)
      for (const e of exemptEntries) {
        console.log(`  ${e.lines.toString().padStart(5)} lines  ${e.path}`)
      }
    }

    if (staleExemptions.length > 0) {
      console.log(`\n── STALE EXEMPTIONS (${staleExemptions.length}) — now ≤${maxLines} lines, can be removed ──`)
      for (const s of staleExemptions) {
        console.log(`  ${s}`)
      }
    }

    if (violations.length === 0 && oversized.length === 0) {
      console.log(`\n  ✓ All ${guardedFiles.length} guarded files are within the ${maxLines}-line budget.`)
    }
  }

  process.exit(violations.length > 0 ? 1 : 0)
}

main()
