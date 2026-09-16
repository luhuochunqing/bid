#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
knowledge_case 来源标题与文件名安全回填脚本（测试环境与本地）
=========================================================================
功能：
1. 回填 300 条历史标书切片案例的来源章节标题、路径与来源文档文件名（来自 obs_bid_file_key 与 scoring_point_title）。
2. 回填 49 条真实结项/导入案例的来源章节标题、路径（智能从正文或评分项提取）与来源文档文件名（关联 project_archive + archive_file）。

环境约束（硬守卫）：
  仅允许连接 127.0.0.1 / localhost / 172.16.38.78 / winbid-01.test.rds.ehsy.com。
"""

import argparse
import os
import re
import sys

ALLOWED_DB_HOSTS = {'127.0.0.1', 'localhost', '172.16.38.78', 'winbid-01.test.rds.ehsy.com'}


def extract_provenance_title(resp, scoring):
    """
    智能提取来源章节标题，与前端 CaseDetailDialog / caseDetailFormatter 契约一致：
    1. 标题标记列表/章节：'# 标题' 或 '* 1. 质量管理方案\n 1. 商品安全溯源'
    2. 详见引用：'详见七、服务方案-（二）、供货方案'
    3. 书名号方案：'《突发事件防汛防灾及双活容灾应急预案》'
    4. 兜底评分项标题
    """
    if not resp:
        return scoring or ''

    # 1. 标题标记列表/章节
    m1 = re.search(
        r'^\s*(?:#{1,6}\s+|[*+\-]\s+(?:\d+[.、]\s*)?|\d+[.、]\s+)([^\n\r]+)(?:\n\s*(?:#{1,6}\s+|[*+\-]\s+(?:\d+[.、]\s*)?|\d+[.、]\s+)([^\n\r]+))?',
        resp
    )
    if m1:
        l1 = re.sub(r'^[#*+\-\s\d.、]+', '', m1.group(1))
        l1 = re.sub(r'[*_`]', '', l1).strip()
        l2 = m1.group(2)
        if l2:
            l2 = re.sub(r'^[#*+\-\s\d.、]+', '', l2)
            l2 = re.sub(r'[*_`]', '', l2).strip()
        if l1 and 2 <= len(l1) <= 40 and not any(c in l1 for c in ['。', '，', '|']):
            if l2 and 2 <= len(l2) <= 40 and not any(c in l2 for c in ['。', '，', '|']) and not l2.startswith(('为', '本')):
                return f'{l1} > {l2}'
            return l1

    # 2. 详见引用
    m2 = re.search(
        r'详见[，,]?\s*([一二三四五六七八九十0-9a-zA-Z\u4e00-\u9fa5（）()、—\-_]+(?:方案|简历|配备|说明|表|章节|条款|内容|部分|计划|业绩))',
        resp,
        re.IGNORECASE
    )
    if m2:
        cleaned = re.sub(r'[-—_]', ' > ', m2.group(1))
        cleaned = re.sub(r'[）)][、，]', '） > ', cleaned)
        cleaned = re.sub(r'^[（(]|\**$', '', cleaned).strip()
        if 2 <= len(cleaned) <= 50:
            return cleaned

    # 3. 《...方案/预案》
    m3 = re.search(r'《([^》]{2,40}(?:方案|预案|设计|规范|体系|计划|报告|指南|架构))》', resp)
    if m3:
        return m3.group(1).strip()

    return scoring or ''


def connect_db(args):
    if args.db_host not in ALLOWED_DB_HOSTS:
        sys.exit(f'❌ 安全拒绝：主机 {args.db_host} 不在允许白名单 {ALLOWED_DB_HOSTS} 内')

    try:
        import mysql.connector
        return mysql.connector.connect(
            host=args.db_host,
            port=args.db_port,
            user=args.db_user,
            password=args.db_pass,
            database=args.db_name,
            autocommit=False
        )
    except Exception as e:
        sys.exit(f'❌ 数据库连接失败: {e}')


def run_backfill(args):
    conn = connect_db(args)
    cursor = conn.cursor(dictionary=True)

    print(f'🔍 正在查询 {args.db_host}:{args.db_port}/{args.db_name} 当前数据状态...')
    cursor.execute('''
        SELECT 
            count(*) as total,
            count(source_section_title) as has_title,
            count(source_section_path) as has_path,
            count(source_document_name) as has_doc_name,
            count(obs_bid_file_key) as has_obs_key
        FROM knowledge_case;
    ''')
    stats_before = cursor.fetchone()
    print('📊 回填前状态：', stats_before)

    # 查询所有案例及归档关联
    cursor.execute('''
        SELECT 
            kc.id,
            kc.source_project_id,
            kc.source_project_name,
            kc.scoring_point_title,
            kc.requirement_raw,
            kc.response_text,
            kc.obs_bid_file_key,
            kc.source_section_title,
            kc.source_section_path,
            kc.source_document_name,
            af.file_name as archive_file_name
        FROM knowledge_case kc
        LEFT JOIN project_archive pa ON kc.source_project_id = pa.project_id
        LEFT JOIN (
            SELECT archive_id, MIN(file_name) as file_name
            FROM archive_file
            WHERE document_category IN ('BID', 'BID_FILE')
            GROUP BY archive_id
        ) af ON pa.id = af.archive_id
        ORDER BY kc.id ASC;
    ''')
    rows = cursor.fetchall()
    print(f'📦 读取到 {len(rows)} 条案例记录')

    updates = []
    for r in rows:
        case_id = r['id']
        req_raw = r['requirement_raw'] or ''
        is_slice = '【历史标书切片】' in req_raw
        scoring = (r['scoring_point_title'] or '').strip()
        resp = r['response_text'] or ''
        obs_key = r['obs_bid_file_key'] or ''
        af_name = r['archive_file_name'] or ''

        # 1. 确定文档名称
        if is_slice:
            doc_name = obs_key.split('/')[-1] if obs_key else (r['source_document_name'] or '标书文件.docx')
        else:
            doc_name = af_name or (obs_key.split('/')[-1] if obs_key else (r['source_document_name'] or f"{r['source_project_name']}-投标文件.docx"))

        # 2. 确定来源章节标题与路径
        if is_slice:
            sec_path = scoring
            sec_title = scoring
        else:
            extracted = extract_provenance_title(resp, scoring)
            if ' > ' in extracted:
                sec_path = extracted
                sec_title = extracted.split(' > ')[-1].strip()
            else:
                sec_path = extracted
                sec_title = extracted

        # 兜底确保非空
        if not sec_title:
            sec_title = scoring or '正文'
        if not sec_path:
            sec_path = sec_title
        if not doc_name:
            doc_name = f"{r['source_project_name']}-投标文件.docx"

        updates.append({
            'id': case_id,
            'title': sec_title,
            'path': sec_path,
            'doc': doc_name,
            'is_slice': is_slice,
            'scoring': scoring
        })

    print(f'📝 准备更新 {len(updates)} 条记录：')
    sample_count = min(5, len(updates))
    print(f'--- 样例（前 {sample_count} 条）---')
    for u in updates[:sample_count]:
        print(f"ID {u['id']:4d} | 类型: {'切片' if u['is_slice'] else '项目'} | 评分项: {u['scoring'][:15]:15s} | 来源标题: {u['title'][:20]:20s} | 文档: {u['doc']}")

    if args.dry_run:
        print('\n⚠️ --dry-run 模式：未执行写入与提交。')
        cursor.close()
        conn.close()
        return

    # 执行批量安全更新
    print(f'\n🚀 开始写入数据库 {args.db_host}...')
    update_sql = '''
        UPDATE knowledge_case
        SET source_section_title = %s,
            source_section_path = %s,
            source_document_name = %s
        WHERE id = %s
    '''
    batch_params = [(u['title'], u['path'], u['doc'], u['id']) for u in updates]
    cursor.executemany(update_sql, batch_params)
    conn.commit()
    print(f'✅ 已成功更新并提交 {len(batch_params)} 条记录！')

    # 校验更新后状态
    cursor.execute('''
        SELECT 
            count(*) as total,
            count(source_section_title) as has_title,
            count(source_section_path) as has_path,
            count(source_document_name) as has_doc_name,
            count(obs_bid_file_key) as has_obs_key
        FROM knowledge_case;
    ''')
    stats_after = cursor.fetchone()
    print('\n🎉 回填后状态校验：', stats_after)

    cursor.close()
    conn.close()


def main():
    parser = argparse.ArgumentParser(description='knowledge_case 来源标题与文件名安全回填脚本')
    parser.add_argument('--db-host', default=os.getenv('XIYU_DB_HOST', 'winbid-01.test.rds.ehsy.com'), help='数据库主机')
    parser.add_argument('--db-port', type=int, default=int(os.getenv('XIYU_DB_PORT', '3306')), help='端口')
    parser.add_argument('--db-user', default=os.getenv('XIYU_DB_USER', 'ea_bid'), help='用户名')
    parser.add_argument('--db-pass', default=os.getenv('XIYU_DB_PASS', 'ra(D7np+Z'), help='密码')
    parser.add_argument('--db-name', default=os.getenv('XIYU_DB_NAME', 'winbid'), help='数据库名')
    parser.add_argument('--dry-run', action='store_true', help='演练模式，不实际写入')
    parser.add_argument('--apply', action='store_true', help='确认执行写入')

    args = parser.parse_args()

    if not args.dry_run and not args.apply:
        print('提示：未指定 --apply 或 --dry-run，默认进入 --dry-run 演练模式以保安全。')
        args.dry_run = True

    run_backfill(args)


if __name__ == '__main__':
    main()
