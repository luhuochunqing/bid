#!/usr/bin/env node
// Input: optional subcommand (status|clean|reap)
// Output: multi-agent housekeeping report + cleanup actions
// Pos: scripts/ — Multi-Agent housekeeping tool
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import process from 'node:process'

const ROOT_DIR = '/Users/user/xiyu/xiyu-bid-poc'
const WORKTREE_DIRS = [
  '/Users/user/xiyu/xiyu-bid-poc',
  '/Users/user/.codex/worktrees/85e2/xiyu-bid-poc',
  '/Users/user/xiyu/worktrees/claude',
  '/Users/user/xiyu/worktrees/codex',
  '/Users/user/xiyu/worktrees/cursor',
  '/Users/user/xiyu/worktrees/gemini',
  '/Users/user/xiyu/worktrees/integrator',
  '/Users/user/xiyu/worktrees/qoder',
  '/Users/user/xiyu/worktrees/trae',
]

const SUBCOMMAND = process.argv[2] || 'status'

function runGit(cwd, args, opts = {}) {
  const result = spawnSync('git', args, {
    cwd, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, ...opts,
  })
  return result
}

function getAgentName(dir) {
  if (dir.includes('/.codex/worktrees/85e2/')) return 'codex'
  if (dir.includes('/worktrees/')) {
    const parts = dir.split('/')
    return parts[parts.indexOf('worktrees') + 1]
  }
  if (dir === ROOT_DIR) return 'main'
  return '?'
}

const REGEX_BRANCH = /branch:\s*"?([^"\n]+)/
const REGEX_EXPIRES = /expiresAt:\s*"?([^"\n]+)/

function collectWorktreeStatus() {
  const statuses = []
  for (const dir of WORKTREE_DIRS) {
    if (!fs.existsSync(dir)) continue
    const branchRes = runGit(dir, ['rev-parse', '--abbrev-ref', 'HEAD'])
    const branch = branchRes.status === 0 ? branchRes.stdout.trim() : '(detached)'
    const statusRes = runGit(dir, ['status', '--porcelain'])
    const uncommitted = statusRes.status === 0
      ? statusRes.stdout.trim().split('\n').filter(Boolean).length : -1
    const unpushedRes = runGit(dir, ['log', '--oneline', '@{u}..HEAD', '--', '.'])
    const unpushed = unpushedRes.status === 0
      ? unpushedRes.stdout.trim().split('\n').filter(Boolean).length : -1
    statuses.push({ dir, name: getAgentName(dir), branch, uncommitted, unpushed })
  }
  return statuses
}

function collectLockStatus(repoRoot) {
  const locksDir = path.join(repoRoot, '.agent-locks')
  if (!fs.existsSync(locksDir)) return { files: [], total: 0, stale: 0 }
  const files = fs.readdirSync(locksDir).filter(f => f.endsWith('.yml'))
  const lockStatuses = []
  for (const file of files) {
    const content = fs.readFileSync(path.join(locksDir, file), 'utf8')
    const bMatch = content.match(REGEX_BRANCH)
    const eMatch = content.match(REGEX_EXPIRES)
    const branch = bMatch ? bMatch[1].trim() : '?'
    const expiresAt = eMatch ? new Date(eMatch[1].trim()) : null
    const expired = expiresAt ? expiresAt < new Date() : false
    const branchRes = runGit(repoRoot, ['branch', '--list', branch])
    const branchExists = branchRes.status === 0 && branchRes.stdout.trim().length > 0
    // Also check remote branch
    const remoteRes = runGit(repoRoot, ['branch', '-r', '--list', 'origin/' + branch])
    const remoteExists = remoteRes.status === 0 && remoteRes.stdout.trim().length > 0
    const mergedRes = runGit(repoRoot, ['branch', '--merged', 'origin/main', '--list', branch])
    const isMerged = mergedRes.status === 0 && mergedRes.stdout.trim().length > 0
    const mergedRemote = runGit(repoRoot, ['branch', '-r', '--merged', 'origin/main', '--list', 'origin/' + branch])
    const isMergedRemote = mergedRemote.status === 0 && mergedRemote.stdout.trim().length > 0
    lockStatuses.push({ file, branch, expired, branchExists, isMerged })
  }
  const stale = lockStatuses.filter(l => (!l.branchExists && !l.remoteExists) || l.expired || l.isMerged || l.isMergedRemote).length
  return { files: lockStatuses, total: files.length, stale }
}

function collectBranchStatus(repoRoot) {
  const localRes = runGit(repoRoot, ['branch', '--list'])
  const localBranches = localRes.status === 0
    ? localRes.stdout.trim().split('\n').filter(Boolean).map(b => b.replace(/^[*+] /, '').trim()) : []
  const taskBranches = localBranches.filter(b => !b.startsWith('agent/') && b !== 'main')
  const mergedRes = runGit(repoRoot, ['branch', '--merged', 'origin/main'])
  const mergedBranches = mergedRes.status === 0
    ? mergedRes.stdout.trim().split('\n').filter(Boolean).map(b => b.replace(/^[*+] /, '').trim()) : []
  const branchStatuses = taskBranches.map(b => ({ name: b, isMerged: mergedBranches.includes(b), stale: mergedBranches.includes(b) }))
  return { branches: branchStatuses, total: taskBranches.length, stale: branchStatuses.filter(b => b.stale).length }
}

function collectRemoteStatus(repoRoot) {
  const res = runGit(repoRoot, ['branch', '-r', '--merged', 'origin/main'])
  if (res.status !== 0) return { stale: [] }
  const branches = [...new Set(res.stdout.trim().split('\n').filter(Boolean)
    .map(b => b.trim().replace('origin/', '')).filter(b => b !== 'main' && b !== 'HEAD'))]
  return { stale: branches }
}

function printReport(ws, locks, branches, remoteBranches) {
  const SEP = '\u2500'.repeat(52)
  console.log(`\n${'='.repeat(56)}`)
  console.log('  Agent Housekeeping Report')
  console.log(`  Generated: ${new Date().toISOString()}`)
  console.log(`${'='.repeat(56)}`)
  
  console.log(`\n[Worktrees] (${ws.length} total):`)
  console.log(`  ${SEP}`)
  for (const w of ws) {
    const shortDir = w.dir.replace('/Users/user/', '~').replace('/.codex/worktrees/85e2/', '/.codex/wt/')
    const label = `${w.name}`.padEnd(8) + `[${shortDir}]`.padEnd(36)
    const b = w.branch.substring(0, 30).padEnd(32)
    let icon, txt
    if (w.uncommitted > 0) { icon = '\u26a0'; txt = `${w.uncommitted} uncommitted` }
    else if (w.unpushed > 0) { icon = '\u26a0'; txt = `${w.unpushed} unpushed` }
    else { icon = '\u2713'; txt = 'clean' }
    console.log(`  ${icon} ${label} ${b} ${txt}`)
  }
  
  console.log(`\n[Locks] (${locks.total} total, ${locks.stale} stale):`)
  console.log(`  ${SEP}`)
  if (locks.total === 0) { console.log('  (none)') }
  else for (const l of locks.files) {
    const status = (l.isMerged || l.isMergedRemote) ? '\u2717 merged' : (!l.branchExists && !l.remoteExists) ? '\u2717 no branch' : l.expired ? '\u2717 expired' : '\u2713 active'
    console.log(`  ${status.padEnd(14)} ${l.file.padEnd(40)} ${l.branch}`)
  }
  
  console.log(`\n[Branches] (${branches.total} task, ${branches.stale} stale):`)
  console.log(`  ${SEP}`)
  for (const b of branches.branches) {
    const status = b.stale ? '\u2717 merged, can delete' : '\u2713 active'
    console.log(`  ${status.padEnd(24)} ${b.name}`)
  }
  
  if (remoteBranches.stale.length > 0) {
    console.log(`\n[Remote Stale] (${remoteBranches.stale.length}):`)
    for (const b of remoteBranches.stale) console.log(`  \u2717 ${b}`)
  }
  
  const actions = []
  for (const w of ws) {
    if (w.uncommitted > 0) actions.push(`Check "${w.name}" — ${w.uncommitted} uncommitted file(s)`)
    if (w.unpushed > 0) actions.push(`Push "${w.name}" — ${w.unpushed} unpushed commit(s)`)
  }
  for (const b of branches.branches) if (b.stale) actions.push(`git branch -D ${b.name}`)
  for (const b of remoteBranches.stale) actions.push(`git push origin --delete ${b}`)
  for (const l of locks.files) if (l.isMerged) actions.push(`rm .agent-locks/${l.file} (${l.branch} merged)`)
  
  console.log(`\n[Actions] (${actions.length}):`)
  console.log(`  ${SEP}`)
  if (actions.length === 0) console.log('  Nothing to clean.')
  else for (let i = 0; i < actions.length; i++) console.log(`  ${i+1}. ${actions[i]}`)
  console.log()
}

function autoClean(ws, locks, repoRoot) {
  console.log('[housekeeping] Auto-cleaning...\n')
  // Remove stale locks
  for (const l of locks.files) {
    if ((l.isMerged || l.isMergedRemote) || (!l.branchExists && !l.remoteExists)) {
      const lockPath = path.join(repoRoot, '.agent-locks', l.file)
      try { fs.unlinkSync(lockPath); console.log(`  Deleted lock: ${l.file}`) }
      catch (e) { console.log(`  Failed: ${l.file}: ${e.message}`) }
    }
  }
  // Delete merged local branches
  const currentRes = runGit(repoRoot, ['rev-parse', '--abbrev-ref', 'HEAD'])
  const currentBranch = currentRes.status === 0 ? currentRes.stdout.trim() : ''
  const localRes = runGit(repoRoot, ['branch', '--merged', 'origin/main'])
  if (localRes.status === 0) {
    for (const b of localRes.stdout.trim().split('\n').filter(Boolean).map(b => b.trim())) {
      if (b === 'main' || b.startsWith('agent/') || b === currentBranch) continue
      const delRes = runGit(repoRoot, ['branch', '-D', b])
      if (delRes.status === 0) console.log(`  Deleted branch: ${b}`)
    }
  }
  console.log('\nClean complete. Run again to see remaining state.')
}

function reap(repoRoot) {
  console.log('[housekeeping] Reap mode: cleaning stale remote branches + locks...\n')
  const mergedRemoteRes = runGit(repoRoot, ['branch', '-r', '--merged', 'origin/main'])
  if (mergedRemoteRes.status === 0) {
    const branches = [...new Set(mergedRemoteRes.stdout.trim().split('\n').filter(Boolean)
      .map(b => b.trim().replace('origin/', '')).filter(b => b !== 'main' && b !== 'HEAD'))]
    for (const b of branches) {
      const delRes = runGit(repoRoot, ['push', 'origin', '--delete', b])
      if (delRes.status === 0) console.log(`  Deleted remote: ${b}`)
      else console.log(`  Failed remote ${b}: ${(delRes.stderr||'').trim().slice(0,60)}`)
    }
  }
  // Delete merged local
  const currentRes = runGit(repoRoot, ['rev-parse', '--abbrev-ref', 'HEAD'])
  const current = currentRes.status === 0 ? currentRes.stdout.trim() : ''
  const localRes = runGit(repoRoot, ['branch', '--merged', 'origin/main'])
  if (localRes.status === 0) {
    for (const b of localRes.stdout.trim().split('\n').filter(Boolean).map(b => b.trim())) {
      if (b === 'main' || b.startsWith('agent/') || b === current) continue
      runGit(repoRoot, ['branch', '-D', b])
      console.log(`  Deleted local: ${b}`)
    }
  }
  // Clean expired locks
  const locksDir = path.join(repoRoot, '.agent-locks')
  if (fs.existsSync(locksDir)) {
    for (const file of fs.readdirSync(locksDir).filter(f => f.endsWith('.yml'))) {
      const content = fs.readFileSync(path.join(locksDir, file), 'utf8')
      const bMatch = content.match(REGEX_BRANCH)
      const branch = bMatch ? bMatch[1].trim() : '?'
      const mergedRes = runGit(repoRoot, ['branch', '--list', branch])
      const exists = mergedRes.status === 0 && mergedRes.stdout.trim().length > 0
      const mergedCheck = runGit(repoRoot, ['branch', '--merged', 'origin/main', '--list', branch])
      const merged = mergedCheck.status === 0 && mergedCheck.stdout.trim().length > 0
      if (!exists || merged) {
        fs.unlinkSync(path.join(locksDir, file))
        console.log(`  Deleted lock: ${file}`)
      }
    }
  }
  runGit(repoRoot, ['remote', 'prune', 'origin'])
  console.log('\nReap complete.')
}

function main() {
  const repoRoot = ROOT_DIR
  if (!fs.existsSync(repoRoot)) { console.error('Repo not found:', repoRoot); process.exit(1) }
  runGit(repoRoot, ['fetch', 'origin', 'main', '--depth=1'], { stdio: ['ignore', 'pipe', 'pipe'] })
  const ws = collectWorktreeStatus()
  const locks = collectLockStatus(repoRoot)
  const branches = collectBranchStatus(repoRoot)
  const remoteBranches = collectRemoteStatus(repoRoot)
  switch (SUBCOMMAND) {
    case 'clean': autoClean(ws, locks, repoRoot); break
    case 'reap': reap(repoRoot); break
    default: printReport(ws, locks, branches, remoteBranches); break
  }
}
main()
