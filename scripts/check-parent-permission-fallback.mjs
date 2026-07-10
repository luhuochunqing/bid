#!/usr/bin/env node
// Input: backend/src/main/java/**/*.java + RoleProfileCatalog.java
// Output: violation report for @PreAuthorize parent permissions that lack
//         a child-to-parent fallback in UserDetailsServiceImpl.
// Pos: scripts/ - Pre-push guardrail against parent-permission 403 regressions.
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background: Controllers guard modules with class-level
//   @PreAuthorize("hasAuthority('resource')").
//   OSS menu mapping grants only child keys such as resource-account / resource-ca
//   for some roles (e.g. bid-projectLeader). Without a fallback that adds the
//   parent key when any child key is present, those OSS users get 403 even though
//   the business intent is to let them see the module.
//
// This script scans:
//   1. All @PreAuthorize(hasAuthority('X')) usages.
//   2. RoleProfileCatalog for permission keys that have children X-Y.
//   3. UserDetailsServiceImpl for the fallback pattern:
//        stream/anyMatch startsWith("X-") && authorities.add("X")
//   If a parent X is required by @PreAuthorize but no fallback is found, fail.

import fs from 'node:fs'
import path from 'node:path'

const ROOT = 'backend/src/main/java'
const ROLE_PROFILE_CATALOG = 'backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java'
const USER_DETAILS_SERVICE = 'backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java'
const SKIP_DIRS = new Set(['target', 'node_modules'])

const HAS_AUTHORITY_RE = /hasAuthority\(\s*['"]([^'"]+)['"]\s*\)/g
const PERMISSION_LITERAL_RE = /"([a-z][a-z0-9.:_-]*)"/g

function* walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) yield* walk(full)
    else if (entry.name.endsWith('.java')) yield full
  }
}

function collectPreauthorizeAuthorities() {
  const authorities = new Set()
  for (const file of walk(ROOT)) {
    const src = fs.readFileSync(file, 'utf8')
    // Only look at lines with @PreAuthorize (or class-level)
    for (const line of src.split('\n')) {
      if (!line.includes('PreAuthorize')) continue
      let m
      while ((m = HAS_AUTHORITY_RE.exec(line)) !== null) {
        authorities.add(m[1])
      }
    }
  }
  return authorities
}

function collectCatalogPermissions() {
  const src = fs.readFileSync(ROLE_PROFILE_CATALOG, 'utf8')
  const perms = new Set()
  let m
  while ((m = PERMISSION_LITERAL_RE.exec(src)) !== null) {
    const key = m[1]
    // Heuristic: permission keys contain '.' or '-' or are known short keys.
    // Exclude obvious non-permission strings (role codes already contain letters,
    // but they are also used as authorities, so keep them if they look like keys).
    perms.add(key)
  }
  return perms
}

function findParentPermissions(allPerms) {
  const parents = new Set()
  const sorted = Array.from(allPerms).sort()
  for (const key of sorted) {
    // A parent key X is one for which a child X-<something> also exists.
    const idx = key.lastIndexOf('-')
    if (idx > 0) {
      parents.add(key.slice(0, idx))
    }
  }
  return parents
}

function hasFallback(userDetailsSrc, parent) {
  // Look for:
  //   ...startsWith("parent-")
  // and
  //   authorities.add("parent")
  // in close proximity is hard; require both tokens exist in the file.
  const startsWithRe = new RegExp(`startsWith\\(\\s*["\']${parent}-["\']\\s*\\)`)
  const addRe = new RegExp(`authorities\\.add\\(\\s*["\']${parent}["\']\\s*\\)`)
  return startsWithRe.test(userDetailsSrc) && addRe.test(userDetailsSrc)
}

const requiredAuthorities = collectPreauthorizeAuthorities()
const catalogPermissions = collectCatalogPermissions()
const parentPermissions = findParentPermissions(catalogPermissions)

const userDetailsSrc = fs.readFileSync(USER_DETAILS_SERVICE, 'utf8')

const requiredParents = Array.from(requiredAuthorities).filter(a => parentPermissions.has(a))
const missing = requiredParents.filter(p => !hasFallback(userDetailsSrc, p))

if (missing.length > 0) {
  console.error('[parent-permission-fallback] 以下父权限在 @PreAuthorize 中被使用，')
  console.error('且 RoleProfileCatalog 中存在以它为前缀的子权限，')
  console.error('但 UserDetailsServiceImpl 中缺少 "子权限 → 父权限" 兜底：')
  for (const p of missing) {
    console.error(`  - ${p}`)
  }
  console.error('')
  console.error('修复方式（二选一）：')
  console.error(`  1. 在 UserDetailsServiceImpl 的权限构建路径中兜底：`)
  console.error(`       if (authorities.stream().anyMatch(p -> p != null && p.startsWith("${missing[0]}-"))) {`)
  console.error(`           authorities.add("${missing[0]}");`)
  console.error(`       }`)
  console.error(`  2. 如果该父权限确实不应由子权限推导获得，在调用点上方加 // SAFE: <原因>`)
  console.error(`     并在 scripts/check-parent-permission-fallback.mjs 的 SAFE_PATTERNS 中登记。`)
  process.exit(1)
}

console.log(`Parent-permission fallback check passed (${requiredParents.length} parent authority/ies required, all have fallback).`)
