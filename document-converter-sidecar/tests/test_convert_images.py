"""Contract tests for POST /convert-images (spec 048 T009/T010).

Input:  Multipart uploads against the FastAPI test client; sample documents
        (docx/pptx/pdf) are generated programmatically in-test with
        python-docx / python-pptx / PyMuPDF, each embedding small PNG images.
Output: Assertions on the image-extraction contract defined in
        specs/048-score-parse-image-multimodal/contracts/sidecar-images-api.md:
        200 + {images[], truncated, warnings}; section attribution
        (docx nearest preceding heading / pptx slide title / pdf
        page->heading backfill); scanned-PDF warning; budget truncation;
        unsupported types -> empty manifest; 401/422 semantics aligned
        with /convert-stream.
Position: test suite; run with `pytest tests/test_convert_images.py -v` from the sidecar directory.
"""
from __future__ import annotations

import base64
import io
import os
import sys
from unittest.mock import patch

import pymupdf
import pytest
from docx import Document
from httpx import AsyncClient, ASGITransport
from PIL import Image
from pptx import Presentation
from pptx.util import Inches


# ---------------------------------------------------------------------------
# Fixtures (module-reload pattern shared with test_convert_stream.py)
# ---------------------------------------------------------------------------

@pytest.fixture()
def no_key_app():
    """App instance with SIDECAR_SHARED_KEY unset (dev mode)."""
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter", "image_extractor"):
            del sys.modules[mod_name]
    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    with patch.dict(os.environ, {}, clear=True):
        for k, v in env_without_key.items():
            os.environ[k] = v
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


def _post_convert_images(app, content: bytes, filename: str = "bid.pdf",
                         headers: dict | None = None):
    """Async helper: issue POST /convert-images with the given bytes."""
    async def _run():
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            return await client.post(
                "/convert-images",
                files={"file": (filename, io.BytesIO(content), "application/octet-stream")},
                headers=headers or {},
            )
    return _run()


# ---------------------------------------------------------------------------
# Sample document builders (minimal files with embedded PNG images)
# ---------------------------------------------------------------------------

def _png_bytes(color: tuple[int, int, int], size: tuple[int, int] = (40, 30)) -> bytes:
    """Return a small solid-color PNG payload."""
    buf = io.BytesIO()
    Image.new("RGB", size, color=color).save(buf, format="PNG")
    return buf.getvalue()


def _make_docx_bytes(png1: bytes, png2: bytes) -> bytes:
    """docx: heading -> paragraph -> image1 -> heading -> image2."""
    doc = Document()
    doc.add_heading("第一章 总则", level=1)
    doc.add_paragraph("正文段落。")
    doc.add_picture(io.BytesIO(png1))
    doc.add_heading("第二章 业绩要求", level=1)
    doc.add_picture(io.BytesIO(png2))
    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


def _make_pptx_bytes(png1: bytes, png2: bytes) -> bytes:
    """pptx: two slides, each with a title and a picture."""
    prs = Presentation()
    slide1 = prs.slides.add_slide(prs.slide_layouts[5])  # Title Only
    slide1.shapes.title.text = "项目概述"
    slide1.shapes.add_picture(io.BytesIO(png1), Inches(1), Inches(2), Inches(1), Inches(1))
    slide2 = prs.slides.add_slide(prs.slide_layouts[5])
    slide2.shapes.title.text = "实施方案"
    slide2.shapes.add_picture(io.BytesIO(png2), Inches(1), Inches(2), Inches(1), Inches(1))
    buf = io.BytesIO()
    prs.save(buf)
    return buf.getvalue()


def _make_pdf_bytes(pages: list[tuple[str, bytes | None]]) -> bytes:
    """pdf: one entry per page — (text, png). Text may be empty (scanned page)."""
    doc = pymupdf.open()
    for text, png in pages:
        page = doc.new_page()
        if text:
            # china-s: pymupdf built-in CJK font — default Helvetica renders
            # Chinese text as literal '?' glyphs, breaking the text layer.
            page.insert_textbox(
                pymupdf.Rect(50, 50, 500, 130), text, fontname="china-s"
            )
        if png:
            page.insert_image(pymupdf.Rect(72, 150, 250, 250), stream=png)
    buf = io.BytesIO()
    doc.save(buf)
    return buf.getvalue()


# ---------------------------------------------------------------------------
# Contract: docx — image extraction with heading-based section attribution
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_docx_images_with_section_attribution(no_key_app):
    png1, png2 = _png_bytes((255, 0, 0)), _png_bytes((0, 0, 255))
    response = await _post_convert_images(
        no_key_app, _make_docx_bytes(png1, png2), "bid.docx")

    assert response.status_code == 200
    body = response.json()
    assert len(body["images"]) == 2
    assert body["truncated"] is False
    assert body["warnings"] == []

    first, second = body["images"]
    assert first["index"] == 0
    assert first["mimeType"] == "image/png"
    assert first["section"] == "第一章 总则"
    assert first["pageHint"] is None
    assert first["width"] == 40
    assert first["height"] == 30
    assert base64.b64decode(first["base64"]) == png1

    assert second["index"] == 1
    assert second["section"] == "第二章 业绩要求"
    assert base64.b64decode(second["base64"]) == png2


# ---------------------------------------------------------------------------
# Contract: pptx — image extraction with slide-title section attribution
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pptx_images_with_slide_title(no_key_app):
    png1, png2 = _png_bytes((255, 0, 0)), _png_bytes((0, 0, 255))
    response = await _post_convert_images(
        no_key_app, _make_pptx_bytes(png1, png2), "bid.pptx")

    assert response.status_code == 200
    body = response.json()
    assert len(body["images"]) == 2
    assert body["truncated"] is False

    first, second = body["images"]
    assert first["section"] == "项目概述"
    assert second["section"] == "实施方案"
    assert first["pageHint"] is None
    assert first["mimeType"] == "image/png"
    assert base64.b64decode(first["base64"]) == png1


# ---------------------------------------------------------------------------
# Contract: pdf with text layer — page->nearest-heading section backfill
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pdf_with_text_layer_section_backfill(no_key_app):
    png1, png2 = _png_bytes((0, 128, 0)), _png_bytes((128, 64, 0))
    pdf = _make_pdf_bytes([
        ("第一章 总则\n正文内容。", png1),
        ("第二章 业绩\n正文内容。", png2),
    ])
    response = await _post_convert_images(no_key_app, pdf, "bid.pdf")

    assert response.status_code == 200
    body = response.json()
    assert len(body["images"]) == 2
    assert body["truncated"] is False
    assert body["warnings"] == []

    first, second = body["images"]
    assert first["section"] == "第一章 总则"
    assert first["pageHint"] == 1
    assert second["section"] == "第二章 业绩"
    assert second["pageHint"] == 2
    # PyMuPDF re-encodes embedded images (ICC profiles etc.), so compare
    # decoded pixels rather than raw bytes.
    decoded = base64.b64decode(first["base64"])
    assert decoded.startswith(b"\x89PNG")
    with Image.open(io.BytesIO(decoded)) as embedded:
        assert embedded.size == (40, 30)
        assert embedded.getpixel((0, 0))[:3] == (0, 128, 0)


# ---------------------------------------------------------------------------
# Contract: scanned pdf (no text layer) — section=None + explicit warning
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_pdf_scanned_no_text_layer(no_key_app):
    png = _png_bytes((128, 0, 128))
    pdf = _make_pdf_bytes([("", png)])
    response = await _post_convert_images(no_key_app, pdf, "scan.pdf")

    assert response.status_code == 200
    body = response.json()
    assert len(body["images"]) == 1
    assert body["truncated"] is False

    image = body["images"][0]
    assert image["section"] is None
    assert image["pageHint"] == 1
    assert "扫描件无章节归属" in body["warnings"]


# ---------------------------------------------------------------------------
# Contract: budget truncation — cumulative bytes over SIDECAR_MAX_IMAGE_BYTES
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_budget_truncation_returns_partial_images(no_key_app):
    png1, png2 = _png_bytes((255, 0, 0)), _png_bytes((0, 0, 255))
    # Budget fits the first image (with margin) but not the cumulative sum.
    budget = len(png1) + 10
    with patch.dict(os.environ, {"SIDECAR_MAX_IMAGE_BYTES": str(budget)}):
        response = await _post_convert_images(
            no_key_app, _make_docx_bytes(png1, png2), "bid.docx")

    assert response.status_code == 200
    body = response.json()
    assert len(body["images"]) == 1
    assert body["truncated"] is True
    assert "图片预算截断" in body["warnings"]
    assert base64.b64decode(body["images"][0]["base64"]) == png1


# ---------------------------------------------------------------------------
# Contract: unsupported type — empty manifest (caller falls back to plain text)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_unsupported_type_returns_empty_images(no_key_app):
    response = await _post_convert_images(no_key_app, "纯文本内容".encode("utf-8"), "notes.txt")

    assert response.status_code == 200
    body = response.json()
    assert body == {"images": [], "truncated": False, "warnings": []}


# ---------------------------------------------------------------------------
# Contract: unparseable supported type -> 422 (same semantics as /convert-stream)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_convert_images_unparseable_returns_422(no_key_app):
    response = await _post_convert_images(no_key_app, b"\x00\x01 not a docx", "bad.docx")

    assert response.status_code == 422
    body = response.json()
    assert "error" in body
    # 不泄露内部异常细节（与 /convert-stream 错误语义一致的安全要求）
    assert "internal" not in body["error"]


# ---------------------------------------------------------------------------
# Contract: auth consistent with /convert (401 when keyed and header wrong)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_convert_images_wrong_key_returns_401():
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter", "image_extractor"):
            del sys.modules[mod_name]
    with patch.dict(os.environ, {"SIDECAR_SHARED_KEY": "testsecret"}, clear=False):
        import app as keyed  # noqa: PLC0415

    response = await _post_convert_images(
        keyed.app, b"data", headers={"X-Sidecar-Key": "wrongkey"})
    assert response.status_code == 401
