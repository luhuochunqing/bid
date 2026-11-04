#!/usr/bin/env python3
"""Input: verification argv/agent adapter. Output: three-skill receipt or blocked delivery.
Pos: scripts/; keep scripts/README.md and AGENTS.md in sync with workflow changes.
"""
import argparse
import fcntl
from pathlib import Path
import sys
import uuid

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent / 'lib'))
from review_agent import command_json, invoke, load_skills, run_process
from review_gate import (STAGES, GateError, digest, git, read_json, snapshot, state_dir,
                         validate_receipt, validate_report, write_json)


def record(state, directory, stage, number, current, passed, artifacts, **extra):
    event = {'stage': stage, 'round': number, 'fingerprint': current['fingerprint'],
             'passed': passed, 'artifacts': {str(p.relative_to(directory)): digest(p.read_bytes())
                                           for p in artifacts if p.is_file()}, **extra}
    state['events'].append(event)
    write_json(directory / 'state.json', state)


def enforce_scope(root, original, before, readonly=False):
    after = snapshot(root, original['base_ref'])
    if (after['branch'], after['base_tip'], after['base']) != (original['branch'], original['base_tip'], original['base']):
        raise GateError('Agent 改变了分支或基线，停止流程')
    if set(after['files']) - set(original['files']):
        raise GateError('Agent 修改超出原任务文件范围，停止并人工处理，不自动回退')
    if git(root, 'rev-parse', 'HEAD').decode().strip() != before['head']:
        raise GateError('Agent 不得提交或改写 HEAD')
    if readonly and after['fingerprint'] != before['fingerprint']:
        raise GateError('只读阶段修改了代码，所有审查失效')
    return after


def execute_verification(root, folder, commands, timeout):
    report_path = folder / 'verify-report.json'
    results = []
    artifacts = [report_path]
    for i, command in enumerate(commands):
        log = folder / f'verify-{i}.log'
        result = {'argv': command, 'exit_code': None, 'log': str(log)}
        results.append(result)
        # Persist the current command identity before attempting process startup.
        write_json(report_path, results)
        try:
            code = run_process(command, root, log, timeout)
        except BaseException as exc:
            result['error'] = str(exc)
            write_json(report_path, results)
            raise
        result['exit_code'] = code
        write_json(report_path, results)
        artifacts.append(log)
        if code:
            break
    return code, report_path, artifacts


def record_failure(state, directory, active, exc):
    state['status'] = 'BLOCKED'
    if active and len(state['events']) == active['event_count']:
        # A process can fail before returning its artifact list (e.g. timeout).
        artifacts = sorted(active['folder'].glob(f"{active['stage']}*"))
        record(state, directory, active['stage'], active['number'], active['before'],
               False, artifacts, exit_code=active['code'], error=str(exc))
    write_json(directory / 'state.json', state)


def execute_stage(args, root, directory, state, original, skills, adapter,
                  commands, folder, number, stage, active, feedback=None):
    before = snapshot(root, args.base)
    before['head'] = git(root, 'rev-parse', 'HEAD').decode().strip()
    active.update(stage=stage, number=number, before=before, folder=folder,
                  event_count=len(state['events']), code=None)
    if stage == 'verify':
        code, report_path, artifacts = execute_verification(root, folder, commands, args.timeout)
        active['code'] = code
        report = read_json(report_path)
        passed = all(result['exit_code'] == 0 for result in report)
    else:
        code, report_path, artifacts = invoke(root, folder, stage, number,
            list(original['files']), original['base'], skills, adapter, args.timeout, feedback)
        active['code'] = code
        if code:
            raise GateError(f'Agent 进程失败（exit {code}），停止；日志: {folder}')
        report = read_json(report_path)
        passed = validate_report(report)
    current = enforce_scope(root, original, before,
                            readonly=stage not in ('ai-slop-cleaner', 'fix'))
    record(state, directory, stage, number, current, passed, artifacts,
           report=str(report_path.relative_to(directory)), exit_code=code)
    return current, passed, report


def execute_rounds(args, root, directory, state, original, skills, adapter, commands, work, active):
    for number in range(1, 4):
        folder = work / str(number)
        folder.mkdir()
        feedback = None
        for stage in STAGES:
            current, passed, report = execute_stage(args, root, directory, state, original,
                skills, adapter, commands, folder, number, stage, active)
            if not passed:
                feedback = {'stage': stage, 'report': report}
                break
        if feedback is None:
            state.update(status='MERGE_READY', fingerprint=current['fingerprint'])
            write_json(directory / 'state.json', state)
            validate_receipt(root, clean=False)
            print('[review-gate] MERGE_READY；提交相同内容后可 push/创建 PR，任何内容变化须重跑。')
            return
        if number == 3:
            raise GateError('三轮仍未通过，停止并请人工裁决；不得提交 PR')
        execute_stage(args, root, directory, state, original, skills, adapter,
                      commands, folder, number, 'fix', active, feedback)
        # A fix never approves itself; every next round starts with cleaning.


def run_workflow(args, root):
    directory = state_dir(root)
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / 'run.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise GateError('同一 worktree 已有审查流程运行') from exc
        state = {'version': 1, 'status': 'RUNNING', 'base_ref': args.base, 'events': []}
        write_json(directory / 'state.json', state)
        active = {}
        try:
            original = snapshot(root, args.base)
            if not original['files']:
                raise GateError('没有可审查的任务改动')
            skills = load_skills(root, args.skills_dir)
            adapter = command_json(args.agent_command) if args.agent_command else None
            commands = [command_json(value) for value in args.verify]
            work = directory / uuid.uuid4().hex
            work.mkdir()
            state['run_dir'] = str(work.relative_to(directory))
            write_json(directory / 'state.json', state)
            execute_rounds(args, root, directory, state, original, skills, adapter, commands, work, active)
        except BaseException as exc:
            record_failure(state, directory, active, exc)
            raise


def is_bootstrap_branch(branch):
    return branch.startswith('agent/') and branch.endswith('-init') and branch.count('/') == 1


def validate_protected_push(root, local_ref, sha, remote_ref, destination):
    bootstrap = is_bootstrap_branch(destination)
    if local_ref == remote_ref and not bootstrap:
        return
    references = ('refs/remotes/origin/main',) if bootstrap else (remote_ref, 'refs/remotes/origin/' + destination)
    for ref in references:
        try:
            if sha == git(root, 'rev-parse', '--verify', ref).decode().strip():
                return
        except GateError:
            continue
    raise GateError('任务分支不得通过改名推送绕过审查门禁')


def validate_push_ref(root, row):
    if len(row) != 4:
        raise GateError('pre-push ref 输入无效')
    local_ref, sha, remote_ref, _ = row
    if set(sha) == {'0'} or remote_ref.startswith('refs/tags/'):
        return
    destination = remote_ref.removeprefix('refs/heads/')
    if destination in ('main', 'master') or destination.startswith('release/') or is_bootstrap_branch(destination):
        validate_protected_push(root, local_ref, sha, remote_ref, destination)
        return
    expected_ref = 'refs/heads/' + git(root, 'branch', '--show-current').decode().strip()
    if remote_ref != expected_ref:
        raise GateError('只能交付当前已审查任务分支')
    if sha != git(root, 'rev-parse', 'HEAD').decode().strip():
        raise GateError('推送 ref/SHA 与当前已审查任务不一致')
    validate_receipt(root)


def validate_pr_head(root, requested_branch):
    branch = git(root, 'branch', '--show-current').decode().strip()
    if requested_branch and requested_branch != branch:
        raise GateError('PR 分支与当前已审查分支不一致')
    lines = git(root, 'ls-remote', '--heads', 'origin', 'refs/heads/' + branch).decode().splitlines()
    if len(lines) != 1 or lines[0].split()[0] != git(root, 'rev-parse', 'HEAD').decode().strip():
        raise GateError('PR 远端 head 不等于本地已审查 HEAD，请先 push')


def check(args, root):
    if args.push:
        for line in sys.stdin:
            if line.strip():
                validate_push_ref(root, line.split())
        return
    branch = git(root, 'branch', '--show-current').decode().strip()
    if not args.pr and is_bootstrap_branch(branch):
        validate_protected_push(root, 'HEAD', git(root, 'rev-parse', 'HEAD').decode().strip(),
                                'refs/heads/' + branch, branch)
        print('[review-gate] init 基准同步已核验：仅允许 origin/main 内容')
        return
    state = validate_receipt(root)
    if args.pr:
        validate_pr_head(root, args.branch)
    if state.get('status') == 'DOCS_ONLY':
        print('[review-gate] 纯文档变更免审（%d 个文件，仅 docs/、.wiki/ 或 *.md/*.mdx）'
              % len(state['files']))
        return
    print('[review-gate] 三技能最终内容凭证通过: ' + state['fingerprint'][:12])


def main():
    parser = argparse.ArgumentParser(description='三技能清理/验证/双审查，最多三轮自动修复')
    parser.add_argument('--project-dir', default='.')
    sub = parser.add_subparsers(dest='action', required=True)
    run = sub.add_parser('run', help='执行流程，只写代码和凭证，不发布')
    run.add_argument('--base', default='origin/main', choices=['origin/main'])
    run.add_argument('--verify', action='append', required=True, help='真实验证命令的 JSON argv，可重复')
    run.add_argument('--agent-command', help='受信任适配器 JSON argv，追加 request.json report.json；默认 Codex')
    run.add_argument('--skills-dir', help='三个精确技能目录所在根目录')
    run.add_argument('--timeout', type=int, default=1800, help='每个进程超时秒数')
    guard = sub.add_parser('check', help='只读检查当前凭证')
    guard.add_argument('--push', action='store_true')
    guard.add_argument('--pr', action='store_true')
    guard.add_argument('--branch')
    args = parser.parse_args()
    try:
        root = Path(git(Path(args.project_dir).resolve(), 'rev-parse', '--show-toplevel').decode().strip())
        if args.action == 'run':
            if args.timeout <= 0:
                raise GateError('timeout 必须大于 0')
            run_workflow(args, root)
        else:
            check(args, root)
    except (GateError, OSError, ValueError, KeyError, TypeError) as exc:
        print('[review-gate] BLOCKED: ' + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
