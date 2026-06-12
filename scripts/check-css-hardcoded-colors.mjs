#!/usr/bin/env node
// Input: staged .vue and .css files from git index
// Output: warns when hardcoded hex/rgb colors appear in styles
// Pos: scripts/ — CSS Design Token 硬编码颜色检查 (2026-06-12 skill-progression-map)
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background (2026-05-20~06-12):
//   Multiple PRs (CO-174, CO-175, CO-177, #449, #448) fixed the same class
//   of issue — frontend styles used hardcoded hex colors (#303133, #C0C4CC)
//   instead of CSS variables (--text-placeholder, --gray-750). This check
//   flags new occurrences without blocking development.

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import process from 'node:process';

const script_name = 'check-css-hardcoded-colors';

const gitEnv = { ...process.env };
delete gitEnv.GIT_DIR;
delete gitEnv.GIT_WORK_TREE;

const ROOT_DIR = (() => {
  const res = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', env: gitEnv });
  return res.stdout ? res.stdout.trim() : process.cwd();
})();

function getStagedFiles() {
  const res = spawnSync('git', ['-C', ROOT_DIR, 'diff', '--cached', '--name-only', '--diff-filter=ACMR'], { encoding: 'utf8', env: gitEnv });
  return res.stdout.trim().split('\n').filter(f => /\.(vue|css|scss)$/i.test(f));
}

// Patterns that are known safe (already using CSS variables, etc.)
const SAFE_PATTERNS = [
  /(?:color|background|border-[a-z]+|fill|stroke):\s*var\(/,
  /--[a-z]/,
  /url\(/,
  /transparent/,
  /currentColor/,
  /inherit|initial|unset/,
];

// Matches hex colors (#rgb, #rrggbb) and rgb/rgba functions
const HARDCODED_COLOR_RE = /(#[0-9a-fA-F]{3,8}\b|rgba?\s*\(\d+[\s,]+[\d.]+[\s,]+[\d.]+(?:[\s,]+[\d.]+)?\))/g;

function checkFile(filePath) {
  const fullPath = path.isAbsolute(filePath) ? filePath : path.join(ROOT_DIR, filePath);
  let content;
  try { content = fs.readFileSync(fullPath, 'utf8'); }
  catch { return []; }

  // Only check <style> blocks in Vue files
  if (filePath.endsWith('.vue')) {
    const styleMatch = content.match(/<style[^>]*>[\s\S]*?<\/style>/g);
    if (!styleMatch) return [];
    content = styleMatch.join('\n');
  }

  const issues = [];
  const lines = content.split('\n');
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Skip safe patterns
    if (SAFE_PATTERNS.some(p => p.test(line))) continue;

    HARDCODED_COLOR_RE.lastIndex = 0;
    let match;
    while ((match = HARDCODED_COLOR_RE.exec(line)) !== null) {
      const trimmed = line.trim();
      issues.push({
        file: filePath,
        line: i + 1,
        color: match[1],
        code: trimmed.substring(0, 80) + (trimmed.length > 80 ? '...' : ''),
      });
    }
  }

  return issues;
}

function main() {
  const stagedFiles = getStagedFiles();
  if (stagedFiles.length === 0) {
    console.log(`${script_name}: no staged .vue/.css/.scss files, skipping.`);
    process.exit(0);
  }

  const allIssues = stagedFiles.flatMap(checkFile);
  if (allIssues.length === 0) {
    console.log(`${script_name}: passed. checked_files=${stagedFiles.length}`);
    process.exit(0);
  }

  console.warn(`\nWARN: ${script_name} — ${allIssues.length} hardcoded color(s) found:`);
  for (const issue of allIssues) {
    console.warn(`  ${issue.file}:${issue.line}  ${issue.color}`);
    console.warn(`    \u2192 ${issue.code}`);
  }
  console.warn(`\n  Prefer src/styles/variables.css CSS variables (--gray-xxx, --text-xxx, etc.)`);
  console.warn(`  over hardcoded hex/rgb values for design token consistency.\n`);
  process.exit(0);
}

main();
