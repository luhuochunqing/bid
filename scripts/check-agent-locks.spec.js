import { describe, expect, it } from 'vitest'

import {
  checkAgentLocks,
  collectLocksFromRegistries,
  findBlockingLocks,
  isExpiredLock,
  loadAgentContext,
  parseAgentLocks,
  parsePorcelainUntrackedFiles,
  pathMatchesLock,
  resolveCurrentBranch,
} from './check-agent-locks.mjs'

const NOW = '2026-05-03T12:00:00+08:00'

describe('check-agent-locks', () => {
  // Use per-test scope to prevent Vitest worker cross-contamination.
  let now
  beforeEach(() => {
    now = new Date(NOW)
  })
  it('parses a minimal lock registry', () => {
    expect(
      parseAgentLocks(`
version: 1
locks:
  - path: src/views/Project/Detail.vue
    scope: file
    owner: claude
    branch: claude/project-detail
    task: project-detail
    expiresAt: 2026-05-05T23:59:59+08:00
    reason: 项目详情页重构
`),
    ).toEqual({
      version: 1,
      locks: [
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'claude',
          branch: 'claude/project-detail',
          task: 'project-detail',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: '项目详情页重构',
        },
      ],
    })
  })

  it('parses quoted scalars that contain yaml comment characters', () => {
    expect(
      parseAgentLocks(`
version: 1
locks:
  - path: "src/views/Project/Detail.vue"
    scope: "file"
    owner: "codex"
    branch: "codex/agent-lock-gate"
    task: "agent-lock-gate"
    expiresAt: "2026-05-04T04:00:00.000Z"
    reason: "项目详情页: 改造 #1"
`),
    ).toMatchObject({
      locks: [
        {
          reason: '项目详情页: 改造 #1',
        },
      ],
    })
  })

  it('loads agent context from key value lines', () => {
    expect(
      loadAgentContext(`
agent=codex
task=agent-lock-gate
branch=codex/agent-lock-gate
base=origin/main
worktree=/Users/user/xiyu/worktrees/codex-agent-lock-gate
`),
    ).toMatchObject({
      agent: 'codex',
      task: 'agent-lock-gate',
      branch: 'codex/agent-lock-gate',
      base: 'origin/main',
    })
  })

  it('loads agent context from legacy json files', () => {
    expect(
      loadAgentContext(`{
  "agent": "codex",
  "branch": "codex/old-task",
  "task": "旧任务"
}`),
    ).toMatchObject({
      agent: 'codex',
      branch: 'codex/old-task',
      task: '旧任务',
    })
  })

  it('matches file and directory locks', () => {
    expect(
      pathMatchesLock('src/views/Project/Detail.vue', {
        path: 'src/views/Project/Detail.vue',
        scope: 'file',
      }),
    ).toBe(true)
    expect(
      pathMatchesLock('src/views/Project/components/TaskBoard.vue', {
        path: 'src/views/Project',
        scope: 'directory',
      }),
    ).toBe(true)
    expect(
      pathMatchesLock('src/views/Projector/Detail.vue', {
        path: 'src/views/Project',
        scope: 'directory',
      }),
    ).toBe(false)
  })

  it('blocks another agent from changing an active locked file', () => {
    const blockingLocks = findBlockingLocks({
      changedFiles: ['src/views/Project/Detail.vue'],
      context: { agent: 'codex', branch: 'codex/project-detail-fix' },
      locks: [
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'claude',
          branch: 'claude/project-detail',
          task: 'project-detail',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: '项目详情页重构',
        },
      ],
      now,
    })

    expect(blockingLocks).toHaveLength(1)
    expect(blockingLocks[0]).toMatchObject({
      file: 'src/views/Project/Detail.vue',
      lock: { owner: 'claude' },
    })
  })

  it('allows the lock owner to change the locked file', () => {
    expect(
      findBlockingLocks({
        changedFiles: ['src/views/Project/Detail.vue'],
        context: { agent: 'claude', branch: 'claude/project-detail' },
        locks: [
          {
            path: 'src/views/Project/Detail.vue',
            scope: 'file',
            owner: 'claude',
            branch: 'claude/project-detail',
            task: 'project-detail',
            expiresAt: '2026-05-05T23:59:59+08:00',
            reason: '项目详情页重构',
          },
        ],
        now,
      }),
    ).toEqual([])
  })

  it('blocks the same agent name on a different task branch', () => {
    const blockingLocks = findBlockingLocks({
      changedFiles: ['src/views/Project/Detail.vue'],
      context: { agent: 'codex', branch: 'codex/another-task' },
      locks: [
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'codex',
          branch: 'codex/project-detail',
          task: 'project-detail',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: '同一 Agent 的另一个任务锁',
        },
      ],
      now,
    })

    expect(blockingLocks).toHaveLength(1)
  })

  it('ignores expired locks for blocking decisions', () => {
    const lock = {
      path: 'src/views/Project/Detail.vue',
      scope: 'file',
      owner: 'claude',
      branch: 'claude/project-detail',
      task: 'project-detail',
      expiresAt: '2026-05-02T23:59:59+08:00',
      reason: '项目详情页重构',
    }

    expect(isExpiredLock(lock, now)).toBe(true)
    expect(
      findBlockingLocks({
        changedFiles: ['src/views/Project/Detail.vue'],
        context: { agent: 'codex', branch: 'codex/project-detail-fix' },
        locks: [lock],
        now,
      }),
    ).toEqual([])
  })

  it('fails duplicate active locks on the same path and scope', () => {
    // Only detect duplicates when the path is actually being changed.
    // When no files are changed, duplicate locks across unrelated branches are
    // benign and should not block CI (e.g. two task branches each holding their
    // own lock on src/router/index.js, but neither branch is being merged now).
    // Within the same branch, two locks on the same path from different agents
    // (e.g. stale lock + new lock) ARE a conflict.
    const result = checkAgentLocks({
      changedFiles: ['src/views/Project/Detail.vue'],
      context: { agent: 'codex', branch: 'codex/agent-lock-gate' },
      locks: [
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'claude',
          branch: 'codex/agent-lock-gate',
          task: 'project-detail',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: '项目详情页重构',
        },
        {
          path: 'src/views/Project/Detail.vue',
          scope: 'file',
          owner: 'gemini',
          branch: 'codex/agent-lock-gate',
          task: 'project-detail-copy',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: '重复锁',
        },
      ],
      now,
    })

    expect(result.ok).toBe(false)
    expect(result.duplicateLocks).toHaveLength(1)
  })

  it('skips duplicate lock check when no files are changed', () => {
    // When nothing is changing, duplicate locks across unrelated task branches
    // are harmless — they represent two agents independently holding their own
    // locks and do not create a collision risk.
    const result = checkAgentLocks({
      changedFiles: [],
      context: { agent: 'codex', branch: 'codex/agent-lock-gate' },
      locks: [
        {
          path: 'src/router/index.js',
          scope: 'file',
          owner: 'agent-a',
          branch: 'agent-a/my-task',
          task: 'my-task',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: 'Add route A',
        },
        {
          path: 'src/router/index.js',
          scope: 'file',
          owner: 'agent-b',
          branch: 'agent-b/other-task',
          task: 'other-task',
          expiresAt: '2026-05-05T23:59:59+08:00',
          reason: 'Add route B',
        },
      ],
      now,
    })

    expect(result.ok).toBe(true)
    expect(result.duplicateLocks).toHaveLength(0)
  })

  it('merges lock registries from remote branches and preserves their source', () => {
    const locks = collectLocksFromRegistries([
      {
        source: 'working-tree',
        registry: {
          locks: [
            {
              path: 'src/views/Project/Detail.vue',
              scope: 'file',
              owner: 'codex',
              branch: 'codex/agent-lock-gate',
              task: 'agent-lock-gate',
              expiresAt: '2026-05-05T23:59:59+08:00',
              reason: '本分支锁',
            },
          ],
        },
      },
      {
        source: 'origin/claude/project-detail',
        registry: {
          locks: [
            {
              path: 'src/views/Project/components',
              scope: 'directory',
              owner: 'claude',
              branch: 'claude/project-detail',
              task: 'project-detail',
              expiresAt: '2026-05-05T23:59:59+08:00',
              reason: '远端分支锁',
            },
          ],
        },
      },
    ], { currentBranch: 'codex/agent-lock-gate' })

    expect(locks).toEqual([
      expect.objectContaining({ owner: 'codex', source: 'working-tree' }),
      expect.objectContaining({ owner: 'claude', source: 'origin/claude/project-detail' }),
    ])
  })

  it('includes all working-tree locks regardless of their branch field when currentBranch is main', () => {
    const locks = collectLocksFromRegistries([
      {
        source: 'working-tree',
        registry: {
          locks: [
            {
              path: 'src/views/Project/Detail.vue',
              scope: 'file',
              owner: 'claude',
              branch: 'claude/project-detail',
              task: 'project-detail',
              expiresAt: '2026-05-05T23:59:59+08:00',
              reason: '其他分支的锁文件被合并进 main 后不应过滤',
            },
          ],
        },
      },
    ], { currentBranch: 'main' })

    expect(locks).toHaveLength(1)
    expect(locks[0]).toMatchObject({ branch: 'claude/project-detail', source: 'working-tree' })
  })

  it('ignores inherited locks that do not belong to their source branch', () => {
    const locks = collectLocksFromRegistries(
      [
        {
          source: 'working-tree',
          registry: {
            locks: [
              {
                path: 'src/views/Project/Detail.vue',
                scope: 'file',
                owner: 'claude',
                branch: 'claude/old-task',
                task: 'old-task',
                expiresAt: '2026-05-05T23:59:59+08:00',
                reason: '从 main 继承来的旧锁',
              },
            ],
          },
        },
        {
          source: 'origin/codex/current-task',
          registry: {
            locks: [
              {
                path: 'src/views/Project/Detail.vue',
                scope: 'file',
                owner: 'claude',
                branch: 'claude/old-task',
                task: 'old-task',
                expiresAt: '2026-05-05T23:59:59+08:00',
                reason: '远端分支继承来的旧锁',
              },
            ],
          },
        },
        {
          source: 'origin/claude/current-task',
          registry: {
            locks: [
              {
                path: 'src/views/Project/components',
                scope: 'directory',
                owner: 'claude',
                branch: 'claude/current-task',
                task: 'current-task',
                expiresAt: '2026-05-05T23:59:59+08:00',
                reason: '真正属于该远端分支的锁',
              },
            ],
          },
        },
      ],
      { currentBranch: 'codex/current-task' },
    )

    expect(locks).toEqual([expect.objectContaining({ branch: 'claude/current-task' })])
  })

  it('parses untracked files from porcelain status output', () => {
    expect(
      parsePorcelainUntrackedFiles(` M package.json
?? .agent-locks.yml
?? scripts/fixtures/
A  scripts/tracked-file.mjs
`),
    ).toEqual(['.agent-locks.yml', 'scripts/fixtures/'])
  })

  it('resolves GitHub pull request head branch when Git checkout is detached', () => {
    expect(
      resolveCurrentBranch('', {
        GITHUB_HEAD_REF: 'codex/agent-lock-gate',
        GITHUB_REF_NAME: '12/merge',
      }),
    ).toBe('codex/agent-lock-gate')
  })
})

describe('findSelfMergeOrphans', () => {
  const futureExpiry = '2026-06-01T00:00:00Z'

  it('flags PRs that add their own lock entry on a per-task file', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const addedLocks = [
      {
        path: 'src/views/Dashboard.vue',
        scope: 'file',
        owner: 'claude',
        branch: 'claude/some-task',
        expiresAt: futureExpiry,
        reason: 'PR self-lock',
        source: 'per-task:some-task.yml',
      },
    ]
    const orphans = findSelfMergeOrphans({
      addedLocks,
      prHeadBranch: 'claude/some-task',
      prBaseBranch: 'main',
    })
    expect(orphans).toHaveLength(1)
    expect(orphans[0]).toMatchObject({ branch: 'claude/some-task' })
  })

  it('does not flag when added lock belongs to a different branch', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: 'x',
          scope: 'file',
          branch: 'codex/other',
          expiresAt: futureExpiry,
          source: 'per-task:codex-other.yml',
        },
      ],
      prHeadBranch: 'claude/some-task',
      prBaseBranch: 'main',
    })
    expect(orphans).toHaveLength(0)
  })

  it('does not flag when PR is not targeting main', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: 'x',
          scope: 'file',
          branch: 'claude/some-task',
          expiresAt: futureExpiry,
        },
      ],
      prHeadBranch: 'claude/some-task',
      prBaseBranch: 'develop',
    })
    expect(orphans).toHaveLength(0)
  })

  it('whitelists janitor branches', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: 'x',
          scope: 'file',
          branch: 'chore/janitor-12345',
          expiresAt: futureExpiry,
        },
      ],
      prHeadBranch: 'chore/janitor-12345',
      prBaseBranch: 'main',
    })
    expect(orphans).toHaveLength(0)
  })

  it('whitelists clean-orphan-lock branches', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: 'x',
          scope: 'file',
          branch: 'chore/clean-orphan-lock-256',
          expiresAt: futureExpiry,
        },
      ],
      prHeadBranch: 'chore/clean-orphan-lock-256',
      prBaseBranch: 'main',
    })
    expect(orphans).toHaveLength(0)
  })

  it('flags legacy-file locks too', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: 'x',
          scope: 'file',
          branch: 'claude/legacy',
          expiresAt: futureExpiry,
          source: 'legacy',
        },
      ],
      prHeadBranch: 'claude/legacy',
      prBaseBranch: 'main',
    })
    expect(orphans).toHaveLength(1)
  })

  it('does not flag when the added lock covers a hot-path file the PR is changing', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: '.github/workflows/agent-locks-janitor.yml',
          scope: 'file',
          branch: 'claude/some-task',
          expiresAt: futureExpiry,
          source: 'per-task:some-task.yml',
        },
      ],
      prHeadBranch: 'claude/some-task',
      prBaseBranch: 'main',
      hotPaths: [{ pattern: '.github/workflows/**', reason: 'CI changes' }],
    })
    expect(orphans).toHaveLength(0)
  })

  it('still flags an unrelated self-lock even when other locks cover a hot-path', async () => {
    const { findSelfMergeOrphans } = await import('./check-agent-locks.mjs')
    const orphans = findSelfMergeOrphans({
      addedLocks: [
        {
          path: '.github/workflows/agent-locks-janitor.yml',
          scope: 'file',
          branch: 'claude/some-task',
          expiresAt: futureExpiry,
        },
        {
          path: 'unrelated/file.txt',
          scope: 'file',
          branch: 'claude/some-task',
          expiresAt: futureExpiry,
        },
      ],
      prHeadBranch: 'claude/some-task',
      prBaseBranch: 'main',
      hotPaths: [{ pattern: '.github/workflows/**', reason: 'CI changes' }],
    })
    expect(orphans).toHaveLength(1)
    expect(orphans[0].path).toBe('unrelated/file.txt')
  })
})
