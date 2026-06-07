// Input: staged *.spec.js files under src/ from git index
// Output: flags missing Pinia initialization and fragile Element Plus selectors
// Pos: scripts/ — Vue test boilerplate guardrail
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'

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

// Detects if the test file likely depends on Pinia stores
function usesPiniaStore(content) {
  // Direct pinia package import
  if (/import\s+.*\s+from\s+['"]pinia['"]/.test(content)) return true
  // Pinia-based store imports (these use defineStore from pinia)
  if (/import\s+.*\s+from\s+['"]@\/stores\//.test(content)) return true
  if (/import\s+.*\s+from\s+['"][^'"]*store[^'"]*['"]/.test(content)) return true
  // Direct API calls
  if (/useStore\(\)/.test(content)) return true
  if (/defineStore\(/.test(content)) return true
  return false
}

// Check if file uses Element Plus input-number component
function usesElInputNumber(content) {
  return /el-input-number/i.test(content)
    || /ElInputNumber/i.test(content)
}

function checkBoilerplate(filePath, repoRoot) {
  const fullPath = path.join(repoRoot, filePath)
  let content
  try {
    content = fs.readFileSync(fullPath, 'utf8')
  } catch {
    return []
  }

  const issues = []

  // Rule 1: Pinia-dependent tests must initialize store in beforeEach
  if (usesPiniaStore(content)) {
    const hasPiniaInit = /setActivePinia\s*\(\s*createPinia\s*\(\s*\)\s*\)/.test(content)
    if (!hasPiniaInit) {
      const hasBeforeEach = /beforeEach\s*\(/.test(content)
      if (hasBeforeEach) {
        issues.push({
          file: filePath,
          severity: 'error',
          id: 'missing-pinia-init',
          message: 'Test file uses Pinia stores but is missing setActivePinia(createPinia()) in beforeEach. This will cause "Pinia not installed" errors.',
          fix: 'Add to beforeEach: import { setActivePinia, createPinia } from \'pinia\'; setActivePinia(createPinia());',
        })
      } else {
        issues.push({
          file: filePath,
          severity: 'error',
          id: 'missing-pinia-init-and-beforeeach',
          message: 'Test file uses Pinia stores but has no beforeEach block with setActivePinia(createPinia()).',
          fix: 'Add beforeEach with Pinia initialization: setActivePinia(createPinia());',
        })
      }
    }
  }

  // Rule 2: Element Plus input-number requires component-aware selectors
  if (usesElInputNumber(content)) {
    const hasInputNumberSelector = /el-input-number|ElInputNumber|input\[role="spinbutton"\]/.test(content)
    if (!hasInputNumberSelector) {
      issues.push({
        file: filePath,
        severity: 'warn',
        id: 'el-input-number-selector',
        message: 'Test uses el-input-number but may lack a component-aware selector. Generic input selectors often fail on el-input-number.',
        fix: 'Use wrapper.findComponent({ name: \'ElInputNumber\' }) or input[role="spinbutton"]',
      })
    }
  }

  // Rule 3: Reminder to inspect component interface before mounting
  const importsComponent = /import\s+\w+\s+from\s+['"]\.\.\/.*\.vue['"]/.test(content)
  if (importsComponent && !content.includes('props:')) {
    issues.push({
      file: filePath,
      severity: 'warn',
      id: 'missing-props-inspection',
      message: 'Test mounts a Vue component — verify props, emits, and expose interfaces match the actual component API.',
      fix: 'Review the component\'s defineProps/defineEmits/defineExpose before writing assertions.',
    })
  }

  return issues
}

function main() {
  const repoRoot = process.cwd()
  const stagedFiles = getStagedFiles(repoRoot).filter(f =>
    f.startsWith('src/') && (f.endsWith('.spec.js') || f.endsWith('.spec.ts'))
  )

  if (stagedFiles.length === 0) {
    console.log('vue-test-boilerplate: no staged test specs, skipping.')
    process.exit(0)
  }

  const allIssues = stagedFiles.flatMap(f => checkBoilerplate(f, repoRoot))

  if (allIssues.length === 0) {
    console.log(`vue-test-boilerplate: passed. checked_files=${stagedFiles.length}`)
    process.exit(0)
  }

  const errors = allIssues.filter(i => i.severity === 'error')
  const warnings = allIssues.filter(i => i.severity === 'warn')

  if (warnings.length > 0) {
    console.error(`\x1b[33mWARN\x1b[0m vue-test-boilerplate: ${warnings.length} warning(s):`)
    for (const issue of warnings) {
      console.error(`  ${issue.file} [${issue.id}] ${issue.message}`)
      if (issue.fix) console.error(`    → fix: ${issue.fix}`)
    }
  }

  if (errors.length > 0) {
    console.error(`\nvue-test-boilerplate: ${errors.length} error(s) found — blocking commit:`)
    for (const issue of errors) {
      console.error(`  ${issue.file} [${issue.id}] ${issue.message}`)
      if (issue.fix) console.error(`    → fix: ${issue.fix}`)
    }
    process.exit(1)
  }

  console.log(`vue-test-boilerplate: passed with ${warnings.length} warning(s). checked_files=${stagedFiles.length}`)
  process.exit(0)
}

main()
