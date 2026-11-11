"""Real synthetic PDF regression for scoring OCR, never a customer fixture."""
import io
import shutil
import subprocess

import pymupdf
import pytest
from PIL import Image, ImageDraw, ImageFont

import converter


def make_pdf(path, scanned_pages=1, native=False):
    image = Image.new('RGB', (1400, 500), 'white')
    font = ImageFont.load_default(size=55)
    ImageDraw.Draw(image).text((70, 150), 'ANNUAL SALES 3500000000', fill='black', font=font)
    data = io.BytesIO()
    image.save(data, format='PNG')
    with pymupdf.open() as pdf:
        if native:
            page = pdf.new_page(width=700, height=250)
            page.insert_text((30, 80), 'NATIVE CONTRACT TERMS', fontsize=18)
        for _ in range(scanned_pages):
            page = pdf.new_page(width=700, height=250)
            page.insert_image(page.rect, stream=data.getvalue())
        pdf.save(path)


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_real_scanned_pdf_has_text_and_physical_pages(tmp_path):
    path = tmp_path / 'scanned.pdf'
    make_pdf(path, scanned_pages=2)
    assert converter.convert_pdf_document(str(path))['markdown'] == ''
    result = converter.convert_scoring_pdf_document(str(path))
    assert [p['pageNumber'] for p in result['pages']] == [1, 2]
    assert result['markdown'].count('ANNUAL SALES') == 2
    assert '3500000000' in result['markdown']
    assert '[第 2 页]' in result['markdown']


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_mixed_pdf_keeps_native_text(tmp_path):
    path = tmp_path / 'mixed.pdf'
    make_pdf(path, native=True)
    result = converter.convert_scoring_pdf_document(str(path))
    assert result['markdown'].count('NATIVE CONTRACT TERMS') == 1
    assert 'ANNUAL SALES' in result['markdown']
    assert [p['pageNumber'] for p in result['pages']] == [1, 2]


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_mixed_pdf_keeps_sparse_scanned_contract_number(tmp_path):
    path = tmp_path / 'sparse-contract.pdf'
    with pymupdf.open() as scan:
        page = scan.new_page(width=595, height=842)
        page.insert_text((72, 72), 'CONTRACT NO 20261111', fontsize=12)
        raster = page.get_pixmap(matrix=pymupdf.Matrix(2, 2), alpha=False)
        image = raster.tobytes('png')
    with pymupdf.open() as pdf:
        page = pdf.new_page(width=595, height=842)
        page.insert_text((72, 72), 'NATIVE CONTRACT TERMS', fontsize=12)
        page = pdf.new_page(width=595, height=842)
        page.insert_image(page.rect, stream=image)
        pdf.save(path)
    result = converter.convert_scoring_pdf_document(str(path))
    assert [p['pageNumber'] for p in result['pages']] == [1, 2]
    assert 'NATIVE CONTRACT TERMS' in result['pages'][0]['text']
    assert 'CONTRACT NO 20261111' in result['pages'][1]['text']
    assert '[第 2 页]\nCONTRACT NO 20261111' in result['markdown']


def test_ocr_failure_never_returns_partial_document(tmp_path, monkeypatch):
    path = tmp_path / 'failure.pdf'
    make_pdf(path, native=True)
    def fail(*args, **kwargs):
        raise FileNotFoundError('tesseract missing')
    monkeypatch.setattr(subprocess, 'run', fail)
    with pytest.raises(converter.ScoringPdfOcrError, match='BID_OCR_UNAVAILABLE'):
        converter.convert_scoring_pdf_document(str(path))


def test_ocr_deadline_does_not_return_native_only(tmp_path, monkeypatch):
    path = tmp_path / 'timeout.pdf'
    make_pdf(path, native=True)
    monkeypatch.setenv('SIDECAR_BID_OCR_TIMEOUT_SECONDS', '0')
    with pytest.raises(converter.ScoringPdfOcrError, match='BID_OCR_TIMEOUT'):
        converter.convert_scoring_pdf_document(str(path))


def test_scoring_ocr_is_not_limited_to_artifact_30_pages(tmp_path, monkeypatch):
    path = tmp_path / 'long.pdf'
    make_pdf(path, scanned_pages=31)
    monkeypatch.setattr(subprocess, 'run', lambda *a, **kw: subprocess.CompletedProcess(a, 0, b'ANNUAL SALES 3500000000', b''))
    result = converter.convert_scoring_pdf_document(str(path))
    assert len(result['pages']) == 31
    assert '[第 31 页]' in result['markdown']


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_scan_with_native_header_still_reads_image(tmp_path):
    path = tmp_path / 'header.pdf'
    make_pdf(path)
    with pymupdf.open(path) as pdf:
        pdf[0].insert_text((20, 25), 'NATIVE HEADER', fontsize=10)
        amended = tmp_path / 'amended.pdf'
        pdf.save(amended)
    result = converter.convert_scoring_pdf_document(str(amended))
    assert 'ANNUAL SALES' in result['markdown']
    assert 'NATIVE HEADER' in result['markdown']


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_blank_scanned_sheet_does_not_abort_complete_document(tmp_path):
    path = tmp_path / 'blank-sheet.pdf'
    make_pdf(path)
    blank = io.BytesIO()
    Image.new('RGB', (1400, 500), 'white').save(blank, format='PNG')
    with pymupdf.open(path) as pdf:
        page = pdf.new_page(width=700, height=250)
        page.insert_image(page.rect, stream=blank.getvalue())
        amended = tmp_path / 'amended.pdf'
        pdf.save(amended)
    result = converter.convert_scoring_pdf_document(str(amended))
    assert len(result['pages']) == 2
    assert result['pages'][1]['text'] == ''
    assert 'ANNUAL SALES' in result['markdown']


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
@pytest.mark.parametrize('split', [False, True], ids=['financial-screenshot', 'split-scan'])
def test_native_heading_does_not_hide_partial_scanned_evidence(tmp_path, split):
    path = tmp_path / 'partial-scan.pdf'
    image = Image.new('RGB', (1400, 630), 'white')
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=55)
    draw.text((70, 100), 'ANNUAL SALES 3500000000', fill='black', font=font)
    draw.text((70, 420), 'CONTRACT VALUE 72000000', fill='black', font=font)
    with pymupdf.open() as pdf:
        page = pdf.new_page(width=600, height=800)
        page.insert_text((30, 50), 'NATIVE FINANCIAL ATTACHMENT', fontsize=16)
        regions = [(0, 630)] if not split else [(0, 315), (315, 630)]
        for top, bottom in regions:
            data = io.BytesIO()
            image.crop((0, top, 1400, bottom)).save(data, format='PNG')
            page.insert_image(pymupdf.Rect(30, 150 + top * 243 / 630,
                                          570, 150 + bottom * 243 / 630),
                              stream=data.getvalue())
        pdf.save(path)
    result = converter.convert_scoring_pdf_document(str(path))
    assert 'NATIVE FINANCIAL ATTACHMENT' in result['markdown']
    assert 'ANNUAL SALES 3500000000' in result['markdown']
    assert 'CONTRACT VALUE 72000000' in result['markdown']
    assert [p['pageNumber'] for p in result['pages']] == [1]


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_native_heading_does_not_hide_inline_scanned_body(tmp_path):
    path = tmp_path / 'inline-scan.pdf'
    image = Image.new('RGB', (1400, 500), 'white')
    ImageDraw.Draw(image).text((70, 150), 'ANNUAL SALES 3500000000',
                              fill='black', font=ImageFont.load_default(size=55))
    with pymupdf.open() as pdf:
        page = pdf.new_page(width=700, height=400)
        page.insert_text((30, 40), 'NATIVE FINANCIAL ATTACHMENT', fontsize=16)
        xref = page.get_contents()[0]
        inline_image = (b'\nq 700 0 0 250 0 0 cm\n'
                        b'BI /W 1400 /H 500 /CS /RGB /BPC 8 /F /AHx ID\n'
                        + image.tobytes().hex().encode('ascii') + b'>\nEI\nQ\n')
        pdf.update_stream(xref, pdf.xref_stream(xref) + inline_image)
        pdf.save(path)
    with pymupdf.open(path) as pdf:
        assert pdf[0].get_images() == []  # Inline images have no image XObject.
        assert len(pdf[0].get_image_info()) == 1
    result = converter.convert_scoring_pdf_document(str(path))
    assert 'NATIVE FINANCIAL ATTACHMENT' in result['markdown']
    assert 'ANNUAL SALES 3500000000' in result['markdown']
    assert [p['pageNumber'] for p in result['pages']] == [1]


def test_native_only_pdf_does_not_require_ocr(tmp_path, monkeypatch):
    path = tmp_path / 'native-only.pdf'
    make_pdf(path, scanned_pages=0, native=True)
    def unavailable(*args, **kwargs):
        raise FileNotFoundError('tesseract missing')
    monkeypatch.setattr(subprocess, 'run', unavailable)
    result = converter.convert_scoring_pdf_document(str(path))
    assert result['markdown'].count('NATIVE CONTRACT TERMS') == 1
