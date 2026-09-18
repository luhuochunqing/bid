from __future__ import annotations

from contextlib import contextmanager
import io
import logging
import os
import struct
import tempfile
import zipfile

logger = logging.getLogger(__name__)


def repair_truncated_docx(path: str) -> str | None:
    """Attempt to repair a .docx file missing its End of Central Directory (EOCD).

    If the file was truncated during upload or is missing its central directory,
    but local file headers (especially 'word/document.xml') are present, this function
    rebuilds the central directory and EOCD and writes a valid zip file to a temp file.
    Returns the path to the repaired temp file, or None if repair is not possible.
    """
    try:
        with open(path, "rb") as f:
            data = f.read()
    except Exception:
        return None

    if not data or not data.startswith(b"PK\x03\x04"):
        return None

    pos = 0
    cd_entries = []
    found_doc_xml = False
    data_len = len(data)

    while pos < data_len:
        idx = data.find(b"PK\x03\x04", pos)
        if idx == -1 or idx + 30 > data_len:
            break

        version = data[idx + 4 : idx + 6]
        flags = data[idx + 6 : idx + 8]
        method = data[idx + 8 : idx + 10]
        mtime = data[idx + 10 : idx + 12]
        mdate = data[idx + 12 : idx + 14]
        crc = data[idx + 14 : idx + 18]
        comp_size = int.from_bytes(data[idx + 18 : idx + 22], "little")
        uncomp_size = int.from_bytes(data[idx + 22 : idx + 26], "little")
        fn_len = int.from_bytes(data[idx + 26 : idx + 28], "little")
        extra_len = int.from_bytes(data[idx + 28 : idx + 30], "little")

        if idx + 30 + fn_len > data_len:
            break
        fn = data[idx + 30 : idx + 30 + fn_len]
        if b"word/document.xml" in fn:
            found_doc_xml = True

        data_start = idx + 30 + fn_len + extra_len

        # Handle ZIP streaming Data Descriptor (Bit 3 flag) when comp_size == 0
        flag_val = int.from_bytes(flags, "little")
        has_data_descriptor = (flag_val & 0x0008) != 0
        if has_data_descriptor and comp_size == 0:
            next_pk = data.find(b"PK", data_start)
            if next_pk != -1:
                comp_size = next_pk - data_start
                if data[next_pk : next_pk + 4] == b"PK\x07\x08" and next_pk + 16 <= data_len:
                    crc = data[next_pk + 4 : next_pk + 8]
                    comp_size = int.from_bytes(data[next_pk + 8 : next_pk + 12], "little")
                    uncomp_size = int.from_bytes(data[next_pk + 12 : next_pk + 16], "little")
                    pos = next_pk + 16
                else:
                    pos = next_pk
            else:
                comp_size = max(0, data_len - data_start)
                pos = data_len
        else:
            pos = data_start + comp_size

        cd_header = (
            b"PK\x01\x02"
            + version
            + version
            + flags
            + method
            + mtime
            + mdate
            + crc
            + struct.pack("<I", comp_size)
            + struct.pack("<I", uncomp_size)
            + struct.pack("<H", fn_len)
            + b"\x00\x00\x00\x00\x00\x00\x00\x00"
            + b"\x00\x00\x00\x00"
            + struct.pack("<I", idx)
            + fn
        )
        cd_entries.append(cd_header)

    if not found_doc_xml or not cd_entries:
        return None

    cd_data = b"".join(cd_entries)
    cd_offset = pos
    eocd = (
        b"PK\x05\x06"
        + b"\x00\x00\x00\x00"
        + struct.pack("<HHII", len(cd_entries), len(cd_entries), len(cd_data), cd_offset)
        + b"\x00\x00"
    )

    rebuilt_data = data[:cd_offset] + cd_data + eocd

    try:
        with zipfile.ZipFile(io.BytesIO(rebuilt_data)) as z:
            if "word/document.xml" not in z.namelist():
                return None
    except Exception:
        return None

    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=".docx") as tmp:
            tmp.write(rebuilt_data)
            return tmp.name
    except Exception:
        return None


@contextmanager
def auto_repaired_docx(path: str):
    """Context manager providing a valid docx path, repairing missing EOCD if needed.

    If `path` is a valid zip/docx, yields `path` directly (zero overhead).
    If `path` is truncated/damaged and repairable, creates a temporary repaired file,
    yields its path, and guarantees removal on context exit.
    If repair is impossible, yields `path` unchanged.
    """
    repaired_path = None
    target_path = path

    if path.lower().endswith(".docx") and not zipfile.is_zipfile(path):
        repaired_path = repair_truncated_docx(path)
        if repaired_path:
            logger.info("Auto-repaired truncated docx with missing EOCD: %s -> %s", path, repaired_path)
            target_path = repaired_path

    try:
        yield target_path
    finally:
        if repaired_path and os.path.exists(repaired_path):
            try:
                os.remove(repaired_path)
            except OSError:
                pass
