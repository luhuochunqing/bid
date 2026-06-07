#!/usr/bin/env node
// Input: --branch <name> — merged branch to release locks for
// Output: removes matching locks from .agent-locks/*.yml; deletes empty files
// Pos: scripts/Agent 锁 — PR 合并时释放分支锁
// 维护声明: 若锁存储路径或 YAML schema 变化，请同步更新 scripts/lib/agent-lock-store.mjs 和 scripts/README.md。

import fs from 'node:fs'
import path from 'node:path'

import {
  PER_TASK_LOCK_DIR,
  writePerTaskRegistry,
} from './lib/agent-lock-store.mjs'
import { parseAgentLocks } from './check-agent-locks.mjs'

function main() {
  const branch = parseArgs()
  if (!branch) {
    console.error('Usage: node scripts/agent-locks-release-branch.mjs --branch <branch>')
    process.exit(1)
  }

  const rootDir = process.cwd()
  const lockDir = path.join(rootDir, PER_TASK_LOCK_DIR)

  if (!fs.existsSync(lockDir)) {
    console.log(JSON.stringify({ branch, filesScanned: 0, locksRemoved: 0, changed: false }))
    return
  }

  let totalRemoved = 0
  let filesScanned = 0
  const changedFiles = []

  for (const entry of fs.readdirSync(lockDir).sort()) {
    if (!entry.endsWith('.yml')) continue
    const filePath = path.join(lockDir, entry)
    if (!fs.statSync(filePath).isFile()) continue

    filesScanned++
    const task = entry.replace(/\.yml$/, '')
    const registry = parseAgentLocks(fs.readFileSync(filePath, 'utf8'))
    const before = (registry.locks || []).length

    const kept = (registry.locks || []).filter((lock) => lock.branch !== branch)
    const removed = before - kept.length
    if (removed === 0) continue

    totalRemoved += removed
    writePerTaskRegistry({ rootDir, task, locks: kept })
    const stillExists = fs.existsSync(filePath)
    changedFiles.push({
      file: entry,
      locksRemoved: removed,
      deleted: !stillExists,
    })
  }

  const changed = totalRemoved > 0

  console.log(JSON.stringify({
    branch,
    filesScanned,
    locksRemoved: totalRemoved,
    filesChanged: changedFiles.length,
    filesDeleted: changedFiles.filter((f) => f.deleted).length,
    files: changedFiles,
    changed,
  }))

  if (!changed) process.exit(0)
}

function parseArgs() {
  const args = process.argv.slice(2)
  for (let i = 0; i < args.length; i++) {
    if (args[i] === '--branch' && args[i + 1]) {
      return args[i + 1]
    }
  }
  return ''
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main()
}
