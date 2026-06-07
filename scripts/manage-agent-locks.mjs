#!/usr/bin/env node
// Input: current Agent context, lock command arguments, and .agent-locks.yml
// Output: updates .agent-locks.yml by acquiring or releasing current-branch locks
// Pos: scripts/多 Agent 文件锁管理
// 维护声明: 若锁字段、默认过期策略或 Agent 上下文格式变化，请同步更新 AGENTS.md、RULES.md 和 scripts/README.md。
import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

import {
  collectLocksFromRegistries,
  isExpiredLock,
  loadAgentContext,
  parseAgentLocks,
  pathMatchesLock,
  resolveCurrentBranch,
} from './check-agent-locks.mjs'
import {
  LEGACY_LOCK_FILE,
  PER_TASK_LOCK_DIR,
  loadCombinedLocks,
  locateLock,
  readPerTaskRegistry,
  writePerTaskRegistry,
} from './lib/agent-lock-store.mjs'

const DEFAULT_LOCK_FILE = LEGACY_LOCK_FILE
const DEFAULT_DAYS = 1

export function createLockFromContext({ context, path: lockPath, scope, reason, days, expiresAt, now = new Date() }) {
  const requestedScope = scope || inferScope(lockPath)
  validateScope(requestedScope)
  const normalizedDays = validateDays(days === undefined ? DEFAULT_DAYS : days)
  const normalizedExpiresAt = expiresAt || new Date(now.getTime() + normalizedDays * 24 * 60 * 60 * 1000).toISOString()
  validateExpiresAt(normalizedExpiresAt)
  const normalizedPath = normalizeRepoPath(lockPath)
  const branch = requireValue(context.branch, 'current branch')
  const owner = context.agent || branch.split('/')[0]

  return {
    path: normalizedPath,
    scope: requestedScope,
    owner,
    branch,
    task: context.task || branch.split('/').slice(1).join('/') || branch,
    expiresAt: normalizedExpiresAt,
    reason: requireValue(reason, '--reason'),
  }
}

export function findAcquisitionConflicts({ existingLocks, requestedLock, now = new Date() }) {
  return existingLocks
    .filter((lock) => !isExpiredLock(lock, now))
    .filter((lock) => lock.branch !== requestedLock.branch)
    .filter((lock) => locksOverlap(lock, requestedLock))
}

export function acquireLock({ registry, context, path: lockPath, scope, reason, days, expiresAt, now = new Date() }) {
  const requestedLock = createLockFromContext({
    context,
    path: lockPath,
    scope,
    reason,
    days,
    expiresAt,
    now,
  })
  const conflicts = findAcquisitionConflicts({
    existingLocks: registry.locks || [],
    requestedLock,
    now,
  })

  if (conflicts.length > 0) {
    return { ok: false, conflicts, registry }
  }

  const nextLocks = (registry.locks || []).filter(
    (lock) =>
      !(
        lock.branch === requestedLock.branch &&
        normalizeRepoPath(lock.path) === requestedLock.path &&
        lock.scope === requestedLock.scope
      ),
  )

  return {
    ok: true,
    lock: requestedLock,
    registry: {
      version: registry.version || 1,
      locks: [...nextLocks, requestedLock],
    },
  }
}

export function releaseLocks({ registry, context, path: lockPath, scope, all = false }) {
  const normalizedPath = lockPath ? normalizeRepoPath(lockPath) : ''
  const removed = []
  const kept = []

  for (const lock of registry.locks || []) {
    const ownedByBranch = lock.branch === context.branch
    const matchesTarget =
      all || (normalizedPath && normalizeRepoPath(lock.path) === normalizedPath && (!scope || lock.scope === scope))

    if (ownedByBranch && matchesTarget) {
      removed.push(lock)
    } else {
      kept.push(lock)
    }
  }

  return {
    removed,
    registry: {
      version: registry.version || 1,
      locks: kept,
    },
  }
}

export function serializeAgentLocks(registry) {
  const locks = registry.locks || []
  if (locks.length === 0) {
    return 'version: 1\nlocks: []\n'
  }

  return [
    'version: 1',
    'locks:',
    ...locks.flatMap((lock) => [
      `  - path: ${quoteYamlScalar(lock.path)}`,
      `    scope: ${quoteYamlScalar(lock.scope)}`,
      `    owner: ${quoteYamlScalar(lock.owner)}`,
      `    branch: ${quoteYamlScalar(lock.branch)}`,
      `    task: ${quoteYamlScalar(lock.task)}`,
      `    expiresAt: ${quoteYamlScalar(lock.expiresAt)}`,
      `    reason: ${quoteYamlScalar(lock.reason)}`,
    ]),
    '',
  ].join('\n')
}

function main() {
  const command = process.argv[2]
  const options = parseCommandArgs(process.argv.slice(3))
  const rootDir = runGit(['rev-parse', '--show-toplevel']).trim()
  const context = loadContextFromWorktree(rootDir)
  const taskSlug = resolveTaskSlug(context)

  if (command === 'acquire') {
    // Collision check: union of (a) all local locks across legacy + per-task
    // files, (b) remote-branch locks. This survives the per-task split.
    const combinedLocal = loadCombinedLocks({ rootDir })
    const remoteLocks = readRemoteLocks(context.branch)
    const allLocks = [...combinedLocal, ...remoteLocks]

    const requestedLock = createLockFromContext({
      context,
      path: options.path,
      scope: options.scope,
      reason: options.reason,
      days: options.days,
      expiresAt: options.expiresAt,
    })
    const conflicts = findAcquisitionConflicts({
      existingLocks: allLocks,
      requestedLock,
    })
    if (conflicts.length > 0) {
      printAcquireConflicts(conflicts)
      process.exit(1)
    }

    // Write to per-task file (new scheme).
    const perTaskRegistry = readPerTaskRegistry({ rootDir, task: taskSlug }) || {
      task: taskSlug,
      locks: [],
    }
    const filteredLocks = perTaskRegistry.locks.filter(
      (lock) =>
        !(
          lock.branch === requestedLock.branch &&
          normalizeRepoPath(lock.path) === requestedLock.path &&
          lock.scope === requestedLock.scope
        ),
    )
    const newLocks = [...filteredLocks, requestedLock]
    const result = writePerTaskRegistry({
      rootDir,
      task: taskSlug,
      locks: newLocks,
    })
    console.log(
      `agent-lock-acquire: locked ${requestedLock.scope}:${requestedLock.path} for ${context.branch} (${path.relative(rootDir, result.path)})`,
    )
    return
  }

  if (command === 'release') {
    if (options.all) {
      const removed = releaseAllForBranch({ rootDir, branch: context.branch, taskSlug })
      console.log(`agent-lock-release: removed ${removed} lock${removed === 1 ? '' : 's'}`)
      return
    }

    if (!options.path) {
      console.error('agent-lock-release: --path required (or --all)')
      process.exit(1)
    }

    const removed = releaseSingleLock({
      rootDir,
      branch: context.branch,
      taskSlug,
      lockPath: options.path,
      scope: options.scope,
    })
    console.log(`agent-lock-release: removed ${removed} lock${removed === 1 ? '' : 's'}`)
    return
  }

  printUsage()
  process.exit(1)
}

function resolveTaskSlug(context) {
  // Prefer explicit task from .agent-task-context; fallback to branch-derived slug.
  const explicit = String(context.task || '').trim()
  if (explicit && !explicit.includes('/') && !explicit.includes('\\')) {
    return explicit
  }
  const branch = String(context.branch || '').trim()
  if (!branch) throw new Error('agent-lock: cannot resolve task slug without branch')
  const afterSlash = branch.includes('/') ? branch.split('/').slice(1).join('/') : branch
  return afterSlash.replaceAll('/', '-')
}

function releaseSingleLock({ rootDir, branch, taskSlug, lockPath, scope }) {
  // Try per-task file first (new scheme).
  const perTask = readPerTaskRegistry({ rootDir, task: taskSlug })
  if (perTask) {
    const before = perTask.locks.length
    const kept = perTask.locks.filter(
      (lock) =>
        !(
          lock.branch === branch &&
          normalizeRepoPath(lock.path) === normalizeRepoPath(lockPath) &&
          (!scope || lock.scope === scope)
        ),
    )
    if (kept.length !== before) {
      writePerTaskRegistry({ rootDir, task: taskSlug, locks: kept })
      return before - kept.length
    }
  }

  // Fall back: locate the lock in legacy file, rewrite it.
  const located = locateLock({ rootDir, path: lockPath, scope, branch })
  if (!located || located.source !== 'legacy') return 0
  return releaseFromLegacy({ rootDir, branch, lockPath, scope })
}

function releaseAllForBranch({ rootDir, branch, taskSlug }) {
  let removed = 0

  const perTask = readPerTaskRegistry({ rootDir, task: taskSlug })
  if (perTask) {
    const kept = perTask.locks.filter((lock) => lock.branch !== branch)
    removed += perTask.locks.length - kept.length
    writePerTaskRegistry({ rootDir, task: taskSlug, locks: kept })
  }

  removed += releaseAllFromLegacy({ rootDir, branch })
  return removed
}

function releaseFromLegacy({ rootDir, branch, lockPath, scope }) {
  const legacyPath = path.join(rootDir, DEFAULT_LOCK_FILE)
  const registry = readLegacyRegistry(legacyPath)
  const before = (registry.locks || []).length
  const kept = (registry.locks || []).filter(
    (lock) =>
      !(
        lock.branch === branch &&
        normalizeRepoPath(lock.path) === normalizeRepoPath(lockPath) &&
        (!scope || lock.scope === scope)
      ),
  )
  if (kept.length === before) return 0
  writeLegacyRegistry(legacyPath, { version: registry.version || 1, locks: kept })
  return before - kept.length
}

function releaseAllFromLegacy({ rootDir, branch }) {
  const legacyPath = path.join(rootDir, DEFAULT_LOCK_FILE)
  const registry = readLegacyRegistry(legacyPath)
  const before = (registry.locks || []).length
  const kept = (registry.locks || []).filter((lock) => lock.branch !== branch)
  if (kept.length === before) return 0
  writeLegacyRegistry(legacyPath, { version: registry.version || 1, locks: kept })
  return before - kept.length
}

function parseCommandArgs(args) {
  const options = {
    lockFile: DEFAULT_LOCK_FILE,
    days: DEFAULT_DAYS,
  }

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index]
    if (arg === '--path') {
      options.path = args[++index]
    } else if (arg === '--scope') {
      options.scope = args[++index]
    } else if (arg === '--reason') {
      options.reason = args[++index]
    } else if (arg === '--days') {
      options.days = Number.parseInt(args[++index], 10)
    } else if (arg === '--expires-at') {
      options.expiresAt = args[++index]
    } else if (arg === '--lock-file') {
      options.lockFile = args[++index]
    } else if (arg === '--all') {
      options.all = true
    }
  }

  return options
}

function readLegacyRegistry(lockFile) {
  if (!fs.existsSync(lockFile)) {
    return { version: 1, locks: [] }
  }
  return parseAgentLocks(fs.readFileSync(lockFile, 'utf8'))
}

function writeLegacyRegistry(lockFile, registry) {
  fs.writeFileSync(lockFile, serializeAgentLocks(registry), 'utf8')
}

function loadContextFromWorktree(rootDir) {
  const gitBranch = runGit(['symbolic-ref', '--quiet', '--short', 'HEAD'], { allowFailure: true }).trim()
  const branch = resolveCurrentBranch(gitBranch)
  const contextPath = path.join(rootDir, '.agent-task-context')
  const rawContext = fs.existsSync(contextPath) ? loadAgentContext(fs.readFileSync(contextPath, 'utf8')) : {}
  return buildCurrentContext({ rawContext, branch })
}

export function buildCurrentContext({ rawContext, branch }) {
  const agentFromBranch = branch.includes('/') ? branch.split('/')[0] : ''
  const taskFromBranch = branch.includes('/') ? branch.split('/').slice(1).join('/') : branch
  const contextBranchMatches = rawContext.branch === branch

  return {
    ...rawContext,
    agent: agentFromBranch || rawContext.agent,
    branch: branch || rawContext.branch,
    task: contextBranchMatches ? rawContext.task : taskFromBranch,
  }
}

export function filterRegistryForBranch(registry, branch) {
  return {
    version: registry.version || 1,
    locks: (registry.locks || []).filter((lock) => lock.branch === branch),
  }
}

function readRemoteLocks(currentBranch, lockFile = DEFAULT_LOCK_FILE) {
  // Reads remote-branch locks from `.agent-locks.yml` only (legacy compat).
  // Per-task files are git-tracked and propagate via fetch, so the new scheme
  // doesn't need a separate remote scan — `loadCombinedLocks` picks them up
  // from the working tree after rebase. This helper preserves the existing
  // remote-branch warning channel for legacy entries while not regressing.
  const refsOutput = runGit(['for-each-ref', '--format=%(refname:short)', 'refs/remotes/origin'], {
    allowFailure: true,
  })
  if (!refsOutput.trim()) return []

  const sources = refsOutput
    .trim()
    .split(/\r?\n/)
    .filter((ref) => ref && ref !== 'origin/HEAD')
    .flatMap((ref) => {
      const content = runGit(['show', `${ref}:${lockFile}`], { allowFailure: true })
      if (!content.trim()) return []
      return [{ source: ref, registry: parseAgentLocks(content) }]
    })

  return collectLocksFromRegistries(sources, { currentBranch })
}

function locksOverlap(left, right) {
  return pathMatchesLock(left.path, right) || pathMatchesLock(right.path, left)
}

function inferScope(lockPath) {
  return lockPath.endsWith('/') ? 'directory' : 'file'
}

function validateScope(scope) {
  if (!['file', 'directory'].includes(scope)) {
    throw new Error(`agent-lock: invalid scope '${scope}', expected file or directory`)
  }
}

function validateDays(days) {
  if (!Number.isInteger(days) || days <= 0) {
    throw new Error(`agent-lock: invalid days '${days}', expected a positive integer`)
  }
  return days
}

function validateExpiresAt(expiresAt) {
  if (Number.isNaN(new Date(expiresAt).getTime())) {
    throw new Error(`agent-lock: invalid expiresAt '${expiresAt}'`)
  }
}

function normalizeRepoPath(filePath) {
  return requireValue(filePath, '--path').replaceAll('\\', '/').replace(/^\.\//, '').replace(/\/+$/, '')
}

function quoteYamlScalar(value) {
  return JSON.stringify(String(value ?? ''))
}

function requireValue(value, label) {
  if (!value) {
    throw new Error(`agent-lock: missing required value ${label}`)
  }
  return value
}

function runGit(args, { allowFailure = false } = {}) {
  const result = spawnSync('git', args, { encoding: 'utf8' })
  if (result.status !== 0) {
    if (allowFailure) return ''
    throw new Error(result.stderr || `git ${args.join(' ')} failed`)
  }
  return result.stdout
}

function printAcquireConflicts(conflicts) {
  console.error('agent-lock-acquire: blocked by active lock')
  for (const lock of conflicts) {
    console.error(
      `  ${lock.scope}:${lock.path} owner=${lock.owner} branch=${lock.branch} task=${lock.task} expiresAt=${lock.expiresAt}`,
    )
    console.error(`    reason=${lock.reason}`)
  }
}

function printUsage() {
  console.error(`Usage:
  node scripts/manage-agent-locks.mjs acquire --path <path> [--scope file|directory] --reason <reason> [--days 1]
  node scripts/manage-agent-locks.mjs release --path <path> [--scope file|directory]
  node scripts/manage-agent-locks.mjs release --all`)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    main()
  } catch (error) {
    console.error(error.message)
    process.exit(1)
  }
}
