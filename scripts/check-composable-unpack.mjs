#!/usr/bin/env node
// Input: src/ use*.js files (full scan or --staged)
// Output: warning when a composable parameter may be ref/raw ambiguous
// Pos: scripts/ - Composable ref/raw input convention linter (2026-06-21 CO-290 复盘)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   CO-290 中 canReviewBid 基于 opts.reviewerId 做比较，调用方误传 ref 对象，
//   但内部未解包，导致 String(ref) === "[object Object]" 或条件判定错误。
//   本检查重点关注“容器型参数（opts/options/params）内部字段直接 .value”这一
//   高危模式，推动混用场景统一使用 resolveOpt 解包。
//
// 规则（warning only，非阻断）：
//   1. 容器型参数（opts/options/params/config/settings）内部直接使用 .value 访问
//      其自身或其字段时告警：调用方可能传入 ref 对象，应通过 resolveOpt 解包。
//   2. 参数名以 Ref 结尾，但函数体中完全没有引用该参数，提醒检查是否死参数或命名误导。
//
// 限制：静态启发式检查，以提醒为主；不检查命名良好的 ref 参数（如 tenderRef）。

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
const SRC_DIR = path.join(ROOT_DIR, 'src');

const SKIP_DIRS = new Set(['node_modules', 'dist', '.vite', '__snapshots__']);

// 容器型参数：常见接受 ref/raw 混合值的 options 对象
const CONTAINER_PARAM_NAMES = new Set([
  'opts', 'options', 'params', 'config', 'settings', 'args',
]);

function getUseFiles() {
  if (STAGED_ONLY) {
    const result = spawnSync('git', [
      'diff', '--cached', '--name-only', '--diff-filter=ACMR',
    ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });
    return (result.stdout || '')
      .trim()
      .split(/\n/)
      .filter(f => f.startsWith('src/') && f.endsWith('.js'));
  }

  const files = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.startsWith('use') && entry.name.endsWith('.js')) files.push(full);
    }
  }
  walk(SRC_DIR);
  return files.map(f => path.relative(ROOT_DIR, f));
}

/**
 * 从文件中提取所有 export function useXxx(...) 函数定义。
 * 返回 { name, paramsText, body, startIndex } 数组。
 */
function extractExportedFunctions(content) {
  const functions = [];
  const fnRe = /export\s+function\s+(use[A-Z][a-zA-Z0-9_$]*)\s*\(\s*([^)]*)\)/g;
  let match;
  while ((match = fnRe.exec(content)) !== null) {
    const name = match[1];
    const paramsText = match[2].trim();
    const bodyStart = content.indexOf('{', match.index + match[0].length);
    if (bodyStart === -1) continue;
    const bodyEnd = findMatchingClose(content, bodyStart);
    if (bodyEnd === -1) continue;
    functions.push({
      name,
      paramsText,
      body: content.slice(bodyStart, bodyEnd + 1),
      startIndex: match.index,
    });
  }
  return functions;
}

function findMatchingClose(content, openIdx) {
  let depth = 0;
  let inString = null;
  for (let i = openIdx; i < content.length; i++) {
    const c = content[i];
    const prev = content[i - 1] || '';
    if (inString) {
      if (c === inString && prev !== '\\') inString = null;
      continue;
    }
    if (c === '"' || c === "'" || c === '`') {
      inString = c;
      continue;
    }
    if (c === '/' && content[i + 1] === '/') {
      while (i < content.length && content[i] !== '\n') i++;
      continue;
    }
    if (c === '/' && content[i + 1] === '*') {
      i += 2;
      while (i < content.length && !(content[i] === '*' && content[i + 1] === '/')) i++;
      i++;
      continue;
    }
    if (c === '{') depth++;
    if (c === '}') {
      depth--;
      if (depth === 0) return i;
    }
  }
  return -1;
}

/**
 * 解析参数名列表。仅支持简单参数、解构参数整体、带默认值的参数。
 */
function extractParamNames(paramsText) {
  const names = [];
  const compact = paramsText.replace(/\s+/g, ' ');
  const parts = compact.split(',').map(p => p.trim()).filter(Boolean);
  for (const part of parts) {
    if (part.startsWith('{') || part.startsWith('[')) {
      const m = part.match(/[{\[]\s*([a-zA-Z_$][a-zA-Z0-9_$]*)/);
      if (m) names.push(m[1]);
      continue;
    }
    const m = part.match(/^([a-zA-Z_$][a-zA-Z0-9_$]*)/);
    if (m) names.push(m[1]);
  }
  return names;
}

function checkFunction(fn) {
  const findings = [];
  const params = extractParamNames(fn.paramsText);

  for (const param of params) {
    const refLike = /Ref$/.test(param);
    const paramWord = `\\b${param}\\b`;
    const usesDotValue = new RegExp(`${paramWord}\\.value\\b`).test(fn.body);
    const usesUnpack = /\b(resolveOpt|toValue|unref)\s*\(/.test(fn.body);
    const usesParam = new RegExp(paramWord).test(fn.body);

    // Rule 1: 容器型参数内部字段直接使用 .value => 高危模式（CO-290）
    if (CONTAINER_PARAM_NAMES.has(param) && usesDotValue) {
      findings.push(
        `容器型参数 ${param} 内部直接使用 ${param}.value 或 ${param}.xxx.value；` +
        `若调用方可能传入 ref 对象，请在读取前使用 resolveOpt() 统一解包。`
      );
    }

    // Rule 2: 参数名以 Ref 结尾却完全没有被引用 => 死参数或命名误导
    if (refLike && !usesParam) {
      findings.push(
        `参数 ${param} 名以 Ref 结尾，但函数体未引用；请确认是否死参数或命名是否准确。`
      );
    }
  }

  return findings;
}

function main() {
  const files = getUseFiles();
  if (files.length === 0) {
    console.log('No composable files to check.');
    process.exit(0);
  }

  let totalFindings = 0;
  for (const relPath of files) {
    const fullPath = path.join(ROOT_DIR, relPath);
    if (!fs.existsSync(fullPath)) continue;
    const content = fs.readFileSync(fullPath, 'utf8');
    const fns = extractExportedFunctions(content);
    if (fns.length === 0) continue;

    for (const fn of fns) {
      const findings = checkFunction(fn);
      if (findings.length > 0) {
        totalFindings += findings.length;
        console.warn(`[composable-unpack] ${relPath}#${fn.name}`);
        for (const f of findings) {
          console.warn(`  ⚠ ${f}`);
        }
      }
    }
  }

  if (totalFindings > 0) {
    console.warn('');
    console.warn(`检测到 ${totalFindings} 处 composable 参数 ref/raw 使用风险。`);
    console.warn('建议：容器型参数内部字段读取前使用 resolveOpt() 解包。');
    process.exit(0);
  }

  console.log('Composable ref/raw convention check passed.');
  process.exit(0);
}

main();
