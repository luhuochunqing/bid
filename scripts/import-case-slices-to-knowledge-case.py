#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
历史标书切片 → knowledge_case 一次性导入脚本（仅限本地 / 西域测试环境）
=========================================================================
把 slice_all_v9.py 产出的切片（/tmp/winbid_slices/project_*.jsonl）映射为
knowledge_case 案例记录，使前台"AI 案例推荐"（案例库主页 + 投标编制/项目阶段
推荐抽屉）有数据可展示。前台推荐链路只读 knowledge_case 表
（POST /api/projects/{id}/cases/recommend → ScoreItemCaseRecommendAppService），
与 bid_case_slice 切片向量表无关。

环境约束（硬守卫，代码内强制）：
  本脚本仅允许连接以下数据库，其他目标一律拒绝：
    - 127.0.0.1 / localhost（本地开发环境）
    - 172.16.38.78（西域测试环境）
  禁止指向生产或任何其他环境。

前置条件：
  目标库已通过 scripts/import-historical-projects.py 导入历史项目
  （projects.source_module = '人工录入'），切片按项目名挂接到对应历史项目。

用法:
  python3 import-case-slices-to-knowledge-case.py [--jsonl-dir=DIR] [--limit=N]
      [--db-host=] [--db-port=] [--db-user=] [--db-pass=] [--db-name=]
      [--dry-run] [--offline] [--purge] [--self-test]

参数:
  --jsonl-dir   切片 JSONL 目录（默认 /tmp/winbid_slices）
  --limit       抽样条数上限（默认 300，按项目 x docx_label 分层轮转抽取）
  --dry-run     连库匹配并打印统计/样例，不写入
  --offline     不连库：解析 + 抽样 + 打印 INSERT 样例（本地验证脚本逻辑用）
  --purge       按标记删除本脚本写入的数据（回滚手段）
  --self-test   运行纯函数断言后退出

示例:
  python3 import-case-slices-to-knowledge-case.py --self-test
  python3 import-case-slices-to-knowledge-case.py --offline --jsonl-dir ./fixture
  python3 import-case-slices-to-knowledge-case.py --db-host=172.16.38.78 --dry-run
  python3 import-case-slices-to-knowledge-case.py --db-host=172.16.38.78 --limit=300
  python3 import-case-slices-to-knowledge-case.py --db-host=172.16.38.78 --purge
"""
import argparse
import json
import os
import re
import sys

# ---------------------------------------------------------------------------
# 环境白名单（核心安全约束）：仅本地与西域测试环境
# ---------------------------------------------------------------------------
ALLOWED_DB_HOSTS = {'127.0.0.1', 'localhost', '172.16.38.78'}

# requirement_raw 占位前缀：NOT NULL 占位文本，兼作本批数据可识别标记（--purge 依据）
REQUIREMENT_PREFIX = '【历史标书切片】'

# OBS 目录名日期前缀，如 "2026.01.20--中国建设银行..." / "2026年1月2日—某项目"
DATE_PREFIX_RE = re.compile(r'^\d{4}[.年/-]\d{1,2}[.月/-]\d{1,2}日?[-—]*\s*')

MAX_TITLE_LEN = 500
MAX_PREVIEW_LEN = 300

FILE_PATTERN = re.compile(r'^project_\d+\.jsonl$')


def guard_db_host(db_host):
    """白名单硬守卫：不在允许清单内直接拒绝退出。"""
    if db_host not in ALLOWED_DB_HOSTS:
        sys.exit(
            f'拒绝连接：--db-host "{db_host}" 不在允许清单 {sorted(ALLOWED_DB_HOSTS)} 内。\n'
            '本脚本仅允许本地（127.0.0.1/localhost）与西域测试环境（172.16.38.78）。'
        )


# ---------------------------------------------------------------------------
# 纯函数：可单测（--self-test）
# ---------------------------------------------------------------------------
def normalize_dir_name(name):
    """剥离 OBS 目录名的日期前缀，得到用于项目名匹配的归一化名称。"""
    if not name:
        return ''
    return DATE_PREFIX_RE.sub('', str(name).strip()).strip()


def match_project(dir_name, projects_by_name):
    """
    切片目录名 → 历史项目匹配。
    1) 归一化后精确相等；2) 互相包含兜底（多候选取最长名，最具体）。
    返回 project dict 或 None。
    """
    norm = normalize_dir_name(dir_name)
    if not norm:
        return None
    exact = projects_by_name.get(norm)
    if exact is not None:
        return exact
    candidates = [
        (name, proj) for name, proj in projects_by_name.items()
        if name and (norm in name or name in norm)
    ]
    if not candidates:
        return None
    candidates.sort(key=lambda kv: len(kv[0]), reverse=True)
    return candidates[0][1]


def load_jsonl_slices(jsonl_dir):
    """扫描目录下 project_*.jsonl，解析为切片 dict 列表（跳过非法行）。"""
    slices = []
    skipped = 0
    if not os.path.isdir(jsonl_dir):
        sys.exit(f'目录不存在：{jsonl_dir}')
    files = sorted(
        f for f in os.listdir(jsonl_dir) if FILE_PATTERN.match(f)
    )
    if not files:
        sys.exit(f'未找到 project_*.jsonl 文件：{jsonl_dir}')
    for fname in files:
        path = os.path.join(jsonl_dir, fname)
        with open(path, encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except ValueError:
                    skipped += 1
                    continue
                title = (rec.get('title') or '').strip()
                preview = (rec.get('text_preview') or '').strip()
                project = (rec.get('project') or '').strip()
                if not title or not preview or not project:
                    skipped += 1
                    continue
                slices.append({
                    'project': project,
                    'docx_label': (rec.get('docx_label') or '').strip()[:50],
                    'title': title[:MAX_TITLE_LEN],
                    'text_preview': preview[:MAX_PREVIEW_LEN],
                })
    print(f'JSONL 解析: {len(slices)} 条切片（跳过 {skipped} 条非法行，{len(files)} 个文件）')
    return slices


def sample_slices(slices, limit):
    """按 (项目, docx_label) 分组轮转抽取，保证覆盖多个项目与各类别。"""
    if limit <= 0 or not slices:
        return []
    groups = {}
    for s in slices:
        groups.setdefault((s['project'], s['docx_label']), []).append(s)
    keys = list(groups.keys())
    picked = []
    idx = 0
    while len(picked) < limit:
        progressed = False
        for k in keys:
            if len(picked) >= limit:
                break
            if groups[k]:
                picked.append(groups[k].pop(0))
                progressed = True
        idx += 1
        if not progressed:
            break
    return picked


def build_row(sl, project):
    """
    切片 + 来源项目 → knowledge_case 插入参数。
    列顺序与 INSERT_SQL 占位符一一对应：
      source_project_id, source_project_name, scoring_point_title,
      requirement_raw, response_text, customer_type, project_type,
      bid_result, scoring_category
    """
    title = sl['title']
    return (
        project['id'],
        project['name'],
        title,
        REQUIREMENT_PREFIX + title,
        sl['text_preview'],
        project['customer_type'],
        project['industry'],
        'WON' if project['status'] == 'WON' else None,
        sl['docx_label'] or None,
    )


INSERT_SQL = """
    INSERT IGNORE INTO knowledge_case (
        source_project_id, source_project_name, scoring_point_title, scoring_point_id,
        requirement_raw, response_text, reuse_count, is_pinned, status,
        customer_type, project_type, bid_result, scoring_category, product_line,
        created_at
    ) VALUES (%s,%s,%s,NULL,%s,%s,0,0,'ACTIVE',%s,%s,%s,%s,NULL,NOW())
"""


# ---------------------------------------------------------------------------
# 数据装配
# ---------------------------------------------------------------------------
def load_historical_projects(args):
    """连库加载 import-historical-projects.py 导入的历史项目（人工录入）。"""
    import pymysql
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT id, name, status, customer_type, industry
                FROM projects WHERE source_module = '人工录入'
            """)
            rows = cur.fetchall()
    finally:
        conn.close()
    projects = {}
    for pid, name, status, cust_type, industry in rows:
        if name:
            projects[name] = {'id': pid, 'name': name, 'status': status,
                              'customer_type': cust_type, 'industry': industry}
    if not projects:
        sys.exit(
            '目标库未找到历史项目（projects.source_module=\'人工录入\'）。\n'
            '请先执行 scripts/import-historical-projects.py 导入历史项目，再运行本脚本。'
        )
    print(f'历史项目: {len(projects)} 个（source_module=人工录入）')
    return projects


def build_offline_projects(slices):
    """离线模式：为每个唯一目录名构造虚拟项目映射（不连库，仅验证逻辑）。"""
    projects = {}
    next_id = 900001
    for s in slices:
        norm = normalize_dir_name(s['project'])
        if norm and norm not in projects:
            projects[norm] = {'id': next_id, 'name': norm, 'status': 'WON',
                              'customer_type': None, 'industry': None}
            next_id += 1
    print(f'[offline] 构造虚拟项目: {len(projects)} 个')
    return projects


def match_slices(slices, projects_by_name):
    """全量切片匹配项目，返回 (已匹配切片列表, 未匹配目录名→条数统计)。"""
    matched = []
    unmatched = {}
    for s in slices:
        proj = match_project(s['project'], projects_by_name)
        if proj is None:
            unmatched[s['project']] = unmatched.get(s['project'], 0) + 1
        else:
            s['_project'] = proj
            matched.append(s)
    return matched, unmatched


def print_stats(matched, unmatched, sampled, rows, args):
    print(f'匹配成功: {len(matched)} 条切片，覆盖 {len({s["_project"]["id"] for s in matched})} 个项目')
    if unmatched:
        print(f'未匹配: {sum(unmatched.values())} 条，涉及 {len(unmatched)} 个项目目录：')
        for name, cnt in sorted(unmatched.items(), key=lambda kv: -kv[1])[:20]:
            print(f'  - {name}（{cnt} 条）')
        if len(unmatched) > 20:
            print(f'  ... 其余 {len(unmatched) - 20} 个项目省略')
    print(f'抽样: {len(sampled)} 条（--limit {args.limit}）')
    if rows:
        print('\n--- 样例（前 5 条）---')
        for r in rows[:5]:
            print(f'  project_id={r[0]} | {r[2][:40]} | category={r[8]} | bid_result={r[7]}')


def fmt_value(v):
    """样例打印用：字符串截断加引号，None → NULL。"""
    if v is None:
        return 'NULL'
    s = str(v)
    return "'" + (s[:40] + '...' if len(s) > 40 else s) + "'"


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------
def do_purge(args):
    """按 requirement_raw 标记删除本脚本写入的数据（回滚手段）。"""
    import pymysql
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        with conn.cursor() as cur:
            cur.execute(
                'SELECT COUNT(*) FROM knowledge_case WHERE requirement_raw LIKE %s',
                (REQUIREMENT_PREFIX + '%',))
            total = cur.fetchone()[0]
            print(f'待删除（{REQUIREMENT_PREFIX} 标记）: {total} 条')
            cur.execute(
                'DELETE FROM knowledge_case WHERE requirement_raw LIKE %s',
                (REQUIREMENT_PREFIX + '%',))
        conn.commit()
        print(f'已删除: {total} 条')
    finally:
        conn.close()


def do_insert(args, rows):
    """正式写入（INSERT IGNORE，幂等）。"""
    import pymysql
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    inserted = 0
    try:
        with conn.cursor() as cur:
            for r in rows:
                cur.execute(INSERT_SQL, r)
                inserted += cur.rowcount
        conn.commit()
    finally:
        conn.close()
    print(f'\n写入完成: 新增 {inserted} 条，跳过重复 {len(rows) - inserted} 条')
    print('验证: SELECT COUNT(*) FROM knowledge_case WHERE requirement_raw LIKE \'【历史标书切片】%\';')


def self_test():
    """纯函数断言：名称归一化 / 项目匹配 / 抽样 / 行装配。"""
    # 1. 日期前缀剥离
    assert normalize_dir_name('2026.01.20--中国建设银行龙集采项目') == '中国建设银行龙集采项目'
    assert normalize_dir_name('2026/3/5-某医院物业项目') == '某医院物业项目'
    assert normalize_dir_name('2026年1月2日—某项目') == '某项目'
    assert normalize_dir_name('无日期前缀项目') == '无日期前缀项目'
    assert normalize_dir_name('') == ''

    # 2. 精确匹配
    p1 = {'id': 1, 'name': '中国建设银行龙集采项目', 'status': 'WON',
          'customer_type': 'CENTRAL_SOE', 'industry': '综合'}
    lookup = {p1['name']: p1}
    assert match_project('2026.01.20--中国建设银行龙集采项目', lookup) is p1
    assert match_project('不存在的项目', lookup) is None

    # 3. 包含兜底（项目名是目录名子串）
    p2 = {'id': 2, 'name': '建设银行项目', 'status': 'LOST',
          'customer_type': None, 'industry': None}
    lookup2 = {p2['name']: p2}
    assert match_project('2026.01.20--建设银行项目标段一', lookup2) is p2

    # 4. 多候选取最长名（最具体）
    p3 = {'id': 3, 'name': '建设银行项目二期', 'status': 'WON',
          'customer_type': None, 'industry': None}
    lookup3 = {p2['name']: p2, p3['name']: p3}
    assert match_project('2026.01.20--建设银行项目二期采购', lookup3) is p3

    # 5. 分层抽样：覆盖多项目与多类别，且不超 limit
    slices = [
        {'project': 'A', 'docx_label': '技术', 'title': f'A技{i}', 'text_preview': 'x'} for i in range(5)
    ] + [
        {'project': 'B', 'docx_label': '商务', 'title': f'B商{i}', 'text_preview': 'x'} for i in range(5)
    ]
    picked = sample_slices(slices, 6)
    assert len(picked) == 6
    assert {s['project'] for s in picked} == {'A', 'B'}
    assert {s['docx_label'] for s in picked} == {'技术', '商务'}
    assert len(sample_slices(slices, 100)) == 10
    assert sample_slices([], 10) == []

    # 6. 行装配：中标项目 → bid_result='WON'；未中标 → NULL
    sl = {'project': 'A', 'docx_label': '技术', 'title': '售后服务方案', 'text_preview': '承诺7x24'}
    row = build_row(sl, p1)
    assert row == (1, '中国建设银行龙集采项目', '售后服务方案',
                   '【历史标书切片】售后服务方案', '承诺7x24',
                   'CENTRAL_SOE', '综合', 'WON', '技术')
    row2 = build_row(sl, p2)
    assert row2[7] is None  # LOST → bid_result NULL

    print('self-test passed: 6 组断言全部通过')


def main():
    ap = argparse.ArgumentParser(description='历史标书切片 → knowledge_case 一次性导入（仅限本地/西域测试环境）')
    ap.add_argument('--jsonl-dir', default='/tmp/winbid_slices')
    ap.add_argument('--limit', type=int, default=300)
    ap.add_argument('--db-host', default='127.0.0.1')
    ap.add_argument('--db-port', type=int, default=3306)
    ap.add_argument('--db-user', default='xiyu_user')
    ap.add_argument('--db-pass', default='XiyuDB!2026')
    ap.add_argument('--db-name', default='xiyu_bid_main')
    ap.add_argument('--dry-run', action='store_true', help='连库匹配并打印统计/样例，不写入')
    ap.add_argument('--offline', action='store_true', help='不连库：解析+抽样+打印 INSERT 样例')
    ap.add_argument('--purge', action='store_true', help='按标记删除本脚本写入的数据')
    ap.add_argument('--self-test', action='store_true', help='运行纯函数断言后退出')
    args = ap.parse_args()

    if args.self_test:
        self_test()
        return

    # 白名单硬守卫：所有连库模式（含 --purge / --dry-run）生效
    if not args.offline:
        guard_db_host(args.db_host)

    if args.purge:
        do_purge(args)
        return

    slices = load_jsonl_slices(args.jsonl_dir)
    projects = build_offline_projects(slices) if args.offline else load_historical_projects(args)

    matched, unmatched = match_slices(slices, projects)
    sampled = sample_slices(matched, args.limit)
    rows = [build_row(s, s['_project']) for s in sampled]
    print_stats(matched, unmatched, sampled, rows, args)

    if args.offline:
        print('\n[offline] 将生成的 INSERT 样例（前 5 条，未连库、未写入）：')
        for r in rows[:5]:
            print('  INSERT IGNORE INTO knowledge_case (...) VALUES ('
                  + ', '.join(fmt_value(v) for v in r) + ', ...)')
        print('[offline] 结束：未连接任何数据库')
        return

    if args.dry_run:
        print(f'\n[dry-run] 未写库，共 {len(rows)} 条待导入')
        return

    do_insert(args, rows)


if __name__ == '__main__':
    main()

