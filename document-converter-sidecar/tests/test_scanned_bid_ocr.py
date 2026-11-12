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


@pytest.mark.parametrize('failure,code', [
    ('missing', 'BID_OCR_UNAVAILABLE'),
    ('timeout', 'BID_OCR_TIMEOUT'),
    ('nonzero', 'BID_OCR_UNAVAILABLE'),
])
def test_ocr_failure_never_returns_partial_document(tmp_path, monkeypatch, failure, code):
    path = tmp_path / 'failure.pdf'
    make_pdf(path, native=True)
    def fail(*args, **kwargs):
        if failure == 'missing':
            raise FileNotFoundError('tesseract missing')
        if failure == 'timeout':
            raise subprocess.TimeoutExpired('tesseract', 30)
        return subprocess.CompletedProcess(args, 1, b'', b'OCR engine failed')
    monkeypatch.setattr(subprocess, 'run', fail)
    with pytest.raises(converter.ScoringPdfOcrError, match=code):
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


def test_empty_ocr_on_one_image_keeps_the_other_pages(tmp_path, monkeypatch):
    path = tmp_path / 'mixed.pdf'
    make_pdf(path, native=True)
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'   ', b''))
    result = converter.convert_scoring_pdf_document(str(path))
    assert 'NATIVE CONTRACT TERMS' in result['markdown']
    assert result['pages'][1]['text'] == ''


def test_imageless_page_without_text_uses_existing_ocr(tmp_path, monkeypatch):
    path = tmp_path / 'vector.pdf'
    with pymupdf.open() as pdf:
        page = pdf.new_page(width=400, height=200)
        page.draw_rect(pymupdf.Rect(30, 30, 180, 80), color=(0, 0, 0), fill=(0, 0, 0))
        pdf.save(path)
    with pymupdf.open(path) as pdf:
        assert pdf[0].get_image_info() == []
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'SCANNED CLAUSE', b''))
    result = converter.convert_scoring_pdf_document(str(path))
    assert 'SCANNED CLAUSE' in result['markdown']


def test_document_with_no_text_after_ocr_fails(tmp_path, monkeypatch):
    path = tmp_path / 'scan.pdf'
    make_pdf(path, scanned_pages=1)
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'   ', b''))
    with pytest.raises(converter.ScoringPdfOcrError, match='BID_OCR_EMPTY'):
        converter.convert_scoring_pdf_document(str(path))


def _docx_with_picture(path, paragraph, image_size):
    from docx import Document
    from docx.shared import Inches
    image_path = path.with_suffix('.png')
    image = Image.new('RGB', image_size, 'white')
    ImageDraw.Draw(image).rectangle((4, 4, image_size[0] - 4, image_size[1] - 4), fill='black')
    image.save(image_path)
    document = Document()
    if paragraph:
        document.add_paragraph(paragraph)
    document.add_picture(str(image_path), width=Inches(2))
    document.save(path)


def test_docx_embedded_scan_uses_existing_ocr(tmp_path, monkeypatch):
    path = tmp_path / 'bid.docx'
    _docx_with_picture(path, 'NATIVE WORD TERMS', (800, 400))
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'SCANNED CLAUSE', b''))
    text = converter.append_docx_image_ocr(str(path), 'NATIVE WORD TERMS')
    assert 'NATIVE WORD TERMS' in text
    assert 'SCANNED CLAUSE' in text


@pytest.mark.parametrize('scoring', [False, True])
@pytest.mark.parametrize('paragraph', ['', 'NATIVE WORD TERMS ' * 5])
def test_docx_worker_conversion_failure_preserves_scoring_opt_in(tmp_path, monkeypatch, scoring, paragraph):
    import app

    path = tmp_path / 'fallback.docx'
    _docx_with_picture(path, paragraph, (800, 400))

    def failed_conversion(*args, **kwargs):
        raise RuntimeError('conversion failed')

    monkeypatch.setattr(app.md, 'convert', failed_conversion)
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'SCANNED CLAUSE', b''))
    if not scoring and not paragraph:
        with pytest.raises(RuntimeError, match='conversion failed'):
            app._convert_worker(str(path), pdf_ocr=scoring)
        return

    result = app._convert_worker(str(path), pdf_ocr=scoring)
    assert ('SCANNED CLAUSE' in result.text_content) == scoring
    if paragraph:
        assert 'NATIVE WORD TERMS' in result.text_content
    assert result.markdown == result.text_content
    assert result.pages == []


def test_docx_native_text_does_not_require_ocr_engine(tmp_path, monkeypatch):
    from docx import Document
    path = tmp_path / 'native.docx'
    document = Document()
    document.add_paragraph('NATIVE WORD TERMS')
    document.save(path)

    def missing(*args, **kwargs):
        raise FileNotFoundError('tesseract')

    monkeypatch.setattr(subprocess, 'run', missing)
    assert converter.append_docx_image_ocr(str(path), 'NATIVE WORD TERMS') == 'NATIVE WORD TERMS'


def test_docx_placeholder_only_empty_ocr_fails(tmp_path, monkeypatch):
    path = tmp_path / 'scan.docx'
    _docx_with_picture(path, None, (800, 400))
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'   ', b''))
    with pytest.raises(converter.ScoringPdfOcrError, match='BID_OCR_EMPTY'):
        converter.append_docx_image_ocr(str(path), '【已附图片】')


def test_docx_icon_empty_ocr_keeps_native_text(tmp_path, monkeypatch):
    path = tmp_path / 'icon.docx'
    _docx_with_picture(path, 'NATIVE WORD TERMS', (32, 32))
    called = {'n': 0}

    def empty_recognition(*args, **kwargs):
        called['n'] += 1
        return subprocess.CompletedProcess(args, 0, b'   ', b'')

    monkeypatch.setattr(subprocess, 'run', empty_recognition)
    assert converter.append_docx_image_ocr(str(path), 'NATIVE WORD TERMS') == 'NATIVE WORD TERMS'
    assert called['n'] == 1


def test_native_only_pdf_does_not_require_ocr(tmp_path, monkeypatch):
    path = tmp_path / 'native-only.pdf'
    make_pdf(path, scanned_pages=0, native=True)
    def unavailable(*args, **kwargs):
        raise FileNotFoundError('tesseract missing')
    monkeypatch.setattr(subprocess, 'run', unavailable)
    result = converter.convert_scoring_pdf_document(str(path))
    assert result['markdown'].count('NATIVE CONTRACT TERMS') == 1


@pytest.mark.parametrize('previous_scan', [False, True], ids=['native-cover', 'first-scan'])
@pytest.mark.parametrize('failure,code', [
    ('timeout', 'BID_OCR_TIMEOUT'),
    ('missing', 'BID_OCR_UNAVAILABLE'),
    ('nonzero', 'BID_OCR_UNAVAILABLE'),
    ('deadline', 'BID_OCR_TIMEOUT'),
])
def test_docx_ocr_failure_never_returns_partial_text(tmp_path, monkeypatch, previous_scan, failure, code):
    from docx import Document
    import time

    path = tmp_path / 'partial.docx'
    native = '' if previous_scan else 'NATIVE COVER'
    _docx_with_picture(path, native, (800, 400))
    if previous_scan:
        picture = io.BytesIO()
        Image.new('RGB', (800, 400), 'black').save(picture, format='PNG')
        picture.seek(0)
        document = Document(path)
        document.add_picture(picture)
        document.save(path)

    clock = [0.0]
    calls = [0]

    def recognize(*args, **kwargs):
        calls[0] += 1
        if previous_scan and calls[0] == 1:
            if failure == 'deadline':
                clock[0] = 10.0
            return subprocess.CompletedProcess(args, 0, b'FIRST SCANNED CLAUSE', b'')
        if failure == 'timeout':
            raise subprocess.TimeoutExpired('tesseract', 30)
        if failure == 'missing':
            raise FileNotFoundError('tesseract')
        return subprocess.CompletedProcess(args, 1, b'', b'OCR engine failed')

    monkeypatch.setattr(subprocess, 'run', recognize)
    if failure == 'deadline':
        monkeypatch.setenv('SIDECAR_BID_OCR_TIMEOUT_SECONDS', '1' if previous_scan else '0')
        monkeypatch.setattr(time, 'monotonic', lambda: clock[0])
    with pytest.raises(converter.ScoringPdfOcrError, match=code):
        converter.append_docx_image_ocr(str(path), native)


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
@pytest.mark.parametrize('mode', ['RGBA', 'LA', 'P'])
@pytest.mark.parametrize('native', ['', 'NATIVE WORD TERMS'])
def test_docx_transparent_contract_preserves_visible_amount(tmp_path, mode, native):
    from docx import Document

    path = tmp_path / 'transparent-contract.docx'
    image = Image.new('RGBA', (1400, 500), (0, 0, 0, 0))
    ImageDraw.Draw(image).text((70, 150), 'CONTRACT VALUE 72000000', fill='black',
                              font=ImageFont.load_default(size=55))
    image = image.convert(mode)
    if mode == 'P':
        image.info['transparency'] = image.getpixel((0, 0))
    picture = io.BytesIO()
    image.save(picture, format='PNG')
    picture.seek(0)
    document = Document()
    if native:
        document.add_paragraph(native)
    document.add_picture(picture)
    document.save(path)

    text = converter.append_docx_image_ocr(str(path), native)
    if native:
        assert native in text
    assert 'CONTRACT VALUE 72000000' in text


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
def test_docx_small_contract_screenshot_is_not_lost_with_native_text(tmp_path):
    from docx import Document

    path = tmp_path / 'small-contract.docx'
    picture = io.BytesIO()
    image = Image.new('RGB', (399, 200), 'white')
    ImageDraw.Draw(image).text((15, 70), 'CONTRACT VALUE 72000000', fill='black',
                              font=ImageFont.load_default(size=20))
    image.save(picture, format='PNG')
    picture.seek(0)
    document = Document()
    document.add_paragraph('NATIVE WORD TERMS')
    document.add_picture(picture)
    document.save(path)

    text = converter.append_docx_image_ocr(str(path), 'NATIVE WORD TERMS')
    assert 'NATIVE WORD TERMS' in text
    assert 'CONTRACT VALUE 72000000' in text
