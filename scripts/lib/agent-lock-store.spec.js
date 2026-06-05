import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import {
  LEGACY_LOCK_FILE,
  PER_TASK_LOCK_DIR,
  loadCombinedLocks,
  locateLock,
  perTaskFilenameFor,
  perTaskLockPath,
  readPerTaskRegistry,
  serializePerTaskRegistry,
  writePerTaskRegistry,
} from './agent-lock-store.mjs'

let tmpRoot

beforeEach(() => {
  tmpRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'agent-lock-store-'))
})

afterEach(() => {
  fs.rmSync(tmpRoot, { recursive: true, force: true })
})

describe('perTaskFilenameFor', () => {
  it('appends .yml to the slug', () => {
    expect(perTaskFilenameFor('tender-project-evaluation')).toBe('tender-project-evaluation.yml')
  })

  it('rejects empty slug', () => {
    expect(() => perTaskFilenameFor('')).toThrow(/empty/)
  })

  it('rejects path-traversal slugs', () => {
    expect(() => perTaskFilenameFor('../evil')).toThrow(/unsafe/)
    expect(() => perTaskFilenameFor('nested/slug')).toThrow(/unsafe/)
  })
})

describe('perTaskLockPath', () => {
  it('returns .agent-locks/<slug>.yml under rootDir', () => {
    expect(perTaskLockPath('/repo', 'x')).toBe('/repo/.agent-locks/x.yml')
  })
})

describe('serializePerTaskRegistry', () => {
  it('serializes empty locks with task field', () => {
    const yaml = serializePerTaskRegistry({ task: 'demo', locks: [] })
    expect(yaml).toContain('task: "demo"')
    expect(yaml).toContain('locks: []')
  })

  it('serializes locks with all fields', () => {
    const yaml = serializePerTaskRegistry({
      task: 'demo',
      locks: [{
        path: 'src/foo', scope: 'file', owner: 'claude', branch: 'claude/demo',
        expiresAt: '2030-01-01T00:00:00Z', reason: '测试',
      }],
    })
    expect(yaml).toContain('task: "demo"')
    expect(yaml).toContain('- path: "src/foo"')
    expect(yaml).toContain('scope: "file"')
    expect(yaml).toContain('reason: "测试"')
  })

  it('throws when task is missing', () => {
    expect(() => serializePerTaskRegistry({ task: '', locks: [] })).toThrow(/task/)
  })
})

describe('writePerTaskRegistry + readPerTaskRegistry', () => {
  it('creates the file and reads it back', () => {
    const result = writePerTaskRegistry({
      rootDir: tmpRoot,
      task: 'demo',
      locks: [{
        path: 'a', scope: 'file', owner: 'c', branch: 'c/demo',
        expiresAt: '2030-01-01T00:00:00Z', reason: 'r',
      }],
    })
    expect(result.written).toBe(true)
    expect(result.deleted).toBe(false)
    expect(fs.existsSync(result.path)).toBe(true)

    const loaded = readPerTaskRegistry({ rootDir: tmpRoot, task: 'demo' })
    expect(loaded).not.toBeNull()
    expect(loaded.locks).toHaveLength(1)
    expect(loaded.locks[0].path).toBe('a')
    expect(loaded.locks[0].task).toBe('demo')
  })

  it('deletes the file when locks are empty', () => {
    const first = writePerTaskRegistry({
      rootDir: tmpRoot,
      task: 'demo',
      locks: [{
        path: 'a', scope: 'file', owner: 'c', branch: 'c/demo',
        expiresAt: '2030-01-01T00:00:00Z', reason: 'r',
      }],
    })
    expect(fs.existsSync(first.path)).toBe(true)

    const second = writePerTaskRegistry({ rootDir: tmpRoot, task: 'demo', locks: [] })
    expect(second.written).toBe(false)
    expect(second.deleted).toBe(true)
    expect(fs.existsSync(second.path)).toBe(false)
  })

  it('readPerTaskRegistry returns null when file missing', () => {
    expect(readPerTaskRegistry({ rootDir: tmpRoot, task: 'missing' })).toBeNull()
  })
})

describe('loadCombinedLocks', () => {
  it('returns empty when no store exists', () => {
    expect(loadCombinedLocks({ rootDir: tmpRoot })).toEqual([])
  })

  it('loads from legacy file only', () => {
    fs.writeFileSync(
      path.join(tmpRoot, LEGACY_LOCK_FILE),
      [
        'version: 1',
        'locks:',
        '  - path: "legacy/path"',
        '    scope: "file"',
        '    owner: "old"',
        '    branch: "old/task"',
        '    expiresAt: "2030-01-01T00:00:00Z"',
        '    reason: "legacy entry"',
        '',
      ].join('\n'),
      'utf8',
    )

    const locks = loadCombinedLocks({ rootDir: tmpRoot })
    expect(locks).toHaveLength(1)
    expect(locks[0].path).toBe('legacy/path')
    expect(locks[0].source).toBe('legacy')
  })

  it('loads from per-task files only', () => {
    fs.mkdirSync(path.join(tmpRoot, PER_TASK_LOCK_DIR))
    fs.writeFileSync(
      path.join(tmpRoot, PER_TASK_LOCK_DIR, 'task-a.yml'),
      serializePerTaskRegistry({
        task: 'task-a',
        locks: [{
          path: 'per-task/a', scope: 'directory', owner: 'claude', branch: 'claude/task-a',
          expiresAt: '2030-01-01T00:00:00Z', reason: 'new',
        }],
      }),
      'utf8',
    )

    const locks = loadCombinedLocks({ rootDir: tmpRoot })
    expect(locks).toHaveLength(1)
    expect(locks[0].path).toBe('per-task/a')
    expect(locks[0].source).toBe('per-task:task-a.yml')
  })

  it('unions legacy + per-task sources', () => {
    fs.writeFileSync(
      path.join(tmpRoot, LEGACY_LOCK_FILE),
      [
        'version: 1',
        'locks:',
        '  - path: "legacy/x"',
        '    scope: "file"',
        '    owner: "old"',
        '    branch: "old/task"',
        '    expiresAt: "2030-01-01T00:00:00Z"',
        '    reason: "L"',
        '',
      ].join('\n'),
      'utf8',
    )
    fs.mkdirSync(path.join(tmpRoot, PER_TASK_LOCK_DIR))
    fs.writeFileSync(
      path.join(tmpRoot, PER_TASK_LOCK_DIR, 'new-task.yml'),
      serializePerTaskRegistry({
        task: 'new-task',
        locks: [{
          path: 'per-task/y', scope: 'file', owner: 'claude', branch: 'claude/new-task',
          expiresAt: '2030-01-01T00:00:00Z', reason: 'P',
        }],
      }),
      'utf8',
    )

    const locks = loadCombinedLocks({ rootDir: tmpRoot })
    expect(locks).toHaveLength(2)
    expect(locks.map((l) => l.path).sort()).toEqual(['legacy/x', 'per-task/y'])
  })

  it('dedupes identical (scope, path, owner, branch) entries appearing in both sources', () => {
    const entry = {
      path: 'shared/path', scope: 'file', owner: 'claude', branch: 'claude/shared',
      expiresAt: '2030-01-01T00:00:00Z', reason: 'dup',
    }
    fs.writeFileSync(
      path.join(tmpRoot, LEGACY_LOCK_FILE),
      [
        'version: 1',
        'locks:',
        '  - path: "shared/path"',
        '    scope: "file"',
        '    owner: "claude"',
        '    branch: "claude/shared"',
        '    expiresAt: "2030-01-01T00:00:00Z"',
        '    reason: "dup"',
        '',
      ].join('\n'),
      'utf8',
    )
    fs.mkdirSync(path.join(tmpRoot, PER_TASK_LOCK_DIR))
    fs.writeFileSync(
      path.join(tmpRoot, PER_TASK_LOCK_DIR, 'shared.yml'),
      serializePerTaskRegistry({ task: 'shared', locks: [entry] }),
      'utf8',
    )

    const locks = loadCombinedLocks({ rootDir: tmpRoot })
    expect(locks).toHaveLength(1)
    expect(locks[0].source).toBe('legacy')
  })

  it('skips non-yml entries in per-task dir', () => {
    fs.mkdirSync(path.join(tmpRoot, PER_TASK_LOCK_DIR))
    fs.writeFileSync(path.join(tmpRoot, PER_TASK_LOCK_DIR, '.gitkeep'), '', 'utf8')
    fs.writeFileSync(path.join(tmpRoot, PER_TASK_LOCK_DIR, 'README.md'), 'ignored', 'utf8')

    expect(loadCombinedLocks({ rootDir: tmpRoot })).toEqual([])
  })
})

describe('locateLock', () => {
  it('finds a lock in legacy file', () => {
    fs.writeFileSync(
      path.join(tmpRoot, LEGACY_LOCK_FILE),
      [
        'version: 1',
        'locks:',
        '  - path: "legacy/x"',
        '    scope: "file"',
        '    owner: "c"',
        '    branch: "c/old"',
        '    expiresAt: "2030-01-01T00:00:00Z"',
        '    reason: "L"',
        '',
      ].join('\n'),
      'utf8',
    )

    const hit = locateLock({ rootDir: tmpRoot, path: 'legacy/x', scope: 'file', branch: 'c/old' })
    expect(hit).toEqual({ source: 'legacy' })
  })

  it('finds a lock in per-task file', () => {
    fs.mkdirSync(path.join(tmpRoot, PER_TASK_LOCK_DIR))
    fs.writeFileSync(
      path.join(tmpRoot, PER_TASK_LOCK_DIR, 'task-a.yml'),
      serializePerTaskRegistry({
        task: 'task-a',
        locks: [{
          path: 'per-task/a', scope: 'file', owner: 'c', branch: 'c/task-a',
          expiresAt: '2030-01-01T00:00:00Z', reason: 'r',
        }],
      }),
      'utf8',
    )

    const hit = locateLock({ rootDir: tmpRoot, path: 'per-task/a', scope: 'file', branch: 'c/task-a' })
    expect(hit).not.toBeNull()
    expect(hit.source).toBe('per-task')
    expect(hit.task).toBe('task-a')
    expect(hit.file).toBe(path.join(tmpRoot, PER_TASK_LOCK_DIR, 'task-a.yml'))
  })

  it('returns null when not found', () => {
    expect(locateLock({ rootDir: tmpRoot, path: 'missing', scope: 'file' })).toBeNull()
  })
})
