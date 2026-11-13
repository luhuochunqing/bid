"""One request-owned process group; no background queue survives cancellation."""
from __future__ import annotations

import asyncio
import multiprocessing
import os
import pickle
import secrets
import signal
import tempfile
import time

from fastapi import HTTPException
from fastapi.responses import JSONResponse


def _reap_on_termination(signum, frame):
    # SIGTERM was sent to the whole request group. Reap direct children before
    # exiting, so timeout does not leave a Tesseract zombie behind.
    while True:
        try:
            os.waitpid(-1, 0)
        except ChildProcessError:
            break
    raise SystemExit(128 + signum)


def _run_worker(worker, args, result_path):
    os.setsid()
    signal.signal(signal.SIGTERM, _reap_on_termination)
    with open(result_path, 'wb') as result_file:
        pickle.dump(worker(*args), result_file, protocol=pickle.HIGHEST_PROTOCOL)


def _stop_process_group(process):
    if process.pid is None:
        process.close()
        return
    # The request PID is its own process group. Killing that group also reaps
    # Tesseract, without touching another conversion or the server process.
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        if process.is_alive():
            process.terminate()
    process.join(timeout=0.5)
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    if process.is_alive():
        process.kill()
    process.join(timeout=1)
    process.close()


async def execute_conversion(worker, args, deadline, request=None):
    if time.monotonic() >= deadline:
        raise HTTPException(status_code=504, detail='BID_OCR_TIMEOUT')
    with tempfile.TemporaryDirectory(prefix='sidecar-convert-') as directory:
        result_path = os.path.join(directory, 'result.pickle')
        process = multiprocessing.get_context('spawn').Process(
            target=_run_worker, args=(worker, args, result_path))
        try:
            process.start()
            while process.is_alive():
                if time.monotonic() >= deadline:
                    raise HTTPException(status_code=504, detail='BID_OCR_TIMEOUT')
                if request is not None and await request.is_disconnected():
                    raise HTTPException(status_code=499, detail='CONVERSION_DISCONNECTED')
                await asyncio.sleep(0.02)
            process.join()
            if process.exitcode != 0 or not os.path.isfile(result_path):
                raise HTTPException(status_code=503, detail='文档转换服务繁忙，请稍后重试')
            if time.monotonic() >= deadline:
                raise HTTPException(status_code=504, detail='BID_OCR_TIMEOUT')
            if os.path.getsize(result_path) > 64 * 1024 * 1024:
                raise HTTPException(status_code=422, detail='SCORING_RESPONSE_TOO_LARGE')
            with open(result_path, 'rb') as result_file:
                return pickle.load(result_file)
        finally:
            _stop_process_group(process)


class ConversionAdmission:
    """Acquire capacity before multipart parsing, and retain the arrival deadline.

    FastAPI may already spool bytes at the network boundary. This admission gate
    prevents another conversion/copy, rather than claiming to undo uploaded bytes.
    """
    def __init__(self, app, shared_key=None):
        self.app = app
        self.shared_key = shared_key
        self.busy = False

    async def __call__(self, scope, receive, send):
        if scope['type'] != 'http' or scope.get('path') not in ('/convert', '/convert-stream', '/convert-images'):
            await self.app(scope, receive, send)
            return
        headers = dict(scope.get('headers', []))
        if self.shared_key and not secrets.compare_digest(
                headers.get(b'x-sidecar-key', b''), self.shared_key.encode()):
            await JSONResponse(status_code=401, content={'detail': '认证失败'})(scope, receive, send)
            return
        report = headers.get(b'x-scoring-report-version')
        deadline = time.monotonic() + 1800
        if report is not None:
            if report != b'1' or headers.get(b'x-pdf-ocr', b'').lower() != b'true':
                await JSONResponse(status_code=400, content={'detail': 'SCORING_PROTOCOL_INVALID'})(scope, receive, send)
                return
        if report is not None or (scope.get('path') == '/convert-images'
                                  and b'x-scoring-deadline-epoch-millis' in headers):
            try:
                epoch = int(headers.get(b'x-scoring-deadline-epoch-millis', b'')) / 1000
                remaining = min(1800, epoch - time.time() - 5)
            except (ValueError, OverflowError):
                await JSONResponse(status_code=400, content={'detail': 'SCORING_DEADLINE_REQUIRED'})(scope, receive, send)
                return
            if remaining <= 0:
                await JSONResponse(status_code=504, content={'detail': 'BID_OCR_TIMEOUT'})(scope, receive, send)
                return
            deadline = time.monotonic() + remaining
        if self.busy:
            await JSONResponse(status_code=503, content={'detail': '文档转换服务繁忙，请稍后重试'})(scope, receive, send)
            return
        self.busy = True
        scope.setdefault('state', {})['conversion_deadline'] = deadline
        response_started = False
        async def bounded_receive():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError('BID_OCR_TIMEOUT')
            return await asyncio.wait_for(receive(), remaining)
        async def tracked_send(message):
            nonlocal response_started
            if message['type'] == 'http.response.start':
                response_started = True
            await send(message)
        try:
            await self.app(scope, bounded_receive, tracked_send)
        except TimeoutError:
            if not response_started:
                await JSONResponse(status_code=504, content={'detail': 'BID_OCR_TIMEOUT'})(scope, receive, send)
        finally:
            self.busy = False
