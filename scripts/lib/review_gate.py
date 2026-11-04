"""Input: Git content and process artifacts. Output: validated delivery receipts.
Pos: scripts/lib; local writers remain a trust boundary. Sync scripts/README.md on changes.
"""
import hashlib
import json
import os
import subprocess

STAGES = ('ai-slop-cleaner', 'verify', 'google-code-review', 'xiyu-code-review')
DOC_EXTENSIONS = ('.md', '.mdx')
DOC_DIRECTORIES = ('docs/', '.wiki/')
DOC_ARTIFACT_EXTENSIONS = ('.txt', '.rst', '.pdf', '.png', '.jpg', '.jpeg', '.gif', '.webp',
                           '.docx', '.pptx')

class GateError(Exception):
    pass


def git(root, *args):
    try:
        return subprocess.check_output(['git', '-C', str(root), *args], stderr=subprocess.PIPE, timeout=30)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        raise GateError('Git 检查失败: ' + ' '.join(args)) from exc


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    os.replace(temporary, path)


def state_dir(root):
    location = git(root, 'rev-parse', '--git-path', 'review-gate').decode().strip()
    return (root / location).resolve()


def changed_files(root, base):
    # Bind both paths of a move, independent of staging and Git rename settings.
    tracked = git(root, 'diff', '--no-renames', '--name-only', '-z', base, '--').split(b'\0')
    untracked = git(root, 'ls-files', '--others', '--exclude-standard', '-z').split(b'\0')
    return sorted({os.fsdecode(name) for name in tracked + untracked if name})


def documentation_only(root, base_ref='origin/main'):
    """仅 Markdown 或文档目录内明确的文档工件免审；未知类型默认走完整门禁。"""
    base_tip = git(root, 'rev-parse', '--verify', base_ref + '^{commit}').decode().strip()
    base = git(root, 'merge-base', base_tip, 'HEAD').decode().strip()
    files = changed_files(root, base)
    if not files:
        return False, files
    return all(name.lower().endswith(DOC_EXTENSIONS)
               or (name.startswith(DOC_DIRECTORIES)
                   and name.lower().endswith(DOC_ARTIFACT_EXTENSIONS))
               for name in files), files


def snapshot(root, base_ref='origin/main'):
    branch = git(root, 'symbolic-ref', '--short', 'HEAD').decode().strip()
    base_tip = git(root, 'rev-parse', '--verify', base_ref + '^{commit}').decode().strip()
    base = git(root, 'merge-base', base_tip, 'HEAD').decode().strip()
    files = {}
    for name in changed_files(root, base):
        path = root / name
        if path.is_symlink():
            files[name] = ['120000', digest(os.fsencode(os.readlink(path)))]
        elif path.is_file():
            files[name] = ['100755' if path.stat().st_mode & 0o111 else '100644', digest(path.read_bytes())]
        elif path.exists():
            raise GateError('不支持子模块或目录变更: ' + name)
        else:
            files[name] = None
    identity = {'branch': branch, 'base_ref': base_ref, 'base_tip': base_tip, 'base': base, 'files': files}
    identity['fingerprint'] = digest(json.dumps(identity, sort_keys=True).encode())
    return identity


def read_json(path):
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError) as exc:
        raise GateError('缺少或损坏的凭证/报告: ' + str(path)) from exc


def validate_report_structure(report):
    if not isinstance(report, dict) or set(report) != {'verdict', 'summary', 'findings'}:
        raise GateError('Agent 报告必须包含 verdict、summary、findings')
    if report['verdict'] not in ('PASS', 'NEEDS_FIXES', 'ESCALATE'):
        raise GateError('无效的审查 verdict')
    if not isinstance(report['summary'], str) or not report['summary'].strip():
        raise GateError('Agent 报告缺少具体结论')
    if not isinstance(report['findings'], list):
        raise GateError('findings 必须是数组')


def validate_finding_fields(finding):
    required = {'severity', 'file', 'line', 'message', 'disposition', 'reason'}
    if not isinstance(finding, dict) or set(finding) != required:
        raise GateError('finding 缺少位置、级别或处理记录')
    if finding['severity'] not in ('Critical', 'Major', 'Minor', 'Info'):
        raise GateError('finding 级别无效')
    if not isinstance(finding['line'], int) or finding['line'] < 1:
        raise GateError('finding 行号无效')
    if not all(isinstance(finding[k], str) for k in ('file', 'message', 'disposition', 'reason')):
        raise GateError('finding 文本字段无效')


def finding_is_unresolved(finding):
    if not finding['file'].strip() or not finding['message'].strip():
        raise GateError('finding 缺少文件或问题描述')
    if finding['disposition'] not in ('open', 'accepted', 'deferred'):
        raise GateError('finding 处理状态无效')
    if finding['severity'] in ('Critical', 'Major') or finding['disposition'] == 'open':
        return True
    if not finding['reason'].strip():
        raise GateError('非阻断意见必须记录处理理由')
    return False


def validate_report(report):
    validate_report_structure(report)
    unresolved = False
    for finding in report['findings']:
        validate_finding_fields(finding)
        if finding_is_unresolved(finding):
            unresolved = True
    if report['verdict'] == 'ESCALATE':
        raise GateError('Agent 要求人工裁决，停止自动修复')
    return report['verdict'] == 'PASS' and not unresolved


def validate_receipt_identity(root, state, clean):
    if state.get('status') != 'MERGE_READY' or state.get('version') != 1:
        raise GateError('三技能流程尚未全部通过；运行 python3 scripts/review-workflow.py run --help')
    if state.get('base_ref') != 'origin/main':
        raise GateError('交付基线必须是 origin/main')
    current = snapshot(root, state['base_ref'])
    if current['fingerprint'] != state.get('fingerprint'):
        raise GateError('代码、分支或基线已变化，必须重新执行三技能流程')
    if clean and git(root, 'status', '--porcelain', '--untracked-files=all').strip():
        raise GateError('交付前必须提交所有已审查改动，工作区须干净')
    return current


def validate_receipt_stages(events):
    final = events[-4:]
    if [e.get('stage') for e in final] != list(STAGES):
        raise GateError('缺少完整且有序的三技能/验证记录')
    if len({e.get('round') for e in final}) != 1 or final[0]['round'] not in (1, 2, 3):
        raise GateError('审查轮次无效')
    return final


def validate_receipt_artifacts(directory, events):
    for event in events:
        if not event.get('artifacts'):
            raise GateError('缺少进程执行工件')
        for relative, expected in event['artifacts'].items():
            artifact = directory / relative
            if not artifact.resolve().is_relative_to(directory) or artifact.is_symlink():
                raise GateError('工件路径越界')
            if not artifact.is_file() or digest(artifact.read_bytes()) != expected:
                raise GateError('工件已丢失或被修改: ' + relative)


def validate_final_reports(directory, final, current):
    for event in final:
        if not event.get('passed') or event.get('fingerprint') != current['fingerprint']:
            raise GateError('最后一轮未全部通过或不对应最终代码')
        if event.get('report') not in event['artifacts']:
            raise GateError('报告未绑定执行工件')
        if event['stage'] != 'verify':
            if not validate_report(read_json(directory / event['report'])):
                raise GateError('审查报告仍存在未处理问题')


def validate_receipt(root, clean=True):
    directory = state_dir(root)
    only_docs, files = documentation_only(root)
    if only_docs:
        if clean and git(root, 'status', '--porcelain', '--untracked-files=all').strip():
            raise GateError('交付前必须提交所有已审查改动，工作区须干净')
        return {'status': 'DOCS_ONLY', 'version': 1, 'base_ref': 'origin/main',
                'fingerprint': 'docs-only', 'files': files}
    state = read_json(directory / 'state.json')
    current = validate_receipt_identity(root, state, clean)
    events = state.get('events', [])
    final = validate_receipt_stages(events)
    validate_receipt_artifacts(directory, events)
    validate_final_reports(directory, final, current)
    return state
