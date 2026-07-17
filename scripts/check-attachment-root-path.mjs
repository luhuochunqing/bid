#!/usr/bin/env node
// Input: git commit range (defaults to merge-base origin/main..HEAD)
// Output: fail if the diff adds new @Value("${*.attachment.root:/...}") defaults starting with "/"
// Pos: scripts/ - prevent recurrence of macOS SSV read-only + absolute path default (engineering-discipline §6.3)
// Usage:
//   node scripts/check-attachment-root-path.mjs [base-ref]              # default: block new violations in diff
//   node scripts/check-attachment-root-path.mjs --audit-existing [src/] # audit existing code (warning only)
//   node scripts/check-attachment-root-path.mjs --audit-existing --json # JSON output for CI
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   macOS Catalina+ enables SSV (System Volume Protection) making root "/" read-only.
//   @Value defaults like "/data/attachments/warehouse" cause Files.createDirectories
//   to throw IOException ("Read-only file system"). Upload fails silently (HTTP 400
//   returned but users may not notice), and Word/PDF generation shows "（文件缺失）"
//   because Files.exists returns false.
//
//   Root cause: developer assumed "/data/..." works on all OSes. Linux allows creating
//   /data, macOS does not. The default should be a relative path like "data/warehouse-attachments"
//   (aligns with personnel/qualification modules that already use relative paths).
//
// History:
//   2026-07-17 CO-582 follow-up: original implementation. Blocks new violations where
//   @Value("${*.attachment.root:/...}") default starts with "/". Also covers
//   *.storage-path, *.storage-root, *.upload-dir, *.root similar patterns.

import fs from 'node:fs'
import path from 'node:path'
import { execSync, spawnSync } from 'node:child_process'

const ROOT = process.cwd()
const args = process.argv.slice(2)
const AUDIT_EXISTING = args.includes('--audit-existing')
const JSON_MODE = args.includes('--json')

// Match @Value annotations whose default literal is a file-system absolute path.
// Excludes URL defaults (http://, https://, ws://, etc.) which legitimately start with "/".
// Example violations:
//   @Value("${warehouse.attachment.root:/data/attachments/warehouse}")
//   @Value("${performance.attachment.root:/data/attachments/performance}")
//   @Value("${file.storage-path:/data/files}")
// Allowed (not matched):
//   @Value("${ai.openai.base-url:https://api.openai.com/v1}")  — URL, not file path
//   @Value("${cors.allowed-origins:http://localhost:1314}")    — URL list, not file path
const VALUE_PATTERN = /@Value\("\$\{[^}]+:\/(?!\/)[^}]*\}"\)/

function listJavaFiles(dir) {
  const results = []
  if (!fs.existsSync(dir)) return results
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      results.push(...listJavaFiles(full))
    } else if (entry.name.endsWith('.java')) {
      results.push(full)
    }
  }
  return results
}

function findViolations(filePath) {
  const content = fs.readFileSync(filePath, 'utf8')
  const lines = content.split('\n')
  const violations = []
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (VALUE_PATTERN.test(line)) {
      violations.push({
        file: filePath,
        line: i + 1,
        content: line.trim(),
      })
    }
  }
  return violations
}

function getDiffFiles(baseRef) {
  const cmd = `git diff --name-only --diff-filter=AM ${baseRef}..HEAD -- 'backend/**/*.java'`
  const out = execSync(cmd, { encoding: 'utf8', cwd: ROOT })
  return out.split('\n').filter(Boolean).map(f => path.join(ROOT, f))
}

function getAddedLines(file, baseRef) {
  const rel = path.relative(ROOT, file)
  const cmd = `git diff ${baseRef}..HEAD -- ${rel}`
  const out = execSync(cmd, { encoding: 'utf8', cwd: ROOT })
  return out.split('\n').filter(l => l.startsWith('+') && !l.startsWith('+++'))
}

function main() {
  if (AUDIT_EXISTING) {
    // Scan all existing Java files for violations (warning only, exit 0)
    const srcArg = args.find(a => !a.startsWith('--'))
    const scanDir = srcArg ? path.resolve(ROOT, srcArg) : path.join(ROOT, 'backend/src/main/java')
    const files = listJavaFiles(scanDir)
    const allViolations = []
    for (const f of files) {
      allViolations.push(...findViolations(f))
    }
    if (JSON_MODE) {
      console.log(JSON.stringify({ violations: allViolations, count: allViolations.length }, null, 2))
    } else {
      if (allViolations.length === 0) {
        console.log(`✓ No @Value defaults starting with "/" found in ${scanDir}`)
      } else {
        console.log(`⚠ Found ${allViolations.length} @Value defaults starting with "/" (existing debt, non-blocking):`)
        for (const v of allViolations) {
          console.log(`  ${path.relative(ROOT, v.file)}:${v.line}  ${v.content}`)
        }
      }
    }
    return 0
  }

  // Default mode: block new violations in diff
  const baseRef = args.find(a => !a.startsWith('--')) || 'origin/main'
  let baseCommit
  try {
    baseCommit = execSync(`git merge-base ${baseRef} HEAD`, { encoding: 'utf8', cwd: ROOT }).trim()
  } catch {
    baseCommit = baseRef
  }

  const diffFiles = getDiffFiles(baseCommit)
  const newViolations = []

  for (const file of diffFiles) {
    if (!fs.existsSync(file)) continue
    const addedLines = getAddedLines(file, baseCommit)
    for (const added of addedLines) {
      const cleaned = added.slice(1).trim()  // strip leading "+"
      if (VALUE_PATTERN.test(cleaned)) {
        newViolations.push({
          file: path.relative(ROOT, file),
          content: cleaned,
        })
      }
    }
  }

  if (newViolations.length === 0) {
    console.log('✓ No new @Value defaults starting with "/" in diff')
    return 0
  }

  console.error(`✗ Found ${newViolations.length} new @Value default(s) starting with "/" (must be relative path):`)
  for (const v of newViolations) {
    console.error(`  ${v.file}: ${v.content}`)
  }
  console.error('')
  console.error('Root cause: macOS SSV makes root "/" read-only. Absolute paths like "/data/..."')
  console.error('cause Files.createDirectories to fail. Use relative paths like "data/warehouse-attachments".')
  console.error('See engineering-discipline.md §6.3 (仓库 Word 合订本附件内容丢失) for details.')
  return 1
}

const exitCode = main()
process.exit(exitCode)
