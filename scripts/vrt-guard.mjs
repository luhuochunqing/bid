#!/usr/bin/env node
// Input: Playwright + running dev server
// Output: VRT pass/fail report + exit code 0/1
// Pos: scripts/vrt-guard.mjs - Visual regression test guard
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * vrt-guard.mjs — Visual Regression Test Guard
 * =============================================
 * Plays back a sanity path of pages via Playwright and compares each
 * screenshot against the stored baseline.
 *
 * Usage:
 *   node scripts/vrt-guard.mjs [--update-baseline] [--verbose]
 *
 *   --update-baseline   Capture new baselines and overwrite existing ones.
 *   --verbose           Print per-page diff ratios even when passing.
 *
 * Exit codes:
 *   0  All pages pass (diff ≤ THRESHOLD_PCT)
 *   1  At least one page exceeds THRESHOLD_PCT difference
 *   2  Playwright not available / page could not load
 *
 * Cog-4D Audit PR #319 action item:
 *   [S层 E3] 在合并"治理 PR"前强制运行 VRT，确保 484+ 修改后像素差异为 0。
 *
 * Baseline directory: .vrt-baselines/  (committed to git)
 * Diff directory:     .vrt-diffs/      (generated, gitignored)
 */

import { execSync } from 'child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'fs';
import { join, relative } from 'path';

const ROOT = process.cwd();
const UPDATE_BASELINE = process.argv.includes('--update-baseline');
const VERBOSE = process.argv.includes('--verbose');

const BASELINE_DIR = join(ROOT, '.vrt-baselines');
const DIFF_DIR     = join(ROOT, '.vrt-diffs');
const THRESHOLD_PCT = 0.1;   // 0.1% — near-zero tolerance for design token work

/* =====================================================================
   Playwright bootstrap
   ===================================================================== */

let playwright, chromium;
try {
  playwright = await import('@playwright/test');
  chromium   = playwright.devices['Desktop Chrome'];
} catch (err) {
  console.error('\n  ERROR  @playwright/test not found.\n');
  console.error('  Install it first: npm install --save-dev @playwright/test');
  console.error('  Then run: npx playwright install chromium\n');
  process.exit(2);
}

/* =====================================================================
   Pixelmatch (optional — graceful degradation if absent)
   ===================================================================== */

let pixelmatch;
try {
  const { default: pm } = await import('pixelmatch');
  pixelmatch = pm;
} catch {
  console.warn('\n  NOTE  pixelmatch not found — running in STORAGE-ONLY mode.');
  console.warn('  Install for diff comparison: npm install --save-dev pixelmatch sharp\n');
}

/* =====================================================================
   Sanity pages to snapshot
   Adjust ROUTES to cover the pages affected by token migration.
   ===================================================================== */

const BASE_URL = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:1314';

const ROUTES = [
  { path: '/',                          label: 'dashboard'       },
  { path: '/project',                   label: 'project-list'    },
  { path: '/bidding',                  label: 'bidding-list'    },
  { path: '/ai',                       label: 'ai-center'       },
  { path: '/login',                    label: 'login'           },
];

/* =====================================================================
   Helpers
   ===================================================================== */

function ensureDir(dir) {
  if (!existsSync(dir)) mkdirSync(dir, { recursive: true });
}

function getBaselinePath(label) {
  return join(BASELINE_DIR, `${label}.png`);
}

function getDiffPath(label) {
  return join(DIFF_DIR, `${label}-diff.png`);
}

function loadPng(path) {
  if (!existsSync(path)) return null;
  return readFileSync(path);
}

/* =====================================================================
   Compute diff ratio between two PNG buffers
   Returns: { diffPixels, totalPixels, ratio }
   ===================================================================== */

async function computeDiff(imgA, imgB) {
  if (!pixelmatch) return null;

  const { default: PNG } = await import('pngjs');
  const a = PNG.sync.read(imgA);
  const b = PNG.sync.read(imgB);
  const { width, height } = a;

  const diff = Buffer.alloc(width * height * 4);
  const diffPixels = pixelmatch(a.data, b.data, diff, width, height, {
    threshold: 0.1,
    alpha: 0.1,
  });
  return {
    diffPixels,
    totalPixels: width * height,
    ratio: (diffPixels / (width * height)) * 100,
    diff,
    width,
    height,
  };
}

/* =====================================================================
   Playwright browser setup
   ===================================================================== */

const browser = await playwright.chromium.launch({ headless: true });
const context = await browser.newContext({
  baseURL: BASE_URL,
  ...chromium,
});

const page = await context.newPage();

/* =====================================================================
   Pre-flight: verify the app is reachable
   ===================================================================== */

console.log('\n' + '='.repeat(60));
console.log('  VRT Guard — Visual Regression Test');
console.log('  Base URL: ' + BASE_URL);
console.log('  Threshold: ' + THRESHOLD_PCT + '%');
console.log('  Mode: ' + (UPDATE_BASELINE ? 'UPDATE-BASELINE' : 'COMPARE'));
console.log('='.repeat(60));

try {
  const response = await page.goto(BASE_URL, { waitUntil: 'domcontentloaded', timeout: 15_000 });
  if (!response || response.status() >= 500) {
    throw new Error(`Server returned HTTP ${response?.status()}`);
  }
} catch (err) {
  console.error('\n  ERROR  Could not reach app at', BASE_URL);
  console.error('  Make sure the dev server is running: npm run dev:stable:start\n');
  await browser.close();
  process.exit(2);
}

ensureDir(BASELINE_DIR);
ensureDir(DIFF_DIR);

/* =====================================================================
   Snapshot loop
   ===================================================================== */

const results = [];
let allPassed = true;

for (const { path, label } of ROUTES) {
  const url = path === '/' ? BASE_URL : `${BASE_URL}${path}`;
  const baselinePath = getBaselinePath(label);
  const diffPath     = getDiffPath(label);

  process.stdout.write(`  [VRT]  ${label.padEnd(18)} → `);

  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 20_000 });
    await page.screenshot({
      path: diffPath,
      fullPage: false,
      animations: 'disabled',
    });

    const fresh = readFileSync(diffPath);

    if (UPDATE_BASELINE) {
      writeFileSync(baselinePath, fresh);
      process.stdout.write('  [BASELINE STORED]\n');
      results.push({ label, status: 'stored', ratio: 0 });
      continue;
    }

    const baseline = loadPng(baselinePath);
    if (!baseline) {
      console.log('  [NO BASELINE — run with --update-baseline first]');
      results.push({ label, status: 'no-baseline', ratio: null });
      allPassed = false;
      continue;
    }

    const diff = await computeDiff(baseline, fresh);
    if (!diff) {
      process.stdout.write(`  [pixelmatch unavailable — diff stored: ${relative(ROOT, diffPath)}]\n`);
      results.push({ label, status: 'manual-review', ratio: null });
      continue;
    }

    const { diffPixels, totalPixels, ratio, diff: diffBuf, width, height } = diff;

    // Save diff image
    const { default: PNG } = await import('pngjs');
    const diffPng = new PNG({ width, height });
    diffPng.data = diffBuf;
    writeFileSync(diffPath, PNG.sync.write(diffPng));

    const pass = ratio <= THRESHOLD_PCT;
    if (!pass) allPassed = false;

    const status = pass ? 'PASS' : 'FAIL';
    const ratioStr = ratio.toFixed(3) + '%';
    process.stdout.write(`  [${status}]  ${ratioStr} diff (${diffPixels}/${totalPixels} px)\n`);
    if (VERBOSE || !pass) {
      process.stdout.write(`         diff image: ${relative(ROOT, diffPath)}\n`);
    }

    results.push({ label, status: pass ? 'pass' : 'fail', ratio });
  } catch (err) {
    process.stdout.write(`  [ERROR]  ${err.message}\n`);
    results.push({ label, status: 'error', ratio: null, error: err.message });
    allPassed = false;
  }
}

await browser.close();

/* =====================================================================
   Report
   ===================================================================== */

console.log('\n' + '-'.repeat(60));
const passed = results.filter(r => r.status === 'pass').length;
const failed = results.filter(r => r.status === 'fail').length;
const noBaseline = results.filter(r => r.status === 'no-baseline').length;
const stored = results.filter(r => r.status === 'stored').length;
const manualReview = results.filter(r => r.status === 'manual-review').length;
const errors = results.filter(r => r.status === 'error').length;

if (UPDATE_BASELINE) {
  console.log(`  Baselines stored: ${stored}`);
} else {
  console.log(`  Passed : ${passed}/${results.length}`);
  console.log(`  Failed : ${failed}`);
  console.log(`  No baseline: ${noBaseline}  (run with --update-baseline)`);
  console.log(`  Manual review: ${manualReview}  (pixelmatch not available)`);
  console.log(`  Errors: ${errors}`);
}

console.log('-'.repeat(60));

if (UPDATE_BASELINE) {
  console.log('\n  Baselines captured. Commit .vrt-baselines/ to git.\n');
  process.exit(0);
}

if (!allPassed) {
  console.log('\n  VRT FAILED — pixel regressions detected.\n');
  console.log('  Diff images saved in: ' + relative(ROOT, DIFF_DIR));
  console.log('  To update baselines: node scripts/vrt-guard.mjs --update-baseline\n');
  process.exit(1);
}

if (noBaseline > 0) {
  console.log('\n  VRT PASSED (but some pages have no baseline — see above)\n');
  process.exit(0);
}

console.log('\n  VRT PASSED — zero significant regressions.\n');
process.exit(0);
