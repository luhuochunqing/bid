#!/usr/bin/env node
// Input: staged files (via git diff --cached), hot-paths.yml, .agent-locks/ directory
// Output: exits non-zero when staged files touch a hot-path but no active lock covers it
// Pos: scripts/ - Pre-commit hot-path lock enforcement
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 工程背景（请勿删除）：
// 2026-05-21 记录于 .wiki/pages/lessons-learned.md §三
// 问题根因：修改 .github/workflows/ 和 .githooks/ 这两个 hot-path 时，
//   忘记在 .agent-locks/ 中注册对应的锁条目，导致 CI agent-locks job 拒绝 PR。
//   反复提交才发现，耗费了额外的 CI 时间。
// 本脚本将此检查前移到 pre-commit，让问题在本地提交时就被拦截。
//
// ⚠️  维护规则（强制）：
//   当你修改以下任何文件时，必须同步在 .agent-locks/<branch>.yml 中注册覆盖该路径的锁：
//     - scripts/hot-paths.yml         → 本脚本依赖此文件定义
//     - scripts/check-agent-locks.mjs → CI 锁检查逻辑
//     - .githooks/pre-commit          → hot-path
//     - .github/workflows/ci.yml      → hot-path
//   未注册锁 → pre-commit 拒绝 + CI agent-locks job 拒绝 PR，双重保障。

import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import process from 'node:process'

const repoRoot = path.resolve(process.cwd())

// ─────────────────────────────────────────────────────────────────────────────
// STEP 1: 获取当前已暂存（staged）的文件列表
// ─────────────────────────────────────────────────────────────────────────────
function getStagedFiles() {
  const result = spawnSync('git', ['diff', '--cached', '--name-only', '--diff-filter=ACDMRT'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
  if (result.status !== 0) return []
  return result.stdout.trim().split('\n').filter(Boolean)
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 2: 从 hot-paths.yml 读取需要加锁的路径模式
// ─────────────────────────────────────────────────────────────────────────────
function loadHotPaths() {
  const hotPathsFile = path.join(repoRoot, 'scripts', 'hot-paths.yml')
  if (!fs.existsSync(hotPathsFile)) return []

  const content = fs.readFileSync(hotPathsFile, 'utf8')
  const patterns = []
  const patternRegex = /^\s+-\s+pattern:\s+"([^"]+)"/gm
  let m
  while ((m = patternRegex.exec(content)) !== null) {
    patterns.push(m[1])
  }
  return patterns
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 3: 将 hot-path glob 模式转换为简单的前缀/精确匹配
// ─────────────────────────────────────────────────────────────────────────────
function fileMatchesHotPath(filePath, pattern) {
  if (pattern.endsWith('/**')) {
    return filePath.startsWith(pattern.slice(0, -3))
  }
  if (pattern.includes('*')) {
    // application*.yml → application 前缀匹配
    const prefix = pattern.split('*')[0]
    return filePath.startsWith(prefix)
  }
  return filePath === pattern
}

// ─────────────────────────────────────────────────────────────────────────────
// STEP 4: 从当前分支名找到对应的 .agent-locks/<branch>.yml，读取锁定路径
// ─────────────────────────────────────────────────────────────────────────────
function getCurrentBranch() {
  const result = spawnSync('git', ['rev-parse', '--abbrev-ref', 'HEAD'], {
    cwd: repoRoot,
    encoding: 'utf8',
  })
  return result.stdout.trim()
}

function loadBranchLocks(branch) {
  const locksDir = path.join(repoRoot, '.agent-locks')
  if (!fs.existsSync(locksDir)) return []

  // 尝试多种命名规则找到锁文件：
  // 1) manage-agent-locks.mjs 使用的 resolveTaskSlug()（去除 agent/ 前缀）
  //    分支 codex/skill-progression-map-0605 → skill-progression-map-0605.yml
  // 2) 旧规则：branch.replace(/\//g, '-')（保留完整分支名）
  //    分支 codex/skill-progression-map-0605 → codex-skill-progression-map-0605.yml
  // 3) 扫描全部 .agent-locks/*.yml 根据 branch: 字段匹配（最健壮）
  const afterSlash = branch.includes('/') ? branch.split('/').slice(1).join('/') : branch;
  const slugName = afterSlash.replaceAll('/', '-') + '.yml';
  const fullName = branch.replace(/\//g, '-') + '.yml';

  let lockFilesToCheck = [fullName, slugName];
  // 如果 slugName 和 fullName 相同，去重
  if (slugName === fullName && lockFilesToCheck.length === 2) {
    lockFilesToCheck = [fullName];
  }

  for (const fileName of lockFilesToCheck) {
    const lockFile = path.join(locksDir, fileName);
    if (fs.existsSync(lockFile)) {
      const paths = extractPathsFromLockFile(lockFile);
      if (paths.length > 0) return paths;
    }
  }

  // Fallback: 扫描所有锁文件，按 branch: 字段匹配
  const allFiles = fs.readdirSync(locksDir).filter(f => f.endsWith('.yml'));
  for (const fileName of allFiles) {
    const lockFile = path.join(locksDir, fileName);
    const content = fs.readFileSync(lockFile, 'utf8');
    const branchRe = /branch:\s+"((?:[^"\\]|\\.)*)"/gm;
    const pathRe = /^(\s*-\s+)?path:\s+"((?:[^"\\]|\\.)*)"/gm;
    const expireRe = /^\s+expiresAt:\s+"((?:[^"\\]|\\.)*)"/gm;

    const branches = []; const paths = []; const expires = [];
    let m;
    while ((m = branchRe.exec(content)) !== null) branches.push(m[1]);
    while ((m = pathRe.exec(content)) !== null) paths.push(m[2]);
    while ((m = expireRe.exec(content)) !== null) expires.push(m[1]);

    const now = new Date();
    const result = [];
    for (let i = 0; i < paths.length; i++) {
      const lockBranch = branches[i] || branches[branches.length - 1] || '';
      if (lockBranch !== branch) continue;
      const expireDate = expires[i] ? new Date(expires[i]) : null;
      if (!expireDate || expireDate > now) {
        result.push(paths[i]);
      }
    }
    if (result.length > 0) return result;
  }

  return [];
}

/**
 * Extract active (non-expired) lock paths from a single lock file.
 */
function extractPathsFromLockFile(lockFile) {
  const content = fs.readFileSync(lockFile, 'utf8');
  const pathRe = /^(\s*-\s+)?path:\s+"((?:[^"\\]|\\.)*)"/gm;
  const expireRe = /^\s+expiresAt:\s+"((?:[^"\\]|\\.)*)"/gm;
  const paths = []; const expires = [];
  let m;
  while ((m = pathRe.exec(content)) !== null) paths.push(m[2]);
  while ((m = expireRe.exec(content)) !== null) expires.push(m[1]);

  const now = new Date();
  const result = [];
  for (let i = 0; i < paths.length; i++) {
    const expireDate = expires[i] ? new Date(expires[i]) : null;
    if (!expireDate || expireDate > now) {
      result.push(paths[i]);
    }
  }
  return result;
}

function fileIsCoveredByLock(filePath, lockPaths) {
  return lockPaths.some(lockPath => {
    // 锁路径有尾部斜杠 → 目录锁，匹配前缀
    if (lockPath.endsWith('/')) {
      return filePath.startsWith(lockPath) || filePath === lockPath.slice(0, -1)
    }
    // 锁路径无尾部斜杠 → 精确文件锁或目录锁（兼容两种写法）
    return filePath === lockPath ||
      filePath.startsWith(lockPath + '/')
  })
}

// ─────────────────────────────────────────────────────────────────────────────
// 主逻辑
// ─────────────────────────────────────────────────────────────────────────────
const stagedFiles = getStagedFiles()
if (stagedFiles.length === 0) {
  process.exit(0)
}

const hotPatterns = loadHotPaths()
const branch = getCurrentBranch()
const lockPaths = loadBranchLocks(branch)
const violations = []

for (const file of stagedFiles) {
  for (const pattern of hotPatterns) {
    if (fileMatchesHotPath(file, pattern)) {
      if (!fileIsCoveredByLock(file, lockPaths)) {
        violations.push({ file, pattern })
      }
      break
    }
  }
}

if (violations.length > 0) {
  console.error('\n❌ Hot-path 锁缺失！以下文件属于高风险路径，必须先在锁文件中注册才能提交：\n')
  for (const { file, pattern } of violations) {
    console.error(`  文件: ${file}`)
    console.error(`  匹配模式: ${pattern}`)
    console.error('')
  }
  console.error('修复方法：在 .agent-locks/<当前分支>.yml 中添加对应路径的锁条目。')
  console.error(`当前分支: ${branch}`)
  console.error(`锁文件路径: .agent-locks/${branch.replace(/\//g, '-')}.yml\n`)
  console.error('示例：')
  console.error('  - path: ".github/workflows/"')
  console.error('    scope: "directory"')
  console.error(`    owner: "${branch.split('/')[0]}"`)
  console.error(`    branch: "${branch}"`)
  console.error('    expiresAt: "2026-07-01T00:00:00Z"')
  console.error('    reason: "说明为什么需要修改此文件"\n')
  console.error('⚠️  这是工程规范（2026-05-21）：改 hot-path 文件前必须先注册锁。')
  console.error('    详见 .wiki/pages/lessons-learned.md §三\n')
  process.exit(1)
}

console.log(`✅ Hot-path 锁检查通过（分支: ${branch}，锁文件覆盖 ${lockPaths.length} 个路径）`)
