"""PDF DocumentArtifact tests for score parsing evidence pipeline.

Input: tests/fixtures/sample_multipage.pdf
Output: schemaVersion=2.0 DocumentArtifact with PDF fileType and cell-level coordinates.
Position: sidecar tests — guards project 1381 PDF score-table routing.
"""
from __future__ import annotations

import os

from converter import extract_pdf_tables_artifact
from converter import _is_pdf_score_table
from converter import _merge_pdf_score_table_continuations

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "sample_multipage.pdf")


def test_extract_pdf_tables_artifact_returns_pdf_document_artifact():
    artifact = extract_pdf_tables_artifact(FIXTURE)

    assert artifact["schemaVersion"] == "2.0"
    assert artifact["artifact"]["fileType"] == "PDF"
    assert artifact["artifact"]["extractionVersion"] == "pdf-table-v1"
    assert artifact["artifact"]["sha256"]
    assert artifact["tables"], "fixture must expose at least one pdfplumber table"

    table = artifact["tables"][0]
    assert table["sourcePage"] >= 1
    assert table["rows"] >= 2
    assert table["columns"] >= 2
    assert table["cells"]
    assert all(str(cell["sourceXmlPath"]).startswith("pdf:page=") for cell in table["cells"])
    assert not any("<br>" in str(cell["rawText"]) for cell in table["cells"])


def test_pdf_score_table_detection_excludes_preliminary_review_table():
    rows = [
        ["条款号", "", "评审因素", "评审标准", "备注"],
        ["1", "资格评\n审标准", "营业执照", "真实有效，满分100分", ""],
        ["2", "形式评\n审标准", "投标人名称", "与营业执照一致（10分）", ""],
    ]

    assert _is_pdf_score_table(rows) is False


def test_pdf_score_table_continuation_is_merged_for_project_1381_shape():
    tables = [
        {
            "sourcePage": 24,
            "sourceTableIndex": 7,
            "rows": [
                ["序号", "评分因素", "评分标准"],
                ["1", "投标报价\n（40分）", "报价规则中含局部满分30分和满分10分。"],
                ["2", "公司综合实力\n（6分）", "证书清单每项1分，最高6分。"],
            ],
        },
        {
            "sourcePage": 25,
            "sourceTableIndex": 8,
            "rows": [
                ["3", "类似业绩\n（10分）", "每有一个类似服务案例得2分，满分为10分。"],
                ["4", "技术方案\n（10分）", "方案优秀得7.1-10.0分。"],
                ["4", "供货方案\n（10分）", "供货保障措施完整得10分。"],
                ["5", "人员配备\n（6分）", "人员配置优秀得4.1-6.0分。"],
                ["6", "质量管理方案（10分）", "质量管理方案优秀得10分。"],
                ["7", "售后服务方案（8分）", "售后服务优秀得8分。"],
                ["合计\n得分", "满分\n（100分）", ""],
            ],
        },
    ]

    merged = _merge_pdf_score_table_continuations(tables)

    assert len(merged) == 1
    rows = merged[0]["rows"]
    assert len(rows) == 10
    assert rows[3][1].startswith("类似业绩")
    assert rows[-1][0].startswith("合计")
    assert _is_pdf_score_table(rows) is True
