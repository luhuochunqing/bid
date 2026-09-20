"""PDF large-file memory guard for the 1373 OOM root-cause fix.

Input:  synthetic ruled-table PDF (in-process via pymupdf, no committed fixture).
Output: Assertions that (a) converting a many-page PDF stays within a strict
        tracemalloc peak budget (chunked open/close, not whole-document load),
        and (b) chunked extraction is byte-identical to whole-document output.
Position: test suite; run with `pytest tests/test_pdf_memory_guard.py -v`
        from the sidecar directory.
"""
from __future__ import annotations

import os
import sys
import tempfile

import pytest

N_PAGES = 100
# spec 066 D4: the production default chunk is now 100 pages (reopen cost
# dominated 90% of a 1133s conversion). This bench pins a small chunk on
# purpose: its job is the O(chunk) residency guard, not the default value
# (pinned by test_pdf_chunk_default_is_100_and_env_overridable). At 100 pages
# with the default, a single open would make chunked == whole-document.
# Budget rationale (measured on this fixture): legacy single-open peaked at
# 189MB and scaled with document size (production 428MB PDF hit ~2.98GB RSS
# and OOM-killed the 3GB worker); chunked open/close (10 pages) peaks at
# ~74MB here and stays O(chunk) regardless of page count. Measured with
# 100-page chunks: 182MB @100 pages, 238MB @400 pages vs 734MB whole-doc —
# still bounded, but heavier than this bench needs. 100MB trips on any
# residency regression while leaving headroom for heavier production pages.
PDF_PEAK_BUDGET_BYTES = 100 * 1024 * 1024
BENCH_CHUNK_PAGES = "10"
# Default-chunk profile budget (measured 182MB @100 pages on this fixture; spec 066 D4).
# Guards the production default's residency; the O(chunk) mechanism guard above
# stays pinned to a small chunk so it keeps exercising multiple open/close cycles.
DEFAULT_CHUNK_PEAK_BUDGET_BYTES = 300 * 1024 * 1024


@pytest.fixture()
def converter_module():
    """Freshly imported converter module (picks up env overrides per test)."""
    for mod_name in list(sys.modules.keys()):
        if mod_name == "converter":
            del sys.modules[mod_name]
    import converter as fresh_converter  # noqa: PLC0415

    return fresh_converter


def _synth_large_pdf(path: str, n_pages: int = N_PAGES) -> None:
    """Build a multi-page PDF with body text plus a ruled 6x12 table per page.

    Ruled lines force pdfplumber's line-strategy table detection to do real
    work per page (the production OOM vector: whole-document parsed
    structures resident at once for a 428MB bid PDF).
    """
    pymupdf = pytest.importorskip("pymupdf")
    doc = pymupdf.open()
    for i in range(n_pages):
        page = doc.new_page()
        page.insert_text((72, 72), f"BID PAGE {i + 1} SUPPLIER DOC", fontsize=12)
        y = 110
        for j in range(10):
            page.insert_text(
                (72, y),
                f"Body paragraph {j + 1} on page {i + 1}: supply and delivery terms.",
                fontsize=10,
            )
            y += 15
        x0, y0, cw, rh = 72, 300, 70, 18
        for r in range(13):
            page.draw_line((x0, y0 + r * rh), (x0 + 6 * cw, y0 + r * rh))
        for c in range(7):
            page.draw_line((x0 + c * cw, y0), (x0 + c * cw, y0 + 12 * rh))
        for r in range(12):
            for c in range(6):
                page.insert_text((x0 + c * cw + 3, y0 + r * rh + 13), f"R{r}C{c}", fontsize=9)
    doc.save(path)
    doc.close()


def test_pdf_large_file_memory_bench(converter_module, monkeypatch):
    """Memory guard: 100-page ruled-table PDF must convert within budget.

    Regression guard for project 1373 (428MB bid PDF OOM-killed the 3GB
    sidecar worker at ~2.98GB RSS): convert_pdf_document held every page's
    parsed structures at once. Chunked open/close must keep the peak near
    O(chunk) instead of O(document). Chunk pinned small (see module notes)
    so this bench keeps exercising multiple open/close cycles.
    """
    import tracemalloc

    monkeypatch.setenv("SIDECAR_PDF_CHUNK_PAGES", BENCH_CHUNK_PAGES)

    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        path = tmp.name
    try:
        _synth_large_pdf(path)
        tracemalloc.start()
        result = converter_module.convert_pdf_document(path)
        _, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        assert "BID PAGE 1" in result["markdown"]
        assert f"BID PAGE {N_PAGES}" in result["markdown"]
        assert len(result["pages"]) == N_PAGES
        assert peak < PDF_PEAK_BUDGET_BYTES, (
            f"Peak memory {peak / (1024 * 1024):.2f}MB exceeded "
            f"{PDF_PEAK_BUDGET_BYTES / (1024 * 1024):.0f}MB budget"
        )

        # Production default (100 pages): residency must stay bounded and output identical.
        monkeypatch.delenv("SIDECAR_PDF_CHUNK_PAGES", raising=False)
        tracemalloc.start()
        default_result = converter_module.convert_pdf_document(path)
        _, default_peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        assert default_result["pages"] == result["pages"]
        assert default_peak < DEFAULT_CHUNK_PEAK_BUDGET_BYTES, (
            f"Default-chunk peak {default_peak / (1024 * 1024):.2f}MB exceeded "
            f"{DEFAULT_CHUNK_PEAK_BUDGET_BYTES / (1024 * 1024):.0f}MB budget"
        )
    finally:
        if os.path.exists(path):
            os.remove(path)


def test_pdf_chunked_output_matches_whole(converter_module, monkeypatch):
    """Chunked extraction must be identical to whole-document extraction.

    Contract guard: the memory fix may only change residency, never output.
    Page numbers stay 1-based continuous; markdown stays byte-identical.
    """
    with tempfile.NamedTemporaryFile(suffix=".pdf", delete=False) as tmp:
        path = tmp.name
    try:
        _synth_large_pdf(path, n_pages=10)
        monkeypatch.setenv("SIDECAR_PDF_CHUNK_PAGES", "1000")
        whole = converter_module.convert_pdf_document(path)
        monkeypatch.setenv("SIDECAR_PDF_CHUNK_PAGES", "3")
        chunked = converter_module.convert_pdf_document(path)

        assert chunked == whole
        assert [p["pageNumber"] for p in chunked["pages"]] == list(range(1, 11))
    finally:
        if os.path.exists(path):
            os.remove(path)
