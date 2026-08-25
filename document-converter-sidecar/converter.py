"""Pure conversion helpers: heading extraction and structural analysis.

Input:  Raw markdown text as a Python string.
Output: Structured list of heading dictionaries with level, path, and char offsets.
Position: Pure-function module; imported by app.py (the HTTP shell). No I/O performed here.
"""
from __future__ import annotations

import re

# 报价评分参数关键词（单一真相源，需与 Java 侧 PriceScoringKeywords.PRICE_PARAMS 保持同步）
PRICE_PARAM_KEYWORDS = ("下浮率", "BP%", "HP%", "LP%", "基准价", "平均价", "参数描述", "L1", "L2", "RH", "RL", "折扣率", "单边法")


def convert_pdf_to_markdown(pdf_path: str) -> str:
    """Convert a PDF file to markdown preserving tables and line breaks using pdfplumber."""
    import pdfplumber

    pages_text: list[str] = []
    with pdfplumber.open(pdf_path) as pdf:
        for page in pdf.pages:
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
                pages_text.append(page_combined.strip())

    full_md = "\n\n".join(pages_text)
    # Normalize form feed \x0c characters to double newlines
    full_md = full_md.replace("\x0c", "\n\n")
    # Cross-page table continuity: merge split price tables.
    # A logical scoring table split across pages appears as two adjacent pipe blocks
    # where the second block's header row has empty leading cells (rowspan continuation)
    # and contains price-parameter keywords (下浮率/BP%/HP%/基准价 etc.). Merging keeps
    # the报价评分 methodology coherent for the downstream LLM, which otherwise would
    # see only the first 3 rows and truncate after the page break.
    return _merge_split_price_tables(full_md)


def _merge_split_price_tables(markdown: str) -> str:
    """Merge adjacent split pipe tables that are continuations of the same logical table."""
    if not markdown or "|" not in markdown:
        return markdown
    lines = markdown.split("\n")
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        # Detect start of a pipe table block
        if line.strip().startswith("|"):
            block_start = i
            while i < len(lines) and lines[i].strip().startswith("|"):
                i += 1
            block_end = i  # exclusive
            block = lines[block_start:block_end]
            # Check if next block (after 1-3 blank lines) looks like continuation
            j = i
            blank_gap = 0
            while j < len(lines) and not lines[j].strip():
                blank_gap += 1
                j += 1
            if blank_gap <= 3 and j < len(lines) and lines[j].strip().startswith("|"):
                next_block_start = j
                k = j
                while k < len(lines) and lines[k].strip().startswith("|"):
                    k += 1
                next_block = lines[next_block_start:k]
                # Continuation heuristic: price param tables split across pages
                if len(block) >= 2 and len(next_block) >= 2:
                    next_header_cells = [c.strip() for c in next_block[0].split("|")]
                    first_row = next_block[0]
                    is_continuation = (
                        first_row.strip().startswith("|") and first_row.count("|") >= 3
                        and (
                            any(kw in first_row for kw in PRICE_PARAM_KEYWORDS)
                            or (
                                not any(kw in first_row for kw in ("评分", "评标", "分值", "满分", "得分"))
                                and not next_header_cells[1].strip()
                            )
                        )
                    )
                    has_price_param = any(
                        any(kw in row for kw in PRICE_PARAM_KEYWORDS) for row in next_block
                    )
                    if is_continuation or (has_price_param and not any("评分" in r or "分值" in r for r in next_block[:2])):
                        if len(next_block) > 2 and "---" in next_block[1]:
                            next_body = [next_block[0]] + next_block[2:]
                        else:
                            next_body = next_block[1:] if len(next_block) > 1 and "---" in next_block[0] else next_block
                        block.extend(next_body)
                        # Skip the merged next block
                        i = k
                        out.extend(block)
                        # Preserve single blank line between merged table and next content
                        if i < len(lines) and blank_gap:
                            out.append("")
                        continue
            out.extend(block)
        else:
            out.append(line)
            i += 1
    return "\n".join(out)


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
