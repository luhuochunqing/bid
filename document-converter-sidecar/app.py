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
from fastapi.responses import JSONResponse, StreamingResponse
from markitdown import MarkItDown

from converter import extract_headings
from image_extractor import extract_images

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration (env-var driven)
# ---------------------------------------------------------------------------

# Default 512 (spec 045 T025): aligned with Java-side app.score-parse.bid-file
# .max-file-bytes (StreamingBidFileFetcher). markitdown conversion of inputs
# beyond ~512MB OOM-kills the sidecar (observed with a 1.62GB bid document);
# both ends MUST keep the same value.
_MAX_UPLOAD_MB: int = int(os.environ.get("SIDECAR_MAX_UPLOAD_MB", "512"))
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


@app.post("/convert-stream")
async def convert_document_stream(
    file: UploadFile,
    x_sidecar_key: str | None = Header(default=None),
) -> StreamingResponse:
    """Convert an uploaded document and stream back plain markdown text (spec 045).

    Contract: specs/045-bid-file-streaming-scoring/contracts/sidecar-streaming-api.md
    Request side mirrors /convert (streamed 64KB reads + 413 guard), response side
    is a text/plain StreamingResponse without JSON metadata. Unparseable documents
    return 422 {"error": ...}; auth matches /convert (X-Sidecar-Key / 401).
    """
    await _check_auth(x_sidecar_key)

    if not file.filename:
        raise HTTPException(status_code=400, detail="未提供文件名")

    original_suffix: str = os.path.splitext(file.filename)[1].lower() or ".bin"

    tmp_path: str | None = None
    converted_path: str | None = None
    try:
        # Stream the upload straight to disk (no in-memory chunk list for GB files)
        with tempfile.NamedTemporaryFile(delete=False, suffix=original_suffix) as tmp:
            tmp_path = tmp.name
            total_read: int = 0
            chunk_size: int = 64 * 1024  # 64 KB
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                total_read += len(chunk)
                if total_read > MAX_UPLOAD_SIZE_BYTES:
                    raise HTTPException(status_code=413, detail="请求体过大")
                tmp.write(chunk)

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

        try:
            result = md.convert(processing_path)
        except Exception:
            logger.exception("Streaming conversion failed for file %s", file.filename)
            return JSONResponse(status_code=422, content={"error": "文档无法解析：格式不支持或已损坏"})

        markdown_text: str = result.text_content or ""

        # Conversion already produced the full text; temp files can be removed now
        # (finally block below). Yield as a chunked text/plain body without JSON.
        return StreamingResponse(iter([markdown_text]), media_type="text/plain; charset=utf-8")

    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)
        if converted_path and os.path.exists(converted_path):
            os.remove(converted_path)


@app.post("/convert-images")
async def convert_document_images(
    file: UploadFile,
    x_sidecar_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Extract embedded images with section attribution from an uploaded document (spec 048).

    Contract: specs/048-score-parse-image-multimodal/contracts/sidecar-images-api.md
    Request side mirrors /convert-stream (streamed 64KB reads + 413 guard);
    response is JSON {images[], truncated, warnings}. Supported types:
    .docx/.pptx/.pdf — anything else returns an empty manifest (the caller
    falls back to plain text). Unparseable documents return 422 {"error": ...};
    auth matches /convert (X-Sidecar-Key / 401).
    """
    await _check_auth(x_sidecar_key)

    if not file.filename:
        raise HTTPException(status_code=400, detail="未提供文件名")

    original_suffix: str = os.path.splitext(file.filename)[1].lower() or ".bin"

    tmp_path: str | None = None
    try:
        # Stream the upload straight to disk (same pattern as /convert-stream)
        with tempfile.NamedTemporaryFile(delete=False, suffix=original_suffix) as tmp:
            tmp_path = tmp.name
            total_read: int = 0
            chunk_size: int = 64 * 1024  # 64 KB
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                total_read += len(chunk)
                if total_read > MAX_UPLOAD_SIZE_BYTES:
                    raise HTTPException(status_code=413, detail="请求体过大")
                tmp.write(chunk)

        try:
            return extract_images(tmp_path)
        except Exception:
            logger.exception("Image extraction failed for file %s", file.filename)
            return JSONResponse(status_code=422, content={"error": "文档无法解析：格式不支持或已损坏"})

    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)


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
