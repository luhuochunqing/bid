"""Synthetic MS-EMF subset: Unicode text only, never customer bytes."""
import io
import struct
import zipfile

import pytest
from PIL import Image


def _record(kind, payload=b''):
    padding = (-len(payload)) % 4
    return struct.pack('<II', kind, 8 + len(payload) + padding) + payload + b'\0' * padding


def _text(text='ISO9001 CONTRACT VALUE 72000000', options=0, offset=76):
    encoded = text.encode('utf-16-le')
    record = bytearray(76 + len(encoded) + (-len(encoded) % 4))
    struct.pack_into('<II', record, 0, 84, len(record))
    struct.pack_into('<I', record, 24, 1)
    struct.pack_into('<III', record, 44, len(encoded) // 2, offset, options)
    record[76:76 + len(encoded)] = encoded
    return bytes(record)


def _plus(kind, flags=0, data=b''):
    return struct.pack('<HHII', kind, flags, 12 + len(data), len(data)) + data


def _comment(records):
    payload = b'EMF+' + b''.join(records)
    return _record(70, struct.pack('<I', len(payload)) + payload)


def _emf(records):
    header = bytearray(108)
    eof = struct.pack('<IIIII', 14, 20, 0, 16, 20)
    struct.pack_into('<II', header, 0, 1, len(header))
    struct.pack_into('<4i', header, 8, 0, 0, 1098, 1243)
    struct.pack_into('<IIII', header, 40, 0x464D4520, 0x10000,
                     len(header) + sum(map(len, records)) + len(eof), len(records) + 2)
    return bytes(header) + b''.join(records) + eof


def _word_with_media(path, raw):
    from docx import Document
    picture = io.BytesIO()
    Image.new('RGB', (20, 20), 'black').save(picture, format='PNG')
    document = Document()
    document.add_picture(io.BytesIO(picture.getvalue()))
    original = io.BytesIO()
    document.save(original)
    with zipfile.ZipFile(original) as source, zipfile.ZipFile(path, 'w') as target:
        for entry in source.infolist():
            data = source.read(entry.filename)
            name = entry.filename
            if name == 'word/media/image1.png':
                name, data = 'word/media/image1.emf', raw
            elif name == 'word/_rels/document.xml.rels':
                data = data.replace(b'media/image1.png', b'media/image1.emf')
            elif name == '[Content_Types].xml':
                data = data.replace(b'</Types>', b'<Default Extension="emf" ContentType="image/x-emf"/></Types>')
            target.writestr(name, data)


def _extract(tmp_path, raw):
    import app
    path = tmp_path / 'synthetic-emf.docx'
    _word_with_media(path, raw)
    return app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)


def test_unicode_emf_text_is_extracted_without_raster_loader(tmp_path):
    result = _extract(tmp_path, _emf([_text()]))
    assert 'ISO9001 CONTRACT VALUE 72000000' in result.text_content
    assert result.extraction['coverageStatus'] == 'COMPLETE'
    assert result.extraction['processedUnitCount'] == 1
    assert result.extraction['uniqueOcrUnitCount'] == 0
    assert result.source_refs[0]['mediaName'] == 'word/media/image1.emf'


def test_source_free_bitblt_and_dual_state_comments_are_validated(tmp_path):
    bitblt = bytearray(100)
    struct.pack_into('<II', bitblt, 0, 76, 100)
    struct.pack_into('<I', bitblt, 40, 0x00F00021)  # PATCOPY has no bitmap source.
    records = [_comment([_plus(0x4001, 1, b'\0' * 16)]),
               _comment([_plus(0x4022, 4), _plus(0x4004)]),
               bytes(bitblt), _text('合同金额72000000'),
               _comment([_plus(0x4002)])]
    result = _extract(tmp_path, _emf(records))
    assert '合同金额72000000' in result.text_content
    assert result.extraction['coverageStatus'] == 'COMPLETE'


@pytest.mark.parametrize('records', [
    [_text(offset=0)],
    [_text(offset=77)],
    [_text(offset=0xFFFFFFFE)],
    [_text(options=0x10)],  # ETO_GLYPH_INDEX is not Unicode text.
    [_text(options=0x04)],  # Clipped text needs rendering.
    [_record(83, b'\0' * 68), _text()],  # ASCII text is outside this subset.
    [_record(96, b'\0' * 28), _text()],  # POLYTEXTOUTW is not silently ignored.
    [_comment([_plus(0x401C)]), _text()],  # EMF+ DrawString must be read by a renderer.
    [_comment([_plus(0x401A)]), _text()],  # EMF+ image payload is unsupported.
    [_comment([_plus(0x4001, 0, b'\0' * 16)]), _text()],  # EMF+ Only is not dual.
    [_record(76, b'\0' * 92 + b'PIXELS'), _text()],
    [_record(999, b''), _text()],
])
def test_unsupported_or_malformed_emf_never_reports_complete(tmp_path, records):
    result = _extract(tmp_path, _emf(records))
    assert result.extraction['coverageStatus'] == 'PARTIAL'
    assert result.extraction['failedUnitCount'] == 1
    assert result.text_content.strip().endswith('rId9]')


@pytest.mark.parametrize('damage', ['record-size', 'header-bytes', 'record-count', 'missing-eof', 'utf16'])
def test_emf_structural_corruption_is_explicit_partial(tmp_path, damage):
    raw = bytearray(_emf([_text()]))
    if damage == 'record-size':
        struct.pack_into('<I', raw, 112, 4)
    elif damage == 'header-bytes':
        struct.pack_into('<I', raw, 48, len(raw) - 4)
    elif damage == 'record-count':
        struct.pack_into('<I', raw, 52, 999)
    elif damage == 'missing-eof':
        raw = raw[:-20]
    else:
        raw[108 + 76:108 + 78] = b'\0\xd8'  # Unpaired high surrogate.
    result = _extract(tmp_path, bytes(raw))
    assert result.extraction['coverageStatus'] == 'PARTIAL'
    assert result.extraction['failedUnitCount'] == 1


def test_emf_bitmap_counts_and_symbol_fonts_cannot_be_ignored(tmp_path):
    bitblt = bytearray(100)
    struct.pack_into('<II', bitblt, 0, 76, 100)
    struct.pack_into('<I', bitblt, 40, 0x00F00021)
    struct.pack_into('<I', bitblt, 96, 1)
    font = bytearray(368)
    struct.pack_into('<II', font, 0, 82, 368)
    font[35] = 2
    for unsupported in (bytes(bitblt), bytes(font)):
        result = _extract(tmp_path, _emf([unsupported, _text()]))
        assert result.extraction['coverageStatus'] == 'PARTIAL'
        assert result.extraction['failedUnitCount'] == 1


def test_emf_parser_respects_request_deadline():
    import time
    from scoring_extraction import _emf_unicode_text, ScoringPdfOcrError
    with pytest.raises(ScoringPdfOcrError, match='BID_OCR_TIMEOUT'):
        _emf_unicode_text(_emf([_text()]), time.monotonic() - 1)
