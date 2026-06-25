#!/usr/bin/env node
// Input: staged Vue files from git index
// Output: blocks commit when a UserPicker call site expected to use mode="search"
//         is modified to use a different mode (e.g. "candidates").
// Pos: scripts/ — UserPicker 模式回归检测 (2026-06-25)
// 维护声明: 若 SEARCH_MODE_FILES 名单或 UserPicker 默认 mode 变化，请同步更新此脚本与 docs/implementation-notes/UserPicker-remote-search-fix.md。
//
// Background (2026-06-25):
//   PR #1088 (sync GitHub main) reverted mode="search" back to mode="candidates"
//   at 3 Bidding transfer/assign call sites, silently breaking search-by-name/
//   employee-number/pinyin for users. The change passed all existing gates
//   because component-level tests verify both modes are valid, and no assertion
//   existed at the call site level.
//
// This check prevents that class of regression by asserting that staged files
// in the SEARCH_MODE_FILES list must use mode="search" on their UserPicker instances.
// If a file legitimately needs a different mode, add it to the CANDIDATES_ALLOWLIST.

import fs from 'node:fs';
import { spawnSync } from 'node:child_process';

// Files whose UserPicker usage is REQUIRED to use mode="search".
// When a file appears here, ANY UserPicker <el-select> in it must have
// mode="search" (or no mode attribute, since the default is "search").
const SEARCH_MODE_FILES = [
  'src/views/Bidding/List.vue',
  'src/views/Bidding/detail/DetailPage.vue',
  'src/views/Bidding/list/components/AssignDialog.vue',
  'src/views/Bidding/list/components/ReminderSettingsDialog.vue',
  'src/views/Bidding/list/components/BiddingSearchCard.vue',
  'src/views/Project/stages/components/TaskKanban.vue',
  'src/views/Project/stages/components/TaskDecomposeDialog.vue',
  'src/views/Project/stages/DraftingStage.vue',
  'src/views/Project/stages/InitiationStage.vue',
  'src/components/project/TaskForm.vue',
  'src/components/CollaborationCenter.vue',
  'src/views/Bidding/TenderSearchCard.vue',
  'src/views/Project/ProjectSearchCard.vue',
  'src/views/Bidding/NewTender/components/BasicInfoStep.vue',
  'src/views/Project/stages/components/CAFormDialog.vue',
  'src/components/system/ProjectGroupSettingsPanel.vue',
  'src/components/project/ProjectCollaboratorsDialog.vue',
];

// Files where UserPicker mode="candidates" is the expected (white-listed) usage.
// Items added here must include a comment explaining why search mode is unsuitable.
const CANDIDATES_ALLOWLIST_COMMENTS = {
  // None for now — all call sites should default to search mode.
  // If a future PR adds a valid candidates-only scenario, register it here:
  // 'src/views/SomeFile.vue': 'pre-loads from a small static set, remote API not needed',
};

// ---------------------------------------------------------------------------

const ROOT = spawnSync('git', ['rev-parse', '--show-toplevel'], {
  encoding: 'utf-8',
  stdio: ['ignore', 'pipe', 'pipe'],
}).stdout.trim();

function stagedFiles() {
  const out = spawnSync('git', ['diff', '--cached', '--name-only', '--diff-filter=ACM'], {
    encoding: 'utf-8',
    cwd: ROOT,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  return out.stdout.trim().split('\n').filter(Boolean);
}

// Extract the UserPicker mode attribute value from file content.
// Returns null when no <UserPicker is found, 'search' when default applies,
// or the explicit value string (e.g. 'candidates') when set.
function getUserPickerMode(content) {
  const userPickerTagRe = /<UserPicker\b([^>]*)\/?>/g;
  const modeAttrRe = /mode\s*=\s*["']([^"']+)["']/;
  const results = [];
  let match;
  while ((match = userPickerTagRe.exec(content)) !== null) {
    const tagContent = match[1];
    const modeMatch = tagContent.match(modeAttrRe);
    results.push(modeMatch ? modeMatch[1] : 'search'); // default is search
  }
  return results;
}

// ---------------------------------------------------------------------------

function main() {
  const files = stagedFiles();
  if (files.length === 0) return;

  let exitCode = 0;

  for (const file of files) {
    if (!SEARCH_MODE_FILES.includes(file)) continue;

    const fullPath = `${ROOT}/${file}`;
    let content;
    try {
      // Read from git index (staged version), not working tree
      const buf = spawnSync('git', ['show', ':' + file], {
        encoding: 'utf-8',
        cwd: ROOT,
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      content = buf.stdout;
    } catch {
      // File might be newly added (no index entry yet)
      try {
        content = fs.readFileSync(fullPath, 'utf-8');
      } catch {
        continue;
      }
    }

    const modes = getUserPickerMode(content);
    if (modes.length === 0) continue; // no UserPicker in this file

    for (const mode of modes) {
      if (mode !== 'search') {
        const allowlistComment = CANDIDATES_ALLOWLIST_COMMENTS[file];
        if (allowlistComment) {
          // White-listed — allowed with comment
          console.warn(`[check-userpicker-mode] ${file}: UserPicker mode="${mode}" (allowed: ${allowlistComment})`);
        } else {
          console.error(
            `\n[check-userpicker-mode] BLOCKED: ${file}` +
            `\n  This file is expected to use UserPicker with mode="search" (remote search)` +
            `\n  but was staged with mode="${mode}". Either:` +
            `\n  - Restore mode="search" (recommended — users need to search by name/employee-number/pinyin),` +
            `\n  - Or register the file in CANDIDATES_ALLOWLIST_COMMENTS in scripts/check-userpicker-mode.mjs` +
            `\n    with an explicit comment explaining why search mode is unsuitable.`
          );
          exitCode = 1;
        }
      }
    }
  }

  exitCode = checkFilterMethodInUserPicker() || exitCode;

  process.exit(exitCode);
}

// ---------------------------------------------------------------------------
// Guard (2026-06-25): block filter-method on UserPicker — it breaks remote
// search in Element Plus because filterMethod takes priority over remoteMethod
// in handleQueryChange:
//   if (filterable && isFunction(filterMethod)) → no-op, never reaches remote-method
function checkFilterMethodInUserPicker() {
  const pickerTagRe = /<UserPicker\b([^>]*)\/?>/g;
  const filterMethodRe = /filter-method\s*=/;

  const files = stagedFiles();
  for (const file of files) {
    if (!file.endsWith('.vue')) continue;
    const fullPath = `${ROOT}/${file}`;
    let content;
    try {
      const buf = spawnSync('git', ['show', ':' + file], {
        encoding: 'utf-8', cwd: ROOT, stdio: ['ignore', 'pipe', 'pipe'],
      });
      content = buf.stdout;
    } catch {
      try { content = fs.readFileSync(fullPath, 'utf-8'); } catch { continue; }
    }
    let match;
    while ((match = pickerTagRe.exec(content)) !== null) {
      if (filterMethodRe.test(match[1])) {
        console.error(
          `\n[check-userpicker-mode] BLOCKED: ${file}` +
          `\n  UserPicker must NOT have a filter-method attribute — it suppresses remote search.` +
          `\n  Remove ':filter-method' from the <UserPicker> tag.`
        );
        return 1;
      }
    }
  }
  return 0;
}

main();