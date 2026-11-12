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
import re
import secrets
import subprocess
import tempfile
import time
from concurrent.futures import ProcessPoolExecutor
from concurrent.futures.process import BrokenProcessPool
from typing import Any

from fastapi import FastAPI, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse, StreamingResponse
from markitdown import MarkItDown

import mammoth

from converter import extract_headings, extract_pdf_tables_artifact
from image_extractor import extract_images
from table_extractor import extract_tables, extract_tables_artifact, SCHEMA_VERSION

# Compiled once — replaces markdown image tokens with 【已附图片】 in extracted text so the
# LLM compliance reviewer reads plain-text evidence markers rather than broken image links.
_MD_IMG_PATTERN = re.compile(r"!\[[^\]]*\]\([^)]*\)")


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)


def _log_conversion(
    file_name: str,
    size_bytes: int,
    started_at: float,
    markdown: str,
    page_count: int,
    warnings: list[str],
) -> None:
    """Structured per-conversion observability log (spec 066 D6/FR-009).

    Empty text is the scanned-document signature; log it at WARN so the
    production signal is visible without turning on debug logging.
    """
    duration_ms = int((time.perf_counter() - started_at) * 1000)
    chars = len(markdown)
    message = "convert file=%s size=%d duration_ms=%d chars=%d pages=%d warnings=%s"
    args = (file_name, size_bytes, duration_ms, chars, page_count, warnings)
    if chars == 0:
        logger.warning(message, *args)
    else:
        logger.info(message, *args)

# ---------------------------------------------------------------------------
# Configuration (env-var driven)
# ---------------------------------------------------------------------------

# Default 1024 (spec 045 T025): aligned with Java-side app.score-parse.bid-file
# .max-file-bytes (StreamingBidFileFetcher). Production may raise this explicitly
# (currently 3072MB); both ends MUST keep the configured values compatible.
_MAX_UPLOAD_MB: int = int(os.environ.get("SIDECAR_MAX_UPLOAD_MB", "1024"))
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
    # convert_image must return [html.element(...)]. Keep src stub + alt text
    # so markitdown preserves "已附图片" in table cells — compliance policies
    # use this marker to detect embedded image evidence (e.g. ID card scans,
    # business-license photos) without needing to read the image data itself.
    placeholder = mammoth.images.img_element(
        lambda image: {"src": "embedded-image", "alt": "已附图片"}
    )
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


def _is_large_docx(docx_path: str, threshold_bytes: int = 10 * 1024 * 1024) -> bool:
    """Check whether a .docx file's word/document.xml exceeds the given uncompressed size threshold."""
    import zipfile

    try:
        with zipfile.ZipFile(docx_path) as z:
            info = z.getinfo("word/document.xml")
            return info.file_size > threshold_bytes
    except Exception:
        return False


def _extract_docx_streaming_sax(docx_path: str) -> str:
    """Memory-efficient streaming SAX extractor for .docx documents.

    Bypasses python-docx and mammoth DOM generation by streaming `word/document.xml`
    via `xml.etree.ElementTree.iterparse`. Maintains O(1) memory by clearing elements
    as soon as they are processed.
    """
    import zipfile
    import xml.etree.ElementTree as ET

    try:
        with zipfile.ZipFile(docx_path) as z:
            if "word/document.xml" not in z.namelist():
                return ""
            with z.open("word/document.xml") as f:
                chunks = []
                current_p = []
                current_cell = []
                current_row = []
                table_rows = []
                in_cell = False
                heading_level = 0

                context = ET.iterparse(f, events=("start", "end"))
                _, root = next(context)

                for event, elem in context:
                    tag = elem.tag.split("}")[-1] if "}" in elem.tag else elem.tag

                    if event == "start":
                        if tag == "tc":
                            in_cell = True
                            current_cell = []
                        elif tag == "tr":
                            current_row = []
                        elif tag == "tbl":
                            table_rows = []
                        elif tag == "p":
                            heading_level = 0
                        elif tag == "pStyle":
                            val = elem.attrib.get(
                                "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}val",
                                elem.attrib.get("val", "")
                            )
                            val_lower = val.lower()
                            if "heading" in val_lower or "标题" in val_lower:
                                for digit in "123456":
                                    if digit in val:
                                        heading_level = int(digit)
                                        break
                                if heading_level == 0:
                                    heading_level = 1
                    elif event == "end":
                        if tag == "t" and elem.text:
                            current_p.append(elem.text)
                        elif tag in ("blip", "imagedata") or tag.lower().endswith("blip"):
                            if not current_p or current_p[-1] != "【已附图片】":
                                current_p.append("【已附图片】")
                        elif tag == "tab":
                            current_p.append("\t")
                        elif tag == "br":
                            current_p.append("\n")
                        elif tag == "p":
                            p_text = "".join(current_p).strip()
                            current_p = []
                            if in_cell:
                                if p_text:
                                    current_cell.append(p_text)
                            else:
                                if p_text:
                                    if heading_level > 0:
                                        prefix = "#" * min(heading_level, 6) + " "
                                        chunks.append(prefix + p_text + "\n\n")
                                    else:
                                        chunks.append(p_text + "\n\n")
                            heading_level = 0
                        elif tag == "tc":
                            in_cell = False
                            cell_text = " ".join(current_cell).replace("\n", " ").replace("|", "\\|").strip()
                            current_row.append(cell_text)
                            current_cell = []
                        elif tag == "tr":
                            if current_row:
                                table_rows.append(current_row)
                                current_row = []
                        elif tag == "tbl":
                            if table_rows:
                                max_cols = max(len(r) for r in table_rows)
                                if max_cols > 0:
                                    normalized = [r + [""] * (max_cols - len(r)) for r in table_rows]
                                    chunks.append("| " + " | ".join(normalized[0]) + " |\n")
                                    chunks.append("| " + " | ".join(["---"] * max_cols) + " |\n")
                                    for r in normalized[1:]:
                                        chunks.append("| " + " | ".join(r) + " |\n")
                                    chunks.append("\n")
                            table_rows = []

                        elem.clear()
                root.clear()
                return "".join(chunks).strip()
    except Exception as e:
        logger.warning("Streaming SAX extraction failed for %s: %s", docx_path, e)
        return ""


def _with_scoring_docx_ocr(path: str, text: str, pdf_ocr: bool) -> str:
    """Scoring opt-in only. Other callers keep the existing Word text."""
    if not pdf_ocr:
        return text
    from converter import append_docx_image_ocr
    return append_docx_image_ocr(path, text or "")


def _convert_worker(path: str, pdf_ocr: bool = False) -> Any:
    from types import SimpleNamespace

    if pdf_ocr and path.lower().endswith(".pdf"):
        from converter import convert_scoring_pdf_document
        result = convert_scoring_pdf_document(path)
        return SimpleNamespace(text_content=result["markdown"], markdown=result["markdown"], pages=result["pages"])

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

    if path.lower().endswith(".docx"):
        from docx_repair import auto_repaired_docx

        with auto_repaired_docx(path) as target_path:
            if _is_large_docx(target_path):
                logger.info("Docx document.xml exceeds threshold; using streaming SAX parser: %s", target_path)
                sax_text = _extract_docx_streaming_sax(target_path)
                if sax_text and len(sax_text.strip()) >= 50:
                    text = _with_scoring_docx_ocr(target_path, sax_text, pdf_ocr)
                    return SimpleNamespace(text_content=text, markdown=text, pages=[])
            try:
                result = md.convert(target_path)
            except Exception as e:
                logger.warning("markitdown failed on docx (%s), falling back to streaming SAX: %s", e, target_path)
                sax_text = _extract_docx_streaming_sax(target_path)
                if pdf_ocr or (sax_text and len(sax_text.strip()) >= 50):
                    text = _with_scoring_docx_ocr(target_path, sax_text, pdf_ocr)
                    return SimpleNamespace(text_content=text, markdown=text, pages=[])
                raise
            text = _MD_IMG_PATTERN.sub("【已附图片】", result.text_content or "")
            text = _with_scoring_docx_ocr(target_path, text, pdf_ocr)
            return SimpleNamespace(
                text_content=text,
                markdown=text if pdf_ocr else (getattr(result, "markdown", None) or result.text_content),
                pages=[])
    else:
        result = md.convert(path)

    # Normalize to a payload that always carries a `pages` list (markitdown has none —
    # docx physical pagination is unobtainable; Java side falls back to virtual pagination).
    pages = getattr(result, "pages", None)
    # Replace markdown image tokens with a plain-text evidence marker so the LLM compliance
    # reviewer correctly interprets embedded images as attached evidence rather than broken
    # links.  Pattern: ![any alt text](any src) → 【已附图片】
    # The Java IMAGE_EVIDENCE_PATTERN also matches "已附图片" as a literal substring,
    # so rule-based checks continue to work.
    normalised_text = _MD_IMG_PATTERN.sub("【已附图片】", result.text_content)
    return SimpleNamespace(
        text_content=normalised_text,
        markdown=getattr(result, "markdown", None) or result.text_content,
        pages=pages if isinstance(pages, list) else [],
    )


def _safe_convert_worker(path: str, pdf_ocr: bool = False) -> Any:
    """Wrapper around _convert_worker ensuring no unpicklable exceptions cross processes.

    Python 3.11 multiprocessing fails to pickle certain chained exceptions (like
    markitdown.FileConversionException with nested tracebacks), throwing:
    TypeError: cannot pickle 'traceback' object.
    Catching and packing error details as a dictionary avoids crashing the worker pool.
    """
    try:
        return _convert_worker(path, pdf_ocr) if pdf_ocr else _convert_worker(path)
    except Exception as exc:
        from converter import ScoringPdfOcrError
        if isinstance(exc, ScoringPdfOcrError):
            return {"error": str(exc), "code": str(exc)}
        logger.exception("Worker conversion failed for %s: %s", path, exc)
        return {"error": f"{type(exc).__name__}: {str(exc)}"}


async def _convert_document(path: str, pdf_ocr: bool = False) -> Any:
    """Run md.convert in the isolated worker pool (async seam; tests patch this)."""
    global _CONVERT_POOL
    loop = asyncio.get_running_loop()
    try:
        args = (path, True) if pdf_ocr else (path,)
        res = await loop.run_in_executor(_get_convert_pool(), _safe_convert_worker, *args)
        if isinstance(res, dict) and "error" in res:
            if res.get("code") in ("BID_OCR_TIMEOUT", "BID_OCR_UNAVAILABLE", "BID_OCR_EMPTY"):
                raise HTTPException(status_code=422, detail=res["code"])
            raise RuntimeError(res["error"])
        return res
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
    """Reject requests with wrong/missing key when SIDECAR_SHARED_KEY is configured.

    Uses `not _SHARED_KEY` (not `is None`) so an empty-string env var also disables
    auth — otherwise every request 401s (2026-09-12 parsing-degradation root cause,
    see scripts/release/remote-deploy.sh).
    """
    if not _SHARED_KEY:
        return  # auth disabled when key is unset or empty
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

        started_at = time.perf_counter()
        result = await _convert_document(processing_path)
        markdown_text: str = result.text_content

        sections = extract_headings(markdown_text)

        warnings: list[str] = []
        if len(markdown_text.strip()) < 100:
            warnings.append("low_text_density")

        # Structured physical pages (PDF via pdfplumber); empty for docx — the Java
        # compliance pipeline falls back to virtual pagination when absent.
        result_pages = getattr(result, "pages", None)
        _log_conversion(
            file.filename,
            total_read,
            started_at,
            markdown_text,
            len(result_pages) if isinstance(result_pages, list) else 0,
            warnings,
        )

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
    x_pdf_ocr: bool = Header(default=False),
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

        started_at = time.perf_counter()
        try:
            if x_pdf_ocr:
                result = await _convert_document(processing_path, pdf_ocr=True)
            else:
                result = await _convert_document(processing_path)
        except HTTPException:
            raise
        except Exception:
            logger.exception("Streaming conversion failed for file %s", file.filename)
            return JSONResponse(status_code=422, content={"error": "文档无法解析：格式不支持或已损坏"})

        markdown_text: str = result.text_content or ""

        stream_pages = getattr(result, "pages", None)
        stream_warnings: list[str] = []
        if len(markdown_text.strip()) < 100:
            stream_warnings.append("low_text_density")
        _log_conversion(
            file.filename,
            total_read,
            started_at,
            markdown_text,
            len(stream_pages) if isinstance(stream_pages, list) else 0,
            stream_warnings,
        )

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
    renderPages: bool = False,
    pages: str | None = None,
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
            page_numbers = None
            if pages:
                try:
                    page_numbers = {int(value) for value in pages.split(",") if value.strip()}
                except ValueError as error:
                    raise HTTPException(status_code=400, detail="pages 必须是逗号分隔的正整数") from error
            return extract_images(tmp_path, render_pages=renderPages, page_numbers=page_numbers)
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
    schema_version: str | None = Header(default=None, alias="X-Schema-Version"),
) -> dict[str, Any]:
    """Extract structured tables (with merged-cell info) from an uploaded .docx/.pdf.

    Default contract (spec 059 Phase 1):
        schemaVersion = "2.2" — versioned DocumentArtifact with cell IDs and OCR text regions,
        XML paths and merge region coordinates.

    Legacy v1 callers can request the old shape via ``X-Schema-Version: 1.0`` —
    useful while the Java backend (Phase 5) is rolled over to v2.
    Returns JSON {tables[], warnings}.  .docx preserves Word merge metadata;
    .pdf emits physical-page/cell coordinates for Evidence Pipeline routing.
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
            chunk_size: int = 64 * 1024  # 64 KB
            while True:
                chunk = await file.read(chunk_size)
                if not chunk:
                    break
                total_read += len(chunk)
                if total_read > MAX_UPLOAD_SIZE_BYTES:
                    raise HTTPException(status_code=413, detail="请求体过大")
                tmp.write(chunk)

        # v2 (default) returns DocumentArtifact; v1 (legacy) returns the old
        # {tables, warnings} shape.  Phase 5 will retire the v1 endpoint once
        # the Java backend fully adopts CanonicalTableGrid.
        # Version echo (release gate): the Java v1 client fail-fasts when the
        # response header/body is not v1, and release smoke fingerprints this
        # header.  Bodies are unchanged (v1 has no schemaVersion field).
        v1_headers = {"X-Schema-Version": "1.0"}
        v2_headers = {"X-Schema-Version": SCHEMA_VERSION}
        if schema_version and schema_version.startswith("1."):
            try:
                return JSONResponse(content=extract_tables(tmp_path), headers=v1_headers)
            except ValueError as exc:
                return JSONResponse(status_code=422, content={"error": str(exc)}, headers=v1_headers)
            except Exception:
                logger.exception("Table extraction failed for file %s", file.filename)
                raise HTTPException(status_code=500, detail="表格提取失败")
        try:
            artifact = (
                extract_pdf_tables_artifact(tmp_path)
                if original_suffix == ".pdf"
                else extract_tables_artifact(tmp_path)
            )
            artifact["schemaVersion"] = SCHEMA_VERSION
            return JSONResponse(content=artifact, headers=v2_headers)
        except ValueError as exc:
            return JSONResponse(status_code=422, content={"error": str(exc)}, headers=v2_headers)
        except Exception:
            logger.exception("Table extraction failed for file %s", file.filename)
            raise HTTPException(status_code=500, detail="表格提取失败")

    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.remove(tmp_path)


@app.get("/health")
def health_check() -> dict[str, str]:
    """Liveness probe (X-Schema-Version header echoes the table contract version)."""
    return JSONResponse(content={"status": "up"}, headers={"X-Schema-Version": SCHEMA_VERSION})


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host=SIDECAR_HOST, port=SIDECAR_PORT)
