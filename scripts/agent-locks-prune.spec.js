import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import {
  classifyLockBuckets,
  isStaleLock,
  pruneLegacyContent,
  prunePerTaskFile,
} from './agent-locks-prune.mjs'

const HOUR_MS = 60 * 60 * 1000
const NOW = new Date('2026-05-14T12:00:00Z')

function makeLock(overrides = {}) {
  return {
    path: 'backend/src/main/resources/db/migration-mysql/V200__x.sql',
    scope: 'file',
    owner: 'claude',
    branch: 'claude/some-task',
    task: 'some-task',
    expiresAt: new Date(NOW.getTime() + 24 * HOUR_MS).toISOString(),
    reason: 'spec fixture',
    ...overrides,
  }
}

describe('isStaleLock', () => {
  const remoteBranches = ['main', 'agent/claude-init', 'claude/active-task']
  const staleThreshold = NOW.getTime() - 24 * HOUR_MS

  it('flags lock as orphan when its branch is gone from remote', () => {
    const lock = makeLock({ branch: 'claude/merged-and-deleted' })
    expect(isStaleLock(lock, { staleThreshold, remoteBranches, now: NOW })).toBe(true)
  })

  it('never flags long-lived branch locks as orphan', () => {
    const lock = makeLock({ branch: 'main', expiresAt: new Date(NOW.getTime() + 48 * HOUR_MS).toISOString() })
    expect(isStaleLock(lock, { staleThreshold, remoteBranches, now: NOW })).toBe(false)
  })

  it('keeps active branch locks even if expired <24h', () => {
    const lock = makeLock({
      branch: 'claude/active-task',
      expiresAt: new Date(NOW.getTime() - 1 * HOUR_MS).toISOString(),
    })
    expect(isStaleLock(lock, { staleThreshold, remoteBranches, now: NOW })).toBe(false)
  })

  it('flags locks expired >24h and no recent branch commits', () => {
    const lock = makeLock({
      branch: 'claude/inactive-task',
      expiresAt: new Date(NOW.getTime() - 48 * HOUR_MS).toISOString(),
    })
    expect(
      isStaleLock(lock, {
        staleThreshold,
        remoteBranches: ['main', 'claude/inactive-task'],
        now: NOW,
        hasRecentCommits: () => false,
      }),
    ).toBe(true)
  })

  it('preserves locks whose branch had recent commits', () => {
    const lock = makeLock({
      branch: 'claude/inactive-task',
      expiresAt: new Date(NOW.getTime() - 48 * HOUR_MS).toISOString(),
    })
    expect(
      isStaleLock(lock, {
        staleThreshold,
        remoteBranches: ['main', 'claude/inactive-task'],
        now: NOW,
        hasRecentCommits: () => true,
      }),
    ).toBe(false)
  })
})

describe('classifyLockBuckets', () => {
  it('groups combined locks by source bucket', () => {
    const locks = [
      { ...makeLock({ branch: 'claude/a' }), source: 'legacy' },
      { ...makeLock({ branch: 'claude/b' }), source: 'per-task:b.yml' },
      { ...makeLock({ branch: 'claude/c' }), source: 'per-task:b.yml' },
      { ...makeLock({ branch: 'claude/d' }), source: 'per-task:d.yml' },
    ]
    const buckets = classifyLockBuckets(locks)
    expect(buckets.legacy).toHaveLength(1)
    expect(buckets.perTask.get('b.yml')).toHaveLength(2)
    expect(buckets.perTask.get('d.yml')).toHaveLength(1)
  })
})

describe('prunePerTaskFile (integration with temp dir)', () => {
  let tmpRoot

  beforeEach(() => {
    tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'prune-pertask-'))
    fs.mkdirSync(path.join(tmpRoot, '.agent-locks'), { recursive: true })
  })

  afterEach(() => {
    fs.rmSync(tmpRoot, { recursive: true, force: true })
  })

  it('deletes the file when all locks are stale', () => {
    const file = path.join(tmpRoot, '.agent-locks', 'gone-branch.yml')
    fs.writeFileSync(
      file,
      `version: 1
task: "gone-branch"
locks:
  - path: "src/x"
    scope: "file"
    owner: "claude"
    branch: "claude/gone-branch"
    expiresAt: "${new Date(NOW.getTime() - 48 * HOUR_MS).toISOString()}"
    reason: "test"
`,
      'utf8',
    )
    const allStaleSignatures = new Set(['file\0src/x\0claude\0claude/gone-branch'])
    const removed = prunePerTaskFile({ rootDir: tmpRoot, file: 'gone-branch.yml', staleSignatures: allStaleSignatures })
    expect(removed).toBe(1)
    expect(fs.existsSync(file)).toBe(false)
  })

  it('rewrites the file preserving non-stale locks', () => {
    const file = path.join(tmpRoot, '.agent-locks', 'mixed.yml')
    fs.writeFileSync(
      file,
      `version: 1
task: "mixed"
locks:
  - path: "src/a"
    scope: "file"
    owner: "claude"
    branch: "claude/gone"
    expiresAt: "${new Date(NOW.getTime() - 48 * HOUR_MS).toISOString()}"
    reason: "stale"
  - path: "src/b"
    scope: "file"
    owner: "claude"
    branch: "claude/mixed"
    expiresAt: "${new Date(NOW.getTime() + 24 * HOUR_MS).toISOString()}"
    reason: "active"
`,
      'utf8',
    )
    const staleSignatures = new Set(['file\0src/a\0claude\0claude/gone'])
    const removed = prunePerTaskFile({ rootDir: tmpRoot, file: 'mixed.yml', staleSignatures })
    expect(removed).toBe(1)
    expect(fs.existsSync(file)).toBe(true)
    const content = fs.readFileSync(file, 'utf8')
    expect(content).toContain('src/b')
    expect(content).not.toContain('src/a')
  })

  it('is a no-op when no locks match staleSignatures', () => {
    const file = path.join(tmpRoot, '.agent-locks', 'untouched.yml')
    const body = `version: 1
task: "untouched"
locks:
  - path: "src/keep"
    scope: "file"
    owner: "claude"
    branch: "claude/keep"
    expiresAt: "${new Date(NOW.getTime() + 24 * HOUR_MS).toISOString()}"
    reason: "active"
`
    fs.writeFileSync(file, body, 'utf8')
    const removed = prunePerTaskFile({ rootDir: tmpRoot, file: 'untouched.yml', staleSignatures: new Set() })
    expect(removed).toBe(0)
    expect(fs.readFileSync(file, 'utf8')).toBe(body)
  })
})

describe('pruneLegacyContent', () => {
  it('removes the matching lock block by signature', () => {
    const content = `version: 1
locks:
  - path: src/a
    scope: file
    owner: claude
    branch: claude/gone
    task: gone
    expiresAt: 2026-05-12T00:00:00Z
    reason: stale
  - path: src/b
    scope: file
    owner: codex
    branch: codex/keep
    task: keep
    expiresAt: 2026-05-20T00:00:00Z
    reason: keep
`
    const staleSignatures = new Set(['file\0src/a\0claude\0claude/gone'])
    const { output, removed } = pruneLegacyContent(content, staleSignatures)
    expect(removed).toBe(1)
    expect(output).toContain('src/b')
    expect(output).not.toContain('src/a')
  })
})
