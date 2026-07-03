#!/usr/bin/env node
// Input: staged Vue files from git index
// Output: fails when frontend templates directly render known enum fields without a formatter
// 维护声明: 若枚举字段清单、formatter 命名惯例变更，请同步更新本脚本 + 字段清单。
// Pos: scripts/ — 前端枚举字段直接渲染检测 (2026-07-03 PR !1632 防复发)
//
// Background (2026-07-03, PR !1632):
//   PR !1571 把后端 customerType 归一化为枚举名（CENTRAL_SOE 等）后，
//   前端两个展示位仍直接 {{ row.customerType }} 渲染，导致用户看到英文。
//   根因：缺乏静态检查，归一化时遗漏展示位翻译层。
//
// 本脚本检测：已知枚举字段在 <template> 的 {{ }} 表达式中被直接渲染，
//           而未经过 formatter 函数（如 customerTypeLabel）。
//
// 豁免机制：
//   在被检测的 mustache 表达式上方一行加 HTML 注释：
//     <!-- SAFE: <具体豁免理由> -->
//     {{ row.customerType }}
//   仅限展示原值有业务必要的场景（如调试页/原始数据查看页）。
//
// 字段清单来源：projectListFormatters.js 中已有 formatter 的字段
//   - customerType → customerTypeLabel()
//   - projectType → projectTypeLabel()
//   - priority → priorityLabel()
//   - stage → stageText()
//   - source/sourceModule → sourceText()
// 后续新增 formatter 时，同步扩展 ENUM_FIELDS。

import fs from 'node:fs';
import { spawnSync } from 'node:child_process';

const script_name = 'check-vue-enum-direct-render';

// 已知需要 formatter 翻译的枚举字段清单。
// key = 字段名（出现在 row.xxx / project.xxx 等访问形式中）
// value = 推荐的 formatter 函数名（用于错误提示）
//
// 字段纳入清单的判定标准：
//   1. 后端已归一化为枚举名（如 CENTRAL_SOE）
//   2. 已有对应 formatter 函数
//   3. 存量直接渲染位已全部修复或 SAFE 豁免
// 后续可逐步扩展（如 priority/stage/source），扩展前需先处理存量。
const ENUM_FIELDS = {
  customerType: 'customerTypeLabel',
  projectType: 'projectTypeLabel',
};

// 已识别的 formatter 函数名（表达式包含这些函数调用时视为已翻译）
const FORMATTER_FUNCS = new Set(Object.values(ENUM_FIELDS));

const gitEnv = { ...process.env };
delete gitEnv.GIT_DIR;
delete gitEnv.GIT_WORK_TREE;

const ROOT_DIR = (() => {
  const res = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', env: gitEnv });
  return res.stdout ? res.stdout.trim() : process.cwd();
})();

function getStagedVueFiles() {
  const result = spawnSync('git', [
    'diff', '--cached', '--name-only', '--diff-filter=ACMR',
  ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });

  return (result.stdout || '')
    .trim()
    .split(/\n/)
    .filter(f => f.endsWith('.vue'))
    .filter(f => f.startsWith('src/'));
}

// 提取 .vue 文件的 <template> 部分（含可能的多个 template 块）
function extractTemplate(content) {
  const templateMatches = [];
  const templateRegex = /<template[^>]*>([\s\S]*?)<\/template>/gi;
  let m;
  while ((m = templateRegex.exec(content)) !== null) {
    const startOffset = m.index + m[0].indexOf(m[1]);
    templateMatches.push({ content: m[1], startOffset });
  }
  return templateMatches;
}

// 将偏移量转为行号
function offsetToLine(content, offset) {
  return (content.slice(0, offset).match(/\n/g) || []).length + 1;
}

// 检测单个 mustache 表达式是否是「直接字段访问」形式
// 直接访问模式：row.customerType / project.customerType / form.customerType / data.customerType
// 排除：包含函数调用（customerTypeLabel(...)）、字符串字面量、数学运算
function isDirectFieldAccess(expr, fieldName) {
  const trimmed = expr.trim();

  // 包含 formatter 函数调用 → 已翻译
  for (const fn of FORMATTER_FUNCS) {
    if (trimmed.includes(fn + '(')) return false;
  }

  // 包含其他函数调用（如 getProjectStatusText(...)、formatDate(...)）→ 视为已处理
  if (/\w+\s*\(/.test(trimmed)) return false;

  // 检测直接字段访问：<identifier>.<fieldName>
  // 允许的 identifier: row, project, form, data, item, scope, record, row.data 等
  const fieldAccessPattern = new RegExp(`\\b\\w+\\.${fieldName}\\b`);
  if (!fieldAccessPattern.test(trimmed)) return false;

  // 排除：v-bind、v-if、v-show 等 directive 表达式（mustache 之外的）
  // （本函数只在 mustache 内容上调用，无需排除 directive）
  return true;
}

function checkFile(filePath) {
  const fullPath = `${ROOT_DIR}/${filePath}`;
  if (!fs.existsSync(fullPath)) return [];

  const content = fs.readFileSync(fullPath, 'utf8');
  const templates = extractTemplate(content);
  const findings = [];

  for (const tpl of templates) {
    const tplContent = tpl.content;
    const tplStart = tpl.startOffset;

    // 匹配所有 mustache 表达式 {{ ... }}
    // 注意：排除转义的 {{ }} 和 v-bind 等指令中的 {{ }}
    const mustacheRegex = /\{\{([\s\S]*?)\}\}/g;
    let m;
    while ((m = mustacheRegex.exec(tplContent)) !== null) {
      const expr = m[1];
      const exprOffset = tplStart + m.index;
      const exprLine = offsetToLine(content, exprOffset);

      // 检查每个枚举字段
      for (const fieldName of Object.keys(ENUM_FIELDS)) {
        if (isDirectFieldAccess(expr, fieldName)) {
          // 检查上方一行是否有 SAFE 豁免注释
          const lines = content.split('\n');
          const prevLine = lines[exprLine - 2] || ''; // exprLine 是 1-based，上一行是 exprLine-2 index
          if (/<!--\s*SAFE:/i.test(prevLine)) {
            continue; // 已豁免
          }

          findings.push({
            file: filePath,
            line: exprLine,
            field: fieldName,
            expr: expr.trim(),
            formatter: ENUM_FIELDS[fieldName],
          });
        }
      }
    }
  }

  return findings;
}

function main() {
  const files = getStagedVueFiles();

  if (files.length === 0) {
    console.log(`${script_name}: no staged Vue files, skipping.`);
    process.exit(0);
  }

  const allFindings = [];
  for (const file of files) {
    const findings = checkFile(file);
    allFindings.push(...findings);
  }

  if (allFindings.length > 0) {
    console.error(`\n${script_name}: ${allFindings.length} 处直接渲染枚举字段未经过 formatter：\n`);

    const byFile = {};
    for (const f of allFindings) {
      if (!byFile[f.file]) byFile[f.file] = [];
      byFile[f.file].push(f);
    }

    for (const [file, findings] of Object.entries(byFile)) {
      console.error(`  ${file}:`);
      for (const f of findings) {
        console.error(`    L${f.line}  {{ ${f.expr} }} — 字段 "${f.field}" 应使用 ${f.formatter}() 翻译`);
      }
    }

    console.error(`\n${script_name}: 后端枚举字段归一化为枚举名后，前端展示位必须经过 formatter 翻译。`);
    console.error(`  根因: PR !1571 归一化 customerType 为枚举名，遗漏展示位翻译层 → 用户看到英文`);
    console.error(`  参考: PR !1632 修复 + lessons-learned.md §34`);
    console.error(`\n${script_name}: 如确需展示原值（调试/原始数据查看），在 mustache 上方加：`);
    console.error(`  <!-- SAFE: <具体豁免理由> -->`);
    console.error(`\n${script_name}: ${allFindings.length} error(s) — blocking.\n`);
    process.exit(1);
  } else {
    console.log(`${script_name}: no direct enum field rendering detected.`);
  }
}

main();
