"""Extract structured tables from Word documents (preserving merged-cell info).

Pure-function module — no ASGI/HTTP concerns.  Called by app.py /extract-tables.
"""

from __future__ import annotations

import logging
import zipfile
from typing import Any

log = logging.getLogger(__name__)

_W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

_SCORE_TABLE_KEYWORDS = ("评分", "评审", "打分", "得分", "分值", "评分因素", "评审因素")


def extract_tables(path: str) -> dict[str, Any]:
    """Return structured table data from a .docx file.

    Each table is a list of rows; each row is a list of cell dicts with:
      - text: stripped cell text
      - rowspan: number of rows this cell spans (>=1)
      - colspan: number of columns this cell spans (>=1)
      - is_merged_continue: True when this position is covered by a neighbour's
        merge (vMerge continue or gridSpan extension) — downstream should skip.
    """
    if not zipfile.is_zipfile(path):
        return {"tables": [], "warnings": ["not a valid docx (not a zip)"]}

    try:
        from docx import Document
    except ImportError:
        return {"tables": [], "warnings": ["python-docx not installed"]}

    warnings: list[str] = []
    try:
        doc = Document(path)
    except Exception as exc:
        log.warning("python-docx failed to open %s: %s", path, exc)
        raise ValueError(f"cannot parse docx: {exc}") from exc

    tables: list[dict[str, Any]] = []
    for idx, table in enumerate(doc.tables):
        try:
            rows_data = _extract_table_rows(table)
        except Exception as exc:
            warnings.append(f"table {idx}: extraction failed ({exc})")
            continue
        if not rows_data:
            continue
        tables.append({
            "tableIndex": idx,
            "rows": rows_data,
            "isScoreTable": _looks_like_score_table(rows_data),
        })

    return {"tables": tables, "warnings": warnings}


# ---------------------------------------------------------------------------
# Row / cell extraction with merge-cell detection
# ---------------------------------------------------------------------------

def _extract_table_rows(table) -> list[list[dict[str, Any]]]:
    """Build a logical grid from a python-docx Table, expanding merges.

    Works directly with raw XML <w:tc> elements because python-docx's
    row.cells property collapses vMerge-continuation cells to the same
    object as the merge-start cell.
    """
    tbl_elem = table._tbl
    raw_tr_elems = tbl_elem.findall(f"{{{_W_NS}}}tr")
    if not raw_tr_elems:
        return []

    num_cols = _grid_width_from_tbl(tbl_elem)
    num_rows = len(raw_tr_elems)
    # occupied[r][c] = True when a prior merge already covers this slot
    occupied: list[list[bool]] = [[False] * num_cols for _ in range(num_rows)]
    # Initialize grid with None placeholders
    grid: list[list[dict[str, Any] | None]] = [
        [None] * num_cols for _ in range(num_rows)
    ]

    for r_idx, tr_elem in enumerate(raw_tr_elems):
        tc_elems = tr_elem.findall(f"{{{_W_NS}}}tc")
        c_idx = 0
        for tc_elem in tc_elems:
            # vMerge-continuation cells don't occupy a new column slot —
            # their position is already filled by the merge-start cell's rowspan.
            if _is_v_merge_continue(tc_elem):
                continue

            while c_idx < num_cols and occupied[r_idx][c_idx]:
                c_idx += 1
            if c_idx >= num_cols:
                break

            colspan = _grid_span_from_tc(tc_elem)
            rowspan = _v_merge_span_from_tc(tc_elem)

            for dr in range(rowspan):
                for dc in range(colspan):
                    rr, cc = r_idx + dr, c_idx + dc
                    if rr < num_rows and cc < num_cols:
                        occupied[rr][cc] = True

            text = _tc_text(tc_elem)
            grid[r_idx][c_idx] = {
                "text": text,
                "rowspan": rowspan,
                "colspan": colspan,
                "isMergedContinue": False,
            }
            for dc in range(1, colspan):
                if c_idx + dc < num_cols:
                    grid[r_idx][c_idx + dc] = {
                        "text": "",
                        "rowspan": 1,
                        "colspan": 1,
                        "isMergedContinue": True,
                    }
            c_idx += colspan

    # Fill any remaining None slots with merged-continue placeholders
    for r_idx in range(num_rows):
        for c_idx in range(num_cols):
            if grid[r_idx][c_idx] is None:
                grid[r_idx][c_idx] = {
                    "text": "",
                    "rowspan": 1,
                    "colspan": 1,
                    "isMergedContinue": True,
                }

    return [row for row in grid]


def _grid_width_from_tbl(tbl_elem) -> int:
    """Column count from <w:tblGrid>/<w:gridCol>, with fallback."""
    tbl_grid = tbl_elem.find(f"{{{_W_NS}}}tblGrid")
    if tbl_grid is not None:
        cols = tbl_grid.findall(f"{{{_W_NS}}}gridCol")
        if cols:
            return len(cols)
    # fallback: max <w:tc> count in any <w:tr>
    return max(
        (len(tr.findall(f"{{{_W_NS}}}tc")) for tr in tbl_elem.findall(f"{{{_W_NS}}}tr")),
        default=0,
    )


def _grid_span_from_tc(tc_elem) -> int:
    """Horizontal span from <w:gridSpan w:val="N"/> on a <w:tc>."""
    tc_pr = tc_elem.find(f"{{{_W_NS}}}tcPr")
    if tc_pr is None:
        return 1
    gs = tc_pr.find(f"{{{_W_NS}}}gridSpan")
    if gs is None:
        return 1
    try:
        val = gs.get(f"{{{_W_NS}}}val") or gs.get("val")
        return max(int(val), 1)
    except (TypeError, ValueError):
        return 1


def _is_v_merge_continue(tc_elem) -> bool:
    """True if this <w:tc> is a vMerge continuation (not a merge start)."""
    tc_pr = tc_elem.find(f"{{{_W_NS}}}tcPr")
    if tc_pr is None:
        return False
    vm = tc_pr.find(f"{{{_W_NS}}}vMerge")
    if vm is None:
        return False
    val = vm.get(f"{{{_W_NS}}}val") or vm.get("val")
    return val != "restart"


def _v_merge_span_from_tc(tc_elem) -> int:
    """Vertical span: if this <w:tc> has vMerge restart, count continuations below."""
    tc_pr = tc_elem.find(f"{{{_W_NS}}}tcPr")
    if tc_pr is None:
        return 1
    vm = tc_pr.find(f"{{{_W_NS}}}vMerge")
    if vm is None:
        return 1
    val = vm.get(f"{{{_W_NS}}}val") or vm.get("val")
    if val != "restart":
        return 1

    # Walk sibling <w:tr> elements after this one
    span = 1
    sibling = tc_elem.getparent().getnext()
    while sibling is not None:
        if sibling.tag != f"{{{_W_NS}}}tr":
            sibling = sibling.getnext()
            continue
        # Check if any tc in this row has vMerge continue
        found_continue = False
        for child_tc in sibling.findall(f"{{{_W_NS}}}tc"):
            child_tc_pr = child_tc.find(f"{{{_W_NS}}}tcPr")
            if child_tc_pr is None:
                continue
            child_vm = child_tc_pr.find(f"{{{_W_NS}}}vMerge")
            if child_vm is None:
                continue
            child_val = child_vm.get(f"{{{_W_NS}}}val") or child_vm.get("val")
            if child_val == "restart":
                break
            # continue or absent val → continuation
            found_continue = True
            break
        if not found_continue:
            break
        span += 1
        sibling = sibling.getnext()
    return span


def _tc_text(tc_elem) -> str:
    """Extract all text from a <w:tc> element."""
    parts: list[str] = []
    for t_elem in tc_elem.iter(f"{{{_W_NS}}}t"):
        if t_elem.text:
            parts.append(t_elem.text)
    return "".join(parts).strip()


# ---------------------------------------------------------------------------
# Score-table heuristic
# ---------------------------------------------------------------------------

def _looks_like_score_table(rows: list[list[dict[str, Any]]]) -> bool:
    """Heuristic: does this table look like a scoring rubric?"""
    sample_texts: list[str] = []
    for row in rows[:5]:
        for cell in row:
            if not cell.get("isMergedContinue"):
                sample_texts.append(cell["text"])
    blob = " ".join(sample_texts)
    return any(kw in blob for kw in _SCORE_TABLE_KEYWORDS)
