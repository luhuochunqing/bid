#!/usr/bin/env node
// Input: src/, backend/src/, e2e/, docs/ source trees
// Output: violation report when deprecated role codes/names are still referenced
// Pos: scripts/ — 死角色/死权限残留检测 (2026-06-21 CO-290 复盘)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   CO-290 修复过程中发现 auditor/manager/bid_senior/task_executor 等角色
//   已下线，但代码、注释、测试、文档中仍有残留，导致：
//   - canReviewBid 基于不存在的 auditor 角色永远 false
//   - 注释误导新开发者以为这些角色仍有效
//   - 测试用例使用无效角色，看似覆盖实际无效
//
// 规则（warning only，非阻断）：
//   扫描指定目录，若发现已下线角色 code 或中文名（完整单词匹配），
//   则报告具体文件位置，提醒清理。
//
// 维护：当 RoleProfileCatalog 再下线角色时，把 code 和中文名加入
// DEPRECATED_ROLES 数组。

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const gitEnv = { ...process.env };
delete gitEnv.GIT_DIR;
delete gitEnv.GIT_WORK_TREE;

const ROOT_DIR = (() => {
  const res = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', env: gitEnv });
  return res.stdout ? res.stdout.trim() : process.cwd();
})();

const STAGED_ONLY = process.argv.includes('--staged');

// 已下线角色：code + 中文名（完整单词边界匹配）
// 注意：'manager' 在 Java 生态中过于常见（EntityManager、Manager 等），
// 因此仅在明显角色上下文（如 roleProfile=manager、Role.MANAGER）中检测。
const DEPRECATED_ROLES = [
  // 中文名只保留完整称谓，避免误伤“审计日志”“张经理”等业务用语
  { code: 'auditor', names: ['审计员'] },
  { code: 'bid_senior', names: ['投标高级工程师', '高级投标工程师'] },
  { code: 'task_executor', names: ['任务执行人', '任务执行者'] },
  // manager 在 Java/JS 中过于常见，仅检测明显角色上下文
  { code: 'manager', names: [], contextRegex: /role[^a-zA-Z0-9]*manager|manager[^a-zA-Z0-9]*role|roleprofile.*manager|manager.*roleprofile|role_name.*manager|role_code.*manager/i },
];

// 额外上下文排除：明确允许出现的历史说明、回滚脚本、归档文档
const EXCLUDE_PATTERNS = [
  /scripts\/check-dead-roles\.mjs$/,            // 本脚本自身
  /\.wiki\/pages\/archives\//,                   // 历史归档
  /docs\/archives\//,                            // 历史归档
  /migration-mysql\/V.*__remove_.*role.*\.sql$/, // 角色下线迁移脚本本身
];

const SCAN_DIRS = ['src', 'backend/src', 'e2e', 'docs', '.agent/contracts'];
const EXTENSIONS = new Set(['.java', '.js', '.mjs', '.cjs', '.ts', '.vue', '.md', '.yml', '.yaml']);
const SKIP_DIRS = new Set(['node_modules', 'dist', '.vite', '.nuxt', '__snapshots__', 'target']);

function isExcluded(filePath) {
  const norm = filePath.split(path.sep).join('/');
  return EXCLUDE_PATTERNS.some(p => p.test(norm));
}

function getFiles() {
  if (STAGED_ONLY) {
    const result = spawnSync('git', [
      'diff', '--cached', '--name-only', '--diff-filter=ACMR',
    ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });
    return (result.stdout || '')
      .trim()
      .split(/\n/)
      .filter(f => SCAN_DIRS.some(d => f.startsWith(d + '/') || f === d))
      .filter(f => EXTENSIONS.has(path.extname(f).toLowerCase()));
  }

  const files = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (SKIP_DIRS.has(entry.name)) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (EXTENSIONS.has(path.extname(entry.name).toLowerCase())) files.push(full);
    }
  }
  for (const dir of SCAN_DIRS) {
    const fullDir = path.join(ROOT_DIR, dir);
    if (fs.existsSync(fullDir)) walk(fullDir);
  }
  return files.map(f => path.relative(ROOT_DIR, f));
}

/**
 * 在 staged 模式下仅返回本次变更的行号集合，避免历史残留噪音。
 */
function getChangedLinesMap() {
  if (!STAGED_ONLY) return null;
  const result = spawnSync('git', [
    'diff', '--cached', '-U0', '--',
  ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });
  const diff = result.stdout || '';
  const map = new Map(); // relPath -> Set(lineNumber)
  let currentFile = null;
  let currentSet = null;

  for (const line of diff.split('\n')) {
    const fileHeader = line.match(/^diff --git a\/(.+?) b\/(.+)$/);
    if (fileHeader) {
      currentFile = fileHeader[2];
      currentSet = new Set();
      map.set(currentFile, currentSet);
      continue;
    }
    if (!currentFile) continue;
    const hunkHeader = line.match(/^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@/);
    if (hunkHeader) {
      const start = parseInt(hunkHeader[1], 10);
      const count = parseInt(hunkHeader[2] || '1', 10);
      for (let i = 0; i < count; i++) {
        currentSet.add(start + i);
      }
    }
  }
  return map;
}

function checkFile(relPath, changedLines) {
  const fullPath = path.join(ROOT_DIR, relPath);
  if (!fs.existsSync(fullPath) || isExcluded(relPath)) return [];

  const content = fs.readFileSync(fullPath, 'utf8');
  const lines = content.split('\n');
  const findings = [];

  lines.forEach((line, idx) => {
    const lineNumber = idx + 1;
    if (changedLines && !changedLines.has(lineNumber)) return;

    for (const role of DEPRECATED_ROLES) {
      // code 检测：完整单词边界
      const codeRegex = new RegExp(`\\b${role.code}\\b`, 'i');
      if (codeRegex.test(line)) {
        // 对 manager 加上下文过滤
        if (role.code === 'manager' && role.contextRegex && !role.contextRegex.test(line)) {
          continue;
        }
        findings.push({ line: idx + 1, text: line.trim(), role: role.code });
        continue;
      }
      // 中文名检测
      for (const name of role.names) {
        if (line.includes(name)) {
          findings.push({ line: idx + 1, text: line.trim(), role: `${role.code}(${name})` });
          break;
        }
      }
    }
  });

  return findings;
}

function main() {
  const files = getFiles();
  if (files.length === 0) {
    console.log('No files to check for dead roles.');
    process.exit(0);
  }

  const changedLinesMap = getChangedLinesMap();
  let totalFindings = 0;
  for (const relPath of files) {
    const changedLines = changedLinesMap ? changedLinesMap.get(relPath) : null;
    const findings = checkFile(relPath, changedLines);
    if (findings.length > 0) {
      totalFindings += findings.length;
      console.warn(`[dead-roles] ${relPath}`);
      for (const f of findings) {
        console.warn(`  ⚠ line ${f.line}: 发现已下线角色 ${f.role}`);
        console.warn(`     ${f.text}`);
      }
    }
  }

  if (totalFindings > 0) {
    console.warn('');
    console.warn(`检测到 ${totalFindings} 处已下线角色残留。`);
    console.warn('请在 CLAUDE.md、RoleProfileCatalog、测试、注释中清理这些角色。');
    process.exit(0);
  }

  console.log('Dead role check passed.');
  process.exit(0);
}

main();
