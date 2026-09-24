#!/usr/bin/env node
// Input: score evidence policy, scoring context, and scope regression tests
// Output: PASS/FAIL contract checks; exit 0 when text evidence remains scheme-scoped
// Pos: scripts/ — scoreparse source-scope regression guard
// 维护声明: 修改文本范围契约时同步更新本检查脚本与评分范围回归测试。
//
// Guard text-backed score evidence against bypassing scheme ownership checks.
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const policyPath = join(root, 'backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreQualificationPolicy.java');
const scopePath = join(root, 'backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringTextScopePolicy.java');
const testPath = join(root, 'backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidenceRoleAndHeadingTest.java');
const policy = readFileSync(policyPath, 'utf8');
const scopePolicy = readFileSync(scopePath, 'utf8');
const tests = readFileSync(testPath, 'utf8');
const checks = [
  ['text locator uses scoring-scheme ownership check', /OriginalScoringContext\.textScopeAt\(claim\.tenderText\(\), line\)/.test(policy)
    && /"OUT_OF_SCHEME"\.equals\(scope\.scopeStatus\(\)\)/.test(policy)],
  ['text evidence is not unconditionally accepted', !/boolean\s+inScheme\s*=\s*textBacked\s*\|\|/.test(policy)],
  ['policy validates source line against heading scope', /textScopeAt\(String text, int line\)/.test(scopePolicy)
    && /line >= scope\.startLine\(\) && line < scope\.endLine\(\)/.test(scopePolicy)
    && /nextSchemeBoundary\(headings, root, lines\.length\)/.test(scopePolicy)],
  ['foreign peer heading has a regression test', /textFactUnderUnrelatedPeerHeadingCannotJoinScoringScheme/.test(tests) && /ITEM_SCHEME_SCOPE_MISMATCH/.test(tests)],
];
let failed = false;
for (const [name, pass] of checks) {
  console.log(`${pass ? 'PASS' : 'FAIL'} ${name}`);
  failed ||= !pass;
}
process.exitCode = failed ? 1 : 0;
