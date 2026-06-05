"""Sidecar HTTP shell that delegates document-to-markdown conversion to markitdown.

Input:  Multipart HTTP upload of an office/PDF document via POST /convert.
Output: JSON with the converted markdown plus structural metadata (sections, hash, warnings).
Position: Standalone FastAPI process; consumed by the Java backend MarkItDownSidecarTextExtractor.
          The Java caller must pass the shared secret in header X-Sidecar-Key when
          SIDECAR_SHARED_KEY is configured on the sidecar side.
"""
from __future__ import annotations

import hashlib
import logging
import os
import secrets
import subprocess
import tempfile
from typing import Any

from fastapi import FastAPI, Header, HTTPException, UploadFile
from markitdown import MarkItDown

from converter import extract_headings

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration (env-var driven)
# ---------------------------------------------------------------------------

_MAX_UPLOAD_MB: int = int(os.environ.get("SIDECAR_MAX_UPLOAD_MB", "30"))
MAX_UPLOAD_SIZE_BYTES: int = _MAX_UPLOAD_MB * 1024 * 1024

SIDECAR_HOST: str = os.environ.get("SIDECAR_HOST", "127.0.0.1")
SIDECAR_PORT: int = int(os.environ.get("SIDECAR_PORT", "8000"))

_SHARED_KEY: str | None = os.environ.get("SIDECAR_SHARED_KEY")
if not _SHARED_KEY:
    logger.warning(
        "SIDECAR_SHARED_KEY is not set — authentication is disabled. "
        "Set this env var in production to protect the /convert endpoint."
    )

# ---------------------------------------------------------------------------
# App + markitdown instance
# ---------------------------------------------------------------------------

app = FastAPI(title="Document Converter Sidecar")
md = MarkItDown()


# ---------------------------------------------------------------------------
# Auth dependency (inline — single use, no abstraction needed)
# ---------------------------------------------------------------------------

async def _check_auth(x_sidecar_key: str | None = Header(default=None)) -> None:
    """Reject requests with wrong/missing key when SIDECAR_SHARED_KEY is configured."""
    if _SHARED_KEY is None:
        return  # auth disabled in dev mode
    if x_sidecar_key is None or not secrets.compare_digest(x_sidecar_key, _SHARED_KEY):
        raise HTTPException(status_code=401, detail="认证失败")


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.post("/convert")
async def convert_document(
    file: UploadFile,
    x_sidecar_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Convert an uploaded document to markdown and return structured metadata."""
    # Auth check
    await _check_auth(x_sidecar_key)

    if not file.filename:
        raise HTTPException(status_code=400, detail="未提供文件名")

    # --- Stream-read with size guard (CRITICAL-2) ---
    chunks: list[bytes] = []
    total_read: int = 0
    chunk_size: int = 64 * 1024  # 64 KB

    while True:
        chunk = await file.read(chunk_size)
        if not chunk:
            break
        total_read += len(chunk)
        if total_read > MAX_UPLOAD_SIZE_BYTES:
            raise HTTPException(status_code=413, detail="请求体过大")
        chunks.append(chunk)

    content: bytes = b"".join(chunks)

    # SHA-256 for deduplication / cache key on the Java side
    content_hash: str = hashlib.sha256(content).hexdigest()

    original_suffix: str = os.path.splitext(file.filename)[1].lower() or ".bin"

    tmp_path: str | None = None
    converted_path: str | None = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=original_suffix) as tmp:
            tmp.write(content)
            tmp_path = tmp.name

        processing_path: str = tmp_path

        # Legacy .doc → .docx conversion via textutil (macOS only, best-effort)
        if original_suffix == ".doc":
            converted_path = tmp_path + "x"
            try:
                subprocess.run(
                    ["textutil", "-convert", "docx", tmp_path, "-output", converted_path],
                    check=True,
                    capture_output=True,
                )
                processing_path = converted_path
            except Exception:
                logger.warning("textutil .doc→.docx conversion failed; falling back to original")

        result = md.convert(processing_path)
        markdown_text: str = result.text_content

        sections = extract_headings(markdown_text)

        warnings: list[str] = []
        if len(markdown_text.strip()) < 100:
            warnings.append("low_text_density")

        return {
            "documentId": file.filename,
            "markdown": markdown_text,
            "sections": sections,
            "tables": [],
            "warnings": warnings,
            "converter": "markitdown",
            "contentHash": content_hash,
        }

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Document conversion failed for file %s", file.filename)
        raise HTTPException(status_code=500, detail="文档转换失败") from exc
    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)
        if converted_path and os.path.exists(converted_path):
            os.remove(converted_path)


@app.get("/health")
def health_check() -> dict[str, str]:
    """Liveness probe."""
    return {"status": "up"}


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=SIDECAR_HOST, port=SIDECAR_PORT)
