"""Input: stage/skills/argv. Output: isolated process reports. Pos: scripts/lib.
Keep scripts/README.md in sync when the agent protocol changes.
"""
import json
import os
from pathlib import Path
import signal
import subprocess

from review_gate import GateError, write_json

SKILLS = ('ai-slop-cleaner', 'google-code-review', 'xiyu-code-review')
FINDING_FIELDS = {
    'severity': {'type': 'string', 'enum': ['Critical', 'Major', 'Minor', 'Info']},
    'file': {'type': 'string'}, 'line': {'type': 'integer'},
    'message': {'type': 'string'},
    'disposition': {'type': 'string', 'enum': ['open', 'accepted', 'deferred']},
    'reason': {'type': 'string'},
}
REPORT_SCHEMA = {
    'type': 'object', 'additionalProperties': False,
    'required': ['verdict', 'summary', 'findings'],
    'properties': {
        'verdict': {'type': 'string', 'enum': ['PASS', 'NEEDS_FIXES', 'ESCALATE']},
        'summary': {'type': 'string'},
        'findings': {'type': 'array', 'items': {
            'type': 'object', 'additionalProperties': False,
            'required': list(FINDING_FIELDS), 'properties': FINDING_FIELDS,
        }},
    },
}


def command_json(value):
    try:
        command = json.loads(value)
    except ValueError as exc:
        raise GateError('命令必须使用 JSON 字符串数组') from exc
    if not isinstance(command, list) or not command or not all(isinstance(x, str) and x for x in command):
        raise GateError('命令必须是非空 JSON 字符串数组')
    return command


def load_skills(root, override=None):
    homes = [Path(override)] if override else [
        root / '.agents/skills', root / '.codex/skills',
        Path.home() / '.agents/skills', Path.home() / '.codex/skills',
    ]
    result = {}
    for name in SKILLS:
        path = next((home / name / 'SKILL.md' for home in homes if (home / name / 'SKILL.md').is_file()), None)
        if path is None:
            raise GateError('缺少必需技能: ' + name)
        content = path.read_text()
        if not content.strip():
            raise GateError('技能文件为空: ' + str(path))
        result[name] = {'path': str(path), 'content': content}
    return result


def run_process(command, root, logfile, timeout, prompt=None):
    with logfile.open('w') as stream:
        process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE if prompt else subprocess.DEVNULL,
                                   stdout=stream, stderr=subprocess.STDOUT, text=True, start_new_session=True)
        try:
            process.communicate(prompt, timeout=timeout)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            raise GateError('进程超时/中断，停止流程；日志: ' + str(logfile))
    return process.returncode


def invoke(root, folder, stage, round_number, scope, base, skills, adapter, timeout, feedback=None):
    review = stage in SKILLS[1:]
    prompt = (
        '你好，同志！在当前隔离任务工作区执行指定阶段。禁止提交、push、PR、部署、删除文件范围外的内容。\n'
        f'阶段: {stage}；第 {round_number}/3 轮。基线: {base}。\n'
        '审查范围是基线到当前工作区的完整 diff（含未跟踪文件），不能只看上一轮发现。\n'
        '阅读 AGENTS.md 及相关项目规范；不要启动开发服务。返回指定 JSON 报告，不写自批凭证。\n'
        '你只是主执行器调度的一个阶段，不得嵌套执行 review-workflow run/check 或收尾流程。\n'
    )
    if review:
        prompt += ('你是新进程的独立审查者，必须只读。自行从全量代码推断行为与设计，不接受作者解释。'
                   '同时给出行为/设计复杂度/交付就绪判断，发现标文件行号。'
                   'Critical/Major 必须阻断。Minor/Info 如接受或延后必须写具体理由；否则 open。\n')
    else:
        prompt += ('你是写入阶段。只允许修改以下原任务文件；不要扩展范围，不改门禁凭证或Git元数据：\n'
                   + json.dumps(scope, ensure_ascii=False) + '\n'
                   '行为保真、先验证保护再最小化修改。范围外问题或需求冲突返回 ESCALATE。\n'
                   '允许创建本会话 .omc/entry-gate-<sessionID>.md 满足宿主前置校验；这是忽略的运行记录，'
                   '不属于业务范围扩展。入口记录中文件范围每行一个绝对路径，点开头路径不可省略。'
                   '不要写 .omc/review-* 或修改本流程 Git 元数据凭证。\n'
                   '沙箱内先跑无需监听端口的最小行为保护测试；需要本地监听的测试由主执行器 verify 阶段执行。\n')
    if stage in skills:
        prompt += '必须实际遵循以下精确技能全文（清理阶段是标准写入 Mode B）：\n' + skills[stage]['content']
    if feedback:
        prompt += '\n修复以下真实运行发现，勿通过删除测试或弱化门禁解决：\n' + json.dumps(feedback, ensure_ascii=False)
    request = {'stage': stage, 'round': round_number, 'scope': scope,
               'base': base, 'prompt': prompt, 'skill': skills.get(stage), 'feedback': feedback}
    request_path = folder / f'{stage}-request.json'
    report_path = folder / f'{stage}-report.json'
    logfile = folder / f'{stage}.log'
    write_json(request_path, request)
    if adapter:
        command = [*adapter, str(request_path), str(report_path)]
        text = None
    else:
        schema_path = folder / 'report-schema.json'
        write_json(schema_path, REPORT_SCHEMA)
        command = ['codex', 'exec', '--ephemeral', '--sandbox', 'read-only' if review else 'workspace-write',
                   '-c', 'ask_for_approval="never"', '--output-schema', str(schema_path),
                   '--output-last-message', str(report_path), '--json', '-']
        text = prompt
    print(f'[review-gate] round {round_number}/3: {stage}', flush=True)
    code = run_process(command, root, logfile, timeout, text)
    return code, report_path, [request_path, report_path, logfile]
