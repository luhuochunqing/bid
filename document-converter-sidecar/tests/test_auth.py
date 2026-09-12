"""Regression tests for Sidecar auth (2026-09-12 401 root cause).

The empty-string env-var trap: SIDECAR_SHARED_KEY="" must disable auth exactly
like an unset var. An empty string previously slipped past the `is None` check
in _check_auth, enabling auth and 401ing every request, which degraded parsing
to PDFBox/人工复核 (see scripts/release/remote-deploy.sh warning).
"""
from __future__ import annotations

import io
import os
import sys
from unittest.mock import patch, MagicMock, AsyncMock

import pytest
from httpx import AsyncClient, ASGITransport


# ---------------------------------------------------------------------------
# Helpers — module-reload pattern shared with test_convert_stream.py
# ---------------------------------------------------------------------------

def _reload_app(shared_key: str | None):
    """Reload app module with SIDECAR_SHARED_KEY set to shared_key (None = unset)."""
    for mod_name in list(sys.modules.keys()):
        if mod_name in ("app", "converter"):
            del sys.modules[mod_name]
    env_without_key = {k: v for k, v in os.environ.items() if k != "SIDECAR_SHARED_KEY"}
    with patch.dict(os.environ, {}, clear=True):
        for k, v in env_without_key.items():
            os.environ[k] = v
        if shared_key is not None:
            os.environ["SIDECAR_SHARED_KEY"] = shared_key
        import app as fresh_app  # noqa: PLC0415
    return fresh_app.app


@pytest.fixture(params=[None, ""], ids=["unset", "empty-string"])
def unset_or_empty_app(request):
    """App whose key is unset or an empty string — both must disable auth."""
    return _reload_app(request.param)


@pytest.fixture()
def keyed_app():
    """App with a real key configured — auth must be enforced."""
    return _reload_app("test-secret-key")


def _post_convert_stream(app, headers: dict | None = None):
    """Async helper: POST /convert-stream with mocked conversion."""
    async def _run():
        app_module = sys.modules["app"]
        mock_result = MagicMock()
        mock_result.text_content = "ok"
        with patch.object(app_module, "_convert_document", new=AsyncMock(return_value=mock_result)):
            async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
                return await client.post(
                    "/convert-stream",
                    files={"file": ("bid.pdf", io.BytesIO(b"%PDF-fake"), "application/pdf")},
                    headers=headers or {},
                )
    return _run()


# ---------------------------------------------------------------------------
# Regression: unset OR empty-string key must NOT enforce auth
# ---------------------------------------------------------------------------

async def test_unset_or_empty_key_accepts_request_without_header(unset_or_empty_app):
    response = await _post_convert_stream(unset_or_empty_app)
    assert response.status_code == 200


# ---------------------------------------------------------------------------
# Configured key: auth must be enforced
# ---------------------------------------------------------------------------

async def test_keyed_app_rejects_missing_or_wrong_key(keyed_app):
    missing = await _post_convert_stream(keyed_app)
    wrong = await _post_convert_stream(keyed_app, headers={"X-Sidecar-Key": "wrong-key"})
    assert missing.status_code == 401
    assert wrong.status_code == 401


async def test_keyed_app_accepts_correct_key(keyed_app):
    response = await _post_convert_stream(keyed_app, headers={"X-Sidecar-Key": "test-secret-key"})
    assert response.status_code == 200
