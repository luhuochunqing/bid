// Input: backend/target/scoreparse-golden-report.json（GoldenCasesReplayTest 落盘）
// Output: Markdown 明细表 → stdout；文件缺失时 exit 1 并给可操作提示
// Pos: scripts/eval-scoreparse —— node 原生零依赖，随 npm run eval:scoreparse:report 冒烟

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const REPORT_PATH = path.join(REPO_ROOT, 'backend', 'target', 'scoreparse-golden-report.json');

let report;
try {
  report = JSON.parse(readFileSync(REPORT_PATH, 'utf8'));
} catch (error) {
  console.error(`[eval-scoreparse] 报告不存在或不可解析: ${REPORT_PATH}`);
  console.error(`  reason: ${error.message}`);
  console.error('  请先跑第一层夹具回放生成报告:');
  console.error('    npm run eval:scoreparse:replay');
  console.error('  （等价于 mvn -f backend/pom.xml test -Dtest=\'GoldenCasesReplayTest\'）');
  process.exit(1);
}

const cases = Array.isArray(report.cases) ? report.cases : [];
if (cases.length === 0) {
  console.error('[eval-scoreparse] 报告内无 cases 数据，疑似测试未实际执行回放用例。');
  process.exit(1);
}

const pct = (value) => `${(Number(value) * 100).toFixed(1)}%`;

console.log('# AI 评分标准解析 · 金标回放报告');
console.log('');
console.log('| Case | regex | table | TP | FP | FN | Precision | Recall | F1 | 权重合计(前→后) |');
console.log('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |');

let totalTp = 0;
let totalFp = 0;
let totalFn = 0;

for (const item of cases) {
  const lanes = item.laneCounts ?? {};
  totalTp += item.tp ?? 0;
  totalFp += item.fp ?? 0;
  totalFn += item.fn ?? 0;
  const weightsBefore = item.weightTotalBeforeNormalize ?? '-';
  const weightsAfter = item.weightTotalAfterNormalize ?? '-';
  const originalTotal = item.originalTotal == null ? 'null' : item.originalTotal;
  console.log(
    `| ${item.case} `
    + `| ${lanes.regex ?? '-'} | ${lanes.table ?? '-'} `
    + `| ${item.tp} | ${item.fp} | ${item.fn} `
    + `| ${pct(item.precision)} | ${pct(item.recall)} | ${pct(item.f1)} `
    + `| ${weightsBefore} → ${weightsAfter} (originalTotal=${originalTotal}) |`,
  );
}

const precision = totalTp + totalFp === 0 ? 0 : totalTp / (totalTp + totalFp);
const recall = totalTp + totalFn === 0 ? 0 : totalTp / (totalTp + totalFn);
const f1 = precision + recall === 0 ? 0 : (2 * precision * recall) / (precision + recall);
console.log(`| **合计** | — | — | ${totalTp} | ${totalFp} | ${totalFn} | ${pct(precision)} | ${pct(recall)} | ${pct(f1)} | — |`);

let totalRegex = 0;
let totalTable = 0;
let totalCases = cases.length;
let reviewRequiredCount = 0;
let structuralFailureCount = 0;
let totalEvidenceSum = 0;
let validEvidenceCount = 0;

for (const item of cases) {
  const lanes = item.laneCounts ?? {};
  totalRegex += Number(lanes.regex ?? 0) + Number(lanes.regexFullText ?? 0);
  totalTable += Number(lanes.table ?? 0);
  if (item.decisionStatus === 'REVIEW_REQUIRED' || item.decisionStatus === 'STRUCTURE_INCOMPLETE') {
    reviewRequiredCount += 1;
  }
  if (item.structurallyValid === false) {
    structuralFailureCount += 1;
  }
  if (typeof item.evidenceCoverage === 'number') {
    totalEvidenceSum += item.evidenceCoverage;
    validEvidenceCount += 1;
  }
}

const reviewRate = totalCases === 0 ? 0 : reviewRequiredCount / totalCases;
const structFailureRate = totalCases === 0 ? 0 : structuralFailureCount / totalCases;
const avgEvidenceCoverage = validEvidenceCount === 0 ? 0 : totalEvidenceSum / validEvidenceCount;

console.log('');
console.log('## 关键质量与安全看板 (Phase 6 KPI)');
console.log('');
console.log(`- **Lane 分布**: 正则候选 ${totalRegex} 项 / 表格候选 ${totalTable} 项`);
console.log(`- **复核率**: ${pct(reviewRate)} (${reviewRequiredCount}/${totalCases})`);
console.log(`- **结构失败率**: ${pct(structFailureRate)} (${structuralFailureCount}/${totalCases})`);
console.log(`- **证据覆盖率**: ${pct(avgEvidenceCoverage)}`);
console.log(`- **F1 综合指标**: ${pct(f1)}`);
