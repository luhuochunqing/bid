"""Complete scoring-source enumeration and bounded, ordered OCR.

The report describes execution coverage, never recognition accuracy. Ordinary
conversion and its existing text-only API do not use this module.
"""
from __future__ import annotations

from concurrent.futures import FIRST_COMPLETED, Future, ThreadPoolExecutor, wait
import hashlib
import io
import os
import posixpath
import re
import struct
import threading
import time
import zipfile
import xml.etree.ElementTree as ET

from PIL import Image

from converter import ScoringPdfOcrError, _env_int, _pixmap_zoom, _prepare_ocr_png, _run_scoring_ocr


class _Coverage:
    def __init__(self, deadline):
        self.started = time.monotonic()
        maximum = min(1500, max(0, _env_int('SIDECAR_BID_OCR_TIMEOUT_SECONDS', 1500)))
        self.deadline = min(deadline if deadline is not None else float('inf'), self.started + maximum)
        self.refs = []
        self.failed = []
        self.unread = []
        self.empty = []
        self.processed = 0
        self.unique = 0
        self.cache = {}
        self.media_cache = {}
        self.encoded_cache = {}
        self.enumerated = True
        self.characters = 0
        self.cache_lock = threading.Lock()

    def expired(self):
        return time.monotonic() >= self.deadline

    def cached(self, cache, key, operation):
        # Reserve one Future before doing IO. Other references await the same
        # result instead of decoding or recognizing the same bytes in parallel.
        with self.cache_lock:
            future = cache.get(key)
            owner = future is None
            if owner:
                future = Future()
                cache[key] = future
        if owner:
            try:
                future.set_result(operation())
            except BaseException as error:
                future.set_exception(error)
                raise
        return future.result()

    def recognize(self, payload):
        def recognize_once():
            if self.expired():
                return '', 'BID_OCR_TIMEOUT'
            with self.cache_lock:
                self.unique += 1
            try:
                return _run_scoring_ocr(payload, self.deadline, omp_thread_limit=1), None
            except ScoringPdfOcrError as error:
                return '', str(error)
        return self.cached(self.cache, hashlib.sha256(payload).digest(), recognize_once)

    def finish_unit(self, ref, text, error=None, blank=False):
        self.processed += 1
        if self.characters + len(text) > 5_000_000:
            error = 'TEXT_LIMIT'
            text = ''
        if error:
            self.failed.append({**ref, 'reason': error})
        elif not text.strip() and not blank:
            self.empty.append(ref.copy())
        self.characters += len(text)
        return text

    def result(self, markdown, pages):
        def identity(ref):
            return tuple(ref.get(key) for key in ('pageNumber', 'partName', 'anchor',
                                                  'relationshipId', 'kind', 'marker'))
        positions = {identity(ref): index for index, ref in enumerate(self.refs)}
        for units in (self.failed, self.unread, self.empty):
            units.sort(key=lambda ref: positions.get(identity(ref), len(self.refs)))
        status = ('UNKNOWN' if not self.enumerated else
                  'PARTIAL' if self.failed or self.unread else 'COMPLETE')
        return {'markdown': markdown, 'pages': pages, 'sourceRefs': self.refs,
                'extraction': {
                    'schemaVersion': 1, 'engineVersion': 'scoring-ocr-v2',
                    'coverageStatus': status, 'sourceUnitCount': len(self.refs),
                    'processedUnitCount': self.processed, 'uniqueOcrUnitCount': self.unique,
                    'unreadUnitCount': len(self.unread), 'failedUnitCount': len(self.failed),
                    'emptyUnitCount': len(self.empty), 'unreadUnits': self.unread,
                    'failedUnits': self.failed, 'emptyUnits': self.empty,
                    'enumerationComplete': self.enumerated,
                    'elapsedMillis': int((time.monotonic() - self.started) * 1000)}}


def _pdf(path, coverage):
    import pymupdf

    # PyMuPDF stays on the caller thread. Only independent OCR subprocesses run
    # concurrently; at most one window of encoded pages is retained.
    workers = min(4, max(1, _env_int('SIDECAR_BID_OCR_WORKERS', 2)))
    pages = {}
    with pymupdf.open(path) as document, ThreadPoolExecutor(
            max_workers=workers, thread_name_prefix='pdf-ocr') as executor:
        coverage.refs = [{'pageNumber': number + 1} for number in range(len(document))]
        pending = {}

        def finish(ref, text, error=None, blank=False):
            text = coverage.finish_unit(ref, text.strip(), error, blank)
            pages[ref['pageNumber']] = {**ref, 'text': text}

        def drain():
            done, _ = wait(pending, return_when=FIRST_COMPLETED)
            for future in done:
                ref, native = pending.pop(future)
                ocr_text, error = future.result()
                text = native
                if ocr_text:
                    text = ocr_text if ''.join(native.split()) in ''.join(ocr_text.split()) else native + '\n' + ocr_text
                elif not error and native.strip():
                    coverage.empty.append(ref.copy())
                finish(ref, text, error)

        for ref in coverage.refs:
            if coverage.expired():
                break
            if len(pending) >= workers:
                drain()
                if coverage.expired():
                    break
            try:
                page = document.load_page(ref['pageNumber'] - 1)
                text = page.get_text().strip()
                if text and not page.get_image_info():
                    finish(ref, text)
                    continue
                zoom = _pixmap_zoom(page.rect.width, page.rect.height)
                pixmap = page.get_pixmap(matrix=pymupdf.Matrix(zoom, zoom), alpha=False)
                with Image.frombytes('RGB', (pixmap.width, pixmap.height), pixmap.samples) as image:
                    del pixmap
                    blank = image.getextrema() == ((255, 255),) * 3
                    if blank:
                        finish(ref, text, blank=True)
                        continue
                    payload = _prepare_ocr_png(image)
                pending[executor.submit(coverage.recognize, payload)] = (ref, text)
            except Exception as exc:
                finish(ref, '', 'BID_OCR_TIMEOUT' if isinstance(exc, TimeoutError) else 'SOURCE_DECODE_FAILED')
        while pending:
            drain()
    coverage.unread.extend({**ref, 'reason': 'BID_OCR_TIMEOUT'}
                           for ref in coverage.refs if ref['pageNumber'] not in pages)
    ordered = [pages.get(ref['pageNumber'], {**ref, 'text': ''}) for ref in coverage.refs]
    markdown = '\n\n'.join(f"[第 {page['pageNumber']} 页]\n{page['text']}" for page in ordered if page['text'])
    if len(markdown) > 5_000_000:
        raise ScoringPdfOcrError('SCORING_TEXT_TOO_LARGE')
    return coverage.result(markdown, ordered)


def _relationships(archive, part_name, deadline):
    path = posixpath.join(posixpath.dirname(part_name), '_rels', posixpath.basename(part_name) + '.rels')
    if path not in archive.namelist():
        return {}
    result = {}
    with archive.open(path) as source:
        for _, element in ET.iterparse(source, events=('end',)):
            if time.monotonic() >= deadline:
                raise TimeoutError('BID_OCR_TIMEOUT')
            if element.tag.split('}')[-1] == 'Relationship':
                target = element.get('Target', '')
                external = element.get('TargetMode') == 'External'
                media = posixpath.normpath(posixpath.join(posixpath.dirname(part_name), target))
                if target.startswith('/'):
                    media = target.lstrip('/')
                result[element.get('Id')] = None if external else media
            element.clear()
    return result


def _emf_unicode_text(raw, deadline):
    """Read a validated Unicode-only MS-EMF subset, never render unknown data.

    EmrText offsets are record-relative (MS-EMF 2.2.5 / 2.3.5.8):
    https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-emf/dd585d0a-5d7c-4034-963a-1141af836972
    Source-free BITBLT and state-only EMF+ Dual comments are checked separately.
    """
    def fail(reason='EMF_UNSUPPORTED_CONTENT'):
        raise ScoringPdfOcrError(reason)

    if len(raw) < 108 or struct.unpack_from('<I', raw, 0)[0] != 1:
        fail('EMF_INVALID_STRUCTURE')
    header_size = struct.unpack_from('<I', raw, 4)[0]
    if header_size not in (88, 100, 108) or header_size > len(raw):
        fail('EMF_INVALID_STRUCTURE')
    signature, version, byte_count, record_count = struct.unpack_from('<4I', raw, 40)
    if (signature != 0x464D4520 or version != 0x10000 or byte_count != len(raw)
            or record_count > len(raw) // 8 or record_count < 3):
        fail('EMF_INVALID_STRUCTURE')
    if struct.unpack_from('<III', raw, 60) != (0, 0, 0):
        fail()  # Descriptions and palettes are outside this small subset.
    if header_size >= 100 and any(struct.unpack_from('<III', raw, 88)):
        fail()  # Embedded pixel formats / OpenGL are not text-only.
    fixed = {10: 16, 18: 12, 20: 12, 22: 12, 24: 12, 25: 12, 27: 16,
             30: 24, 33: 8, 34: 12, 37: 12, 38: 28, 39: 24, 40: 12, 54: 16}
    plus_sizes = {0x4001: 28, 0x4002: 12, 0x4004: 12, 0x401E: 12,
                  0x4021: 12, 0x4022: 12, 0x4024: 12, 0x402A: 36, 0x4030: 16}
    plus_started = plus_ended = False
    offset, count, characters = 0, 0, 0
    pieces = []
    while offset < len(raw):
        if time.monotonic() >= deadline:
            fail('BID_OCR_TIMEOUT')
        if offset + 8 > len(raw):
            fail('EMF_INVALID_STRUCTURE')
        kind, size = struct.unpack_from('<II', raw, offset)
        if size < 8 or size % 4 or offset + size > len(raw):
            fail('EMF_INVALID_STRUCTURE')
        record = memoryview(raw)[offset:offset + size]
        if kind == 1:
            if offset != 0 or size != header_size:
                fail('EMF_INVALID_STRUCTURE')
        elif kind == 14:
            if (size != 20 or offset + size != len(raw)
                    or struct.unpack_from('<III', record, 8) != (0, 16, 20)):
                fail('EMF_INVALID_STRUCTURE')
        elif kind == 84:
            if size < 76:
                fail('EMF_INVALID_STRUCTURE')
            chars, string_offset, options = struct.unpack_from('<III', record, 44)
            dx_offset = struct.unpack_from('<I', record, 72)[0]
            if options != 0:
                fail('EMF_UNSUPPORTED_TEXT')
            string_end = string_offset + chars * 2
            if (string_offset < 76 or string_offset % 2 or string_end > size
                    or characters + chars > 5_000_000):
                fail('EMF_INVALID_STRUCTURE')
            if dx_offset and (dx_offset < 76 or dx_offset % 4 or dx_offset + chars * 4 > size
                              or not (dx_offset >= string_end or dx_offset + chars * 4 <= string_offset)):
                fail('EMF_INVALID_STRUCTURE')
            try:
                text = bytes(record[string_offset:string_end]).decode('utf-16-le').rstrip('\0')
            except UnicodeDecodeError:
                fail('EMF_UNSUPPORTED_TEXT')
            if any(ord(char) < 32 and char not in '\t\r\n' for char in text):
                fail('EMF_UNSUPPORTED_TEXT')
            pieces.append(text)
            characters += chars
        elif kind == 76:
            # MS-EMF 2.3.1.2: PATCOPY needs no source bitmap. Any offsets,
            # bitmap header/bits or other raster operation needs a renderer.
            if (size != 100 or struct.unpack_from('<I', record, 40)[0] != 0x00F00021
                    or any(struct.unpack_from('<4I', record, 84))):
                fail('EMF_BITMAP_UNSUPPORTED')
        elif kind == 70:
            if (size < 16 or struct.unpack_from('<I', record, 8)[0] != size - 12
                    or bytes(record[12:16]) != b'EMF+'):
                fail()
            position = 16
            while position < size:
                if position + 12 > size:
                    fail('EMF_INVALID_STRUCTURE')
                plus_kind, flags, length, data_size = struct.unpack_from('<HHII', record, position)
                if (plus_kind not in plus_sizes or length != plus_sizes[plus_kind]
                        or data_size != length - 12 or position + length > size):
                    fail()
                allowed_flags = {0x401E: range(16), 0x4021: range(8), 0x4022: range(5),
                                 0x4024: range(5), 0x4030: range(7)}
                if plus_kind in allowed_flags and flags not in allowed_flags[plus_kind]:
                    fail()
                if plus_kind == 0x4001:
                    if plus_started or count != 1 or flags != 1:
                        fail()  # Only Dual guarantees the base EMF is complete.
                    plus_started = True
                elif not plus_started or plus_ended:
                    fail()
                elif plus_kind == 0x4002:
                    if flags != 0:
                        fail()
                    plus_ended = True
                elif plus_kind in (0x4004, 0x402A) and flags != 0:
                    fail()
                position += length
        elif kind == 75:
            if size != 16 or struct.unpack_from('<I', record, 8)[0] != 0:
                fail()  # Only a reset with no variable region payload.
        elif kind == 82:
            if size not in (332, 368) or record[35] == 2:
                fail('EMF_UNSUPPORTED_TEXT')  # Symbol-font glyph codes are not Unicode.
        elif kind not in fixed or size != fixed[kind]:
            fail('EMF_UNSUPPORTED_RECORD')
        offset += size
        count += 1
    if kind != 14 or count != record_count or (plus_started and not plus_ended):
        fail('EMF_INVALID_STRUCTURE')
    text = '\n'.join(piece for piece in pieces if piece.strip())
    if not text.strip():
        fail('EMF_NO_UNICODE_TEXT')
    return text


def _word_image(archive, name, coverage):
    def read_media():
        # The encoded limit and lazy Pillow dimensions precede pixel loading.
        info = archive.getinfo(name)
        if info.file_size > max(1, _env_int('SIDECAR_BID_OCR_MAX_IMAGE_BYTES', 64 * 1024 * 1024)):
            return '', 'IMAGE_BYTES_LIMIT', False
        if coverage.expired():
            return '', 'BID_OCR_TIMEOUT', False
        raw = archive.read(name)

        def decode_pixels():
            try:
                if name.lower().endswith('.emf') or (raw[:4] == b'\x01\x00\x00\x00' and raw[40:44] == b' EMF'):
                    return _emf_unicode_text(raw, coverage.deadline), None, False
                with Image.open(io.BytesIO(raw)) as image:
                    if image.width * image.height > max(1, _env_int('SIDECAR_BID_OCR_MAX_IMAGE_PIXELS', 16_000_000)):
                        return '', 'IMAGE_PIXELS_LIMIT', False
                    if coverage.expired():
                        return '', 'BID_OCR_TIMEOUT', False
                    with image.convert('RGBA') as rgba, Image.new('RGB', image.size, 'white') as rgb:
                        rgb.paste(rgba, mask=rgba.getchannel('A'))
                        blank = rgb.getextrema() == ((255, 255),) * 3
                        if blank:
                            return '', None, True
                        payload = _prepare_ocr_png(rgb)
                # Release full-resolution pixel buffers while Tesseract runs.
                text, error = coverage.recognize(payload)
                return text, error, False
            except ScoringPdfOcrError as error:
                return '', str(error), False
            except Exception:
                return '', 'SOURCE_DECODE_FAILED', False

        return coverage.cached(coverage.encoded_cache, hashlib.sha256(raw).digest(), decode_pixels)

    return coverage.cached(coverage.media_cache, name, read_media)


def _word_reference(archive, ref, coverage):
    media = ref.get('mediaName')
    if not media or not media.startswith('word/'):
        return '', 'IMAGE_REFERENCE_UNRESOLVED', False
    try:
        return _word_image(archive, media, coverage)
    except Exception as error:
        return '', ('BID_OCR_TIMEOUT' if isinstance(error, TimeoutError) else 'SOURCE_DECODE_FAILED'), False


def _word_results(archive, refs, coverage):
    workers = min(4, max(1, _env_int('SIDECAR_BID_OCR_WORKERS', 1)))
    # Only this window is submitted. Finished jobs free a slot immediately;
    # their text goes directly into canonical refs, not an extra result queue.
    with ThreadPoolExecutor(max_workers=workers, thread_name_prefix='scoring-ocr') as executor:
        pending = {}
        next_index = 0
        while pending or (next_index < len(refs) and not coverage.expired()):
            while len(pending) < workers and next_index < len(refs) and not coverage.expired():
                ref = refs[next_index]
                pending[executor.submit(_word_reference, archive, ref, coverage)] = (next_index, ref)
                next_index += 1
            if pending:
                done, _ = wait(pending, return_when=FIRST_COMPLETED)
                for future in sorted(done, key=lambda item: pending[item][0]):
                    _, ref = pending.pop(future)
                    yield ref, future.result()


def _docx(path, sax_extractor, coverage):
    try:
        native_text = sax_extractor(path, coverage.refs, coverage.deadline)
    except Exception:
        coverage.enumerated = False
        coverage.unread.extend({**ref, 'reason': 'SOURCE_ENUMERATION_FAILED'} for ref in coverage.refs)
        return coverage.result('', [])
    # SAX preserves existing headings/tables; sourceRefs carry native text for
    # exact paragraph anchors instead of running a second body traversal.
    body = native_text if len(native_text) <= 5_000_000 else ''
    coverage.characters = len(body)
    replacements = {}
    # Verify emitted native units in source order. Enumeration alone does not
    # prove a paragraph survived SAX table rendering into model input.
    native_refs = [ref for ref in coverage.refs if ref['kind'] == 'TEXT']
    coverage.processed = len(native_refs)
    if len(native_text) > 5_000_000:
        coverage.failed.extend({**ref, 'reason': 'TEXT_LIMIT'} for ref in native_refs)
    else:
        # Inline image placeholders are separate units; table nesting adds
        # Markdown pipe escapes but must not make emitted native text fail.
        def normalized(text):
            return ''.join(re.sub(r'\\+(?=\|)', '', text).split())
        normalized_body = normalized(re.sub(r'【图片证据:[^】]+】', '', native_text))
        position = 0
        for ref in native_refs:
            text = normalized(ref['text'])
            found = normalized_body.find(text, position)
            if found < 0:
                coverage.failed.append({**ref, 'reason': 'SOURCE_TEXT_NOT_EMITTED'})
            else:
                position = found + len(text)
    image_refs = [ref for ref in coverage.refs if ref['kind'] == 'IMAGE']
    relationships = {}
    with zipfile.ZipFile(path) as archive:
        for ref in image_refs:
            if coverage.expired():
                break
            part = ref['partName']
            try:
                if part not in relationships:
                    relationships[part] = _relationships(archive, part, coverage.deadline)
                ref['mediaName'] = relationships[part].get(ref.get('relationshipId'))
            except TimeoutError:
                break
            except Exception:
                ref['mediaName'] = None
        completed_images = set()
        for ref, (text, error, blank) in _word_results(archive, image_refs, coverage):
            if coverage.characters + len(text) > 5_000_000:
                raise ScoringPdfOcrError('SCORING_TEXT_TOO_LARGE')
            text = coverage.finish_unit(ref, text, error, blank)
            ref['text'] = text
            location = f"[{ref['partName']}#{ref['anchor']} {ref['relationshipId']}]"
            replacements[ref['marker']] = location + ((' ' + text) if text else '')
            completed_images.add(id(ref))
        coverage.unread.extend({**ref, 'reason': 'BID_OCR_TIMEOUT'}
                               for ref in image_refs if id(ref) not in completed_images)
    # Each marker originated in the existing SAX traversal. Substitution keeps
    # the OCR beside its paragraph/table cell, including repeated references.
    body = re.sub(r'【图片证据:[^】]+】', lambda match: replacements.get(match[0], '[图片未读取]'), body)
    if len(body) > 5_000_000:
        raise ScoringPdfOcrError('SCORING_TEXT_TOO_LARGE')
    return coverage.result(body, [])


def extract_scoring_document(path, sax_extractor, deadline=None):
    coverage = _Coverage(deadline)
    suffix = os.path.splitext(path)[1].lower()
    if suffix == '.pdf':
        return _pdf(path, coverage)
    if suffix == '.docx':
        return _docx(path, sax_extractor, coverage)
    coverage.enumerated = False
    return coverage.result('', [])
