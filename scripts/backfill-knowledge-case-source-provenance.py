#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""存量案例来源标题回填 CLI；匹配和数据库职责位于同目录模块。"""
import argparse
import os
import tempfile

from backfill_knowledge_case_source_provenance_db import UPDATE_SQL, connect, load_cases
from backfill_knowledge_case_source_provenance_matching import (
    build_norm_pos_map, find_loose_match, find_markdown_file, find_nearest_heading,
    load_jsonl_index, normalize_ws, parse_markdown_headings, process_cases,
)


def _password(value):
    return value if value is not None else os.environ.get('XIYU_DB_PASS')


def do_dry_run(args):
    conn = connect(args)
    try:
        cases = load_cases(conn)
    finally:
        conn.close()
    results = process_cases(cases, load_jsonl_index(args.jsonl_dir), args.markdown_dir)
    stats = {}
    for result in results:
        stats[result['_status']] = stats.get(result['_status'], 0) + 1
    print(f'待扫描案例: {len(cases)} 条')
    print('\n=== 回填 dry-run 报告 ===')
    labels = [('updatable', '可更新（唯一命中 + 有最近标题）'),
              ('insufficient_evidence', '证据不足（短前缀/评分项标题，禁止写库）'),
              ('no_match', '无文本匹配'), ('cross_section_conflict', '跨章节/多文件冲突'),
              ('file_missing', '文件缺失'), ('no_nearest_heading', '无最近标题'),
              ('no_response_text', '无应答文本')]
    for key, label in labels:
        print(f'{label}: {stats.get(key, 0)}')
    print('\n[dry-run] 未写库。确认无误后使用 --apply 执行回填。')


def do_apply(args):
    conn = connect(args)
    try:
        cases = load_cases(conn)
        results = process_cases(cases, load_jsonl_index(args.jsonl_dir), args.markdown_dir)
        updatable = [result for result in results if result['_status'] == 'updatable']
        if not updatable:
            print('无可更新记录（无唯一命中 + 最近标题的组合）')
            return
        with conn.cursor() as cursor:
            cursor.execute('SELECT COUNT(*) FROM knowledge_case WHERE source_section_title IS NOT NULL')
            before = cursor.fetchone()[0]
        conn.begin()
        updated = 0
        with conn.cursor() as cursor:
            for result in updatable:
                cursor.execute(UPDATE_SQL, (result['_source_section_title'], result['_source_section_path'],
                                            result['_source_document_name'], result['_source_page_number'],
                                            result['_source_section_index'], result['id']))
                updated += cursor.rowcount
        conn.commit()
        with conn.cursor() as cursor:
            cursor.execute('SELECT COUNT(*) FROM knowledge_case WHERE source_section_title IS NOT NULL')
            after = cursor.fetchone()[0]
        print(f'回填前 source_section_title 非空: {before}')
        print(f'本次更新: {updated}')
        print(f'回填后 source_section_title 非空: {after}')
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def _self_test():
    content = "## 章一\n唯一短语\n" + ("## 后续章\n正文\n" * 20)
    headings = parse_markdown_headings(content)
    assert len(headings) == 21 and headings[0]['char_start'] == 0
    orig_pos, _, _, _ = find_loose_match(content, '唯一短语')
    assert find_nearest_heading(headings, orig_pos)['title'] == '章一'

    content2 = '# 一级\n## 二级\n### 三级\n文本\n'
    headings2 = parse_markdown_headings(content2)
    assert headings2[2]['level'] == 3 and headings2[2]['path'] == '一级 > 二级 > 三级'

    content3 = '# 第一章\n一、 中文章节\n第2节 服务\n1.1 数字\n（1）条目\n###### 末级\n正文'
    actual = parse_markdown_headings(content3)
    expected = [(1, '第一章'), (2, '一、 中文章节'), (2, '第2节 服务'),
                (3, '1.1 数字'), (4, '（1）条目'), (6, '末级')]
    assert [(item['level'], item['title']) for item in actual] == expected
    assert actual[4]['path'] == '第一章 > 第2节 服务 > 1.1 数字 > （1）条目'

    short = '## A\n' + ('前缀' * 100)
    response = short[5:] + '完整尾部'
    with tempfile.TemporaryDirectory() as tmp_dir:
        obs_key = 'P/doc.docx'
        path = os.path.join(tmp_dir, 'P/doc.md')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8') as handle:
            handle.write('# A\n' + short[5:] + '\n')
        base = {'id': 1, 'response_text': response, 'scoring_point_title': '技术方案标题',
                'obs_bid_file_key': obs_key}
        source = [{'project': 'P', 'obs_key': obs_key, 'title': 'x', 'docx_label': 'x'}]
        index = {normalize_ws(response): source}
        assert process_cases([base], index, tmp_dir)[0]['_status'] == 'insufficient_evidence'
        full = {**base, 'id': 2, 'response_text': '正文证据', 'scoring_point_title': ' 技术方案标题 '}
        with open(path, 'w', encoding='utf-8') as handle:
            handle.write('# A\n技术方案标题\n正文证据\n')
        index['正文证据'] = source
        assert process_cases([full], index, tmp_dir)[0]['_status'] == 'updatable'

    norm, mapping = build_norm_pos_map('a  b\n\nc')
    assert norm == 'a b c' and mapping == [0, 1, 3, 4, 6]
    print('backfill self-test passed: 6 组断言全部通过')


def main():
    parser = argparse.ArgumentParser(description='存量案例来源标题回填（仅本地/西域测试环境）')
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--dry-run', action='store_true', help='只读扫描，不写库')
    mode.add_argument('--apply', action='store_true', help='事务写入回填')
    mode.add_argument('--self-test', action='store_true', help='运行离线自检断言后退出')
    parser.add_argument('--jsonl-dir', default='/tmp/winbid_slices')
    parser.add_argument('--markdown-dir', default=None)
    parser.add_argument('--db-host', default='127.0.0.1')
    parser.add_argument('--db-port', type=int, default=3306)
    parser.add_argument('--db-user', default='xiyu_user')
    parser.add_argument('--db-pass', default=None, help='数据库密码；优先使用此参数，否则读取 XIYU_DB_PASS（无明文默认）')
    parser.add_argument('--db-name', default='xiyu_bid_main')
    args = parser.parse_args()
    args.db_pass = _password(args.db_pass)
    if args.self_test:
        _self_test()
        return
    if not args.db_pass:
        parser.error('缺少数据库密码：请设置 XIYU_DB_PASS，或传入 --db-pass；不会回显密码')
    (do_dry_run if args.dry_run else do_apply)(args)


if __name__ == '__main__':
    main()
