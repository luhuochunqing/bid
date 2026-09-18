from __future__ import annotations

import io
import os
import struct
import tempfile
import zipfile
import zlib
import pytest

from docx_repair import auto_repaired_docx, repair_truncated_docx
from table_extractor import extract_tables_artifact
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
    eocd_pos = raw_bytes.rfind(b"PK\x05\x06")
    assert eocd_pos != -1
    return raw_bytes[:eocd_pos]


def test_repair_truncated_docx(tmp_path):
    truncated_bytes = _create_truncated_docx_bytes()
    truncated_file = tmp_path / "truncated.docx"
    truncated_file.write_bytes(truncated_bytes)

    assert not zipfile.is_zipfile(str(truncated_file))

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
    res = _safe_convert_worker("/non/existent/path/file.docx")
    assert isinstance(res, dict)
    assert "error" in res


def test_auto_repaired_docx_context_manager(tmp_path):
    # 1. On valid docx: zero overhead, target == path, no tempfile created
    valid_file = tmp_path / "valid.docx"
    with zipfile.ZipFile(valid_file, "w") as z:
        z.writestr("word/document.xml", "<xml/>")
    with auto_repaired_docx(str(valid_file)) as target:
        assert target == str(valid_file)

    # 2. On truncated docx: repairs to tempfile and cleans up after context exit
    truncated_bytes = _create_truncated_docx_bytes()
    truncated_file = tmp_path / "truncated_ctx.docx"
    truncated_file.write_bytes(truncated_bytes)

    temp_path = None
    with auto_repaired_docx(str(truncated_file)) as target:
        temp_path = target
        assert temp_path != str(truncated_file)
        assert os.path.exists(temp_path)
        assert zipfile.is_zipfile(temp_path)

    # After exit, temp file MUST be deleted
    assert not os.path.exists(temp_path)


# ---------------------------------------------------------------------------
# 8-Scenario Regression Matrix Required by Reviewer
# ---------------------------------------------------------------------------

def test_regression_1_missing_eocd_only(tmp_path):
    """Matrix 1: Only EOCD is missing; central directory entries are intact."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("word/document.xml", "<w:document><w:body><w:p><w:t>Only EOCD Missing</w:t></w:p></w:body></w:document>")
    raw = buf.getvalue()
    eocd_pos = raw.rfind(b"PK\x05\x06")
    trunc = raw[:eocd_pos]

    fpath = tmp_path / "missing_eocd_only.docx"
    fpath.write_bytes(trunc)

    repaired = repair_truncated_docx(str(fpath))
    assert repaired is not None
    try:
        with zipfile.ZipFile(repaired) as z:
            assert z.testzip() is None
            assert b"Only EOCD Missing" in z.read("word/document.xml")
    finally:
        if os.path.exists(repaired):
            os.remove(repaired)


def test_regression_2_partial_central_directory(tmp_path):
    """Matrix 2: Truncated midway through the central directory."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("word/document.xml", "<w:document>Partial CD Test</w:document>")
        z.writestr("extra.xml", "<extra/>")
    raw = buf.getvalue()
    cd_pos = raw.find(b"PK\x01\x02")
    assert cd_pos != -1
    # Truncate 20 bytes into the Central Directory
    trunc = raw[:cd_pos + 20]

    fpath = tmp_path / "partial_cd.docx"
    fpath.write_bytes(trunc)

    repaired = repair_truncated_docx(str(fpath))
    assert repaired is not None
    try:
        with zipfile.ZipFile(repaired) as z:
            assert z.testzip() is None
            assert b"Partial CD Test" in z.read("word/document.xml")
    finally:
        if os.path.exists(repaired):
            os.remove(repaired)


def test_regression_3_bit3_with_signature_descriptor(tmp_path):
    """Matrix 3: Bit 3 flag set with PK\\x07\\x08 signature Data Descriptor."""
    xml_content = b"<w:document><w:body><w:p><w:t>Bit 3 With Signature</w:t></w:p></w:body></w:document>"
    crc = zlib.crc32(xml_content)
    comp = zlib.compress(xml_content)[2:-4]
    fn = b"word/document.xml"

    # Local file header: bit 3 set (flags=0x0008), comp_size=0, uncomp_size=0, crc=0
    lh = (
        b"PK\x03\x04\x14\x00\x08\x00\x08\x00\x00\x00\x00\x00"
        + struct.pack("<IIIHH", 0, 0, 0, len(fn), 0)
        + fn
        + comp
        + b"PK\x07\x08" + struct.pack("<III", crc, len(comp), len(xml_content))
    )

    fpath = tmp_path / "bit3_with_sig.docx"
    fpath.write_bytes(lh)

    repaired = repair_truncated_docx(str(fpath))
    assert repaired is not None
    try:
        with zipfile.ZipFile(repaired) as z:
            assert z.testzip() is None
            assert b"Bit 3 With Signature" in z.read("word/document.xml")
    finally:
        if os.path.exists(repaired):
            os.remove(repaired)


def test_regression_4_bit3_without_signature_descriptor(tmp_path):
    """Matrix 4: Bit 3 flag set with raw Data Descriptor (no PK\\x07\\x08 signature)."""
    xml_content = b"<w:document><w:body><w:p><w:t>Bit 3 No Signature</w:t></w:p></w:body></w:document>"
    crc = zlib.crc32(xml_content)
    comp = zlib.compress(xml_content)[2:-4]
    fn = b"word/document.xml"

    # Local file header: bit 3 set (flags=0x0008), followed by comp, followed by 12-byte descriptor
    lh = (
        b"PK\x03\x04\x14\x00\x08\x00\x08\x00\x00\x00\x00\x00"
        + struct.pack("<IIIHH", 0, 0, 0, len(fn), 0)
        + fn
        + comp
        + struct.pack("<III", crc, len(comp), len(xml_content))
    )

    fpath = tmp_path / "bit3_no_sig.docx"
    fpath.write_bytes(lh)

    repaired = repair_truncated_docx(str(fpath))
    assert repaired is not None
    try:
        with zipfile.ZipFile(repaired) as z:
            assert z.testzip() is None
            assert b"Bit 3 No Signature" in z.read("word/document.xml")
    finally:
        if os.path.exists(repaired):
            os.remove(repaired)


def test_regression_5_compressed_data_contains_pk_bytes(tmp_path):
    """Matrix 5: Compressed deflate stream naturally contains PK byte sequence."""
    # Construct an uncompressed DEFLATE block containing b"PK\x03\x04" inside payload
    payload = b"<w:document><w:body><w:p><w:t>Data with PK\x03\x04 embedded</w:t></w:p></w:body></w:document>"
    n = len(payload)
    # BFINAL=1, BTYPE=00 (uncompressed deflate block)
    raw_deflate_block = b"\x01" + struct.pack("<HH", n, (~n) & 0xFFFF) + payload
    assert b"PK" in raw_deflate_block

    crc = zlib.crc32(payload)
    fn = b"word/document.xml"
    lh = (
        b"PK\x03\x04\x14\x00\x08\x00\x08\x00\x00\x00\x00\x00"
        + struct.pack("<IIIHH", 0, 0, 0, len(fn), 0)
        + fn
        + raw_deflate_block
        + b"PK\x07\x08" + struct.pack("<III", crc, len(raw_deflate_block), len(payload))
    )

    fpath = tmp_path / "pk_inside_deflate.docx"
    fpath.write_bytes(lh)

    # Must NOT stop early at the PK bytes inside the payload
    repaired = repair_truncated_docx(str(fpath))
    assert repaired is not None
    try:
        with zipfile.ZipFile(repaired) as z:
            assert z.testzip() is None
            assert b"Data with PK\x03\x04 embedded" in z.read("word/document.xml")
    finally:
        if os.path.exists(repaired):
            os.remove(repaired)


def test_regression_6_truncated_inside_document_xml_data_rejected(tmp_path):
    """Matrix 6: File truncated right in the middle of word/document.xml must be rejected."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("word/document.xml", "<w:document>" + "A" * 5000 + "</w:document>")
    raw = buf.getvalue()

    doc_idx = raw.find(b"word/document.xml")
    assert doc_idx != -1
    # Truncate 30 bytes into the compressed data of word/document.xml
    trunc = raw[:doc_idx + len(b"word/document.xml") + 30]

    fpath = tmp_path / "trunc_inside_xml.docx"
    fpath.write_bytes(trunc)

    repaired = repair_truncated_docx(str(fpath))
    assert repaired is None, "Must refuse to repair when document.xml payload is truncated"


def test_regression_7_corrupt_stream_crc_failure_rejected(tmp_path):
    """Matrix 7: Corrupt compressed payload causing CRC failure must be rejected."""
    xml_content = b"<w:document><w:body><w:p><w:t>CRC Check</w:t></w:p></w:body></w:document>"
    comp = zlib.compress(xml_content)[2:-4]
    fn = b"word/document.xml"

    # Corrupt one byte of the compressed stream
    corrupt_comp = bytearray(comp)
    corrupt_comp[5] ^= 0xFF

    # Wrong CRC in descriptor or local header
    lh = (
        b"PK\x03\x04\x14\x00\x00\x00\x08\x00\x00\x00\x00\x00"
        + struct.pack("<IIIHH", 12345678, len(corrupt_comp), len(xml_content), len(fn), 0)
        + fn
        + bytes(corrupt_comp)
    )

    fpath = tmp_path / "crc_failure.docx"
    fpath.write_bytes(lh)

    repaired = repair_truncated_docx(str(fpath))
    assert repaired is None, "Must reject file failing CRC / testzip"


def test_regression_8_oversized_file_fail_fast(tmp_path):
    """Matrix 8: File exceeding size limit must Fail-Fast without memory buffer allocation."""
    fpath = tmp_path / "oversized.docx"
    # Create 100KB file, but set limit to 50KB
    fpath.write_bytes(b"PK\x03\x04" + b"\x00" * (100 * 1024))

    repaired = repair_truncated_docx(str(fpath), max_size_bytes=50 * 1024)
    assert repaired is None, "Must fail-fast when file exceeds max_size_bytes limit"


def test_regression_9_zip_bomb_uncompressed_size_cap_rejected(tmp_path, monkeypatch):
    """Matrix 9: Highly compressed stream exceeding uncompressed safety cap must be rejected."""
    raw_payload = b"\x00" * (100 * 1024)  # 100KB
    comp = zlib.compress(raw_payload)[2:-4]
    fn = b"word/document.xml"
    crc = zlib.crc32(raw_payload)
    lh = (
        b"PK\x03\x04\x14\x00\x08\x00\x08\x00\x00\x00\x00\x00"
        + struct.pack("<IIIHH", 0, 0, 0, len(fn), 0)
        + fn
        + comp
        + b"PK\x07\x08" + struct.pack("<III", crc, len(comp), len(raw_payload))
    )
    fpath = tmp_path / "zip_bomb.docx"
    fpath.write_bytes(lh)

    monkeypatch.setenv("DOCX_MAX_UNCOMPRESSED_ENTRY_SIZE", str(50 * 1024))
    repaired = repair_truncated_docx(str(fpath))
    assert repaired is None, "Must reject entry whose uncompressed size exceeds safety cap"

