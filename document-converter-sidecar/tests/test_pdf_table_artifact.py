"""PDF DocumentArtifact tests for score parsing evidence pipeline.

Input: tests/fixtures/sample_multipage.pdf
Output: schemaVersion=2.2 DocumentArtifact with PDF fileType and cell/text coordinates.
Position: sidecar tests — guards project 1381 PDF score-table routing.
"""
from __future__ import annotations

import os

import fitz

from converter import extract_pdf_tables_artifact
from converter import _is_pdf_score_table
from converter import _merge_pdf_score_table_continuations
from converter import _pdf_chapter_heading
from converter import _pdf_page_chapter_heading

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "sample_multipage.pdf")
CHINESE_OCR_FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "scanned_chinese_score.png")


def test_extract_pdf_tables_artifact_returns_pdf_document_artifact():
    artifact = extract_pdf_tables_artifact(FIXTURE)

    assert artifact["schemaVersion"] == "2.2"
    assert artifact["artifact"]["fileType"] == "PDF"
    assert artifact["artifact"]["extractionVersion"] == "pdf-table-v2"
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


def test_pdf_artifact_emits_ocr_text_with_physical_page_and_region(tmp_path):
    image_pdf = fitz.open()
    image_page = image_pdf.new_page(width=595, height=842)
    image_page.insert_image(image_page.rect, filename=CHINESE_OCR_FIXTURE)
    path = tmp_path / "ocr-only.pdf"
    image_pdf.save(path)
    image_pdf.close()

    artifact = extract_pdf_tables_artifact(str(path))

    blocks = artifact["textBlocks"]
    matches = [block for block in blocks if "技术评分标准" in "".join(block["text"].split())]
    assert len(matches) == 1
    block = matches[0]
    assert block["sourcePage"] == 1
    assert block["confidence"] >= 70
    assert block["bounds"]["width"] > 0
    assert block["bounds"]["height"] > 0
    assert block["bounds"]["imageWidth"] > 0
    assert block["bounds"]["imageHeight"] > 0


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


def test_pdf_score_table_deep_header_and_multipage_continuation_merged_for_project_1117_shape():
    tables = [
        {
            "sourcePage": 42,
            "sourceTableIndex": 16,
            "rows": [
                ["2", "投标内容", "符合第二章“投标人须知”第1.3.1项规定", "", "重要指标"],
                ["3", "投标有效期", "符合第二章“投标人须知”第3.3.1项规定", "", "重要指标"],
                ["4", "投标保证金", "符合第二章“投标人须知”第3.4.1项规定", "", "重要指标"],
                ["条款号", "条款内容", "编列内容", "", "分值"],
                ["2.3.1", "分值构成", "是否设置权重：是 （如选择“是”，则“分值构成”中的各项分值", "", "总分100分"],
                ["2.3.2", "评标基准价计算方法", "详见本表“2.3.4（4）”", "", "/"],
                ["2.3.3", "投标报价的偏差率计算公式", "详见本表“2.3.4（4）”", "", "/"],
                ["2.3.4（1） 技术评审", "", "", "", ""],
                ["序号", "评分因素", "评分标准", "分值", "最高得分是否唯一"],
                ["1", "管理体系", "1、评审标准：1）具有 ISO9001 质量管理体系认证、ISO14001 环境管理体系认证...", "10.0", "否"],
            ],
        },
        {
            "sourcePage": 43,
            "sourceTableIndex": 17,
            "rows": [
                ["2", "公司业绩", "评审标准：1）每提供一个有效业绩得5分，最高得分30分。2）业绩需提供合同首页...", "30.0", "否"],
                ["3", "仓储能力", "1、评审标准：1）投标人需提供指定六大地区内各一个配送仓库，面积不低于...", "25.0", "否"],
                ["4", "线上业务能力", "1、评审标准：1）投标人具有成熟电商平台运营操作能力，得5分...", "5.0", "否"],
            ],
        },
        {
            "sourcePage": 44,
            "sourceTableIndex": 18,
            "rows": [
                ["5", "企业荣誉", "1、评审标准：1）具有中国品牌信誉评价认证五星级证书；企业信用AAA级证书...", "5.0", "否"],
                ["6", "服务方案", "1、评审标准：1）评价所有涉及本项目配送服务的各方面方案，如配送时效保障措施...", "15.0", "否"],
                ["7", "应急方案", "1、评审标准：1）承诺在接到平台订单后5天内到货，缺货/补货24小时内响应...", "10.0", "否"],
            ],
        },
        {
            "sourcePage": 44,
            "sourceTableIndex": 19,
            "rows": [
                ["偏差率"] + [""] * 38,
                ["价格评审", "", "评标价计算方式：不含税投标总价"] + [""] * 36,
                ["", "", "价格得分计算方式：价格得分", "=M-[(", "Ⅰ投标价格"] + [""] * 34,
                ["", "", "M=100", "(", "价格评价分项满分值", ")"] + [""] * 33,
            ],
        },
    ]

    merged = _merge_pdf_score_table_continuations(tables)

    assert len(merged) == 2, "Tables 16, 17, 18 should be merged; formula table 19 kept separate"
    score_table = merged[0]
    formula_table = merged[1]

    assert len(score_table["rows"]) == 16
    assert _is_pdf_score_table(score_table["rows"]) is True
    assert _is_pdf_score_table(formula_table["rows"]) is False

    rows = score_table["rows"]
    assert rows[8] == ["序号", "评分因素", "评分标准", "分值", "最高得分是否唯一"]
    assert rows[9][0] == "1" and rows[9][1] == "管理体系" and rows[9][3] == "10.0"
    assert rows[10][0] == "2" and rows[10][1] == "公司业绩" and rows[10][3] == "30.0"
    assert rows[11][0] == "3" and rows[11][1] == "仓储能力" and rows[11][3] == "25.0"
    assert rows[12][0] == "4" and rows[12][1] == "线上业务能力" and rows[12][3] == "5.0"
    assert rows[13][0] == "5" and rows[13][1] == "企业荣誉" and rows[13][3] == "5.0"
    assert rows[14][0] == "6" and rows[14][1] == "服务方案" and rows[14][3] == "15.0"
    assert rows[15][0] == "7" and rows[15][1] == "应急方案" and rows[15][3] == "10.0"


def _draw_grid(page, top, rows, left=60.0, col_width=150.0, row_height=24.0):
    n_cols = max(len(r) for r in rows)
    width = col_width * n_cols
    height = row_height * len(rows)
    for i in range(n_cols + 1):
        x = left + i * col_width
        page.draw_line(fitz.Point(x, top), fitz.Point(x, top + height))
    for j in range(len(rows) + 1):
        y = top + j * row_height
        page.draw_line(fitz.Point(left, y), fitz.Point(left + width, y))
    for j, row in enumerate(rows):
        for i, text in enumerate(row):
            if text:
                page.insert_text(fitz.Point(left + i * col_width + 6, top + j * row_height + 17),
                                 text, fontsize=9, fontname="china-s")


def _make_sectioned_pdf(path):
    doc = fitz.open()
    toc = doc.new_page()
    toc.insert_text((72, 72), "目  录", fontsize=14, fontname="china-s")
    toc.insert_text((72, 110), "第三章 评审办法 " + "." * 40 + " 27", fontsize=11, fontname="china-s")
    toc.insert_text((72, 130), "第四章 合同履约考核 " + "." * 40 + " 96", fontsize=11, fontname="china-s")
    page2 = doc.new_page()
    page2.insert_text((72, 72), "第三章 评审办法", fontsize=14, fontname="china-s")
    _draw_grid(page2, 110, [["序号", "评审因素", "说明"], ["1", "供应商业绩", "详见正文"]])
    page3 = doc.new_page()
    _draw_grid(page3, 72, [["序号", "评审因素", "说明"], ["2", "财务状况", "详见正文"]])
    page4 = doc.new_page()
    _draw_grid(page4, 72, [["序号", "评审因素", "说明"], ["3", "服务网点", "详见正文"]])
    page4.insert_text((72, 400), "第四章 合同履约考核", fontsize=14, fontname="china-s")
    page5 = doc.new_page()
    _draw_grid(page5, 90, [["序号", "考核项", "说明"], ["1", "响应率", "按季度统计"]])
    doc.save(str(path))
    doc.close()


def test_pdf_chapter_heading_rejects_toc_dot_leader_lines():
    assert _pdf_chapter_heading("第三章 评审办法 " + "." * 28 + " 27") == ""
    assert _pdf_chapter_heading("第三章 评审办法………27") == ""
    assert _pdf_chapter_heading("第三章 评审办法") == "第三章 评审办法"
    assert _pdf_chapter_heading("3.2.3 评分标准") == ""
    assert _pdf_chapter_heading("") == ""


def test_pdf_page_chapter_heading_skips_toc_and_non_chapter_lines():
    page_text = "\n".join([
        "目  录",
        "第三章 评审办法 " + "." * 30 + " 27",
        "3.2.3 评分标准",
        "第四章 合同履约考核",
    ])

    assert _pdf_page_chapter_heading(page_text) == "第四章 合同履约考核"
    assert _pdf_page_chapter_heading("3.2.3 评分标准\n序号 评分因素 评分标准 分值") == ""
    assert _pdf_page_chapter_heading("") == ""


def test_pdf_tables_attribute_nearest_preceding_chapter_heading(tmp_path):
    pdf_path = tmp_path / "sectioned.pdf"
    _make_sectioned_pdf(pdf_path)

    artifact = extract_pdf_tables_artifact(str(pdf_path))

    assert [t["sourcePage"] for t in artifact["tables"]] == [2, 3, 4, 5]
    assert [t["sourceSection"] for t in artifact["tables"]] == [
        "第三章 评审办法", "第三章 评审办法", "第三章 评审办法", "第四章 合同履约考核",
    ]
    # 第 4 页表格位于本章标题（top=400）上方 → 归属上一章，验证按位置而非页级首标题归属


def test_pdf_score_table_continuation_not_merged_across_known_sections():
    first = {
        "sourcePage": 24,
        "sourceTableIndex": 7,
        "sourceSection": "第三章 评审办法",
        "rows": [
            ["序号", "评分因素", "评分标准"],
            ["1", "公司实力（6分）", "证书清单每项1分，最高6分。"],
        ],
    }
    same_section = {
        "sourcePage": 25,
        "sourceTableIndex": 8,
        "sourceSection": "第三章 评审办法",
        "rows": [["2", "类似业绩（10分）", "每有一个类似服务案例得2分，满分为10分。"]],
    }
    other_section = {
        "sourcePage": 25,
        "sourceTableIndex": 8,
        "sourceSection": "第四章 合同履约考核",
        "rows": [["2", "类似业绩（10分）", "每有一个类似服务案例得2分，满分为10分。"]],
    }

    assert len(_merge_pdf_score_table_continuations([first, same_section])) == 1
    assert len(_merge_pdf_score_table_continuations([first, other_section])) == 2
