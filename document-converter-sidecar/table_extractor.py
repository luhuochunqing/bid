"""Extract structured tables from Word documents into versioned DocumentArtifact.

Pure-function module — no ASGI/HTTP concerns.  Called by app.py /extract-tables.

Output contract (spec 059 Phase 1):
    {
      "schemaVersion": "2.0",
      "artifact": {
        "documentId": "sha256:<64hex>",
        "sha256": "<64hex>",
        "fileType": "DOCX",
        "extractionVersion": "table-v2"
      },
      "tables": [
        {
          "tableId": "t-0003",
          "sourcePage": null | <page number>,
          "sourceSection": null | <heading text>,
          "rows": <int>,
          "columns": <int>,
          "mergedRegions": [
            {"startCellId": "...", "rowspan": N, "colspan": M}
          ],
          "cells": [
            {
              "cellId": "t-0003-r2-c3",
              "row": 2, "column": 3,
              "rawText": "...", "normalizedText": "...",
              "rowspan": 1, "colspan": 1, "isMergedContinue": False,
              "sourceXmlPath": "/w:document[1]/w:body[1]/w:tbl[3]/w:tr[2]/w:tc[3]"
            }
          ],
          "isScoreTable": True | False
        }
      ],
      "warnings": [...]
    }

This module exposes both:
- ``extract_tables_artifact(path)`` — versioned v2 contract.
- ``extract_tables(path)`` — legacy v1 contract kept for backward compat
  (delegates to v2 then strips down to the old structure).
"""

from __future__ import annotations

import hashlib
import logging
import os
import re
import zipfile
from typing import Any

log = logging.getLogger(__name__)

_W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

_SCORE_TABLE_KEYWORDS = ("评分", "评审", "打分", "得分", "分值", "评分因素", "评审因素")

SCHEMA_VERSION = "2.0"
EXTRACTION_VERSION = "table-v2"


# ---------------------------------------------------------------------------
# Public entry points
# ---------------------------------------------------------------------------

from docx_repair import auto_repaired_docx, repair_truncated_docx


def extract_tables(path: str) -> dict[str, Any]:
    """Legacy v1 contract: {tables: [...], warnings: [...]}.

    Delegates to v2 and projects the artifact down to the v1 shape so existing
    callers continue to work during the Phase 5 cutover.
    """
    artifact_doc = extract_tables_artifact(path)
    legacy_tables: list[dict[str, Any]] = []
    for t in artifact_doc["tables"]:
        legacy_tables.append({
            "tableIndex": int(t["tableId"].rsplit("-", 1)[1]) if t["tableId"].startswith("t-") else 0,
            "rows": _to_legacy_rows(t["cells"], t["rows"], t["columns"]),
            "isScoreTable": t["isScoreTable"],
        })
    return {"tables": legacy_tables, "warnings": artifact_doc["warnings"]}


def extract_tables_artifact(path: str) -> dict[str, Any]:
    """Versioned v2 contract (spec 059 Phase 1).

    Returns the full DocumentArtifact with cell IDs, XML paths and
    merge region coordinates.  Pure function, no I/O outside the file
    SHA-256 calculation.
    """
    artifact: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "artifact": _build_artifact_header(path),
        "tables": [],
        "warnings": [],
    }

    try:
        from docx import Document
        from docx.table import Table
        from docx.text.paragraph import Paragraph
        from converter import extract_headings
    except ImportError:
        artifact["warnings"].append("python-docx not installed")
        return artifact

    with auto_repaired_docx(path) as target_path:
        if not zipfile.is_zipfile(target_path):
            artifact["warnings"].append("not a valid docx (not a zip)")
            return artifact

        try:
            doc = Document(target_path)
        except Exception as exc:
            log.warning("python-docx failed to open %s: %s", target_path, exc)
            raise ValueError(f"cannot parse docx: {exc}") from exc

        heading_path: list[str] = []
        table_index = 0
        for block in doc.element.body.iterchildren():
            if block.tag == f"{{{_W_NS}}}p":
                paragraph = Paragraph(block, doc)
                paragraph_headings = extract_headings(paragraph.text)
                if paragraph_headings:
                    heading = paragraph_headings[0]
                    level = int(heading["level"])
                    heading_path = heading_path[:max(0, level - 1)]
                    heading_path.append(str(heading["heading"]))
                continue
            if block.tag != f"{{{_W_NS}}}tbl":
                continue

            table = Table(block, doc)
            idx = table_index
            table_index += 1
            try:
                section = " > ".join(heading_path) or None
                tbl_data = _build_table_artifact(table, idx, section)
            except Exception as exc:
                artifact["warnings"].append(f"table {idx}: extraction failed ({exc})")
                continue
            if tbl_data is None:
                continue
            artifact["tables"].append(tbl_data)

    return artifact


# ---------------------------------------------------------------------------
# Artifact header
# ---------------------------------------------------------------------------

def _build_artifact_header(path: str) -> dict[str, Any]:
    sha256 = _file_sha256(path)
    return {
        "documentId": f"sha256:{sha256}",
        "sha256": sha256,
        "fileType": "DOCX",
        "extractionVersion": EXTRACTION_VERSION,
    }


def _file_sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(64 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


# ---------------------------------------------------------------------------
# Table artifact builder
# ---------------------------------------------------------------------------

def _build_table_artifact(table, idx: int, source_section: str | None = None) -> dict[str, Any] | None:
    """Build the v2 table artifact for a single python-docx Table.

    Returns ``None`` when the table has no rows.  Otherwise produces a dict
    with logical rows / columns, merged region list, and cells with XML paths.

    Algorithm:
      1. Compute the logical grid dimensions from <w:tblGrid>.
      2. Walk each row, mapping every <w:tc> to a logical (row, col, span)
         coordinate.  vMerge continuations reuse the previous row's column;
         gridSpan continuations share the row with their origin cell.
      3. Emit one cell entry per logical origin and one ``isMergedContinue``
         entry per covered slot, with cell IDs derived from (table, row, col).
      4. Emit mergedRegions for every origin whose rowspan>1 or colspan>1.
    """
    tbl_elem = table._tbl
    raw_tr_elems = tbl_elem.findall(f"{{{_W_NS}}}tr")
    if not raw_tr_elems:
        return None

    num_cols = _grid_width_from_tbl(tbl_elem)
    num_rows = len(raw_tr_elems)
    table_id = f"t-{idx:04d}"

    # Logical grid: grid[row][col] = cell descriptor or None.
    # Descriptor: {"kind": "ORIGIN" | "VMERGE_CONT" | "GRIDSPAN_CONT",
    #              "tc_elem": element, "tc_pos": int,
    #              "rowspan": int, "colspan": int, "origin_row": int, "origin_col": int,
    #              "text": str}
    grid: list[list[dict[str, Any] | None]] = [
        [None] * num_cols for _ in range(num_rows)
    ]

    for r_idx, tr_elem in enumerate(raw_tr_elems):
        c_idx = 0
        tc_elems = tr_elem.findall(f"{{{_W_NS}}}tc")
        for tc_pos, tc_elem in enumerate(tc_elems):
            if _is_v_merge_continue(tc_elem):
                # vMerge continuation: column = first un-filled column at
                # this row (Word semantics — the continuation aligns with the
                # restart cell's column).  The gridSpan of the restart cell
                # may exceed 1; we only need to consume ``colspan`` columns
                # worth of slots for the cursor.  Recover colspan from the
                # row above's grid[r_idx-1] at this column — if the previous
                # row's cell at the same c_idx is an ORIGIN with gridSpan,
                # the vMerge continuation covers the entire origin span.
                if r_idx == 0:
                    # Defensive: a vMerge continuation in row 0 with no
                    # restart above is malformed.  Skip and warn via a
                    # generic None placement; downstream STRUCTURE_INCOMPLETE
                    # detection will reject the table.
                    continue
                prev_col = c_idx
                # Find the previous-row ORIGIN whose c-span covers c_idx.
                above = grid[r_idx - 1][prev_col]
                while above is None and prev_col > 0:
                    prev_col -= 1
                    above = grid[r_idx - 1][prev_col]
                if above is None:
                    continue  # orphan continuation
                colspan_origin = above["colspan"]
                rowspan_origin = above["rowspan"]
                # Continuation only valid if the origin truly spans here.
                if rowspan_origin <= 1:
                    continue
                # The continuation occupies exactly the origin's first column
                # (Word stores one continuation tc per origin column in the
                # restart's gridSpan).  But for gridSpan>1 there can be
                # multiple continuation tcs; here we model a single
                # continuation tc as covering only col==origin_col of the
                # origin.  Other columns are filled in by GRIDSPAN_CONT below.
                grid[r_idx][above["origin_col"]] = {
                    "kind": "VMERGE_CONT",
                    "tc_elem": tc_elem,
                    "tc_pos": tc_pos,
                    "rowspan": 1,
                    "colspan": 1,
                    "origin_row": above["origin_row"],
                    "origin_col": above["origin_col"],
                    "text": "",
                }
                # For gridSpan>1 origins, also need horizontal coverage in
                # later columns.  Those are emitted as GRIDSPAN_CONT entries
                # by the row-above origin below; but the row-above origin
                # only emits them in its own row.  We need an explicit
                # pass below to copy those continuations downward for any
                # rows the origin spans.
                c_idx += colspan_origin
                continue

            colspan = _grid_span_from_tc(tc_elem)
            text = _tc_text(tc_elem)

            # Skip columns already filled by a previous iteration (defensive).
            while c_idx < num_cols and grid[r_idx][c_idx] is not None:
                c_idx += 1
            if c_idx >= num_cols:
                break

            # Rowspan needs the restart's exact logical column — resolve the
            # cursor first, then compute the span with explicit grid
            # arithmetic (spec 059: 禁止"发现任一 continuation"近似推断).
            rowspan = _v_merge_span_from_tc(tc_elem, c_idx, colspan)

            origin = {
                "kind": "ORIGIN",
                "tc_elem": tc_elem,
                "tc_pos": tc_pos,
                "rowspan": rowspan,
                "colspan": colspan,
                "origin_row": r_idx,
                "origin_col": c_idx,
                "text": text,
            }
            grid[r_idx][c_idx] = origin
            # Fill origin's rowspan × colspan footprint with this origin /
            # its continuations.
            for dr in range(rowspan):
                for dc in range(colspan):
                    rr, cc = r_idx + dr, c_idx + dc
                    if rr >= num_rows or cc >= num_cols:
                        continue
                    if dr == 0 and dc == 0:
                        # Already set above
                        continue
                    if grid[rr][cc] is None:
                        grid[rr][cc] = {
                            "kind": "GRIDSPAN_CONT" if dr == 0 else "VMERGE_CONT",
                            "tc_elem": tc_elem,
                            "tc_pos": tc_pos,
                            "rowspan": rowspan,
                            "colspan": colspan,
                            "origin_row": r_idx,
                            "origin_col": c_idx,
                            "text": "",
                        }
            c_idx += colspan

    # PASS 2: emit cells + merged regions from the logical grid.
    cells: list[dict[str, Any]] = []
    merged_regions: list[dict[str, Any]] = []

    for r_idx in range(num_rows):
        for c_idx in range(num_cols):
            cell = grid[r_idx][c_idx]
            if cell is None:
                # Defensive: an unfilled slot means the table has structure
                # holes (e.g. malformed XML).  Emit a placeholder so the
                # contract stays consistent and downstream validation can
                # flag STRUCTURE_INCOMPLETE.
                cells.append({
                    "cellId": f"{table_id}-r{r_idx}-c{c_idx}",
                    "row": r_idx,
                    "column": c_idx,
                    "rawText": "",
                    "normalizedText": "",
                    "rowspan": 1,
                    "colspan": 1,
                    "isMergedContinue": True,
                    "sourceXmlPath": _build_xml_path(
                        tbl_elem,
                        raw_tr_elems[r_idx],
                        raw_tr_elems[r_idx].findall(f"{{{_W_NS}}}tc")[min(c_idx, len(raw_tr_elems[r_idx].findall(f"{{{_W_NS}}}tc")) - 1)]
                        if raw_tr_elems[r_idx].findall(f"{{{_W_NS}}}tc") else tbl_elem,
                        r_idx,
                        min(c_idx, max(0, len(raw_tr_elems[r_idx].findall(f"{{{_W_NS}}}tc")) - 1)),
                    ),
                })
                continue

            cell_id = f"{table_id}-r{r_idx}-c{c_idx}"
            xml_path = _build_xml_path(tbl_elem, raw_tr_elems[r_idx], cell["tc_elem"], r_idx, cell["tc_pos"])
            is_continuation = cell["kind"] != "ORIGIN"
            cells.append({
                "cellId": cell_id,
                "row": r_idx,
                "column": c_idx,
                "rawText": cell["text"],
                "normalizedText": _normalize_text(cell["text"]),
                "rowspan": cell["rowspan"],
                "colspan": cell["colspan"],
                "isMergedContinue": is_continuation,
                "sourceXmlPath": xml_path,
            })

            if cell["kind"] == "ORIGIN" and (cell["rowspan"] > 1 or cell["colspan"] > 1):
                merged_regions.append({
                    "startCellId": cell_id,
                    "rowspan": cell["rowspan"],
                    "colspan": cell["colspan"],
                })

    return {
        "tableId": table_id,
        "sourcePage": None,
        "sourceSection": source_section,
        "rows": num_rows,
        "columns": num_cols,
        "mergedRegions": merged_regions,
        "cells": cells,
        "isScoreTable": _looks_like_score_table_v2(cells),
    }


def _normalize_text(text: str) -> str:
    """Collapse internal whitespace, strip surrounding whitespace."""
    if not text:
        return ""
    return re.sub(r"\s+", " ", text).strip()


# ---------------------------------------------------------------------------
# XML path construction
# ---------------------------------------------------------------------------

def _build_xml_path(tbl_elem, tr_elem, tc_elem, row_index: int, tc_position: int) -> str:
    """Build an XPath-like locator: /w:document[1]/w:body[1]/w:tbl[N]/w:tr[R]/w:tc[C]."""
    tbl_pos = _sibling_index(tbl_elem, f"{{{_W_NS}}}tbl") + 1
    return f"/w:document[1]/w:body[1]/w:tbl[{tbl_pos}]/w:tr[{row_index + 1}]/w:tc[{tc_position + 1}]"


def _sibling_index(elem, tag: str) -> int:
    """Return 0-based index of ``elem`` among siblings matching ``tag``."""
    idx = 0
    for child in elem.getparent().iterchildren(tag):
        if child is elem:
            return idx
        idx += 1
    return idx


# ---------------------------------------------------------------------------
# Legacy rows helper (kept so v1 callers still work)
# ---------------------------------------------------------------------------

def _to_legacy_rows(cells: list[dict[str, Any]], rows: int, cols: int) -> list[list[dict[str, Any]]]:
    """Project the v2 cell list back to the v1 nested-row shape."""
    grid: list[list[dict[str, Any] | None]] = [[None] * cols for _ in range(rows)]
    for cell in cells:
        r, c = cell["row"], cell["column"]
        if grid[r][c] is None:
            grid[r][c] = {
                "text": cell["rawText"],
                "rowspan": cell["rowspan"],
                "colspan": cell["colspan"],
                "isMergedContinue": cell["isMergedContinue"],
            }
    out: list[list[dict[str, Any]]] = []
    for row in grid:
        out.append([
            cell or {"text": "", "rowspan": 1, "colspan": 1, "isMergedContinue": True}
            for cell in row
        ])
    return out


# ---------------------------------------------------------------------------
# Cell grid helpers
# ---------------------------------------------------------------------------

def _grid_width_from_tbl(tbl_elem) -> int:
    tbl_grid = tbl_elem.find(f"{{{_W_NS}}}tblGrid")
    if tbl_grid is not None:
        cols = tbl_grid.findall(f"{{{_W_NS}}}gridCol")
        if cols:
            return len(cols)
    return max(
        (len(tr.findall(f"{{{_W_NS}}}tc")) for tr in tbl_elem.findall(f"{{{_W_NS}}}tr")),
        default=0,
    )


def _grid_span_from_tc(tc_elem) -> int:
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
    tc_pr = tc_elem.find(f"{{{_W_NS}}}tcPr")
    if tc_pr is None:
        return False
    vm = tc_pr.find(f"{{{_W_NS}}}vMerge")
    if vm is None:
        return False
    val = vm.get(f"{{{_W_NS}}}val") or vm.get("val")
    return val != "restart"


def _v_merge_val(tc_elem) -> str | None:
    """vMerge 状态：``None``=未合并，``"restart"``=重起，``"continue"``=延续。

    Word 允许 ``<w:vMerge/>`` 不带 val 属性，缺省即延续（``_is_v_merge_continue``
    同样语义）。
    """
    tc_pr = tc_elem.find(f"{{{_W_NS}}}tcPr")
    if tc_pr is None:
        return None
    vm = tc_pr.find(f"{{{_W_NS}}}vMerge")
    if vm is None:
        return None
    val = vm.get(f"{{{_W_NS}}}val") or vm.get("val")
    return val if val else "continue"


def _v_merge_span_from_tc(tc_elem, logical_col: int, colspan: int) -> int:
    """Vertical span of a vMerge restart cell, via explicit XML grid arithmetic.

    spec 059 Plan：合并覆盖范围必须用 vMerge/gridSpan 显式列坐标计算，禁止
    "该行存在任一 vMerge continuation"式的近似推断——多列独立错位合并时，
    近似推断会把其他列的 continuation 误记到当前列的跨度上（审核 P0 缺陷）。

    Algorithm: for every physical row below the restart, accumulate each
    <w:tc>'s gridSpan to locate the cell that covers ``logical_col``; the
    span only grows when THAT cell is a vMerge continuation. A new
    ``restart`` or an unmerged cell at that column ends the span.
    """
    if _v_merge_val(tc_elem) != "restart":
        return 1

    span = 1
    sibling = tc_elem.getparent().getnext()
    while sibling is not None:
        if sibling.tag != f"{{{_W_NS}}}tr":
            sibling = sibling.getnext()
            continue
        col_cursor = 0
        found_continue = False
        for child_tc in sibling.findall(f"{{{_W_NS}}}tc"):
            child_start = col_cursor
            col_cursor += _grid_span_from_tc(child_tc)
            if child_start >= logical_col + colspan:
                break  # walked past the restart's column range
            if col_cursor <= logical_col:
                continue  # cell ends before the restart column starts
            # This tc covers the restart's logical column.
            child_val = _v_merge_val(child_tc)
            if child_val == "restart":
                # A new restart in the middle of our span means our span
                # ends here (e.g. a sub-header that overlaps).
                return span
            if child_val is None:
                # The column is no longer merged in this row — span ends.
                return span
            found_continue = True
            break
        if not found_continue:
            return span
        span += 1
        sibling = sibling.getnext()
    return span


def _tc_text(tc_elem) -> str:
    parts: list[str] = []
    for t_elem in tc_elem.iter(f"{{{_W_NS}}}t"):
        if t_elem.text:
            parts.append(t_elem.text)
    return "".join(parts).strip()


# ---------------------------------------------------------------------------
# Score-table heuristic (v2 — uses normalized text)
# ---------------------------------------------------------------------------

def _looks_like_score_table_v2(cells: list[dict[str, Any]]) -> bool:
    sample_texts: list[str] = []
    for cell in cells[:30]:  # first ~30 cells
        if cell.get("isMergedContinue"):
            continue
        sample_texts.append(cell.get("normalizedText") or "")
    blob = " ".join(sample_texts)
    return any(kw in blob for kw in _SCORE_TABLE_KEYWORDS)
