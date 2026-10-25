// Input: actual pre-push argument/branch policy and regression dispatch blocks.
// Output: bounded routing regression; command recorders do not run a push or business tests.
// Pos: scripts pre-push dispatch tests.
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
const source=readFileSync(new URL('./pre-push-gate.sh',import.meta.url),'utf8');
function slice(start,end) {
 const from=source.indexOf(start),to=source.indexOf(end,from);
 assert.ok(from>=0 && to>from,'actual gate source blocks must exist');
 return source.slice(from,to);
}
function dispatch(args=[],skip='0',changed='1',branch='agent/codex/task') {
 const policy=slice('# ── 参数解析','# ── 完全绕过');
 const regression=slice('# Bounded executable source regressions','# ── 12. 前端单元测试');
 const script=`set -euo pipefail
 git(){ printf '%s' "$TEST_BRANCH"; }
 pass(){ printf 'PASS %s\\n' "$*"; }
 fail(){ printf 'FAIL %s\\n' "$*"; }
 skip(){ printf 'SKIP %s\\n' "$*"; }
 node(){ printf 'NODE %s\\n' "$*"; }
 bash(){ printf 'BASH %s\\n' "$*"; }
 ${policy}
 ROOT_DIR="$PWD"
 printf 'FRONTEND_SKIP=%s\\n' "$SKIP_TESTS"
 ${regression}`;
 const run=spawnSync('bash',['-s','--',...args],{input:script,encoding:'utf8',timeout:10000,env:{...process.env,TEST_BRANCH:branch,SCOREPARSE_GATE_SKIP:skip,SP_PROD_CHANGED:changed}});
 assert.equal(run.status,0,run.stderr);return run.stdout;
}
test('agent no arguments keeps frontend shortcut and dispatches only the worktree topology regression',()=>{
 const out=dispatch();assert.match(out,/FRONTEND_SKIP=true/);assert.match(out,/NODE .*check-token-coverage.test.mjs/);assert.match(out,/BASH .*check-worktree-protection.test.sh/);assert.doesNotMatch(out,/check-git-properties/);
});
test('explicit skip-tests skips both regressions',()=>{const out=dispatch(['--skip-tests']);assert.doesNotMatch(out,/^NODE |^BASH /m);assert.match(out,/SKIP .*--skip-tests/);});
test('explicit source gate skip still executes governance regressions',()=>{const out=dispatch([],'1');assert.match(out,/^NODE /m);assert.match(out,/check-worktree-protection\.test\.sh/);assert.doesNotMatch(out,/check-scoreparse-root-cause\.sh/);});
test('no source changes still executes the topology regression only',()=>{const out=dispatch([],'0','0');assert.match(out,/^NODE /m);assert.match(out,/check-worktree-protection\.test\.sh/);assert.doesNotMatch(out,/check-git-properties/);assert.doesNotMatch(out,/check-scoreparse-root-cause\.sh/);});
test('non-agent branch dispatches both without changing frontend policy',()=>{const out=dispatch([],'0','1','feature/normal');assert.match(out,/FRONTEND_SKIP=false/);assert.match(out,/^NODE /m);assert.match(out,/^BASH /m);});
