#!/usr/bin/env node
// Input: backend/src/main/java/**/*.java source tree, User.getRoleCode() call sites
// Output: violation report for direct User.getRoleCode() invocations outside
//         the sanctioned SAFE-comment whitelist
// Pos: scripts/ - Repository maintenance guardrail against CO-361/CO-373 root cause
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// CO-373 root cause: OSS-synced users (role_id=NULL) hit the fallback branch
// in User.getRoleCode() and receive "manager", causing downstream permission
// checks to mis-classify them. To prevent re-introduction, every call site of
// User.getRoleCode() must either:
//   (a) be replaced with `effectiveRoleResolver.resolveRoleCode(user)` or
//       `dataScopeConfigService.getRoleCode(user)` (preferred for permission
//       decisions), or
//   (b) be marked with `// SAFE: <reason>` on the line above the call (only
//       allowed for the documented exemption list).
//
// This script ONLY matches receivers that look like User variables —
// `user`, `u`, `currentUser`, `actor`, `assignee`, `principal`,
// `targetUser`, `assigneeUser`, `userEntity`, `loginUser` — to avoid false
// positives against unrelated classes that happen to expose a `getRoleCode()`
// getter (e.g. PositionToRoleMapping, PersonToRoleMapping, RoleProfile).
//
// The documented exemption surface is intentionally small:
//   1. User.java (the getter definition itself, plus getter documentation)
//   2. EffectiveRoleResolver (reads entity value to compare against cache)
//   3. DataScopeConfigService.getRoleCode / isLocalSystemAccount (admin
//      local-account fallback logic; already exception-tested via CO-373)

import fs from 'node:fs'
import path from 'node:path'

const ROOT = 'backend/src/main/java'
const SKIP_DIRS = new Set(['target', 'node_modules'])

// Files that may call User.getRoleCode() WITHOUT a SAFE comment. These are
// the documented exemption surface — keep this list tiny and review every
// entry. Any new file added here must include a justification in the file's
// header comment AND in CO-373 follow-up tickets.
const FILE_WHITELIST = new Set([
  'backend/src/main/java/com/xiyu/bid/entity/User.java',
  'backend/src/main/java/com/xiyu/bid/security/EffectiveRoleResolver.java',
  'backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java',
])

// Test files are out of scope (mirrors check-task-status-literal.mjs).
const TEST_DIR_HINT = path.join(path.sep, 'src', 'test') + path.sep

// Recognised exemption markers. Must appear on the line IMMEDIATELY above the
// call site (single-line `// SAFE:` or `// DEPRECATED:` style). Lines that are
// blank are skipped while looking upward.
const SAFE_PATTERN = /\/\/\s*(SAFE|DEPRECATED)\s*:/i

// Recognised User-type receiver variable names. Conservative on purpose —
// only identifiers that semantically refer to a User entity.
const USER_RECEIVER = /\b(?:user|currentUser|targetUser|assigneeUser|loginUser|userEntity|principal|actor|assignee)\b\.getRoleCode\(\)/

// Comment / doc markers. Any line that starts with one of these (allowing
// leading whitespace) is treated as non-code — used to suppress false
// positives when javadoc / line comments mention the deprecated method name.
const COMMENT_LINE = /^\s*(\*|\/\*|\/\/|\*\/|<!--)/

function* walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) yield* walk(full)
    else if (entry.name.endsWith('.java')) yield full
  }
}

function isWhitelistedFile(normPath) {
  return FILE_WHITELIST.has(normPath)
}

function isTestFile(normPath) {
  return normPath.includes(TEST_DIR_HINT)
}

function hasSafeCommentAbove(lines, idx) {
  // Walk upward, skipping blank lines, stopping at the first non-blank
  // non-comment line. If ANY line in the contiguous comment block above
  // starts with `// SAFE:` or `// DEPRECATED:`, treat the call as exempt.
  // This allows multi-line SAFE rationales (comment + continuation lines)
  // to be recognised as a single annotation.
  for (let look = idx - 1; look >= 0; look--) {
    const trimmed = lines[look].trim()
    if (trimmed === '') continue
    if (!trimmed.startsWith('//')) return false
    if (SAFE_PATTERN.test(trimmed)) return true
    // Inside a comment block — keep scanning upward for the SAFE marker.
  }
  return false
}

let violations = 0
let whitelisted = 0
let safed = 0

for (const file of walk(ROOT)) {
  const norm = file.split(path.sep).join('/')
  if (isTestFile(norm)) continue
  if (isWhitelistedFile(norm)) {
    whitelisted++
    continue
  }
  const src = fs.readFileSync(file, 'utf8')
  const lines = src.split('\n')
  lines.forEach((line, i) => {
    if (COMMENT_LINE.test(line)) return
    if (!USER_RECEIVER.test(line)) return
    if (hasSafeCommentAbove(lines, i)) {
      safed++
      return
    }
    console.error(`[rolecode-direct-call] ${norm}:${i + 1}  ${line.trim()}`)
    violations++
  })
}

if (violations > 0) {
  console.error('')
  console.error(`检测到 ${violations} 处 User.getRoleCode() 直调，违反 CO-373 收敛规范。`)
  console.error(`处理方式（二选一）：`)
  console.error(`  1. 【首选】迁移到 EffectiveRoleResolver.resolveRoleCode(user) 或 DataScopeConfigService.getRoleCode(user)`)
  console.error(`  2. 【豁免】在调用点上方一行加 // SAFE: <具体原因> 注释（仅限展示/MDC 日志/登录响应/admin 本地判定等已记录豁免）`)
  console.error(`豁免白名单文件：`)
  for (const f of FILE_WHITELIST) console.error(`  - ${f}`)
  console.error(`根因 & 治理：https://linear.app/ericforai/issue/CO-373`)
  process.exit(1)
}

console.log(`Role code direct-call check passed (${whitelisted} whitelisted files, ${safed} SAFE-annotated sites).`)