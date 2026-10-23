#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
70 个历史项目标书特征深度抽取与清洗流水线脚本
=========================================================================
围绕第一性原理：在 AI 智能案例推荐中准确推荐相似历史案例。
对 70 个历史项目的 docx 切片（/tmp/winbid_slices/project_*.jsonl）进行深度
特征提取与噪声过滤，萃取高纯度黄金案例库，输出结构化特征 JSONL，
作为入库 knowledge_case 的标准化数据源。

核心能力：
  1. 核心企业专名提取（剥离年份、日期、行政与地域前缀，如"中国建设银行"→"建设银行"）；
  2. 10 大招投标核心业务子主题（Sub-Topic）确定性规则分类器；
  3. 样板段落物理过滤门禁（过滤目录、资质扫描、纯表格及正文少于 120 字片段）；
  4. 文本充实度与内容质量评分（quality_score: 0-100）；
  5. 100% 校验与补齐 OBS 原版标书对象键（obs_bid_file_key）。

用法:
  python3 scripts/extract-historical-case-features.py --self-test
  python3 scripts/extract-historical-case-features.py --offline [--input-dir=DIR] [--limit=N]
  python3 scripts/extract-historical-case-features.py --input-dir=/tmp/winbid_slices --output=./cleaned_cases.jsonl
"""

import argparse
import json
import os
import re
import sys

# ---------------------------------------------------------------------------
# 常量定义与正则配置
# ---------------------------------------------------------------------------

OBS_BID_ROOT_PREFIX = '2026年（投标文件）/'

# 日期前缀正则（如 2026.01.20-- / 2026年1月2日—）
DATE_PREFIX_RE = re.compile(r'^\d{4}[.年/-]\d{1,2}[.月/-]\d{1,2}日?[-—_]*\s*')

# 机构专名清理：剥离国字头与行政前缀
ORG_GEO_PREFIX_RE = re.compile(r'^(中国|中华|国家|中央|北京市?|上海市?|天津市?|重庆市?|广东省?|江苏省?|浙江省?|山东省?|四川省?|湖北省?|湖南省?|河北省?|河南省?|陕西省?|山西省?|辽宁省?|吉林省?|黑龙江省?|安徽省?|江西省?|福建省?|广西|内蒙古|新疆|西藏|宁夏|海南省?|贵州省?|云南省?|青海省?|甘肃省?)')
COMPANY_SUFFIX_RE = re.compile(r'(股份有限公司|有限责任公司|集团有限公司|有限公司|总公司|分公司|集团|公司)$')

# 过滤黑名单：纯格式、发票、资质等非应答章节
BLACKLIST_SECTION_KEYWORDS = [
    '发票', '执照', '证明', '社保', '审计报告', '开户许可', '许可证',
    '复印件', '扫描件', '资质证书', '营业执照', '授权书', '法定代表人授权',
    '承诺书', '无重大违法声明', '投标函附录', '关联关系声明',
    '业绩证明', '中标通知书', '合同复印件', '合作协议', '框架协议',
    '开标一览表', '报价一览表', '分项报价表', '报价明细表', '投标保证金',
    '自查表', '偏差表', '资格证明文件', '目录', '附件清单', '封皮'
]

# 10 大招投标核心业务子主题规则定义（优先级从上到下按匹配深度评估）
SUB_TOPIC_RULES = [
    (
        'SLA_SERVICE',
        '售后服务与SLA保障',
        [
            '售后服务', '售后保障', '服务承诺', '运维保障', '运维服务', '服务体系',
            '故障恢复', '响应时间', '7x24', '7*24', 'SLA', '日常巡检', '巡检服务',
            '维保服务', '运行维护', '备件支持', '售后网点'
        ]
    ),
    (
        'EMERGENCY_PLAN',
        '应急预案与突发事件响应',
        [
            '应急预案', '应急方案', '应急保障', '应急响应', '应急措施', '应急管理', '应急组织',
            '突发事件', '突发故障', '应急演练',
            '防汛', '防台', '防汛防台', '备用方案', '容灾备份', '紧急情况', '应急处理'
        ]
    ),
    (
        'INTEGRATION',
        '系统对接与数据集成',
        [
            '系统对接', '接口规范', '接口设计', '接口方案', '数据迁移', '数据接口',
            'API接口', 'ERP对接', '打通对接', '系统集成', '联调测试', '单点登录', '外部系统对接'
        ]
    ),
    (
        'SECURITY_COMPLIANCE',
        '网络安全与等保合规',
        [
            '网络安全', '数据安全', '等保', '信息安全', '保密承诺', '保密措施',
            '数据加密', '防攻击', '漏洞防护', '权限管理', '身份认证', '安全合规', '访问控制'
        ]
    ),
    (
        'LOGISTICS_WAREHOUSE',
        '仓储管理与物流配送',
        [
            '仓储管理', '物流配送', '仓储布局', '配送方案', '配送时效', '运输保障',
            '车辆调配', '库存管理', '供应链保障', '库房管理', '最后一公里', '绿色包装'
        ]
    ),
    (
        'QUALITY_CONTROL',
        '质量保证与检验验收',
        [
            '质量保证', '质量控制', '质量管理', '验收标准', '验收方案', '检验检测',
            '正品保障', '假一赔十', '质量检验', '质量溯源', '退换货', '不良品处理'
        ]
    ),
    (
        'DELIVERY_PROGRESS',
        '项目进度与实施计划',
        [
            '项目进度', '实施计划', '实施方案', '交付计划', '项目计划', '里程碑',
            '工期保障', '时间节点', '交付节点', '进度控制', '阶段划分'
        ]
    ),
    (
        'SYS_ARCH',
        '系统架构与技术方案',
        [
            '技术架构', '架构设计', '系统架构', '总体架构', '总体设计', '技术路线',
            '技术方案', '组网方案', '组网设计', '系统拓扑', '功能架构', '应用架构',
            '部署架构', '高可用设计', '高可用架构', '数据库设计', '建设方案'
        ]
    ),
    (
        'ORG_STAFF',
        '组织架构与人员配置',
        [
            '项目组织', '团队配置', '人员配备', '项目经理', '项目团队', '岗位职责',
            '组织机构', '人员资质', '人员配置', '专家团队', '人员培训', '培训方案'
        ]
    ),
    (
        'COMMERCIAL_TERMS',
        '商务响应与交付承诺',
        [
            '商务条款', '商务偏离', '付款方式', '结算方式', '交货期', '质保期承诺',
            '交货地点', '商务响应', '合同条款'
        ]
    )
]

MIN_TEXT_LENGTH = 120  # 最低文本字数门禁

# ---------------------------------------------------------------------------
# 纯函数：特征抽取与质量过滤（可单测）
# ---------------------------------------------------------------------------

def normalize_project_dir(dir_name: str) -> str:
    """剥离 OBS 目录名的日期与分隔符前缀，返回干净的项目名称。"""
    if not dir_name:
        return ''
    clean = DATE_PREFIX_RE.sub('', str(dir_name).strip()).strip()
    return clean


def extract_customer_entity(project_name: str) -> str:
    """
    从项目名称中抽取企业专属专名（客户核心实体）。
    例：
      - "2026.01.20--中国建设银行龙集采项目" -> "建设银行"
      - "中国长江三峡集团2026年办公及工业品电商化采购项目" -> "三峡"
      - "国家电网有限公司物资采购项目" -> "国家电网"
    """
    clean = normalize_project_dir(project_name)
    if not clean:
        return ''

    # 特殊重点客户专名精准优先映射
    key_entities = [
        ('三峡', '三峡'),
        ('长江电力', '三峡'),
        ('建设银行', '建设银行'),
        ('建行', '建设银行'),
        ('国家电网', '国家电网'),
        ('国网', '国家电网'),
        ('南方电网', '南方电网'),
        ('南网', '南方电网'),
        ('中核', '中核'),
        ('中广核', '中广核'),
        ('中石油', '中石油'),
        ('中石化', '中石化'),
        ('中国海油', '中海油'),
        ('中国移动', '中国移动'),
        ('中国电信', '中国电信'),
        ('中国联通', '中国联通'),
        ('华能', '华能'),
        ('大唐', '大唐'),
        ('华电', '华电'),
        ('国家能源', '国家能源'),
        ('中泰', '中泰'),
        ('宝武', '宝武')
    ]
    for pattern, entity in key_entities:
        if pattern in clean:
            return entity

    # 通用剥离逻辑：去除行政前缀与公司后缀
    sub = ORG_GEO_PREFIX_RE.sub('', clean)
    # 取前段核心词（遇到年份、采购、项目等动作词截断）
    m = re.split(r'(\d{4}年?|项目|采购|标段|招标|集采|电商)', sub)
    if m and m[0]:
        candidate = COMPANY_SUFFIX_RE.sub('', m[0]).strip()
        if len(candidate) >= 2:
            return candidate[:10]

    return clean[:8]


def is_noise_section(title: str, text: str) -> bool:
    """判定是否为非应答类的纯格式/发票/资质样板章节。"""
    if not title:
        return True
    for kw in BLACKLIST_SECTION_KEYWORDS:
        if kw in title:
            return True
    # 纯公司名结尾且短于 30 字符通常是签章页
    if title.endswith('公司') and len(title) < 30:
        return True
    return False


def classify_sub_topic(title: str, text_preview: str, full_text: str = '') -> tuple[str, str]:
    """
    根据章节标题与文本内容推断招投标业务子主题。
    返回 (sub_topic_code, sub_topic_name)。若无法明确推断，返回 ('OTHER', '其他方案')。
    """
    title_str = title or ''
    content_str = (text_preview or '') + ' ' + (full_text[:400] if full_text else '')

    # 1. 优先根据标题命中（标题的置信度最高）
    for code, name, keywords in SUB_TOPIC_RULES:
        for kw in keywords:
            if kw in title_str:
                return code, name

    # 2. 标题未命中时，检查前部正文内容关键词频次
    best_code = 'OTHER'
    best_name = '其他方案'
    max_hit = 0
    for code, name, keywords in SUB_TOPIC_RULES:
        hit = sum(1 for kw in keywords if kw in content_str)
        if hit > max_hit and hit >= 2:  # 至少命中 2 个关键词才采纳
            max_hit = hit
            best_code = code
            best_name = name

    return best_code, best_name


def calculate_quality_score(text: str, para_count: int = 1) -> int:
    """
    计算案例文本的充实度与质量评分（0-100）。
    规则：
      - 文本字数阶梯（基础分）
      - 段落数结构加分
      - 列表/数字量化指标加分
    """
    if not text or len(text.strip()) < MIN_TEXT_LENGTH:
        return 0

    length = len(text.strip())
    score = 50

    # 字数阶梯
    if length >= 1500:
        score += 25
    elif length >= 600:
        score += 20
    elif length >= 300:
        score += 15
    elif length >= 120:
        score += 10

    # 段落丰富度
    if para_count >= 4:
        score += 10
    elif para_count >= 2:
        score += 5

    # 结构化标记检测（条理性加分）
    if re.search(r'([一二三四五]、|\d+\.|\(\d+\)|•|—)', text):
        score += 10

    # 具体数字量化指标加分（承诺度如 7x24, 99.9%, 2小时）
    if re.search(r'(\d+小时|\d+%|\d+天|7x24|7\*24)', text, re.IGNORECASE):
        score += 5

    return min(100, max(0, score))


def is_valid_obs_bid_key(key: str) -> bool:
    """校验 OBS 对象键合法性（非空、桶内根前缀开头、无路径穿越）。"""
    if not key or not isinstance(key, str):
        return False
    key = key.strip()
    if not key.startswith(OBS_BID_ROOT_PREFIX):
        return False
    if '..' in key or key.startswith('/'):
        return False
    return True


def clean_and_extract_slice(raw_slice: dict) -> dict | None:
    """
    全流程清洗单条原始切片数据。
    若符合质量门禁，返回增强后的特征字典；若为噪音或不达标，返回 None。
    """
    title = (raw_slice.get('title') or '').strip()
    preview = (raw_slice.get('text_preview') or '').strip()
    full_text = (raw_slice.get('text') or preview).strip()
    project_dir = (raw_slice.get('project') or '').strip()
    obs_key = (raw_slice.get('obs_bid_file_key') or '').strip()
    docx_label = (raw_slice.get('docx_label') or '技术').strip()
    para_count = int(raw_slice.get('para_count') or 1)

    # 1. 过滤非法或缺失必填项
    if not title or not project_dir or not is_valid_obs_bid_key(obs_key):
        return None

    # 2. 噪声门禁过滤
    if is_noise_section(title, full_text):
        return None

    # 3. 长度门禁过滤
    if len(full_text) < MIN_TEXT_LENGTH:
        return None

    # 4. 特征抽取
    customer_entity = extract_customer_entity(project_dir)
    clean_proj_name = normalize_project_dir(project_dir)
    sub_topic, sub_topic_cn = classify_sub_topic(title, preview, full_text)
    quality_score = calculate_quality_score(full_text, para_count)

    # 5. 组装标准化特征记录
    return {
        'project_dir': project_dir,
        'project_name': clean_proj_name,
        'customer_entity': customer_entity,
        'docx_label': docx_label,
        'title': title[:500],
        'sub_topic': sub_topic,
        'sub_topic_cn': sub_topic_cn,
        'text_preview': preview[:300],
        'text': full_text,
        'text_length': len(full_text),
        'para_count': para_count,
        'quality_score': quality_score,
        'obs_bid_file_key': obs_key
    }


# ---------------------------------------------------------------------------
# 自测套件（--self-test 核心质量门禁）
# ---------------------------------------------------------------------------

def run_self_test():
    """运行纯函数断言，确保特征提取与过滤规则绝对稳健。"""
    print("Running extract-historical-case-features self-tests...")

    # 1. 项目名称归一化
    assert normalize_project_dir("2026.01.20--中国建设银行龙集采项目") == "中国建设银行龙集采项目"
    assert normalize_project_dir("2026年3月5日—某某电力项目") == "某某电力项目"
    assert normalize_project_dir("无日期项目") == "无日期项目"

    # 2. 核心企业专名抽取
    assert extract_customer_entity("2026.01.20--中国建设银行龙集采项目") == "建设银行"
    assert extract_customer_entity("中国长江三峡集团2026年采购项目") == "三峡"
    assert extract_customer_entity("国家电网有限公司办公用品集采") == "国家电网"
    assert extract_customer_entity("中国南方电网2025年项目") == "南方电网"
    assert extract_customer_entity("新疆中泰化学股份有限公司项目") == "中泰"
    assert extract_customer_entity("中国海油物资招标") == "中海油"

    # 3. 噪声过滤判断
    assert is_noise_section("投标发票复印件清单", "发票如下...")
    assert is_noise_section("法定代表人授权书", "本人特授权...")
    assert is_noise_section("商务偏差表", "无偏离")
    assert not is_noise_section("7x24小时售后服务保障方案", "我司承诺提供全天候响应...")
    assert not is_noise_section("系统总体架构与接口设计", "本平台采用微服务架构...")

    # 4. 业务子主题分类
    s1, _ = classify_sub_topic("售后服务承诺与SLA保障", "承诺7x24小时上门维保响应")
    assert s1 == "SLA_SERVICE"

    s2, _ = classify_sub_topic("防汛防台及突发事件应急预案", "备品备件库充足")
    assert s2 == "EMERGENCY_PLAN"

    s3, _ = classify_sub_topic("ERP系统对接与API接口规范", "提供RESTful标准接口数据迁移")
    assert s3 == "INTEGRATION"

    s4, _ = classify_sub_topic("网络安全与等保三级合规措施", "数据落盘国密SM4加密")
    assert s4 == "SECURITY_COMPLIANCE"

    s5, _ = classify_sub_topic("仓储网点布局与干线物流配送方案", "建立全国中心仓")
    assert s5 == "LOGISTICS_WAREHOUSE"

    s6, _ = classify_sub_topic("质量控制与抽样检验验收标准", "出厂检验把关，假一赔十")
    assert s6 == "QUALITY_CONTROL"

    s7, _ = classify_sub_topic("实施周期与关键里程碑交付计划", "分三阶段推进上线")
    assert s7 == "DELIVERY_PROGRESS"

    s8, _ = classify_sub_topic("系统总体技术架构与组网设计", "采用双活高可用拓扑")
    assert s8 == "SYS_ARCH"

    s9, _ = classify_sub_topic("项目经理及关键岗位人员资质配置", "配备PMP认证项目经理")
    assert s9 == "ORG_STAFF"

    # 5. 质量评分计算
    assert calculate_quality_score("太短", 1) == 0  # < 120 字得 0 分
    good_text = (
        "一、总体保障方案\n"
        "我司承诺为本项目提供 7x24 小时全天候技术保障，全国设立 30 个本地备件库。\n"
        "1. 响应时间：30分钟内响应，2小时内到达现场排查。\n"
        "2. 故障恢复：一般故障 4 小时内恢复，重大故障 8 小时内排除并提供应急备机。\n"
        "3. 日常巡检：每月提供一次系统健康体检报告。"
    )
    score = calculate_quality_score(good_text, para_count=4)
    assert score >= 80, f"Expected high quality score >= 80, got {score}"

    # 6. OBS Key 校验
    assert is_valid_obs_bid_key(OBS_BID_ROOT_PREFIX + "项目A/技术标.docx")
    assert not is_valid_obs_bid_key("invalid_prefix/a.docx")
    assert not is_valid_obs_bid_key(OBS_BID_ROOT_PREFIX + "../etc/passwd")

    # 7. 单条全流程清洗测试
    raw_valid = {
        'title': '售后服务保障体系及7x24小时响应承诺',
        'text_preview': '我司针对本项目建立完善的售后服务保障体系……',
        'text': good_text,
        'project': '2026.01.20--中国建设银行龙集采项目',
        'obs_bid_file_key': OBS_BID_ROOT_PREFIX + '2026.01.20--中国建设银行龙集采项目/技术标.docx',
        'docx_label': '技术',
        'para_count': 4
    }
    cleaned = clean_and_extract_slice(raw_valid)
    assert cleaned is not None
    assert cleaned['customer_entity'] == '建设银行'
    assert cleaned['sub_topic'] == 'SLA_SERVICE'
    assert cleaned['quality_score'] >= 80
    assert cleaned['project_name'] == '中国建设银行龙集采项目'

    raw_short = dict(raw_valid, text="字数不足")
    assert clean_and_extract_slice(raw_short) is None

    raw_noise = dict(raw_valid, title="开标一览表")
    assert clean_and_extract_slice(raw_noise) is None

    print("self-test passed: 全部 7 组断言全部通过！")


# ---------------------------------------------------------------------------
# 主执行入口
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description='70 个历史项目标书特征深度抽取与清洗流水线')
    parser.add_argument('--self-test', action='store_true', help='运行内置自测套件后退出')
    parser.add_argument('--offline', action='store_true', help='离线测试模式：仅解析并打印提取统计，不落盘')
    parser.add_argument('--input-dir', default='/tmp/winbid_slices', help='切片 JSONL 输入目录')
    parser.add_argument('--output', default='./cleaned_cases.jsonl', help='清洗后特征 JSONL 输出文件')
    parser.add_argument('--limit', type=int, default=0, help='限制处理或输出条数（0 表示无限制）')

    args = parser.parse_args()

    if args.self_test:
        run_self_test()
        sys.exit(0)

    if not os.path.isdir(args.input_dir):
        print(f"输入目录不存在: {args.input_dir}。可使用 --self-test 验证脚本核心逻辑。")
        sys.exit(1)

    files = sorted(f for f in os.listdir(args.input_dir) if f.startswith('project_') and f.endswith('.jsonl'))
    if not files:
        print(f"在 {args.input_dir} 中未找到 project_*.jsonl 切片文件。")
        sys.exit(1)

    print(f"开始处理 {len(files)} 个项目的切片文件...")
    total_read = 0
    total_kept = 0
    sub_topic_counts = {}
    customer_counts = {}

    out_file = None if args.offline else open(args.output, 'w', encoding='utf-8')

    try:
        for fname in files:
            fpath = os.path.join(args.input_dir, fname)
            with open(fpath, 'r', encoding='utf-8') as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    total_read += 1
                    try:
                        raw = json.loads(line)
                    except Exception:
                        continue

                    cleaned = clean_and_extract_slice(raw)
                    if cleaned is not None:
                        total_kept += 1
                        st = cleaned['sub_topic']
                        cust = cleaned['customer_entity']
                        sub_topic_counts[st] = sub_topic_counts.get(st, 0) + 1
                        if cust:
                            customer_counts[cust] = customer_counts.get(cust, 0) + 1

                        if out_file is not None:
                            out_file.write(json.dumps(cleaned, ensure_ascii=False) + '\n')

                        if args.limit > 0 and total_kept >= args.limit:
                            break
            if args.limit > 0 and total_kept >= args.limit:
                break
    finally:
        if out_file is not None:
            out_file.close()

    print("\n--- 处理完成统计 ---")
    print(f"总读取切片数: {total_read}")
    print(f"清洗后保留高质量案例数: {total_kept} (保留率: {total_kept/max(1, total_read)*100:.1f}%)")
    print("\n业务子主题分布:")
    for st, cnt in sorted(sub_topic_counts.items(), key=lambda x: x[1], reverse=True):
        print(f"  - {st}: {cnt} 条")
    print("\n主要客户实体覆盖Top 10:")
    for cust, cnt in sorted(customer_counts.items(), key=lambda x: x[1], reverse=True)[:10]:
        print(f"  - {cust}: {cnt} 条")

    if not args.offline:
        print(f"\n清洗结果已输出至: {args.output}")


if __name__ == '__main__':
    main()
