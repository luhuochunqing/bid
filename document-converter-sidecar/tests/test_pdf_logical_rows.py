"""Business rows retain their physical locations across adjacent PDF pages."""
import copy
import pytest
from converter import _link_pdf_logical_rows


def table(page, rows, section="第三章 评审办法"):
    return {"tableId": f"p{page}", "sourcePage": page, "sourceSection": section,
            "columns": 4, "rows": len(rows), "cells": [
                {"cellId": f"p{page}-r{r}-c{c}", "row": r, "column": c,
                 "sourcePage": page, "sourceXmlPath": f"pdf:page={page};row={r};col={c}",
                 "rawText": text, "normalizedText": text}
                for r, row in enumerate(rows) for c, text in enumerate(row)]}


def test_adjacent_empty_identity_continuation_keeps_physical_coordinates():
    tables = [table(7, [["", "", "综合服务方案（10分）", "售前服务方案及"]]),
              table(8, [["", "", "", "售后服务方案，最高10分。"],
                        ["", "", "下一项（2分）", "最高2分"]])]
    original = copy.deepcopy(tables)
    _link_pdf_logical_rows(tables)
    assert tables[0]["cells"][0]["logicalRowId"] == tables[1]["cells"][0]["logicalRowId"]
    assert tables[1]["cells"][4]["logicalRowId"] != tables[1]["cells"][0]["logicalRowId"]
    for after, before in zip(tables[1]["cells"], original[1]["cells"]):
        assert after["sourceXmlPath"] == before["sourceXmlPath"]
        assert after["sourcePage"] == before["sourcePage"]
        assert after["rawText"] == before["rawText"]


@pytest.mark.parametrize("page,section,name", [(9,"第三章 评审办法",""), (8,"第四章 合同",""),
    (8,"",""), (8,"第三章 评审办法","新评分项（3分）")])
def test_unproven_continuity_is_not_linked(page,section,name):
    tables = [table(7, [["", "", "综合服务方案（10分）", "售前服务方案及"]]),
              table(page, [["", "", name, "售后服务方案"]],section)]
    _link_pdf_logical_rows(tables)
    assert tables[0]["cells"][0]["logicalRowId"] != tables[1]["cells"][0]["logicalRowId"]


def test_intra_table_empty_prefix_continuation_linked():
    tables = [table(34, [
        ["3.2.3（2）", "技术评分标准", "针对办公用品的综合服务方案（10分）", "1.综合考察供应商售前服务能力，须提供售前服务方案，包括但不限于重大物流"],
        ["", "", "", "配送纠纷解决方案及周期、重大质量纠纷解决方案及周期等。按照以上方案评分。1.评价为优的得8-10分；"],
        ["", "", "针对办公用品的产品质量及价格管控方案（10分）", "1.综合考察供应商对推送至通用电子商城商品价格合规管控能力"]
    ])]
    _link_pdf_logical_rows(tables)
    # Row 0 and Row 1 share logicalRowId
    assert tables[0]["cells"][0]["logicalRowId"] == tables[0]["cells"][4]["logicalRowId"]
    # Row 2 is independent
    assert tables[0]["cells"][0]["logicalRowId"] != tables[0]["cells"][8]["logicalRowId"]


def test_rule_column_before_score_column_cross_page_linked():
    """When the table has columns [序号, 评分项名称, 评分标准, 分值], rule column is col 2 (not col -1).

    Continuation row on next page has text in col 2, while col 0, 1, 3 are empty.
    It must link to previous page's row.
    """
    tables = [
        table(14, [
            ["序号", "评分项名称", "评分标准", "分值"],
            ["9", "服务响应（10分）", "1.能够支持采购人历史交易数据导入...以及系统操作及结果", "10"]
        ]),
        table(15, [
            ["", "", "显示视频。）2.投标人能够提供售前、售中、售后服务，得3分，提供承诺书，格式自拟，盖章有效。3.投标人能够提供人员培训，得2分，提供承诺书，格式自拟，盖章有效。", ""],
            ["10", "质量验收评价扣分及相关情况说明", "1.按照日常考核扣分...", "0"]
        ])
    ]
    _link_pdf_logical_rows(tables)
    # Row 1 of page 14 (cells[4..7]) and Row 0 of page 15 (cells[0..3]) must share logicalRowId
    assert tables[0]["cells"][4]["logicalRowId"] == tables[1]["cells"][0]["logicalRowId"]
    # Row 1 of page 15 (cells[4..7]) is a new item and must have a different logicalRowId
    assert tables[1]["cells"][4]["logicalRowId"] != tables[1]["cells"][0]["logicalRowId"]


def test_rule_column_before_score_column_intra_table_linked():
    """Within a single table, if rule column is before score column, continuation row must link."""
    tables = [
        table(14, [
            ["序号", "评审因素", "评审细则", "满分"],
            ["1", "资质证书（5分）", "具备国家级高新技术企业证书得3分，具备ISO9001证书", "5"],
            ["", "", "得2分。", ""],
            ["2", "财务能力（5分）", "资产负债率低于60%得5分", "5"]
        ])
    ]
    _link_pdf_logical_rows(tables)
    # Row 1 (cells[4..7]) and Row 2 (cells[8..11]) must share logicalRowId
    assert tables[0]["cells"][4]["logicalRowId"] == tables[0]["cells"][8]["logicalRowId"]
    # Row 3 (cells[12..15]) is independent
    assert tables[0]["cells"][4]["logicalRowId"] != tables[0]["cells"][12]["logicalRowId"]

