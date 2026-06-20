#!/usr/bin/env node
// Input: optional subcommand (status|clean|reap) [--dry-run]
// Output: multi-agent housekeeping report + cleanup actions
// Features: auto-discover worktrees, branch relation to origin/main, branch occupancy, stash, stale locks
// Pos: scripts/ — Multi-Agent housekeeping tool
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import process from 'node:process'

const ROOT_DIR = '/Users/user/xiyu/xiyu-bid-poc'

// 需要扫描的 worktree 父目录
const WORKTREE_SCAN_ROOTS = [
  '/Users/user/xiyu/worktrees',
  '/Users/user/.codex/worktrees',
]

// 硬编码兜底：老路径或特殊路径
const FALLBACK_WORKTREE_DIRS = [
  '/Users/user/xiyu/xiyu-bid-poc',
  '/Users/user/.codex/worktrees/85e2/xiyu-bid-poc',
  '/Users/user/xiyu/worktrees/claude',
  '/Users/user/xiyu/worktrees/codex',
  '/Users/user/xiyu/worktrees/cursor',
  '/Users/user/xiyu/worktrees/gemini',
  '/Users/user/xiyu/worktrees/kimi',
  '/Users/user/xiyu/worktrees/mimo',
  '/Users/user/xiyu/worktrees/qoder',
  '/Users/user/xiyu/worktrees/trae',
  '/Users/user/xiyu/worktrees/zcode',
]

const SUBCOMMAND = process.argv[2] || 'status'
const DRY_RUN = process.argv.includes('--dry-run')

function runGit(cwd, args, opts = {}) {
  const result = spawnSync('git', args, {
    cwd, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024, ...opts,
  })
  return result
}

function isGitRepo(dir) {
  if (!dir || !fs.existsSync(dir)) return false
  const res = runGit(dir, ['rev-parse', '--git-dir'])
  return res.status === 0 && res.stdout.trim().length > 0
}

function getAgentName(dir) {
  const resolved = path.resolve(dir)
  if (resolved === path.resolve(ROOT_DIR)) return 'main'
  // Codex 的 worktree 路径在 .codex/worktrees/<id>/ 下，按 <id> 区分
  const codexMatch = resolved.match(/\/\.codex\/worktrees\/([^/]+)/)
  if (codexMatch) return `codex-${codexMatch[1]}`
  const match = resolved.match(/\/worktrees\/([^/]+)/)
  if (match) return match[1]
  return path.basename(resolved)
}

function discoverWorktrees() {
  const found = new Set()

  // 1. 硬编码兜底
  for (const dir of FALLBACK_WORKTREE_DIRS) {
    if (isGitRepo(dir)) found.add(path.resolve(dir))
  }

  // 2. 扫描已知 worktree 父目录，发现隐藏工作区
  for (const root of WORKTREE_SCAN_ROOTS) {
    if (!fs.existsSync(root)) continue
    const entries = fs.readdirSync(root, { withFileTypes: true })
    for (const entry of entries) {
      if (!entry.isDirectory() || entry.name.startsWith('.')) continue

      if (root === '/Users/user/.codex/worktrees') {
        // Codex 结构可能是 .codex/worktrees/<id>/ 或 .codex/worktrees/<id>/xiyu-bid-poc
        const idDir = path.join(root, entry.name)
        if (isGitRepo(idDir)) {
          found.add(path.resolve(idDir))
        } else {
          const repoDir = path.join(idDir, 'xiyu-bid-poc')
          if (isGitRepo(repoDir)) found.add(path.resolve(repoDir))
        }
      } else {
        const repoDir = path.join(root, entry.name)
        if (isGitRepo(repoDir)) found.add(path.resolve(repoDir))
      }
    }
  }

  // 3. Git 官方 worktree 注册表（从主仓库读取）
  const wtList = runGit(ROOT_DIR, ['worktree', 'list', '--porcelain'])
  if (wtList.status === 0) {
    for (const line of wtList.stdout.split('\n')) {
      if (line.startsWith('worktree ')) {
        const dir = line.slice('worktree '.length).trim()
        if (isGitRepo(dir)) found.add(path.resolve(dir))
      }
    }
  }

  return [...found].sort()
}

const REGEX_BRANCH = /branch:\s*"?([^"\n]+)/
const REGEX_EXPIRES = /expiresAt:\s*"?([^"\n]+)/

function getBranchRelation(dir, branch) {
  if (!branch || branch === '(detached)') {
    return { merged: false, ahead: 0, behind: 0 }
  }
  const mergedRes = runGit(dir, ['branch', '--merged', 'origin/main', '--list', branch])
  const merged = mergedRes.status === 0 && mergedRes.stdout.trim().length > 0

  const aheadRes = runGit(dir, ['rev-list', '--count', `origin/main..${branch}`])
  const ahead = aheadRes.status === 0 ? parseInt(aheadRes.stdout.trim(), 10) || 0 : 0

  const behindRes = runGit(dir, ['rev-list', '--count', `${branch}..origin/main`])
  const behind = behindRes.status === 0 ? parseInt(behindRes.stdout.trim(), 10) || 0 : 0

  return { merged, ahead, behind }
}

function collectWorktreeStatus() {
  const dirs = discoverWorktrees()
  const statuses = []
  for (const dir of dirs) {
    const branchRes = runGit(dir, ['rev-parse', '--abbrev-ref', 'HEAD'])
    const branch = branchRes.status === 0 ? branchRes.stdout.trim() : '(detached)'

    const statusRes = runGit(dir, ['status', '--porcelain'])
    const uncommitted = statusRes.status === 0
      ? statusRes.stdout.trim().split('\n').filter(Boolean).length : -1

    const upstreamRes = runGit(dir, ['rev-parse', '--abbrev-ref', '@{u}'])
    const hasUpstream = upstreamRes.status === 0 && upstreamRes.stdout.trim().length > 0
    const unpushedRes = runGit(dir, ['log', '--oneline', '@{u}..HEAD', '--', '.'])
    const unpushed = hasUpstream && unpushedRes.status === 0
      ? unpushedRes.stdout.trim().split('\n').filter(Boolean).length : 0

    const relation = getBranchRelation(dir, branch)

    // detached HEAD 且当前提交已在 origin/main 中、工作区干净、无未推送提交，可安全移除
    const removable = branch === 'HEAD'
      && relation.ahead === 0
      && uncommitted === 0
      && unpushed === 0

    statuses.push({
      dir,
      name: getAgentName(dir),
      branch,
      uncommitted,
      unpushed,
      hasUpstream,
      relation,
      removable,
    })
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
    lockStatuses.push({ file, branch, expired, branchExists, remoteExists, isMerged, isMergedRemote })
  }
  const stale = lockStatuses.filter(l => (!l.branchExists && !l.remoteExists) || l.expired || l.isMerged || l.isMergedRemote).length
  return { files: lockStatuses, total: files.length, stale }
}

function collectBranchStatus(repoRoot) {
  const localRes = runGit(repoRoot, ['branch', '--format=%(refname:short)'])
  const localBranches = localRes.status === 0
    ? localRes.stdout.trim().split('\n').filter(Boolean) : []
  // 只关注可清理的 task 分支（非 agent/、非 main）
  const taskBranches = localBranches.filter(b => b !== 'main' && !b.startsWith('agent/'))
  const mergedRes = runGit(repoRoot, ['branch', '--merged', 'origin/main', '--format=%(refname:short)'])
  const mergedBranches = mergedRes.status === 0
    ? mergedRes.stdout.trim().split('\n').filter(Boolean) : []
  const branchStatuses = taskBranches.map(b => ({
    name: b,
    isMerged: mergedBranches.includes(b),
    stale: mergedBranches.includes(b),
  }))
  return {
    branches: branchStatuses,
    total: taskBranches.length,
    stale: branchStatuses.filter(b => b.stale).length,
  }
}

function collectBranchOccupancy(worktrees) {
  // 所有本地分支（从主仓库读取，因为共享 .git）
  const localRes = runGit(ROOT_DIR, ['branch', '--format=%(refname:short)'])
  if (localRes.status !== 0) return []
  const branches = localRes.stdout.trim().split('\n').filter(Boolean)
  const occupancy = []
  for (const branch of branches) {
    const occupant = worktrees.find(w => w.branch === branch)
    occupancy.push({ branch, worktree: occupant ? occupant.name : null })
  }
  return occupancy
}

function collectStashStatus(repoRoot) {
  // stash 是 repo 全局共享的，不是按 worktree 隔离的，统一从主仓库列出
  const res = runGit(repoRoot, ['stash', 'list'])
  if (res.status !== 0) return { entries: [], total: 0 }
  const entries = res.stdout.trim().split('\n').filter(Boolean)
  return { entries, total: entries.length }
}

function collectRemoteStatus(repoRoot) {
  const res = runGit(repoRoot, ['branch', '-r', '--merged', 'origin/main'])
  if (res.status !== 0) return { stale: [] }
  const branches = [...new Set(res.stdout.trim().split('\n').filter(Boolean)
    .map(b => b.trim().replace('origin/', '')).filter(b => b !== 'main' && !b.startsWith('HEAD')))]
  return { stale: branches }
}

function shortDir(dir) {
  return dir.replace('/Users/user/', '~/').replace('/.codex/worktrees/85e2/', '/.codex/wt/')
}

function formatRelation(branch, rel) {
  const parts = []
  if (rel.ahead > 0) parts.push(`+${rel.ahead}`)
  if (rel.behind > 0) parts.push(`-${rel.behind}`)
  const sync = parts.length > 0 ? parts.join('/') : 'sync'
  // main 显示同步状态即可；其他分支 merged 是高优先级清理信号
  if (branch === 'main') return sync
  if (rel.merged) return 'merged'
  return sync
}

function printReport(ws, locks, branches, remoteBranches, occupancy, stash) {
  const SEP = '\u2500'.repeat(80)
  console.log(`\n${'='.repeat(84)}`)
  console.log('  Agent Housekeeping Report')
  console.log(`  Generated: ${new Date().toISOString()}`)
  console.log(`${'='.repeat(84)}`)

  console.log(`\n[Worktrees] (${ws.length} discovered):`)
  console.log(`  ${SEP}`)
  for (const w of ws) {
    const rel = formatRelation(w.branch, w.relation)
    const dir = shortDir(w.dir).padEnd(36)
    const name = w.name.padEnd(8)
    const branch = w.branch.substring(0, 28).padEnd(30)

    let icon, txt
    if (w.uncommitted > 0) { icon = '\u26a0'; txt = `${w.uncommitted} uncommitted` }
    else if (!w.hasUpstream && w.relation.ahead > 0) { icon = '\u26a0'; txt = `${w.relation.ahead} unpushed (no upstream)` }
    else if (w.unpushed > 0) { icon = '\u26a0'; txt = `${w.unpushed} unpushed` }
    else { icon = w.removable ? '\u267b' : '\u2713'; txt = w.removable ? 'clean · removable' : 'clean' }

    const extras = [rel, txt].filter(Boolean).join(' | ')
    console.log(`  ${icon} ${name} ${branch} ${dir} ${extras}`)
  }

  if (occupancy.length > 0) {
    console.log(`\n[Branch Occupancy] (${occupancy.length} local branches):`)
    console.log(`  ${SEP}`)
    const attached = occupancy.filter(o => o.worktree)
    const detached = occupancy.filter(o => !o.worktree && o.branch !== 'main' && !o.branch.startsWith('agent/'))
    if (attached.length > 0) {
      console.log('  Currently checked out:')
      for (const o of attached) console.log(`    \u2713 ${o.branch.padEnd(40)} ${o.worktree}`)
    }
    if (detached.length > 0) {
      console.log('  Not checked out anywhere:')
      for (const o of detached) console.log(`    \u26a0 ${o.branch}`)
    }
  }

  console.log(`\n[Stash] (${stash.total} total):`)
  console.log(`  ${SEP}`)
  if (stash.total === 0) { console.log('  (none)') }
  else for (const entry of stash.entries) console.log(`  \u26a0 ${entry}`)

  console.log(`\n[Locks] (${locks.total} total, ${locks.stale} stale):`)
  console.log(`  ${SEP}`)
  if (locks.total === 0) { console.log('  (none)') }
  else for (const l of locks.files) {
    const status = (l.isMerged || l.isMergedRemote) ? '\u2717 merged' : (!l.branchExists && !l.remoteExists) ? '\u2717 no branch' : l.expired ? '\u2717 expired' : '\u2713 active'
    console.log(`  ${status.padEnd(14)} ${l.file.padEnd(40)} ${l.branch}`)
  }

  console.log(`\n[Task Branches] (${branches.total} total, ${branches.stale} stale):`)
  console.log(`  ${SEP}`)
  if (branches.branches.length === 0) console.log('  (none)')
  else for (const b of branches.branches) {
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
    if (!w.hasUpstream && w.relation.ahead > 0) actions.push(`Push "${w.name}" — ${w.relation.ahead} commit(s) with no upstream (git push -u origin ${w.branch})`)
    else if (w.unpushed > 0) actions.push(`Push "${w.name}" — ${w.unpushed} unpushed commit(s)`)
    if (w.removable) actions.push(`Remove worktree "${w.name}" — git -C ${ROOT_DIR} worktree remove ${w.dir}`)
  }
  if (stash.total > 0) actions.push(`Review stash — ${stash.total} stash(es) may need cleanup`)
  for (const b of branches.branches) if (b.stale) actions.push(`git branch -D ${b.name}`)
  for (const b of remoteBranches.stale) actions.push(`git push origin --delete ${b}`)
  for (const l of locks.files) {
    if (l.isMerged || l.isMergedRemote) actions.push(`rm .agent-locks/${l.file} (${l.branch} merged)`)
    else if (!l.branchExists && !l.remoteExists) actions.push(`rm .agent-locks/${l.file} (${l.branch} missing)`)
    else if (l.expired) actions.push(`rm .agent-locks/${l.file} (${l.branch} expired)`)
  }

  console.log(`\n[Actions] (${actions.length}):`)
  console.log(`  ${SEP}`)
  if (actions.length === 0) console.log('  Nothing to clean.')
  else for (let i = 0; i < actions.length; i++) console.log(`  ${i + 1}. ${actions[i]}`)
  console.log()
}

function autoClean(ws, locks, repoRoot, dryRun = false) {
  const prefix = dryRun ? '[dry-run] ' : ''
  console.log(`${prefix}[housekeeping] Auto-cleaning...\n`)
  // Remove stale locks
  for (const l of locks.files) {
    if ((l.isMerged || l.isMergedRemote) || (!l.branchExists && !l.remoteExists) || l.expired) {
      const lockPath = path.join(repoRoot, '.agent-locks', l.file)
      if (dryRun) {
        console.log(`  ${prefix}Would delete lock: ${l.file}`)
        continue
      }
      try { fs.unlinkSync(lockPath); console.log(`  Deleted lock: ${l.file}`) }
      catch (e) { console.log(`  Failed: ${l.file}: ${e.message}`) }
    }
  }
  // Delete merged local branches
  const currentRes = runGit(repoRoot, ['rev-parse', '--abbrev-ref', 'HEAD'])
  const currentBranch = currentRes.status === 0 ? currentRes.stdout.trim() : ''
  const localRes = runGit(repoRoot, ['branch', '--merged', 'origin/main'])
  if (localRes.status === 0) {
    for (const b of localRes.stdout.trim().split('\n').filter(Boolean).map(b => b.replace(/^[*+] /, '').trim())) {
      if (b === 'main' || b.startsWith('agent/') || b === currentBranch) continue
      if (dryRun) {
        console.log(`  ${prefix}Would delete branch: ${b}`)
        continue
      }
      const delRes = runGit(repoRoot, ['branch', '-D', b])
      if (delRes.status === 0) console.log(`  Deleted branch: ${b}`)
    }
  }
  console.log(`\n${prefix}Clean complete.${dryRun ? ' No changes made.' : ' Run again to see remaining state.'}`)
}

function reap(repoRoot, dryRun = false) {
  const prefix = dryRun ? '[dry-run] ' : ''
  console.log(`${prefix}[housekeeping] Reap mode: cleaning stale remote branches + locks...\n`)
  const mergedRemoteRes = runGit(repoRoot, ['branch', '-r', '--merged', 'origin/main'])
  if (mergedRemoteRes.status === 0) {
    const branches = [...new Set(mergedRemoteRes.stdout.trim().split('\n').filter(Boolean)
      .map(b => b.trim().replace('origin/', '')).filter(b => b !== 'main' && !b.startsWith('HEAD')))]
    for (const b of branches) {
      if (dryRun) {
        console.log(`  ${prefix}Would delete remote: ${b}`)
        continue
      }
      const delRes = runGit(repoRoot, ['push', 'origin', '--delete', b])
      if (delRes.status === 0) console.log(`  Deleted remote: ${b}`)
      else console.log(`  Failed remote ${b}: ${(delRes.stderr || '').trim().slice(0, 60)}`)
    }
  }
  // Delete merged local
  const currentRes = runGit(repoRoot, ['rev-parse', '--abbrev-ref', 'HEAD'])
  const current = currentRes.status === 0 ? currentRes.stdout.trim() : ''
  const localRes = runGit(repoRoot, ['branch', '--merged', 'origin/main'])
  if (localRes.status === 0) {
    for (const b of localRes.stdout.trim().split('\n').filter(Boolean).map(b => b.replace(/^[*+] /, '').trim())) {
      if (b === 'main' || b.startsWith('agent/') || b === current) continue
      if (dryRun) {
        console.log(`  ${prefix}Would delete local: ${b}`)
        continue
      }
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
      const eMatch = content.match(REGEX_EXPIRES)
      const branch = bMatch ? bMatch[1].trim() : '?'
      const expiresAt = eMatch ? new Date(eMatch[1].trim()) : null
      const expired = expiresAt ? expiresAt < new Date() : false
      const mergedRes = runGit(repoRoot, ['branch', '--list', branch])
      const exists = mergedRes.status === 0 && mergedRes.stdout.trim().length > 0
      const mergedCheck = runGit(repoRoot, ['branch', '--merged', 'origin/main', '--list', branch])
      const merged = mergedCheck.status === 0 && mergedCheck.stdout.trim().length > 0
      if (!exists || merged || expired) {
        if (dryRun) {
          console.log(`  ${prefix}Would delete lock: ${file}`)
          continue
        }
        fs.unlinkSync(path.join(locksDir, file))
        console.log(`  Deleted lock: ${file}`)
      }
    }
  }
  if (!dryRun) runGit(repoRoot, ['remote', 'prune', 'origin'])
  else console.log(`  ${prefix}Would run: git remote prune origin`)
  console.log(`\n${prefix}Reap complete.${dryRun ? ' No changes made.' : ''}`)
}

function main() {
  const repoRoot = ROOT_DIR
  if (!fs.existsSync(repoRoot)) { console.error('Repo not found:', repoRoot); process.exit(1) }
  // 全量 fetch，不用 --depth：避免给本地长期仓库维持 shallow 边界（会导致 merge-base 等图判断失灵）
  runGit(repoRoot, ['fetch', 'origin', 'main'], { stdio: ['ignore', 'pipe', 'pipe'] })
  const ws = collectWorktreeStatus()
  const locks = collectLockStatus(repoRoot)
  const branches = collectBranchStatus(repoRoot)
  const remoteBranches = collectRemoteStatus(repoRoot)
  const occupancy = collectBranchOccupancy(ws)
  const stash = collectStashStatus(repoRoot)
  switch (SUBCOMMAND) {
    case 'clean': autoClean(ws, locks, repoRoot, DRY_RUN); break
    case 'reap': reap(repoRoot, DRY_RUN); break
    default: printReport(ws, locks, branches, remoteBranches, occupancy, stash); break
  }
}
main()
