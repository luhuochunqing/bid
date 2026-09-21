"""Memory-guard tests for POST /convert-images on large bid documents.

Input:  Synthetic docx/PDF fixtures built in-process (no committed binaries):
        (a) a zip-shaped docx carrying 3 x 1MB of incompressible media,
        (b) a real python-docx with two small images,
        (c) a 10-page PDF rendered through renderPages=true.
Output: Assertions that (a) a docx whose word/media/ uncompressed total exceeds
        the budget is skipped WITHOUT materializing the media (bounded peak),
        (b) an in-budget docx keeps extracting images with section attribution,
        (c) PDF page rendering stops early once the source-image budget is hit.
Position: test suite; run with
        `pytest tests/test_convert_images_memory_guard.py -v` from the sidecar directory.

Root cause guarded here (2026-09-21, project 1382): /convert-images ran
MarkItDown(keep_data_uris=True) in-process, so mammoth inlined every embedded
image as base64 (measured 14.7x media size peak). A media-heavy bid docx blew
the 4GB sidecar cgroup limit, the container was OOM-killed mid-request, and the
compliance seal-vision channel silently degraded. The text path already had
pre_process_docx bypass + media slimming; the image path had neither.
"""
from __future__ import annotations

import io
import os
import sys
import tempfile
import zipfile
from unittest.mock import patch

import pytest
from httpx import AsyncClient, ASGITransport

# The guard must keep peak memory far below the media payload; 20MB leaves room
# for interpreter + FastAPI/httpx overhead while still tripping on any
# regression that materializes the 3MB payload as base64 (~4.1MB) plus copies.
OVER_BUDGET_PEAK_BUDGET_BYTES = 20 * 1024 * 1024
MEDIA_ENTRY_BYTES = 1024 * 1024
MEDIA_ENTRY_COUNT = 3


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


def _post_convert_images(app, content: bytes, filename: str, params: dict | None = None):
    async def _run():
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            return await client.post(
                "/convert-images",
                files={"file": (filename, io.BytesIO(content), "application/octet-stream")},
                params=params or {},
            )
    return _run()


def _synth_media_heavy_docx(entry_bytes: int = MEDIA_ENTRY_BYTES,
                            entry_count: int = MEDIA_ENTRY_COUNT) -> bytes:
    """Zip-shaped docx whose word/media/ holds entry_count incompressible files."""
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "[Content_Types].xml",
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="xml" ContentType="application/xml"/></Types>',
        )
        archive.writestr(
            "word/document.xml",
            '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
            "<w:body><w:p><w:r><w:t>Media heavy bid</w:t></w:r></w:p></w:body></w:document>",
        )
        for index in range(entry_count):
            archive.writestr(f"word/media/image_{index}.jpg", os.urandom(entry_bytes))
    return buf.getvalue()


def _synth_small_docx_with_images() -> bytes:
    """Real docx with two small PNGs under a heading (section attribution)."""
    Image = pytest.importorskip("PIL.Image")
    docx_mod = pytest.importorskip("docx")
    buf = io.BytesIO()
    document = docx_mod.Document()
    document.add_heading("Memory guard fixture", level=1)
    for _ in range(2):
        image = Image.new("RGB", (64, 48), (200, 30, 30))
        image_buf = io.BytesIO()
        image.save(image_buf, format="PNG")
        image_buf.seek(0)
        document.add_picture(image_buf)
    document.save(buf)
    return buf.getvalue()


def _synth_pdf(page_count: int = 10) -> bytes:
    pymupdf = pytest.importorskip("pymupdf")
    document = pymupdf.open()
    for index in range(page_count):
        page = document.new_page()
        page.insert_text((72, 72), f"BID PAGE {index + 1}", fontsize=12)
    payload = document.tobytes()
    document.close()
    return payload


@pytest.mark.asyncio
async def test_docx_over_media_budget_skips_extraction_without_materializing(no_key_app, monkeypatch):
    """Over-budget docx: no images, explicit warning, and bounded peak memory."""
    import tracemalloc

    monkeypatch.setenv("SIDECAR_MAX_DOCX_MEDIA_BYTES", str(MEDIA_ENTRY_BYTES))
    content = _synth_media_heavy_docx()

    tracemalloc.start()
    response = await _post_convert_images(no_key_app, content, "media-heavy.docx")
    _, peak = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    assert response.status_code == 200
    payload = response.json()
    assert payload["images"] == []
    assert any("超预算" in warning for warning in payload["warnings"]), payload["warnings"]
    assert peak < OVER_BUDGET_PEAK_BUDGET_BYTES, (
        f"Peak memory {peak / (1024 * 1024):.2f}MB exceeded "
        f"{OVER_BUDGET_PEAK_BUDGET_BYTES / (1024 * 1024):.0f}MB budget"
    )


@pytest.mark.asyncio
async def test_docx_within_media_budget_keeps_extracting_images(no_key_app, monkeypatch):
    """In-budget docx: extraction contract unchanged (images + section attribution)."""
    monkeypatch.delenv("SIDECAR_MAX_DOCX_MEDIA_BYTES", raising=False)
    response = await _post_convert_images(no_key_app, _synth_small_docx_with_images(), "small.docx")

    assert response.status_code == 200
    payload = response.json()
    assert len(payload["images"]) == 2
    assert all(image["section"] == "Memory guard fixture" for image in payload["images"])
    assert all(image["mimeType"] == "image/png" for image in payload["images"])


@pytest.mark.asyncio
async def test_pdf_render_stops_early_when_source_budget_exceeded(no_key_app, monkeypatch):
    """PDF page rendering must stop at the source budget instead of holding all pages."""
    # Boundary budget: the first rendered page always lands, the second trips the
    # guard — deterministic regardless of how well PNG compresses the fixture.
    monkeypatch.setenv("SIDECAR_MAX_SOURCE_IMAGE_BYTES", "1")
    response = await _post_convert_images(
        no_key_app, _synth_pdf(10), "bid.pdf", params={"renderPages": "true"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert len(payload["images"]) == 1
    assert payload["images"][0]["pageHint"] == 1
    assert any("超预算" in warning for warning in payload["warnings"]), payload["warnings"]
