#!/usr/bin/env node
// Input: git staged diff 或 git diff against base ref
// Output: 报告本次变更中新增的硬编码颜色及违规文件
// Pos: scripts/check-token-coverage.mjs
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 增量门禁策略（--delta flag）：
//   - 分析 git diff 中的文件内容变更（--staged 或 --base=REF）
//   - 仅统计本次 PR/变更新引入的 #hex 颜色
//   - 超出阈值则失败，防止每次 PR 悄悄引入大量硬编码颜色
//
// 全量门禁策略（无 --delta）：
//   - 扫描所有 CSS/Vue 文件
//   - 总数超出阈值则失败，约束存量债务总量
import { execSync } from 'child_process';
import { readFileSync } from 'fs';
import { join } from 'path';

const ROOT = process.cwd();
const SRC = join(ROOT, 'src');
const FAIL_ON_HEX = process.argv.includes('--fail-on-hex');
const IS_DELTA = process.argv.includes('--delta');
const IS_STAGED = process.argv.includes('--staged');
const DELTA_BASE = process.argv.find(arg => arg.startsWith('--base='))?.split('=')[1];
const MAX_HEX = parseInt(process.argv.find(arg => arg.startsWith('--max-hex='))?.split('=')[1] || (IS_DELTA ? '5' : '0'));
const MAX_HEX_TOTAL = parseInt(process.argv.find(arg => arg.startsWith('--max-hex-total='))?.split('=')[1] || '960');

const IGNORE_FILES = ['src/styles/variables.css', 'src/styles/variables.scss'];

function run(cmd) {
  try { return execSync(cmd, { encoding: 'utf-8', cwd: ROOT }); } catch { return ''; }
}

function countNewHexInDiff(diffOutput) {
  const hexRe = /#[0-9a-fA-F]{3,6}\b/g;
  const lines = diffOutput.split('\n');
  let newHexCount = 0;

  for (const line of lines) {
    if (!line.startsWith('+')) continue;
    const content = line.slice(1).trim();
    if (content.startsWith('//') || content.startsWith('/*')) continue;

    let match;
    hexRe.lastIndex = 0;
    while ((match = hexRe.exec(content)) !== null) {
      const before = content.slice(Math.max(0, match.index - 80), match.index).toLowerCase();
      const colorKeywords = ['color', 'background', 'rgb', 'hsl', 'border', 'box-shadow', 'stroke', 'fill', 'outline', 'caret', 'text-shadow'];
      if (!colorKeywords.some(kw => before.includes(kw))) continue;
      newHexCount++;
    }
  }
  return newHexCount;
}

function getDelta() {
  if (IS_STAGED) {
    return run('git diff --staged -- src/');
  }
  if (DELTA_BASE) {
    return run('git diff ' + DELTA_BASE + '..HEAD -- src/');
  }
  return run('git diff HEAD~1..HEAD -- src/');
}

if (IS_DELTA || IS_STAGED) {
  const diff = getDelta();
  const newHexCount = countNewHexInDiff(diff);

  console.log('\n' + '='.repeat(70));
  console.log('  Design Token Governance — Incremental Delta Check');
  console.log('  ' + new Date().toISOString().slice(0, 10));
  console.log('='.repeat(70));

  if (newHexCount === 0) {
    console.log('\n  ✅ No new hardcoded colors introduced in this change.\n');
  } else {
    console.log('\n  ❌ ' + newHexCount + ' new hardcoded color(s) introduced in this PR.');
    console.log('     Threshold: ' + MAX_HEX + ' per change. Use CSS variables instead.\n');
    console.log('  New colors found in git diff — each must be replaced with a design token (var(--xxx)).\n');
  }

  if (FAIL_ON_HEX && newHexCount > MAX_HEX) {
    console.error('  ❌ Error: ' + newHexCount + ' new hardcoded colors (max: ' + MAX_HEX + ').');
    console.error('     Fix: replace #hex colors with var(--token) or use --max-hex=N to adjust.\n');
    process.exit(1);
  }
  process.exit(0);
}

// ============================================================
// Full codebase scan (no --delta)
// ============================================================
function findAllFiles(dir, ext) {
  try {
    return run('find ' + dir + ' -name *.' + ext + ' -not -path */node_modules/*').trim().split('\n').filter(Boolean);
  } catch { return []; }
}

const cssFiles = findAllFiles(SRC, 'css');
const vueFiles = findAllFiles(SRC, 'vue');
const allFiles = [...cssFiles, ...vueFiles];

const hexRe = /#[0-9a-fA-F]{3,6}\b/g;
const tokenRe = /var\(--/g;

const fileStats = {};
for (const file of allFiles) {
  const relPath = file.startsWith(ROOT) ? file.slice(ROOT.length + 1) : file;
  if (IGNORE_FILES.includes(relPath)) continue;
  const content = readFileSync(file, 'utf-8');
  const hexCount = (content.match(hexRe) || []).length;
  const tokenCount = (content.match(tokenRe) || []).length;
  if (hexCount > 0 || tokenCount > 0) {
    fileStats[relPath] = { hexCount, tokenCount };
  }
}

const byDir = {};
let totalHex = 0, totalToken = 0;
for (const [file, { hexCount, tokenCount }] of Object.entries(fileStats)) {
  const dir = file.split('/').slice(0, 2).join('/');
  if (!byDir[dir]) byDir[dir] = { hexCount: 0, tokenCount: 0 };
  byDir[dir].hexCount += hexCount;
  byDir[dir].tokenCount += tokenCount;
  totalHex += hexCount;
  totalToken += tokenCount;
}

const offenders = Object.entries(fileStats).sort((a, b) => b[1].hexCount - a[1].hexCount);
const total = totalHex + totalToken;
const coveragePct = total > 0 ? ((totalToken / total) * 100).toFixed(1) : '0.0';

console.log('\n' + '='.repeat(70));
console.log('  Design Token Coverage Report');
console.log('  ' + new Date().toISOString().slice(0, 10));
console.log('='.repeat(70));

console.log('\n  By Directory:');
console.log('  ' + '-'.repeat(66));
console.log('  ' + 'Directory'.padEnd(25) + 'Hardcoded'.padStart(12) + 'Tokens'.padStart(12) + 'Coverage'.padStart(12));
console.log('  ' + '-'.repeat(66));
for (const [dir, { hexCount, tokenCount }] of Object.entries(byDir).sort()) {
  const pct = (hexCount + tokenCount) > 0 ? ((tokenCount / (hexCount + tokenCount)) * 100).toFixed(1) : '0.0';
  console.log('  ' + dir.padEnd(25) + hexCount.toString().padStart(12) + tokenCount.toString().padStart(12) + (pct + '%').padStart(12));
}
console.log('  ' + '-'.repeat(66));
console.log('  ' + 'TOTAL'.padEnd(25) + totalHex.toString().padStart(12) + totalToken.toString().padStart(12) + (coveragePct + '%').padStart(12));

console.log('\n  Top 15 files with most hardcoded colors:');
console.log('  ' + '-'.repeat(70));
for (const [file, { hexCount, tokenCount }] of offenders.slice(0, 15)) {
  const displayFile = file.length > 50 ? '...' + file.slice(-47) : file;
  console.log('  ' + hexCount.toString().padStart(4) + '  ' + displayFile.padEnd(52) + ' (tokens: ' + tokenCount + ')');
}

if (totalHex > 1000) {
  console.log('\n  ⚠️  Still ' + totalHex + ' hardcoded colors. Major debt detected.\n');
} else if (totalHex === 0) {
  console.log('\n  ✅ No hardcoded colors found. Design token coverage complete!\n');
} else {
  console.log('\n  👍 Down to ' + totalHex + ' hardcoded colors. Keep migrating.\n');
}

if (FAIL_ON_HEX && totalHex > MAX_HEX_TOTAL) {
  console.error('\n  ❌ Error: ' + totalHex + ' hardcoded colors found. Max allowed: ' + MAX_HEX_TOTAL + '.\n');
  process.exit(1);
}
