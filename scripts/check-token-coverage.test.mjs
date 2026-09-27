// Input: portable temporary source tree and actual checker CLI.
// Output: real scan detects nested colors, excludes dependencies, fails closed on missing sources.
// Pos: scripts token governance regression.
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
const cli=resolve('scripts/check-token-coverage.mjs');
test('full scan handles spaces and glob-like names and excludes nested node_modules',()=>{
 const dir=mkdtempSync(join(tmpdir(),'token scan '));
 try {
  mkdirSync(join(dir,'src/nested [path]/node_modules'),{recursive:true});
  writeFileSync(join(dir,'src/nested [path]/actual file.vue'),'<style>.x { color: #abc; background: var(--surface); }</style>');
  writeFileSync(join(dir,'src/nested [path]/node_modules/ignored.css'),'color: #123;');
  const r=spawnSync(process.execPath,[cli,'--fail-on-hex','--max-hex-total=0'],{cwd:dir,encoding:'utf8'});
  assert.equal(r.status,1,r.stdout+r.stderr);
  assert.match(r.stdout,/TOTAL\s+1\s+1/);
 } finally {rmSync(dir,{recursive:true,force:true});}
});
test('missing source directory is a failed scan',()=>{
 const dir=mkdtempSync(join(tmpdir(),'token missing '));
 try {assert.notEqual(spawnSync(process.execPath,[cli],{cwd:dir}).status,0);} finally {rmSync(dir,{recursive:true,force:true});}
});
