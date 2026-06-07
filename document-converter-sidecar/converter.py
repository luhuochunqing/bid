"""Pure conversion helpers: heading extraction and structural analysis.

Input:  Raw markdown text as a Python string.
Output: Structured list of heading dictionaries with level, path, and char offsets.
Position: Pure-function module; imported by app.py (the HTTP shell). No I/O performed here.
"""
from __future__ import annotations

import re


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

    for match in heading_pattern.finditer(markdown_text):
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
