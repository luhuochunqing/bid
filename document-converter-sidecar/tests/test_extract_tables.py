"""Tests for table_extractor module and POST /extract-tables endpoint."""
from __future__ import annotations

import io
import os
import sys
import tempfile
from unittest.mock import patch

import pytest
from docx import Document
from docx.oxml.ns import qn
from httpx import AsyncClient, ASGITransport

from table_extractor import extract_tables


# ---------------------------------------------------------------------------
# Helpers — build .docx in memory, write to temp file, call extract_tables
# ---------------------------------------------------------------------------

def _write_docx(doc: Document) -> str:
    tmp = tempfile.NamedTemporaryFile(suffix=".docx", delete=False)
    doc.save(tmp.name)
    tmp.close()
    return tmp.name


def _set_v_merge(cell, val: str | None = None):
    """Set vMerge on a cell's tcPr.  val=None → <w:vMerge/> (continue)."""
    tc_pr = cell._element.get_or_add_tcPr()
    vm = tc_pr.makeelement(qn("w:vMerge"), {})
    if val:
        vm.set(qn("w:val"), val)
    tc_pr.append(vm)


def _set_grid_span(cell, val: int):
    """Set gridSpan on a cell's tcPr."""
    tc_pr = cell._element.get_or_add_tcPr()
    gs = tc_pr.makeelement(qn("w:gridSpan"), {qn("w:val"): str(val)})
    tc_pr.append(gs)


# ---------------------------------------------------------------------------
# Unit tests — extract_tables() pure function
# ---------------------------------------------------------------------------

class TestExtractTablesPlain:
    """Plain table (no merges)."""

    def test_simple_table(self):
        doc = Document()
        table = doc.add_table(rows=3, cols=3)
        table.cell(0, 0).text = "评分因素"
        table.cell(0, 1).text = "分值"
        table.cell(0, 2).text = "评分标准"
        table.cell(1, 0).text = "技术方案"
        table.cell(1, 1).text = "30"
        table.cell(1, 2).text = "优得30分"
        table.cell(2, 0).text = "价格"
        table.cell(2, 1).text = "70"
        table.cell(2, 2).text = "最低價得70分"

        path = _write_docx(doc)
        try:
            result = extract_tables(path)
            assert len(result["tables"]) == 1
            tbl = result["tables"][0]
            assert tbl["isScoreTable"] is True
            assert len(tbl["rows"]) == 3
            # all rowspan=1, colspan=1, no merges
            for row in tbl["rows"]:
                for cell in row:
                    assert cell["rowspan"] == 1
                    assert cell["colspan"] == 1
                    assert cell["isMergedContinue"] is False
            assert tbl["rows"][1][0]["text"] == "技术方案"
            assert tbl["rows"][1][1]["text"] == "30"
        finally:
            os.unlink(path)

    def test_non_score_table(self):
        doc = Document()
        table = doc.add_table(rows=2, cols=2)
        table.cell(0, 0).text = "姓名"
        table.cell(0, 1).text = "年龄"
        table.cell(1, 0).text = "张三"
        table.cell(1, 1).text = "30"

        path = _write_docx(doc)
        try:
            result = extract_tables(path)
            assert len(result["tables"]) == 1
            assert result["tables"][0]["isScoreTable"] is False
        finally:
            os.unlink(path)


class TestExtractTablesVerticalMerge:
    """Vertical merge (vMerge restart / continue)."""

    def test_vmerge_restart_and_continue(self):
        doc = Document()
        table = doc.add_table(rows=3, cols=2)
        table.cell(0, 0).text = "评分因素"
        table.cell(0, 1).text = "分值"
        table.cell(1, 0).text = "技术方案"
        table.cell(1, 1).text = "30"
        table.cell(2, 0).text = ""  # merged continuation
        table.cell(2, 1).text = "30"

        # Set vMerge: row1 col0 = restart, row2 col0 = continue
        _set_v_merge(table.cell(1, 0), "restart")
        _set_v_merge(table.cell(2, 0))  # no val = continue

        path = _write_docx(doc)
        try:
            result = extract_tables(path)
            tbl = result["tables"][0]
            # row 1: cell(1,0) should have rowspan=2
            assert tbl["rows"][1][0]["rowspan"] == 2
            assert tbl["rows"][1][0]["text"] == "技术方案"
            # row 2: cell(2,0) should be isMergedContinue=True
            assert tbl["rows"][2][0]["isMergedContinue"] is True
        finally:
            os.unlink(path)


class TestExtractTablesHorizontalMerge:
    """Horizontal merge (gridSpan)."""

    def test_gridspan(self):
        doc = Document()
        table = doc.add_table(rows=2, cols=3)
        table.cell(0, 0).text = "评分标准"
        # cell(0,0) spans 2 columns
        _set_grid_span(table.cell(0, 0), 2)
        table.cell(0, 2).text = "备注"
        table.cell(1, 0).text = "优得30分"
        table.cell(1, 1).text = ""  # covered by gridSpan
        table.cell(1, 2).text = "无"

        path = _write_docx(doc)
        try:
            result = extract_tables(path)
            tbl = result["tables"][0]
            # row 0: first cell colspan=2, second is merged-continue
            assert tbl["rows"][0][0]["colspan"] == 2
            assert tbl["rows"][0][0]["text"] == "评分标准"
            assert tbl["rows"][0][1]["isMergedContinue"] is True
        finally:
            os.unlink(path)


class TestExtractTablesEdgeCases:
    """Edge cases."""

    def test_non_docx_file(self):
        tmp = tempfile.NamedTemporaryFile(suffix=".txt", delete=False)
        tmp.write(b"not a docx")
        tmp.close()
        try:
            result = extract_tables(tmp.name)
            assert result["tables"] == []
            assert len(result["warnings"]) > 0
        finally:
            os.unlink(tmp.name)

    def test_corrupted_file(self):
        tmp = tempfile.NamedTemporaryFile(suffix=".docx", delete=False)
        # Write a valid zip but not a valid docx
        import zipfile
        with zipfile.ZipFile(tmp.name, "w") as zf:
            zf.writestr("dummy.txt", "hello")
        tmp.close()
        try:
            # python-docx may raise on invalid docx structure
            result = extract_tables(tmp.name)
            # either returns empty or raises ValueError — both acceptable
            if result["tables"]:
                pytest.fail("should not extract tables from corrupted file")
        except ValueError:
            pass  # expected
        finally:
            os.unlink(tmp.name)

    def test_empty_document(self):
        doc = Document()
        path = _write_docx(doc)
        try:
            result = extract_tables(path)
            assert result["tables"] == []
        finally:
            os.unlink(path)


# ---------------------------------------------------------------------------
# HTTP endpoint tests — POST /extract-tables
# ---------------------------------------------------------------------------

@pytest.fixture()
def app_instance():
    """App instance with no auth key."""
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter", "image_extractor", "table_extractor"):
            del sys.modules[mod_name]
    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    with patch.dict(os.environ, {}, clear=True):
        for k, v in env_without_key.items():
            os.environ[k] = v
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


def _post_extract_tables(app, content: bytes, filename: str = "test.docx",
                          headers: dict | None = None):
    async def _run():
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            return await client.post(
                "/extract-tables",
                files={"file": (filename, io.BytesIO(content), "application/octet-stream")},
                headers=headers or {},
            )
    return _run()


class TestExtractTablesEndpoint:
    """HTTP contract tests for POST /extract-tables."""

    @pytest.mark.asyncio
    async def test_valid_docx_returns_200(self, app_instance):
        doc = Document()
        doc.add_table(rows=2, cols=2).cell(0, 0).text = "评分"
        buf = io.BytesIO()
        doc.save(buf)
        content = buf.getvalue()

        resp = await _post_extract_tables(app_instance, content, "test.docx")
        assert resp.status_code == 200
        data = resp.json()
        assert "tables" in data
        assert "warnings" in data

    @pytest.mark.asyncio
    async def test_non_docx_returns_empty_tables(self, app_instance):
        resp = await _post_extract_tables(app_instance, b"not a docx", "test.txt")
        assert resp.status_code == 200
        data = resp.json()
        assert data["tables"] == []

    @pytest.mark.asyncio
    async def test_no_filename_returns_error(self, app_instance):
        async def _run():
            async with AsyncClient(transport=ASGITransport(app=app_instance), base_url="http://test") as client:
                return await client.post(
                    "/extract-tables",
                    files={"file": ("", io.BytesIO(b"data"), "application/octet-stream")},
                )
        resp = await _run()
        assert resp.status_code in (400, 422)


class TestExtractTablesVersionHeaders:
    """Release gate: X-Schema-Version request/response echo (Stage D)."""

    @pytest.mark.asyncio
    async def test_v1_request_echoes_v1_header(self, app_instance):
        doc = Document()
        doc.add_table(rows=2, cols=2).cell(0, 0).text = "评分"
        buf = io.BytesIO()
        doc.save(buf)

        resp = await _post_extract_tables(
            app_instance, buf.getvalue(), "test.docx", headers={"X-Schema-Version": "1.0"})
        assert resp.status_code == 200
        assert resp.headers.get("x-schema-version") == "1.0"
        data = resp.json()
        assert "tables" in data and "warnings" in data
        assert "schemaVersion" not in data  # v1 body shape unchanged

    @pytest.mark.asyncio
    async def test_default_request_echoes_v2_header(self, app_instance):
        resp = await _post_extract_tables(app_instance, b"not a docx", "test.txt")
        assert resp.status_code == 200
        assert resp.headers.get("x-schema-version") == "2.2"
        assert resp.json()["schemaVersion"] == "2.2"

    @pytest.mark.asyncio
    async def test_health_echoes_v2_header(self, app_instance):
        async with AsyncClient(transport=ASGITransport(app=app_instance),
                               base_url="http://test") as client:
            resp = await client.get("/health")
        assert resp.status_code == 200
        assert resp.json() == {"status": "up"}  # body unchanged
        assert resp.headers.get("x-schema-version") == "2.2"
