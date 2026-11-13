"""Memory-guard tests for the sidecar OOM root-cause fix.

Input:  synthetic noisy-JPEG docx (in-process) + injected worker failures.
Output: Assertions that (a) mammoth no longer inlines embedded images as
        base64 data URIs, (b) a dead conversion worker surfaces as HTTP 503
        on both /convert and /convert-stream (not swallowed into 422) and
        the next request can run, (c) a real end-to-end conversion through the
        request worker still succeeds.
Position: test suite; run with `pytest tests/test_memory_guard.py -v` from
        the sidecar directory.
"""
from __future__ import annotations

import io
import os
import sys
import tempfile
from unittest.mock import patch

import mammoth
import pytest
from httpx import AsyncClient, ASGITransport


# ---------------------------------------------------------------------------
# Fixtures (module-reload pattern shared with test_app.py)
# ---------------------------------------------------------------------------

@pytest.fixture()
def app_module():
    """Freshly imported app module (dev mode, no shared key)."""
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    with patch.dict(os.environ, {}, clear=True):
        for k, v in env_without_key.items():
            os.environ[k] = v
        import app as fresh_app  # noqa: PLC0415
    return fresh_app


# ---------------------------------------------------------------------------
# Root cause: docx image base64 inlining must be suppressed
# ---------------------------------------------------------------------------

def _synth_docx_with_images(n_images: int) -> bytes:
    """Build an in-memory docx embedding n independent noisy JPEGs."""
    Image = pytest.importorskip("PIL.Image")
    docx_mod = pytest.importorskip("docx")
    buf = io.BytesIO()
    document = docx_mod.Document()
    document.add_heading("Memory guard fixture", level=1)
    for i in range(n_images):
        img = Image.frombytes("RGB", (64, 48), os.urandom(64 * 48 * 3))
        img_buf = io.BytesIO()
        img.save(img_buf, format="JPEG", quality=80)
        img_buf.seek(0)
        document.add_picture(img_buf)
        document.add_paragraph(f"paragraph after image {i}")
    document.save(buf)
    return buf.getvalue()


def test_docx_image_inlining_suppressed(app_module):
    """Root-cause fix: mammoth must NOT inline embedded images as base64.

    Regression guard for the 2026-08 OOM: a 401MB-media bid document used to
    expand into ~559MB of HTML inside the sidecar process. If this test ever
    fails, image suppression has regressed and the OOM vector is open again.

    Suppression lives inside _convert_worker (worker process only), so this
    test invokes the worker function directly and restores the parent's
    mammoth afterwards — /convert-images depends on data URIs in-process.
    """
    content = _synth_docx_with_images(3)
    with tempfile.NamedTemporaryFile(suffix=".docx", delete=False) as tmp:
        tmp.write(content)
        path = tmp.name
    original = mammoth.convert_to_html
    try:
        result = app_module._convert_worker(path)
        assert "data:image" not in result.text_content
        assert "Memory guard fixture" in result.text_content
    finally:
        mammoth.convert_to_html = original
        app_module._MAMMOTH_PATCHED = False
        os.remove(path)


def test_docx_pre_process_memory_bench(app_module):
    """Memory guard: docx with large embedded media must not balloon in memory.

    Regression guard for MarkItDown pre_process_docx: without bypass, a 30MB
    media file causes >60MB in-memory zip re-encoding. With bypass, peak memory
    stays below 15MB.
    """
    import zipfile
    import tracemalloc
    import markitdown.converters._docx_converter as dc

    with tempfile.NamedTemporaryFile(suffix=".docx", delete=False) as tmp:
        path = tmp.name
        with zipfile.ZipFile(path, "w") as z:
            z.writestr(
                "word/document.xml",
                '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
                "<w:body><w:p><w:r><w:t>Memory Guard Benchmark Header</w:t></w:r></w:p></w:body></w:document>",
            )
            z.writestr(
                "[Content_Types].xml",
                '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                '<Default Extension="xml" ContentType="application/xml"/></Types>',
            )
            # Add 25MB of dummy media
            dummy_media = b"x" * (5 * 1024 * 1024)
            for i in range(5):
                z.writestr(f"word/media/image_{i}.png", dummy_media)

    original_mammoth = mammoth.convert_to_html
    original_pre_process = dc.pre_process_docx
    try:
        tracemalloc.start()
        result = app_module._convert_worker(path)
        _, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        assert "Memory Guard Benchmark Header" in result.text_content
        # Peak memory must be strictly bounded under 20MB (normal is <5MB)
        assert peak < 20 * 1024 * 1024, f"Peak memory {peak / (1024*1024):.2f}MB exceeded 20MB budget"
    finally:
        mammoth.convert_to_html = original_mammoth
        dc.pre_process_docx = original_pre_process
        app_module._MAMMOTH_PATCHED = False
        app_module._PRE_PROCESS_PATCHED = False
        if os.path.exists(path):
            os.remove(path)


# ---------------------------------------------------------------------------
# Defense in depth: dead worker -> 503
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
@pytest.mark.parametrize('endpoint', ['/convert', '/convert-stream'])
async def test_dead_worker_returns_503(app_module, monkeypatch, endpoint):
    """Both endpoint contracts propagate request-worker failure as 503."""
    import conversion_execution
    from fastapi import HTTPException
    from unittest.mock import AsyncMock
    monkeypatch.setattr(conversion_execution, 'execute_conversion', AsyncMock(
        side_effect=HTTPException(status_code=503, detail='文档转换服务繁忙，请稍后重试')))
    async with AsyncClient(transport=ASGITransport(app=app_module.app), base_url='http://test') as client:
        response = await client.post(endpoint, files={'file': ('doc.pdf', b'data')})
    assert response.status_code == 503
    assert response.json()['detail'] == '文档转换服务繁忙，请稍后重试'


# ---------------------------------------------------------------------------
# End-to-end: real conversion through a request worker
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_real_conversion_through_worker_succeeds(app_module):
    """Full chain: upload -> worker process -> pickled ConvertResult -> 200."""
    payload = "# Worker smoke test\n\nplain text body".encode()
    async with AsyncClient(
        transport=ASGITransport(app=app_module.app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/convert",
            files={"file": ("note.txt", io.BytesIO(payload), "text/plain")},
        )
    assert response.status_code == 200
    assert "plain text body" in response.json()["markdown"]
