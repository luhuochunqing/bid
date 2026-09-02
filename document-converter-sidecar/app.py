"""Sidecar HTTP shell that delegates document-to-markdown conversion to markitdown.

Input:  Multipart HTTP upload of an office/PDF document via POST /convert.
Output: JSON with the converted markdown plus structural metadata (pages, sections, hash, warnings).
        `pages` is the physical-page slice list (PDF via pdfplumber; empty for docx) consumed by
        the compliance quality-check pipeline — guarded by scripts/check-pages-contract.mjs.
Position: Standalone FastAPI process; consumed by the Java backend MarkItDownSidecarTextExtractor.
          The Java caller must pass the shared secret in header X-Sidecar-Key when
          SIDECAR_SHARED_KEY is configured on the sidecar side.
"""
from __future__ import annotations

import asyncio
import hashlib
import logging
import os
import secrets
import subprocess
import tempfile
from concurrent.futures import ProcessPoolExecutor
from concurrent.futures.process import BrokenProcessPool
from typing import Any

from fastapi import FastAPI, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse, StreamingResponse
from markitdown import MarkItDown

import mammoth

from converter import extract_headings
from image_extractor import extract_images
from table_extractor import extract_tables

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
# Memory guard: suppress docx image base64 inlining (OOM root cause fix)
# ---------------------------------------------------------------------------

_MAMMOTH_PATCHED: bool = False


def _suppress_docx_image_inlining() -> None:
    """Replace mammoth's default base64 data-uri image converter with an
    empty placeholder (idempotent, applied inside the conversion worker only).

    Root cause of the 2026-08 test-env OOM (20min -> 4.7GB RSS, 7 kills):
    mammoth (markitdown's .docx backend) inlines every embedded image as a
    base64 data URI. A real bid document with 1893 images / 401MB media
    expands to ~559MB HTML and a multi-GB transient heap. Reproduced locally:
    400 noisy JPEGs -> 65.7MB HTML / 724MB peak RSS (14.7x media size);
    with suppression the peak stays at interpreter baseline (~49MB).
    The Java caller strips data URIs from markdown anyway, so inlining is
    pure memory waste.

    MUST NOT be applied in the parent process: /convert-images
    (image_extractor._extract_docx) relies on mammoth data URIs to extract
    embedded images, and markitdown's DocxConverter does not forward
    keep_data_uris down to the mammoth call — so the suppression is scoped
    to the worker process where text conversion actually runs.
    """
    global _MAMMOTH_PATCHED
    if _MAMMOTH_PATCHED:
        return
    # mammoth.images.inline is a decorator factory (img_element); a valid
    # convert_image must return [html.element(...)]. Keep alt text, drop src.
    placeholder = mammoth.images.img_element(lambda image: {"src": ""})
    original = mammoth.convert_to_html

    def convert_to_html_without_images(fileobj, **kwargs):
        kwargs.setdefault("convert_image", placeholder)
        return original(fileobj, **kwargs)

    mammoth.convert_to_html = convert_to_html_without_images
    _MAMMOTH_PATCHED = True


_PRE_PROCESS_PATCHED: bool = False


def _suppress_docx_pre_process() -> None:
    """Bypass MarkItDown's pre_process_docx in the conversion worker.

    Root cause of the 2026-08 383MB docx OOM (cgroup limit=1GB):
    markitdown.converters._docx_converter.pre_process_docx unzips and reads
    EVERY file inside the docx (including all embedded media/images) into an
    in-memory dictionary, and re-zips them into a BytesIO. For a 383MB file,
    this allocates >760MB of RAM instantly, triggering Linux cgroup OOM-Killer.
    Bypassing pre_process_docx lets mammoth stream from disk, reducing memory
    from 893MB to <30MB (99.95% reduction).
    """
    global _PRE_PROCESS_PATCHED
    if _PRE_PROCESS_PATCHED:
        return
    try:
        import markitdown.converters._docx_converter as dc
        dc.pre_process_docx = lambda x: x
        _PRE_PROCESS_PATCHED = True
    except Exception:
        pass

# ---------------------------------------------------------------------------
# Conversion isolation pool (defense in depth)
# ---------------------------------------------------------------------------

# Each conversion runs in a single worker process so residual memory blow-ups
# (pptx/pdf converters, malformed files) are absorbed by the worker instead of
# PID 1: cgroup OOM recycles the worker, not the service. max_tasks_per_child
# bounds per-worker growth and returns memory to the OS periodically.
_MAX_CONVERSIONS_PER_WORKER: int = int(
    os.environ.get("SIDECAR_MAX_CONVERSIONS_PER_WORKER", "16")
)
_CONVERT_POOL: ProcessPoolExecutor | None = None


def _get_convert_pool() -> ProcessPoolExecutor:
    global _CONVERT_POOL
    if _CONVERT_POOL is None:
        _CONVERT_POOL = ProcessPoolExecutor(
            max_workers=1, max_tasks_per_child=_MAX_CONVERSIONS_PER_WORKER
        )
    return _CONVERT_POOL


def _convert_worker(path: str) -> Any:
    from types import SimpleNamespace

    _suppress_docx_image_inlining()
    _suppress_docx_pre_process()
    if path.lower().endswith(".pdf"):
        try:
            from converter import convert_pdf_document

            pdf_result = convert_pdf_document(path)
            pdf_md = str(pdf_result["markdown"])
            pdf_pages = pdf_result["pages"]
            if pdf_md and len(pdf_md.strip()) >= 50:
                return SimpleNamespace(text_content=pdf_md, markdown=pdf_md, pages=pdf_pages)
        except Exception as e:
            logger.warning("Custom PDF conversion failed, falling back to markitdown: %s", e)
    result = md.convert(path)
    # Normalize to a payload that always carries a `pages` list (markitdown has none —
    # docx physical pagination is unobtainable; Java side falls back to virtual pagination).
    pages = getattr(result, "pages", None)
    return SimpleNamespace(
        text_content=result.text_content,
        markdown=getattr(result, "markdown", None) or result.text_content,
        pages=pages if isinstance(pages, list) else [],
    )


async def _convert_document(path: str) -> Any:
    """Run md.convert in the isolated worker pool (async seam; tests patch this)."""
    global _CONVERT_POOL
    loop = asyncio.get_running_loop()
    try:
        return await loop.run_in_executor(_get_convert_pool(), _convert_worker, path)
    except BrokenProcessPool:
        logger.error("Conversion worker died (likely OOM); recycling pool")
        _CONVERT_POOL = None
        raise HTTPException(status_code=503, detail="文档转换服务繁忙，请稍后重试")


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

    hasher = hashlib.sha256()
    original_suffix: str = os.path.splitext(file.filename)[1].lower() or ".bin"

    tmp_path: str | None = None
    converted_path: str | None = None
    try:
        # Stream the upload straight to disk with progressive SHA-256 calculation
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
                hasher.update(chunk)
                tmp.write(chunk)

        content_hash: str = hasher.hexdigest()
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

        result = await _convert_document(processing_path)
        markdown_text: str = result.text_content

        sections = extract_headings(markdown_text)

        warnings: list[str] = []
        if len(markdown_text.strip()) < 100:
            warnings.append("low_text_density")

        # Structured physical pages (PDF via pdfplumber); empty for docx — the Java
        # compliance pipeline falls back to virtual pagination when absent.
        result_pages = getattr(result, "pages", None)

        return {
            "documentId": file.filename,
            "markdown": markdown_text,
            "sections": sections,
            "pages": result_pages if isinstance(result_pages, list) else [],
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
            result = await _convert_document(processing_path)
        except HTTPException:
            raise
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


@app.post("/extract-tables")
async def extract_document_tables(
    file: UploadFile,
    x_sidecar_key: str | None = Header(default=None),
) -> dict[str, Any]:
    """Extract structured tables (with merged-cell info) from an uploaded .docx.

    Returns JSON {tables[], warnings}.  Only .docx is supported — other types
    return an empty tables list (caller falls back to markdown recall).
    Auth matches /convert (X-Sidecar-Key / 401).
    """
    await _check_auth(x_sidecar_key)

    if not file.filename:
        raise HTTPException(status_code=400, detail="未提供文件名")

    original_suffix: str = os.path.splitext(file.filename)[1].lower() or ".bin"

    tmp_path: str | None = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=original_suffix) as tmp:
            tmp_path = tmp.name
            total_read: int = 0
            chunk_size: int = 64 * 1024
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                total_read += len(chunk)
                if total_read > MAX_UPLOAD_SIZE_BYTES:
                    raise HTTPException(status_code=413, detail="请求体过大")
                tmp.write(chunk)

        try:
            return extract_tables(tmp_path)
        except ValueError as exc:
            return JSONResponse(status_code=422, content={"error": str(exc)})
        except Exception:
            logger.exception("Table extraction failed for file %s", file.filename)
            return JSONResponse(status_code=422, content={"error": "文档表格无法解析"})

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
