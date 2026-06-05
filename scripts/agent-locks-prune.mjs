#!/usr/bin/env node
// Input: .agent-locks.yml + .agent-locks/*.yml, git branch state, and current timestamp
// Output: removes stale locks from both stores; deletes empty per-task files
// Pos: scripts/Agent 锁清理工具
// 维护声明: 若锁过期策略或清理阈值变化，请同步更新 RULES.md 和 scripts/README.md。

import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

import {
  LEGACY_LOCK_FILE,
  PER_TASK_LOCK_DIR,
  loadCombinedLocks,
  readPerTaskRegistry,
  writePerTaskRegistry,
} from './lib/agent-lock-store.mjs'
import { parseAgentLocks } from './check-agent-locks.mjs'

const STALE_THRESHOLD_HOURS = 24
const LONG_LIVED_BRANCHES = new Set(['main', 'master'])

export function lockSignature(lock) {
  return `${lock.scope}\0${normalize(lock.path)}\0${lock.owner || ''}\0${lock.branch || ''}`
}

export function isStaleLock(
  lock,
  {
    staleThreshold,
    remoteBranches,
    now = new Date(),
    hasRecentCommits = (branch) => defaultHasRecentCommits(branch),
  },
) {
  // Long-lived branches (main/master) are never orphaned even if their locks expired.
  if (lock.branch && LONG_LIVED_BRANCHES.has(lock.branch)) {
    if (!lock.expiresAt) return false
    const expiresAtTime = new Date(lock.expiresAt).getTime()
    return expiresAtTime <= staleThreshold
  }

  // Orphan-branch detection: branch gone from remote → stale regardless of expiresAt.
  if (lock.branch && !remoteBranches.includes(lock.branch)) {
    return true
  }

  if (!lock.expiresAt) return false
  const expiresAtTime = new Date(lock.expiresAt).getTime()
  if (expiresAtTime > staleThreshold) return false

  // Expired >24h. Spare if branch is active.
  if (lock.branch && hasRecentCommits(lock.branch)) return false

  return true
}

export function classifyLockBuckets(locks) {
  const legacy = []
  const perTask = new Map()
  for (const lock of locks) {
    if (lock.source === 'legacy') {
      legacy.push(lock)
      continue
    }
    if (typeof lock.source === 'string' && lock.source.startsWith('per-task:')) {
      const file = lock.source.slice('per-task:'.length)
      if (!perTask.has(file)) perTask.set(file, [])
      perTask.get(file).push(lock)
    }
  }
  return { legacy, perTask }
}

export function prunePerTaskFile({ rootDir, file, staleSignatures, lockDir = PER_TASK_LOCK_DIR }) {
  const filePath = path.join(rootDir, lockDir, file)
  if (!fs.existsSync(filePath)) return 0
  const registry = parseAgentLocks(fs.readFileSync(filePath, 'utf8'))
  const before = (registry.locks || []).length
  const kept = (registry.locks || []).filter((lock) => !staleSignatures.has(lockSignature(lock)))
  if (kept.length === before) return 0

  const task = file.replace(/\.yml$/, '')
  writePerTaskRegistry({ rootDir, task, locks: kept, lockDir })
  return before - kept.length
}

export function pruneLegacyContent(content, staleSignatures) {
  const lines = content.split(/\r?\n/)
  const lockBlocks = parseLegacyLockBlocks(lines)
  const removeRanges = lockBlocks
    .filter((block) => staleSignatures.has(lockSignature(block.lock)))
    .map((block) => ({ start: block.startLine, end: block.endLine }))

  if (removeRanges.length === 0) {
    return { output: content, removed: 0 }
  }

  const kept = lines.filter((_, idx) => !removeRanges.some((r) => idx >= r.start && idx <= r.end))
  return { output: kept.join('\n'), removed: removeRanges.length }
}

function parseLegacyLockBlocks(lines) {
  const blocks = []
  let current = null
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()
    if (trimmed.startsWith('- ')) {
      if (current) current.endLine = i - 1
      current = { startLine: i, lock: {} }
      blocks.push(current)
      assignField(current.lock, trimmed.slice(2))
      continue
    }
    if (line.startsWith('    ') && current) {
      assignField(current.lock, trimmed)
    }
  }
  if (current) current.endLine = lines.length - 1
  return blocks
}

function assignField(target, fieldLine) {
  const idx = fieldLine.indexOf(':')
  if (idx === -1) return
  const key = fieldLine.slice(0, idx).trim()
  const value = fieldLine.slice(idx + 1).trim().replace(/^["']|["']$/g, '')
  target[key] = value
}

function defaultHasRecentCommits(branch) {
  const out = runGit(['log', '--oneline', '--since=24 hours ago', `origin/${branch}`], { allowFailure: true })
  return out.trim().length > 0
}

function getRemoteBranches() {
  const output = runGit(['branch', '-r', '--format=%(refname:short)'], { allowFailure: true })
  return output
    .split(/\r?\n/)
    .map((line) => line.trim().replace(/^origin\//, ''))
    .filter(Boolean)
}

function normalize(filePath) {
  return String(filePath || '').replaceAll('\\', '/').replace(/^\.\//, '').replace(/\/+$/, '')
}

function runGit(args, { allowFailure = false } = {}) {
  const result = spawnSync('git', args, { encoding: 'utf8' })
  if (result.status !== 0) {
    if (allowFailure) return ''
    throw new Error(result.stderr || `git ${args.join(' ')} failed`)
  }
  return result.stdout
}

export function main() {
  const rootDir = runGit(['rev-parse', '--show-toplevel']).trim()
  const now = new Date()
  const staleThreshold = now.getTime() - STALE_THRESHOLD_HOURS * 60 * 60 * 1000
  const remoteBranches = getRemoteBranches()

  const combined = loadCombinedLocks({ rootDir })
  if (combined.length === 0) {
    console.log('agent-locks-prune: no locks found (legacy + per-task), nothing to prune')
    return 0
  }

  const staleLocks = combined.filter((lock) => isStaleLock(lock, { staleThreshold, remoteBranches, now }))
  if (staleLocks.length === 0) {
    console.log('agent-locks-prune: no stale locks found')
    return 0
  }

  const staleSignatures = new Set(staleLocks.map(lockSignature))
  const buckets = classifyLockBuckets(staleLocks)
  let removed = 0

  // Prune per-task files. writePerTaskRegistry unlinks the file when locks become empty.
  for (const [file] of buckets.perTask) {
    removed += prunePerTaskFile({ rootDir, file, staleSignatures })
  }

  // Prune legacy single file.
  if (buckets.legacy.length > 0) {
    const legacyPath = path.join(rootDir, LEGACY_LOCK_FILE)
    if (fs.existsSync(legacyPath)) {
      const content = fs.readFileSync(legacyPath, 'utf8')
      const { output, removed: legacyRemoved } = pruneLegacyContent(content, staleSignatures)
      if (legacyRemoved > 0) {
        fs.writeFileSync(legacyPath, output, 'utf8')
        removed += legacyRemoved
      }
    }
  }

  console.log(`agent-locks-prune: removed ${removed} stale lock(s)`)
  for (const lock of staleLocks) {
    console.log(
      `  ${lock.scope}:${lock.path} owner=${lock.owner} branch=${lock.branch} source=${lock.source} expiresAt=${lock.expiresAt}`,
    )
  }
  return removed
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main()
    process.exit(0)
  } catch (error) {
    console.error(error.message)
    process.exit(1)
  }
}
