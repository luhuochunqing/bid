// Input: repository root + (optional) lock-file path overrides
// Output: agent-lock storage helpers backing both legacy single-file and per-task multi-file schemes
// Pos: scripts/lib - agent-lock storage backend
// 维护声明: 若新锁存储路径或 schema 字段变化，请同步更新 scripts/check-agent-locks.mjs、scripts/manage-agent-locks.mjs、scripts/README.md。

import fs from 'node:fs'
import path from 'node:path'

import { parseAgentLocks } from '../check-agent-locks.mjs'

export const LEGACY_LOCK_FILE = '.agent-locks.yml'
export const PER_TASK_LOCK_DIR = '.agent-locks'

/**
 * Filename slug for a per-task lock file. Mirrors agent-start-task.sh slug rules:
 * branch "<agent>/<task-slug>" → "<task-slug>.yml". When branch lacks "/",
 * falls back to the bare branch name.
 */
export function perTaskFilenameFor(task) {
  const slug = String(task || '').trim()
  if (!slug) throw new Error('agent-lock-store: empty task slug')
  if (slug.includes('/') || slug.includes('\\') || slug.includes('..')) {
    throw new Error(`agent-lock-store: unsafe task slug '${slug}'`)
  }
  return `${slug}.yml`
}

/**
 * Resolve absolute path of the per-task lock file for a given task slug.
 */
export function perTaskLockPath(rootDir, task) {
  return path.join(rootDir, PER_TASK_LOCK_DIR, perTaskFilenameFor(task))
}

/**
 * Load locks from BOTH the legacy single file and any `.agent-locks/*.yml`
 * per-task files. Returns a flat lock list; deduplicates entries with the
 * same (scope, path, owner, branch) signature so a lock that exists in both
 * stores doesn't get counted twice.
 */
export function loadCombinedLocks({ rootDir, lockFile = LEGACY_LOCK_FILE, lockDir = PER_TASK_LOCK_DIR }) {
  const locks = []
  const seen = new Set()

  const pushUnique = (lock) => {
    const key = `${lock.scope}\0${lock.path}\0${lock.owner || ''}\0${lock.branch || ''}`
    if (seen.has(key)) return
    seen.add(key)
    locks.push(lock)
  }

  const legacyPath = path.join(rootDir, lockFile)
  if (fs.existsSync(legacyPath)) {
    const registry = parseAgentLocks(fs.readFileSync(legacyPath, 'utf8'))
    for (const lock of registry.locks || []) {
      pushUnique({ ...lock, source: 'legacy' })
    }
  }

  const perTaskDir = path.join(rootDir, lockDir)
  if (fs.existsSync(perTaskDir) && fs.statSync(perTaskDir).isDirectory()) {
    for (const entry of fs.readdirSync(perTaskDir).sort()) {
      if (!entry.endsWith('.yml')) continue
      const filePath = path.join(perTaskDir, entry)
      if (!fs.statSync(filePath).isFile()) continue
      const registry = parseAgentLocks(fs.readFileSync(filePath, 'utf8'))
      for (const lock of registry.locks || []) {
        pushUnique({ ...lock, source: `per-task:${entry}` })
      }
    }
  }

  return locks
}

/**
 * Serialize a per-task registry (single task, multiple locks) to YAML.
 * Adds a top-level `task:` field that mirrors the filename slug.
 */
export function serializePerTaskRegistry({ task, locks }) {
  if (!task) throw new Error('agent-lock-store: serializePerTaskRegistry requires task')
  if (!Array.isArray(locks) || locks.length === 0) {
    return `version: 1\ntask: ${quote(task)}\nlocks: []\n`
  }

  return [
    'version: 1',
    `task: ${quote(task)}`,
    'locks:',
    ...locks.flatMap((lock) => [
      `  - path: ${quote(lock.path)}`,
      `    scope: ${quote(lock.scope)}`,
      `    owner: ${quote(lock.owner)}`,
      `    branch: ${quote(lock.branch)}`,
      `    expiresAt: ${quote(lock.expiresAt)}`,
      `    reason: ${quote(lock.reason)}`,
    ]),
    '',
  ].join('\n')
}

/**
 * Read the per-task file for a given task. Returns null if no file exists.
 * Returned lock entries inherit the file's task slug if missing.
 */
export function readPerTaskRegistry({ rootDir, task, lockDir = PER_TASK_LOCK_DIR }) {
  const filePath = path.join(rootDir, lockDir, perTaskFilenameFor(task))
  if (!fs.existsSync(filePath)) return null
  const registry = parseAgentLocks(fs.readFileSync(filePath, 'utf8'))
  return {
    task,
    locks: (registry.locks || []).map((lock) => ({ ...lock, task: lock.task || task })),
  }
}

/**
 * Atomically write a per-task file. Empty `locks` deletes the file (kept
 * empty files would otherwise pollute git status).
 */
export function writePerTaskRegistry({ rootDir, task, locks, lockDir = PER_TASK_LOCK_DIR }) {
  const dir = path.join(rootDir, lockDir)
  const filePath = path.join(dir, perTaskFilenameFor(task))

  if (!locks || locks.length === 0) {
    if (fs.existsSync(filePath)) fs.unlinkSync(filePath)
    return { written: false, deleted: true, path: filePath }
  }

  fs.mkdirSync(dir, { recursive: true })
  const content = serializePerTaskRegistry({ task, locks })
  fs.writeFileSync(filePath, content, 'utf8')
  return { written: true, deleted: false, path: filePath }
}

/**
 * Find the source of an existing lock by (path, scope, branch). Returns:
 *   - { source: 'legacy' } if it lives in `.agent-locks.yml`
 *   - { source: 'per-task', task, file } if in `.agent-locks/<task>.yml`
 *   - null if not found
 */
export function locateLock({ rootDir, path: lockPath, scope, branch, lockFile = LEGACY_LOCK_FILE, lockDir = PER_TASK_LOCK_DIR }) {
  const target = (l) =>
    normalize(l.path) === normalize(lockPath) &&
    l.scope === scope &&
    (!branch || l.branch === branch)

  const legacyPath = path.join(rootDir, lockFile)
  if (fs.existsSync(legacyPath)) {
    const registry = parseAgentLocks(fs.readFileSync(legacyPath, 'utf8'))
    if ((registry.locks || []).some(target)) {
      return { source: 'legacy' }
    }
  }

  const perTaskDir = path.join(rootDir, lockDir)
  if (fs.existsSync(perTaskDir) && fs.statSync(perTaskDir).isDirectory()) {
    for (const entry of fs.readdirSync(perTaskDir).sort()) {
      if (!entry.endsWith('.yml')) continue
      const filePath = path.join(perTaskDir, entry)
      if (!fs.statSync(filePath).isFile()) continue
      const registry = parseAgentLocks(fs.readFileSync(filePath, 'utf8'))
      if ((registry.locks || []).some(target)) {
        return { source: 'per-task', task: entry.replace(/\.yml$/, ''), file: filePath }
      }
    }
  }

  return null
}

function quote(value) {
  return JSON.stringify(String(value ?? ''))
}

function normalize(filePath) {
  return String(filePath || '').replaceAll('\\', '/').replace(/^\.\//, '').replace(/\/+$/, '')
}
