"""PDF DocumentArtifact tests for score parsing evidence pipeline.

Input: tests/fixtures/sample_multipage.pdf
Output: schemaVersion=2.2 DocumentArtifact with PDF fileType and cell/text coordinates.
Position: sidecar tests — guards project 1381 PDF score-table routing.
"""
from __future__ import annotations

import os
import re

import fitz
import pytest

from converter import extract_pdf_tables_artifact
from converter import _is_pdf_score_table
from converter import _merge_pdf_score_table_continuations
from converter import _pdf_chapter_heading
from converter import _pdf_page_chapter_heading_positions
from converter import _pdf_page_has_unresolved_section_break
from converter import _pdf_page_tables
from converter import _pdf_section_heading

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


def test_pdf_page_section_break_flags_only_chapters_without_a_usable_anchor():
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
        "第３章 合同履约考核": True,             # 全角数字：后端只认 ASCII → 不可写出去
        "第五卷 商务卷": True,                  # 卷/册同属章级容器
        "第三册 附件": True,
        # 反向：非章级行不得因为放宽而误掐断继承
        "第二十一条 规定说明": False,
        "第一次修改部分内容": False,
        "第三方部分内容": False,
    }
    for text, expected in cases.items():
        page = _LinePage([(text, 100.0)])
        assert _pdf_page_has_unresolved_section_break(page) is expected, text

    assert _pdf_page_has_unresolved_section_break(object()) is False


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
    "第3 章 合同履约考核",      # 数字与「章」间有空白（评审 F-1 的具体输入）
    "第一百零一章 合同履约考核",  # 「零」不在锚点式的窄字符集内
    "第３章 合同履约考核",       # 全角数字：锚点式只认 ASCII，写出去后端也解析不了
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


@pytest.mark.parametrize("line", [
    "第３章 合同履约考核",       # 全角数字：后端 SOURCE_HEADING 只认 ASCII
    "第3 章 合同履约考核",        # 数字与「章」间有空白：后端要求「第X章」连续
    "第一百零一章 合同履约考核",   # 「零」不在后端窄字符集内
])
def test_pdf_chapter_anchor_is_never_emitted_when_the_backend_cannot_parse_it(line):
    """接缝契约的另一半：不可被后端命中的行也不得被写成 sourceSection。

    Python 的 `\\d` 是 Unicode 数字类（连全角「３」也认），后端 `SOURCE_HEADING` 只认 ASCII `0-9`；
    若锚点式沿用 `\\d`，sidecar 就会写出后端自己解析不了的章节标题——两层对"章标题语法"的口径
    不一致。这些行必须留在"_PDF_SECTION_BREAK 掐断继承 → 转人工"一侧。"""
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
