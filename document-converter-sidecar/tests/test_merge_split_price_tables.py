# Input: converter._merge_split_price_tables（跨页报价表合并后处理）
# Output: 合并边界行为回归（评审 !2399：无关表不融合 / 链式合并 / 列数校验 / 重复表头）
# Pos: document-converter-sidecar/tests — 纯函数测试，无 I/O 依赖
"""报价评分跨页表合并的回归测试。

背景（2026-08-25 评审 !2399）：初版合并启发式存在两个缺陷——
1. "空首格 + 无评分关键词即合并" 会把页边界处语义无关的表融合（数据损坏）；
2. 只支持两块成对合并，跨 3 页以上的价格表仍然截断。
本文件锚定收紧后的边界行为。
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from converter import _merge_split_price_tables


def _count_blocks(markdown: str) -> int:
    blocks = 0
    in_block = False
    for line in markdown.split("\n"):
        is_pipe = line.strip().startswith("|")
        if is_pipe and not in_block:
            blocks += 1
        in_block = is_pipe
    return blocks


PRICE_TABLE_P1 = """| 序号 | 评分因素 | 分值 |
| --- | --- | --- |
| 1 | 价格分计算方法 | 30 |
| 2 | 基准价 = 平均价 × 0.98 | 20 |
"""

PRICE_TABLE_CONT = """| 3 | 下浮率 K | 20 |
| --- | --- | --- |
"""

PRICE_TABLE_CONT2 = """| 4 | BP% = 100 - | 15 |
| --- | --- | --- |
"""


def test_two_page_price_table_merged():
    md = PRICE_TABLE_P1 + "\n" + PRICE_TABLE_CONT
    assert _count_blocks(_merge_split_price_tables(md)) == 1


def test_three_page_price_table_chain_merged():
    # 评审 !2399 CRITICAL：链式合并，跨 3 页不再只合并前两块
    md = PRICE_TABLE_P1 + "\n" + PRICE_TABLE_CONT + "\n" + PRICE_TABLE_CONT2
    assert _count_blocks(_merge_split_price_tables(md)) == 1


def test_four_page_price_table_chain_merged():
    cont3 = """| 5 | HP% 系数 | 10 |
| --- | --- | --- |
"""
    md = PRICE_TABLE_P1 + "\n" + PRICE_TABLE_CONT + "\n" + PRICE_TABLE_CONT2 + "\n" + cont3
    assert _count_blocks(_merge_split_price_tables(md)) == 1


def test_unrelated_adjacent_tables_not_merged():
    # 评审 !2399 CRITICAL：资格表 + 空首格人员表（无价格关键词）不得融合
    md = """| 资格条件 | 要求 | 备注 |
| --- | --- | --- |
| 营业执照 | 有效 | 必须 |
| 安全生产许可证 | 有效 | 必须 |

|  | 姓名 | 职称 |
| --- | --- | --- |
| 项目经理 | 张三 | 高工 |
"""
    assert _count_blocks(_merge_split_price_tables(md)) == 2


def test_column_count_mismatch_not_merged():
    # 列数不一致（6 列主表 vs 3 列价格表）不得拼接成畸形表
    md = """| 序号 | 参数 | L1 | L2 | RH | RL |
| --- | --- | --- | --- | --- | --- |
| 1 | 下浮率 | 3 | 6 | 1 | 0 |

| 报价人 | 得分 |
| --- | --- |
| 甲公司 | 95 |
"""
    assert _count_blocks(_merge_split_price_tables(md)) == 2


def test_java_only_keywords_continuation_merged():
    # Python 侧关键词集与 Java PRICE_PARAMS 同步（含 报价人/评标基准价）后续表仍可合并
    md = PRICE_TABLE_P1 + "\n| 3 | 评标基准价 = 报价人平均价 | 20 |\n| --- | --- | --- |\n"
    assert _count_blocks(_merge_split_price_tables(md)) == 1


def test_repeated_header_continuation_merged_without_duplicate_header():
    # 跨页重复表头：合并后只保留一份表头
    md = PRICE_TABLE_P1 + "\n| 序号 | 评分因素 | 分值 |\n| --- | --- | --- |\n| 3 | 下浮率 K | 20 |\n"
    out = _merge_split_price_tables(md)
    assert _count_blocks(out) == 1
    assert out.count("| 序号 | 评分因素 | 分值 |") == 1


def test_gap_over_three_blank_lines_not_merged():
    # 间隔 >3 空行视为不同区域，即便含价格关键词也保守不合并
    md = PRICE_TABLE_P1 + "\n\n\n\n" + PRICE_TABLE_CONT
    assert _count_blocks(_merge_split_price_tables(md)) == 2


def test_text_without_pipes_untouched():
    md = "第一章 招标公告\n\n本项目为信息化建设。\n"
    assert _merge_split_price_tables(md) == md


def test_short_token_substring_not_merged():
    # 短词按子串匹配会把含 "L1级" 的无关技术表误合并；
    # 短词必须词边界匹配（与 Java 侧 PRICE_PARAM_ROW 短词纪律一致）
    md = """| 项目 | 描述 |
| --- | --- |
| 资质 | 一级 |

| 等级 | 说明 |
| --- | --- |
| L1级 | 缓存加速 |
"""
    assert _count_blocks(_merge_split_price_tables(md)) == 2


def test_short_token_number_suffix_not_merged():
    # "L10" 中的子串 "L1" 不得触发合并（编号后缀数字）
    md = """| 项目 | 描述 |
| --- | --- |
| 资质 | 一级 |

| 编号 | 说明 |
| --- | --- |
| L10 | 某等级 |
"""
    assert _count_blocks(_merge_split_price_tables(md)) == 2


def test_short_token_word_boundary_merged():
    # 短词独立成词（后随空格/管道）且列数一致时仍可识别为参数续表
    md = """| 维度 | 内容 | 分值 |
| --- | --- | --- |
| 报价评分 | 价格分计算方法 | 30 |

|  | 参数 | 值 |
| --- | --- | --- |
|  | L1 | 3 |
"""
    assert _count_blocks(_merge_split_price_tables(md)) == 1


def test_short_token_rl_word_boundary_merged():
    md = """| 维度 | 内容 | 分值 |
| --- | --- | --- |
| 报价评分 | 方法 | 30 |

|  | 参数 | 值 |
| --- | --- | --- |
|  | RL | 0.5 |
"""
    assert _count_blocks(_merge_split_price_tables(md)) == 1
