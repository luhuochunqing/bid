import { describe, expect, it } from 'vitest'
import { spawnSync } from 'node:child_process'

const script = 'scripts/agent-start-task.sh'

function runStartTask(args) {
  return spawnSync('bash', [script, ...args], {
    cwd: process.cwd(),
    encoding: 'utf8',
  })
}

describe('agent-start-task', () => {
  it('dry-runs initial file and directory locks after creating the task worktree', () => {
    const result = runStartTask([
      'codex',
      'lock-bootstrap',
      '--dry-run',
      '--lock',
      'src/views/Project/Detail.vue',
      '--lock-dir',
      'src/views/Project/components',
      '--lock-reason',
      '项目详情改造',
      '--lock-days',
      '2',
    ])

    expect(result.status).toBe(0)
    expect(result.stdout).toContain('base:     origin/main')
    expect(result.stdout).toContain('lock file:      src/views/Project/Detail.vue')
    expect(result.stdout).toContain('lock directory: src/views/Project/components')
    expect(result.stdout).toContain(
      'node scripts/manage-agent-locks.mjs acquire --path src/views/Project/Detail.vue --scope file',
    )
    expect(result.stdout).toContain('git push -u origin codex/lock-bootstrap')
  })

  it('accepts an explicit base ref before lock flags', () => {
    const result = runStartTask([
      'claude',
      'project-page',
      'upstream/main',
      '--dry-run',
      '--lock',
      'package.json',
    ])

    expect(result.status).toBe(0)
    expect(result.stdout).toContain('base:     upstream/main')
    expect(result.stdout).toContain('lock file:      package.json')
    expect(result.stdout).toContain('reason 任务 project-page 初始锁')
  })
})
