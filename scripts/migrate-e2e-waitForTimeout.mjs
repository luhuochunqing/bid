#!/usr/bin/env node
// Input: scans all e2e/*.spec.js files for waitForTimeout calls
// Output: per-occurrence suggestions for what to replace with
// Pos: scripts/ — E2E waitForTimeout 存量迁移建议工具 (2026-06-12 skill-progression-map)
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   check-e2e-selectors.mjs (2026-05-29) marked waitForTimeout as error (blocking)
//   for new code. 66 legacy usages remain across 15 spec files. This tool audits
//   each occurrence and suggests the appropriate replacement strategy.
//
// Replacement strategies (by context):
//   - waitForResponse(url)   when preceding lines contain fetch/xhr/goto
//   - waitForSelector(sel)   when preceding lines contain locator/getBy*
//   - waitForURL(url)        when preceding lines contain page.goto
//   - waitForFunction(...)   as last resort with a guard condition

import fs from 'node:fs';
import { spawnSync } from 'node:child_process';
import process from 'node:process';

const script_name = 'migrate-e2e-waitForTimeout';

const ROOT_DIR = (() => {
  const res = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8' });
  return res.stdout ? res.stdout.trim() : process.cwd();
})();

function findAllE2eFiles() {
  const files = [];
  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = dir + '/' + entry.name;
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && entry.name.endsWith('.spec.js')) files.push(full);
    }
  }
  walk(ROOT_DIR + '/e2e');
  return files;
}

// Context analysis: look at 3 lines before waitForTimeout to guess intent
function suggestReplacement(lines, idx) {
  const line = lines[idx];

  // Check line itself for pattern clues
  const targetMs = line.match(/waitForTimeout\((\d+)\)/);
  const msWait = targetMs ? parseInt(targetMs[1], 10) : null;

  // Look back up to 5 lines for contextual clues
  const contextStart = Math.max(0, idx - 5);
  const contextLines = lines.slice(contextStart, idx + 1).join('\n');

  const suggestions = [];

  // Pattern 1: After page.goto -> waitForURL or waitForLoadState('networkidle')
  if (/page\.goto|page\.navigate/.test(contextLines)) {
    suggestions.push({
      pattern: 'navigation-follow',
      score: 7,
      message: 'Replace with waitForURL(urlPattern) or a targeted waitForSelector for a specific element that confirms the page loaded.',
      lines: contextLines,
    });
  }

  // Pattern 2: After page.locator or getBy* -> waitForSelector
  if (/locator\(|getByRole\(|getByText\(|getByTestId\(|getByLabel\(/.test(contextLines)) {
    suggestions.push({
      pattern: 'element-visible',
      score: 8,
      message: 'Replace with locator().waitFor({ state: "visible" }) or the getBy* equivalent.',
      lines: contextLines,
    });
  }

  // Pattern 3: After an API call/response pattern -> waitForResponse
  if (/fetch|axios|request|response|page\.route/.test(contextLines)) {
    suggestions.push({
      pattern: 'api-wait',
      score: 9,
      message: 'Replace with page.waitForResponse(urlOrPredicate) to wait for the specific API response.',
      lines: contextLines,
    });
  }

  // Pattern 4: About element click/action->visible -> waitForSelector
  if (/\.click\(|\.fill\(|\.press\(|\.dblclick\(|\.selectOption\(/.test(contextLines)) {
    suggestions.push({
      pattern: 'post-action-wait',
      score: 6,
      message: 'Replace with locator().waitFor({ state: "visible" }) on the element expected to appear after the action.',
      lines: contextLines,
    });
  }

  // Pattern 5: Long timeout (>=1000ms) -> likely animation/db wait
  if (msWait !== null && msWait >= 1000) {
    suggestions.push({
      pattern: 'animation-timeout',
      score: 5,
      message: `Long wait (${msWait}ms). Consider replacing with waitForSelector or waitForResponse for a specific condition.`,
      lines: contextLines,
    });
  }

  // Default fallback
  if (suggestions.length === 0) {
    suggestions.push({
      pattern: 'unknown-context',
      score: 3,
      message: 'No contextual clues found. Review the test to understand what condition this waitForTimeout is guarding, then replace with a targeted wait strategy.',
      lines: contextLines,
    });
  }

  suggestions.sort((a, b) => b.score - a.score);
  return suggestions;
}

function main() {
  const files = findAllE2eFiles();
  let totalOccurrences = 0;

  console.log(`\n${script_name}: scanning ${files.length} E2E spec files for waitForTimeout...\n`);

  for (const file of files) {
    const relPath = file.replace(ROOT_DIR + '/', '');
    const content = fs.readFileSync(file, 'utf8');
    const lines = content.split('\n');
    const occurrences = [];

    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes('waitForTimeout(')) {
        occurrences.push(i);
        totalOccurrences++;
      }
    }

    if (occurrences.length === 0) continue;

    console.log(`\n### ${relPath} — ${occurrences.length} occurrence(s)`);
    console.log(`| Line | Code | Best suggestion |`);
    console.log(`|------|------|-----------------|`);

    for (const idx of occurrences) {
      const code = lines[idx].trim();
      const suggestions = suggestReplacement(lines, idx);
      const best = suggestions[0];
      const displayCode = code.length > 70 ? code.substring(0, 67) + '...' : code;
      console.log(`| ${idx + 1} | \`${displayCode}\` | ${best.message.substring(0, 80)} |`);
    }
  }

  console.log(`\n--- Summary ---`);
  console.log(`Files scanned: ${files.length}`);
  console.log(`Files with waitForTimeout: ${files.filter(f => fs.readFileSync(f, 'utf8').includes('waitForTimeout(')).length}`);
  console.log(`Total waitForTimeout occurrences: ${totalOccurrences}`);
  console.log(`\nPriority order for cleanup (by file):`);
  const fileCounts = files.map(f => {
    const content = fs.readFileSync(f, 'utf8');
    const matches = content.match(/waitForTimeout\(/g);
    return { file: f, count: matches ? matches.length : 0 };
  }).filter(f => f.count > 0).sort((a, b) => b.count - a.count);
  for (const fc of fileCounts) {
    console.log(`  ${fc.count}x  ${fc.file.replace(ROOT_DIR + '/', '')}`);
  }
  console.log(`\nManual fix required for each occurrence.`);
  console.log(`After cleanup, run: grep -r "waitForTimeout" e2e/ | wc -l  (expected: 0)`);
}

main();
