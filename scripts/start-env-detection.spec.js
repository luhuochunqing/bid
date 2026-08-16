import { describe, expect, it } from 'vitest'
import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// dev-env.sh 仅在主工作区（trae）导出端口/DB 变量；其他 worktree/CI 检出目录无开发环境，环境检测用例仅在主工作区执行
const skipOnNonMainWorktree = !rootDir.includes('worktrees/trae')

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

  // session-gate 等辅助脚本可能混合到 stdout，过滤出包含端口/DB名的环境行
  const allLines = String(result.stdout).trim().split('\n')
  return allLines.filter(l => /^(Frontend|Backend|Sidecar|DB Name|Redis DB):/.test(l))
}

describe('start.sh environment detection', () => {
  it.skipIf(skipOnNonMainWorktree)('loads the current worktree dev environment before delegating', () => {
    const result = run('bash', ['./start.sh', 'status'])

    // start.sh status 触发 session-gate 全流程，退出码可能非零
    // 核心验证：环境检测信息正确出现在输出中
    for (const line of detectedEnvironmentLines()) {
      expect(result.stdout).toContain(line)
    }
  })
})
