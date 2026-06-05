// Input: staged files from git index
// Output: warns when files approach the line budget (200+ lines, budget=300)
// Pos: scripts/ — Line-budget early warning (non-blocking)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// This is a lightweight early-warning companion to check-line-budgets.mjs.
// It warns at 200 lines (2/3 of the 300-line budget) so developers can
// proactively split files before hitting the hard gate.

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptFile = fileURLToPath(import.meta.url)
const scriptDir = path.dirname(scriptFile)
const defaultConfigPath = path.join(scriptDir, 'line-budget.config.json')

const WARN_THRESHOLD = 200

function runGit(repoRoot, args) {
  return execFileSync('git', ['-C', repoRoot, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function getStagedFiles(repoRoot) {
  const output = runGit(repoRoot, ['diff', '--cached', '--name-only', '--diff-filter=ACMR'])
  return output.trim().split('\n').filter(Boolean)
}

function isGuarded(filePath, config) {
  const ext = path.extname(filePath)
  if (!config.includeExtensions.includes(ext)) return false
  if (!config.includePrefixes.some(p => filePath.startsWith(p))) return false
  if (config.excludePrefixes.some(p => filePath.startsWith(p))) return false
  if (config.excludeFiles.includes(filePath)) return false
  if (config.excludeSuffixes.some(s => filePath.endsWith(s))) return false
  return true
}

function main() {
  const repoRoot = process.cwd()
  const config = JSON.parse(fs.readFileSync(defaultConfigPath, 'utf8'))
  const staged = getStagedFiles(repoRoot).filter(f => isGuarded(f, config))

  const warnings = []
  for (const f of staged) {
    try {
      const content = fs.readFileSync(path.join(repoRoot, f), 'utf8')
      const lines = content.split('\n').length
      if (lines >= WARN_THRESHOLD && lines < config.maxLines) {
        warnings.push({ file: f, lines })
      }
    } catch { /* skip unreadable */ }
  }

  if (warnings.length === 0) {
    process.exit(0) // silent pass
  }

  for (const w of warnings) {
    console.error(`\x1b[33mline-budget-warn\x1b[0m: ${w.file} is at ${w.lines} lines (budget=${config.maxLines}, warn-at=${WARN_THRESHOLD})`)
    console.error(`  Consider splitting before it crosses the ${config.maxLines}-line hard gate.`)
  }
  // Non-blocking — warning only
  process.exit(0)
}

main()
