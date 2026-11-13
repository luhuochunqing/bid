"""Real worker cancellation, admission, deadline and ordinary API contracts."""
import asyncio
import os
import subprocess
import sys
import time
from types import SimpleNamespace

import pytest
from fastapi import HTTPException
from httpx import ASGITransport, AsyncClient


def _blocking_worker(pid_path):
    process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(120)'])
    with open(pid_path, 'w') as file:
        file.write(f'{os.getpid()} {process.pid}')
    time.sleep(120)


def _normal_worker():
    return SimpleNamespace(text_content='ordinary conversion')


def _alive(pid):
    try:
        os.kill(pid, 0)
        return True
    except ProcessLookupError:
        return False


@pytest.mark.asyncio
async def test_running_timeout_reaps_request_process_tree_and_next_request_works(tmp_path):
    from conversion_execution import execute_conversion
    pid_path = tmp_path / 'pids'
    deadline = time.monotonic() + 2
    with pytest.raises(HTTPException) as caught:
        await execute_conversion(_blocking_worker, (str(pid_path),), deadline)
    assert caught.value.status_code == 504
    assert pid_path.exists(), 'must test an already-running worker, not just spawn timeout'
    pids = [int(pid) for pid in pid_path.read_text().split()]
    for _ in range(50):
        if not any(_alive(pid) for pid in pids):
            break
        await asyncio.sleep(0.02)
    assert not any(_alive(pid) for pid in pids)
    result = await execute_conversion(_normal_worker, (), time.monotonic() + 5)
    assert result.text_content == 'ordinary conversion'


@pytest.mark.asyncio
async def test_expired_request_does_not_start_worker(tmp_path):
    from conversion_execution import execute_conversion
    pid_path = tmp_path / 'never-started'
    with pytest.raises(HTTPException) as caught:
        await execute_conversion(_blocking_worker, (str(pid_path),), time.monotonic() - 1)
    assert caught.value.status_code == 504
    assert not pid_path.exists()


@pytest.mark.asyncio
async def test_image_supplement_rejects_expired_or_malformed_deadline():
    import app
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        for deadline, status in [('1', 504), ('invalid', 400)]:
            response = await client.post('/convert-images?renderPages=true&pages=1',
                    headers={'X-Scoring-Deadline-Epoch-Millis': deadline},
                    files={'file': ('unread.pdf', b'invalid')})
            assert response.status_code == status


@pytest.mark.asyncio
async def test_image_supplement_keeps_ordinary_invalid_page_error_contract():
    import app
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        response = await client.post('/convert-images?pages=invalid', files={'file': ('invalid.pdf', b'invalid')})
        assert response.status_code == 422
        assert response.json() == {'error': '文档无法解析：格式不支持或已损坏'}


@pytest.mark.asyncio
async def test_image_supplement_shares_conversion_capacity(monkeypatch):
    import app
    entered, release = asyncio.Event(), asyncio.Event()
    async def hold(*args, **kwargs):
        entered.set()
        await release.wait()
        return SimpleNamespace(text_content='ordinary', pages=[])
    monkeypatch.setattr(app, '_convert_document', hold)
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        first = asyncio.create_task(client.post('/convert', files={'file': ('first.txt', b'first')}))
        await entered.wait()
        try:
            busy = await client.post('/convert-images?renderPages=true&pages=1',
                    headers={'X-Scoring-Deadline-Epoch-Millis': str(int((time.time() + 60) * 1000))},
                    files={'file': ('unread.pdf', b'invalid')})
            assert busy.status_code == 503
        finally:
            release.set()
            assert (await first).status_code == 200


@pytest.mark.asyncio
@pytest.mark.parametrize('cancel', [False, True])
async def test_image_supplement_reaps_worker_and_upload_then_accepts_next(tmp_path, monkeypatch, cancel):
    import app
    import conversion_execution
    import pymupdf
    execute = conversion_execution.execute_conversion
    pid_path = tmp_path / 'image-worker-pids'
    copies = []
    async def blocking_images(worker, args, deadline, request=None):
        assert worker.__name__ == '_safe_extract_images_worker'
        copies.append(args[0])
        assert os.path.exists(args[0])
        return await execute(_blocking_worker, (str(pid_path),), deadline, request)
    monkeypatch.setattr(conversion_execution, 'execute_conversion', blocking_images)
    pdf_path = tmp_path / 'page.pdf'
    with pymupdf.open() as document:
        document.new_page().insert_text((50, 50), 'page evidence')
        document.save(pdf_path)
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        task = asyncio.create_task(client.post('/convert-images?renderPages=true&pages=1',
                headers={'X-Scoring-Deadline-Epoch-Millis': str(int((time.time() + (30 if cancel else 7)) * 1000))},
                files={'file': ('page.pdf', pdf_path.read_bytes())}))
        try:
            if cancel:
                for _ in range(200):
                    if pid_path.exists() or task.done():
                        break
                    await asyncio.sleep(0.02)
                assert pid_path.exists(), 'supplement must reach isolated worker'
                task.cancel()
                with pytest.raises(asyncio.CancelledError):
                    await task
            else:
                assert (await task).status_code == 504
            assert pid_path.exists()
            assert not any(_alive(int(pid)) for pid in pid_path.read_text().split())
            assert copies and all(not os.path.exists(path) for path in copies)
            monkeypatch.setattr(conversion_execution, 'execute_conversion', execute)
            following = await client.post('/convert-images?renderPages=true&pages=1',
                    files={'file': ('next.pdf', pdf_path.read_bytes())})
            assert following.status_code == 200, following.text
            assert len(following.json()['images']) == 1
        finally:
            if not task.done():
                task.cancel()
                try:
                    await task
                except asyncio.CancelledError:
                    pass


@pytest.mark.asyncio
async def test_scoring_opt_in_json_and_expired_upload(tmp_path):
    import app
    from .test_scoring_extraction_v2 import _forty_one
    path = tmp_path / 'complete.docx'
    _forty_one(path, 'docx')
    headers = {'X-Scoring-Report-Version': '1', 'X-Pdf-Ocr': 'true',
               'X-Scoring-Deadline-Epoch-Millis': str(int((time.time() + 60) * 1000))}
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        response = await client.post('/convert', headers=headers,
                                     files={'file': ('complete.docx', path.read_bytes())})
        assert response.status_code == 200, response.text
        result = response.json()
        assert result['extraction']['coverageStatus'] == 'COMPLETE'
        assert result['extraction']['sourceUnitCount'] == 41
        assert 'CERTIFICATE ABC999' in result['markdown']
        assert result['sourceRefs'][-1]['mediaName']
        headers['X-Scoring-Deadline-Epoch-Millis'] = '1'
        response = await client.post('/convert', headers=headers,
                                     files={'file': ('expired.docx', b'invalid')})
        assert response.status_code == 504
        ordinary = await client.post('/convert', files={'file': ('ordinary.txt', b'ordinary body')})
        assert ordinary.status_code == 200
        assert ordinary.json()['markdown'] == 'ordinary body'
        assert 'extraction' not in ordinary.json()


@pytest.mark.asyncio
async def test_shared_capacity_rejects_before_second_worker_and_recovers(monkeypatch):
    import app
    entered = asyncio.Event()
    release = asyncio.Event()
    async def hold(*args, **kwargs):
        entered.set()
        await release.wait()
        return SimpleNamespace(text_content='ordinary', pages=[])
    monkeypatch.setattr(app, '_convert_document', hold)
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        first = asyncio.create_task(client.post('/convert', files={'file': ('first.txt', b'first')}))
        await entered.wait()
        headers = {'X-Scoring-Report-Version': '1', 'X-Pdf-Ocr': 'true',
                   'X-Scoring-Deadline-Epoch-Millis': str(int((time.time() + 60) * 1000))}
        busy = await client.post('/convert', headers=headers, files={'file': ('busy.pdf', b'busy')})
        assert busy.status_code == 503
        release.set()
        assert (await first).status_code == 200
        assert (await client.post('/convert', files={'file': ('next.txt', b'next')})).status_code == 200


@pytest.mark.asyncio
async def test_disconnect_kills_only_request_worker(tmp_path):
    from conversion_execution import execute_conversion
    pid_path = tmp_path / 'disconnected-pids'
    class Disconnected:
        async def is_disconnected(self):
            return pid_path.exists()
    with pytest.raises(HTTPException) as caught:
        await execute_conversion(_blocking_worker, (str(pid_path),), time.monotonic() + 10, Disconnected())
    assert caught.value.status_code == 499
    for _ in range(50):
        if not any(_alive(int(pid)) for pid in pid_path.read_text().split()):
            break
        await asyncio.sleep(0.02)
    assert not any(_alive(int(pid)) for pid in pid_path.read_text().split())


@pytest.mark.asyncio
async def test_upload_receive_is_part_of_original_deadline():
    from conversion_execution import ConversionAdmission
    called = []
    async def parsing_app(scope, receive, send):
        await receive()
        called.append('parsed')
    async def slow_receive():
        await asyncio.sleep(0.5)
        return {'type': 'http.request', 'body': b'late bytes', 'more_body': False}
    sent = []
    async def send(message):
        sent.append(message)
    scope = {'type': 'http', 'path': '/convert', 'headers': [
        (b'x-scoring-report-version', b'1'), (b'x-pdf-ocr', b'true'),
        (b'x-scoring-deadline-epoch-millis', str(int((time.time() + 5.1) * 1000)).encode())]}
    admission = ConversionAdmission(parsing_app)
    await admission(scope, slow_receive, send)
    assert sent[0]['status'] == 504
    assert called == []
    assert not admission.busy


@pytest.mark.asyncio
async def test_endpoint_timeout_removes_upload_copy(monkeypatch):
    import app
    copied = []
    async def timeout(path, **kwargs):
        copied.append(path)
        assert os.path.exists(path)
        raise HTTPException(status_code=504, detail='BID_OCR_TIMEOUT')
    monkeypatch.setattr(app, '_convert_document', timeout)
    headers = {'X-Scoring-Report-Version': '1', 'X-Pdf-Ocr': 'true',
               'X-Scoring-Deadline-Epoch-Millis': str(int((time.time() + 60) * 1000))}
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        response = await client.post('/convert', headers=headers, files={'file': ('timeout.pdf', b'data')})
    assert response.status_code == 504
    assert copied and all(not os.path.exists(path) for path in copied)


@pytest.mark.asyncio
async def test_cancellation_reaps_worker_without_touching_unrelated_process(tmp_path):
    from conversion_execution import execute_conversion
    pid_path = tmp_path / 'cancelled-pids'
    unrelated = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(30)'])
    task = asyncio.create_task(execute_conversion(
        _blocking_worker, (str(pid_path),), time.monotonic() + 10))
    try:
        for _ in range(200):
            if pid_path.exists():
                break
            await asyncio.sleep(0.02)
        assert pid_path.exists()
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert unrelated.poll() is None
        assert not any(_alive(int(pid)) for pid in pid_path.read_text().split())
    finally:
        if not task.done():
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
        unrelated.terminate()
        unrelated.wait(timeout=5)


@pytest.mark.asyncio
async def test_v2_http_missing_eocd_preserves_original_upload_hash(tmp_path):
    import app
    import hashlib
    from docx import Document
    path = tmp_path / 'missing-eocd.docx'
    document = Document()
    document.add_paragraph('NATIVE CONTRACT VALUE 72000000')
    document.save(path)
    original = path.read_bytes()
    truncated = original[:original.rfind(b'PK\x05\x06')]
    expected_hash = hashlib.sha256(truncated).hexdigest()
    assert expected_hash != hashlib.sha256(original).hexdigest()
    headers = {'X-Scoring-Report-Version': '1', 'X-Pdf-Ocr': 'true',
               'X-Scoring-Deadline-Epoch-Millis': str(int((time.time() + 60) * 1000))}
    async with AsyncClient(transport=ASGITransport(app=app.app), base_url='http://test') as client:
        response = await client.post('/convert', headers=headers,
                                     files={'file': ('missing-eocd.docx', truncated)})
    assert response.status_code == 200, response.text
    result = response.json()
    assert result['contentHash'] == expected_hash
    assert 'NATIVE CONTRACT VALUE 72000000' in result['markdown']
    assert result['extraction']['coverageStatus'] == 'COMPLETE'
