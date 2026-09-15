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
    - 172.16.38.78（西域测试环境应用主机）
    - winbid-01.test.rds.ehsy.com（西域测试环境 RDS，即 172.16.38.78 配套数据库）
  禁止指向生产或任何其他环境。

前置条件：
  目标库已通过 scripts/import-historical-projects.py 导入历史项目。
  识别标记兼容两种 source_module：'人工录入'（新版导入）与 'MANUAL_SINGLE'
  （早期导入的存量数据）。切片按项目名挂接到对应历史项目。

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
  --backfill-meta 一次性回填存量切片案例的 bid_result / project_type（配合 --dry-run）
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
# 环境白名单（核心安全约束）：仅本地与西域测试环境（应用主机 + 配套测试 RDS）
# ---------------------------------------------------------------------------
ALLOWED_DB_HOSTS = {'127.0.0.1', 'localhost', '172.16.38.78', 'winbid-01.test.rds.ehsy.com'}

# requirement_raw 占位前缀：NOT NULL 占位文本，兼作本批数据可识别标记（--purge 依据）
REQUIREMENT_PREFIX = '【历史标书切片】'

# 历史项目的 source_module 识别标记：新版导入写'人工录入'，早期导入写'MANUAL_SINGLE'，两者都认。
# （测试库实际存量即 MANUAL_SINGLE，见 2026-08-31 导入前查证）
HISTORICAL_SOURCE_MODULES = ('人工录入', 'MANUAL_SINGLE')

# 项目状态 → knowledge_case.bid_result 全终态映射（前台三态：已中标/未中标/已流标）
BID_RESULT_MAP = {'WON': 'WON', 'LOST': 'LOST', 'FAILED': 'FAILED'}

# 项目类型中文原值 → 枚举码（与 import-historical-projects.py 的 PROJECT_TYPE 一致）。
# 历史项目的"项目类型"存在 tenders.project_type（中文原值），前台案例标签/筛选期望枚举码。
PROJECT_TYPE_MAP = {'办公': 'OFFICE', '综合': 'COMPREHENSIVE', '集采': 'COLLECTIVE',
                    '工业品': 'INDUSTRIAL', '其他': 'OTHER'}

# OBS 目录名日期前缀，如 "2026.01.20--中国建设银行..." / "2026年1月2日—某项目"
DATE_PREFIX_RE = re.compile(r'^\d{4}[.年/-]\d{1,2}[.月/-]\d{1,2}日?[-—]*\s*')

MAX_TITLE_LEN = 500
MAX_PREVIEW_LEN = 300
MAX_SOURCE_SECTION_TITLE_LEN = 500
MAX_SOURCE_SECTION_PATH_LEN = 2000
MAX_SOURCE_DOCUMENT_NAME_LEN = 255

FILE_PATTERN = re.compile(r'^project_\d+\.jsonl$')


def guard_db_host(db_host):
    """白名单硬守卫：不在允许清单内直接拒绝退出。"""
    if db_host not in ALLOWED_DB_HOSTS:
        sys.exit(
            f'拒绝连接：--db-host "{db_host}" 不在允许清单 {sorted(ALLOWED_DB_HOSTS)} 内。\n'
            '本脚本仅允许本地（127.0.0.1/localhost）与西域测试环境'
            '（172.16.38.78 / winbid-01.test.rds.ehsy.com）。'
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
                obs_key = (rec.get('obs_bid_file_key') or '').strip()
                if not title or not preview or not project or not is_valid_obs_bid_key(obs_key):
                    skipped += 1
                    continue
                slices.append({
                    'project': project,
                    'docx_label': (rec.get('docx_label') or '').strip()[:50],
                    'title': title[:MAX_TITLE_LEN],
                    'text_preview': preview[:MAX_PREVIEW_LEN],
                    'text': (rec.get('text') or '').strip(),
                    'obs_bid_file_key': obs_key,
                    'sub_topic': (rec.get('sub_topic') or '').strip() or None,
                    'quality_score': int(rec.get('quality_score') or 60),
                    **extract_source_fields(rec),
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
      bid_result, scoring_category, obs_bid_file_key, sub_topic, quality_score,
      source_section_title, source_section_path, source_document_name,
      source_page_number, source_section_index
    """
    title = sl['title']
    return (
        project['id'],
        project['name'],
        title,
        REQUIREMENT_PREFIX + title,
        sl.get('text') or sl['text_preview'],
        project['customer_type'],
        effective_project_type(project),
        BID_RESULT_MAP.get(project['status']),
        sl['docx_label'] or None,
        sl.get('obs_bid_file_key') or None,
        sl.get('sub_topic') or None,
        int(sl.get('quality_score') or 60),
        sl.get('source_section_title') or None,
        sl.get('source_section_path') or None,
        sl.get('source_document_name') or None,
        sl.get('source_page_number') or None,
        sl.get('source_section_index') or None,
    )


def effective_project_type(project):
    """project_type 取值：取来源项目的"项目类型"（tenders.project_type 中文原值，
    加载时已放入 proj_type_cn），经 PROJECT_TYPE_MAP 归一为枚举码（前台标签/筛选值域）。

    不得取 projects.industry（历史项目该列全空），也不得回退取 customer_type——
    两套枚举语义不同（项目类型=办公/综合/集采/工业品/其他，客户类型=LOCAL_SOE 等），
    混填会导致前台项目类型标签显示原始码。未匹配/为空 → None。
    """
    raw = (project.get('proj_type_cn') or '').strip()
    return PROJECT_TYPE_MAP.get(raw)


INSERT_SQL = """
    INSERT IGNORE INTO knowledge_case (
        source_project_id, source_project_name, scoring_point_title, scoring_point_id,
        requirement_raw, response_text, reuse_count, is_pinned, status,
        customer_type, project_type, bid_result, scoring_category, product_line,
        obs_bid_file_key, sub_topic, quality_score,
        source_section_title, source_section_path, source_document_name,
        source_page_number, source_section_index,
        created_at
    ) VALUES (%s,%s,%s,NULL,%s,%s,0,0,'ACTIVE',%s,%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s,%s,NOW())
"""

# OBS 桶内历史投标文件根前缀（与后端 ObsObjectKeyPolicy.HISTORICAL_ROOT_PREFIX 一致）
OBS_BID_ROOT_PREFIX = u'2026年（投标文件）/'


def is_valid_obs_bid_key(key):
    """纯函数：obs_bid_file_key 合法性（非空、桶内根前缀开头、无路径穿越）。"""
    if not key or not isinstance(key, str):
        return False
    key = key.strip()
    if not key.startswith(OBS_BID_ROOT_PREFIX):
        return False
    if key.startswith('/') or '..' in key:
        return False
    return True


def validate_source_section_title(value):
    """校验来源章节标题：非空字符串截断至 MAX_SOURCE_SECTION_TITLE_LEN，空/None → None。"""
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    return s[:MAX_SOURCE_SECTION_TITLE_LEN]


def validate_source_section_path(value):
    """校验来源章节路径：非空字符串截断至 MAX_SOURCE_SECTION_PATH_LEN，空/None → None。"""
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    return s[:MAX_SOURCE_SECTION_PATH_LEN]


def validate_source_document_name(value):
    """校验来源文档名：非空字符串截断至 MAX_SOURCE_DOCUMENT_NAME_LEN，空/None → None。"""
    if value is None:
        return None
    s = str(value).strip()
    if not s:
        return None
    return s[:MAX_SOURCE_DOCUMENT_NAME_LEN]


def validate_source_page_number(value):
    """校验来源页码：正整数或 None；非法值（负数、零、非数字）→ None。"""
    if value is None:
        return None
    try:
        n = int(value)
    except (ValueError, TypeError):
        return None
    return n if n > 0 else None


def validate_source_section_index(value):
    """校验章节序号：非负整数或 None；非法值（负数、非数字）→ None。"""
    if value is None:
        return None
    try:
        n = int(value)
    except (ValueError, TypeError):
        return None
    return n if n >= 0 else None


def extract_source_fields(rec):
    """从 JSONL 记录提取并校验五个来源字段，返回 dict。缺失/非法 → None。"""
    return {
        'source_section_title': validate_source_section_title(rec.get('source_section_title')),
        'source_section_path': validate_source_section_path(rec.get('source_section_path')),
        'source_document_name': validate_source_document_name(rec.get('source_document_name')),
        'source_page_number': validate_source_page_number(rec.get('source_page_number')),
        'source_section_index': validate_source_section_index(rec.get('source_section_index')),
    }


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
            # 项目类型真值在 tenders.project_type（中文原值）；projects.industry 历史项目全空
            cur.execute("""
                SELECT p.id, p.name, p.status, p.customer_type, t.project_type
                FROM projects p
                LEFT JOIN tenders t ON p.tender_id = t.id
                WHERE p.source_module IN (%s, %s)
            """, HISTORICAL_SOURCE_MODULES)
            rows = cur.fetchall()
    finally:
        conn.close()
    projects = {}
    for pid, name, status, cust_type, proj_type_cn in rows:
        if name:
            projects[name] = {'id': pid, 'name': name, 'status': status,
                              'customer_type': cust_type, 'proj_type_cn': proj_type_cn}
    if not projects:
        sys.exit(
            '目标库未找到历史项目（projects.source_module IN '
            + str(HISTORICAL_SOURCE_MODULES) + '）。\n'
            '请先执行 scripts/import-historical-projects.py 导入历史项目，再运行本脚本。'
        )
    print(f'历史项目: {len(projects)} 个（source_module IN {", ".join(HISTORICAL_SOURCE_MODULES)}）')
    return projects


def build_offline_projects(slices):
    """离线模式：为每个唯一目录名构造虚拟项目映射（不连库，仅验证逻辑）。"""
    projects = {}
    next_id = 900001
    for s in slices:
        norm = normalize_dir_name(s['project'])
        if norm and norm not in projects:
            projects[norm] = {'id': next_id, 'name': norm, 'status': 'WON',
                              'customer_type': None, 'proj_type_cn': None}
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
            src_title = r[12] if len(r) > 12 else None
            print(f'  project_id={r[0]} | {r[2][:40]} | category={r[8]} | bid_result={r[7]}'
                  f' | source_title={fmt_value(src_title)}')


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


def do_backfill_meta(args):
    """一次性回填存量切片案例的 bid_result / project_type（修复 2026-08-31 导入的两处缺陷）：
      - bid_result：此前仅 WON 写入，LOST/FAILED 被丢 → 按来源项目状态全终态回填
      - project_type：此前误读 projects.industry（全空）→ 改按 tenders.project_type 归一回填
    范围锚定：【历史标书切片】标记 且来源项目 source_module 为历史导入。"""
    import pymysql
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT kc.id, kc.bid_result, kc.project_type, p.status, t.project_type
                FROM knowledge_case kc
                JOIN projects p ON kc.source_project_id = p.id
                LEFT JOIN tenders t ON p.tender_id = t.id
                WHERE kc.requirement_raw LIKE %s AND p.source_module IN (%s, %s)
            """, (REQUIREMENT_PREFIX + '%',) + HISTORICAL_SOURCE_MODULES)
            rows = cur.fetchall()
        if not rows:
            print('未找到需回填的切片案例（' + REQUIREMENT_PREFIX + ' 标记）')
            return
        bid_updates, type_updates = [], []
        for kc_id, old_bid, old_type, proj_status, proj_type_cn in rows:
            new_bid = BID_RESULT_MAP.get(proj_status)
            if new_bid and new_bid != old_bid:
                bid_updates.append((new_bid, kc_id))
            new_type = PROJECT_TYPE_MAP.get((proj_type_cn or '').strip())
            if new_type and new_type != old_type:
                type_updates.append((new_type, kc_id))
        print(f'扫描: {len(rows)} 条切片案例')
        print(f'待回填 bid_result: {len(bid_updates)} 条（样例: {bid_updates[:5]}）')
        print(f'待回填 project_type: {len(type_updates)} 条（样例: {type_updates[:5]}）')
        if args.dry_run:
            print('[dry-run] 未写库')
            return
        with conn.cursor() as cur:
            cur.executemany('UPDATE knowledge_case SET bid_result=%s WHERE id=%s', bid_updates)
            cur.executemany('UPDATE knowledge_case SET project_type=%s WHERE id=%s', type_updates)
        conn.commit()
        print('回填完成')
        print("复核: SELECT bid_result, COUNT(*) FROM knowledge_case WHERE requirement_raw LIKE '【历史标书切片】%' GROUP BY bid_result;")
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
          'customer_type': 'CENTRAL_SOE', 'proj_type_cn': '综合'}
    lookup = {p1['name']: p1}
    assert match_project('2026.01.20--中国建设银行龙集采项目', lookup) is p1
    assert match_project('不存在的项目', lookup) is None

    # 3. 包含兜底（项目名是目录名子串）
    p2 = {'id': 2, 'name': '建设银行项目', 'status': 'LOST',
          'customer_type': None, 'proj_type_cn': None}
    lookup2 = {p2['name']: p2}
    assert match_project('2026.01.20--建设银行项目标段一', lookup2) is p2

    # 4. 多候选取最长名（最具体）
    p3 = {'id': 3, 'name': '建设银行项目二期', 'status': 'WON',
          'customer_type': None, 'proj_type_cn': '集采'}
    lookup3 = {p2['name']: p2, p3['name']: p3}
    assert match_project('2026.01.20--建设银行项目二期采购', lookup3) is p3

    # 5. 分层抽样：覆盖多项目与多类别，且不超 limit；抽样后切片保留 text 全文键
    slices = [
        {'project': 'A', 'docx_label': '技术', 'title': f'A技{i}', 'text_preview': 'x', 'text': '全文A',
         'obs_bid_file_key': OBS_BID_ROOT_PREFIX + 'A/技术标.docx'} for i in range(5)
    ] + [
        {'project': 'B', 'docx_label': '商务', 'title': f'B商{i}', 'text_preview': 'x', 'text': '全文B',
         'obs_bid_file_key': OBS_BID_ROOT_PREFIX + 'B/商务标.docx'} for i in range(5)
    ]
    picked = sample_slices(slices, 6)
    assert len(picked) == 6
    assert {s['project'] for s in picked} == {'A', 'B'}
    assert {s['docx_label'] for s in picked} == {'技术', '商务'}
    assert all('text' in s for s in picked)  # 抽样不得丢失全文键（CO 修复：曾因此回退到 300 字预览）
    assert len(sample_slices(slices, 100)) == 10
    assert sample_slices([], 10) == []

    # 6. 行装配：全终态映射（WON/LOST/FAILED），项目类型归一为枚举码，透出 sub_topic 与 quality_score
    sl = {'project': 'A', 'docx_label': '技术', 'title': '售后服务方案', 'text_preview': '承诺7x24',
          'obs_bid_file_key': OBS_BID_ROOT_PREFIX + '2026.01.20--中国建设银行龙集采项目/技术标.docx',
          'sub_topic': 'SLA_SERVICE', 'quality_score': 85}
    row = build_row(sl, p1)
    assert row == (1, '中国建设银行龙集采项目', '售后服务方案',
                   '【历史标书切片】售后服务方案', '承诺7x24',
                   'CENTRAL_SOE', 'COMPREHENSIVE', 'WON', '技术',
                   OBS_BID_ROOT_PREFIX + '2026.01.20--中国建设银行龙集采项目/技术标.docx',
                   'SLA_SERVICE', 85,
                   None, None, None, None, None)
    row2 = build_row(sl, p2)
    assert row2[7] == 'LOST'   # LOST → 未中标（修复：此前误丢为 NULL 致前台显示"未知"）
    p4 = {'id': 4, 'name': '流标项目', 'status': 'FAILED',
          'customer_type': None, 'proj_type_cn': '集采'}
    assert build_row(sl, p4)[7] == 'FAILED'   # FAILED → 已流标
    assert build_row(sl, p4)[6] == 'COLLECTIVE'
    assert build_row(sl, p3)[6] == 'COLLECTIVE'

    # 7. 全文优先：切片带 text（全文）时 response_text 用全文，缺失时回退 text_preview
    sl_full = dict(sl, text='承诺7x24小时响应，这是完整正文……')
    assert build_row(sl_full, p1)[4] == '承诺7x24小时响应，这是完整正文……'
    assert build_row(sl, p1)[4] == '承诺7x24'

    # 8. project_type 归一：中文→枚举码；未知中文/为空 → None；不回退客户类型
    assert build_row(sl, p2)[6] is None
    assert effective_project_type({'proj_type_cn': '办公', 'customer_type': 'LOCAL_SOE'}) == 'OFFICE'
    assert effective_project_type({'proj_type_cn': '工业品', 'customer_type': None}) == 'INDUSTRIAL'
    assert effective_project_type({'proj_type_cn': '未知类型', 'customer_type': None}) is None
    assert effective_project_type({'proj_type_cn': None, 'customer_type': 'LOCAL_SOE'}) is None

    # 9. obs_bid_file_key 校验：每条记录非空且以桶内根前缀开头（--self-test 契约）
    assert is_valid_obs_bid_key(OBS_BID_ROOT_PREFIX + '某项目/技术标.docx')
    assert not is_valid_obs_bid_key('')
    assert not is_valid_obs_bid_key(None)
    assert not is_valid_obs_bid_key('其他桶前缀/文件.docx')
    assert not is_valid_obs_bid_key('/' + OBS_BID_ROOT_PREFIX + 'x.docx')
    assert not is_valid_obs_bid_key(OBS_BID_ROOT_PREFIX + '../etc/passwd')
    assert all(is_valid_obs_bid_key(s['obs_bid_file_key']) for s in slices)
    assert row[9] == sl['obs_bid_file_key']  # 行装配透出 obs key（index 9）
    assert build_row({**sl, 'obs_bid_file_key': None}, p1)[9] is None  # 缺失时 NULL

    # 10. 来源字段：有显式字段时写入对应位置（index 12-16）
    sl_with_source = {**sl,
                      'source_section_title': '技术方案 > 项目实施',
                      'source_section_path': '技术方案 > 项目实施 > 服务保障',
                      'source_document_name': '技术标.docx',
                      'source_page_number': 12,
                      'source_section_index': 3}
    row_src = build_row(sl_with_source, p1)
    assert len(row_src) == 17
    assert row_src[12] == '技术方案 > 项目实施'
    assert row_src[13] == '技术方案 > 项目实施 > 服务保障'
    assert row_src[14] == '技术标.docx'
    assert row_src[15] == 12
    assert row_src[16] == 3

    # 11. 来源字段：无显式字段时全部 NULL（旧格式兼容，title 不复制到来源标题）
    row_nosrc = build_row(sl, p1)
    assert row_nosrc[12] is None
    assert row_nosrc[14] is None
    assert row_nosrc[15] is None
    assert row_nosrc[16] is None
    assert row_nosrc[17 - 1] is None

    # 12. 来源字段校验：非法页码 → None
    assert validate_source_page_number(-1) is None
    assert validate_source_page_number(0) is None
    assert validate_source_page_number('abc') is None
    assert validate_source_page_number(None) is None
    assert validate_source_page_number(5) == 5
    assert validate_source_page_number('42') == 42

    # 13. 来源字段校验：超长值截断
    long_title = 'A' * 600
    assert len(validate_source_section_title(long_title)) == MAX_SOURCE_SECTION_TITLE_LEN
    long_path = 'B' * 3000
    assert len(validate_source_section_path(long_path)) == MAX_SOURCE_SECTION_PATH_LEN
    long_doc = 'C' * 300
    assert len(validate_source_document_name(long_doc)) == MAX_SOURCE_DOCUMENT_NAME_LEN

    # 14. 来源字段校验：章节序号非法值
    assert validate_source_section_index(-1) is None
    assert validate_source_section_index('xyz') is None
    assert validate_source_section_index(0) == 0
    assert validate_source_section_index(7) == 7

    # 15. 同名标题不复制：title == source_section_title 时各字段独立，不互相覆盖
    sl_same = {**sl, 'source_section_title': '售后服务方案'}
    row_same = build_row(sl_same, p1)
    assert row_same[2] == '售后服务方案'  # scoring_point_title 仍是 title
    assert row_same[12] == '售后服务方案'  # source_section_title 独立写入
    assert row_same[3] == '【历史标书切片】售后服务方案'  # requirement_raw 不受来源字段影响

    # 16. extract_source_fields：从 JSONL 记录提取并校验
    rec_full = {
        'source_section_title': '项目实施',
        'source_section_path': '技术方案 > 项目实施',
        'source_document_name': '技术标.docx',
        'source_page_number': 10,
        'source_section_index': 2,
    }
    src = extract_source_fields(rec_full)
    assert src == {'source_section_title': '项目实施', 'source_section_path': '技术方案 > 项目实施',
                   'source_document_name': '技术标.docx', 'source_page_number': 10,
                   'source_section_index': 2}
    rec_empty = {}
    src_empty = extract_source_fields(rec_empty)
    assert all(v is None for v in src_empty.values())
    rec_mixed = {'source_section_title': '', 'source_page_number': -5, 'source_section_index': 0}
    src_mixed = extract_source_fields(rec_mixed)
    assert src_mixed['source_section_title'] is None
    assert src_mixed['source_page_number'] is None
    assert src_mixed['source_section_index'] == 0

    print('self-test passed: 16 组断言全部通过')


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
    ap.add_argument('--backfill-meta', action='store_true',
                    help='一次性回填存量切片案例的 bid_result/project_type（配合 --dry-run 预览）')
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

    if args.backfill_meta:
        do_backfill_meta(args)
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

