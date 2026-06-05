import { describe, expect, it } from 'vitest'

import {
  acquireLock,
  buildCurrentContext,
  createLockFromContext,
  findAcquisitionConflicts,
  releaseLocks,
  serializeAgentLocks,
  filterRegistryForBranch,
} from './manage-agent-locks.mjs'

const now = new Date('2026-05-03T12:00:00+08:00')
const context = {
  agent: 'codex',
  branch: 'codex/agent-lock-gate',
  task: 'agent-lock-gate',
}

describe('manage-agent-locks', () => {
  it('derives task from current branch when agent context is stale', () => {
    expect(
      buildCurrentContext({
        rawContext: {
          agent: 'codex',
          branch: 'codex/old-task',
          task: '旧任务',
        },
        branch: 'codex/agent-lock-gate',
      }),
    ).toMatchObject({
      agent: 'codex',
      branch: 'codex/agent-lock-gate',
      task: 'agent-lock-gate',
    })
  })

  it('creates a complete lock from current task context', () => {
    expect(
      createLockFromContext({
        context,
        path: 'src/views/Project/Detail.vue',
        scope: 'file',
        reason: '项目详情页改造',
        days: 2,
        now,
      }),
    ).toEqual({
      path: 'src/views/Project/Detail.vue',
      scope: 'file',
      owner: 'codex',
      branch: 'codex/agent-lock-gate',
      task: 'agent-lock-gate',
      expiresAt: '2026-05-05T04:00:00.000Z',
      reason: '项目详情页改造',
    })
  })

  it('infers directory scope from a trailing slash path', () => {
    expect(
      createLockFromContext({
        context,
        path: 'src/views/Project/',
        reason: '项目目录改造',
        days: 1,
        now,
      }),
    ).toMatchObject({
      path: 'src/views/Project',
      scope: 'directory',
    })
  })

  it('rejects invalid scopes before writing a lock', () => {
    expect(() =>
      createLockFromContext({
        context,
        path: 'src/views/Project/Detail.vue',
        scope: 'component',
        reason: '非法 scope',
        days: 1,
        now,
      }),
    ).toThrow('scope')
  })

  it('rejects invalid expiration day values before writing a lock', () => {
    expect(() =>
      createLockFromContext({
        context,
        path: 'src/views/Project/Detail.vue',
        scope: 'file',
        reason: '非法 days',
        days: Number.NaN,
        now,
      }),
    ).toThrow('days')
  })

  it('rejects invalid explicit expiration timestamps before writing a lock', () => {
    expect(() =>
      createLockFromContext({
        context,
        path: 'src/views/Project/Detail.vue',
        scope: 'file',
        reason: '非法 expiresAt',
        expiresAt: 'tomorrow',
        now,
      }),
    ).toThrow('expiresAt')
  })

  it('blocks acquiring a file lock under another active directory lock', () => {
    const conflicts = findAcquisitionConflicts({
      existingLocks: [
        {
          path: 'src/views/Project',
          scope: 'directory',
          owner: 'claude',
          branch: 'claude/project',
          task: 'project',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: '项目目录改造',
        },
      ],
      requestedLock: createLockFromContext({
        context,
        path: 'src/views/Project/Detail.vue',
        scope: 'file',
        reason: '项目详情页改造',
        days: 1,
        now,
      }),
      now,
    })

    expect(conflicts).toHaveLength(1)
    expect(conflicts[0].branch).toBe('claude/project')
  })

  it('refreshes the current branch lock instead of duplicating it', () => {
    const result = acquireLock({
      registry: {
        version: 1,
        locks: [
          {
            path: 'src/views/Project/Detail.vue',
            scope: 'file',
            owner: 'codex',
            branch: 'codex/agent-lock-gate',
            task: 'old-task',
            expiresAt: '2026-05-04T04:00:00.000Z',
            reason: '旧原因',
          },
        ],
      },
      context,
      path: 'src/views/Project/Detail.vue',
      scope: 'file',
      reason: '新原因',
      days: 2,
      now,
    })

    expect(result.ok).toBe(true)
    expect(result.registry.locks).toEqual([
      expect.objectContaining({
        branch: 'codex/agent-lock-gate',
        task: 'agent-lock-gate',
        reason: '新原因',
        expiresAt: '2026-05-05T04:00:00.000Z',
      }),
    ])
  })

  it('filters inherited local locks from other branches before writing this branch registry', () => {
    const registry = {
      version: 1,
      locks: [
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'claude',
          branch: 'claude/old-task',
          task: 'old-task',
          expiresAt: '2026-05-05T04:00:00.000Z',
          reason: '从 main 继承的旧锁',
        },
        {
          path: 'scripts/manage-agent-locks.mjs',
          scope: 'file',
          owner: 'codex',
          branch: 'codex/agent-lock-gate',
          task: 'agent-lock-gate',
          expiresAt: '2026-05-05T04:00:00.000Z',
          reason: '当前分支锁',
        },
      ],
    }

    expect(filterRegistryForBranch(registry, 'codex/agent-lock-gate')).toEqual({
      version: 1,
      locks: [expect.objectContaining({ branch: 'codex/agent-lock-gate' })],
    })
  })

  it('releases only locks owned by the current branch', () => {
    const registry = {
      version: 1,
      locks: [
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'codex',
          branch: 'codex/agent-lock-gate',
          task: 'agent-lock-gate',
          expiresAt: '2026-05-05T04:00:00.000Z',
          reason: '本分支锁',
        },
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'claude',
          branch: 'claude/project',
          task: 'project',
          expiresAt: '2026-05-05T04:00:00.000Z',
          reason: '其他分支锁',
        },
      ],
    }

    const result = releaseLocks({
      registry,
      context,
      path: 'src/views/Project/Detail.vue',
      scope: 'file',
    })

    expect(result.removed).toHaveLength(1)
    expect(result.registry.locks).toEqual([expect.objectContaining({ branch: 'claude/project' })])
  })

  it('serializes locks as parseable yaml registry text', () => {
    expect(
      serializeAgentLocks({
        version: 1,
        locks: [
          createLockFromContext({
            context,
            path: 'src/views/Project/Detail.vue',
            scope: 'file',
            reason: '项目详情页: 改造 #1',
            days: 1,
            now,
          }),
        ],
      }),
    ).toContain('reason: "项目详情页: 改造 #1"')
  })
})
