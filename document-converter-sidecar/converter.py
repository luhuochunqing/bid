"""Conversion helpers: heading extraction, PDF->markdown/pages, price-table merging.

Input:  Raw markdown text (heading extraction) or a PDF file path (pdfplumber conversion).
Output: Heading dictionaries with level/path/char offsets; PDF whole-document markdown and
        per-page slices [{"pageNumber", "text"}] (convert_pdf_document) consumed by the Java
        compliance pipeline — field contract guarded by scripts/check-pages-contract.mjs.
Position: Imported by app.py (the HTTP shell). Heading analysis is pure; the PDF conversion
        functions perform file I/O via pdfplumber and run inside the worker process.
"""
from __future__ import annotations

import re
from collections import Counter

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


def convert_pdf_to_pages(pdf_path: str) -> list[dict]:
    """Convert a PDF file to per-page markdown dicts preserving physical page boundaries.

    Output: [{"pageNumber": 1-based int, "text": page markdown}, ...]
    Consumed by the compliance quality-check pipeline (Java `DocumentContent.pages`);
    per-page markdown/table extraction is identical to the legacy whole-document path.
    """
    import pdfplumber

    pages: list[dict] = []
    with pdfplumber.open(pdf_path) as pdf:
        for index, page in enumerate(pdf.pages):
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
                pages.append({
                    "pageNumber": index + 1,
                    "text": page_combined.strip().replace("\x0c", "\n\n"),
                })
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
    heading_pattern = re.compile(r"^(#{1,6})\s+(.*)$", re.MULTILINE)
    md_matches = list(heading_pattern.finditer(markdown_text))

    if md_matches:
        for match in md_matches:
            level = len(match.group(1))
            heading_text = match.group(2).strip()
            offset = match.start()
            sections.append(
                {
                    "heading": heading_text,
                    "level": level,
                    "charStart": offset,
                    "charEnd": len(markdown_text),  # filled below
                }
            )
    else:
        # Fallback to Chinese chapter and section patterns for raw extracted text
        cn_heading_pattern = re.compile(
            r"^(第[一二三四五六七八九十百千万\d]+[章节部分篇]\s*.*|"
            r"评审办法前附表|评标办法前附表|评分标准前附表|评分细则前附表|"
            r"[一二三四五六七八九十]、\s*.*|"
            r"\d+\s+(?:评审方法|初步评审标准和程序|详细评审标准和程序|推荐成交原则|技术规范书).*)$",
            re.MULTILINE,
        )
        for match in cn_heading_pattern.finditer(markdown_text):
            heading_text = match.group(1).strip()
            if len(heading_text) <= 50:
                offset = match.start()
                level = 1 if heading_text.startswith("第") else 2
                sections.append(
                    {
                        "heading": heading_text,
                        "level": level,
                        "charStart": offset,
                        "charEnd": len(markdown_text),
                    }
                )

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
