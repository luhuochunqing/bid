#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
历史投标项目信息批量导入脚本（生产可用版）
=========================================
从 Excel 采集表批量导入历史投标项目，写入 6 张表：
  tenders / projects / project_initiation_details / project_result / project_archive / project_lead_assignment

修复记录（2026-08-28）：
  1. 营收单位：表格"客户营收(亿)"直接按亿值存储，不再 ×100000000
     （系统 annual_revenue 字段语义就是"亿"，前端列"客户年营收（亿）"直接显示该值）
  2. 项目类型：project_type 同时写入 tenders.project_type（归一化枚举名）和
     project_initiation_details.project_type，避免前端列表"项目类型"列为空

用法:
  python3 import-historical-projects.py <excel路径> <users.tsv路径> [--db-host=] [--db-port=] [--db-user=] [--db-pass=] [--db-name=] [--dry-run]

参数:
  <excel路径>  采集表 xlsx（列顺序见 TEMPLATE_HEADERS，从第 3 行开始读数据）
  <users.tsv>  用户映射文件：id<TAB>姓名，每行一个用户
  --dry-run    只解析不写库，打印将导入的行数和负责人匹配情况

示例:
  python3 import-historical-projects.py '历史投标项目信息采集表.xlsx' users.tsv --dry-run
  python3 import-historical-projects.py '历史投标项目信息采集表.xlsx' users.tsv --db-host=172.16.38.78 --db-pass='xxx'
"""
import argparse
import hashlib
import re
import sys

import openpyxl
import pymysql

# ---------------------------------------------------------------------------
# 采集表列顺序（与 Excel 表头一致，0 基索引）
# ---------------------------------------------------------------------------
COL = {
    'name': 0,            # A 项目名称 *
    'owner': 1,           # B 招标主体 *
    'source_platform': 2, # C 来源平台 *
    'status': 3,          # D 项目状态 *
    'hq_loc': 4,          # E 总部所在地 *
    'cust_type': 5,       # F 客户类型 *
    'proj_type': 6,       # G 项目类型 *
    'priority': 7,        # H 优先级 *
    'revenue': 8,         # I 客户营收(亿)
    'expected_bidders': 9,# J 计划入围供应商数
    'open_time': 10,      # K 开标时间 *
    'bid_month': 11,      # L 投标月份
    'contact_name': 12,   # M 联系人1姓名
    'contact_phone': 13,  # N 联系人1电话
    'project_leader': 14, # O 项目负责人 *
    'bidding_leader': 15, # P 投标负责人 *
    'period_years': 16,   # Q 项目服务周期(年) *
    'period_end': 17,     # R 项目服务周期截止时间 *
    'phase': 18,          # S 项目阶段 *
}

# 跳过名单：负责人为"邹会华"时取下一个（用户需求）
SKIP_LEADER_NAMES = {'邹会华'}

# ---------------------------------------------------------------------------
# 映射表
# ---------------------------------------------------------------------------
CUSTOMER_TYPE = {
    '政府机关/事业单位/高校': 'GOVERNMENT', '政府机关': 'GOVERNMENT', '政府': 'GOVERNMENT',
    '事业单位': 'GOVERNMENT', '高校': 'GOVERNMENT',
    '央企': 'CENTRAL_SOE', '国企': 'CENTRAL_SOE',
    '地方国企': 'LOCAL_SOE',
    '民企': 'PRIVATE',
    '港澳台及外企': 'FOREIGN',
}
PROJECT_TYPE = {'办公': 'OFFICE', '综合': 'COMPREHENSIVE', '集采': 'COLLECTIVE', '工业品': 'INDUSTRIAL', '其他': 'OTHER'}
PRIORITY = {'S级': 'S', 'A级': 'A', 'B级': 'B', 'C级': 'C', 'S': 'S', 'A': 'A', 'B': 'B', 'C': 'C'}

# 项目状态 → (projects.status, stage, tender.status, result_type)
STATUS_MAP = {
    '已中标': ('WON', 'CLOSED', 'WON', 'WON'),
    '中标': ('WON', 'CLOSED', 'WON', 'WON'),
    '未中标': ('LOST', 'CLOSED', 'LOST', 'LOST'),
    '未中': ('LOST', 'CLOSED', 'LOST', 'LOST'),
    '已流标': ('FAILED', 'CLOSED', 'ABANDONED', 'FAILED'),
    '已放弃': ('ABANDONED', 'CLOSED', 'ABANDONED', 'ABANDONED'),
    '流标、弃标、废标': ('FAILED', 'CLOSED', 'ABANDONED', 'FAILED'),
}


def clean(v):
    if v is None:
        return None
    s = str(v).strip()
    return s or None


def norm_status(raw):
    s = clean(raw)
    if not s:
        return None
    # 投标中 → 跳过（用户需求：该 1 条暂不导入）
    # 注：评标中但有终态状态（已中标/未中标/已流标）的行也导入，stage 统一 CLOSED
    if s in ('投标中',):
        return None
    return STATUS_MAP.get(s)


def norm_customer_type(raw):
    ct = clean(raw)
    if not ct:
        return None
    compact = ct.replace(' ', '')
    return CUSTOMER_TYPE.get(ct) or CUSTOMER_TYPE.get(compact)


def norm_project_type(raw):
    pt = clean(raw)
    return PROJECT_TYPE.get(pt) if pt else None


def norm_priority(raw):
    pr = clean(raw)
    return PRIORITY.get(pr) if pr else None


def parse_leaders(raw):
    """解析负责人列："姓名（工号）"、多人用/、,、;分隔。
    返回 (姓名, 工号) 或 None。
    - 多人：取第一个非 SKIP_LEADER_NAMES 者（如"邹会华,蒲菲菲"跳过邹会华取蒲菲菲）
    - 单人（如"邹会华"）：即使在被跳名单中也取该人（不能丢弃唯一负责人）"""
    s = clean(raw)
    if not s:
        return None
    # 拆多人
    parts = [p.strip() for p in re.split(r'[/、,;，；]', s) if p.strip()]
    if not parts:
        return None
    # 单人 → 直接取（跳过名单仅适用于多人组合顺延）
    if len(parts) == 1:
        return _parse_one(parts[0])
    # 多人 → 取第一个非跳过者；若全被跳过，兜底取第一个
    for part in parts:
        name, emp = _parse_one(part)
        if name not in SKIP_LEADER_NAMES:
            return (name, emp)
    return _parse_one(parts[0])


def _parse_one(part):
    """解析单个"姓名（工号）"或纯姓名，返回 (姓名, 工号)。"""
    m = re.match(r'^(.+?)[（(](.+?)[）)]$', part)
    if m:
        return (m.group(1).strip(), m.group(2).strip())
    return (part, None)


def ts(dt):
    if dt is None:
        return None
    if hasattr(dt, 'strftime'):
        return dt.strftime('%Y-%m-%d %H:%M:%S')
    return None


def parse_open_time(v):
    if v is None:
        return None
    if hasattr(v, 'strftime'):
        return v
    s = str(v).strip()
    for fmt in ('%Y-%m-%d %H:%M:%S', '%Y-%m-%d %H:%M', '%Y-%m-%d', '%Y/%m/%d', '%Y年%m月%d日'):
        try:
            import datetime
            return datetime.datetime.strptime(s, fmt)
        except ValueError:
            continue
    return None


def to_num(v):
    if v is None or str(v).strip() == '':
        return None
    try:
        return float(v)
    except (ValueError, TypeError):
        return None


def main():
    ap = argparse.ArgumentParser(description='历史投标项目信息批量导入（生产可用版）')
    ap.add_argument('excel')
    ap.add_argument('users_tsv', nargs='?', default=None,
                    help='可选：id<TAB>姓名 用户映射文件；不传则从目标库 users 表加载')
    ap.add_argument('--db-host', default='127.0.0.1')
    ap.add_argument('--db-port', type=int, default=3306)
    ap.add_argument('--db-user', default='xiyu_user')
    ap.add_argument('--db-pass', default='XiyuDB!2026')
    ap.add_argument('--db-name', default='xiyu_bid_main')
    ap.add_argument('--dry-run', action='store_true', help='只解析不写库')
    args = ap.parse_args()

    # ---- 加载用户映射：姓名 → (user id, employee_number) ----
    # 优先用 tsv（生产用户导出，覆盖真实员工）；再用目标库 users 表补工号。
    # 目标库 users 若包含同一姓名，则以目标库为准（保证 id 与目标库一致）。
    users = {}
    if args.users_tsv:
        for line in open(args.users_tsv, encoding='utf-8'):
            parts = line.rstrip('\n').split('\t')
            if len(parts) >= 2 and parts[1]:
                users.setdefault(parts[1], (parts[0], None))
        print(f'用户映射来自 tsv: {len(users)} 人')

    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        with conn.cursor() as cur:
            cur.execute('SELECT id, full_name, employee_number FROM users')
            db_users = 0
            for uid, full_name, emp in cur.fetchall():
                if full_name:
                    users[full_name] = (str(uid), emp)
                    db_users += 1
        print(f'目标库 users 覆盖/补充: {db_users} 人')
    finally:
        conn.close()

    # ---- 解析 Excel ----
    wb = openpyxl.load_workbook(args.excel, data_only=True)
    rows = []
    for sheet in wb.sheetnames:
        ws = wb[sheet]
        # 自动检测表头行：前 5 行内找到含"项目名称"的行，数据从下一行开始
        start_row = 3  # 默认：第 1 行表头，第 2 行开始数据
        for probe in range(1, 6):
            probe_vals = [c.value for c in ws[probe]]
            if any(isinstance(v, str) and '项目名称' in v for v in probe_vals):
                start_row = probe + 1
                break
        for row in ws.iter_rows(min_row=start_row, values_only=True):
            if not row or not clean(row[COL['name']]):
                continue
            rows.append((start_row + len(rows), row))
    print(f'解析行数: {len(rows)}')

    # ---- 逐行转换 ----
    parsed = []          # 可导入行
    skipped = []         # 跳过行（投标中/状态非法）
    failed = []          # 失败行（缺必填/负责人不存在）
    stats = {'leader_matched': 0, 'leader_fallback': 0}

    for row_no, row in rows:
        name = clean(row[COL['name']])
        status_map = norm_status(row[COL['status']])
        if status_map is None:
            skipped.append((row_no, name, f'状态"{row[COL["status"]]}"非终态'))
            continue
        status, stage, tender_status, result_type = status_map

        # 必填校验（来源平台非必填：表格全空，导入时用"人工录入"兜底）
        missing = []
        for field, label in (('owner', '招标主体'), ('cust_type', '客户类型'),
                             ('proj_type', '项目类型'),
                             ('open_time', '开标时间'), ('project_leader', '项目负责人'),
                             ('bidding_leader', '投标负责人'), ('period_years', '服务周期'),
                             ('period_end', '服务周期截止时间')):
            if not clean(row[COL[field]]):
                missing.append(label)
        if missing:
            failed.append((row_no, name, f'缺必填字段: {",".join(missing)}'))
            continue

        # 负责人解析：users[姓名] = (id, employee_number)
        pl = parse_leaders(row[COL['project_leader']])
        bl = parse_leaders(row[COL['bidding_leader']])
        if pl is None or bl is None:
            failed.append((row_no, name, '负责人解析失败'))
            continue
        pl_name, pl_emp = pl
        bl_name, bl_emp = bl

        pl_user = users.get(pl_name) if pl_name else None
        if pl_user:
            stats['leader_matched'] += 1
            pl_id, pl_emp_from_db = pl_user
        else:
            pl_id, pl_emp_from_db = '1', None  # admin 兜底
            stats['leader_fallback'] += 1
        owner_user_id = int(pl_id) if str(pl_id).isdigit() else None

        # 投标负责人仅展示姓名（工号随项目负责人走系统规范，投标负责人在前端由 id 反查）
        bl_user = users.get(bl_name) if bl_name else None
        bl_id = bl_user[0] if bl_user else None

        open_dt = parse_open_time(row[COL['open_time']])
        if open_dt is None:
            failed.append((row_no, name, f'开标时间无法解析: {row[COL["open_time"]]}'))
            continue

        period_end = row[COL['period_end']]
        period_years = to_num(row[COL['period_years']])

        # 营收：直接存表格值（亿），不放大
        revenue = to_num(row[COL['revenue']])

        bid_month = clean(row[COL['bid_month']])
        if not bid_month:
            bid_month = open_dt.strftime('%Y-%m')

        parsed.append({
            'row_no': row_no,
            'name': name,
            'owner': clean(row[COL['owner']]),
            # 来源平台空 → "人工录入"（用户需求 6：HISTORICAL_IMPORT 改为人工录入）
            'source_platform': clean(row[COL['source_platform']]) or '人工录入',
            'hq_loc': clean(row[COL['hq_loc']]),
            'status': status, 'stage': stage, 'tender_status': tender_status, 'result_type': result_type,
            'cust_type': norm_customer_type(row[COL['cust_type']]),
            'proj_type_enum': norm_project_type(row[COL['proj_type']]),
            'proj_type_raw': clean(row[COL['proj_type']]),
            'priority': norm_priority(row[COL['priority']]),
            'revenue': revenue,
            'expected_bidders': to_num(row[COL['expected_bidders']]),
            'open_dt': open_dt,
            'bid_month': bid_month,
            'contact_name': clean(row[COL['contact_name']]),
            'contact_phone': clean(row[COL['contact_phone']]),
            'pl_name': pl_name, 'pl_emp': pl_emp or pl_emp_from_db,
            'bl_name': bl_name, 'bl_emp': bl_emp,
            'manager_id': owner_user_id,
            'owner_user_id': owner_user_id,
            'bidding_leader_id': int(bl_id) if bl_id and str(bl_id).isdigit() else None,
            'period_years': period_years,
            'period_end': period_end,
            'external_id': 'HISTORICAL_' + hashlib.md5((name + (clean(row[COL['owner']]) or '')).encode()).hexdigest()[:16],
        })

    print(f'可导入: {len(parsed)} 条，跳过: {len(skipped)} 条，失败: {len(failed)} 条')
    print(f'负责人匹配: {stats["leader_matched"]}，fallback admin: {stats["leader_fallback"]}')
    if skipped:
        print('\n--- 跳过清单 ---')
        for s in skipped:
            print(f'  行{s[0]}: {s[1]} - {s[2]}')
    if failed:
        print('\n--- 失败清单 ---')
        for f in failed:
            print(f'  行{f[0]}: {f[1]} - {f[2]}')

    if args.dry_run:
        print(f'\n[dry-run] 未写库，共 {len(parsed)} 条待导入')
        return

    # ---- 写库 ----
    conn = pymysql.connect(host=args.db_host, port=args.db_port, user=args.db_user,
                           password=args.db_pass, database=args.db_name, charset='utf8mb4')
    try:
        with conn.cursor() as cur:
            # 幂等：按"项目名称+招标主体"查重（旧数据 external_id 为随机 UUID，不能用它做幂等键）
            # 兼容两种来源：tenders.title 占位标讯名 = 项目名，projects.name = 项目名
            existing = set()
            cur.execute("""
                SELECT CONCAT(t.title, '||', t.purchaser_name)
                FROM tenders t WHERE t.source_type = 'MANUAL_SINGLE'
                UNION
                SELECT CONCAT(p.name, '||', p.customer)
                FROM projects p WHERE p.source_module = '人工录入'
            """)
            for r in cur.fetchall():
                existing.add(r[0])

            imported = dup = 0
            for p in parsed:
                key = f"{p['name']}||{p['owner']}"
                if key in existing:
                    dup += 1
                    continue
                _import_row(conn, cur, p)
                existing.add(key)
                imported += 1
            conn.commit()
            print(f'\n导入完成: 新增 {imported} 条，跳过重复 {dup} 条')
    finally:
        conn.close()


def _import_row(conn, cur, p):
    """单行导入：写 6 张表。所有时间统一用开标时间（用户需求 created_at=开标时间）。"""
    open_ts = p['open_dt'].strftime('%Y-%m-%d %H:%M:%S')
    period_end_d = p['period_end'].strftime('%Y-%m-%d') if hasattr(p['period_end'], 'strftime') else None
    period_end_ts = p['period_end'].strftime('%Y-%m-%d %H:%M:%S') if hasattr(p['period_end'], 'strftime') else None

    # 1. tender
    cur.execute("""
        INSERT INTO tenders (
            external_id, title, source, source_type, purchaser_name, priority,
            bid_opening_time, publish_date, status, project_type, industry,
            customer_type, region, project_manager_name, bidding_person_name,
            bidding_person_id, contact_name, contact_phone, source_platform,
            creator_id, creator_name,
            version, created_at, updated_at
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """, (
        p['external_id'], p['name'], '人工录入', 'MANUAL_SINGLE', p['owner'], p['priority'],
        open_ts, p['open_dt'].strftime('%Y-%m-%d'), p['tender_status'],
        p['proj_type_enum'], p['proj_type_raw'], p['cust_type'], p['hq_loc'],
        p['pl_name'], p['bl_name'], p['bidding_leader_id'],
        p['contact_name'], p['contact_phone'], p['source_platform'], 1, '系统管理员',
        0, open_ts, open_ts,
    ))
    tender_id = cur.lastrowid

    # 2. project
    cur.execute("""
        INSERT INTO projects (
            name, tender_id, manager_id, status, stage, customer, customer_type,
            region, platform, industry, source_module, start_date, end_date,
            initiated_at, evaluating_at, closed_at, deadline,
            created_at, updated_at
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """, (
        p['name'], tender_id, p['manager_id'], p['status'], p['stage'], p['owner'], p['cust_type'],
        p['hq_loc'], p['source_platform'], p['proj_type_raw'], '人工录入',
        open_ts, period_end_ts, open_ts, open_ts, open_ts, p['open_dt'].strftime('%Y-%m-%d'),
        open_ts, open_ts,
    ))
    project_id = cur.lastrowid

    # 3. project_initiation_details
    cur.execute("""
        INSERT INTO project_initiation_details (
            project_id, owner_unit, bid_open_time, bid_month, expected_bidders,
            customer_type, project_type, annual_revenue, contract_period_months,
            bidding_leader_name, project_leader_name, headquarters_location,
            bidding_platform, customer_grade, bid_status,
            review_status, reviewed_at, locked, need_deposit, eval_prefilled,
            owner_user_id, created_by, updated_by, created_at, updated_at
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """, (
        project_id, p['owner'], open_ts, p['bid_month'], int(p['expected_bidders']) if p['expected_bidders'] else None,
        p['cust_type'], p['proj_type_raw'], p['revenue'],
        int(round(p['period_years'] * 12)) if p['period_years'] else None,
        p['bl_name'], p['pl_name'], p['hq_loc'], p['source_platform'], p['priority'],
        '未中标' if p['status'] == 'LOST' else ('已中标' if p['status'] == 'WON' else '已流标'),
        'APPROVED', open_ts, 1, 'NO', 0,
        p['owner_user_id'], 1, 1, open_ts, open_ts,
    ))

    # 4. project_result
    cur.execute("""
        INSERT INTO project_result (
            project_id, result_type, service_period_years, service_period_end_date,
            registered_at, summary, created_at, updated_at, created_by, updated_by
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
    """, (
        project_id, p['result_type'],
        round(p['period_years'], 1) if p['period_years'] else None,
        period_end_d, open_ts, '历史项目导入', open_ts, open_ts, 1, 1,
    ))

    # 5. project_archive
    cur.execute("""
        INSERT INTO project_archive (
            project_id, project_name, archive_status, created_at, updated_at
        ) VALUES (%s,%s,%s,%s,%s)
    """, (project_id, p['name'][:200], 'ACTIVE', open_ts, open_ts))

    # 6. project_lead_assignment（primary_lead_user_id = 主投标负责人，
    #    后端 ProjectQueryService 用它回填 biddingLeaderId；项目负责人走 projects.manager_id）
    cur.execute("""
        INSERT INTO project_lead_assignment (
            project_id, primary_lead_user_id, assigned_at, assigned_by, created_at, updated_at
        ) VALUES (%s,%s,%s,%s,%s,%s)
    """, (project_id, p['bidding_leader_id'], open_ts, 1, open_ts, open_ts))


if __name__ == '__main__':
    main()
