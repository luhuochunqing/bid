from __future__ import annotations

import io
import os
import zipfile
import pytest

from table_extractor import repair_truncated_docx, extract_tables_artifact
from app import _safe_convert_worker


def _create_truncated_docx_bytes() -> bytes:
    """Create a minimal docx missing its EOCD record (truncated)."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", (
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
            '</Types>'
        ))
        z.writestr("_rels/.rels", (
            '<?xml version="1.0" encoding="UTF-8"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            '</Relationships>'
        ))
        z.writestr("word/document.xml", (
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            '<w:body>'
            '<w:p><w:r><w:t>Hello Truncated World</w:t></w:r></w:p>'
            '<w:tbl>'
            '<w:tr><w:tc><w:p><w:r><w:t>评分因素</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>分值</w:t></w:r></w:p></w:tc></w:tr>'
            '<w:tr><w:tc><w:p><w:r><w:t>技术方案</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>10</w:t></w:r></w:p></w:tc></w:tr>'
            '</w:tbl>'
            '</w:body>'
            '</w:document>'
        ))
    raw_bytes = buf.getvalue()
    # Find EOCD signature PK\x05\x06 and strip it (or truncate right in the middle of central directory)
    eocd_pos = raw_bytes.rfind(b"PK\x05\x06")
    assert eocd_pos != -1
    # Truncate before EOCD so it's missing EOCD
    return raw_bytes[:eocd_pos]


def test_repair_truncated_docx(tmp_path):
    truncated_bytes = _create_truncated_docx_bytes()
    truncated_file = tmp_path / "truncated.docx"
    truncated_file.write_bytes(truncated_bytes)

    # Standard zipfile fails on this truncated file
    assert not zipfile.is_zipfile(str(truncated_file))

    # repair_truncated_docx succeeds
    repaired_path = repair_truncated_docx(str(truncated_file))
    assert repaired_path is not None
    assert os.path.exists(repaired_path)

    try:
        assert zipfile.is_zipfile(repaired_path)
        with zipfile.ZipFile(repaired_path) as z:
            assert "word/document.xml" in z.namelist()
            doc_xml = z.read("word/document.xml").decode("utf-8")
            assert "Hello Truncated World" in doc_xml
    finally:
        if os.path.exists(repaired_path):
            os.remove(repaired_path)


def test_extract_tables_artifact_on_truncated_docx(tmp_path):
    truncated_bytes = _create_truncated_docx_bytes()
    truncated_file = tmp_path / "truncated_tables.docx"
    truncated_file.write_bytes(truncated_bytes)

    artifact = extract_tables_artifact(str(truncated_file))
    assert artifact["schemaVersion"] == "2.0"
    assert len(artifact["tables"]) == 1
    table = artifact["tables"][0]
    assert table["isScoreTable"] is True
    assert table["rows"] == 2
    assert table["columns"] == 2


def test_safe_convert_worker_catches_exceptions():
    # If path does not exist, _safe_convert_worker should return error dict rather than raising unpicklable exception
    res = _safe_convert_worker("/non/existent/path/file.docx")
    assert isinstance(res, dict)
    assert "error" in res
