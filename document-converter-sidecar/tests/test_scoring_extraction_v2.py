"""Scoring opt-in coverage against complete source ranges, using real OCR."""
import io
import shutil
import subprocess

import pymupdf
import pytest
from PIL import Image, ImageDraw, ImageFont


def _picture(text):
    image = Image.new('RGB', (1000, 220), 'white')
    ImageDraw.Draw(image).text((30, 70), text, fill='black', font=ImageFont.load_default(size=40))
    data = io.BytesIO()
    image.save(data, format='PNG')
    return data.getvalue()


def _forty_one(path, kind):
    repeated = _picture('CONTRACT VALUE 12300000')
    tail = _picture('CERTIFICATE ABC999')
    if kind == 'pdf':
        with pymupdf.open() as document:
            for number in range(41):
                page = document.new_page(width=600, height=160)
                page.insert_image(page.rect, stream=tail if number == 40 else repeated)
            document.save(path)
    else:
        from docx import Document
        document = Document()
        for number in range(41):
            document.add_picture(io.BytesIO(tail if number == 40 else repeated))
        document.save(path)


def test_nested_tables_keep_outer_and_inner_evidence_in_source_order(tmp_path):
    import app
    from docx import Document
    path = tmp_path / 'nested-evidence.docx'
    document = Document()
    outer = document.add_table(rows=1, cols=2)
    outer.cell(0, 0).text = 'OUTER CERTIFICATE ISO9001'
    inner = outer.cell(0, 0).add_table(rows=1, cols=1)
    inner.cell(0, 0).text = 'INNER CONTRACT VALUE 72000000'
    deep = inner.cell(0, 0).add_table(rows=1, cols=1)
    deep.cell(0, 0).text = 'DEEP CERTIFICATE ISO14001'
    inner.cell(0, 0).add_paragraph('INNER TAIL EVIDENCE')
    outer.cell(0, 0).add_paragraph('OUTER TAIL EVIDENCE')
    outer.cell(0, 1).text = 'NEXT CELL EVIDENCE'
    document.save(path)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    texts = [ref['text'] for ref in result.source_refs if ref['kind'] == 'TEXT']
    for text in texts:
        assert text in result.markdown
        assert result.markdown.count(text) == 1
    positions = [result.markdown.index(text) for text in texts]
    assert positions == sorted(positions)
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['sourceUnitCount'] == result.extraction['processedUnitCount'] == 6
    ordinary = app._extract_docx_streaming_sax(str(path))
    assert all(text in ordinary for text in texts)


def test_enumerated_native_text_missing_from_output_is_failed_coverage(tmp_path):
    from docx import Document
    from scoring_extraction import extract_scoring_document
    path = tmp_path / 'missing-evidence.docx'
    Document().save(path)
    def dropped_text(path, refs, deadline):
        refs.append({'partName': 'word/document.xml', 'anchor': 'p1', 'kind': 'TEXT',
                     'text': 'CERTIFICATE DROPPED FROM BODY'})
        return 'unrelated body'
    result = extract_scoring_document(str(path), dropped_text)
    assert result['extraction']['coverageStatus'] == 'PARTIAL'
    assert result['extraction']['failedUnitCount'] == 1
    assert result['extraction']['failedUnits'][0]['reason'] == 'SOURCE_TEXT_NOT_EMITTED'


def test_native_text_around_inline_image_and_nested_pipe_stays_complete(tmp_path):
    import app
    from docx import Document
    path = tmp_path / 'inline-evidence.docx'
    document = Document()
    outer = document.add_table(rows=1, cols=1)
    inner = outer.cell(0, 0).add_table(rows=1, cols=1)
    paragraph = inner.cell(0, 0).paragraphs[0]
    paragraph.add_run('NATIVE | BEFORE ')
    picture = io.BytesIO()
    Image.new('RGB', (10, 10), 'white').save(picture, format='PNG')
    paragraph.add_run().add_picture(io.BytesIO(picture.getvalue()))
    paragraph.add_run(' AFTER CERTIFICATE')
    document.save(path)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['failedUnitCount'] == 0
    assert 'NATIVE' in result.markdown and 'AFTER CERTIFICATE' in result.markdown


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
@pytest.mark.parametrize('kind', ['pdf', 'docx'])
def test_all_41_units_include_tail_evidence_and_deduplicate(tmp_path, kind):
    import app
    path = tmp_path / f'complete.{kind}'
    _forty_one(path, kind)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert 'CERTIFICATE ABC999' in result.text_content
    report = result.extraction
    assert report['coverageStatus'] == 'COMPLETE'
    assert report['sourceUnitCount'] == report['processedUnitCount'] == 41
    assert report['uniqueOcrUnitCount'] == 2
    assert report['unreadUnitCount'] == report['failedUnitCount'] == 0
    if kind == 'pdf':
        assert [page['pageNumber'] for page in result.pages] == list(range(1, 42))
    else:
        assert result.source_refs[-1]['relationshipId']
        assert result.source_refs[-1]['mediaName'].startswith('word/media/')
        assert result.source_refs[-1]['anchor'] == 'p41'


def test_engine_failure_and_deadline_are_explicit_partial_coverage(tmp_path, monkeypatch):
    import app
    import time
    path = tmp_path / 'partial.pdf'
    _forty_one(path, 'pdf')
    calls = []
    def recognize(*args, **kwargs):
        calls.append(1)
        raise FileNotFoundError('tesseract unavailable')
    monkeypatch.setattr(subprocess, 'run', recognize)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'PARTIAL'
    assert result.extraction['failedUnitCount'] == 41
    assert result.extraction['failedUnits'][-1]['pageNumber'] == 41
    calls.clear()
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True,
                                 deadline=time.monotonic() - 1)
    assert calls == []
    assert result.extraction['unreadUnitCount'] == 41


@pytest.mark.parametrize('limit,reason', [('bytes', 'IMAGE_BYTES_LIMIT'), ('pixels', 'IMAGE_PIXELS_LIMIT')])
def test_word_limits_are_checked_before_pixel_decode(tmp_path, monkeypatch, limit, reason):
    import app
    path = tmp_path / 'limited.docx'
    _forty_one(path, 'docx')
    monkeypatch.setenv('SIDECAR_BID_OCR_MAX_IMAGE_BYTES' if limit == 'bytes' else
                       'SIDECAR_BID_OCR_MAX_IMAGE_PIXELS', '1')
    def forbidden(*args, **kwargs):
        raise AssertionError('pixel decoding/OCR must not run for rejected image')
    monkeypatch.setattr(Image.Image, 'load', forbidden)
    monkeypatch.setattr(subprocess, 'run', forbidden)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'PARTIAL'
    assert result.extraction['processedUnitCount'] == 41
    assert result.extraction['failedUnitCount'] == 41
    assert {unit['reason'] for unit in result.extraction['failedUnits']} == {reason}


@pytest.mark.parametrize('workers', [1, 4])
def test_duplicate_word_references_decode_once_and_stay_at_source(tmp_path, monkeypatch, workers):
    import app
    from docx import Document
    monkeypatch.setenv('SIDECAR_BID_OCR_WORKERS', str(workers))
    path = tmp_path / 'ordered.docx'
    document = Document()
    document.add_paragraph('BEFORE CONTRACT')
    for _ in range(41):
        document.add_picture(io.BytesIO(_picture('CONTRACT VALUE 72000000')))
    document.add_paragraph('AFTER CONTRACT')
    document.save(path)
    opened = []
    real_open = Image.open
    def open_image(*args, **kwargs):
        opened.append(1)
        return real_open(*args, **kwargs)
    monkeypatch.setattr(Image, 'open', open_image)
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'CONTRACT VALUE 72000000', b''))
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert len(opened) == 1
    assert result.extraction['sourceUnitCount'] == 43
    assert result.text_content.index('BEFORE CONTRACT') < result.text_content.index('CONTRACT VALUE')
    assert result.text_content.rindex('CONTRACT VALUE') < result.text_content.index('AFTER CONTRACT')
    assert result.source_refs[1]['text'] == 'CONTRACT VALUE 72000000'


@pytest.mark.skipif(shutil.which('tesseract') is None, reason='requires real Tesseract')
@pytest.mark.parametrize('mode', ['RGBA', 'LA', 'P'])
def test_v2_transparent_small_word_image_retains_amount(tmp_path, mode):
    import app
    from docx import Document
    path = tmp_path / f'transparent-{mode}.docx'
    image = Image.new('RGBA', (399, 200), (0, 0, 0, 0))
    ImageDraw.Draw(image).text((15, 70), 'CONTRACT VALUE 72000000', fill='black',
                              font=ImageFont.load_default(size=20))
    image = image.convert(mode)
    if mode == 'P':
        image.info['transparency'] = image.getpixel((0, 0))
    data = io.BytesIO()
    image.save(data, format='PNG')
    document = Document()
    document.add_paragraph('NATIVE TERMS')
    document.add_picture(io.BytesIO(data.getvalue()))
    document.save(path)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert 'CONTRACTVALUE72000000' in ''.join(result.text_content.split())
    assert result.extraction['coverageStatus'] == 'COMPLETE'


def test_unreferenced_word_parts_do_not_become_coverage_obligations(tmp_path):
    import app
    import zipfile
    from docx import Document
    path = tmp_path / 'orphan-header.docx'
    document = Document()
    document.add_paragraph('ACTUAL NATIVE BODY')
    document.save(path)
    with zipfile.ZipFile(path, 'a') as archive:
        archive.writestr('word/header999.xml', '<broken-orphan')
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['sourceUnitCount'] == 1
    assert result.text_content == 'ACTUAL NATIVE BODY'


def test_referenced_word_header_is_enumerated(tmp_path):
    import app
    from docx import Document
    path = tmp_path / 'header.docx'
    document = Document()
    document.add_paragraph('BODY')
    document.sections[0].header.paragraphs[0].text = 'REFERENCED HEADER'
    document.save(path)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['sourceUnitCount'] == 2
    assert 'REFERENCED HEADER' in result.text_content
    assert result.source_refs[-1]['partName'] == 'word/header1.xml'


def test_nonwhite_empty_ocr_is_distinct_from_failed_and_unread(tmp_path, monkeypatch):
    import app
    path = tmp_path / 'empty.pdf'
    _forty_one(path, 'pdf')
    monkeypatch.setattr(subprocess, 'run', lambda *a, **k: subprocess.CompletedProcess(a, 0, b'', b''))
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['emptyUnitCount'] == 41
    assert result.extraction['emptyUnits'][-1]['pageNumber'] == 41
    assert result.extraction['failedUnitCount'] == result.extraction['unreadUnitCount'] == 0


def test_mixed_native_text_and_image_empty_ocr_records_page(tmp_path, monkeypatch):
    import app
    path = tmp_path / 'mixed-empty-ocr.pdf'
    with pymupdf.open() as document:
        page = document.new_page(width=600, height=400)
        page.insert_text((30, 40), 'NATIVE CERTIFICATE HEADER')
        page.insert_image(pymupdf.Rect(30, 80, 570, 300),
                          stream=_picture('SCANNED EVIDENCE'))
        document.save(path)
    calls = []
    def empty_ocr(*args, **kwargs):
        calls.append(1)
        return subprocess.CompletedProcess(args, 0, b'', b'')
    monkeypatch.setattr(subprocess, 'run', empty_ocr)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert calls == [1]
    assert 'NATIVE CERTIFICATE HEADER' in result.text_content
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['sourceUnitCount'] == result.extraction['processedUnitCount'] == 1
    assert result.extraction['uniqueOcrUnitCount'] == 1
    assert result.extraction['emptyUnitCount'] == 1
    assert result.extraction['emptyUnits'] == [{'pageNumber': 1}]
    assert result.extraction['failedUnitCount'] == result.extraction['unreadUnitCount'] == 0


def test_unverifiable_word_enumeration_is_unknown(tmp_path):
    import app
    import zipfile
    path = tmp_path / 'broken.docx'
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('word/document.xml', '<not-xml')
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert result.extraction['coverageStatus'] == 'UNKNOWN'
    assert result.extraction['enumerationComplete'] is False


@pytest.mark.parametrize('report', [False, True], ids=['existing-text-entry', 'v2-report-entry'])
def test_scoring_entries_preserve_native_docx_after_missing_eocd_repair(tmp_path, report):
    import app
    import hashlib
    from docx import Document
    path = tmp_path / 'missing-eocd.docx'
    document = Document()
    document.add_paragraph('NATIVE CONTRACT VALUE 72000000')
    document.save(path)
    original = path.read_bytes()
    eocd = original.rfind(b'PK\x05\x06')
    assert eocd > 0
    truncated = original[:eocd]
    path.write_bytes(truncated)
    expected_hash = hashlib.sha256(truncated).hexdigest()
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=report)
    assert 'NATIVE CONTRACT VALUE 72000000' in result.text_content
    if report:
        assert result.extraction['coverageStatus'] == 'COMPLETE'
        assert result.extraction['sourceUnitCount'] == result.extraction['processedUnitCount'] == 1
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected_hash


def test_word_native_text_after_timed_out_image_is_already_processed(tmp_path, monkeypatch):
    import app
    import scoring_extraction
    from docx import Document
    path = tmp_path / 'native-after-timeout.docx'
    document = Document()
    document.add_picture(io.BytesIO(_picture('UNREAD SCAN')))
    document.add_paragraph('LATE NATIVE CONTRACT VALUE 72000000')
    document.add_picture(io.BytesIO(_picture('OTHER UNREAD SCAN')))
    document.save(path)
    clock = [0.0]
    monkeypatch.setattr(scoring_extraction.time, 'monotonic', lambda: clock[0])
    def expire(*args, **kwargs):
        clock[0] = 10.0
        raise scoring_extraction.ScoringPdfOcrError('BID_OCR_TIMEOUT')
    monkeypatch.setattr(scoring_extraction, '_run_scoring_ocr', expire)
    monkeypatch.setenv('SIDECAR_BID_OCR_WORKERS', '1')
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True, deadline=1.0)
    assert 'LATE NATIVE CONTRACT VALUE 72000000' in result.text_content
    assert result.extraction['sourceUnitCount'] == 3
    assert result.extraction['processedUnitCount'] == 2
    assert result.extraction['unreadUnitCount'] == 1
    assert result.extraction['unreadUnits'][0]['kind'] == 'IMAGE'
    assert result.extraction['failedUnitCount'] == 1


def test_word_ocr_has_four_bounded_workers_and_deterministic_reference_order(tmp_path, monkeypatch):
    import app
    import scoring_extraction
    import threading
    import time
    from docx import Document
    path = tmp_path / 'parallel.docx'
    document = Document()
    for number in range(6):
        document.add_picture(io.BytesIO(_picture(f'CONTRACT VALUE {number}')))
    document.save(path)
    lock = threading.Lock()
    first_four = threading.Event()
    active = [0]
    peak = [0]
    def recognize(*args, **kwargs):
        with lock:
            active[0] += 1
            peak[0] = max(peak[0], active[0])
            if active[0] == 4:
                first_four.set()
        try:
            assert first_four.wait(timeout=0.5), 'four OCR jobs must be able to progress together'
            time.sleep(0.02)
            return 'CONTRACT'
        finally:
            with lock:
                active[0] -= 1
    monkeypatch.setattr(scoring_extraction, '_run_scoring_ocr', recognize)
    monkeypatch.setenv('SIDECAR_BID_OCR_WORKERS', '99')
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert peak[0] == 4
    assert result.extraction['uniqueOcrUnitCount'] == 6
    assert result.extraction['processedUnitCount'] == 6
    assert [ref['anchor'] for ref in result.source_refs] == [f'p{number}' for number in range(1, 7)]
    assert result.extraction['coverageStatus'] == 'COMPLETE'


def test_word_parallel_completion_preserves_wire_order_and_diagnostics(tmp_path, monkeypatch):
    import app
    import scoring_extraction
    import time
    from docx import Document
    path = tmp_path / 'out-of-order.docx'
    document = Document()
    for number in range(1, 7):
        image = Image.new('RGB', (120, 80), 'white')
        image.putpixel((1, 1), (number, 100, 100))
        raw = io.BytesIO()
        image.save(raw, format='PNG')
        document.add_picture(io.BytesIO(raw.getvalue()))
    document.save(path)
    def recognize(payload, *args, **kwargs):
        with Image.open(io.BytesIO(payload)) as image:
            number = image.getpixel((1, 1))[0]
        time.sleep((7 - number) * 0.01)
        if number == 4:
            raise scoring_extraction.ScoringPdfOcrError('BID_OCR_UNAVAILABLE')
        return '' if number in (3, 6) else f'UNIT {number}'
    monkeypatch.setattr(scoring_extraction, '_run_scoring_ocr', recognize)
    outputs = []
    for workers in (1, 4):
        monkeypatch.setenv('SIDECAR_BID_OCR_WORKERS', str(workers))
        result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
        result.extraction.pop('elapsedMillis')
        outputs.append((result.text_content, result.source_refs, result.extraction))
    assert outputs[0] == outputs[1]
    assert [unit['anchor'] for unit in outputs[1][2]['emptyUnits']] == ['p3', 'p6']
    assert outputs[1][2]['failedUnits'][0]['anchor'] == 'p4'
