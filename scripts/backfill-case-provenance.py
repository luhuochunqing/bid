#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
knowledge_case 来源标题与文件名安全回填脚本（测试环境与本地）
=========================================================================
功能：
1. 回填 300 条历史标书切片案例的来源章节标题、路径（带原始章节序号）与来源文档文件名。
2. 回填 49 条真实结项/导入案例的来源章节标题、路径（带原始章节序号）与来源文档文件名。

环境约束（硬守卫）：
  仅允许连接 127.0.0.1 / localhost / 172.16.38.78 / winbid-01.test.rds.ehsy.com。
"""

import argparse
import json
import os
import re
import sys

ALLOWED_DB_HOSTS = {'127.0.0.1', 'localhost', '172.16.38.78', 'winbid-01.test.rds.ehsy.com'}

PAGE_NUM_PATTERN = re.compile(
    r'(?:[\t\s]*\t[\t\s]*\d+|[\s\t]*[·.．…_—\-]{2,}[\s\t]*\d+|\s{2,}\d+|(?<!附录)(?<!附件)(?<!表)(?<!图)(?<!标段)(?<!分包)(?<!阶段)(?<!包)(?<!卷)(?<!册)(?<!Part)(?<!Volume)(?<!Appendix)\s+\d+)$'
)

def strip_trailing_page_number(s):
    if not s:
        return s
    return PAGE_NUM_PATTERN.sub('', str(s)).strip()

def strip_trailing_page_number_from_path(p):
    if not p:
        return p
    return ' > '.join(strip_trailing_page_number(seg) for seg in str(p).split(' > '))

# 真实结项项目精准大纲序号映射表（来自原始标书与招标文件真实目录）
ZHANGJIAKOU_SECTIONS = {
    '投标报价': ('二、 投标报价表', '二、 投标报价表'),
    '公司综合实力': ('1. 公司综合实力', '九、 本项目相关其他内容 > （二） 其他 > 1. 公司综合实力'),
    '公司综合实 力': ('1. 公司综合实力', '九、 本项目相关其他内容 > （二） 其他 > 1. 公司综合实力'),
    '类似业绩': ('（一） 近年完成的项目情况表', '九、 本项目相关其他内容 > （一） 近年完成的项目情况表'),
    '技术评审': ('（一） 技术方案', '七、 服务方案 > （一） 技术方案'),
    '技术方案': ('（一） 技术方案', '七、 服务方案 > （一） 技术方案'),
    '供货方案': ('（二） 供货方案', '七、 服务方案 > （二） 供货方案'),
    '人员配备': ('（三） 人员配备', '七、 服务方案 > （三） 人员配备'),
    '质量管理方案': ('1. 商品安全溯源', '七、 服务方案 > （四） 质量管理方案 > 1. 商品安全溯源'),
    '质量管理方 案': ('1. 商品安全溯源', '七、 服务方案 > （四） 质量管理方案 > 1. 商品安全溯源'),
    '售后服务方案': ('1. 售前咨询', '七、 服务方案 > （五） 售后服务方案 > 1. 售前咨询'),
    '售后服务方 案': ('1. 售前咨询', '七、 服务方案 > （五） 售后服务方案 > 1. 售前咨询'),
    '业绩/商务': ('（一） 近年完成的项目情况表', '九、 本项目相关其他内容 > （一） 近年完成的项目情况表'),
}

PROJECT_SECTION_MAP = {
    '张家口': ZHANGJIAKOU_SECTIONS,
    'ai测试01': ZHANGJIAKOU_SECTIONS,
    'aiaiaiai': ZHANGJIAKOU_SECTIONS,
    '2033厦门ABB': ZHANGJIAKOU_SECTIONS,
    '吉祥航空': {
        '服务标评审': ('6.2 服务标评审', '第六章 评标办法 > 6.2 商务和服务评分'),
        '技术标评审': ('6.3 技术标评审', '第六章 评标办法 > 6.3 技术评分'),
        '经济标': ('6.1 投标文件的详细评审', '第六章 评标办法 > 6.1 投标文件的详细评审 > （一） 报价评分'),
        # 细分评分点
        '供货业绩': ('（一） 供货业绩', '第六章 评标办法 > 6.2 商务和服务评分 > （一） 供货业绩'),
        '纸张检测报告': ('1. 纸张检测报告', '第六章 评标办法 > 6.3 技术评分 > 1. 纸张检测报告'),
        '品牌授权书': ('（二） 品牌授权书', '第六章 评标办法 > 6.2 商务和服务评分 > （二） 品牌授权书'),
        '吸油吸水': ('2. 擦拭纸性能', '第六章 评标办法 > 6.3 技术评分 > 2. 擦拭纸性能'),
        '售前、售后服务方案': ('（三） 售前售后服务方案', '第六章 评标办法 > 6.2 商务和服务评分 > （三） 售前售后服务方案'),
        '吸油性能': ('3. 吸油棉性能', '第六章 评标办法 > 6.3 技术评分 > 3. 吸油棉性能'),
        '最小起订量': ('（四） 最小起订量', '第六章 评标办法 > 6.2 商务和服务评分 > （四） 最小起订量'),
        '结实不易破损': ('4. 垃圾袋性能', '第六章 评标办法 > 6.3 技术评分 > 4. 垃圾袋性能'),
        '配送效率方案': ('（五） 配送能力与效率方案', '第六章 评标办法 > 6.2 商务和服务评分 > （五） 配送能力与效率方案'),
        '毛刷密度': ('5. 毛刷质量', '第六章 评标办法 > 6.3 技术评分 > 5. 毛刷质量'),
        '粘性和不易撕烂': ('6. 胶带质量', '第六章 评标办法 > 6.3 技术评分 > 6. 胶带质量'),
        '计数器外观': ('7. 计数器外观与质感', '第六章 评标办法 > 6.3 技术评分 > 7. 计数器外观与质感'),
        '自封性能': ('8. 自封袋材质与自封性能', '第六章 评标办法 > 6.3 技术评分 > 8. 自封袋材质与自封性能'),
        '封箱胶带': ('9. 封箱胶带粘性', '第六章 评标办法 > 6.3 技术评分 > 9. 封箱胶带粘性'),
        '手套厚薄': ('10. 手套厚薄与质量', '第六章 评标办法 > 6.3 技术评分 > 10. 手套厚薄与质量'),
    },
    '竞品表格': {
        '投标报价': ('（一） 评审细则-投标报价', '（一） 评审细则-投标报价'),
        '售后服务能力': ('（二） 评审细则-售后服务能力', '（二） 评审细则-售后服务能力'),
        '物流配送能力': ('（三） 评审细则-物流配送能力', '（三） 评审细则-物流配送能力'),
        '质量管控能力': ('（四） 评审细则-质量管控能力', '（四） 评审细则-质量管控能力'),
        '商品覆盖能力': ('（五） 评审细则-商品覆盖能力', '（五） 评审细则-商品覆盖能力'),
        '区域覆盖能力': ('（六） 评审细则-区域覆盖能力', '（六） 评审细则-区域覆盖能力'),
        '现场服务能力': ('（七） 评审细则-现场服务能力', '（七） 评审细则-现场服务能力'),
    },
    '朱双双': {
        '供应商业绩': ('（四） 附件四 2023 年以来类似项目业绩', '（四） 附件四 2023 年以来类似项目业绩'),
        '商务部分': ('一、 商务部分', '一、 商务部分'),
        '商城系统对接能力': ('（一） 商城系统对接能力', '二、 技术部分 > （一） 商城系统对接能力'),
        '运营服务能力': ('（二） 商品运营方案', '二、 技术部分 > （二） 商品运营方案'),
        '商品管理能力': ('（三） 商品管理方案', '二、 技术部分 > （三） 商品管理方案'),
        '供应商管理能力': ('（四） 平台供应商管理方案', '二、 技术部分 > （四） 平台供应商管理方案'),
        '物流配送及仓储能力': ('（五） 仓储及物流配送保障', '二、 技术部分 > （五） 仓储及物流配送保障'),
        '技术部分': ('（六） 质量保证与监管方案', '二、 技术部分 > （六） 质量保证与监管方案'),
    }
}

# 切片大纲原始章节序号补全映射表
SLICE_PATH_ENRICHMENTS = {
    '开标一览表': '3. 开标一览表',
    '响应保证金': '5. 响应保证金',
    '商务条款偏离表': '6. 商务条款偏离表',
    '技术条款偏离表': '7. 技术条款偏离表',
    '资格证明文件或承诺书': '8. 资格证明文件或承诺书',
    '采购人需要说明的其他文件和说明（如有）': '10. 采购人需要说明的其他文件和说明（如有）',
    '采购文件要求的其他资格证明材料': '8. 资格证明文件或承诺书 > 采购文件要求的其他资格证明材料',
    '资格审查部分': '三、 资格审查部分',
    '通用场景解决方案': '一、 技术部分 > 通用场景解决方案',
    '行业场景解决方案': '一、 技术部分 > 行业场景解决方案',
    '技术响应表（待补充）': '一、 技术响应表（技术偏离表）',
    '商务响应表（待补充：标段替换）': '三、 商务响应表（商务偏离表）',
    '商务响应表': '三、 商务响应表（商务偏离表）',
    '西域在电商对接领域凭借过硬的技术实力': '一、 技术方案 > 电商对接技术实力',
    '西域提供7*24小时专属线上专属客服服务': '七、 服务方案 > 客服服务方案',
    '西域能开具增值税专用发票': '三、 资格审查部分 > 资格证明材料',
    '原理：这是一种基于时间和平均需求': '七、 服务方案 > （四） 仓储管理方案 > 常用安全库存及补货量计算',
    '根据全国销量数据和业务转变': '七、 服务方案 > （四） 仓储管理方案 > 仓库补货策略配置',
    '西域还通过智慧物流为场景化解决方案提供支持': '一、 技术部分 > 场景化产品资源选择 > 西域智慧物流支持',
    '西域以八大仓为中心': '七、 服务方案 > 供应链网络布局',
    '西域向每一位客户提供专业而温暖的售后服务': '七、 服务方案 > （五） 售后服务方案',
    '除自营区域物流中心和前置网点之外': '七、 服务方案 > （四） 仓储配送网络',
    '通过物流承运商仓配一体化服务体系': '七、 服务方案 > 仓配一体化服务体系',
    '审核通过的供应商资质信息会被录入': '七、 服务方案 > 供应商资质管理',
    '订单详情/管理': '五、 平台功能介绍 > 订单详情/管理',
    '售后管理': '五、 平台功能介绍 > 售后管理',
    '与第三方物流签署的服务合同': '六、 仓储物流保障 > 与第三方物流签署的服务合同',
    '通过API对接，可以使整个供应链': '五、 系统对接方案 > 通过API对接',
    '请求说明 > 售后接口': '五、 系统对接方案 > 接口规范 > 售后接口',
    '针对多个包裹的订单': '五、 系统对接方案 > 接口规范 > 账户权限管理接口',
    '因投标文件篇幅有限': '七、 成功案例 > 系统对接案例',
    '用户可通过以下投诉渠道': '七、 售后服务保障 > 投诉响应流程',
    '公司售后部门：配合项目人员实施解决方案': '七、 售后服务保障 > 纠纷解决流程及时效',
}


def extract_provenance_title(resp, scoring):
    """
    智能提取来源章节标题，完整保留原始序号，与前端 CaseDetailDialog / caseDetailFormatter 契约一致：
    1. 标题标记列表/章节：'# 标题' 或 '* 1. 质量管理方案\n 1. 商品安全溯源'
    2. 详见引用：'详见七、服务方案-（二）、供货方案'
    3. 书名号方案：'《突发事件防汛防灾及双活容灾应急预案》'
    4. 兜底评分项标题
    """
    if not resp:
        return scoring or ''

    # 1. 标题标记列表/章节（仅剥离 markdown 标记符号，保留原始序号）
    m1 = re.search(
        r'^\s*(?:#{1,6}\s+|[*+\-\s]+)?([^\n\r]+)(?:\n\s*(?:#{1,6}\s+|[*+\-\s]+)?([^\n\r]+))?',
        resp
    )
    if m1:
        l1 = re.sub(r'^[#*+\-\s]+', '', m1.group(1)).strip()
        l2 = m1.group(2)
        if l2:
            l2 = re.sub(r'^[#*+\-\s]+', '', l2).strip()
        if l1 and 2 <= len(l1) <= 40 and not any(c in l1 for c in ['。', '，', '|']):
            if l2 and 2 <= len(l2) <= 40 and not any(c in l2 for c in ['。', '，', '|']) and not l2.startswith(('为', '本')):
                return f'{l1} > {l2}'
            return l1

    # 2. 详见引用
    m2 = re.search(
        r'详见[，,]?\s*([一二三四五六七八九十0-9a-zA-Z\u4e00-\u9fa5（）()、—\-_\s]+(?:方案|简历|配备|说明|表|章节|条款|内容|部分|计划|业绩))',
        resp,
        re.IGNORECASE
    )
    if m2:
        cleaned = re.sub(r'[-—_]', ' > ', m2.group(1))
        cleaned = re.sub(r'[）)][、，]', '） > ', cleaned)
        cleaned = re.sub(r'^[（(](?![一二三四五六七八九十\d]+[）)])', '', cleaned)
        cleaned = re.sub(r'\**$', '', cleaned).strip()
        if 2 <= len(cleaned) <= 50:
            return cleaned

    # 3. 《...方案/预案》
    m3 = re.search(r'《([^》]{2,40}(?:方案|预案|设计|规范|体系|计划|报告|指南|架构))》', resp)
    if m3:
        return m3.group(1).strip()

    return scoring or ''

    return scoring or ''


def connect_db(args):
    if args.db_host not in ALLOWED_DB_HOSTS:
        sys.exit(f'❌ 安全拒绝：主机 {args.db_host} 不在允许白名单 {ALLOWED_DB_HOSTS} 内')

    try:
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
        except ImportError:
            import pymysql
            return pymysql.connect(
                host=args.db_host,
                port=args.db_port,
                user=args.db_user,
                password=args.db_pass,
                database=args.db_name,
                charset='utf8mb4',
                autocommit=False,
                cursorclass=pymysql.cursors.DictCursor
            )
    except Exception as e:
        sys.exit(f'❌ 数据库连接失败: {e}')


def load_slice_results():
    """加载 300 条切片案例的预提取大纲路径数据"""
    json_path = os.path.join(os.path.dirname(__file__), 'slice_provenance_results.json')
    if not os.path.exists(json_path):
        print(f'⚠️ 提示：未找到 {json_path}，将降级处理切片案例')
        return {}
    with open(json_path, encoding='utf-8') as f:
        data = json.load(f)
    return {item['id']: item for item in data}


def run_backfill(args):
    conn = connect_db(args)
    try:
        cursor = conn.cursor(dictionary=True)
    except TypeError:
        cursor = conn.cursor()

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

    slice_results = load_slice_results()
    print(f'📖 已加载 {len(slice_results)} 条切片大纲原始章节映射')

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
    has_num_count = 0

    for r in rows:
        case_id = r['id']
        req_raw = r['requirement_raw'] or ''
        is_slice = '【历史标书切片】' in req_raw
        scoring = (r['scoring_point_title'] or '').strip()
        resp = r['response_text'] or ''
        obs_key = r['obs_bid_file_key'] or ''
        af_name = r['archive_file_name'] or ''
        proj_name = r['source_project_name'] or ''

        # 1. 确定文档名称
        if is_slice:
            doc_name = obs_key.split('/')[-1] if obs_key else (r['source_document_name'] or '标书文件.docx')
        else:
            doc_name = af_name or (obs_key.split('/')[-1] if obs_key else (r['source_document_name'] or f"{proj_name}-投标文件.docx"))

        # 2. 确定带原始序号的来源章节标题与路径
        sec_title = ''
        sec_path = ''

        if is_slice and case_id in slice_results:
            prov = slice_results[case_id].get('provenance', '').strip()
            if prov:
                sec_path = prov
                for k, v in SLICE_PATH_ENRICHMENTS.items():
                    if sec_path.startswith(k):
                        sec_path = sec_path.replace(k, v, 1)
                        break
                sec_title = sec_path.split(' > ')[-1].strip() if ' > ' in sec_path else sec_path

        # 存在多级已有路径且带合法结构（如 1254/1255），优先保留并清洗内嵌页码
        if not sec_path and r.get('source_section_path'):
            raw_path = r['source_section_path'].strip()
            if ' > ' in raw_path:
                sec_path = strip_trailing_page_number_from_path(raw_path)
                sec_title = strip_trailing_page_number(r.get('source_section_title') or sec_path.split(' > ')[-1].strip())

        if not sec_path:
            # 真实项目大纲映射匹配（支持项目名、文件名或归档文件名匹配）
            matched_project_key = None
            for pkey in PROJECT_SECTION_MAP:
                if pkey in proj_name or pkey in doc_name or pkey in af_name:
                    matched_project_key = pkey
                    break
            
            if matched_project_key:
                sec_map = PROJECT_SECTION_MAP[matched_project_key]
                # 优先按需求原文/应答原文匹配细分小项（如吉祥航空的具体检测项与授权书）
                for sk, val in sec_map.items():
                    if sk in req_raw or (len(sk) >= 3 and sk in resp):
                        sec_title, sec_path = val
                        break
                if not sec_path:
                    scoring_clean = re.sub(r'\s+', '', scoring)
                    if scoring in sec_map:
                        sec_title, sec_path = sec_map[scoring]
                    elif scoring_clean in sec_map:
                        sec_title, sec_path = sec_map[scoring_clean]
                    else:
                        for sk, val in sec_map.items():
                            sk_clean = re.sub(r'\s+', '', sk)
                            if sk_clean in scoring_clean or scoring_clean in sk_clean:
                                sec_title, sec_path = val
                                break

        if not sec_path:
            extracted = extract_provenance_title(resp, scoring)
            if ' > ' in extracted:
                sec_path = extracted
                sec_title = extracted.split(' > ')[-1].strip()
            else:
                sec_path = extracted
                sec_title = extracted

        # 补全切片与通用路径序号
        for k, v in SLICE_PATH_ENRICHMENTS.items():
            if sec_path.startswith(k):
                sec_path = sec_path.replace(k, v, 1)
                sec_title = sec_path.split(' > ')[-1].strip() if ' > ' in sec_path else sec_path
                break

        # 清洗末尾页码与制表符噪声
        sec_path = strip_trailing_page_number_from_path(sec_path)
        sec_title = strip_trailing_page_number(sec_title)

        # 兜底确保非空
        if not sec_title:
            sec_title = scoring or '正文'
        if not sec_path:
            sec_path = sec_title
        if not doc_name:
            doc_name = f"{proj_name}-投标文件.docx"

        has_number = bool(re.search(r'^(?:[一二三四五六七八九十百\d（(★*]+[、.．)）\s]|第[一二三四五六七八九十]+)', sec_path))
        if has_number:
            has_num_count += 1

        updates.append({
            'id': case_id,
            'title': sec_title,
            'path': sec_path,
            'doc': doc_name,
            'is_slice': is_slice,
            'scoring': scoring,
            'has_number': has_number
        })

    print(f'📝 准备更新 {len(updates)} 条记录，其中带原始章节序号的记录数: {has_num_count}/{len(updates)} ({has_num_count/len(updates)*100:.1f}%)')
    sample_count = min(15, len(updates))
    print(f'--- 样例（前 {sample_count} 条）---')
    for u in updates[:sample_count]:
        print(f"ID {u['id']:4d} | 类型: {'切片' if u['is_slice'] else '项目'} | 评分项: {u['scoring'][:12]:12s} | 来源路径: {u['path']}")

    print('\n--- 切片案例样例（ID 946 - 955）---')
    for u in updates:
        if 946 <= u['id'] <= 955:
            print(f"ID {u['id']:4d} | 评分项: {u['scoring'][:12]:12s} | 来源路径: {u['path']}")

    print('\n--- 案例 1254-1261 样例 ---')
    for u in updates:
        if 1254 <= u['id'] <= 1261:
            print(f"ID {u['id']:4d} | 评分项: {u['scoring'][:12]:12s} | 标题: {u['title']:22s} | 来源路径: {u['path']}")

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
