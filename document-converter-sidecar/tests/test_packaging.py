"""Import the actual Docker COPY source set in an isolated directory."""
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

import pytest


@pytest.mark.parametrize('dockerfile', ['Dockerfile', 'Dockerfile.cn'])
def test_packaged_source_set_imports_and_runs_scoring_v2(tmp_path, dockerfile):
    source = Path(__file__).resolve().parents[1]
    for line in (source / dockerfile).read_text().splitlines():
        if not line.strip().startswith('COPY '):
            continue
        arguments = shlex.split(line)
        for name in arguments[1:-1]:
            if name.endswith('.py'):
                shutil.copy2(source / name, tmp_path / name)
    smoke = '''
from pathlib import Path
import tempfile
from docx import Document
import app
import scoring_extraction
assert Path(app.__file__).parent == Path.cwd()
with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / 'native.docx'
    document = Document()
    document.add_paragraph('PACKAGED CONTRACT VALUE 72000000')
    document.save(path)
    result = app._convert_worker(str(path), pdf_ocr=True, scoring_report=True)
    assert 'PACKAGED CONTRACT VALUE 72000000' in result.text_content
    assert result.extraction['coverageStatus'] == 'COMPLETE'
'''
    completed = subprocess.run([sys.executable, '-c', smoke], cwd=tmp_path,
                               env={**os.environ, 'PYTHONPATH': str(tmp_path),
                                    'PYTHONDONTWRITEBYTECODE': '1'},
                               capture_output=True, text=True, timeout=15)
    assert completed.returncode == 0, completed.stderr
