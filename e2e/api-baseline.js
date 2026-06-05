// Input: Playwright env vars, rehearsal state markers, and health endpoints
// Output: shared helpers for managed API-backed Playwright baseline bootstrapping
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const DEFAULT_API_BASE_URL = 'http://127.0.0.1:18080'
const DEFAULT_WEB_BASE_URL = 'http://127.0.0.1:1314'
const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const stateDir = path.resolve(process.env.STATE_DIR || path.join(rootDir, '.rehearsal'))
const stackMarkerPath = path.join(stateDir, 'playwright-api-stack.started')
const backendPidPath = path.join(stateDir, 'backend.pid')
const frontendPidPath = path.join(stateDir, 'frontend.pid')

function pidFileAlive(pidPath) {
  if (!fs.existsSync(pidPath)) {
    return false
  }

  const pid = Number(fs.readFileSync(pidPath, 'utf8').trim())
  if (!Number.isInteger(pid) || pid <= 0) {
    return false
  }

  try {
    process.kill(pid, 0)
    return true
  } catch {
    return false
  }
}

export function resolveApiBaseUrl() {
  return process.env.PLAYWRIGHT_API_BASE_URL || DEFAULT_API_BASE_URL
}

export function resolveWebBaseUrl() {
  return process.env.PLAYWRIGHT_BASE_URL || DEFAULT_WEB_BASE_URL
}

export async function isHttpReady(url) {
  try {
    const response = await fetch(url)
    return response.ok
  } catch {
    return false
  }
}

export async function ensureManagedStackReady() {
  const apiReady = await isHttpReady(`${resolveApiBaseUrl()}/actuator/health`)
  const webReady = await isHttpReady(resolveWebBaseUrl())
  const managedReady = fs.existsSync(stackMarkerPath) &&
    pidFileAlive(backendPidPath) &&
    pidFileAlive(frontendPidPath)

  return { apiReady, webReady, managedReady, ready: apiReady && webReady && managedReady }
}
