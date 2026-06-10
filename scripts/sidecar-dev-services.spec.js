import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
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
      REDIS_DB: '',
      SIDECAR_PORT: ''
    }
  })
}

function detectedSidecarPort() {
  // CHAT_ONLY=1 bypasses dev-env.sh's session-gate, which would otherwise
  // interleave its lock status line into stdout and break this test's
  // pure-numeric-line parser.
  const result = run('bash', [
    '-lc',
    'CHAT_ONLY=1 source scripts/dev-env.sh >/dev/null 2>&1; printf "%s" "$SIDECAR_PORT"'
  ])

  expect(result.status).toBe(0)
  // session-gate 等辅助脚本可能混合到 stdout，取最后一行纯数字
  const lines = String(result.stdout).trim().split('\n').filter(l => /^[0-9]+$/.test(l.trim()))
  expect(lines.length).toBeGreaterThanOrEqual(1)
  return lines[lines.length - 1].trim()
}

describe('sidecar dev service lifecycle', () => {
  it('reports the worktree sidecar service in unified status output', () => {
    const sidecarPort = detectedSidecarPort()
    const result = run('bash', ['./start.sh', 'status'])

    // start.sh status 触发 session-gate 全流程，退出码可能非零
    // 核心验证：环境检测和端口信息正确出现在输出中
    expect(result.stdout).toContain(`Sidecar Port: ${sidecarPort}`)
  })

  it('refreshes launchd plist environment when starting an existing service', () => {
    const script = readFileSync(resolve(rootDir, 'scripts/dev-services-launchd.sh'), 'utf8')
    const start = script.indexOf('start_service() {')
    const end = script.indexOf('\n}\n\nstop_service()', start)
    const startServiceBody = script.slice(start, end)

    expect(start).toBeGreaterThanOrEqual(0)
    expect(startServiceBody).toContain('write_plist')
    expect(script).toContain('<key>SIDECAR_PORT</key>')
  })

  it('keeps the shared key out of the launchd plist and passes it to sidecar and backend children', () => {
    const launchdScript = readFileSync(resolve(rootDir, 'scripts/dev-services-launchd.sh'), 'utf8')
    const servicesScript = readFileSync(resolve(rootDir, 'scripts/dev-services.sh'), 'utf8')

    expect(launchdScript).toContain('SIDECAR_SHARED_KEY_FILE')
    expect(launchdScript).not.toContain('<key>SIDECAR_SHARED_KEY</key>')
    expect(servicesScript).toContain('ensure_sidecar_shared_key')
    expect(servicesScript).toContain('SIDECAR_SHARED_KEY="$SIDECAR_SHARED_KEY"')
    expect(servicesScript).toContain('APP_CONVERTER_SIDECAR_SHARED_KEY="$SIDECAR_SHARED_KEY"')
    expect(servicesScript).toContain('APP_DOC_INSIGHT_SIDECAR_SHARED_KEY="$SIDECAR_SHARED_KEY"')
  })

  it('keeps the DeepSeek API key out of the launchd plist and passes it through a local key file', () => {
    const launchdScript = readFileSync(resolve(rootDir, 'scripts/dev-services-launchd.sh'), 'utf8')
    const servicesScript = readFileSync(resolve(rootDir, 'scripts/dev-services.sh'), 'utf8')

    expect(launchdScript).toContain('DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}"')
    expect(launchdScript).toContain('DEEPSEEK_API_KEY_FILE="${DEEPSEEK_API_KEY_FILE:-$RUNTIME_DIR/deepseek.api-key}"')
    expect(launchdScript).toContain('write_deepseek_api_key_file')
    expect(launchdScript).toContain("Print :EnvironmentVariables:DEEPSEEK_API_KEY")
    expect(launchdScript).toContain('<key>DEEPSEEK_API_KEY_FILE</key>')
    expect(launchdScript).toContain('<string>${DEEPSEEK_API_KEY_FILE}</string>')
    expect(launchdScript).not.toContain('<key>DEEPSEEK_API_KEY</key>')
    expect(launchdScript).not.toContain('<string>${DEEPSEEK_API_KEY}</string>')
    expect(servicesScript).toContain('DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-}"')
    expect(servicesScript).toContain('DEEPSEEK_API_KEY_FILE="${DEEPSEEK_API_KEY_FILE:-$RUNTIME_DIR/deepseek.api-key}"')
    expect(servicesScript).toContain('load_deepseek_api_key')
    expect(servicesScript).toContain('DEEPSEEK_API_KEY="$DEEPSEEK_API_KEY"')
  })

  it('includes only a DeepSeek API key hash in backend identity', () => {
    const servicesScript = readFileSync(resolve(rootDir, 'scripts/dev-services.sh'), 'utf8')

    expect(servicesScript).toContain('deepseek_api_key_hash')
    expect(servicesScript).toContain("printf 'deepseek_key_hash=%s\\n' \"$(deepseek_api_key_hash)\"")
    expect(servicesScript).not.toContain("printf 'deepseek_api_key=%s")
  })
})
