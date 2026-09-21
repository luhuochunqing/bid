"""Pure image-extraction helpers for POST /convert-images (spec 048 T009).

Input:  Path to a document file on disk (.docx / .pptx / .pdf).
Output: {"images": [...], "truncated": bool, "warnings": [...]} per the
        contract in specs/048-score-parse-image-multimodal/contracts/sidecar-images-api.md.
Position: Pure-function module; imported by app.py (the HTTP shell).

Dispatch by extension:
  - .docx: markitdown with keep_data_uris=True (spike 2026-08-19 verified the
    data-URI base64 roundtrips the embedded image bytes losslessly, and
    headings appear in document order — section attribution comes free from
    the nearest preceding markdown heading).
  - .pptx: python-pptx PICTURE shapes; section = slide title.
  - .pdf: PyMuPDF page images; with a text layer the section is backfilled
    from a page->nearest-heading map; a fully scanned PDF (no text layer)
    yields section=None plus an explicit warning.
  - anything else: empty manifest (caller falls back to plain text).
"""
from __future__ import annotations

import base64
import io
import os
import re
import zipfile
from typing import Any, NamedTuple

import pymupdf
from PIL import Image as PILImage
from markitdown import MarkItDown
from pptx import Presentation
from pptx.enum.shapes import MSO_SHAPE_TYPE

# Default 15MB (spec 048 review H1): calibrated against the Java-side
# max-extract-response-bytes of 25MB — 15MB binary ≈ 20MB after base64 ×4/3
# inflation, leaving ~5MB headroom. Bump either side only in sync with the other.
DEFAULT_MAX_IMAGE_BYTES: int = 15 * 1024 * 1024

# docx 图片抽取的媒体体积预算（word/media/ 未压缩总量）。超预算直接跳过抽图：
# mammoth 会把每张内嵌图 base64 内联进 HTML（实测峰值 ≈ 媒体体积的 14.7 倍），
# 4GB 容器下 300MB 级媒体即可触发 cgroup OOM（2026-09-21 项目 1382 事故）。
DEFAULT_MAX_DOCX_MEDIA_BYTES: int = 64 * 1024 * 1024

# PDF 分支累积源图的字节预算：响应预算（DEFAULT_MAX_IMAGE_BYTES）只裁剪返回体，
# 不约束抽取期间的驻留内存，超预算提前停止累积。
DEFAULT_MAX_SOURCE_IMAGE_BYTES: int = 64 * 1024 * 1024

_DOCX_MEDIA_PREFIX = "word/media/"

_md = MarkItDown()

# markdownify emits embedded images as ![alt](data:image/png;base64,XXXX)
_DATA_URI_IMG_RE = re.compile(
    r"!\[[^\]]*\]\(data:(image/[a-zA-Z0-9.+-]+);base64,([A-Za-z0-9+/=]+)\)"
)
_MD_HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")

# PDF heading heuristics: "第X章 ..." / "X.X 标题" / "X、标题"
_PDF_HEADING_PATTERNS = (
    re.compile(r"^第[一二三四五六七八九十百千0-9]+\s*章"),
    re.compile(r"^\d{1,2}(?:\.\d{1,2}){0,3}[、\s]\s*\S+"),
)
_PDF_HEADING_MAX_LINE_LEN = 50

_PIL_FORMAT_TO_MIME = {
    "PNG": "image/png",
    "JPEG": "image/jpeg",
    "GIF": "image/gif",
    "WEBP": "image/webp",
}


class _RawImage(NamedTuple):
    data: bytes
    mime: str
    section: str | None
    page_hint: int | None
    width: int
    height: int


def extract_images(
    path: str,
    max_image_bytes: int | None = None,
    render_pages: bool = False,
    page_numbers: set[int] | None = None,
) -> dict[str, Any]:
    """Extract images with section attribution from a document file.

    Returns the contract payload: {"images": [...], "truncated": bool,
    "warnings": [...]}. Unsupported extensions return an empty manifest;
    supported-but-corrupt files raise and the HTTP shell maps that to 422.
    Cumulative extracted bytes over SIDECAR_MAX_IMAGE_BYTES (default 15MB)
    stop extraction early with truncated=true and keep what was collected.
    """
    if max_image_bytes is None:
        max_image_bytes = int(
            os.environ.get("SIDECAR_MAX_IMAGE_BYTES", str(DEFAULT_MAX_IMAGE_BYTES))
        )

    extension = os.path.splitext(path)[1].lower()
    if extension == ".docx":
        raw_images, warnings = _extract_docx(path)
    elif extension == ".pptx":
        raw_images, warnings = _extract_pptx(path)
    elif extension == ".pdf":
        raw_images, warnings = (
            _render_pdf_pages(path, page_numbers) if render_pages else _extract_pdf(path)
        )
    else:
        return {"images": [], "truncated": False, "warnings": []}

    images: list[dict[str, Any]] = []
    truncated = False
    used_bytes = 0
    for raw in raw_images:
        if used_bytes + len(raw.data) > max_image_bytes:
            truncated = True
            break
        used_bytes += len(raw.data)
        images.append(
            {
                "index": len(images),
                "mimeType": raw.mime,
                "section": raw.section,
                "pageHint": raw.page_hint,
                "width": raw.width,
                "height": raw.height,
                "base64": base64.b64encode(raw.data).decode("ascii"),
            }
        )
    if truncated:
        warnings = [*warnings, "图片预算截断"]
    return {"images": images, "truncated": truncated, "warnings": warnings}


# ---------------------------------------------------------------------------
# .docx — markitdown data-URI output, section = nearest preceding heading
# ---------------------------------------------------------------------------

def _extract_docx(path: str) -> tuple[list[_RawImage], list[str]]:
    # markitdown silently falls back to its plain-text converter for garbage
    # bytes, so gate on the OOXML zip container first — a .docx that is not a
    # zip is unparseable (HTTP shell maps this to 422).
    if not zipfile.is_zipfile(path):
        raise ValueError(f"not a valid docx container: {path}")

    media_budget = int(
        os.environ.get("SIDECAR_MAX_DOCX_MEDIA_BYTES", str(DEFAULT_MAX_DOCX_MEDIA_BYTES))
    )
    media_total = _docx_media_total_bytes(path)
    if media_total > media_budget:
        return [], [
            f"docx媒体体积超预算（{media_total / (1024 * 1024):.1f}MB > "
            f"{media_budget / (1024 * 1024):.0f}MB），跳过图片抽取"
        ]

    _bypass_markitdown_docx_pre_process()
    result = _md.convert(path, keep_data_uris=True)
    markdown_text = result.text_content or ""

    images: list[_RawImage] = []
    warnings: list[str] = []
    current_section: str | None = None
    for line in markdown_text.splitlines():
        heading = _MD_HEADING_RE.match(line.strip())
        if heading:
            current_section = heading.group(2).strip()
            continue
        for match in _DATA_URI_IMG_RE.finditer(line):
            data = base64.b64decode(match.group(2))
            meta = _read_image_meta(data)
            if meta is None:
                warnings.append("跳过无法解析的图片")
                continue
            mime, width, height = meta
            images.append(_RawImage(data, mime, current_section, None, width, height))
    return images, warnings


# ---------------------------------------------------------------------------
# .pptx — PICTURE shapes, section = slide title
# ---------------------------------------------------------------------------

def _extract_pptx(path: str) -> tuple[list[_RawImage], list[str]]:
    presentation = Presentation(path)

    images: list[_RawImage] = []
    warnings: list[str] = []
    for slide in presentation.slides:
        section: str | None = None
        title_shape = slide.shapes.title
        if title_shape is not None and title_shape.has_text_frame:
            section = title_shape.text.strip() or None

        for shape in slide.shapes:
            if shape.shape_type != MSO_SHAPE_TYPE.PICTURE:
                continue
            try:
                blob = shape.image.blob
            except Exception:
                warnings.append("跳过无法读取的幻灯片图片")
                continue
            meta = _read_image_meta(blob)
            if meta is None:
                warnings.append("跳过无法解析的幻灯片图片")
                continue
            mime, width, height = meta
            images.append(_RawImage(blob, mime, section, None, width, height))
    return images, warnings


# ---------------------------------------------------------------------------
# .pdf — PyMuPDF page images, section backfilled from page->nearest heading
# ---------------------------------------------------------------------------

def _match_pdf_heading(line: str) -> str | None:
    candidate = line.strip()
    if not candidate or len(candidate) > _PDF_HEADING_MAX_LINE_LEN:
        return None
    for pattern in _PDF_HEADING_PATTERNS:
        if pattern.match(candidate):
            return candidate
    return None


def _extract_pdf(path: str) -> tuple[list[_RawImage], list[str]]:
    document = pymupdf.open(path)
    try:
        page_texts = [page.get_text() for page in document]
        has_text_layer = any(text.strip() for text in page_texts)

        warnings: list[str] = []
        if not has_text_layer:
            warnings.append("扫描件无章节归属")

        # Page -> heading in effect at the end of that page (carries over pages).
        page_sections: list[str | None] = []
        current: str | None = None
        for text in page_texts:
            for line in text.splitlines():
                heading = _match_pdf_heading(line)
                if heading:
                    current = heading
            page_sections.append(current)

        images: list[_RawImage] = []
        seen_xrefs: set[int] = set()
        total_bytes = 0
        source_budget = _source_image_budget()
        for page_index, page in enumerate(document):
            section = None if not has_text_layer else page_sections[page_index]
            page_hint = page_index + 1
            for image_ref in page.get_images(full=True):
                xref = image_ref[0]
                if xref in seen_xrefs:
                    continue
                seen_xrefs.add(xref)
                try:
                    data = document.extract_image(xref)["image"]
                except Exception:
                    warnings.append(f"跳过无法提取的图片（第 {page_hint} 页）")
                    continue
                meta = _read_image_meta(data)
                if meta is None:
                    warnings.append(f"跳过无法解析的图片（第 {page_hint} 页）")
                    continue
                mime, width, height = meta
                images.append(_RawImage(data, mime, section, page_hint, width, height))
                total_bytes += len(data)
                if total_bytes > source_budget:
                    warnings.append(
                        f"源图字节超预算（>{source_budget / (1024 * 1024):.0f}MB），提前停止抽取"
                    )
                    return images, warnings
        return images, warnings
    finally:
        document.close()


def _render_pdf_pages(
    path: str, page_numbers: set[int] | None
) -> tuple[list[_RawImage], list[str]]:
    """Render requested PDF pages as PNG evidence; page numbers are one-based."""
    document = pymupdf.open(path)
    try:
        selected = page_numbers or set(range(1, len(document) + 1))
        images: list[_RawImage] = []
        warnings: list[str] = []
        total_bytes = 0
        source_budget = _source_image_budget()
        for page_number in sorted(selected):
            if page_number < 1 or page_number > len(document):
                warnings.append(f"跳过越界页码：{page_number}")
                continue
            page = document[page_number - 1]
            pixmap = page.get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5), alpha=False)
            page_bytes = pixmap.tobytes("png")
            images.append(_RawImage(
                page_bytes, "image/png", None, page_number,
                pixmap.width, pixmap.height,
            ))
            total_bytes += len(page_bytes)
            if total_bytes > source_budget:
                warnings.append(
                    f"源图字节超预算（>{source_budget / (1024 * 1024):.0f}MB），提前停止抽取"
                )
                break
        return images, warnings
    finally:
        document.close()


# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------

def _source_image_budget() -> int:
    return int(os.environ.get("SIDECAR_MAX_SOURCE_IMAGE_BYTES", str(DEFAULT_MAX_SOURCE_IMAGE_BYTES)))


def _docx_media_total_bytes(path: str) -> int:
    """word/media/ 未压缩总量：读 zip 中央目录，不解压、常量内存。"""
    total = 0
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            if info.filename.startswith(_DOCX_MEDIA_PREFIX):
                total += info.file_size
    return total


def _bypass_markitdown_docx_pre_process() -> None:
    """幂等绕过 markitdown 的 pre_process_docx（与 app._convert_worker 同款护栏）。

    pre_process_docx 会把 docx 内每个条目（含全部媒体）解到内存再重新打包，
    /convert-images 在父进程内联执行，必须同样绕过，否则大标书在这一步就翻倍。
    """
    try:
        import markitdown.converters._docx_converter as docx_converter  # noqa: PLC0415
    except Exception:
        return
    if getattr(docx_converter.pre_process_docx, "_xiyu_bypassed", False):
        return

    def _identity(path_or_stream: object) -> object:
        return path_or_stream

    _identity._xiyu_bypassed = True  # type: ignore[attr-defined]
    docx_converter.pre_process_docx = _identity


def _read_image_meta(data: bytes) -> tuple[str, int, int] | None:
    """Return (mimeType, width, height), or None when undecodable/unsupported."""
    try:
        with PILImage.open(io.BytesIO(data)) as image:
            pil_format = image.format
            width, height = image.size
    except Exception:
        return None
    mime = _PIL_FORMAT_TO_MIME.get(pil_format or "")
    if mime is None:
        return None
    return mime, width, height
