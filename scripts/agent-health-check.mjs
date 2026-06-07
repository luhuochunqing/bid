#!/usr/bin/env node
// Input: all xiyu-bid-poc worktrees' .runtime/dev-services/ state files and pid liveness
// Output: human-readable health report across worktrees (backend/frontend/sidecar, fail-state, recent errors)
// Pos: scripts/多 Agent 健康度聚合工具
// 维护声明: 若 .runtime/dev-services/ 布局或 fail-state 格式变化，请同步更新 RULES.md 和 scripts/README.md。

import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'

const DEFAULT_WORKTREES_ROOT = '/Users/user/xiyu/worktrees'
const DEFAULT_MAIN_REPO = '/Users/user/xiyu/xiyu-bid-poc'
const RUNTIME_SUBPATH = '.runtime/dev-services'
const LOG_TAIL_BYTES = 4000

function main() {
  const options = parseArgs(process.argv.slice(2))
  const worktrees = discoverWorktrees(options)

  if (worktrees.length === 0) {
    console.log('agent-health-check: no worktrees with .runtime/dev-services/ found.')
    console.log(`  searched: ${options.mainRepo}, ${options.worktreesRoot}/*`)
    return
  }

  const reports = worktrees.map((wt) => inspectWorktree(wt))

  if (options.format === 'json') {
    console.log(JSON.stringify(reports, null, 2))
    return
  }

  printHumanReport(reports)
}

function discoverWorktrees({ mainRepo, worktreesRoot }) {
  const candidates = []

  if (fs.existsSync(path.join(mainRepo, RUNTIME_SUBPATH))) {
    candidates.push({ name: 'main', path: mainRepo })
  }

  if (fs.existsSync(worktreesRoot)) {
    for (const entry of fs.readdirSync(worktreesRoot, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue
      const full = path.join(worktreesRoot, entry.name)
      if (fs.existsSync(path.join(full, RUNTIME_SUBPATH))) {
        candidates.push({ name: entry.name, path: full })
      }
    }
  }

  return candidates
}

function inspectWorktree({ name, path: wtPath }) {
  const runtimeDir = path.join(wtPath, RUNTIME_SUBPATH)
  const branch = readBranch(wtPath)
  const failState = readFailState(path.join(runtimeDir, 'backend.fail-state'))
  const backend = inspectService(runtimeDir, 'backend')
  const frontend = inspectService(runtimeDir, 'frontend')
  const sidecar = inspectService(runtimeDir, 'sidecar')

  return {
    name,
    path: wtPath,
    branch,
    failState,
    backend,
    frontend,
    sidecar,
  }
}

function readBranch(wtPath) {
  const result = spawnSync('git', ['-C', wtPath, 'rev-parse', '--abbrev-ref', 'HEAD'], {
    encoding: 'utf8',
  })
  if (result.status !== 0) return '(unknown)'
  const output = result.stdout.trim()
  if (output === 'HEAD') {
    const sha = spawnSync('git', ['-C', wtPath, 'rev-parse', '--short', 'HEAD'], { encoding: 'utf8' })
    return `(detached ${sha.stdout.trim()})`
  }
  return output
}

function readFailState(failStatePath) {
  if (!fs.existsSync(failStatePath)) return null
  try {
    const content = fs.readFileSync(failStatePath, 'utf8')
    const obj = {}
    for (const line of content.split(/\r?\n/)) {
      const idx = line.indexOf(':')
      if (idx === -1) continue
      obj[line.slice(0, idx).trim()] = line.slice(idx + 1).trim()
    }
    return obj
  } catch {
    return { error: 'could not read fail-state file' }
  }
}

function inspectService(runtimeDir, service) {
  const pidPath = path.join(runtimeDir, `${service}.pid`)
  const logPath = path.join(runtimeDir, `${service}.log`)
  const identityPath = path.join(runtimeDir, `${service}.identity`)

  const info = {
    present: fs.existsSync(pidPath),
    pid: null,
    alive: false,
    identity: null,
    logSize: null,
    logMtime: null,
    lastError: null,
  }

  if (!info.present) return info

  try {
    info.pid = fs.readFileSync(pidPath, 'utf8').trim()
    info.alive = isPidAlive(info.pid)
  } catch {}

  if (fs.existsSync(identityPath)) {
    info.identity = fs.readFileSync(identityPath, 'utf8').trim()
  }

  if (fs.existsSync(logPath)) {
    const stat = fs.statSync(logPath)
    info.logSize = stat.size
    info.logMtime = stat.mtime.toISOString()
    info.lastError = scanLastError(logPath, stat.size)
  }

  return info
}

function isPidAlive(pid) {
  if (!pid || !/^\d+$/.test(pid)) return false
  try {
    process.kill(Number(pid), 0)
    return true
  } catch {
    return false
  }
}

function scanLastError(logPath, size) {
  try {
    const start = Math.max(0, size - LOG_TAIL_BYTES)
    const fd = fs.openSync(logPath, 'r')
    const buffer = Buffer.alloc(size - start)
    fs.readSync(fd, buffer, 0, buffer.length, start)
    fs.closeSync(fd)
    const tail = buffer.toString('utf8')
    const lines = tail.split(/\r?\n/).reverse()
    for (const line of lines) {
      if (/\[ERROR\]|Caused by|BUILD FAILURE|FlywayValidateException|cannot find symbol/.test(line)) {
        return line.length > 200 ? line.slice(0, 200) + '...' : line
      }
    }
    return null
  } catch {
    return null
  }
}

function printHumanReport(reports) {
  console.log('agent-health-check: worktree status snapshot\n')
  for (const r of reports) {
    console.log(`=== ${r.name} (${r.branch}) ===`)
    console.log(`  path: ${r.path}`)

    if (r.failState) {
      console.log(`  STATUS: STOPPED (backend.fail-state present)`)
      for (const [k, v] of Object.entries(r.failState)) {
        console.log(`    ${k}: ${v}`)
      }
      console.log('')
      continue
    }

    for (const [service, info] of [['backend', r.backend], ['frontend', r.frontend], ['sidecar', r.sidecar]]) {
      if (!info.present) {
        console.log(`  ${service}: not configured`)
        continue
      }
      const liveness = info.alive ? 'ALIVE' : 'DEAD'
      const logInfo = info.logSize != null
        ? ` log=${formatSize(info.logSize)} mtime=${info.logMtime}`
        : ''
      console.log(`  ${service}: ${liveness} pid=${info.pid || '?'}${logInfo}`)
      if (info.lastError) {
        console.log(`    last_error: ${info.lastError}`)
      }
    }
    console.log('')
  }

  // Git wrapper safety (system-level --no-verify prohibition) — global for the invoking worktree
  console.log('=== git wrapper safety (across current shell env) ===')
  try {
    const res = spawnSync('bash', ['scripts/check-git-wrapper.sh'], { stdio: 'inherit' })
    if (res.status !== 0) {
      console.log('  git-wrapper: FAIL (see above output)')
    }
  } catch (e) {
    console.log('  git-wrapper: could not execute check (' + e.message + ')')
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes}B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}K`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}M`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)}G`
}

function parseArgs(args) {
  const options = {
    mainRepo: DEFAULT_MAIN_REPO,
    worktreesRoot: DEFAULT_WORKTREES_ROOT,
    format: 'human',
  }
  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i]
    if (arg === '--main-repo') options.mainRepo = args[++i]
    else if (arg === '--worktrees-root') options.worktreesRoot = args[++i]
    else if (arg === '--json') options.format = 'json'
  }
  return options
}

main()
