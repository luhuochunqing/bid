#!/usr/bin/env node
// Input: .agent-locks.yml, current branch context, and optional --days parameter
// Output: extends expiresAt for all locks owned by current branch
// Pos: scripts/Agent 锁续期工具
// 维护声明: 若锁字段或过期时间策略变化，请同步更新 RULES.md 和 scripts/README.md。

import fs from 'node:fs'
import path from 'path'
import { spawnSync } from 'node:child_process'

const DEFAULT_LOCK_FILE = '.agent-locks.yml'
const DEFAULT_EXTENSION_DAYS = 2

function main() {
  const options = parseArgs(process.argv.slice(2))
  const rootDir = runGit(['rev-parse', '--show-toplevel']).trim()
  const lockFile = path.join(rootDir, options.lockFile)

  if (!fs.existsSync(lockFile)) {
    console.error('agent-lock-renew: no .agent-locks.yml found')
    process.exit(1)
  }

  const gitBranch = runGit(['symbolic-ref', '--quiet', '--short', 'HEAD'], { allowFailure: true }).trim()
  const currentBranch = gitBranch || process.env.GITHUB_HEAD_REF || ''

  if (!currentBranch) {
    console.error('agent-lock-renew: could not determine current branch')
    process.exit(1)
  }

  const content = fs.readFileSync(lockFile, 'utf8')
  const lines = content.split(/\r?\n/)
  const now = new Date()
  const newExpiresAt = new Date(now.getTime() + options.extensionDays * 24 * 60 * 60 * 1000).toISOString()
  let renewedCount = 0
  let inMyLock = false

  const updatedLines = lines.map((line) => {
    const trimmed = line.trim()

    if (trimmed.startsWith('- ') || (trimmed.startsWith('path:') && !line.startsWith('    '))) {
      inMyLock = false
    }

    if (trimmed.startsWith('branch:')) {
      const branchValue = trimmed.slice('branch:'.length).trim().replace(/^["']|["']$/g, '')
      if (branchValue === currentBranch) {
        inMyLock = true
      }
    }

    if (inMyLock && trimmed.startsWith('expiresAt:')) {
      renewedCount++
      const indent = line.match(/^\s*/)[0]
      return `${indent}expiresAt: "${newExpiresAt}"`
    }

    return line
  })

  fs.writeFileSync(lockFile, updatedLines.join('\n'), 'utf8')

  console.log(`agent-lock-renew: renewed ${renewedCount} lock(s) for branch=${currentBranch}`)
  console.log(`  new expiresAt: ${newExpiresAt}`)
}

function parseArgs(args) {
  const options = {
    lockFile: DEFAULT_LOCK_FILE,
    extensionDays: DEFAULT_EXTENSION_DAYS,
  }

  for (let i = 0; i < args.length; i++) {
    const arg = args[i]
    if (arg === '--lock-file') {
      options.lockFile = args[++i]
    } else if (arg === '--days') {
      options.extensionDays = Number.parseInt(args[++i], 10)
    }
  }

  return options
}

function runGit(args, { allowFailure = false } = {}) {
  const result = spawnSync('git', args, { encoding: 'utf8' })
  if (result.status !== 0) {
    if (allowFailure) return ''
    throw new Error(result.stderr || `git ${args.join(' ')} failed`)
  }
  return result.stdout
}

main()
