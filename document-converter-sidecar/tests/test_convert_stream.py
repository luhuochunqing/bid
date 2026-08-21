"""Contract tests for POST /convert-stream (spec 045 T005).

Input:  Multipart uploads against the FastAPI test client (md.convert mocked).
Output: Assertions on the streaming contract defined in
        specs/045-bid-file-streaming-scoring/contracts/sidecar-streaming-api.md:
        200 + text/plain streamed body; empty text layer -> 200 empty body;
        unparseable file -> 422 with {"error": ...}; auth consistent with /convert.
Position: test suite; run with `pytest tests/test_convert_stream.py -v` from the sidecar directory.
"""
from __future__ import annotations

import io
import os
import sys
from unittest.mock import patch, MagicMock, AsyncMock

import pytest
from httpx import AsyncClient, ASGITransport


# ---------------------------------------------------------------------------
# Fixtures (module-reload pattern shared with test_app.py)
# ---------------------------------------------------------------------------

@pytest.fixture()
def no_key_app():
    """App instance with SIDECAR_SHARED_KEY unset (dev mode)."""
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    with patch.dict(os.environ, {}, clear=True):
        for k, v in env_without_key.items():
            os.environ[k] = v
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


def _post_convert_stream(app, content: bytes, filename: str = "bid.pdf",
                         headers: dict | None = None):
    """Async helper: issue POST /convert-stream with the given bytes."""
    async def _run():
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            return await client.post(
                "/convert-stream",
                files={"file": (filename, io.BytesIO(content), "application/octet-stream")},
                headers=headers or {},
            )
    return _run()


# ---------------------------------------------------------------------------
# Contract: 200 + text/plain streamed body
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_convert_stream_returns_200_text_plain(no_key_app):
    mock_result = MagicMock()
    mock_result.text_content = "# 标题\n\n投标文件正文内容。"
    app_module = sys.modules["app"]
    with patch.object(app_module, "_convert_document", new=AsyncMock(return_value=mock_result)):
        response = await _post_convert_stream(no_key_app, b"%PDF-fake-doc")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    assert response.text == "# 标题\n\n投标文件正文内容。"


# ---------------------------------------------------------------------------
# Contract: no text layer -> 200 + empty body
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_convert_stream_no_text_layer_returns_200_empty_body(no_key_app):
    mock_result = MagicMock()
    mock_result.text_content = ""
    app_module = sys.modules["app"]
    with patch.object(app_module, "_convert_document", new=AsyncMock(return_value=mock_result)):
        response = await _post_convert_stream(no_key_app, b"%PDF-scanned-no-text")

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/plain")
    assert response.text == ""


# ---------------------------------------------------------------------------
# Contract: unparseable file -> 422 with {"error": ...}
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_convert_stream_unparseable_returns_422(no_key_app):
    app_module = sys.modules["app"]
    with patch.object(app_module, "_convert_document", new=AsyncMock(side_effect=ValueError("unsupported format"))):
        response = await _post_convert_stream(no_key_app, b"\x00\x01 not a document")

    assert response.status_code == 422
    body = response.json()
    assert "error" in body
    # 不泄露内部异常细节（与 /convert 错误语义一致的安全要求）
    assert "internal" not in body["error"]


# ---------------------------------------------------------------------------
# Contract: auth consistent with /convert (401 when keyed and header wrong)
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_convert_stream_wrong_key_returns_401():
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    with patch.dict(os.environ, {"SIDECAR_SHARED_KEY": "testsecret"}, clear=False):
        import app as keyed  # noqa: PLC0415

    response = await _post_convert_stream(
        keyed.app, b"data", headers={"X-Sidecar-Key": "wrongkey"})
    assert response.status_code == 401
