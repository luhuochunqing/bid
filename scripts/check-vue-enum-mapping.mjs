#!/usr/bin/env node
// Input: staged Vue files from git index
// Output: warns when frontend files contain hard-coded enum mapping tables that likely diverge from backend API values
// 维护声明: 若枚举映射模式、变量命名惯例变更，请同步更新本脚本。
// Pos: scripts/ — 前端硬编码枚举映射表检测 (2026-06-05 skill-progression-map)
//
// Background (2026-05-29~06-05):
//   PR #39, #63, #71, #72 all fixed the same class of bug — frontend Vue
//   files contained hard-coded mapping objects (_PT, _CT, etc.) that drifted
//   from backend enum values. PR #76 migrated the mapping to backend data
//   dictionary API, which is the correct approach. This check prevents new
//   occurrences from entering the codebase without explicit awareness.
//
// Patterns flagged (warning only, non-blocking):
//   1. Object literals with key patterns like `_PT`, `_CT`, `Map` in names
//   2. Large switch/case or if/else chains mapping enum-like strings
//   3. Variables named *Map, *Mapping, *Lookup, *Table containing 3+ entries
//   4. Exports of constant objects that look like enum translation tables

import fs from 'node:fs';
import { spawnSync } from 'node:child_process';

const WARN_PATTERNS = [
  // Pattern 1: Object keys matching _XX or _XXX pattern (backend enum literals)
  { regex: /['"]?_[A-Z]{2,}['"]?\s*:/, label: 'backend-enum-key' },

  // Pattern 2: Variable declarations with Map/Mapping/Lookup/Table suffix
  { regex: /(const|let|var)\s+\w*(?:Map|Mapping|Lookup|Table)\s*=[\s\S]*?\{/, label: 'mapping-variable' },

  // Pattern 3: Exported object with 4+ enum-like key-value pairs
  { regex: /export\s+(default\s+)?(const|function)\s+\w*(?:map|Map|mapping|Mapping)\s*[=\(]/, label: 'exported-mapping' },

  // Pattern 4: Object patterns with both _PT and _CT simultaneously
  { regex: /_[CP]T['"]?\s*:/, label: 'project-customer-type-mapping' },
];

const SUSPICIOUS_VAR_NAMES = [
  /projectTypeMap/i,
  /customerTypeMap/i,
  /statusMap/i,
  /priorityMap/i,
  /sourceTypeMap/i,
  /initiationStageMap/i,
  /enumLabelMap/i,
  /labelMap/i,
  /valueLabelMap/i,
  /displayNameMap/i,
];

const gitEnv = { ...process.env };
delete gitEnv.GIT_DIR;
delete gitEnv.GIT_WORK_TREE;

const ROOT_DIR = (() => {
  const res = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', env: gitEnv });
  return res.stdout ? res.stdout.trim() : process.cwd();
})();

const script_name = 'check-vue-enum-mapping';

function getStagedVueFiles() {
  const result = spawnSync('git', [
    'diff', '--cached', '--name-only', '--diff-filter=ACMR',
  ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });

  return (result.stdout || '')
    .trim()
    .split(/\n/)
    .filter(f => f.endsWith('.vue') || f.endsWith('.js') || f.endsWith('.ts'))
    .filter(f => f.startsWith('src/'));
}

function checkFile(filePath) {
  const fullPath = `${ROOT_DIR}/${filePath}`;
  if (!fs.existsSync(fullPath)) return [];

  const content = fs.readFileSync(fullPath, 'utf8');
  const lines = content.split('\n');
  const findings = [];

  // Check for suspicious variable names first
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    for (const pattern of SUSPICIOUS_VAR_NAMES) {
      if (pattern.test(line)) {
        findings.push({
          file: filePath,
          line: i + 1,
          pattern: 'suspicious-variable-name',
          message: `变量名 "${line.trim().match(/\w*(?:Map|Mapping|Lookup|Table)\w*/)?.[0] || '匹配项'}" 看起来像硬编码枚举映射表`,
        });
        break;
      }
    }
  }

  // Check for object patterns containing backend enum-style keys
  // We look for multi-line object assignments
  let inObject = false;
  let objectStartLine = 0;
  let braceCount = 0;
  let foundKeyInObject = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Start of an object assignment
    if (!inObject && /=\s*\{/.test(line) && !/import\s/.test(line)) {
      inObject = true;
      objectStartLine = i + 1;
      braceCount = (line.match(/\{/g) || []).length - (line.match(/\}/g) || []).length;
      foundKeyInObject = false;
      continue;
    }

    if (inObject) {
      braceCount += (line.match(/\{/g) || []).length;
      braceCount -= (line.match(/\}/g) || []).length;

      // Check all warn patterns
      for (const pattern of WARN_PATTERNS) {
        if (pattern.regex.test(line)) {
          foundKeyInObject = true;
          break;
        }
      }

      if (braceCount <= 0) {
        // End of object
        if (foundKeyInObject) {
          findings.push({
            file: filePath,
            line: objectStartLine,
            pattern: 'enum-mapping-object',
            message: `第 ${objectStartLine} 行开始的 Object 包含后端枚举风格键值，建议迁移至后端数据字典 API`,
          });
        }
        inObject = false;
        foundKeyInObject = false;
      }
    }
  }

  // Also check for large switch statements on enum-like values
  // (heuristic: switch with 4+ cases matching enum-style values)
  const switchMatches = content.matchAll(/switch\s*\(([^)]+)\)\s*\{/g);
  for (const match of switchMatches) {
    const switchVar = match[1].trim();
    // Find the position to get line number
    const upToSwitch = content.slice(0, match.index);
    const lineNum = (upToSwitch.match(/\n/g) || []).length + 1;

    // Only flag if the switch variable looks like an enum
    if (/type|status|stage|level|priority|source/i.test(switchVar)) {
      // Count cases in this switch
      const switchBody = content.slice(match.index);
      const caseMatches = switchBody.match(/case\s+/g);
      if (caseMatches && caseMatches.length >= 4) {
        findings.push({
          file: filePath,
          line: lineNum,
          pattern: 'large-enum-switch',
          message: `第 ${lineNum} 行 switch(${switchVar}) 含 ${caseMatches.length} 个 case，如为枚举值到显示文本的映射，建议迁移至后端数据字典 API`,
        });
      }
    }
  }

  return findings;
}

function main() {
  const files = getStagedVueFiles();

  if (files.length === 0) {
    console.log(`${script_name}: no staged Vue/JS/TS files, skipping.`);
    process.exit(0);
  }

  const allFindings = [];
  for (const file of files) {
    const findings = checkFile(file);
    allFindings.push(...findings);
  }

  if (allFindings.length > 0) {
    console.log(`\n${script_name}: ${allFindings.length} potential hard-coded enum mapping(s) found (warn only):\n`);

    // Group by file
    const byFile = {};
    for (const f of allFindings) {
      if (!byFile[f.file]) byFile[f.file] = [];
      byFile[f.file].push(f);
    }

    for (const [file, findings] of Object.entries(byFile)) {
      console.log(`  ${file}:`);
      for (const f of findings) {
        console.log(`    L${f.line}  ${f.message}`);
      }
    }

    console.log(`\n${script_name}: 建议将枚举映射迁移至后端数据字典 API，避免前后端不同步。`);
    console.log(`  参考: PR #76 — 标讯→立项枚举映射迁移至后端数据字典 API`);
    console.log(`${script_name}: ${allFindings.length} warning(s) — non-blocking.\n`);
  } else {
    console.log(`${script_name}: no hard-coded enum mapping patterns detected.`);
  }
}

main();
