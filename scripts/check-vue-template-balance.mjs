#!/usr/bin/env node
// Input: staged or all .vue files
// Output: warning when <template> section has unmatched opening/closing tags
// Pos: scripts/ — Vue 模板标签平衡检查 (2026-06-21 CO-290 复盘)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   CO-290 修复过程中，TaskKanban.vue 第 53 行的 </div> 闭合标签被误删，
//   但 npm run build 通过（Vite 对结构错误不阻断），导致 REVIEW 列的 <el-empty>
//   被错误嵌套在 card-actions div 内。本检查用于在 pre-commit 阶段捕获
//   此类明显的模板标签不平衡。
//
// 规则（warning only，非阻断）：
//   1. 扫描 .vue 文件的 <template> 区域
//   2. 提取非自闭合标签的开始标签与结束标签
//   3. 使用栈匹配；若最终栈非空或遇到不匹配的结束标签，则告警
//
// 限制：
//   - 不解析 v-if/v-for 等动态结构，仅做静态标签平衡检查
//   - 不处理 <script setup> 中动态渲染的组件
//   - 对含特殊语法（如 <slot>、<component :is="...">）可能误报，
//     可通过 BALANCE_SKIP_TAGS 排除

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

// 自闭合标签白名单（HTML void elements + 常见 Vue 单标签组件 + 占位符）
const VOID_TAGS = new Set([
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
  // Vue/ElemePlus 常见单标签
  'el-divider', 'el-input', 'el-empty',
  // 占位符：被保护起来的 <tag ... />
  'void-placeholder',
]);

// 完全忽略的标签（解析器元标签或动态占位）
const SKIP_TAGS = new Set([
  'template', 'slot', 'component',
]);

function getVueFiles() {
  if (STAGED_ONLY) {
    const result = spawnSync('git', [
      'diff', '--cached', '--name-only', '--diff-filter=ACMR',
    ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });
    return (result.stdout || '')
      .trim()
      .split(/\n/)
      .filter(f => f.startsWith('src/') && f.endsWith('.vue'));
  }

  const files = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('.') || entry.name === 'node_modules') continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith('.vue')) files.push(path.relative(ROOT_DIR, full));
    }
  }
  walk(path.join(ROOT_DIR, 'src'));
  return files;
}

function extractTemplate(content) {
  // 定位最外层 <template>（支持 <template lang="xxx">）
  const outerStartMatch = content.match(/<template(?:\s+lang=["'][^"']*["'])?\s*>/);
  if (!outerStartMatch) return null;
  const realStart = outerStartMatch.index;

  // 找到匹配的 </template>：跳过内部 <template #slot> 等
  let depth = 1;
  let pos = realStart + outerStartMatch[0].length;
  const endTag = '</template>';
  const startTagRe = /<template(?:\s+[^>]*)?>/g;

  while (pos < content.length && depth > 0) {
    const nextEnd = content.indexOf(endTag, pos);
    if (nextEnd === -1) return null;

    // 在 pos..nextEnd 之间是否有新的 <template ...> 开始？
    startTagRe.lastIndex = pos;
    const nextStartMatch = startTagRe.exec(content);
    if (nextStartMatch && nextStartMatch.index < nextEnd) {
      depth++;
      pos = nextStartMatch.index + nextStartMatch[0].length;
    } else {
      depth--;
      if (depth === 0) {
        return content.slice(realStart, nextEnd + endTag.length);
      }
      pos = nextEnd + endTag.length;
    }
  }

  return null;
}

function checkBalance(filePath, template) {
  const stack = [];
  const findings = [];
  let i = 0;
  let inString = null;
  let inComment = false;
  let tagBuffer = null;

  function flushTag() {
    if (!tagBuffer) return;
    const raw = tagBuffer.join('');
    tagBuffer = null;

    // raw 形如 <tag ...> 或 </tag> 或 <tag ... />
    const inner = raw.slice(1, -1).trim();
    if (inner.startsWith('!')) return; // <!DOCTYPE, <!-- 等

    let isEndTag = false;
    let body = inner;
    if (body.startsWith('/')) {
      isEndTag = true;
      body = body.slice(1).trim();
    }

    const nameMatch = body.match(/^([a-zA-Z][a-zA-Z0-9-]*)/);
    if (!nameMatch) return;
    const tagName = nameMatch[1];
    body = body.slice(tagName.length).trim();
    const lowerTag = tagName.toLowerCase();

    if (SKIP_TAGS.has(lowerTag)) return;
    if (VOID_TAGS.has(lowerTag)) return;
    if (body.endsWith('/')) return; // 自闭合

    if (isEndTag) {
      if (stack.length === 0) {
        findings.push(`未匹配到的结束标签 </${tagName}>`);
        return;
      }
      const last = stack[stack.length - 1];
      if (last.name !== lowerTag) {
        findings.push(`标签不匹配：期望 </${last.name}>，实际遇到 </${tagName}>`);
      } else {
        stack.pop();
      }
    } else {
      stack.push({ name: lowerTag, raw: tagName });
    }
  }

  while (i < template.length) {
    const c = template[i];
    const c2 = template[i + 1] || '';
    const c3 = template[i + 2] || '';
    const c4 = template[i + 3] || '';

    if (inComment) {
      if (c === '-' && c2 === '-' && c3 === '>') {
        inComment = false;
        i += 3;
        continue;
      }
      i++;
      continue;
    }

    if (inString) {
      if (c === '\\') {
        i += 2;
        continue;
      }
      if (c === inString) inString = null;
      i++;
      continue;
    }

    if (c === '<' && c2 === '!' && c3 === '-' && c4 === '-') {
      inComment = true;
      i += 4;
      continue;
    }

    if (c === '"' || c === "'" || c === '`') {
      inString = c;
      i++;
      continue;
    }

    if (c === '<') {
      tagBuffer = ['<'];
      i++;
      continue;
    }

    if (tagBuffer) {
      tagBuffer.push(c);
      if (c === '>') {
        flushTag();
      }
      i++;
      continue;
    }

    i++;
  }

  while (stack.length > 0) {
    const unclosed = stack.pop();
    findings.push(`开始标签 <${unclosed.raw}> 未闭合`);
  }

  return findings;
}

function main() {
  const files = getVueFiles();
  if (files.length === 0) {
    console.log('No .vue files to check.');
    process.exit(0);
  }

  let totalFindings = 0;
  for (const relPath of files) {
    const fullPath = path.join(ROOT_DIR, relPath);
    if (!fs.existsSync(fullPath)) continue;

    const content = fs.readFileSync(fullPath, 'utf8');
    const template = extractTemplate(content);
    if (!template) continue;

    const findings = checkBalance(relPath, template);
    if (findings.length > 0) {
      totalFindings += findings.length;
      console.warn(`[vue-template-balance] ${relPath}`);
      for (const f of findings) {
        console.warn(`  ⚠ ${f}`);
      }
    }
  }

  if (totalFindings > 0) {
    console.warn('');
    console.warn(`检测到 ${totalFindings} 处 Vue 模板标签平衡问题。`);
    console.warn('请在提交前用浏览器或开发者工具验证模板渲染结构。');
    // 非阻断：返回 0，仅作为 warning
    process.exit(0);
  }

  console.log('Vue template balance check passed.');
  process.exit(0);
}

main();
