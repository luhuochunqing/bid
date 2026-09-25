"""Conversion helpers: heading extraction, PDF->markdown/pages, price-table merging.

Input:  Raw markdown text (heading extraction) or a PDF file path (pdfplumber conversion).
Output: Heading dictionaries with level/path/char offsets; PDF whole-document markdown and
        per-page slices [{"pageNumber", "text"}] (convert_pdf_document) consumed by the Java
        compliance pipeline — field contract guarded by scripts/check-pages-contract.mjs.
Position: Imported by app.py (the HTTP shell). Heading analysis is pure; the PDF conversion
        functions perform file I/O via pdfplumber and run inside the worker process.
"""
from __future__ import annotations

import csv
import hashlib
import io
import os
import re
import subprocess
from collections import Counter

SCHEMA_VERSION = "2.2"
PDF_TABLE_EXTRACTION_VERSION = "pdf-table-v1"
PDF_OCR_MAX_PAGES = 30
PDF_OCR_MIN_CONFIDENCE = 70.0

# 报价评分参数关键词（与 Java 侧 backend/src/main/java/com/xiyu/bid/biddraftagent/domain/PriceScoringKeywords.java
# 的 PRICE_PARAMS 保持同步；两处修改必须同步，Java 侧另有 ScoreParsePromptsTest.buildCandidateExtractionPrompt_mustContainCriticalPriceParams 校验与 prompt 的同步）
# 长词参数：子串匹配无歧义；短词参数：与评分项编号（L10/L1级 等）存在歧义，
# 必须按词边界匹配（Unicode \b），与 Java 侧 PRICE_PARAM_ROW 的短词纪律一致。
PRICE_PARAM_LONG_KEYWORDS = (
    "下浮率", "BP%", "HP%", "LP%", "基准价", "评标基准价", "平均价", "最高价", "最低价",
    "折扣率", "单边法", "报价人", "进入价格", "价格分计算", "参数描述",
)
PRICE_PARAM_SHORT_KEYWORDS = ("L1", "L2", "RH", "RL")
PRICE_PARAM_KEYWORDS = PRICE_PARAM_LONG_KEYWORDS + PRICE_PARAM_SHORT_KEYWORDS
_SHORT_TOKEN_PATTERN = re.compile(r"\b(?:" + "|".join(PRICE_PARAM_SHORT_KEYWORDS) + r")\b")


def _row_has_price_param(row: str) -> bool:
    """行级报价参数命中：长词子串匹配；短词需词边界（防 L1级/L10 等子串误命中）。"""
    return any(kw in row for kw in PRICE_PARAM_LONG_KEYWORDS) or bool(_SHORT_TOKEN_PATTERN.search(row))


def _pdf_chunk_pages() -> int:
    """Pages per open/close chunk (env-overridable for tests/ops tuning).

    Default 100 (spec 066 D4): reopening a 441MB PDF costs ~2.5s per open —
    411 opens of 10 pages dominated ~90% of a 1133s conversion. 100-page
    chunks cut that to ~42 opens while keeping peak residency O(chunk).
    """
    try:
        return max(1, int(os.environ.get("SIDECAR_PDF_CHUNK_PAGES", "100")))
    except (TypeError, ValueError):
        return 100


def _convert_pdf_chunk(pdf_path: str, start: int, end: int) -> list[dict]:
    """Extract pages [start, end) with all pdfplumber structures freed on return.

    Root-cause fix for project 1373 (428MB bid PDF OOM-killed the 3GB worker
    at ~2.98GB RSS): the legacy single-open loop held every page's parsed
    structures (chars/edges/tables) for the whole document. Scoping one chunk
    per open/close bounds the peak to O(chunk) plus accumulated strings.
    """
    import pdfplumber

    chunk: list[dict] = []
    with pdfplumber.open(pdf_path) as pdf:
        for index in range(start, end):
            page = pdf.pages[index]
            tables = page.extract_tables() or []
            table_markdowns: list[str] = []
            for table in tables:
                if not table or not any(table):
                    continue
                cleaned: list[list[str]] = []
                for row in table:
                    cleaned_row: list[str] = []
                    for cell in row:
                        if cell is None:
                            cleaned_row.append("")
                        else:
                            cleaned_row.append(str(cell).strip().replace("\n", "<br>"))
                    cleaned.append(cleaned_row)
                if not cleaned:
                    continue
                max_cols = max(len(r) for r in cleaned)
                if max_cols == 0:
                    continue
                header = cleaned[0] + [""] * (max_cols - len(cleaned[0]))
                sep = ["---"] * max_cols
                lines = ["| " + " | ".join(header) + " |", "| " + " | ".join(sep) + " |"]
                for row in cleaned[1:]:
                    r = row + [""] * (max_cols - len(row))
                    lines.append("| " + " | ".join(r) + " |")
                table_markdowns.append("\n".join(lines))

            text = (page.extract_text() or "").strip()
            if table_markdowns:
                if text:
                    page_combined = text + "\n\n" + "\n\n".join(table_markdowns)
                else:
                    page_combined = "\n\n".join(table_markdowns)
            else:
                page_combined = text

            if page_combined.strip():
                chunk.append({
                    "pageNumber": index + 1,
                    "text": page_combined.strip().replace("\x0c", "\n\n"),
                })
    return chunk


def convert_pdf_to_pages(pdf_path: str) -> list[dict]:
    """Convert a PDF file to per-page markdown dicts preserving physical page boundaries.

    Output: [{"pageNumber": 1-based int, "text": page markdown}, ...]
    Consumed by the compliance quality-check pipeline (Java `DocumentContent.pages`);
    per-page markdown/table extraction is identical to the legacy whole-document path.
    """
    import pdfplumber

    with pdfplumber.open(pdf_path) as pdf:
        total = len(pdf.pages)
    chunk_size = _pdf_chunk_pages()
    pages: list[dict] = []
    for start in range(0, total, chunk_size):
        pages.extend(_convert_pdf_chunk(pdf_path, start, min(start + chunk_size, total)))
    return pages


def convert_pdf_document(pdf_path: str) -> dict:
    """Convert a PDF file once, returning both the merged markdown and per-page slices.

    Output: {"markdown": whole-document markdown (legacy post-processing applied:
    \\x0c normalization + split price-table merge for the scoreparse consumer),
    "pages": [{"pageNumber": 1-based int, "text": page markdown}, ...]}
    The pages are raw physical-page slices WITHOUT cross-page merging — the compliance
    quality-check pipeline consumes them for physical pagination.
    """
    pages = convert_pdf_to_pages(pdf_path)
    pages_text: list[str] = [str(p["text"]) for p in pages]

    full_md = "\n\n".join(pages_text)
    # Normalize form feed \x0c characters to double newlines
    full_md = full_md.replace("\x0c", "\n\n")
    # Cross-page table continuity: merge split price tables.
    # A price scoring table split across pages appears as adjacent pipe blocks
    # (each page is extracted as an independent table). Merging keeps the price
    # scoring methodology coherent for the downstream LLM, which otherwise would
    # see only the first rows and truncate after the page break.
    return {"markdown": _merge_split_price_tables(full_md), "pages": pages}


def convert_pdf_to_markdown(pdf_path: str) -> str:
    """Convert a PDF file to markdown preserving tables and line breaks using pdfplumber."""
    return str(convert_pdf_document(pdf_path)["markdown"])


def extract_pdf_tables_artifact(pdf_path: str) -> dict:
    """Extract PDF tables into the sidecar DocumentArtifact v2 shape.

    The docx extractor already emits a canonical cell grid for Evidence Pipeline.
    PDF score tables must use the same contract so Java can run the same
    deterministic fact extractor instead of falling back to lossy markdown/LLM.
    """
    import pdfplumber

    artifact = {
        "schemaVersion": SCHEMA_VERSION,
        "artifact": _build_pdf_artifact_header(pdf_path),
        "tables": [],
        "textBlocks": [],
        "warnings": [],
    }
    try:
        with pdfplumber.open(pdf_path) as pdf:
            extracted_tables: list[dict] = []
            page_texts: list[str] = []
            ocr_pages: set[int] = set()
            table_index = 0
            for page_index, page in enumerate(pdf.pages):
                page_number = page_index + 1
                extract_text = getattr(page, "extract_text", None)
                page_text = (extract_text() or "") if callable(extract_text) else ""
                page_texts.append(page_text)
                if _is_pdf_scoring_page(page_text):
                    ocr_pages.add(page_number)
                page_width = float(getattr(page, "width", 0) or 0)
                page_height = float(getattr(page, "height", 0) or 0)
                page_area = max(page_width * page_height, 1.0)
                for image in getattr(page, "images", ()) or ():
                    image_area = max(0.0, float(image.get("x1", 0)) - float(image.get("x0", 0))) * max(
                        0.0, float(image.get("bottom", 0)) - float(image.get("top", 0))
                    )
                    if image_area / page_area >= 0.2:
                        ocr_pages.add(page_number)
                tables = page.extract_tables() or []
                for raw_table in tables:
                    table = _normalise_pdf_table(raw_table)
                    if not table:
                        continue
                    max_cols = max(len(row) for row in table)
                    if max_cols <= 0:
                        continue
                    rows = [row + [""] * (max_cols - len(row)) for row in table]
                    if _is_pdf_score_table(rows):
                        ocr_pages.add(page_number)
                    extracted_tables.append({
                        "sourcePage": page_number,
                        "sourceTableIndex": table_index,
                        "rows": rows,
                    })
                    table_index += 1
            for logical_index, table in enumerate(_merge_pdf_score_table_continuations(extracted_tables)):
                rows = table["rows"]
                max_cols = max(len(row) for row in rows)
                table_id = f"p{table['sourcePage']}-t{logical_index:04d}"
                cells = []
                for row_idx, row in enumerate(rows):
                    source_rows = table.get("sourceRows", [])
                    source_row = source_rows[row_idx] if row_idx < len(source_rows) else {}
                    for col_idx, text in enumerate(row):
                        cell_id = f"{table_id}-r{row_idx}-c{col_idx}"
                        cells.append({
                            "cellId": cell_id,
                            "row": row_idx,
                            "column": col_idx,
                            "rawText": text,
                            "normalizedText": _normalise_cell_text(text),
                            "rowspan": 1,
                            "colspan": 1,
                            "isMergedContinue": False,
                            "sourcePage": source_row.get("page", table["sourcePage"]),
                            "sourceXmlPath": (
                                f"pdf:page={source_row.get('page', table['sourcePage'])};"
                                f"table={source_row.get('table', table.get('sourceTableIndex', logical_index))};"
                                f"row={source_row.get('row', row_idx)};col={col_idx}"
                            ),
                        })
                artifact["tables"].append({
                    "tableId": table_id,
                    "sourcePage": table["sourcePage"],
                    "sourceSection": "",
                    "rows": len(rows),
                    "columns": max_cols,
                    "mergedRegions": [],
                    "cells": cells,
                    "isScoreTable": _is_pdf_score_table(rows),
                })
            text_blocks, ocr_warnings = _extract_pdf_ocr_text_blocks(
                pdf_path, sorted(ocr_pages), page_texts
            )
            artifact["textBlocks"] = text_blocks
            artifact["warnings"].extend(ocr_warnings)
    except Exception as exc:
        artifact["warnings"].append(f"pdf table extraction failed: {exc}")
    return artifact


def _is_pdf_scoring_page(text: str) -> bool:
    return bool(re.search(r"(?:评分|评审).{0,16}(?:标准|办法|细则|因素)|分值构成", text))


def _extract_pdf_ocr_text_blocks(
    pdf_path: str, requested_pages: list[int], native_page_texts: list[str]
) -> tuple[list[dict], list[str]]:
    if not requested_pages:
        return [], []
    limit = _pdf_ocr_page_limit()
    selected_pages = requested_pages[:limit]
    warnings: list[str] = []
    if len(requested_pages) > limit:
        warnings.append(f"PDF OCR 页数超限：仅处理评分相关页前 {limit} 页")
    try:
        import pymupdf

        document = pymupdf.open(pdf_path)
    except Exception:
        return [], ["PDF OCR 初始化失败，文字证据未生成"]

    blocks: list[dict] = []
    current_section = ""
    try:
        for page_number in selected_pages:
            page_text = native_page_texts[page_number - 1] if page_number <= len(native_page_texts) else ""
            try:
                pixmap = document.load_page(page_number - 1).get_pixmap(matrix=pymupdf.Matrix(2, 2), alpha=False)
                process = subprocess.run(
                    ["tesseract", "stdin", "stdout", "-l", "chi_sim+eng", "--psm", "6", "tsv"],
                    input=pixmap.tobytes("png"), capture_output=True, timeout=12, check=False,
                )
            except (OSError, subprocess.TimeoutExpired):
                warnings.append(f"PDF 第 {page_number} 页 OCR 不可用")
                continue
            if process.returncode != 0:
                warnings.append(f"PDF 第 {page_number} 页 OCR 失败")
                continue
            page_blocks = _parse_tesseract_tsv(process.stdout.decode("utf-8", errors="replace"), page_number,
                                               pixmap.width, pixmap.height)
            ocr_text = "\n".join(block["text"] for block in page_blocks)
            current_section = _pdf_section_heading(page_text) or _pdf_section_heading(ocr_text) or current_section
            native_normalized = _normalize_pdf_text(page_text)
            for block in page_blocks:
                if _normalize_pdf_text(block["text"]) in native_normalized:
                    continue
                block["sourceSection"] = current_section
                blocks.append(block)
    finally:
        document.close()
    return blocks, warnings


def _parse_tesseract_tsv(tsv: str, page_number: int, image_width: int, image_height: int) -> list[dict]:
    paragraphs: dict[tuple[str, str], dict] = {}
    for row in csv.DictReader(io.StringIO(tsv), delimiter="\t"):
        token = (row.get("text") or "").strip()
        try:
            confidence = float(row.get("conf", "-1"))
            left, top = int(row["left"]), int(row["top"])
            width, height = int(row["width"]), int(row["height"])
        except (TypeError, ValueError, KeyError):
            continue
        if not token or confidence < 0:
            continue
        key = (row.get("block_num", "0"), row.get("par_num", "0"))
        value = paragraphs.setdefault(key, {"lines": {}, "confidence": [], "left": left,
                                              "top": top, "right": left + width, "bottom": top + height})
        line = value["lines"].setdefault(row.get("line_num", "0"), [])
        line.append(token)
        value["confidence"].append(confidence)
        value["left"] = min(value["left"], left)
        value["top"] = min(value["top"], top)
        value["right"] = max(value["right"], left + width)
        value["bottom"] = max(value["bottom"], top + height)

    result = []
    for index, value in enumerate(paragraphs.values(), start=1):
        confidence = min(value["confidence"])
        text = "\n".join(" ".join(value["lines"][line]) for line in sorted(value["lines"], key=int)).strip()
        if confidence < PDF_OCR_MIN_CONFIDENCE or len(text) < 6:
            continue
        left, top, right, bottom = value["left"], value["top"], value["right"], value["bottom"]
        result.append({
            "blockId": f"p{page_number}-ocr-{index}",
            "sourcePage": page_number,
            "sourceSection": "",
            "text": text,
            "confidence": confidence,
            "bounds": {"left": left, "top": top, "width": right - left, "height": bottom - top,
                       "imageWidth": image_width, "imageHeight": image_height},
        })
    return result


def _pdf_section_heading(text: str) -> str:
    for line in text.splitlines():
        candidate = " ".join(line.split()).strip(" ：:")
        if len(candidate) > 80:
            continue
        if re.match(r"^第[一二三四五六七八九十百\d]+[章节部分].{0,60}$", candidate):
            return candidate
        if re.fullmatch(r".{0,12}(?:评审办法|评分办法|评审标准|评分标准|评审细则|评分细则|合同履约考核).{0,12}", candidate):
            return candidate
    return ""


def _normalize_pdf_text(text: str) -> str:
    return re.sub(r"\s+", "", text or "").lower()


def _pdf_ocr_page_limit() -> int:
    try:
        return max(0, min(int(os.getenv("SIDECAR_PDF_OCR_MAX_PAGES", PDF_OCR_MAX_PAGES)), 100))
    except (TypeError, ValueError):
        return PDF_OCR_MAX_PAGES


def _build_pdf_artifact_header(path: str) -> dict:
    sha256 = _file_sha256(path)
    return {
        "documentId": f"sha256:{sha256}",
        "sha256": sha256,
        "fileType": "PDF",
        "extractionVersion": PDF_TABLE_EXTRACTION_VERSION,
    }


def _file_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(64 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _normalise_pdf_table(raw_table: list | None) -> list[list[str]]:
    rows: list[list[str]] = []
    for raw_row in raw_table or []:
        row = []
        for cell in raw_row or []:
            row.append("" if cell is None else str(cell).strip())
        if any(cell for cell in row):
            rows.append(row)
    return rows


def _merge_pdf_score_table_continuations(tables: list[dict]) -> list[dict]:
    merged: list[dict] = []
    for table in tables:
        rows = table["rows"]
        enriched = {
            **table,
            "sourceRows": [
                {"page": table["sourcePage"], "table": table["sourceTableIndex"], "row": idx}
                for idx, _ in enumerate(rows)
            ],
        }
        if merged and _is_pdf_score_table_continuation(merged[-1]["rows"], rows):
            merged[-1]["rows"].extend(rows)
            merged[-1]["sourceRows"].extend(enriched["sourceRows"])
        else:
            merged.append(enriched)
    return merged


def _is_pdf_score_table_continuation(previous: list[list[str]], current: list[list[str]]) -> bool:
    if not previous or not current:
        return False
    if max(len(row) for row in previous) != max(len(row) for row in current):
        return False
    if not any(_is_genuine_header_row(r) for r in previous):
        return False
    if _has_pdf_score_header(current):
        return False
    first_cell = _normalise_cell_text(current[0][0] if current[0] else "")
    if not (re.fullmatch(r"\d+(?:[.、]\d+)?", first_cell) or "合计" in first_cell):
        return False
    return (
        _has_pdf_weighted_score_item(current)
        or _has_pdf_total_row(current)
        or _has_numeric_score_item(current)
    )


def _normalise_cell_text(text: str) -> str:
    return re.sub(r"\s+", " ", (text or "").replace("\x0c", " ")).strip()


def _is_genuine_header_row(row: list[str]) -> bool:
    if not row or len(row) < 3 or len(row) > 15:
        return False
    first = _normalise_cell_text(row[0])
    if re.fullmatch(r"\d+(?:[.、]\d+)?", first):
        return False
    if any(len(_normalise_cell_text(c)) > 40 for c in row):
        return False
    text = " ".join(_normalise_cell_text(c) for c in row)
    explicit_keywords = (
        "评分因素", "评审因素", "评分项", "评审项",
        "评分标准", "评审标准", "评分细则", "打分细则", "打分标准",
    )
    if any(keyword in text for keyword in explicit_keywords):
        return True
    return any(w in text for w in ("分值", "得分", "权重")) and any(
        c in text for c in ("序号", "条款号", "条款内容", "项目", "指标")
    )


def _is_pdf_score_table(rows: list[list[str]]) -> bool:
    if not rows:
        return False
    sample = " ".join(" ".join(row) for row in rows[:min(len(rows), 10)])
    return (
        _has_pdf_score_header(rows)
        and (
            _has_pdf_weighted_score_item(rows)
            or _has_pdf_total_row(rows)
            or _has_numeric_score_item(rows)
        )
        and not _is_pdf_preliminary_review_table(sample)
    )


def _has_pdf_score_header(rows: list[list[str]]) -> bool:
    return any(_is_genuine_header_row(r) for r in rows[:min(len(rows), 10)])


def _has_numeric_score_item(rows: list[list[str]]) -> bool:
    for row in rows:
        if not row:
            continue
        for cell in row[1:]:
            text = _normalise_cell_text(cell)
            if not text:
                continue
            if re.fullmatch(r"[~≈]?\s*\d{1,3}(?:\.\d+)?\s*(?:分|分值|%)?", text):
                m = re.search(r"\d+(?:\.\d+)?", text)
                if m:
                    try:
                        val = float(m.group(0))
                        if 0 < val <= 100:
                            return True
                    except ValueError:
                        pass
    return False


def _has_pdf_weighted_score_item(rows: list[list[str]]) -> bool:
    sample = " ".join(" ".join(row) for row in rows)
    return bool(re.search(r"[（(]\s*\d{1,3}(?:\.\d+)?\s*分\s*[）)]", sample))


def _has_pdf_total_row(rows: list[list[str]]) -> bool:
    sample = " ".join(" ".join(row) for row in rows)
    return bool(re.search(r"(?:合计|满分|总分).{0,12}[（(]?\s*\d{1,3}(?:\.\d+)?\s*分", sample))


def _is_pdf_preliminary_review_table(sample: str) -> bool:
    compact = re.sub(r"\s+", "", sample or "")
    return any(keyword in compact for keyword in ("资格评审标准", "形式评审标准", "响应性评审标准", "初步评审标准"))


def _merge_split_price_tables(markdown: str) -> str:
    """Merge adjacent pipe blocks that are continuations of the same price table.

    合并条件（保守，仅针对报价参数表）：
    1. 延续块任一行命中 PRICE_PARAM_KEYWORDS —— 不含价格参数的相邻表一律不合并；
    2. 延续块首行列数（管道数）与主块主体行列数一致 —— 防止不同结构的表被拼接；
    3. 两块间隔 ≤3 空行（页边界）。
    持续吸收延续块，支持跨 3 页以上的长表链式合并。
    """
    if not markdown or "|" not in markdown:
        return markdown
    lines = markdown.split("\n")
    out: list[str] = []
    i = 0
    while i < len(lines):
        if not lines[i].strip().startswith("|"):
            out.append(lines[i])
            i += 1
            continue
        block_start = i
        while i < len(lines) and lines[i].strip().startswith("|"):
            i += 1
        block = lines[block_start:i]
        # 持续吸收延续块：跨 3+ 页的长表在同一循环内继续合并，而非仅两块成对
        while True:
            j = i
            blank_gap = 0
            while j < len(lines) and not lines[j].strip():
                blank_gap += 1
                j += 1
            if blank_gap > 3 or j >= len(lines) or not lines[j].strip().startswith("|"):
                break
            next_start = j
            k = j
            while k < len(lines) and lines[k].strip().startswith("|"):
                k += 1
            next_block = lines[next_start:k]
            if not _is_price_table_continuation(block, next_block):
                break
            block.extend(_continuation_body(block, next_block))
            i = k
        out.extend(block)
    return "\n".join(out)


def _is_price_table_continuation(block: list[str], next_block: list[str]) -> bool:
    if len(block) < 2 or not next_block:
        return False
    first_row = next_block[0]
    if first_row.count("|") < 3:
        return False
    # 报价参数表延续信号：块内任一行命中关键词（无价格参数的相邻表不合并）
    if not any(_row_has_price_param(row) for row in next_block):
        return False
    # 列数一致性：延续块首行与主块主体行的管道数必须一致
    return first_row.count("|") == _body_pipe_count(block)


def _continuation_body(block: list[str], next_block: list[str]) -> list[str]:
    body = [row for row in next_block if not _is_separator_row(row)]
    # 跨页重复表头：与主块首行相同则丢弃，避免合并后出现重复表头
    if body and block and _normalize_cells(body[0]) == _normalize_cells(block[0]):
        body = body[1:]
    return body


def _is_separator_row(row: str) -> bool:
    cells = [c.strip() for c in row.split("|")]
    return any("-" in c for c in cells) and all(c == "" or set(c) <= {"-", ":"} for c in cells)


def _normalize_cells(row: str) -> str:
    return "|".join(c.strip() for c in row.split("|"))


def _body_pipe_count(block: list[str]) -> int:
    """主块主体行的列数签名：非分隔行管道数的众数（单表内各行被填充为等宽）。"""
    counts = Counter(row.count("|") for row in block if not _is_separator_row(row))
    if not counts:
        return -1
    return counts.most_common(1)[0][0]


def extract_headings(markdown_text: str) -> list[dict[str, object]]:
    """Parse markdown headings and return structured section metadata.

    Each entry contains:
      - heading (str): the heading text
      - level (int): 1-6
      - charStart (int): byte offset of the heading line start
      - charEnd (int): byte offset where this section ends (next heading or EOF)
      - path (list[str]): breadcrumb path from root to this heading
    """
    if not markdown_text:
        return []

    sections: list[dict[str, object]] = []
    heading_pattern = re.compile(r"^(#{1,6})\s+(.*)$")
    cn_heading_pattern = re.compile(
        r"^(第[一二三四五六七八九十百千万\d]+[章节部分篇]\s*.*|"
        r"评审办法前附表|评标办法前附表|评分标准前附表|评分细则前附表|"
        r"[一二三四五六七八九十]、\s*.*|"
        r"[（(][一二三四五六七八九十]+[)）]\s*.*|"
        r"\d+\s+(?:评审方法|初步评审标准和程序|详细评审标准和程序|推荐成交原则|技术规范书).*)$"
    )
    offset = 0
    for raw_line in markdown_text.splitlines(keepends=True):
        line = raw_line.rstrip("\r\n")
        markdown_match = heading_pattern.fullmatch(line.strip())
        if markdown_match:
            heading_text = _strip_markdown_emphasis(markdown_match.group(2).strip())
            if heading_text:
                sections.append({
                    "heading": heading_text,
                    "level": len(markdown_match.group(1)),
                    "charStart": offset,
                    "charEnd": len(markdown_text),
                })
        else:
            heading_text = _strip_markdown_emphasis(line.strip())
            if cn_heading_pattern.fullmatch(heading_text) and len(heading_text) <= 80:
                if heading_text.startswith("第"):
                    level = 1
                elif re.match(r"^[（(]", heading_text):
                    level = 3
                else:
                    level = 2
                sections.append({
                    "heading": heading_text,
                    "level": level,
                    "charStart": offset,
                    "charEnd": len(markdown_text),
                })
        offset += len(raw_line)

    # Fill charEnd using the next section's charStart
    for i in range(len(sections) - 1):
        sections[i]["charEnd"] = sections[i + 1]["charStart"]

    # Build breadcrumb path
    current_path: list[str] = []
    for section in sections:
        level = int(section["level"])
        current_path = current_path[: level - 1]
        while len(current_path) < level - 1:
            current_path.append("")
        current_path.append(str(section["heading"]))
        section["path"] = list(current_path)

    return sections


def _strip_markdown_emphasis(value: str) -> str:
    """Remove bold markers without changing source offsets used by callers."""
    return value.replace("**", "").replace("__", "").strip()
