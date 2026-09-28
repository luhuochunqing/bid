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
