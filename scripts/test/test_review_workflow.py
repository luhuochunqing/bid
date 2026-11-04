"""Input: temporary Git repositories/local analyzers. Output: behavioral assertions.
Pos: scripts/test; sync scripts/README.md when the workflow contract changes.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
CLI = ROOT / 'scripts/review-workflow.py'
sys.path.insert(0, str(ROOT / 'scripts/lib'))
import review_gate
from review_gate import snapshot

AGENT = '''import json, os, sys
from pathlib import Path
r = json.loads(Path(sys.argv[1]).read_text())
with Path(os.environ['REVIEW_TEST_TRACE']).open('a') as f: f.write(r['stage'] + '\\n')
mode = os.environ.get('REVIEW_TEST_MODE', '')
stage = r['stage']; code = Path('code.txt')
out = {'verdict':'PASS','summary':'Read actual file and checked content.','findings':[]}
if mode == 'timeout':
    import time; time.sleep(30)
if mode == 'commit' and stage == 'google-code-review':
    import subprocess; subprocess.check_call(['git','commit','--allow-empty','-qm','forbidden'])
if mode == 'missing': sys.exit(0)
if mode == 'invalid-json': Path(sys.argv[2]).write_text('{'); sys.exit(0)
if mode == 'fix-escalate' and stage == 'fix': out['verdict'] = 'ESCALATE'
if mode == 'crash': sys.exit(9)
if mode == 'malformed': out = {'verdict':'PASS'}
if mode == 'escalate': out['verdict'] = 'ESCALATE'
if mode == 'readonly' and stage == 'google-code-review': code.write_text('changed by reviewer')
if mode == 'outside' and stage == 'ai-slop-cleaner': Path('other.txt').write_text('changed')
if stage in ('google-code-review', 'xiyu-code-review'):
    bad = 'BUG' in code.read_text() and (mode != 'xiyu' or stage == 'xiyu-code-review')
    if bad:
        out = {'verdict':'NEEDS_FIXES','summary':'Actual file contains BUG.',
          'findings':[{'severity':'Major','file':'code.txt','line':1,
                       'message':'BUG present','disposition':'open','reason':''}]}
if mode == 'hidden-blocker' and stage == 'google-code-review':
    out['findings'] = [{'severity':'Major','file':'code.txt','line':1,
        'message':'must block even with PASS','disposition':'accepted','reason':'ignore'}]
if stage == 'fix' and mode != 'never': code.write_text(code.read_text().replace('BUG','FIXED'))
Path(sys.argv[2]).write_text(json.dumps(out))
'''

class GateValidationTest(unittest.TestCase):
    def test_report_validation_preserves_errors_and_blocking_policy(self):
        valid = {'verdict': 'PASS', 'summary': 'checked', 'findings': []}
        finding = {'severity': 'Minor', 'file': 'code.txt', 'line': 1,
                   'message': 'detail', 'disposition': 'accepted', 'reason': 'justified'}
        cases = [
            ({}, 'Agent 报告必须包含 verdict、summary、findings'),
            (dict(valid, verdict='UNKNOWN'), '无效的审查 verdict'),
            (dict(valid, summary=' '), 'Agent 报告缺少具体结论'),
            (dict(valid, findings={}), 'findings 必须是数组'),
            (dict(valid, findings=[{}]), 'finding 缺少位置、级别或处理记录'),
        ]
        for changes, message in [
            ({'severity': 'unknown'}, 'finding 级别无效'),
            ({'line': 0}, 'finding 行号无效'),
            ({'reason': None}, 'finding 文本字段无效'),
            ({'file': ' '}, 'finding 缺少文件或问题描述'),
            ({'disposition': 'unknown'}, 'finding 处理状态无效'),
            ({'reason': ' '}, '非阻断意见必须记录处理理由'),
        ]:
            cases.append((dict(valid, findings=[dict(finding, **changes)]), message))
        cases.append((dict(valid, verdict='ESCALATE'), 'Agent 要求人工裁决，停止自动修复'))
        for report, message in cases:
            with self.subTest(report=report):
                with self.assertRaises(review_gate.GateError) as error:
                    review_gate.validate_report(report)
                self.assertEqual(str(error.exception), message)
        self.assertTrue(review_gate.validate_report(valid))
        self.assertTrue(review_gate.validate_report(dict(valid, findings=[finding])))
        for changes in ({'severity': 'Major'}, {'severity': 'Critical'}, {'disposition': 'open'}):
            self.assertFalse(review_gate.validate_report(
                dict(valid, findings=[dict(finding, **changes, reason='')])))
        self.assertFalse(review_gate.validate_report(dict(valid, verdict='NEEDS_FIXES')))

    def test_receipt_validation_preserves_errors_and_order(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary).resolve()
            report = directory / 'report.json'
            report.write_text(json.dumps({'verdict': 'PASS', 'summary': 'checked', 'findings': []}))
            artifact = {'report.json': review_gate.digest(report.read_bytes())}
            event = {'round': 1, 'passed': True, 'fingerprint': 'current',
                     'artifacts': artifact, 'report': 'report.json'}
            state = {'status': 'MERGE_READY', 'version': 1, 'base_ref': 'origin/main',
                     'fingerprint': 'current',
                     'events': [dict(event, stage=stage) for stage in review_gate.STAGES]}
            cases = []
            def changed(message, mutate):
                value = copy.deepcopy(state)
                mutate(value)
                cases.append((value, message))
            changed('三技能流程尚未全部通过；运行 python3 scripts/review-workflow.py run --help',
                    lambda s: s.update(status='BLOCKED'))
            changed('交付基线必须是 origin/main', lambda s: s.update(base_ref='main'))
            changed('代码、分支或基线已变化，必须重新执行三技能流程', lambda s: s.update(fingerprint='old'))
            changed('缺少完整且有序的三技能/验证记录', lambda s: s.update(events=s['events'][::-1]))
            changed('审查轮次无效', lambda s: s['events'][0].update(round=2))
            changed('缺少进程执行工件', lambda s: s['events'][0].update(artifacts={}))
            changed('工件路径越界', lambda s: s['events'][0].update(artifacts={'../escape': 'hash'}))
            changed('工件已丢失或被修改: report.json',
                    lambda s: s['events'][0].update(artifacts={'report.json': 'wrong'}))
            changed('最后一轮未全部通过或不对应最终代码', lambda s: s['events'][0].update(passed=False))
            changed('报告未绑定执行工件', lambda s: s['events'][0].update(report='other.json'))
            with patch.object(review_gate, 'state_dir', return_value=directory), \
                 patch.object(review_gate, 'snapshot', return_value={'fingerprint': 'current'}), \
                 patch.object(review_gate, 'git', return_value=b''):
                for value, message in cases:
                    with self.subTest(message=message), patch.object(review_gate, 'read_json', return_value=value):
                        with self.assertRaises(review_gate.GateError) as error:
                            review_gate.validate_receipt(directory)
                        self.assertEqual(str(error.exception), message)
                (directory / 'state.json').write_text(json.dumps(state))
                self.assertEqual(review_gate.validate_receipt(directory), state)
                with patch.object(review_gate, 'git', return_value=b' M code.txt'):
                    with self.assertRaisesRegex(review_gate.GateError, '交付前必须提交所有已审查改动，工作区须干净'):
                        review_gate.validate_receipt(directory)
                    self.assertEqual(review_gate.validate_receipt(directory, clean=False), state)
                report.write_text(json.dumps({'verdict': 'NEEDS_FIXES', 'summary': 'blocked', 'findings': []}))
                for item in state['events']:
                    item['artifacts']['report.json'] = review_gate.digest(report.read_bytes())
                (directory / 'state.json').write_text(json.dumps(state))
                with self.assertRaisesRegex(review_gate.GateError, '审查报告仍存在未处理问题'):
                    review_gate.validate_receipt(directory)


class VerificationEvidenceTest(unittest.TestCase):
    def setUp(self):
        spec = importlib.util.spec_from_file_location('review_workflow', CLI)
        self.workflow = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.workflow)
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.folder = Path(temporary.name)

    def test_second_command_exception_preserves_completed_result(self):
        first = [sys.executable, '-c', 'print("completed")']
        failures = ([sys.executable, '-c', 'import time; time.sleep(30)'],
                    [str(self.folder / 'missing-executable')])
        for command in failures:
            with self.subTest(command=command):
                with self.assertRaises((self.workflow.GateError, OSError)):
                    self.workflow.execute_verification(
                        self.folder, self.folder, [first, command], 0.2)
                results = json.loads((self.folder / 'verify-report.json').read_text())
                self.assertEqual(results[0]['argv'], first)
                self.assertEqual(results[0]['exit_code'], 0)
                self.assertEqual(results[1]['argv'], command)
                self.assertIsNone(results[1]['exit_code'])
                self.assertTrue(results[1]['error'])
                self.assertTrue(Path(results[0]['log']).exists())
                self.assertTrue(Path(results[1]['log']).exists())

    def test_each_completion_is_persisted_before_next_start(self):
        commands = [[sys.executable, '-c', 'print(1)'],
                    [sys.executable, '-c', 'print(2)']]
        real_process = self.workflow.run_process
        def observe(command, root, logfile, timeout):
            results = json.loads((self.folder / 'verify-report.json').read_text())
            self.assertEqual(results[-1]['argv'], command)
            self.assertIsNone(results[-1]['exit_code'])
            if command == commands[1]:
                self.assertEqual(results[0]['exit_code'], 0)
            return real_process(command, root, logfile, timeout)
        with patch.object(self.workflow, 'run_process', side_effect=observe):
            code, _, _ = self.workflow.execute_verification(
                self.folder, self.folder, commands, 1)
        self.assertEqual(code, 0)

    def test_failure_event_binds_partial_report_and_logs(self):
        folder = self.folder / '1'
        folder.mkdir()
        commands = [[sys.executable, '-c', 'print("done")'],
                    [str(self.folder / 'missing-executable')]]
        state = {'status': 'RUNNING', 'events': []}
        active = {'stage': 'verify', 'number': 1, 'before': {'fingerprint': 'test'},
                  'folder': folder, 'event_count': 0, 'code': None}
        try:
            self.workflow.execute_verification(self.folder, folder, commands, 1)
        except OSError as exc:
            self.workflow.record_failure(state, self.folder, active, exc)
        else:
            self.fail('missing executable must fail closed')
        event = state['events'][0]
        self.assertEqual(state['status'], 'BLOCKED')
        self.assertFalse(event['passed'])
        self.assertIsNone(event['exit_code'])
        self.assertEqual(len(event['artifacts']), 3)
        for name, expected in event['artifacts'].items():
            self.assertEqual(hashlib.sha256((self.folder / name).read_bytes()).hexdigest(), expected)
        self.assertEqual(json.loads((folder / 'verify-report.json').read_text())[0]['exit_code'], 0)

    def test_nonzero_stops_and_preserves_exit_code(self):
        command = [sys.executable, '-c', 'raise SystemExit(7)']
        code, report_path, artifacts = self.workflow.execute_verification(
            self.folder, self.folder, [command, ['must-not-start']], 1)
        self.assertEqual(code, 7)
        self.assertEqual(json.loads(report_path.read_text()), [
            {'argv': command, 'exit_code': 7, 'log': str(self.folder / 'verify-0.log')}])
        self.assertIn(report_path, artifacts)


class ReviewWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.top = Path(self.temp.name); self.repo = self.top / 'repo'; self.repo.mkdir()
        self.trace = self.top / 'trace'; self.agent = self.top / 'agent.py'; self.agent.write_text(AGENT)
        self.skills = self.top / 'skills'
        for skill in ('ai-slop-cleaner','google-code-review','xiyu-code-review'):
            folder = self.skills / skill; folder.mkdir(parents=True)
            (folder / 'SKILL.md').write_text('# ' + skill + '\nCheck the code.\n')
        self.env = dict(os.environ, REVIEW_TEST_TRACE=str(self.trace))
        self.git('init','-q','-b','main'); self.git('config','user.name','Review Test')
        self.git('config','user.email','review@example.invalid')
        (self.repo/'code.txt').write_text('baseline\n'); (self.repo/'other.txt').write_text('untouched\n')
        self.commit(); self.git('update-ref','refs/remotes/origin/main','HEAD')
        self.git('checkout','-qb','agent/test/gate'); (self.repo/'code.txt').write_text('feature\n'); self.commit()

    def git(self,*args):
        return subprocess.check_output(['git','-C',str(self.repo),*args],text=True).strip()
    def commit(self):
        self.git('add','.'); self.git('commit','-qm','test change')
    def cli(self,*args,mode='',input=None):
        return subprocess.run([sys.executable,str(CLI),'--project-dir',str(self.repo),*args],input=input,
            text=True,capture_output=True,env=dict(self.env,REVIEW_TEST_MODE=mode))
    def run_flow(self,mode='',verify=None):
        cmd = verify or [sys.executable,'-c',"from pathlib import Path; assert Path('code.txt').exists()"]
        return self.cli('run','--agent-command',json.dumps([sys.executable,str(self.agent)]),
            '--skills-dir',str(self.skills),'--verify',json.dumps(cmd),'--timeout','10',mode=mode)
    def stages(self): return self.trace.read_text().splitlines()
    def assert_ok(self,r): self.assertEqual(r.returncode,0,r.stdout+r.stderr)

    def assert_failed_event(self, stage, exit_code):
        directory = self.repo / self.git('rev-parse', '--git-path', 'review-gate')
        state = json.loads((directory / 'state.json').read_text())
        self.assertEqual(state['status'], 'BLOCKED')
        events = [e for e in state['events'] if e['stage'] == stage]
        self.assertEqual(len(events), 1)
        event = events[0]
        self.assertEqual(event['round'], 1)
        self.assertFalse(event['passed'])
        self.assertEqual(event['exit_code'], exit_code)
        self.assertTrue(event['error'])
        self.assertTrue(event['artifacts'])
        for name, expected in event['artifacts'].items():
            self.assertEqual(hashlib.sha256((directory / name).read_bytes()).hexdigest(), expected)
        return event

    def test_failed_stage_retains_audit_event(self):
        for mode in ('missing', 'invalid-json', 'malformed', 'crash', 'outside', 'readonly', 'fix-escalate'):
            with self.subTest(mode=mode):
                # Each scenario needs an untouched repository and event history.
                case = ReviewWorkflowTest()
                case.setUp()
                try:
                    if mode == 'fix-escalate':
                        (case.repo / 'code.txt').write_text('BUG\n')
                    result = case.run_flow(mode=mode)
                    self.assertNotEqual(result.returncode, 0)
                    stage = ('fix' if mode == 'fix-escalate' else
                             'google-code-review' if mode == 'readonly' else 'ai-slop-cleaner')
                    case.assert_failed_event(stage, 9 if mode == 'crash' else 0)
                finally:
                    case.doCleanups()

    def test_restoring_rename_source_invalidates_fingerprint(self):
        self.git('config', 'diff.renames', 'true')
        self.git('mv', 'other.txt', 'renamed.txt')
        self.commit()
        reviewed = snapshot(self.repo)
        (self.repo / 'other.txt').write_text('untouched\n')
        self.commit()
        restored = snapshot(self.repo)
        self.assertNotEqual(reviewed['fingerprint'], restored['fingerprint'])
        self.assertIn('other.txt', reviewed['files'])
        self.assertIsNone(reviewed['files']['other.txt'])
        self.assertNotIn('other.txt', restored['files'])

    def test_rename_fingerprint_survives_staging_and_commit(self):
        self.git('config', 'diff.renames', 'true')
        (self.repo / 'other.txt').rename(self.repo / 'renamed.txt')
        unstaged = snapshot(self.repo)
        self.git('add', '-A')
        staged = snapshot(self.repo)
        self.commit()
        committed = snapshot(self.repo)
        self.assertEqual(unstaged, staged)
        self.assertEqual(unstaged, committed)
        self.assertIn('other.txt', committed['files'])
        self.assertIsNone(committed['files']['other.txt'])
        self.assertIn('renamed.txt', committed['files'])

    def test_missing_receipt_blocks(self):
        r=self.cli('check'); self.assertNotEqual(r.returncode,0); self.assertIn('review-gate',r.stderr)
    def test_success_requires_exact_sequence(self):
        self.assert_ok(self.run_flow())
        self.assertEqual(self.stages(),['ai-slop-cleaner','google-code-review','xiyu-code-review'])
        self.assert_ok(self.cli('check'))
    def test_google_finding_fixed_then_reviews_rerun(self):
        (self.repo/'code.txt').write_text('BUG\n'); self.assert_ok(self.run_flow())
        self.assertEqual(self.stages(),['ai-slop-cleaner','google-code-review','fix','ai-slop-cleaner','google-code-review','xiyu-code-review'])
        self.assertEqual((self.repo/'code.txt').read_text(),'FIXED\n')
        directory=self.repo/self.git('rev-parse','--git-path','review-gate')
        state=json.loads((directory/'state.json').read_text())
        fixes=[event for event in state['events'] if event['stage']=='fix']
        self.assertEqual([event['passed'] for event in fixes],[True], 'fix outcome must use its own report, not the previous failed review')
        self.assertNotEqual(self.cli('check').returncode,0); self.commit(); self.assert_ok(self.cli('check'))
    def test_xiyu_finding_invalidates_google(self):
        (self.repo/'code.txt').write_text('BUG\n'); self.assert_ok(self.run_flow(mode='xiyu'))
        self.assertEqual(self.stages().count('google-code-review'),2)
        self.assertEqual(self.stages().count('xiyu-code-review'),2)
    def test_three_rounds_stop(self):
        (self.repo/'code.txt').write_text('BUG\n'); self.assertNotEqual(self.run_flow(mode='never').returncode,0)
        self.assertEqual(self.stages().count('google-code-review'),3); self.assertEqual(self.stages().count('fix'),2)
        self.assertNotEqual(self.cli('check').returncode,0)
    def test_new_content_invalidates(self):
        self.assert_ok(self.run_flow()); (self.repo/'new.txt').write_text('unreviewed'); self.commit()
        self.assertNotEqual(self.cli('check').returncode,0)
    def test_baseline_changes_invalidate(self):
        self.assert_ok(self.run_flow()); self.git('update-ref','refs/remotes/origin/main','HEAD')
        self.assertNotEqual(self.cli('check').returncode,0)
    def test_readonly_review_enforced(self):
        r=self.run_flow(mode='readonly'); self.assertNotEqual(r.returncode,0); self.assertIn('只读',r.stderr)
        self.assertNotIn('xiyu-code-review',self.stages())
    def test_writer_scope_enforced(self):
        r=self.run_flow(mode='outside'); self.assertNotEqual(r.returncode,0); self.assertIn('范围',r.stderr)
    def test_invalid_output_crash_escalation(self):
        for mode in ('malformed','crash','escalate'):
            with self.subTest(mode=mode):
                self.assertNotEqual(self.run_flow(mode=mode).returncode,0); self.assertNotEqual(self.cli('check').returncode,0)
    def test_new_failed_run_invalidates_old_success(self):
        self.assert_ok(self.run_flow()); self.assertNotEqual(self.run_flow(mode='crash').returncode,0)
        self.assertNotEqual(self.cli('check').returncode,0)
    def test_missing_skill_blocks(self):
        (self.skills/'xiyu-code-review/SKILL.md').unlink(); self.assertNotEqual(self.run_flow().returncode,0)
    def test_pass_cannot_hide_blocker(self):
        self.assertNotEqual(self.run_flow(mode='hidden-blocker').returncode,0)
    def test_test_failure_gets_fixed(self):
        (self.repo/'code.txt').write_text('BUG\n')
        cmd=[sys.executable,'-c',"from pathlib import Path; assert 'BUG' not in Path('code.txt').read_text()"]
        self.assert_ok(self.run_flow(verify=cmd))
        self.assertEqual(self.stages(),['ai-slop-cleaner','fix','ai-slop-cleaner','google-code-review','xiyu-code-review'])
    def test_push_actual_ref_and_deletion(self):
        self.assert_ok(self.run_flow()); sha=self.git('rev-parse','HEAD'); zero='0'*40
        self.assert_ok(self.cli('check','--push',input=f'refs/heads/agent/test/gate {sha} refs/heads/agent/test/gate {zero}\n'))
        self.assertNotEqual(self.cli('check','--push',input=f'refs/heads/other {sha} refs/heads/other {zero}\n').returncode,0)
        self.assert_ok(self.cli('check','--push',input=f'(delete) {zero} refs/heads/agent/old {sha}\n'))

    def install_gate(self):
        (self.repo/'scripts/lib').mkdir(parents=True)
        for name in ('review_gate.py','review_agent.py'):
            shutil.copy2(ROOT/'scripts/lib'/name,self.repo/'scripts/lib'/name)
        shutil.copy2(CLI,self.repo/'scripts/review-workflow.py')
        self.commit()

    def test_full_hook_deletion_and_mixed_push_without_receipt(self):
        self.install_gate()
        (self.repo/'.githooks').mkdir()
        hook = self.repo/'.githooks/pre-push'
        shutil.copy2(ROOT/'.githooks/pre-push', hook)
        shutil.copy2(ROOT/'scripts/pre-push-gate.sh', self.repo/'scripts/pre-push-gate.sh')
        self.commit()
        # No receipt exists; origin/main has also advanced since the task began.
        self.git('update-ref', 'refs/remotes/origin/main', 'HEAD')
        sha = self.git('rev-parse', 'HEAD'); zero = '0'*40
        deletion = f'(delete) {zero} refs/heads/agent/old {sha}\n'
        update = f'refs/heads/agent/test/gate {sha} refs/heads/agent/test/gate {zero}\n'
        environment = dict(self.env)
        environment.pop('PRE_PUSH_GATE', None)  # Exercise the default quality-gate path.
        for rows, allowed in ((deletion, True), (deletion*2, True),
                              (deletion+update, False), (update+deletion, False),
                              (update, False), ('', False)):
            with self.subTest(rows=rows):
                result = subprocess.run(['bash', str(hook), 'origin'], cwd=self.repo,
                    env=environment, input=rows, text=True, capture_output=True, timeout=15)
                if allowed:
                    self.assert_ok(result)
                else:
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn('[review-gate] BLOCKED', result.stderr)

    def test_pr_remote_head_must_match(self):
        bare=self.top/'remote.git'
        subprocess.check_call(['git','init','-q','--bare',str(bare)])
        self.git('remote','add','origin',str(bare))
        self.assert_ok(self.run_flow())
        self.assertNotEqual(self.cli('check','--pr').returncode,0)
        self.git('push','-q','origin','HEAD:refs/heads/agent/test/gate')
        self.assert_ok(self.cli('check','--pr'))
        self.assertNotEqual(self.cli('check','--pr','--branch','agent/other').returncode,0)

    def test_report_log_tampering_blocks(self):
        self.assert_ok(self.run_flow())
        directory=self.repo/self.git('rev-parse','--git-path','review-gate')
        log=next(directory.glob('*/1/google-code-review.log'))
        log.write_text('replaced evidence')
        self.assertNotEqual(self.cli('check').returncode,0)

    def test_delivery_entrypoints_block_without_receipt_even_with_legacy_bypass(self):
        self.install_gate()
        self.git('remote','add','origin','git@gitee.com:invalid/offline-test.git')
        environment=dict(self.env,GITEE_TOKEN='offline-contract-token',PRE_PUSH_GATE='0',
                         UNIFIED_DEV_FLOW_SKIP_REVIEW='1')
        scripts=[('scripts/pr-create.sh',['test title']),('scripts/gitee-pr-helper.sh',['create']),
                 ('scripts/pre-push-gate.sh',[])]
        for name,args in scripts:
            with self.subTest(entry=name):
                target=self.repo/name; shutil.copy2(ROOT/name,target)
                self.commit()
                r=subprocess.run(['bash',str(target),*args],cwd=self.repo,env=environment,
                    input='test body',text=True,capture_output=True,timeout=15)
                self.assertNotEqual(r.returncode,0); self.assertIn('[review-gate] BLOCKED',r.stderr)
        (self.repo/'.githooks').mkdir()
        hook=self.repo/'.githooks/pre-push'; shutil.copy2(ROOT/'.githooks/pre-push',hook)
        self.commit(); sha=self.git('rev-parse','HEAD'); zero='0'*40
        r=subprocess.run(['bash',str(hook),'origin'],cwd=self.repo,env=environment,
            input=f'refs/heads/agent/test/gate {sha} refs/heads/agent/test/gate {zero}\n',
            text=True,capture_output=True,timeout=15)
        self.assertNotEqual(r.returncode,0); self.assertIn('[review-gate] BLOCKED',r.stderr)
        (self.repo/'scripts/review-workflow.py').unlink()
        r=subprocess.run(['bash',str(self.repo/'scripts/pr-create.sh'),'test title'],cwd=self.repo,
            env=environment,input='test body',text=True,capture_output=True,timeout=15)
        self.assertNotEqual(r.returncode,0); self.assertIn('review-workflow.py',r.stderr)

    def test_review_cannot_commit_same_content(self):
        r=self.run_flow(mode='commit')
        self.assertNotEqual(r.returncode,0); self.assertIn('HEAD',r.stderr)

    def test_verification_cannot_modify_sources(self):
        cmd=[sys.executable,'-c',"from pathlib import Path; Path('code.txt').write_text('mutated')"]
        r=self.run_flow(verify=cmd)
        self.assertNotEqual(r.returncode,0); self.assertIn('只读',r.stderr)

    def test_task_cannot_push_to_main_to_skip_gate(self):
        sha=self.git('rev-parse','HEAD'); zero='0'*40
        r=self.cli('check','--push',input=f'refs/heads/agent/test/gate {sha} refs/heads/main {zero}\n')
        self.assertNotEqual(r.returncode,0)

    def test_timeout_stops_without_ready_receipt(self):
        r=self.cli('run','--agent-command',json.dumps([sys.executable,str(self.agent)]),
            '--skills-dir',str(self.skills),'--verify','["git","diff","--check"]','--timeout','1',mode='timeout')
        self.assertNotEqual(r.returncode,0); self.assertIn('超时',r.stderr)
        self.assertNotEqual(self.cli('check').returncode,0)
        self.assert_failed_event('ai-slop-cleaner', None)

    def test_verification_timeout_retains_audit_event(self):
        result = self.cli('run', '--agent-command', json.dumps([sys.executable, str(self.agent)]),
            '--skills-dir', str(self.skills), '--verify', '["git","diff","--check"]',
            '--verify', json.dumps([sys.executable, '-c', 'import time; time.sleep(30)']),
            '--timeout', '1')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('超时', result.stderr)
        event = self.assert_failed_event('verify', None)
        self.assertEqual(len(event['artifacts']), 3)
        directory = self.repo / self.git('rev-parse', '--git-path', 'review-gate')
        report_path = next(directory / name for name in event['artifacts']
                           if name.endswith('verify-report.json'))
        results = json.loads(report_path.read_text())
        self.assertEqual(results[0]['argv'], ['git', 'diff', '--check'])
        self.assertEqual(results[0]['exit_code'], 0)
        self.assertEqual(results[1]['argv'],
                         [sys.executable, '-c', 'import time; time.sleep(30)'])
        self.assertIsNone(results[1]['exit_code'])
        self.assertTrue(results[1]['error'])

    def test_actual_git_push_accepts_head_expression(self):
        self.install_gate()
        (self.repo/'.githooks').mkdir()
        shutil.copy2(ROOT/'.githooks/pre-push',self.repo/'.githooks/pre-push')
        self.commit()
        self.git('config','core.hooksPath','.githooks')
        bare=self.top/'remote.git'
        subprocess.check_call(['git','init','-q','--bare',str(bare)])
        self.git('remote','add','origin',str(bare))
        self.assert_ok(self.run_flow())
        r=subprocess.run(['git','push','origin','HEAD:refs/heads/agent/test/gate'],cwd=self.repo,
            env=dict(self.env,PRE_PUSH_GATE='0'),text=True,capture_output=True,timeout=15)
        self.assert_ok(r)

    def test_tag_publication_keeps_existing_behavior(self):
        self.install_gate()
        (self.repo/'.githooks').mkdir()
        shutil.copy2(ROOT/'.githooks/pre-push',self.repo/'.githooks/pre-push')
        self.commit()
        self.git('config','core.hooksPath','.githooks')
        bare=self.top/'remote.git'
        subprocess.check_call(['git','init','-q','--bare',str(bare)])
        self.git('remote','add','origin',str(bare))
        self.git('tag','-a','contract-v1','-m','release fixture')
        r=subprocess.run(['git','push','origin','refs/tags/contract-v1'],cwd=self.repo,
            env=dict(self.env,PRE_PUSH_GATE='0'),text=True,capture_output=True,timeout=15)
        self.assert_ok(r)

    def test_existing_main_mirror_source_is_allowed(self):
        sha=self.git('rev-parse','origin/main'); zero='0'*40
        self.assert_ok(self.cli('check','--push',input=f'refs/remotes/origin/main {sha} refs/heads/main {zero}\n'))
        self.assert_ok(self.cli('check','--push',input=f'HEAD {sha} refs/heads/main {zero}\n'))
        task_sha=self.git('rev-parse','HEAD')
        self.assertNotEqual(self.cli('check','--push',input=f'HEAD {task_sha} refs/heads/main {zero}\n').returncode,0)

    def test_bootstrap_sync_allows_only_origin_main(self):
        self.install_gate()
        self.git('update-ref','refs/remotes/origin/main','HEAD')
        self.git('checkout','-qb','agent/test-init')
        sha=self.git('rev-parse','HEAD'); zero='0'*40
        self.assert_ok(self.cli('check','--push',input=f'HEAD {sha} refs/heads/agent/test-init {zero}\n'))
        self.assert_ok(self.cli('check'))
        self.assertNotEqual(self.cli('check','--pr').returncode,0)
        (self.repo/'code.txt').write_text('unreviewed seed change')
        self.commit(); changed=self.git('rev-parse','HEAD')
        for source in ('HEAD','refs/heads/agent/test-init'):
            self.assertNotEqual(self.cli('check','--push',input=f'{source} {changed} refs/heads/agent/test-init {sha}\n').returncode,0)
        self.assertNotEqual(self.cli('check').returncode,0)

if __name__ == '__main__': unittest.main()
