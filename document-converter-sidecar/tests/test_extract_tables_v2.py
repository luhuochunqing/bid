"""Tests for the v2 DocumentArtifact contract (spec 059 Phase 1).

Validates:
- schemaVersion + artifact header (sha256, extractionVersion, fileType)
- Per-cell cellId uniqueness and stable XML path
- Vertical / horizontal / combined merge coverage in mergedRegions
- Legacy v1 contract remains available as a thin wrapper
- Edge cases: empty doc, corrupted file, non-docx, multiple tables
"""
from __future__ import annotations

import io
import os
import re
import sys
import tempfile

import pytest
from docx import Document
from docx.oxml.ns import qn

from table_extractor import extract_tables, extract_tables_artifact, SCHEMA_VERSION


def test_pdf_continuation_cells_expose_physical_page(monkeypatch, tmp_path):
    from converter import extract_pdf_tables_artifact

    page_tables = [
        [["序号", "评分因素", "分值"], ["1", "技术方案", "（20分）"]],
        [["2", "项目团队", "（10分）"]],
    ]

    class Page:
        def __init__(self, tables):
            self.tables = tables

        def extract_tables(self):
            return self.tables

    class Pdf:
        pages = [Page([tables]) for tables in page_tables]

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

    import types
    monkeypatch.setitem(sys.modules, "pdfplumber", types.SimpleNamespace(open=lambda _path: Pdf()))
    source = tmp_path / "continuation.pdf"
    source.write_bytes(b"test pdf bytes")

    artifact = extract_pdf_tables_artifact(str(source))
    cells = artifact["tables"][0]["cells"]

    assert {cell["rawText"]: cell.get("sourcePage") for cell in cells if cell["rawText"]} == {
        "序号": 1, "评分因素": 1, "分值": 1, "1": 1, "技术方案": 1, "（20分）": 1,
        "2": 2, "项目团队": 2, "（10分）": 2,
    }


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _write_docx(doc: Document) -> str:
    tmp = tempfile.NamedTemporaryFile(suffix=".docx", delete=False)
    doc.save(tmp.name)
    tmp.close()
    return tmp.name


def _set_v_merge(cell, val: str | None = None) -> None:
    tc_pr = cell._element.get_or_add_tcPr()
    vm = tc_pr.makeelement(qn("w:vMerge"), {})
    if val:
        vm.set(qn("w:val"), val)
    tc_pr.append(vm)


def _set_grid_span(cell, val: int) -> None:
    tc_pr = cell._element.get_or_add_tcPr()
    gs = tc_pr.makeelement(qn("w:gridSpan"), {qn("w:val"): str(val)})
    tc_pr.append(gs)


def _raw_tc(table, row_idx: int, tc_pos: int):
    """Get raw <w:tc> element at the given row and tc position, bypassing
    python-docx's grid_offset logic (which gets confused by merges)."""
    tr = table._tbl.findall(qn("w:tr"))[row_idx]
    return tr.findall(qn("w:tc"))[tc_pos]


def _raw_set_v_merge(table, row_idx: int, tc_pos: int, val: str | None = None) -> None:
    tc = _raw_tc(table, row_idx, tc_pos)
    tc_pr = tc.find(qn("w:tcPr"))
    if tc_pr is None:
        tc_pr = tc.makeelement(qn("w:tcPr"), {})
        tc.insert(0, tc_pr)
    vm = tc_pr.makeelement(qn("w:vMerge"), {})
    if val:
        vm.set(qn("w:val"), val)
    tc_pr.append(vm)


def _raw_set_grid_span(table, row_idx: int, tc_pos: int, val: int) -> None:
    tc = _raw_tc(table, row_idx, tc_pos)
    tc_pr = tc.find(qn("w:tcPr"))
    if tc_pr is None:
        tc_pr = tc.makeelement(qn("w:tcPr"), {})
        tc.insert(0, tc_pr)
    gs = tc_pr.makeelement(qn("w:gridSpan"), {qn("w:val"): str(val)})
    tc_pr.append(gs)


def _set_run_text(cell, text: str) -> None:
    """Append a <w:r><w:t>text</w:t></w:r> into the cell (forces <w:t> elements)."""
    from docx.oxml.ns import nsmap
    para = cell._element.find(qn("w:p"))
    if para is None:
        para = cell._element.makeelement(qn("w:p"), {})
        cell._element.append(para)
    run = para.makeelement(qn("w:r"), {})
    t = run.makeelement(qn("w:t"), {})
    t.text = text
    run.append(t)
    para.append(run)


# ---------------------------------------------------------------------------
# Schema / artifact header
# ---------------------------------------------------------------------------

class TestArtifactHeader:
    """Artifact header must always be present and well-formed."""

    def test_schema_version_is_2_0(self):
        doc = Document()
        doc.add_table(rows=1, cols=1).cell(0, 0).text = "x"
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            assert artifact["schemaVersion"] == "2.0"
            assert artifact["schemaVersion"] == SCHEMA_VERSION
        finally:
            os.unlink(path)

    def test_artifact_metadata_matches_file(self):
        doc = Document()
        doc.add_table(rows=1, cols=1).cell(0, 0).text = "评分"
        path = _write_docx(doc)
        try:
            import hashlib
            expected_sha = hashlib.sha256(open(path, "rb").read()).hexdigest()
            artifact = extract_tables_artifact(path)
            meta = artifact["artifact"]
            assert meta["fileType"] == "DOCX"
            assert meta["extractionVersion"] == "table-v2"
            assert meta["sha256"] == expected_sha
            assert meta["documentId"] == f"sha256:{expected_sha}"
        finally:
            os.unlink(path)

    def test_artifact_metadata_for_non_docx(self):
        tmp = tempfile.NamedTemporaryFile(suffix=".txt", delete=False)
        tmp.write(b"not a docx")
        tmp.close()
        try:
            artifact = extract_tables_artifact(tmp.name)
            assert artifact["schemaVersion"] == "2.0"
            assert artifact["artifact"]["fileType"] == "DOCX"
            # sha256 should still be computed for the uploaded file
            assert len(artifact["artifact"]["sha256"]) == 64
            assert artifact["tables"] == []
            assert len(artifact["warnings"]) >= 1
        finally:
            os.unlink(tmp.name)


# ---------------------------------------------------------------------------
# Cell-level metadata
# ---------------------------------------------------------------------------

class TestCellMetadata:
    """Each cell carries unique ID + stable XML path + raw + normalized text."""

    def test_cell_ids_unique(self):
        doc = Document()
        table = doc.add_table(rows=3, cols=4)
        for r in range(3):
            for c in range(4):
                table.cell(r, c).text = f"r{r}c{c}"
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            cell_ids = [cell["cellId"] for cell in tbl["cells"]]
            assert len(cell_ids) == len(set(cell_ids)), "cell IDs must be unique"
            for cell in tbl["cells"]:
                assert re.match(r"^t-\d{4}-r\d+-c\d+$", cell["cellId"])
        finally:
            os.unlink(path)

    def test_xml_path_format(self):
        doc = Document()
        table = doc.add_table(rows=2, cols=2)
        _set_run_text(table.cell(0, 0), "评分因素")
        _set_run_text(table.cell(0, 1), "分值")
        _set_run_text(table.cell(1, 0), "技术")
        _set_run_text(table.cell(1, 1), "30")
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            xml_paths = [c["sourceXmlPath"] for c in tbl["cells"]]
            assert all(
                re.match(r"^/w:document\[1\]/w:body\[1\]/w:tbl\[\d+\]/w:tr\[\d+\]/w:tc\[\d+\]$", p)
                for p in xml_paths
            ), f"unexpected XML path format: {xml_paths}"
        finally:
            os.unlink(path)

    def test_nested_paragraph_cell_text_concatenated(self):
        """单元格内嵌多段落文本必须完整进入证据文本（SYN-NESTED-PARA）。

        评分细则单元格常含多个 <w:p>；_tc_text 必须把所有段落的 <w:t>
        串联进 rawText，否则证据链断裂。
        """
        doc = Document()
        table = doc.add_table(rows=1, cols=2)
        cell = table.cell(0, 0)
        _set_run_text(cell, "优得4分；")
        cell.add_paragraph("差扣4分")
        _set_run_text(table.cell(0, 1), "8")
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            c0 = tbl["cells"][0]
            assert "优得4分" in c0["rawText"], c0
            assert "差扣4分" in c0["rawText"], "第二段文本丢失，证据链断裂"
            assert "优得4分" in c0["normalizedText"]
            assert "差扣4分" in c0["normalizedText"]
        finally:
            os.unlink(path)

    def test_raw_and_normalized_text(self):
        doc = Document()
        table = doc.add_table(rows=1, cols=1)
        _set_run_text(table.cell(0, 0), "  评分   因素\t(30分)  ")
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            cell = artifact["tables"][0]["cells"][0]
            # rawText is the stripped but whitespace-preserving text
            assert "评分" in cell["rawText"]
            assert "(30分)" in cell["rawText"]
            # normalizedText collapses internal whitespace
            assert cell["normalizedText"] == "评分 因素 (30分)"
        finally:
            os.unlink(path)


# ---------------------------------------------------------------------------
# Merged regions
# ---------------------------------------------------------------------------

class TestMergedRegions:
    """MergedRegion coordinates must reflect vMerge / gridSpan semantics."""

    def test_vmerge_continuation_recorded(self):
        doc = Document()
        table = doc.add_table(rows=3, cols=2)
        table.cell(0, 0).text = "评分因素"
        table.cell(0, 1).text = "分值"
        table.cell(1, 0).text = "技术方案"
        table.cell(1, 1).text = "30"
        table.cell(2, 0).text = ""
        table.cell(2, 1).text = "30"
        _set_v_merge(table.cell(1, 0), "restart")
        _set_v_merge(table.cell(2, 0))
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            regions = tbl["mergedRegions"]
            vregions = [r for r in regions if r["rowspan"] > 1]
            assert len(vregions) == 1
            assert vregions[0]["rowspan"] == 2
            assert vregions[0]["colspan"] == 1
            start = next(c for c in tbl["cells"] if c["cellId"] == vregions[0]["startCellId"])
            assert start["rawText"] == "技术方案"
            cont = next(c for c in tbl["cells"] if c["isMergedContinue"] and c["row"] == 2 and c["column"] == 0)
            assert cont["rawText"] == ""
        finally:
            os.unlink(path)

    def test_independent_column_merges_do_not_cross_contaminate(self):
        """两列在不同行各自纵向合并，跨度互不污染（审核 P0 回归）。

        旧实现按"该行存在任一 vMerge continuation"近似推断整行跨度：
        列 0（行 1-2 合并）的跨度会被列 1 在行 3/4 的 continuation
        误拉长到 4，随后 Java 端 PASS 4 覆盖校验会把正常表格判成
        STRUCTURE_INCOMPLETE。修复后按 gridSpan 累加定位到重启列，
        只认该列自己的 continuation。
        """
        doc = Document()
        table = doc.add_table(rows=5, cols=2)
        table.cell(0, 0).text = "评分因素"
        table.cell(0, 1).text = "分值"
        # 列 0 在行 1-2 合并；行 3、4 是普通行
        table.cell(1, 0).text = "技术方案"
        _set_v_merge(table.cell(1, 0), "restart")
        table.cell(2, 0).text = ""
        _set_v_merge(table.cell(2, 0))
        table.cell(3, 0).text = "A1"
        table.cell(4, 0).text = "A2"
        # 列 1 在行 3-4 合并；行 1、2 是普通行
        table.cell(1, 1).text = "10"
        table.cell(2, 1).text = "10"
        table.cell(3, 1).text = "商务部分"
        _set_v_merge(table.cell(3, 1), "restart")
        table.cell(4, 1).text = ""
        _set_v_merge(table.cell(4, 1))

        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            by_rc = {(c["row"], c["column"]): c for c in tbl["cells"]}
            # 两处合并各自的跨度都必须恰好为 2
            assert by_rc[(1, 0)]["rowspan"] == 2, by_rc[(1, 0)]
            assert by_rc[(3, 1)]["rowspan"] == 2, by_rc[(3, 1)]
            regions = {r["startCellId"]: r for r in tbl["mergedRegions"]}
            assert len(regions) == 2, regions
            assert regions[by_rc[(1, 0)]["cellId"]]["rowspan"] == 2
            assert regions[by_rc[(3, 1)]["cellId"]]["rowspan"] == 2
        finally:
            os.unlink(path)

    def test_gridspan_recorded(self):
        doc = Document()
        table = doc.add_table(rows=2, cols=3)
        table.cell(0, 0).text = "评分标准"
        _set_grid_span(table.cell(0, 0), 2)
        table.cell(0, 2).text = "备注"
        table.cell(1, 0).text = "优得30分"
        table.cell(1, 1).text = ""
        table.cell(1, 2).text = "无"
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            hregions = [r for r in tbl["mergedRegions"] if r["colspan"] > 1]
            assert len(hregions) == 1
            assert hregions[0]["rowspan"] == 1
            assert hregions[0]["colspan"] == 2
        finally:
            os.unlink(path)

    def test_combined_vh_merge(self):
        """Cell that spans 2 rows × 2 columns, broken into separate pieces.

        Real Word representation: row 0 col 0 has vMerge=restart with
        gridSpan=2 (covers row 0 cols 0+1). Row 1 col 0 is vMerge
        continuation; row 1 col 1 is the corner cell.

        python-docx's ``table.cell(r, c)`` resolves cells by grid_offset,
        so we operate on raw <w:tc> elements to bypass that confusion.
        """
        doc = Document()
        table = doc.add_table(rows=2, cols=2)
        _set_run_text(table.cell(0, 0), "财务能力")
        # Top-left (raw tc of row 0 tc 0): vMerge restart + gridSpan=2
        _raw_set_v_merge(table, 0, 0, "restart")
        _raw_set_grid_span(table, 0, 0, 2)
        # Bottom-left (raw tc of row 1 tc 0): vMerge continuation
        _raw_set_v_merge(table, 1, 0)

        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            tbl = artifact["tables"][0]
            assert tbl["rows"] == 2
            assert tbl["columns"] == 2
            origin = next(
                c for c in tbl["cells"]
                if c["row"] == 0 and c["column"] == 0 and not c["isMergedContinue"]
            )
            assert origin["rawText"] == "财务能力"
            # The corner (1,0) continuation must exist as isMergedContinue=True
            corner = next(
                (c for c in tbl["cells"]
                 if c["row"] == 1 and c["column"] == 0 and c["isMergedContinue"]),
                None,
            )
            assert corner is not None, "expected vMerge continuation at (1,0)"
            # The origin (0,0) should have rowspan=2 because vMerge restart
            # detected the continuation in row 1.
            assert origin["rowspan"] == 2
            assert origin["colspan"] == 2
            region = next(
                (r for r in tbl["mergedRegions"] if r["startCellId"] == origin["cellId"]),
                None,
            )
            assert region is not None
            assert region["rowspan"] == 2
            assert region["colspan"] == 2
        finally:
            os.unlink(path)


# ---------------------------------------------------------------------------
# Multiple tables
# ---------------------------------------------------------------------------

class TestMultipleTables:
    """tableId distinguishes tables across the same document."""

    def test_multiple_tables_have_distinct_ids(self):
        doc = Document()
        for i in range(3):
            tbl = doc.add_table(rows=1, cols=1)
            tbl.cell(0, 0).text = f"table{i} content"
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            table_ids = [t["tableId"] for t in artifact["tables"]]
            assert len(table_ids) == 3
            assert len(set(table_ids)) == 3
            assert table_ids == ["t-0000", "t-0001", "t-0002"]
        finally:
            os.unlink(path)


# ---------------------------------------------------------------------------
# Legacy v1 contract stays compatible
# ---------------------------------------------------------------------------

class TestLegacyV1Contract:
    """extract_tables() still returns the old {tables, warnings} shape."""

    def test_legacy_shape_preserved(self):
        doc = Document()
        table = doc.add_table(rows=2, cols=2)
        table.cell(0, 0).text = "评分因素"
        table.cell(0, 1).text = "分值"
        table.cell(1, 0).text = "技术方案"
        table.cell(1, 1).text = "30"
        path = _write_docx(doc)
        try:
            result = extract_tables(path)
            assert "tables" in result
            assert "warnings" in result
            tbl = result["tables"][0]
            assert tbl["rows"][0][0]["text"] == "评分因素"
            assert tbl["isScoreTable"] is True
        finally:
            os.unlink(path)


# ---------------------------------------------------------------------------
# Edge cases
# ---------------------------------------------------------------------------

class TestEdgeCases:
    def test_empty_document(self):
        doc = Document()
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            assert artifact["tables"] == []
            assert artifact["warnings"] == []
        finally:
            os.unlink(path)

    def test_corrupted_zip(self):
        import zipfile
        tmp = tempfile.NamedTemporaryFile(suffix=".docx", delete=False)
        with zipfile.ZipFile(tmp.name, "w") as zf:
            zf.writestr("dummy.txt", "hello")
        tmp.close()
        try:
            with pytest.raises(ValueError):
                extract_tables_artifact(tmp.name)
        finally:
            os.unlink(tmp.name)

    def test_table_with_only_one_empty_row_is_skipped(self):
        doc = Document()
        # add an empty paragraph then an empty table
        doc.add_table(rows=1, cols=1)  # all blank
        path = _write_docx(doc)
        try:
            artifact = extract_tables_artifact(path)
            # empty table has cells but rows == 1 with empty text — kept but not score
            assert len(artifact["tables"]) == 1
            tbl = artifact["tables"][0]
            assert tbl["rows"] == 1
            assert tbl["columns"] == 1
            assert tbl["isScoreTable"] is False
        finally:
            os.unlink(path)
