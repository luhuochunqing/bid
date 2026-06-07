#!/usr/bin/env node
// Input: src/ directory (.css and .vue files)
// Output: modified files with hex colors replaced by CSS var() references
// Pos: scripts/migrate-colors.mjs - Color token migration script
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { readFileSync, writeFileSync } from 'fs';
import { execSync } from 'child_process';
import { join, relative } from 'path';

const ROOT = process.cwd();
const DRY_RUN = process.argv.includes('--dry-run');
const VERBOSE = process.argv.includes('--verbose');

// Build lookup: normalized hex → CSS variable
const COLOR_MAP = new Map([
  // Brand greens
  ['2e7659', 'var(--brand-xiyu-logo)'],
  ['27674e', 'var(--brand-xiyu-logo-hover)'],
  ['1f553f', 'var(--brand-xiyu-logo-active)'],
  ['e7f2ed', 'var(--brand-xiyu-logo-light)'],
  // Brand blues
  ['0066cc', 'var(--brand-primary)'],
  ['3388dd', 'var(--brand-primary-light)'],
  ['0052a3', 'var(--brand-primary-dark)'],
  // Functional
  ['00aa44', 'var(--color-success)'],
  ['ff8800', 'var(--color-warning)'],
  ['dd2200', 'var(--color-danger)'],
  // Grays
  ['f5f7fa', 'var(--gray-50)'],
  ['e8e8e8', 'var(--gray-100)'],
  ['e8ecf0', 'var(--gray-150)'],
  ['d0d0d0', 'var(--gray-200)'],
  ['e4e7ed', 'var(--gray-250)'],
  ['b0b0b0', 'var(--gray-300)'],
  ['909399', 'var(--gray-350)'],
  ['999999', 'var(--gray-400)'],
  ['666666', 'var(--gray-500)'],
  ['606266', 'var(--gray-550)'],
  ['444444', 'var(--gray-600)'],
  ['333333', 'var(--gray-700)'],
  ['303133', 'var(--gray-750)'],
  ['222222', 'var(--gray-800)'],
  ['1a1a1a', 'var(--gray-900)'],
  ['6b7280', 'var(--gray-650)'],
  ['111827', 'var(--gray-950)'],
  // Semantic text colors (preferred - last set wins)
  ['1a1a1a', 'var(--text-primary)'],
  ['666666', 'var(--text-secondary)'],
  ['606266', 'var(--text-secondary-ui)'],
  ['999999', 'var(--text-tertiary)'],
  ['909399', 'var(--text-muted)'],
  ['64748b', 'var(--text-slate)'],
  ['bbbbbb', 'var(--text-placeholder)'],
  // Sidebar
  ['334155', 'var(--sidebar-text)'],
  ['475569', 'var(--sidebar-text-secondary)'],
  // Backgrounds (preferred - last set wins)
  ['f5f7fa', 'var(--bg-subtle)'],
  ['ffffff', 'var(--bg-card)'],
  ['f0f2f5', 'var(--bg-page)'],
  ['f9fafb', 'var(--bg-subtle)'], // Common subtle gray bg
  // Accent
  ['0369a1', 'var(--accent-blue)'],
  ['e0f2fe', 'var(--accent-blue-light)'],
  // PR #320 Regressions and Common Offenses
  ['166534', 'var(--brand-xiyu-logo-active)'], // Solid green button
  ['dc2626', 'var(--color-danger)'], // Error state red
  ['b91c1c', 'var(--color-danger-dark)'],
  ['059669', 'var(--color-success-dark)'],
  ['fde68a', 'var(--color-warning-light)'],
  ['ef4444', 'var(--color-danger)'],
  ['10b981', 'var(--color-success)'],
  ['e2e8f0', 'var(--gray-200)'],
  ['f1f5f9', 'var(--gray-50)'],
  ['94a3b8', 'var(--gray-400)'],
  ['fee2e2', 'var(--color-danger-bg)'],
  ['fef3c7', 'var(--color-warning-bg)'],
]);

function normalizeHex(hex) {
  let h = hex.replace(/^#/, '').toLowerCase();
  // Expand 3-digit hex to 6-digit
  if (h.length === 3) {
    h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2];
  }
  return h;
}

function findFiles(dir, exts) {
  const extFilter = exts.map(e => `-name '*.${e}'`).join(' -o ');
  try {
    const out = execSync(`find ${dir} \\( ${extFilter} \\) -not -path '*/node_modules/*'`, {
      encoding: 'utf-8', cwd: ROOT,
    });
    return out.trim().split('\n').filter(Boolean);
  } catch { return []; }
}

// NEVER modify variables.css — it defines the tokens, don't self-reference them
const SKIP_FILES = ['src/styles/variables.css', 'src/styles/variables.scss'];

function fileIncluded(fullPath) {
  const rel = relative(ROOT, fullPath);
  return !SKIP_FILES.includes(rel);
}

const allFiles = findFiles(join(ROOT, 'src'), ['css', 'vue']).filter(fileIncluded);

let totalReplaced = 0;
let totalFiles = 0;

// Helper: compute relative @import path to variables.css
function getImportPath(filePath) {
  const fileDir = filePath.substring(0, filePath.lastIndexOf('/'));
  const stylesDir = join(ROOT, 'src/styles');
  const rel = relative(fileDir, stylesDir);
  return (rel || '.') + '/variables.css';
}

function replaceColorsInContent(content) {
  let replaced = 0;
  const hexRe = /#[0-9a-fA-F]{3,6}\b/g;

  const result = content.replace(hexRe, (match, offset) => {
    const normalized = normalizeHex(match);
    // Don't replace colors inside existing var() calls
    const before = content.substring(Math.max(0, offset - 30), offset);
    if (/var\([^)]*$/.test(before)) {
      return match;
    }
    if (COLOR_MAP.has(normalized)) {
      replaced++;
      return COLOR_MAP.get(normalized);
    }
    return match;
  });

  return { content: result, replaced };
}

for (const file of allFiles) {
  let content;
  try {
    content = readFileSync(file, 'utf-8');
  } catch { continue; }
  
  const relPath = relative(ROOT, file);
  const isVue = relPath.endsWith('.vue');
  const isGlobalStyle = relPath.startsWith('src/styles/');

  const { content: newContent, replaced } = replaceColorsInContent(content);

  // Add @import only for view-level CSS files (not src/styles/ — loaded by main.js)
  let importAdded = false;
  if (!isVue && !isGlobalStyle && replaced > 0 && !newContent.includes('variables.css')) {
    const finalNewContent = `@import '${getImportPath(file)}';\n\n` + newContent;
    if (!DRY_RUN) {
      writeFileSync(file, finalNewContent, 'utf-8');
    }
    importAdded = true;
    totalFiles++;
  } else if (replaced > 0) {
    if (!DRY_RUN) {
      writeFileSync(file, newContent, 'utf-8');
    }
    totalFiles++;
  }

  if (replaced > 0 || importAdded) {
    totalReplaced += replaced;
    if (VERBOSE || !DRY_RUN) {
      console.log(`  ${DRY_RUN ? '[DRY]' : '[OK]'}  ${relPath.padEnd(50)} → ${replaced} replaces${importAdded ? ' (+@import)' : ''}`);
    }
  }
}

console.log(`\nDone: ${totalFiles} files modified, ${totalReplaced} colors replaced.`);

