"""PDF DocumentArtifact tests for score parsing evidence pipeline.

Input: tests/fixtures/sample_multipage.pdf
Output: schemaVersion=2.2 DocumentArtifact with PDF fileType and cell/text coordinates.
Position: sidecar tests — guards project 1381 PDF score-table routing.
"""
from __future__ import annotations

import os
import re

import fitz
import pdfplumber
import pytest

from converter import extract_pdf_tables_artifact
from converter import _is_pdf_score_table
from converter import _is_pdf_page_border_table
from converter import _is_genuine_header_row
from converter import _merge_pdf_score_table_continuations
from converter import _pdf_chapter_heading
from converter import _pdf_page_chapter_heading_positions
from converter import _pdf_last_chapter_event
from converter import _pdf_page_unresolved_section_break_tops
from converter import _pdf_page_tables
from converter import _pdf_section_heading
from converter import _pdf_table_section

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "sample_multipage.pdf")
PAGE_BORDER_FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "page_border_continuation.pdf")
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


def test_scoring_part_heading_inside_scoring_table_is_not_a_chapter_boundary():
    class Table:
        bbox = (0, 100, 500, 300)

        @staticmethod
        def extract():
            return [["序号", "评分因素", "评分标准", "分值"],
                    ["1", "技术方案", "方案得分20分", "20分"]]

    class Page:
        @staticmethod
        def extract_text_lines():
            return [{"text": "第一部分：技术评审（合计20分）", "top": 120}]

        @staticmethod
        def find_tables():
            return [Table()]

    page = Page()

    assert _pdf_page_chapter_heading_positions(page) == []
    assert _pdf_page_unresolved_section_break_tops(page) == []


def test_real_chapter_heading_inside_scoring_table_stays_a_chapter_boundary():
    class Table:
        bbox = (0, 100, 500, 300)

        @staticmethod
        def extract():
            return [["序号", "评分因素", "评分标准", "分值"],
                    ["1", "技术方案", "方案得分20分", "20分"]]

    class Page:
        @staticmethod
        def extract_text_lines():
            return [{"text": "第四章：合同履约考核", "top": 120}]

        @staticmethod
        def find_tables():
            return [Table()]

    page = Page()

    assert _pdf_page_chapter_heading_positions(page) == [(120.0, "第四章：合同履约考核")]


def test_part_heading_inside_table_boundary_is_not_section_break():
    class Table:
        bbox = (0, 100, 500, 300)

        @staticmethod
        def extract():
            return [["序号", "评分因素", "评分标准", "分值"],
                    ["1", "技术方案", "方案得分20分", "20分"]]

    class PageInside:
        @staticmethod
        def extract_text_lines():
            return [
                {"text": "第一部分", "top": 120},
                {"text": "第二部分：其他要求", "top": 180},
            ]

        @staticmethod
        def find_tables():
            return [Table()]

    class PageOutside:
        @staticmethod
        def extract_text_lines():
            return [{"text": "第三部分 合同履约考核", "top": 50}]

        @staticmethod
        def find_tables():
            return [Table()]

    assert _pdf_page_unresolved_section_break_tops(PageInside()) == []
    assert _pdf_page_unresolved_section_break_tops(PageOutside()) == [50.0]


def test_line_on_table_border_with_tolerance_is_inside_table():
    class Table:
        bbox = (0, 100, 500, 300)

    class PageBorder:
        @staticmethod
        def extract_text_lines():
            # Line top is slightly outside (99.5) but bottom is inside (109.5)
            return [{"text": "第一部分", "top": 99.5, "bottom": 109.5}]

        @staticmethod
        def find_tables():
            return [Table()]

    assert _pdf_page_unresolved_section_break_tops(PageBorder()) == []


def test_part_heading_in_weakly_structured_but_explicit_scoring_table_is_recognized():
    class Table:
        bbox = (0, 100, 500, 300)

        @staticmethod
        def extract():
            return [["", "", "", "报价评分标准\n办公用品价格分评审（40分）\n价格分（满分40分）=一级集采商品价格分（满分28分）\n第一部分：一级集采商品（合计28分）"]]

    class Page:
        @staticmethod
        def extract_text_lines():
            return [{"text": "第一部分：一级集采商品（合计28分）", "top": 160}]

        @staticmethod
        def find_tables():
            return [Table()]

    page = Page()

    assert not _is_pdf_score_table(Table.extract())
    assert _pdf_page_unresolved_section_break_tops(page) == []


def test_pdf_artifact_emits_ocr_text_with_physical_page_and_region(tmp_path):
    image_pdf = fitz.open()
    image_page = image_pdf.new_page(width=595, height=842)
    # Calibrated real rendering: full-page placement yields a 63.9-confidence token.
    # 400-point width preserves image-area detection and gives >96 at production 2x OCR.
    image_page.insert_image(fitz.Rect(0, 0, 400, 566), filename=CHINESE_OCR_FIXTURE)
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


@pytest.mark.parametrize("line", [
    "第七章 评分标准 " + "." * 28 + "- 52 -",
    "第七章 评分标准………— 52 —",
    "第七章 评分标准 - 52 -",
])
def test_pdf_headings_reject_toc_hyphen_page_numbers(line):
    assert _pdf_chapter_heading(line) == ""
    assert _pdf_section_heading(line) == ""
    assert _pdf_section_heading(line + "\n第七章 评分标准") == "第七章 评分标准"


def test_pdf_chapter_heading_rejects_wide_dot_leader_characters():
    for leader in "…·•‥∙．⋯⋮⋱․‧":
        assert _pdf_chapter_heading("第三章 评审办法" + leader * 3 + "27") == "", leader
    # 单个点号不构成点线，不能把正文标题误杀
    assert _pdf_chapter_heading("第三章 评审办法. 补充说明") == "第三章 评审办法. 补充说明"


def test_pdf_chapter_heading_rejects_toc_lines_without_dot_leaders():
    assert _pdf_chapter_heading("第三章 评审办法 27") == ""
    assert _pdf_chapter_heading("第三章 评审办法\t96") == ""
    # 有意偏向：标题以裸页码收尾的合法章（如「第三章 附件 3」）会被拒 → 该章转人工复核，
    # 换取的是「无点线目录行」这一真实常见形状不再成为方案根。
    assert _pdf_chapter_heading("第三章 附件 3") == ""
    # 年份结尾的正文标题不应被当作目录行
    assert _pdf_chapter_heading("第三章 2026 年度评审办法") == "第三章 2026 年度评审办法"


class _LinePage:
    """Page double exposing only line geometry (no tables)."""

    def __init__(self, lines):
        self._lines = [{"text": text, "top": top} for text, top in lines]

    def extract_text_lines(self):
        return self._lines


def test_pdf_page_chapter_heading_positions_rejects_toc_and_non_chapter_lines():
    page = _LinePage([
        ("目  录", 40.0),
        ("第三章 评审办法 " + "." * 30 + " 27", 60.0),
        ("3.2.3 评分标准", 80.0),
        ("第四章 合同履约考核", 400.0),
    ])

    assert _pdf_page_chapter_heading_positions(page) == [(400.0, "第四章 合同履约考核")]
    assert _pdf_page_chapter_heading_positions(object()) == []


def test_pdf_page_section_break_tops_flag_only_chapters_without_a_usable_anchor():
    cases = {
        "第四章 合同履约考核": False,          # 可转锚点 → 继承合法
        "第4章 合同履约考核": False,           # 阿拉伯数字编号同样被后端接受
        "3.2.3 评分标准": False,               # 非章级行，不阻断继承
        "第三部分 合同履约考核": True,          # 后端只认「章」→ 锚点不可用
        "第三编 合同履约考核": True,            # 章级容器（编/篇）同样不可转锚点
        "第五篇 附件": True,
        "第三章": True,                        # 无标题 → 后端不认 → 锚点不可用
        "第四章 合同履约考核" + "." * 30 + " 96": True,   # 目录行 → 拒绝转锚点
        "第四章 " + "合" * 60: True,            # 超长标题 → 拒绝转锚点
        "第一节 评分标准": False,               # 章内层级：页首出现不掐断本页继承
        "第十条 付款方式": False,
        # 识别面必须严格宽于锚点式：这些行「看起来是新的一章」但转不成锚点，
        # 漏掉任何一条都会让外章表格继承上一章并被错误采纳（评审 F-1）。
        "第3 章 合同履约考核": True,            # 数字与「章」之间有空白
        "第 十一 章 合同履约考核": True,         # 「第」与数字间、数字内部都有空白
        "第 三 章 评审办法": True,
        "第一百零一章 合同履约考核": True,        # 「零」不在锚点式的窄字符集内
        "第一〇一章 合同履约考核": True,          # 「〇」同理
        "第一千二百三十四章 合同履约考核": True,   # 7 个数字字符：量词不能给识别面设上限（评审 F-2）
        "第一百零一亿章 合同履约考核": True,      # 数字类完整：万亿同属中文数字字符集
        "第五卷 商务卷": True,                  # 卷/册同属章级容器
        "第三册 附件": True,
        # 反向：非章级行不得因为放宽而误掐断继承
        "第二十一条 规定说明": False,
        "第一次修改部分内容": False,
        "第三方部分内容": False,
        # 全角数字章号**可转锚点**，故不在此列：锚点原样写出文档自己的章标题
        # （见 _PDF_CHAPTER_HEADING 注释）。把它收紧成 ASCII 会让这类表格丢掉本来成立的
        # 方案归属（评审第 3 轮 F-3），是修复目标的相反方向。
        "第３章 合同履约考核": False,
    }
    for text, expected in cases.items():
        page = _LinePage([(text, 100.0)])
        assert bool(_pdf_page_unresolved_section_break_tops(page)) is expected, text

    # 位置随表返回：只掐断断点下方的表格（见 _pdf_table_section）
    assert _pdf_page_unresolved_section_break_tops(_LinePage([("第三部分 合同履约考核", 100.0)])) == [100.0]
    assert _pdf_page_unresolved_section_break_tops(object()) == []


def test_pdf_tables_attribute_nearest_preceding_chapter_heading(tmp_path):
    pdf_path = tmp_path / "sectioned.pdf"
    _make_sectioned_pdf(pdf_path)

    artifact = extract_pdf_tables_artifact(str(pdf_path))

    assert [t["sourcePage"] for t in artifact["tables"]] == [2, 3, 4, 5]
    assert [t["sourceSection"] for t in artifact["tables"]] == [
        "第三章 评审办法", "第三章 评审办法", "第三章 评审办法", "第四章 合同履约考核",
    ]
    # 第 4 页表格位于本章标题（top=400）上方 → 归属上一章，验证按位置而非页级首标题归属


# 后端 SOURCE_HEADING 的章级分支：`侧写出的 sourceSection` 必须能被它命中，否则锚点被拒。
BACKEND_CHAPTER_HEADING = re.compile(r"^第[一二三四五六七八九十百0-9]+章\s*\S")


def _make_pdf_with_unrecognised_chapter(path, chapter_line):
    """Chapter 3 is a usable anchor; the next chapter's title is one the backend does not accept."""
    doc = fitz.open()
    page2 = doc.new_page()
    page2.insert_text((72, 72), "第三章 评审办法", fontsize=14, fontname="china-s")
    _draw_grid(page2, 110, [["序号", "评审因素", "说明"], ["1", "供应商业绩", "详见正文"]])
    page3 = doc.new_page()
    page3.insert_text((72, 72), chapter_line, fontsize=14, fontname="china-s")
    _draw_grid(page3, 110, [["序号", "考核项", "说明"], ["1", "响应率", "按季度统计"]])
    page4 = doc.new_page()
    _draw_grid(page4, 72, [["序号", "考核项"], ["2", "投诉率"]])
    doc.save(str(path))
    doc.close()


@pytest.mark.parametrize("chapter_line", [
    "第三部分 合同履约考核",   # 后端只认「章」→ 不可转锚点
    "第三编 合同履约考核",     # 章级容器（编/篇）同样不可转锚点
    "第三篇 合同履约考核",
    "第3 章 合同履约考核",      # 数字与「章」间有空白（评审第 2 轮 F-1 的具体输入）
    "第一百零一章 合同履约考核",  # 「零」不在锚点式的窄字符集内
    "第一千二百三十四章 合同履约考核",  # 超过 6 个数字字符（评审第 3 轮 F-2）
    "第五卷 商务卷",
])
def test_pdf_tables_on_unrecognised_chapter_page_do_not_inherit_previous_chapter(tmp_path, chapter_line):
    """无法转成锚点的章标题页必须掐断跨页继承。

    继承会把该章表格挂到上一章，后端对 sourceSection 命中的表格判 IN_SCHEME 并据此采纳 ——
    外章表格被静默算进方案，比"没有锚点转人工"危险得多。"""
    pdf_path = tmp_path / "unrecognised-chapter.pdf"
    _make_pdf_with_unrecognised_chapter(pdf_path, chapter_line)

    tables = extract_pdf_tables_artifact(str(pdf_path))["tables"]

    assert [t["sourcePage"] for t in tables] == [1, 2, 3]
    assert tables[0]["sourceSection"] == "第三章 评审办法"
    assert {t["sourceSection"] for t in tables[1:]} == {""}


def test_pdf_score_table_continuation_is_not_merged_across_an_unrecognised_chapter(tmp_path):
    """评审第 3 轮 F-1：把章节置空只是第一步，续表合并会把空章节当通配值并把外章行并回前一章。

    `_same_or_unknown_section` 容许空章节桥接（续页重复标题是例外、不是常态），所以"边界已断开"
    必须是随表传递的独立信号（`sectionBoundaryCut`），不能靠空字符串表达——否则合并后要沿用
    前一张表的 `sourceSection`，外章评分项就被挂回前一章并被采纳。"""
    pdf_path = tmp_path / "continuation-after-cut.pdf"
    doc = fitz.open()
    page1 = doc.new_page()
    page1.insert_text((72, 72), "第三章 评审办法", fontsize=14, fontname="china-s")
    _draw_grid(page1, 110, [["序号", "评分因素", "评分标准"], ["1", "供应商业绩（3分）", "每项1分，最高3分"]])
    page2 = doc.new_page()
    page2.insert_text((72, 72), "第三部分 合同履约考核", fontsize=14, fontname="china-s")
    _draw_grid(page2, 110, [["2", "响应率（3分）", "最高得3分"], ["3", "投诉率（2分）", "最高得2分"]])
    doc.save(str(pdf_path))
    doc.close()

    tables = extract_pdf_tables_artifact(str(pdf_path))["tables"]

    assert [t["sourcePage"] for t in tables] == [1, 2]
    assert [t["sourceSection"] for t in tables] == ["第三章 评审办法", ""]


def test_pdf_table_above_an_unrecognised_chapter_line_keeps_the_previous_chapter(tmp_path):
    """掐断只针对断点下方的表格：断点上方的表格仍在已知章节内。

    页级一刀切会把断点上方的表格也清空，白白丢掉本可成立的归属（评审第 3 轮 F-4 的过判面）。"""
    pdf_path = tmp_path / "table-above-break.pdf"
    doc = fitz.open()
    page1 = doc.new_page()
    page1.insert_text((72, 72), "第三章 评审办法", fontsize=14, fontname="china-s")
    _draw_grid(page1, 110, [["序号", "评审因素", "说明"], ["1", "供应商业绩", "详见正文"]])
    page2 = doc.new_page()
    _draw_grid(page2, 72, [["序号", "评审因素", "说明"], ["2", "财务状况", "详见正文"]])
    page2.insert_text((72, 400), "第三部分 合同履约考核", fontsize=14, fontname="china-s")
    doc.save(str(pdf_path))
    doc.close()

    sections = [t["sourceSection"] for t in extract_pdf_tables_artifact(str(pdf_path))["tables"]]

    assert sections == ["第三章 评审办法", "第三章 评审办法"]


def test_pdf_score_table_continuation_is_not_merged_across_a_page_boundary_cut(tmp_path):
    """自审发现：断点在第 2 页末尾时，第 3 页的续表会丢掉"边界已断开"信号。

    第 2 页出现章级断点后 `current_section` 被清空，第 3 页的表格**继承的是空串**（自己没有断点），
    若 `inherited_cut` 不随页传递，它就长得像一张"章节未知、可桥接"的表 → 合并且沿用第 1 页的
    `sourceSection`，外章行被挂回前一章。断点必须活到下一个锚点为止。"""
    pdf_path = tmp_path / "cross-page-cut.pdf"
    doc = fitz.open()
    page1 = doc.new_page()
    page1.insert_text((72, 72), "第三章 评审办法", fontsize=14, fontname="china-s")
    _draw_grid(page1, 110, [["序号", "评分因素", "评分标准"], ["1", "供应商业绩（3分）", "每项1分，最高3分"]])
    page2 = doc.new_page()
    _draw_grid(page2, 72, [["2", "财务状况（2分）", "最高得2分"], ["3", "响应率（3分）", "最高得3分"]])
    page2.insert_text((72, 700), "第三部分 合同履约考核", fontsize=14, fontname="china-s")
    page3 = doc.new_page()
    _draw_grid(page3, 110, [["4", "投诉率（2分）", "最高得2分"], ["5", "服务承诺（3分）", "最高得3分"]])
    doc.save(str(pdf_path))
    doc.close()

    tables = extract_pdf_tables_artifact(str(pdf_path))["tables"]

    # 第 1、2 页同属第三章（合法续表 → 合并）；第 3 页属叫不出名字的那一章（断点未跨页失效）
    assert [t["sourcePage"] for t in tables] == [1, 3]
    assert [t["sourceSection"] for t in tables] == ["第三章 评审办法", ""]


def test_pdf_table_section_cuts_only_below_an_unrecognised_chapter_line():
    """归属由**最近的在前**章级事件决定：可能是锚点，也可能是转不成锚点的断点。"""
    headings = [(100.0, "第三章 评审办法"), (400.0, "第四章 合同履约考核")]

    # 断点（200）在第三章标题之后：断点下第一张表仍归第三章，断点下第二张表叫不出名字
    assert _pdf_table_section(headings, [200.0], 150.0, "第二章 其他",
                              inherited_cut=False) == ("第三章 评审办法", False)
    assert _pdf_table_section(headings, [200.0], 300.0, "第三章 评审办法",
                              inherited_cut=False) == ("", True)
    # 断点之后又读到锚点 → 锚点更近，表格归它
    assert _pdf_table_section(headings, [200.0], 420.0, "第三章 评审办法",
                              inherited_cut=False) == ("第四章 合同履约考核", False)
    # 锚点与断点同行（top 相等）→ 顺序不可判，按本文件的一贯方向取断点（宁可漏归属也不冒错误采纳）
    assert _pdf_table_section([(200.0, "第三章 评审办法")], [200.0], 300.0, "第三章 评审办法",
                              inherited_cut=False) == ("", True)
    # 无行几何：按页级规则，本页任何章级行都算在表格之前
    assert _pdf_table_section(headings, [200.0], None, "第三章 评审办法",
                              inherited_cut=False) == ("", True)
    # 本页毫无章级信号 → 继承上一页的章
    assert _pdf_table_section([], [], 300.0, "第三章 评审办法",
                              inherited_cut=False) == ("第三章 评审办法", False)
    # 继承来的空章节若带着上一页的断点 → 断点随页传递（否则合并会把外章行挂回前一章）
    assert _pdf_table_section([], [], 300.0, "", inherited_cut=True) == ("", True)
    assert _pdf_table_section([], [], None, "", inherited_cut=True) == ("", True)
    # 本页读到锚点 → 章节有了名字，继承来的断点到此结束
    assert _pdf_table_section(headings, [], 300.0, "", inherited_cut=True) == ("第三章 评审办法", False)


def test_pdf_last_chapter_event_prefers_the_lowest_position_and_breaks_on_ties():
    """页内"最后一个章级事件"只有一处定义（评审第 4 轮 F-1：页末与逐表两处曾各写一套）。"""
    anchors = [(100.0, "第三章 评审办法"), (400.0, "第四章 合同履约考核")]

    assert _pdf_last_chapter_event(anchors, []) == (400.0, "第四章 合同履约考核", False)
    assert _pdf_last_chapter_event([], [200.0]) == (200.0, "", True)
    # 顺序：断点在前、锚点在后 → 锚点是最后一个事件（页末状态必须取它，否则下一页丢归属）
    assert _pdf_last_chapter_event(anchors, [200.0]) == (400.0, "第四章 合同履约考核", False)
    # 顺序：锚点在前、断点在后 → 断点是最后一个事件（跨页掐断必须继续生效）
    assert _pdf_last_chapter_event([(100.0, "第三章 评审办法")], [200.0]) == (200.0, "", True)
    # 同行并列（top 相等）→ 不可判，取断点
    assert _pdf_last_chapter_event([(200.0, "第三章 评审办法")], [200.0]) == (200.0, "", True)
    # limit_top（逐表查询）：只算表格上方的事件
    assert _pdf_last_chapter_event(anchors, [200.0], 300.0) == (200.0, "", True)
    assert _pdf_last_chapter_event(anchors, [200.0], 150.0) == (100.0, "第三章 评审办法", False)
    # 本页毫无章级信号 → None（调用方保持原状态）
    assert _pdf_last_chapter_event([], []) is None


def test_pdf_page_ending_on_an_anchor_does_not_cut_the_next_page(tmp_path):
    """评审第 4 轮 F-1：页末状态取"本页最后一个章级事件"，而不是"本页出现过断点"。

    第 2 页先在页首出现不可命名断点、其后又读到有效锚点「第四章 评分办法」——第四章已被点名，
    第 3 页无标题的续表必须继承它并按合法续表合并。页末只要看到断点就清空，会让已命名章节的
    合法续页丢掉 `sourceSection` → 后端判 `SOURCE_UNVERIFIED` → 转人工（用户可见症状）。
    反方向（本页最后一个事件是断点 → 跨页仍须掐断）由
    `test_pdf_score_table_continuation_is_not_merged_across_a_page_boundary_cut` 覆盖。"""
    pdf_path = tmp_path / "break-then-anchor.pdf"
    doc = fitz.open()
    page1 = doc.new_page()
    page1.insert_text((72, 72), "第三章 评审办法", fontsize=14, fontname="china-s")
    _draw_grid(page1, 110, [["序号", "评分因素", "评分标准"], ["1", "供应商业绩（3分）", "每项1分，最高3分"]])
    page2 = doc.new_page()
    page2.insert_text((72, 60), "第三部分 合同履约考核", fontsize=14, fontname="china-s")
    page2.insert_text((72, 150), "第四章 评分办法", fontsize=14, fontname="china-s")
    _draw_grid(page2, 200, [["序号", "评分因素", "评分标准"], ["2", "财务状况（2分）", "最高得2分"]])
    page3 = doc.new_page()
    _draw_grid(page3, 110, [["3", "响应率（3分）", "最高得3分"]])
    doc.save(str(pdf_path))
    doc.close()

    tables = extract_pdf_tables_artifact(str(pdf_path))["tables"]

    assert [t["sourcePage"] for t in tables] == [1, 2]
    assert [t["sourceSection"] for t in tables] == ["第三章 评审办法", "第四章 评分办法"]
    # 合并确实发生了（第 3 页的续行并进了第四章那张表），不是"两张表恰好章节都不一样"
    assert tables[1]["rows"] == 3


def test_pdf_table_sections_are_accepted_by_backend_chapter_heading_pattern(tmp_path):
    """接缝契约：sidecar 写出的 sourceSection 必须与后端能接受的章标题同构。"""
    pdf_path = tmp_path / "sectioned.pdf"
    _make_sectioned_pdf(pdf_path)

    sections = [t["sourceSection"] for t in extract_pdf_tables_artifact(str(pdf_path))["tables"]]

    assert sections == [
        "第三章 评审办法", "第三章 评审办法", "第三章 评审办法", "第四章 合同履约考核",
    ]
    for section in sections:
        assert BACKEND_CHAPTER_HEADING.match(section), section


def test_pdf_full_width_chapter_number_is_emitted_verbatim_as_an_anchor():
    """接缝契约：锚点原样写出文档自己的章标题，不替后端改写数字形态。

    后端把方案标题与 `sourceSection` 做**字符串**比对（`OriginalScoringSchemePolicy.tableBelongsTo`），
    而方案标题既可能来自正文标题（通用 Markdown 标题分支不看数字形态），也可能由表格自身的
    `sourceSection` 依 `isValidSchemeRoot` 建立（`schemeTitleForTable`）——两侧同源时全角章号本就能
    正常归属。曾按"后端 SOURCE_HEADING 只认 ASCII"把锚点数字类收紧成 `0-9`，结果是这类表格的
    `sourceSection` 变空、`!section.isBlank()` 分支不成立 → 丢掉本来成立的方案归属（评审第 3 轮 F-3）。"""
    assert _pdf_chapter_heading("第３章 合同履约考核") == "第３章 合同履约考核"
    assert _pdf_chapter_heading("第１２章 评分办法") == "第１２章 评分办法"


@pytest.mark.parametrize("line", [
    "第3 章 合同履约考核",        # 数字与「章」间有空白：锚点要求「第X章」连续
    "第一百零一章 合同履约考核",   # 「零」不在锚点式的数字字符集内
    "第三部分 合同履约考核",       # 「部分」不是「章」
])
def test_pdf_chapter_lines_outside_the_anchor_grammar_are_never_emitted(line):
    """不可转锚点的章级行必须留在"_PDF_SECTION_BREAK 掐断继承 → 转人工"一侧。"""
    assert not BACKEND_CHAPTER_HEADING.match(line), line
    assert _pdf_chapter_heading(line) == ""


def test_pdf_section_heading_skips_toc_dot_leader_lines():
    assert _pdf_section_heading("第三章 评审办法 " + "." * 30 + " 27") == ""
    assert _pdf_section_heading("\n".join([
        "目  录",
        "第三章 评审办法 " + "." * 30 + " 27",
        "第四章 合同履约考核 " + "." * 30 + " 96",
    ])) == ""
    assert _pdf_section_heading("第三章 评审办法\n3.2 评分标准") == "第三章 评审办法"


def test_pdf_section_heading_rejects_toc_lines_without_dot_leaders():
    """OCR 文本路径与表格路径共用同一条「目录行不是标题」规则（同一个 `_PDF_TOC_LEADER`）。

    两条路径的偏向前提一致：漏掉一个章锚点只是转人工复核，把目录行当章锚点则会让
    `current_section` 继承链把外章内容挂到本方案下。"""
    assert _pdf_section_heading("第三章 评审办法 27") == ""
    assert _pdf_section_heading("第四章 合同履约考核 96") == ""
    # 有意偏向的另一面：标题以裸 1-3 位数字收尾的正文行同样被拒（转人工）
    assert _pdf_section_heading("第三章 附件 3") == ""
    # 四位年份结尾的正文标题不受影响
    assert _pdf_section_heading("第四章 2026 年度考核") == "第四章 2026 年度考核"


class _TablePageWithFailingFinder:
    """Page double whose table finder raises.

    pdfplumber's `Page.extract_tables()` runs through the same finder as `find_tables()`, so a
    finder failure is not recoverable at this layer and must surface rather than be reported as
    "page has no tables" — an empty list would silently drop every fact on the page. The double
    deliberately has no `extract_tables()`: nothing may be consulted after the finder fails."""

    def find_tables(self):
        raise RuntimeError("table finder unavailable")


def test_pdf_page_tables_propagates_table_finder_failure():
    with pytest.raises(RuntimeError, match="table finder unavailable"):
        _pdf_page_tables(_TablePageWithFailingFinder())


class _TablePageWithoutFinder:
    """Page double exposing only `extract_tables()` — tables but no geometry.

    The no-coordinate path is not a failure path: the table is still returned (with `top=None`),
    so the caller inherits the previous chapter instead of losing the table entirely."""

    def __init__(self, rows):
        self._rows = rows

    def extract_tables(self):
        return [self._rows]


def test_pdf_page_tables_without_a_finder_keep_tables_without_geometry():
    rows = [["序号", "评审因素"], ["1", "供应商业绩"]]
    assert _pdf_page_tables(_TablePageWithoutFinder(rows)) == [(None, rows)]


def test_pdf_score_table_continuation_not_merged_across_known_sections_or_a_cut_boundary():
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
    cut_section = {
        "sourcePage": 25,
        "sourceTableIndex": 8,
        "sourceSection": "",
        "sectionBoundaryCut": True,
        "rows": [["2", "类似业绩（10分）", "每有一个类似服务案例得2分，满分为10分。"]],
    }

    assert len(_merge_pdf_score_table_continuations([first, same_section])) == 1
    assert len(_merge_pdf_score_table_continuations([first, other_section])) == 2
    # 章节被断点清空 → 合并会让续行沿用前一张表的 `sourceSection`，等于把外章行挂回本方案
    assert len(_merge_pdf_score_table_continuations([first, cut_section])) == 2


def test_pdf_score_table_continuation_allows_empty_first_cell():
    first = {
        "sourcePage": 34,
        "sourceTableIndex": 0,
        "sourceSection": "第三章 评审办法",
        "rows": [
            ["条款号及名称", "评分因素", "评分标准", "分值"],
            ["3.2.3（1）", "商务评分标准", "供应商业绩（7分）", "相关服务业绩"],
        ],
    }
    continuation_blank_first_cell = {
        "sourcePage": 35,
        "sourceTableIndex": 1,
        "sourceSection": "第三章 评审办法",
        "rows": [
            ["", "", "", "财务会计报表复印件（满分3分）"],
            ["3.2.3（2）", "技术评分标准", "综合服务方案（10分）", "服务方案评分"],
        ],
    }
    merged = _merge_pdf_score_table_continuations([first, continuation_blank_first_cell])
    assert len(merged) == 1
    assert len(merged[0]["rows"]) == 4
    assert merged[0]["rows"][2][3] == "财务会计报表复印件（满分3分）"


def test_pdf_page_tables_skips_full_page_border_pseudo_table():
    class FakeTable:
        def __init__(self, bbox, rows):
            self.bbox = bbox
            self._rows = rows
        def extract(self):
            return self._rows

    class FakePage:
        width = 595.25
        height = 841.9
        def __init__(self, tables):
            self._tables = tables
        def find_tables(self):
            return self._tables

    border_table = FakeTable(
        bbox=(0.0, 0.0, 600.0, 900.0),
        rows=[["页眉/边框说明"], ["页脚说明"]]  # cols = 1, >=90% of page
    )
    real_table = FakeTable(
        bbox=(55.0, 100.0, 550.0, 750.0),
        rows=[["条款号", "评分因素", "评分标准", "分值"], ["1", "技术", "方案详实", "10分"]]
    )

    page = FakePage([border_table, real_table])
    tables = _pdf_page_tables(page)
    # The full-page 1-column pseudo-table must be skipped
    assert len(tables) == 1
    top, rows = tables[0]
    assert top == 100.0
    assert len(rows) == 2
    assert len(rows[0]) == 4


def test_pdf_score_table_continuation_allows_purely_descriptive_continuation_rows():
    first = {
        "sourcePage": 46,
        "sourceTableIndex": 0,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["条款号", "评分因素", "评分标准", "分值"],
            ["2.2.3（2）", "技术评分标准", "对接能力和方案（10分）", "对接技术方案成熟先进"],
        ],
    }
    # Continuation page has no new score numbers; it only continues the description text
    continuation_pure_desc = {
        "sourcePage": 47,
        "sourceTableIndex": 1,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["", "", "", "对接接口详细规范、数据同步频率及异常恢复时限等满足要求。"],
            ["", "", "", "完整覆盖上述核心内容，另一项仅作提及但无实质响应扣分。"],
        ],
    }
    merged = _merge_pdf_score_table_continuations([first, continuation_pure_desc])
    assert len(merged) == 1
    assert len(merged[0]["rows"]) == 4
    assert "对接接口详细规范" in merged[0]["rows"][2][3]


def test_pdf_score_table_continuation_allows_table_notes_continuation():
    first = {
        "sourcePage": 48,
        "sourceTableIndex": 0,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["条款号", "评分因素", "评分标准", "分值"],
            ["2.2.3（3）", "投标报价评分标准（35分）", "投标报价（35分）", "得分计算公式如下..."],
        ],
    }
    continuation_notes = {
        "sourcePage": 49,
        "sourceTableIndex": 1,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["注：1-Bn大于J时，投标人扣分最多扣7分", "", "", ""],
            ["需要补充的其他内容：", "", "", ""],
        ],
    }
    merged = _merge_pdf_score_table_continuations([first, continuation_notes])
    assert len(merged) == 1
    assert len(merged[0]["rows"]) == 4
    assert "注：1-Bn大于J时" in merged[0]["rows"][2][0]


def test_real_pdf_page_border_skipped_and_score_continuation_merged():
    """Environment-level test using real PDF binary with pdfplumber extraction.

    Guards:
    1. Full-page border frame (>=90% page dimensions) is detected as pseudo-table by pdfplumber
       and successfully skipped by `_is_pdf_page_border_table`.
    2. Multi-page scoring tables across pages are not severed by decorative border tables.
    3. Continuation table with empty first cell and [8-5) half-open interval score merges seamlessly.
    4. Table footnote starting with '注：' does not false-trigger as a genuine score header.
    """
    assert os.path.exists(PAGE_BORDER_FIXTURE), f"Missing golden fixture: {PAGE_BORDER_FIXTURE}"

    with pdfplumber.open(PAGE_BORDER_FIXTURE) as pdf:
        assert len(pdf.pages) == 2
        for page in pdf.pages:
            tables = page.find_tables()
            assert len(tables) == 2, "Real pdfplumber must detect 1 border table and 1 content table per page"
            # Verify border detection classifies accurately on real pdfplumber Table objects
            border_tables = [t for t in tables if _is_pdf_page_border_table(t, page)]
            content_tables = [t for t in tables if not _is_pdf_page_border_table(t, page)]
            assert len(border_tables) == 1, "Exactly one pseudo-table border per page"
            assert len(content_tables) == 1, "Exactly one real table per page"

    artifact = extract_pdf_tables_artifact(PAGE_BORDER_FIXTURE)
    assert artifact["schemaVersion"] == "2.2"
    # Border pseudo-tables skipped, real tables merged across pages into 1 logical table
    assert len(artifact["tables"]) == 1, "Tables on page 1 and page 2 must merge into a single score table"

    score_table = artifact["tables"][0]
    assert score_table["isScoreTable"] is True
    assert score_table["rows"] == 6
    assert score_table["columns"] == 4

    cell_texts = [c["rawText"] for c in score_table["cells"]]
    # Verify continuous items across both pages
    assert any("2.2.3（1" in t for t in cell_texts)
    assert any("2.2.3（2" in t for t in cell_texts)
    assert any("售后服务" in t for t in cell_texts)
    assert any("2.2.3（3" in t for t in cell_texts)
    assert any("[8-5）分" in t for t in cell_texts)
    assert any("注：评分标准中括号表示边界分值。" in t for t in cell_texts)


def test_two_column_page_border_with_binding_line_skipped():
    """Attack vector: full-page border with left vertical binding line (装订线).

    When a PDF page border includes a binding margin or double vertical line,
    pdfplumber detects a 2-column pseudo-table. `_is_pdf_page_border_table` must
    correctly classify and filter it out instead of passing it to table pipeline.
    """
    class FakeTable:
        def __init__(self, bbox, rows):
            self.bbox = bbox
            self._rows = rows
        def extract(self):
            return self._rows

    class FakePage:
        width = 595.28
        height = 841.89
        def __init__(self, tables):
            self._tables = tables
        def find_tables(self):
            return self._tables

    two_col_border = FakeTable(
        bbox=(25.0, 25.0, 570.0, 815.0),
        rows=[
            ["装订线", "贵州能源集团采购项目 招标文件"],
            ["装订线", "第 1 页"],
        ]
    )
    page = FakePage([two_col_border])
    assert _is_pdf_page_border_table(two_col_border, page) is True


def test_footnote_with_scoring_keywords_not_misclassified_as_score_header():
    """Footnote starting with '注：' containing '评分标准' must not be classified as a table header."""
    footnote_row = ["注：评分标准中括号表示边界分值。", "", "", ""]
    assert _is_genuine_header_row(footnote_row) is False


def test_empty_first_cell_category_row_with_scores_not_misclassified_as_header():
    """A category continuation row starting with empty cell and containing score keywords
    must NOT be classified as a table column header.
    """
    category_row = ["", "技术评分标准（45分）", "实施方案与进度计划（45分）", "45分"]
    assert _is_genuine_header_row(category_row) is False


def test_pdf_score_table_continuation_merges_across_interleaved_noise_table():
    """Real-world resilience: continuation merge must tolerate interleaved noise tables
    (e.g. 1-row page header/footer/sign box) between pages without severing the scoring chain.
    """
    t1 = {
        "sourcePage": 1,
        "sourceTableIndex": 0,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["条款号", "评分因素", "评分标准", "分值"],
            ["1", "商务评分标准（20分）", "企业业绩（20分）", "20分"],
        ],
    }
    # Interleaved noise table at top of page 2 (e.g. page header/doc title box)
    t_noise = {
        "sourcePage": 2,
        "sourceTableIndex": 0,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["贵州能源集团采购项目", "文件编号：0637-266002041125"],
        ],
    }
    # Real continuation score table on page 2
    t_cont = {
        "sourcePage": 2,
        "sourceTableIndex": 1,
        "sourceSection": "第三章 评标办法",
        "rows": [
            ["", "技术评分标准（45分）", "系统方案（45分）", "45分"],
            ["2", "投标报价评分标准（35分）", "基准价计算公式", "35分"],
        ],
    }

    merged = _merge_pdf_score_table_continuations([t1, t_noise, t_cont])
    # Must produce 2 tables:
    # 1. Main score table with 4 rows (t1 + t_cont merged)
    # 2. Interleaved noise table preserved intact
    assert len(merged) == 2
    assert merged[0]["rows"][0] == ["条款号", "评分因素", "评分标准", "分值"]
    assert len(merged[0]["rows"]) == 4
    assert merged[0]["rows"][2][1] == "技术评分标准（45分）"
    assert merged[0]["rows"][3][1] == "投标报价评分标准（35分）"
    # Noise table preserved as separate non-interfering table
    assert merged[1]["rows"] == [["贵州能源集团采购项目", "文件编号：0637-266002041125"]]


def test_spatial_containment_identifies_multi_column_page_border_enclosing_inner_table():
    """Spatial topology: any table spanning >=90% page dimensions that geometrically
    encloses another table on the page is conclusively an outer border container,
    even if it has 3 columns (e.g. Logo | Title | DocNumber in header box).
    """
    class FakeTable:
        def __init__(self, bbox, rows):
            self.bbox = bbox
            self._rows = rows
        def extract(self):
            return self._rows

    # 3-column outer frame (e.g. Logo | Title | DocNumber)
    outer_3col_border = FakeTable(
        bbox=(25.0, 25.0, 570.0, 815.0),
        rows=[
            ["Logo", "贵州能源集团采购项目", "DocNo: 0637"],
            ["正文区域", "正文区域", "正文区域"],
        ],
    )
    # Inner scoring table completely inside the outer frame
    inner_table = FakeTable(
        bbox=(50.0, 100.0, 540.0, 400.0),
        rows=[
            ["条款号", "评分因素", "评分标准", "分值"],
            ["1", "商务标", "业绩", "10分"],
        ],
    )

    class FakePage:
        width = 595.28
        height = 841.89
        def __init__(self, tables):
            self._tables = tables
        def find_tables(self):
            return self._tables

    page = FakePage([outer_3col_border, inner_table])

    # Outer 3-col border must be recognized as page border via spatial containment
    assert _is_pdf_page_border_table(outer_3col_border, page) is True
    # Inner table must NOT be recognized as page border
    assert _is_pdf_page_border_table(inner_table, page) is False



def test_repeated_header_continuation_preserves_source_pages_and_joins_closed_score_table():
    header = ["序号", "评价维度", "评分细则", "分值"]
    tables = [
        {"sourcePage": 9, "sourceTableIndex": 0, "sourceSection": "评分规则", "rows": [header, ["1", "价格", "价格得60分", "60"], ["2", "服务", "服务得40分", "40"]]},
        {"sourcePage": 10, "sourceTableIndex": 1, "sourceSection": "评分规则", "rows": [header, ["", "", "补充服务要求", ""], ["3", "说明", "不计分", "0"], ["4", "合计", "", "100"]]},
    ]
    merged = _merge_pdf_score_table_continuations(tables)
    assert len(merged) == 1
    assert merged[0]["rows"].count(header) == 1
    assert [r["page"] for r in merged[0]["sourceRows"]] == [9, 9, 9, 10, 10, 10]
    assert merged[0]["rows"][-1] == ["4", "合计", "", "100"]


@pytest.mark.parametrize("change", ["reset", "gap", "nonadjacent", "different_header", "closed", "total_score", "different_section", "unknown_section", "unicode_code"])
def test_repeated_header_never_merges_independent_or_unproven_tables(change):
    header = ["序号", "评价维度", "评分细则", "分值"]
    first = {"sourcePage": 9, "sourceTableIndex": 0, "sourceSection": "评分规则", "rows": [header, ["1", "价格", "价格得60分", "60"], ["2", "服务", "服务得40分", "40"]]}
    second = {"sourcePage": 10, "sourceTableIndex": 1, "sourceSection": "评分规则", "rows": [header.copy(), ["", "", "续页描述", ""], ["3", "能力", "能力得10分", "10"]]}
    if change == "reset": second["rows"][2][0] = "1"
    if change == "gap": second["rows"][2][0] = "5"
    if change == "nonadjacent": second["sourcePage"] = 11
    if change == "different_header": second["rows"][0][1] = "评审项目"
    if change == "closed": first["rows"].append(["3", "合计", "", "100"]); second["rows"][2][0] = "4"
    if change == "total_score": first["rows"].append(["3", "总分", "", "100"]); second["rows"][2][0] = "4"
    if change == "different_section": second["sourceSection"] = "另一评分方案"
    if change == "unknown_section": first["sourceSection"] = ""; second["sourceSection"] = ""
    if change == "unicode_code": second["rows"][2][0] = "²"
    assert len(_merge_pdf_score_table_continuations([first, second])) == 2


def test_arabic_numbered_score_heading_anchors_and_next_section_cuts_scope():
    class Page:
        def extract_text_lines(self):
            return [
                {"text": "7 招标评分规则（投标人提供材料索引）", "top": 30, "bottom": 40},
                {"text": "8 评标流程", "top": 500, "bottom": 510},
            ]
        def find_tables(self):
            return []

    page = Page()
    headings = _pdf_page_chapter_heading_positions(page)
    breaks = _pdf_page_unresolved_section_break_tops(page)
    assert headings == [(30.0, "7 招标评分规则（投标人提供材料索引）")]
    assert breaks == [500.0]
    assert _pdf_table_section(headings, breaks, 100.0, "", inherited_cut=False) == (headings[0][1], False)
    assert _pdf_table_section(headings, breaks, 520.0, "", inherited_cut=False) == ("", True)


def test_numbered_scoring_heading_inside_table_is_not_a_section_anchor():
    class Table:
        bbox = (0, 20, 500, 200)
        def extract(self): return [["序号", "评价维度", "评分细则", "分值"]]
    class Page:
        def extract_text_lines(self): return [{"text": "7 招标评分规则", "top": 40, "bottom": 50}]
        def find_tables(self): return [Table()]
    assert _pdf_page_chapter_heading_positions(Page()) == []
    assert _pdf_page_unresolved_section_break_tops(Page()) == []
