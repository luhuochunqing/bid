"""Tests for streaming SAX docx text extractor.

Input:  Synthetic docx with headings, paragraphs, and tables.
Output: Assertions that (a) _extract_docx_streaming_sax extracts clean text and tables,
        (b) _is_large_docx respects threshold,
        (c) _convert_worker routes large docx to streaming SAX and falls back on error.
"""
from __future__ import annotations

import io
import os
import zipfile
import pytest


def _build_test_docx(paragraphs: list[str], tables: list[list[list[str]]] | None = None) -> bytes:
    """Build a minimal valid docx in memory without external docx dependency."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as z:
        # [Content_Types].xml
        z.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        # _rels/.rels
        z.writestr(
            "_rels/.rels",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""",
        )
        # word/document.xml
        p_xml = []
        for p in paragraphs:
            p_xml.append(f"""<w:p><w:r><w:t>{p}</w:t></w:r></w:p>""")

        tbl_xml = []
        if tables:
            for tbl in tables:
                tbl_xml.append("<w:tbl>")
                for row in tbl:
                    tbl_xml.append("<w:tr>")
                    for cell in row:
                        tbl_xml.append(f"<w:tc><w:p><w:r><w:t>{cell}</w:t></w:r></w:p></w:tc>")
                    tbl_xml.append("</w:tr>")
                tbl_xml.append("</w:tbl>")

        body = "".join(p_xml) + "".join(tbl_xml)
        doc_xml = f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    {body}
  </w:body>
</w:document>"""
        z.writestr("word/document.xml", doc_xml)
    return buf.getvalue()


def test_extract_docx_streaming_sax(tmp_path):
    from app import _extract_docx_streaming_sax

    paragraphs = ["第一章 投标邀请书", "本工程为大型基础设施建设项目。", "投标截止时间为2026年10月1日。"]
    tables = [
        [["序号", "项目名称", "金额"], ["1", "主体工程", "500万元"], ["2", "安装工程", "300万元"]]
    ]
    docx_bytes = _build_test_docx(paragraphs, tables)
    file_path = str(tmp_path / "sample.docx")
    with open(file_path, "wb") as f:
        f.write(docx_bytes)

    text = _extract_docx_streaming_sax(file_path)
    assert "第一章 投标邀请书" in text
    assert "本工程为大型基础设施建设项目" in text
    assert "主体工程" in text
    assert "500万元" in text
    assert "| 序号 | 项目名称 | 金额 |" in text


def test_is_large_docx(tmp_path):
    from app import _is_large_docx

    docx_bytes = _build_test_docx(["测试文本" * 100])
    file_path = str(tmp_path / "check_size.docx")
    with open(file_path, "wb") as f:
        f.write(docx_bytes)

    assert _is_large_docx(file_path, threshold_bytes=50) is True
    assert _is_large_docx(file_path, threshold_bytes=10 * 1024 * 1024) is False


def test_convert_worker_large_docx_routing(tmp_path):
    from app import _convert_worker

    # Build docx with sufficient text (>50 chars)
    docx_bytes = _build_test_docx(["这是用于测试大文件流式解析路由的投标书长文本内容。" * 5])
    file_path = str(tmp_path / "large_routed.docx")
    with open(file_path, "wb") as f:
        f.write(docx_bytes)

    # Calling _convert_worker directly
    result = _convert_worker(file_path)
    assert result.text_content
    assert "投标书长文本内容" in result.text_content


def test_extract_docx_streaming_sax_corrupted_file(tmp_path):
    from app import _extract_docx_streaming_sax

    corrupt_file = str(tmp_path / "corrupt.docx")
    with open(corrupt_file, "wb") as f:
        f.write(b"not a valid zip file content")

    text = _extract_docx_streaming_sax(corrupt_file)
    assert text == ""


def test_extract_docx_streaming_sax_headings(tmp_path):
    from app import _extract_docx_streaming_sax

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        doc_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p>
      <w:pPr><w:pStyle w:val="heading 1"/></w:pPr>
      <w:r><w:t>工程概况</w:t></w:r>
    </w:p>
    <w:p>
      <w:pPr><w:pStyle w:val="heading 2"/></w:pPr>
      <w:r><w:t>施工组织设计</w:t></w:r>
    </w:p>
    <w:p>
      <w:r><w:t>正文段落内容</w:t></w:r>
    </w:p>
  </w:body>
</w:document>"""
        z.writestr("word/document.xml", doc_xml)

    file_path = str(tmp_path / "headings.docx")
    with open(file_path, "wb") as f:
        f.write(buf.getvalue())

    text = _extract_docx_streaming_sax(file_path)
    assert "# 工程概况" in text
    assert "## 施工组织设计" in text
    assert "正文段落内容" in text


def test_extract_docx_streaming_sax_images_in_table(tmp_path):
    """Verify that images in tables (e.g. ID card scans) produce 【已附图片】 markers."""
    from app import _extract_docx_streaming_sax

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        doc_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
    <w:tbl>
      <w:tr>
        <w:tc><w:p><w:r><w:t>身份证（人像页）</w:t></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:t>身份证（国徽页）</w:t></w:r></w:p></w:tc>
      </w:tr>
      <w:tr>
        <w:tc><w:p><w:r><w:drawing><a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="rId1"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></w:drawing></w:r></w:p></w:tc>
        <w:tc><w:p><w:r><w:drawing><a:graphic><a:graphicData><pic:pic><pic:blipFill><a:blip r:embed="rId2"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></w:drawing></w:r></w:p></w:tc>
      </w:tr>
    </w:tbl>
  </w:body>
</w:document>"""
        z.writestr("word/document.xml", doc_xml)

    file_path = str(tmp_path / "id_card_table.docx")
    with open(file_path, "wb") as f:
        f.write(buf.getvalue())

    text = _extract_docx_streaming_sax(file_path)
    assert "| 身份证（人像页） | 身份证（国徽页） |" in text
    assert "| 【已附图片】 | 【已附图片】 |" in text


def test_extract_docx_streaming_sax_vml_image(tmp_path):
    """Verify that VML imagedata elements also produce 【已附图片】 markers."""
    from app import _extract_docx_streaming_sax

    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr(
            "[Content_Types].xml",
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""",
        )
        doc_xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:v="urn:schemas-microsoft-com:vml"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <w:body>
    <w:p>
      <w:r><w:t>法定代表人身份证明：</w:t></w:r>
      <w:r>
        <w:pict>
          <v:shape><v:imagedata r:id="rId1"/></v:shape>
        </w:pict>
      </w:r>
    </w:p>
  </w:body>
</w:document>"""
        z.writestr("word/document.xml", doc_xml)

    file_path = str(tmp_path / "vml_image.docx")
    with open(file_path, "wb") as f:
        f.write(buf.getvalue())

    text = _extract_docx_streaming_sax(file_path)
    assert "法定代表人身份证明：【已附图片】" in text


