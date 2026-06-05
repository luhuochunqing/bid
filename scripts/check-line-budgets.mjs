// Input: git diff scope, line-budget config, and tracked source contents
// Output: ratcheting line-budget violations for guarded source files
// Pos: scripts/ - Core source line-budget guardrail
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const scriptFile = fileURLToPath(import.meta.url)
const scriptDir = path.dirname(scriptFile)
const defaultConfigPath = path.join(scriptDir, 'line-budget.config.json')

export function loadConfig(configPath = defaultConfigPath) {
  return JSON.parse(fs.readFileSync(configPath, 'utf8'))
}

export function countLines(content) {
  if (content.length === 0) {
    return 0
  }
  const newlineCount = content.match(/\n/g)?.length ?? 0
  return content.endsWith('\n') ? newlineCount : newlineCount + 1
}

export function isGuardedPath(filePath, config) {
  const extension = path.extname(filePath)
  if (!config.includeExtensions.includes(extension)) {
    return false
  }
  if (!config.includePrefixes.some((prefix) => filePath.startsWith(prefix))) {
    return false
  }
  if (config.excludePrefixes.some((prefix) => filePath.startsWith(prefix))) {
    return false
  }
  if (config.excludeFiles.includes(filePath)) {
    return false
  }
  if (config.excludeSuffixes.some((suffix) => filePath.endsWith(suffix))) {
    return false
  }
  return true
}

export function parseNameStatusLine(line) {
  if (!line.trim()) {
    return null
  }

  const fields = line.split('\t')
  const rawStatus = fields[0]
  const statusCode = rawStatus[0]

  if (statusCode === 'R' && fields.length >= 3) {
    return {
      statusCode,
      oldPath: fields[1],
      newPath: fields[2],
    }
  }

  if (statusCode === 'C' && fields.length >= 3) {
    return {
      statusCode,
      oldPath: null,
      newPath: fields[2],
    }
  }

  if (fields.length >= 2) {
    return {
      statusCode,
      oldPath: statusCode === 'M' ? fields[1] : null,
      newPath: fields[1],
    }
  }

  return null
}

export function classifyBudgetChange({ maxLines, oldLines, newLines }) {
  if (newLines <= maxLines) {
    return null
  }
  if (oldLines == null) {
    return 'NEW_OVER_LIMIT'
  }
  if (oldLines <= maxLines) {
    return 'CROSSED_LIMIT'
  }
  if (newLines > oldLines) {
    return 'GREW_WHILE_OVER_LIMIT'
  }
  return null
}

function runGit(repoRoot, args) {
  return execFileSync('git', ['-C', repoRoot, ...args], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  })
}

function readGitObject(repoRoot, objectSpec) {
  try {
    return runGit(repoRoot, ['show', objectSpec])
  } catch {
    return null
  }
}

function parseArgs(argv) {
  const options = {
    staged: false,
    workingTree: false,
    base: null,
    head: 'HEAD',
  }

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index]
    if (arg === '--staged') {
      options.staged = true
      continue
    }
    if (arg === '--working-tree') {
      options.workingTree = true
      continue
    }
    if (arg === '--base') {
      options.base = argv[index + 1]
      index += 1
      continue
    }
    if (arg === '--head') {
      options.head = argv[index + 1]
      index += 1
      continue
    }
    if (arg === '--help') {
      console.log('Usage: node scripts/check-line-budgets.mjs [--working-tree] [--staged] [--base <rev>] [--head <rev>]')
      process.exit(0)
    }
    throw new Error(`Unknown argument: ${arg}`)
  }

  if (options.staged && options.workingTree) {
    throw new Error('--staged and --working-tree cannot be combined')
  }
  if ((options.staged || options.workingTree) && (options.base || options.head !== 'HEAD')) {
    throw new Error('--staged/--working-tree cannot be combined with --base/--head')
  }
  if (options.head !== 'HEAD' && !options.base) {
    throw new Error('--head requires --base')
  }
  if (!options.staged && !options.workingTree && !options.base) {
    options.workingTree = true
  }

  return options
}

function collectDiffEntries(repoRoot, options) {
  const trackedArgs = options.staged
    ? ['diff', '--cached', '--name-status', '--find-renames', '--diff-filter=ACMR']
    : options.workingTree
      ? ['diff', '--name-status', '--find-renames', '--diff-filter=ACMR', 'HEAD']
      : ['diff', '--name-status', '--find-renames', '--diff-filter=ACMR', options.base, options.head]

  const trackedOutput = runGit(repoRoot, trackedArgs).trim()
  const trackedEntries = trackedOutput
    ? trackedOutput
        .split('\n')
        .map(parseNameStatusLine)
        .filter(Boolean)
    : []

  if (!options.workingTree) {
    return trackedEntries
  }

  const untrackedOutput = runGit(repoRoot, ['ls-files', '--others', '--exclude-standard']).trim()
  if (!untrackedOutput) {
    return trackedEntries
  }

  const seenPaths = new Set(trackedEntries.map((entry) => entry.newPath))
  const untrackedEntries = untrackedOutput
    .split('\n')
    .filter(Boolean)
    .filter((filePath) => !seenPaths.has(filePath))
    .map((filePath) => ({
      statusCode: 'A',
      oldPath: null,
      newPath: filePath,
    }))

  return [...trackedEntries, ...untrackedEntries]
}

function readOldContent(repoRoot, entry, options, config) {
  if (entry.statusCode !== 'M' && entry.statusCode !== 'R') {
    return null
  }
  if (!entry.oldPath || !isGuardedPath(entry.oldPath, config)) {
    return null
  }

  const objectSpec = options.staged || options.workingTree
    ? `HEAD:${entry.oldPath}`
    : `${options.base}:${entry.oldPath}`

  return readGitObject(repoRoot, objectSpec)
}

function readNewContent(repoRoot, entry, options) {
  if (options.staged) {
    return readGitObject(repoRoot, `:${entry.newPath}`)
  }
  if (options.workingTree) {
    try {
      return fs.readFileSync(path.join(repoRoot, entry.newPath), 'utf8')
    } catch {
      return null
    }
  }
  return readGitObject(repoRoot, `${options.head}:${entry.newPath}`)
}

function evaluateDiff(repoRoot, entries, options, config) {
  const results = []

  for (const entry of entries) {
    if (!isGuardedPath(entry.newPath, config)) {
      continue
    }

    const newContent = readNewContent(repoRoot, entry, options)
    if (newContent == null) {
      continue
    }

    const oldContent = readOldContent(repoRoot, entry, options, config)
    const newLines = countLines(newContent)
    const oldLines = oldContent == null ? null : countLines(oldContent)
    const reason = classifyBudgetChange({
      maxLines: config.maxLines,
      oldLines,
      newLines,
    })

    if (reason) {
      results.push({
        path: entry.newPath,
        reason,
        oldLines,
        newLines,
      })
    }
  }

  return results.sort((left, right) => left.path.localeCompare(right.path))
}

function formatReason(reason) {
  if (reason === 'NEW_OVER_LIMIT') {
    return 'new file exceeds the line budget'
  }
  if (reason === 'CROSSED_LIMIT') {
    return 'file crossed the line budget in this change'
  }
  return 'historically oversized file grew again'
}

function main() {
  const repoRoot = process.cwd()
  const config = loadConfig()
  const options = parseArgs(process.argv.slice(2))

  const entries = collectDiffEntries(repoRoot, options)
  const violations = evaluateDiff(repoRoot, entries, options, config)

  if (violations.length > 0) {
    console.error(`line-budget check failed (limit=${config.maxLines}):`)
    for (const violation of violations) {
      const oldDisplay = violation.oldLines == null ? 'NEW' : String(violation.oldLines)
      console.error(
        `- [${violation.reason}] ${violation.path} (${oldDisplay} -> ${violation.newLines} lines): ${formatReason(violation.reason)}`
      )
    }
    process.exit(1)
  }

  console.log(`line-budget check passed. guarded_changes=${entries.filter((entry) => isGuardedPath(entry.newPath, config)).length}`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === scriptFile) {
  try {
    main()
  } catch (error) {
    console.error(`line-budget: ${error.message}`)
    process.exit(1)
  }
}
