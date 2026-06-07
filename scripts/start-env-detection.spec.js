import { describe, expect, it } from 'vitest'
import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function run(command, args) {
  return spawnSync(command, args, {
    cwd: rootDir,
    encoding: 'utf8',
    env: {
      ...process.env,
      BACKEND_PORT: '',
      FRONTEND_PORT: '',
      DB_NAME: '',
      REDIS_DB: ''
    }
  })
}

function detectedEnvironmentLines() {
  const result = run('bash', [
    '-lc',
    [
      'source scripts/dev-env.sh >/dev/null',
      'printf "Frontend Port: %s\\n" "$FRONTEND_PORT"',
      'printf "Backend Port: %s\\n" "$BACKEND_PORT"',
      'printf "Sidecar Port: %s\\n" "$SIDECAR_PORT"',
      'printf "DB Name: %s\\n" "$DB_NAME"',
      'printf "Redis DB: %s\\n" "$REDIS_DB"'
    ].join('; ')
  ])

  expect(result.status).toBe(0)
  return result.stdout.trim().split('\n')
}

describe('start.sh environment detection', () => {
  it('loads the current worktree dev environment before delegating', () => {
    const result = run('bash', ['./start.sh', 'status'])

    expect(result.status).toBe(0)
    for (const line of detectedEnvironmentLines()) {
      expect(result.stdout).toContain(line)
    }
  })
})
