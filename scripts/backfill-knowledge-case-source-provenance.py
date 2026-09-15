#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
存量案例来源标题回填脚本（仅限本地 / 西域测试环境）
====================================================
扫描 knowledge_case 中来源字段为空的记录，以 response_text 在原始 JSONL 做
空白归一化精确匹配，结合 Markdown 标题索引确定最近有效章节标题。
仅「唯一文本证据命中 + 存在最近标题」时写入五字段；其余保持 NULL。

绝不复制 scoring_point_title 作为 source_section_title。

环境约束（硬守卫）：
  仅允许连接 127.0.0.1 / localhost / 172.16.38.78 / winbid-01.test.rds.ehsy.com

用法:
  # 只读扫描，不写库
  python3 backfill-knowledge-case-source-provenance.py --dry-run \\
      --jsonl-dir /tmp/winbid_slices --markdown-dir /tmp/winbid_md

  # 确认报告后执行回填（事务 + 审计清单）
  python3 backfill-knowledge-case-source-provenance.py --apply \\
      --jsonl-dir /tmp/winbid_slices --markdown-dir /tmp/winbid_md

参数:
  --dry-run        只读扫描，输出统计与样例，不写库
  --apply          事务写入，前后计数 + 可逆审计清单
  --jsonl-dir      切片 JSONL 目录（默认 /tmp/winbid_slices）
  --markdown-dir   标书 Markdown 目录（可选；无则来源标题保持 NULL）
  --db-host/port/user/pass/name  数据库连接（沿用既有脚本风格）
"""
import argparse
import json
import os
import re
import sys

ALLOWED_DB_HOSTS = {'127.0.0.1', 'localhost', '172.16.38.78', 'winbid-01.test.rds.ehsy.com'}

HEADING_RE = re.compile(
    r'^(?:(#{1,6})\s+(.+)'
    r'|((?:第[一二三四五六七八九十百千\d]+[章节条款 partPart])\s*.*)'
    r'|((\d+(?:\.\d+)*)[)\.、．]\s+.+))$'
)

UPDATE_SQL = """
    UPDATE knowledge_case
    SET source_section_title = %s,
        source_section_path = %s,
        source_document_name = %s,
        source_page_number = %s,
        source_section_index = %s
    WHERE id = %s
"""


def guard_db_host(db_host):
    if db_host not in ALLOWED_DB_HOSTS:
        sys.exit(
            f'拒绝连接：--db-host "{db_host}" 不在允许清单 {sorted(ALLOWED_DB_HOSTS)} 内。'
        )


def normalize_ws(text):
    if not text:
        return ''
    return re.sub(r'\s+', ' ', str(text)).strip()


def parse_markdown_headings(content):
    headings = []
    idx = 0
    ancestor_stack = []
    char_offset = 0
    for line in content.split('\n'):
        stripped = line.strip()
        m = HEADING_RE.match(stripped)
        if m:
            if m.group(1):
                level = len(m.group(1))
                title = m.group(2).strip()
            elif m.group(3):
                level = 1
                title = m.group(3).strip()
            elif m.group(4):
                num = m.group(5)
                level = num.count('.') + 1
                title = stripped
            else:
                level = 0
                title = None
            if level and title:
                while ancestor_stack and ancestor_stack[-1][0] >= level:
                    ancestor_stack.pop()
                ancestor_stack.append((level, title))
                path = ' > '.join(t for _, t in ancestor_stack)
                headings.append({
                    'index': idx,
                    'char_start': char_offset,
                    'level': level,
                    'title': title,
                    'path': path,
                })
                idx += 1
        char_offset += len(line) + 1
    return headings


def find_nearest_heading(headings, match_char_offset):
    best = None
    for h in headings:
        if h['char_start'] <= match_char_offset:
            best = h
        else:
            break
    return best


def build_norm_pos_map(content):
    """Build normalized content (matching normalize_ws) and a position map
    from normalized-index → original-index.
    normalize_ws collapses whitespace runs to single space and strips edges.
    """
    norm_chars = []
    norm_to_orig = []
    in_ws = True
    for i, ch in enumerate(content):
        if ch.isspace():
            if not in_ws and norm_chars:
                norm_chars.append(' ')
                norm_to_orig.append(i)
                in_ws = True
        else:
            norm_chars.append(ch)
            norm_to_orig.append(i)
            in_ws = False
    while norm_chars and norm_chars[-1] == ' ':
        norm_chars.pop()
        norm_to_orig.pop()
    return ''.join(norm_chars), norm_to_orig


def find_loose_match(content, response_text):
    norm_content, norm_to_orig = build_norm_pos_map(content)
    norm_resp = normalize_ws(response_text)
    if not norm_resp:
        return None, None, norm_content, norm_to_orig
    pos = norm_content.find(norm_resp)
    if pos >= 0 and pos < len(norm_to_orig):
        return norm_to_orig[pos], pos, norm_content, norm_to_orig
    short = norm_resp[:min(200, len(norm_resp))]
    if short:
        pos = norm_content.find(short)
        if pos >= 0 and pos < len(norm_to_orig):
            return norm_to_orig[pos], pos, norm_content, norm_to_orig
    return None, None, norm_content, norm_to_orig


def load_jsonl_index(jsonl_dir):
    text_index = {}
    if not jsonl_dir or not os.path.isdir(jsonl_dir):
        return text_index
    count = 0
    for fname in sorted(os.listdir(jsonl_dir)):
        if not re.match(r'^project_\d+\.jsonl$', fname):
            continue
        with open(os.path.join(jsonl_dir, fname), encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    rec = json.loads(line)
                except ValueError:
                    continue
                text = (rec.get('text') or rec.get('text_preview') or '').strip()
                if not text:
                    continue
                norm = normalize_ws(text)
                if norm not in text_index:
                    text_index[norm] = []
                text_index[norm].append({
                    'project': (rec.get('project') or '').strip(),
                    'obs_key': (rec.get('obs_bid_file_key') or '').strip(),
                    'title': (rec.get('title') or '').strip(),
                    'docx_label': (rec.get('docx_label') or '').strip(),
                })
                count += 1
    print(f'JSONL 索引: {count} 条切片，{len(text_index)} 个唯一文本')
    return text_index


def find_markdown_file(markdown_dir, obs_key):
    if not markdown_dir or not obs_key:
        return None
    for ext in ('.md', '.markdown', '.txt'):
        candidate = os.path.join(markdown_dir, os.path.splitext(obs_key)[0] + ext)
        if os.path.isfile(candidate):
            return candidate
    return None


def search_in_markdown(md_path, response_text):
    if not md_path or not os.path.isfile(md_path):
        return None
    with open(md_path, encoding='utf-8') as f:
        content = f.read()
    headings = parse_markdown_headings(content)
    orig_pos, norm_pos, _, _ = find_loose_match(content, response_text)
    if orig_pos is None:
        return {'headings': headings, 'match': None}
    nearest = find_nearest_heading(headings, orig_pos)
    return {'headings': headings, 'match': {'orig_pos': orig_pos, 'nearest': nearest}}


def process_cases(cases, text_index, markdown_dir):
    results = []
    for case in cases:
        resp = (case.get('response_text') or '').strip()
        if not resp:
            results.append({**case, '_status': 'no_response_text'})
            continue
        norm = normalize_ws(resp)
        matches = text_index.get(norm, [])
        if not matches:
            results.append({**case, '_status': 'no_match', '_matches': []})
            continue
        unique_projects = set(m['project'] for m in matches)
        unique_obs_keys = set(m['obs_key'] for m in matches)
        if len(matches) > 1 and (len(unique_projects) > 1 or len(unique_obs_keys) > 1):
            results.append({
                **case, '_status': 'cross_section_conflict',
                '_matches': matches, '_conflict_reason': '多项目或多文件命中',
            })
            continue
        matched = matches[0]
        obs_key = case.get('obs_bid_file_key') or matched.get('obs_key') or ''
        md_path = find_markdown_file(markdown_dir, obs_key)
        if not md_path:
            results.append({
                **case, '_status': 'file_missing',
                '_matched': matched, '_obs_key': obs_key,
            })
            continue
        md_result = search_in_markdown(md_path, resp)
        if md_result is None:
            results.append({**case, '_status': 'file_missing', '_matched': matched})
            continue
        nearest = md_result['match']['nearest'] if md_result['match'] else None
        if nearest is None and md_result['headings']:
            results.append({
                **case, '_status': 'no_nearest_heading',
                '_matched': matched, '_headings_count': len(md_result['headings']),
            })
            continue
        doc_name = os.path.basename(obs_key) if obs_key else None
        results.append({
            **case,
            '_status': 'updatable',
            '_matched': matched,
            '_source_section_title': nearest['title'] if nearest else None,
            '_source_section_path': nearest['path'] if nearest else None,
            '_source_section_index': nearest['index'] if nearest else None,
            '_source_document_name': doc_name,
            '_source_page_number': None,
        })
    return results


def load_cases(conn):
    with conn.cursor() as cur:
        cur.execute("""
            SELECT id, response_text, scoring_point_title, obs_bid_file_key
            FROM knowledge_case
            WHERE source_section_title IS NULL
              AND response_text IS NOT NULL
              AND response_text != ''
        """)
        rows = cur.fetchall()
    return [
        {'id': r[0], 'response_text': r[1], 'scoring_point_title': r[2],
         'obs_bid_file_key': r[3]}
        for r in rows
    ]


def do_dry_run(args):
    import pymysql
    guard_db_host(args.db_host)
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        cases = load_cases(conn)
    finally:
        conn.close()
    print(f'待扫描案例: {len(cases)} 条')
    text_index = load_jsonl_index(args.jsonl_dir)
    results = process_cases(cases, text_index, args.markdown_dir)

    stats = {}
    for r in results:
        s = r['_status']
        stats[s] = stats.get(s, 0) + 1

    print(f'\n=== 回填 dry-run 报告 ===')
    print(f'可更新（唯一命中 + 有最近标题）: {stats.get("updatable", 0)}')
    print(f'无文本匹配: {stats.get("no_match", 0)}')
    print(f'跨章节/多文件冲突: {stats.get("cross_section_conflict", 0)}')
    print(f'文件缺失: {stats.get("file_missing", 0)}')
    print(f'无最近标题: {stats.get("no_nearest_heading", 0)}')
    print(f'无应答文本: {stats.get("no_response_text", 0)}')

    updatable = [r for r in results if r['_status'] == 'updatable']
    if updatable:
        print(f'\n--- 可更新样例（前 10 条）---')
        for r in updatable[:10]:
            resp_preview = (r.get('response_text') or '')[:60]
            print(f'  case_id={r["id"]} | title={r.get("scoring_point_title", "")[:30]}'
                  f' | source_title={r["_source_section_title"][:40]}'
                  f' | doc={r["_source_document_name"]}'
                  f' | evidence="{resp_preview}..."')

    conflicts = [r for r in results if r['_status'] == 'cross_section_conflict']
    if conflicts:
        print(f'\n--- 冲突样例（前 5 条）---')
        for r in conflicts[:5]:
            n = len(r.get('_matches', []))
            print(f'  case_id={r["id"]} | matches={n}'
                  f' | reason={r.get("_conflict_reason", "")}'
                  f' | evidence="{(r.get("response_text") or "")[:60]}..."')

    missing = [r for r in results if r['_status'] == 'file_missing']
    if missing:
        print(f'\n--- 文件缺失样例（前 5 条）---')
        for r in missing[:5]:
            print(f'  case_id={r["id"]} | obs_key={r.get("_obs_key", "")[:60]}')

    print(f'\n[dry-run] 未写库。确认无误后使用 --apply 执行回填。')


def do_apply(args):
    import pymysql
    guard_db_host(args.db_host)
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        cases = load_cases(conn)
        text_index = load_jsonl_index(args.jsonl_dir)
        results = process_cases(cases, text_index, args.markdown_dir)
        updatable = [r for r in results if r['_status'] == 'updatable']

        if not updatable:
            print('无可更新记录（无唯一命中 + 最近标题的组合）')
            return

        with conn.cursor() as cur:
            cur.execute('SELECT COUNT(*) FROM knowledge_case WHERE source_section_title IS NOT NULL')
            before = cur.fetchone()[0]

        conn.begin()
        updated = 0
        audit = []
        with conn.cursor() as cur:
            for r in updatable:
                cur.execute(UPDATE_SQL, (
                    r['_source_section_title'],
                    r['_source_section_path'],
                    r['_source_document_name'],
                    r['_source_page_number'],
                    r['_source_section_index'],
                    r['id'],
                ))
                updated += cur.rowcount
                audit.append({
                    'case_id': r['id'],
                    'source_section_title': r['_source_section_title'],
                    'source_section_path': r['_source_section_path'],
                    'source_document_name': r['_source_document_name'],
                    'source_page_number': r['_source_page_number'],
                    'source_section_index': r['_source_section_index'],
                    'scoring_point_title': r.get('scoring_point_title'),
                    'evidence_preview': (r.get('response_text') or '')[:100],
                })
        conn.commit()

        with conn.cursor() as cur:
            cur.execute('SELECT COUNT(*) FROM knowledge_case WHERE source_section_title IS NOT NULL')
            after = cur.fetchone()[0]

        print(f'\n=== 回填完成 ===')
        print(f'回填前 source_section_title 非空: {before}')
        print(f'本次更新: {updated}')
        print(f'回填后 source_section_title 非空: {after}')

        print(f'\n=== 审计清单（可逆，前 20 条）===')
        for a in audit[:20]:
            print(f'  case_id={a["case_id"]}'
                  f' | scoring="{(a["scoring_point_title"] or "")[:25]}"'
                  f' → source="{(a["source_section_title"] or "")[:35]}"'
                  f' | path={a["source_section_path"]}'
                  f' | doc={a["source_document_name"]}'
                  f' | idx={a["source_section_index"]}')
        if len(audit) > 20:
            print(f'  ... 其余 {len(audit) - 20} 条省略')

        print(f'\n回滚: UPDATE knowledge_case SET source_section_title=NULL,'
              f' source_section_path=NULL, source_document_name=NULL,'
              f' source_page_number=NULL, source_section_index=NULL'
              f' WHERE id IN ({",".join(str(a["case_id"]) for a in audit)});')
    finally:
        conn.close()


def _self_test():
    # 1. parse_markdown_headings: char_start 是原文字符偏移
    content = "## 章一\n唯一短语\n" + ("## 后续章\n正文\n" * 200)
    headings = parse_markdown_headings(content)
    assert len(headings) == 201
    assert headings[0]['title'] == '章一'
    assert headings[0]['char_start'] == 0
    assert headings[0]['index'] == 0
    assert headings[1]['title'] == '后续章'
    assert headings[1]['char_start'] > headings[0]['char_start']
    assert headings[1]['index'] == 1

    # 2. 命中在文档开头但后面有很多标题 → 返回「章一」而非靠后的「后续章」
    orig_pos, _, _, _ = find_loose_match(content, "唯一短语")
    assert orig_pos is not None
    nearest = find_nearest_heading(headings, orig_pos)
    assert nearest is not None
    assert nearest['title'] == '章一', f'期望「章一」，实际「{nearest["title"]}」'

    # 3. 命中在无标题段落（在两个标题之间）→ 返回最近的上级标题
    content2 = "# 上级\n\n正文段落\n\n## 下级\n\n目标文本在这里\n\n## 另一个\n\n结尾"
    headings2 = parse_markdown_headings(content2)
    assert len(headings2) == 3
    assert headings2[0]['title'] == '上级'
    assert headings2[1]['title'] == '下级'
    assert headings2[2]['title'] == '另一个'
    orig_pos2, _, _, _ = find_loose_match(content2, "目标文本在这里")
    assert orig_pos2 is not None
    nearest2 = find_nearest_heading(headings2, orig_pos2)
    assert nearest2 is not None
    assert nearest2['title'] == '下级', f'期望「下级」，实际「{nearest2["title"]}」'

    # 4. 找不到匹配 → None
    orig_pos3, _, _, _ = find_loose_match(content2, "完全不存在的文本XYZ")
    assert orig_pos3 is None

    # 5. 空白归一化匹配：原文多空格/换行，搜索串单空格
    content3 = "## 标题\n\n这段    文字\n包含   多个   空格\n\n## 后续\n"
    headings3 = parse_markdown_headings(content3)
    orig_pos4, _, _, _ = find_loose_match(content3, "这段 文字 包含 多个 空格")
    assert orig_pos4 is not None
    nearest4 = find_nearest_heading(headings3, orig_pos4)
    assert nearest4['title'] == '标题'

    # 6. 短前缀回退也能映射到原文坐标
    content4 = "## 章一\n这是一段非常长的正文内容需要验证短前缀回退机制\n## 章二\n"
    headings4 = parse_markdown_headings(content4)
    orig_pos5, _, _, _ = find_loose_match(content4, "这是一段非常长")
    assert orig_pos5 is not None
    nearest5 = find_nearest_heading(headings4, orig_pos5)
    assert nearest5['title'] == '章一'

    # 7. 路径层级正确
    content5 = "# 一级\n## 二级\n### 三级\n文本\n"
    headings5 = parse_markdown_headings(content5)
    assert headings5[2]['path'] == '一级 > 二级 > 三级'
    orig_pos6, _, _, _ = find_loose_match(content5, "文本")
    nearest6 = find_nearest_heading(headings5, orig_pos6)
    assert nearest6['title'] == '三级'
    assert nearest6['path'] == '一级 > 二级 > 三级'

    # 8. 无标题文档 → find_nearest_heading 返回 None
    content6 = "没有任何标题的纯正文内容"
    headings6 = parse_markdown_headings(content6)
    assert len(headings6) == 0
    assert find_nearest_heading(headings6, 0) is None

    # 9. build_norm_pos_map 坐标映射正确（与 normalize_ws 语义一致：空白折叠为单空格）
    text = "a  b\n\nc"
    norm, mapping = build_norm_pos_map(text)
    assert norm == 'a b c'
    assert mapping[0] == 0   # 'a' → orig 0
    assert mapping[1] == 1   # ' ' → orig 1 (空白折叠，取首个空白字符位置)
    assert mapping[2] == 3   # 'b' → orig 3
    assert mapping[3] == 4   # ' ' → orig 4 (换行折叠)
    assert mapping[4] == 6   # 'c' → orig 6

    print('backfill self-test passed: 9 组断言全部通过')


def main():
    ap = argparse.ArgumentParser(
        description='存量案例来源标题回填（仅本地/西域测试环境）',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 只读扫描
  python3 %(prog)s --dry-run --jsonl-dir /tmp/winbid_slices --markdown-dir /tmp/winbid_md

  # 执行回填
  python3 %(prog)s --apply --jsonl-dir /tmp/winbid_slices --markdown-dir /tmp/winbid_md

说明:
  --dry-run  只读扫描真实案例与原始 JSONL/Markdown，输出统计与样例，不写库
  --apply    事务 + 参数化 SQL + 前后计数 + 可逆审计清单；仅对「唯一文本证据
             命中且存在最近标题」的记录写入五字段；无命中/跨章节多命中/文件缺失
             /仅有评分项标题或 docx_label 时保持 NULL；绝不复制 scoring_point_title
        """,
    )
    mode = ap.add_mutually_exclusive_group(required=True)
    mode.add_argument('--dry-run', action='store_true', help='只读扫描，不写库')
    mode.add_argument('--apply', action='store_true', help='事务写入回填')
    mode.add_argument('--self-test', action='store_true', help='运行离线自检断言后退出')
    ap.add_argument('--jsonl-dir', default='/tmp/winbid_slices',
                    help='切片 JSONL 目录（默认 /tmp/winbid_slices）')
    ap.add_argument('--markdown-dir', default=None,
                    help='标书 Markdown 目录（可选；无则来源标题保持 NULL）')
    ap.add_argument('--db-host', default='127.0.0.1')
    ap.add_argument('--db-port', type=int, default=3306)
    ap.add_argument('--db-user', default='xiyu_user')
    ap.add_argument('--db-pass', default='XiyuDB!2026')
    ap.add_argument('--db-name', default='xiyu_bid_main')
    args = ap.parse_args()

    if args.self_test:
        _self_test()
    elif args.dry_run:
        do_dry_run(args)
    else:
        do_apply(args)


if __name__ == '__main__':
    main()
