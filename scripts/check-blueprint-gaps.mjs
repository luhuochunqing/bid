// Input: git diff scope, docs/plans/ directory, and source files
// Output: BLOCKER if blueprint-touched files exist without a valid gap table
// Pos: scripts/ - Blueprint gap gate paired with check-line-budgets
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// THIS IS A GATE, NOT A SCANNER. It only checks files that are
// actually being changed in this commit. Existing gaps in old files
// are tracked but do not block new work.

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptFile = fileURLToPath(import.meta.url)

// Blueprint section patterns that require a gap table
const BLUEPRINT_PATTERNS = [
  /4\.2\.\d+/,    // §4.2.x — 标讯创建/列表/详情/...
  /4\.3\.\d+/,    // §4.3.x — 项目相关
  /4\.1\.\d+/,    // §4.1.x
  /§4\.\d+\.\d+/, // alternate notation
]

// Minimum required sections in a valid gap table
const REQUIRED_GAP_SECTIONS = [
  '## 1.',      // gap analysis table
  '## 3.',      // task breakdown
  '## 5.',      // TODO checklist
  '## 6.',      // acceptance criteria
]

// Patterns that indicate the gap table is "complete enough" (all fields checked)
const COMPLETION_MARKERS = [
  /\[[x✓✔]\]/,  // checked items like [x] or [✓]
  /\[[\u2713\u2714]\]/, // unicode checkmarks
]

function runGit(repoRoot, args) {
  try {
    return execFileSync('git', ['-C', repoRoot, ...args], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    })
  } catch {
    return ''
  }
}

function getChangedFiles(repoRoot, options) {
  const stagedArgs = options.staged
    ? ['diff', '--cached', '--name-status', '--diff-filter=ACMR']
    : ['diff', '--name-status', '--diff-filter=ACMR', 'HEAD']

  const output = runGit(repoRoot, stagedArgs).trim()
  if (!output) return []

  return output
    .split('\n')
    .filter(Boolean)
    .map(line => {
      const fields = line.split('\t')
      return fields[1] || fields[0]?.slice(1) || null
    })
    .filter(Boolean)
}

function isBlueprintTouched(filePath) {
  return BLUEPRINT_PATTERNS.some(pattern => pattern.test(filePath))
}

function findPlanFileForSection(repoRoot, sectionPattern, changedFiles) {
  // Look for plan files that match the section pattern
  const plansDir = path.join(repoRoot, 'docs', 'plans')
  if (!fs.existsSync(plansDir)) return null

  const files = fs.readdirSync(plansDir).filter(f => f.endsWith('.md'))
  const matchedFiles = files.filter(f => sectionPattern.test(f))
  if (matchedFiles.length > 0) {
    return path.join(plansDir, matchedFiles[0])
  }

  // Fallback: find plan file that has the same 4.x.x section in its name
  for (const file of files) {
    if (sectionPattern.test(file)) {
      return path.join(plansDir, file)
    }
  }

  // Fallback: find any plan file whose name appears in changed files
  // (e.g. tender-create-4.2.3-plan.md ←> src/views/Bidding/TenderCreatePage.vue)
  const changedSourceFiles = changedFiles.filter(f =>
    !f.startsWith('docs/') && !f.startsWith('e2e/')
  )
  for (const sourceFile of changedSourceFiles) {
    for (const planFile of files) {
      // Extract 4.x.x number from plan filename
      const sectionMatch = planFile.match(/4\.\d+\.\d+/)
      if (sectionMatch && sourceFile.includes(sectionMatch[0])) {
        return path.join(plansDir, planFile)
      }
    }
  }

  return null
}

function validateGapTable(planFilePath) {
  if (!fs.existsSync(planFilePath)) {
    return { valid: false, reason: 'MISSING', missing: 'plan file does not exist' }
  }

  const content = fs.readFileSync(planFilePath, 'utf8')

  // Check required sections exist
  const missingSections = REQUIRED_GAP_SECTIONS.filter(section => !content.includes(section))
  if (missingSections.length > 0) {
    return {
      valid: false,
      reason: 'INCOMPLETE',
      missing: `missing sections: ${missingSections.join(', ')}`
    }
  }

  // Check for gap analysis table (has markdown table structure)
  const hasTableStructure = content.includes('|') && content.includes('---')
  if (!hasTableStructure) {
    return {
      valid: false,
      reason: 'INCOMPLETE',
      missing: 'gap analysis table not found (expected markdown table with |---|---| structure)'
    }
  }

  // Check for TODO checklist with items
  const todoSection = content.match(/## 5\.[\s\S]*?(?=##|$)/)?.[0] || ''
  const todoItems = (todoSection.match(/\[ \]/g) || []).length
  const checkedItems = (todoSection.match(/\[[x✓✔]\]/g) || []).length
  if (todoItems === 0 && checkedItems === 0) {
    return {
      valid: false,
      reason: 'INCOMPLETE',
      missing: 'TODO checklist has no items'
    }
  }

  return { valid: true, reason: 'OK', todoItems, checkedItems }
}

function parseArgs(argv) {
  const options = {
    staged: false,
    workingTree: true,
    failOnMissing: true,
    verbose: false,
  }

  for (let i = 2; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--staged') {
      options.staged = true
      options.workingTree = false
    } else if (arg === '--staged') {
      options.staged = true
      options.workingTree = false
    } else if (arg === '--warn') {
      options.failOnMissing = false
    } else if (arg === '--verbose' || arg === '-v') {
      options.verbose = true
    } else if (arg === '--help') {
      console.log(`Usage: node scripts/check-blueprint-gaps.mjs [--staged] [--warn] [-v]
  --staged    check only staged files (default: working tree)
  --warn      warn instead of blocking on missing gap table
  -v          verbose output
Exit code: 0 = pass, 1 = block/warn, 2 = error`)
      process.exit(0)
    }
  }
  return options
}

function main() {
  const repoRoot = process.cwd()
  const options = parseArgs(process.argv)

  const changedFiles = getChangedFiles(repoRoot, options)

  if (options.verbose) {
    console.log(`check-blueprint-gaps: scanning ${changedFiles.length} changed file(s)`)
  }

  // Find blueprint-touched source files (exclude docs, e2e, test files)
  const blueprintTouched = changedFiles.filter(f =>
    isBlueprintTouched(f) &&
    !f.startsWith('docs/') &&
    !f.startsWith('e2e/') &&
    !f.endsWith('.spec.js') &&
    !f.endsWith('.test.js')
  )

  if (blueprintTouched.length === 0) {
    if (options.verbose) {
      console.log('check-blueprint-gaps: no blueprint-touched source files found, passing')
    }
    process.exit(0)
  }

  if (options.verbose) {
    console.log(`Blueprint-touched source files: ${blueprintTouched.join(', ')}`)
  }

  // Extract section numbers from changed files to find corresponding plan files
  const touchedSections = new Set()
  for (const file of blueprintTouched) {
    for (const pattern of BLUEPRINT_PATTERNS) {
      const match = file.match(pattern)
      if (match) touchedSections.add(match[0])
    }
  }

  const results = []
  for (const section of touchedSections) {
    const sectionPattern = new RegExp(section.replace(/\./g, '\\.'))
    const planFile = findPlanFileForSection(repoRoot, sectionPattern, blueprintTouched)
    const validation = validateGapTable(planFile || '')

    results.push({
      section,
      planFile,
      ...validation,
    })
  }

  const failures = results.filter(r => !r.valid)
  const passes = results.filter(r => r.valid)

  if (options.verbose) {
    for (const r of results) {
      const status = r.valid ? 'PASS' : 'FAIL'
      const section = `[${status}] §${r.section}`
      const plan = r.planFile ? `→ ${path.relative(repoRoot, r.planFile)}` : '→ (not found)'
      const detail = r.missing ? ` (${r.missing})` : ` (${r.checkedItems}/${r.todoItems} items checked)`
      console.log(`${section} ${plan}${detail}`)
    }
  }

  if (failures.length > 0) {
    console.error('check-blueprint-gaps: BLOCKED')
    for (const f of failures) {
      if (f.reason === 'MISSING') {
        console.error(`  ❌ §${f.section}: gap table file missing`)
        console.error(`     Expected: docs/plans/*${f.section}*-plan.md`)
        console.error(`     Action: create docs/plans/tender-*-${f.section}-plan.md with gap analysis`)
      } else if (f.reason === 'INCOMPLETE') {
        console.error(`  ❌ §${f.section}: gap table incomplete (${f.missing})`)
        console.error(`     File: ${f.planFile ? path.relative(repoRoot, f.planFile) : 'not found'}`)
      }
    }
    console.error('')
    console.error('Hint: Gap table must contain:')
    console.error('  ## 1. Gap analysis table (| field | 蓝图 | 当前 | 差距 |)')
    console.error('  ## 3. Task breakdown (TODO format)')
    console.error('  ## 5. Detailed checklist [ ] task items')
    console.error('  ## 6. Acceptance criteria')
    console.error('')
    console.error('Reference: see docs/plans/tender-create-4.2.3-plan.md as template')
    process.exit(options.failOnMissing ? 1 : 0)
  }

  // All passed
  const totalItems = passes.reduce((sum, r) => sum + (r.checkedItems || 0), 0)
  const totalTodos = passes.reduce((sum, r) => sum + (r.todoItems || 0), 0)
  console.log(`check-blueprint-gaps: passed §${[...touchedSections].join(', ')} (${totalItems}/${totalTodos} TODOs checked)`)
  process.exit(0)
}

try {
  main()
} catch (error) {
  console.error(`check-blueprint-gaps: internal error — ${error.message}`)
  process.exit(2)
}

export { validateGapTable, isBlueprintTouched, findPlanFileForSection }
