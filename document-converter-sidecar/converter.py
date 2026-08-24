"""Pure conversion helpers: heading extraction and structural analysis.

Input:  Raw markdown text as a Python string.
Output: Structured list of heading dictionaries with level, path, and char offsets.
Position: Pure-function module; imported by app.py (the HTTP shell). No I/O performed here.
"""
from __future__ import annotations

import re


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
    return full_md.replace("\x0c", "\n\n")


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
