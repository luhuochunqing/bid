"""Tests for document-converter-sidecar app.py.

Input:  HTTP requests to the FastAPI test client.
Output: Assertions on status codes, response bodies, and logged warnings.
Position: test suite; run with `pytest tests/ -v` from the sidecar directory.
"""
from __future__ import annotations

import io
import os
import logging
import sys
from unittest.mock import patch, MagicMock

import pytest
from httpx import AsyncClient, ASGITransport


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_pdf_bytes(size_bytes: int) -> bytes:
    """Return a minimal byte payload of the given size (not a real PDF)."""
    return b"%PDF-fake" + b"x" * (size_bytes - len(b"%PDF-fake"))


def _reload_app(env: dict[str, str]) -> object:
    """Re-import app module with patched environment variables."""
    import sys
    # Remove cached module so env vars are re-read at module level
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    with patch.dict(os.environ, env, clear=False):
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture()
def no_key_app():
    """App instance with SIDECAR_SHARED_KEY unset (dev mode)."""
    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    import sys
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    with patch.dict(os.environ, {}, clear=True):
        # Re-populate with everything except SIDECAR_SHARED_KEY
        for k, v in env_without_key.items():
            os.environ[k] = v
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


@pytest.fixture()
def keyed_app():
    """App instance with SIDECAR_SHARED_KEY=testsecret."""
    import sys
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    with patch.dict(os.environ, {"SIDECAR_SHARED_KEY": "testsecret"}, clear=False):
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


# ---------------------------------------------------------------------------
# CRITICAL-2: Upload size limit (30 MB default)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_upload_exceeds_limit_returns_413(no_key_app):
    """Payloads over MAX_UPLOAD_SIZE_BYTES must return 413."""
    # Use a small test limit via env so we don't need a real 30 MB buffer
    import sys
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    with patch.dict(os.environ, {"SIDECAR_MAX_UPLOAD_MB": "1"}, clear=False):
        import app as size_limited_app  # noqa: PLC0415

    oversized = _make_pdf_bytes(2 * 1024 * 1024)  # 2 MB > 1 MB limit
    async with AsyncClient(
        transport=ASGITransport(app=size_limited_app.app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/convert",
            files={"file": ("big.pdf", io.BytesIO(oversized), "application/pdf")},
        )
    assert response.status_code == 413


@pytest.mark.asyncio
async def test_upload_within_limit_does_not_413(no_key_app):
    """Payloads within the size limit must not be rejected with 413."""
    import sys
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]

    small_content = b"small content"
    mock_result = MagicMock()
    mock_result.text_content = "# Heading\n\nSome text here that is long enough to pass the density check."

    with patch.dict(os.environ, {"SIDECAR_MAX_UPLOAD_MB": "1"}, clear=False):
        import app as size_limited_app  # noqa: PLC0415

    with patch.object(size_limited_app.md, "convert", return_value=mock_result):
        async with AsyncClient(
            transport=ASGITransport(app=size_limited_app.app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/convert",
                files={"file": ("small.pdf", io.BytesIO(small_content), "application/pdf")},
            )
    assert response.status_code != 413


# ---------------------------------------------------------------------------
# HIGH-1: Authentication via X-Sidecar-Key
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_missing_key_header_returns_401(keyed_app):
    """When SIDECAR_SHARED_KEY is set and header is absent, return 401."""
    async with AsyncClient(
        transport=ASGITransport(app=keyed_app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/convert",
            files={"file": ("doc.pdf", io.BytesIO(b"data"), "application/pdf")},
        )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_wrong_key_header_returns_401(keyed_app):
    """When SIDECAR_SHARED_KEY is set and header value is wrong, return 401."""
    async with AsyncClient(
        transport=ASGITransport(app=keyed_app), base_url="http://test"
    ) as client:
        response = await client.post(
            "/convert",
            headers={"X-Sidecar-Key": "wrongkey"},
            files={"file": ("doc.pdf", io.BytesIO(b"data"), "application/pdf")},
        )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_correct_key_header_proceeds(keyed_app):
    """When SIDECAR_SHARED_KEY is set and header matches, request proceeds past auth."""
    mock_result = MagicMock()
    mock_result.text_content = "# Title\n\nBody text that is sufficiently long for the density check."
    # Patch the md instance on the module that backs keyed_app
    app_module = sys.modules["app"]
    with patch.object(app_module.md, "convert", return_value=mock_result):
        async with AsyncClient(
            transport=ASGITransport(app=keyed_app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/convert",
                headers={"X-Sidecar-Key": "testsecret"},
                files={"file": ("doc.pdf", io.BytesIO(b"data"), "application/pdf")},
            )
    # Must not be 401 (may be 200 or 500 depending on markitdown processing)
    assert response.status_code != 401


@pytest.mark.asyncio
async def test_no_key_configured_allows_request_without_header(no_key_app, caplog):
    """When SIDECAR_SHARED_KEY is unset, requests succeed without X-Sidecar-Key (dev compat)."""
    app_module = sys.modules["app"]
    mock_result = MagicMock()
    mock_result.text_content = "# Title\n\nBody text that is sufficiently long for the density check."
    with patch.object(app_module.md, "convert", return_value=mock_result):
        async with AsyncClient(
            transport=ASGITransport(app=no_key_app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/convert",
                files={"file": ("doc.pdf", io.BytesIO(b"data"), "application/pdf")},
            )
    assert response.status_code != 401


@pytest.mark.asyncio
async def test_no_key_configured_logs_warning(caplog):
    """When SIDECAR_SHARED_KEY is unset, a startup warning must be logged."""
    import sys
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]

    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    with patch.dict(os.environ, {}, clear=True):
        for k, v in env_without_key.items():
            os.environ[k] = v
        with caplog.at_level(logging.WARNING):
            import app  # noqa: PLC0415  # triggers module-level warning

    assert any("SIDECAR_SHARED_KEY" in record.message for record in caplog.records)


# ---------------------------------------------------------------------------
# MED-1: Exception details must not leak to client
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_conversion_error_returns_generic_chinese_message(no_key_app):
    """Conversion exceptions must return the generic Chinese error, not the raw exception."""
    app_module = sys.modules["app"]
    with patch.object(app_module.md, "convert", side_effect=RuntimeError("internal details")):
        async with AsyncClient(
            transport=ASGITransport(app=no_key_app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/convert",
                files={"file": ("doc.pdf", io.BytesIO(b"data"), "application/pdf")},
            )
    assert response.status_code == 500
    body = response.json()
    assert body["detail"] == "文档转换失败"
    assert "internal details" not in response.text


# ---------------------------------------------------------------------------
# Converter helper unit tests (pure logic in converter.py)
# ---------------------------------------------------------------------------

def test_extract_headings_empty_string():
    from converter import extract_headings
    assert extract_headings("") == []


def test_extract_headings_single_heading():
    from converter import extract_headings
    result = extract_headings("# Hello\n\nSome body.")
    assert len(result) == 1
    assert result[0]["heading"] == "Hello"
    assert result[0]["level"] == 1
    assert result[0]["path"] == ["Hello"]


def test_extract_headings_nested():
    from converter import extract_headings
    md = "# Top\n\n## Sub\n\n### Deep\n"
    result = extract_headings(md)
    assert result[0]["path"] == ["Top"]
    assert result[1]["path"] == ["Top", "Sub"]
    assert result[2]["path"] == ["Top", "Sub", "Deep"]


def test_extract_headings_char_offsets():
    from converter import extract_headings
    text = "# A\n\n## B\n"
    result = extract_headings(text)
    # charEnd of first section should equal charStart of second
    assert result[0]["charEnd"] == result[1]["charStart"]
    # charEnd of last section should equal len(text)
    assert result[-1]["charEnd"] == len(text)


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_health_check(no_key_app):
    async with AsyncClient(
        transport=ASGITransport(app=no_key_app), base_url="http://test"
    ) as client:
        response = await client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "up"}
