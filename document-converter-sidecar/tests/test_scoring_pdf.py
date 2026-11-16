"""PDF rendering stays on its caller; OCR subprocess work has a bounded window."""
import io
import threading
import time

import pymupdf
from PIL import Image, ImageDraw, ImageFont


def picture(text):
    image = Image.new('RGB', (900, 200), 'white')
    ImageDraw.Draw(image).text((20, 60), text, fill='black', font=ImageFont.load_default(size=32))
    data = io.BytesIO()
    image.save(data, format='PNG')
    return data.getvalue()


def test_pdf_ocr_uses_bounded_parallel_window_and_keeps_page_order(tmp_path, monkeypatch):
    import scoring_extraction as extraction
    path = tmp_path / 'window.pdf'
    with pymupdf.open() as doc:
        for i in range(8):
            page = doc.new_page(width=600, height=160)
            page.insert_image(page.rect, stream=picture(f'PAGE {i}'))
        doc.save(path)
    caller = threading.get_ident()
    original_render = pymupdf.Page.get_pixmap
    def render(page, *args, **kwargs):
        assert threading.get_ident() == caller
        return original_render(page, *args, **kwargs)
    monkeypatch.setattr(pymupdf.Page, 'get_pixmap', render)
    monkeypatch.setenv('SIDECAR_BID_OCR_WORKERS', '3')
    active = peak = 0
    lock = threading.Lock()
    def recognize(payload, deadline, **kwargs):
        nonlocal active, peak
        with lock:
            active += 1
            peak = max(peak, active)
        time.sleep(.06)
        with lock:
            active -= 1
        return 'RECOGNIZED SOURCE'
    monkeypatch.setattr(extraction, '_run_scoring_ocr', recognize)
    result = extraction.extract_scoring_document(str(path), None)
    assert 1 < peak <= 3
    assert [p['pageNumber'] for p in result['pages']] == list(range(1, 9))
    assert all('RECOGNIZED SOURCE' in p['text'] for p in result['pages'])
    assert result['extraction']['processedUnitCount'] == 8
    assert result['extraction']['coverageStatus'] == 'COMPLETE'


def test_pdf_timeout_keeps_exact_unread_suffix(tmp_path, monkeypatch):
    import scoring_extraction as extraction
    path=tmp_path/'timeout.pdf'
    with pymupdf.open() as doc:
        for i in range(7):
            page=doc.new_page(width=600,height=160)
            page.insert_image(page.rect,stream=picture(f'UNIQUE {i}'))
        doc.save(path)
    monkeypatch.setenv('SIDECAR_BID_OCR_WORKERS','2')
    monkeypatch.setenv('SIDECAR_BID_OCR_TIMEOUT_SECONDS','0')
    result=extraction.extract_scoring_document(str(path),None)
    report=result['extraction']
    assert report['coverageStatus']=='PARTIAL'
    assert report['processedUnitCount']==0
    assert report['unreadUnitCount']==7
    assert len(result['pages'])==7
