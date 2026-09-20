"""Golden tests for converter PDF conversion (real pdfplumber path).

Input:  tests/fixtures/sample_multipage.pdf (3-page PDF, committed static fixture).
Output: Assertions that (a) the legacy whole-document markdown is byte-identical to
        convert_pdf_document()["markdown"] (scoreparse consumes it — highest-blast-radius
        contract), and (b) pages are 1-based physical slices consumed by the compliance
        quality-check pipeline (Java DocumentContent.pages).
Position: test suite; run with `pytest tests/ -v` from the sidecar directory.
"""
from __future__ import annotations

import os

import converter
from converter import convert_pdf_document, convert_pdf_to_markdown, convert_pdf_to_pages

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "sample_multipage.pdf")


def test_markdown_byte_identical_between_legacy_and_document_api():
    """scoreparse 消费 convert_pdf_to_markdown；新 API 的 markdown 必须与其字节级相等。"""
    legacy = convert_pdf_to_markdown(FIXTURE)
    combined = convert_pdf_document(FIXTURE)

    assert combined["markdown"] == legacy
    assert "SAMPLE BID DOCUMENT" in combined["markdown"]
    # 报价表跨页合并后处理仍生效（page 2 表格保留 pipe 结构）
    assert "|" in combined["markdown"]


def test_pages_are_physical_one_based_slices():
    """pages 为 1-based 物理页切片：页号连续、页内容互不串页。"""
    result = convert_pdf_document(FIXTURE)
    pages = result["pages"]

    assert [p["pageNumber"] for p in pages] == [1, 2, 3]
    assert "PAGE 1" in pages[0]["text"]
    assert "PAGE 2" in pages[1]["text"]
    assert "PAGE 3" in pages[2]["text"]
    # 物理边界：各页互不包含其他页的标记
    for i, page in enumerate(pages):
        for j, other in enumerate(pages):
            if i != j:
                marker = f"PAGE {other['pageNumber']}"
                assert marker not in page["text"], f"page {i + 1} leaked marker of page {j + 1}"


def test_pages_text_is_subset_of_merged_markdown():
    """每页 text 均为合并 markdown 的内容来源（join 前后内容守恒，无字符注入）。"""
    result = convert_pdf_document(FIXTURE)
    joined = "\n\n".join(str(p["text"]) for p in result["pages"])

    for page in result["pages"]:
        # 空白差异容忍（join 只加分隔符），逐页首行必须原样出现在合并文本中
        first_line = page["text"].splitlines()[0]
        assert first_line in result["markdown"]
    assert len(joined) <= len(result["markdown"]) + 8  # \x0c→\n\n 替换可能引入少量差异


def test_pdf_chunk_default_is_100_and_env_overridable(monkeypatch):
    """spec 066 D4：默认分块 100 页（重开成本占解析 90%），环境变量可覆盖。"""
    monkeypatch.delenv("SIDECAR_PDF_CHUNK_PAGES", raising=False)
    assert converter._pdf_chunk_pages() == 100

    monkeypatch.setenv("SIDECAR_PDF_CHUNK_PAGES", "7")
    assert converter._pdf_chunk_pages() == 7

    monkeypatch.setenv("SIDECAR_PDF_CHUNK_PAGES", "not-a-number")
    assert converter._pdf_chunk_pages() == 100
