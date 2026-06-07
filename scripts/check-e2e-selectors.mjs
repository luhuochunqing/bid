// Input: staged e2e/*.spec.js files from git index
// Output: flags fragile Playwright selectors that risk strict-mode violations in CI
// Pos: scripts/ — E2E selector quality guardrail
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'

const WARNING = '\x1b[33mWARN\x1b[0m'

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

const FRAGILE_PATTERNS = [
  {
    id: 'bare-text-locator',
    regex: new RegExp("locator\\('text=[^)]+'\\)", 'g'),
    severity: 'error',
    message: "avoid bare text= locator \u2014 use getByRole/getByText/getByTestId. Bare text= is fragile against whitespace/translation changes.",
  },
  {
    id: 'bare-css-class-locator',
    regex: new RegExp("locator\\('\\.(?!(?:el-|data-testid|test-))[a-zA-Z][a-zA-Z0-9_-]*'\\)", 'g'),
    severity: 'warn',
    message: "CSS class-only locators (non-Element Plus) are fragile \u2014 prefer data-testid or getByRole for stability.",
  },
  {
    id: 'unscoped-nth',
    regex: new RegExp("locator\\([^)]+\\)\\.(?:nth|first|last)\\([^)]*\\)", 'g'),
    severity: 'warn',
    message: "unscoped .nth()/.first()/.last() can break when DOM order changes \u2014 scope to a parent container first.",
  },
  {
    id: 'page-dollar-selector',
    regex: new RegExp("page\\.\\$\\(", 'g'),
    severity: 'error',
    message: "page.$() is discouraged \u2014 use page.locator() instead.",
  },
  {
    id: 'long-getByText',
    regex: new RegExp("getByText\\(['\\\"][^'\\\"]{15,}['\\\"]\\)", 'g'),
    severity: 'warn',
    message: "getByText with long text is fragile \u2014 use getByTestId or getByRole with a name pattern.",
  },
  {
    id: 'getByText-strict-risk',
    regex: new RegExp("getByText\\(['\\\"][^'\\\"]+['\\\"]\\)(?!\\.(?:first|last|nth)\\([^)]*\\))", 'g'),
    severity: 'warn',
    message: "getByText() without .first()/.nth() may cause strict mode violation if the text also appears in breadcrumbs. Use getByRole(\"heading\") or add .first().",
  },
  {
    id: 'waitForTimeout',
    regex: new RegExp("\\.waitForTimeout\\(", 'g'),
    severity: 'error',
    message: "page.waitForTimeout() is timing-based and fragile \u2014 use waitForResponse(), waitForSelector() or waitForURL() instead.",
  },
  {
    id: 'networkidle-wait',
    regex: new RegExp("waitForLoadState\\('networkidle'\\)", 'g'),
    severity: 'error',
    message: "waitForLoadState('networkidle') is fragile in CI \u2014 use waitForResponse() for specific API calls or waitForSelector() for DOM elements.",
  },
  {
    id: 'waitForLoadState-generic',
    regex: new RegExp("\\.waitForLoadState\\(", 'g'),
    severity: 'warn',
    message: "waitForLoadState() is timing-based \u2014 prefer targeted waits like waitForResponse() or waitForSelector().",
  },
  {
    id: 'waitForNavigation',
    regex: new RegExp("\\.waitForNavigation\\(", 'g'),
    severity: 'warn',
    message: "page.waitForNavigation() is fragile \u2014 prefer page.waitForURL() which is more predictable.",
  },
  {
    id: 'getByRole-without-name',
    regex: new RegExp("getByRole\\(['\\\"][^'\\\"]+['\\\"]\\)(?!\\s*\\.)", 'g'),
    severity: 'warn',
    message: "getByRole() without { name: ... } may match multiple elements \u2014 add a name filter for stability.",
  },
  {
    id: 'bare-locator-body',
    regex: new RegExp("locator\\('body'\\)", 'g'),
    severity: 'warn',
    message: "locator('body') is too broad \u2014 scope to a specific container like locator('.app-main').",
  },
]

function checkFile(filePath, repoRoot) {
  const fullPath = path.join(repoRoot, filePath)
  let content
  try {
    content = fs.readFileSync(fullPath, 'utf8')
  } catch {
    return []
  }

  const issues = []
  for (const pattern of FRAGILE_PATTERNS) {
    pattern.regex.lastIndex = 0
    let match
    while ((match = pattern.regex.exec(content)) !== null) {
      const lineNum = content.substring(0, match.index).split('\n').length
      const matched = match[0].length > 60 ? match[0].substring(0, 57) + '...' : match[0]
      issues.push({
        file: filePath,
        line: lineNum,
        severity: pattern.severity,
        id: pattern.id,
        match: matched,
        message: pattern.message,
      })
    }
  }

  return issues
}

function getTargetFiles(repoRoot) {
  const explicitArgs = process.argv.slice(2).filter(Boolean)
  if (explicitArgs.length > 0) {
    return explicitArgs
      .map(input => path.isAbsolute(input) ? input : path.join(repoRoot, input))
      .flatMap(fullPath => {
        if (!fs.existsSync(fullPath)) return []
        const stat = fs.statSync(fullPath)
        if (stat.isDirectory()) {
          return fs.readdirSync(fullPath)
            .filter(name => name.endsWith('.spec.js'))
            .map(name => path.relative(repoRoot, path.join(fullPath, name)))
        }
        return [path.relative(repoRoot, fullPath)]
      })
      .filter(file => file.startsWith('e2e/') && file.endsWith('.spec.js'))
  }

  return getStagedFiles(repoRoot).filter(f => f.startsWith('e2e/') && f.endsWith('.spec.js'))
}

function main() {
  const repoRoot = process.cwd()
  const stagedFiles = getTargetFiles(repoRoot)

  if (stagedFiles.length === 0) {
    console.log('e2e-selectors: no staged E2E specs, skipping.')
    process.exit(0)
  }

  const allIssues = stagedFiles.flatMap(f => checkFile(f, repoRoot))

  if (allIssues.length === 0) {
    console.log(`e2e-selectors: passed. checked_files=${stagedFiles.length}`)
    process.exit(0)
  }

  const errors = allIssues.filter(i => i.severity === 'error')
  const warnings = allIssues.filter(i => i.severity === 'warn')

  if (warnings.length > 0) {
    console.error(`${WARNING} e2e-selectors: ${warnings.length} warning(s) in staged E2E specs:`)
    for (const issue of warnings) {
      console.error(`  ${issue.file}:${issue.line} [${issue.id}] ${issue.message}`)
      console.error(`    \u2192 ${issue.match}`)
    }
  }

  if (errors.length > 0) {
    console.error(`\ne2e-selectors: ${errors.length} error(s) found \u2014 blocking commit:`)
    for (const issue of errors) {
      console.error(`  ${issue.file}:${issue.line} [${issue.id}] ${issue.message}`)
      console.error(`    \u2192 ${issue.match}`)
    }
    process.exit(1)
  }

  console.log(`e2e-selectors: passed with ${warnings.length} warning(s). checked_files=${stagedFiles.length}`)
  process.exit(0)
}

main()