from __future__ import annotations

from contextlib import contextmanager
import logging
import os
import struct
import tempfile
import zipfile
import zlib

logger = logging.getLogger(__name__)

# Default maximum file size eligible for repair (64MB) to prevent OOM
DEFAULT_MAX_REPAIR_SIZE = 64 * 1024 * 1024
# Maximum uncompressed chunk per decompress() call (1MB) to prevent decompression memory spikes
MAX_DECOMP_CHUNK = 1024 * 1024
# Maximum allowable uncompressed entry size (256MB) to protect against zip bombs
DEFAULT_MAX_UNCOMPRESSED_ENTRY_SIZE = 256 * 1024 * 1024


def repair_truncated_docx(path: str, max_size_bytes: int | None = None) -> str | None:
    """Attempt to repair a .docx file missing its End of Central Directory (EOCD).

    Memory-safe, streaming implementation:
    - Scans Local File Headers via disk seeks without loading the entire file into memory.
    - Streams payload to a temporary file in 64KB blocks.
    - Accurately resolves General Purpose Bit 3 / Data Descriptors using zlib raw deflate
      stream termination with bounded 1MB output chunks and unconsumed_tail handling,
      supporting both with-signature ('PK\\x07\\x08') and without-signature descriptors.
    - Strongly validates the repaired zip with testzip() and non-buffering word/document.xml
      checks without reading the entire XML into memory.
    - Returns None if repair is impossible or file is truncated inside 'word/document.xml'.
    """
    if max_size_bytes is None:
        max_size_bytes = int(os.getenv("DOCX_MAX_REPAIR_SIZE_BYTES", DEFAULT_MAX_REPAIR_SIZE))

    try:
        file_size = os.path.getsize(path)
    except OSError:
        return None

    if file_size == 0 or file_size > max_size_bytes:
        if file_size > max_size_bytes:
            logger.warning(
                "Docx repair skipped for %s: file size (%d bytes) exceeds limit (%d bytes)",
                path, file_size, max_size_bytes,
            )
        return None

    try:
        with open(path, "rb") as f:
            if f.read(4) != b"PK\x03\x04":
                return None
            f.seek(0)

            cd_entries: list[bytes] = []
            found_doc_xml = False
            last_valid_pos = 0
            max_uncomp_entry_size = int(
                os.getenv("DOCX_MAX_UNCOMPRESSED_ENTRY_SIZE", DEFAULT_MAX_UNCOMPRESSED_ENTRY_SIZE)
            )

            while f.tell() < file_size:
                entry_offset = f.tell()
                sig = f.read(4)
                if sig != b"PK\x03\x04":
                    break

                header_bytes = f.read(26)
                if len(header_bytes) < 26:
                    break

                version = header_bytes[0:2]
                flags = header_bytes[2:4]
                method = header_bytes[4:6]
                mtime = header_bytes[6:8]
                mdate = header_bytes[8:10]
                crc = header_bytes[10:14]
                comp_size = int.from_bytes(header_bytes[14:18], "little")
                uncomp_size = int.from_bytes(header_bytes[18:22], "little")
                fn_len = int.from_bytes(header_bytes[22:24], "little")
                extra_len = int.from_bytes(header_bytes[24:26], "little")

                if f.tell() + fn_len + extra_len > file_size:
                    break

                fn = f.read(fn_len)
                if extra_len > 0:
                    f.seek(extra_len, os.SEEK_CUR)

                is_doc_xml = b"word/document.xml" in fn
                data_start = f.tell()

                flag_val = int.from_bytes(flags, "little")
                has_descriptor = (flag_val & 0x0008) != 0
                method_num = int.from_bytes(method, "little")

                if has_descriptor and comp_size == 0:
                    # Streaming Data Descriptor case
                    if method_num != 8:
                        logger.debug("Unsupported streaming compression method %d in %s", method_num, fn)
                        break

                    dobj = zlib.decompressobj(-15)
                    crc_calc = 0
                    uncomp_calc = 0
                    decomp_failed = False

                    while not dobj.eof:
                        remaining_file = file_size - f.tell()
                        if remaining_file <= 0 and not dobj.unconsumed_tail:
                            break
                        chunk = f.read(min(65536, remaining_file)) if remaining_file > 0 else b""
                        if not chunk and not dobj.unconsumed_tail:
                            break

                        data = chunk
                        while data or dobj.unconsumed_tail:
                            try:
                                decomp = dobj.decompress(data, MAX_DECOMP_CHUNK)
                                data = dobj.unconsumed_tail
                                if decomp:
                                    uncomp_calc += len(decomp)
                                    crc_calc = zlib.crc32(decomp, crc_calc)
                                    if uncomp_calc > max_uncomp_entry_size:
                                        logger.warning(
                                            "Uncompressed entry %s exceeds safety limit %d",
                                            fn, max_uncomp_entry_size,
                                        )
                                        decomp_failed = True
                                        break
                                if not decomp and not dobj.unconsumed_tail:
                                    break
                            except zlib.error:
                                decomp_failed = True
                                break

                        if decomp_failed:
                            break

                    if decomp_failed or not dobj.eof:
                        logger.debug("Deflate stream truncated or failed in %s", fn)
                        break

                    actual_comp_size = f.tell() - data_start - len(dobj.unused_data)
                    f.seek(data_start + actual_comp_size)

                    desc_peek = f.read(16)
                    desc_len = 0
                    desc_crc = 0
                    if desc_peek.startswith(b"PK\x07\x08") and len(desc_peek) == 16:
                        desc_crc, _, _ = struct.unpack("<III", desc_peek[4:16])
                        desc_len = 16
                    elif len(desc_peek) >= 12:
                        desc_crc, _, _ = struct.unpack("<III", desc_peek[:12])
                        desc_len = 12
                    else:
                        break

                    if desc_crc != crc_calc:
                        logger.debug("CRC mismatch in data descriptor for %s: expected %d, got %d", fn, desc_crc, crc_calc)
                        break

                    crc = struct.pack("<I", desc_crc)
                    comp_size = actual_comp_size
                    uncomp_size = uncomp_calc
                    f.seek(data_start + actual_comp_size + desc_len)
                else:
                    # Standard entry with known comp_size
                    if data_start + comp_size > file_size:
                        logger.debug("Payload truncated for %s: needed %d, file ended at %d", fn, data_start + comp_size, file_size)
                        break
                    f.seek(data_start + comp_size)
                    if has_descriptor:
                        sig_peek = f.read(4)
                        if sig_peek == b"PK\x07\x08":
                            f.seek(12, os.SEEK_CUR)
                        else:
                            f.seek(-len(sig_peek), os.SEEK_CUR)

                # Only mark document.xml as found once its entire payload is confirmed valid
                if is_doc_xml:
                    found_doc_xml = True

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
                    + b"\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00\x00"
                    + struct.pack("<I", entry_offset)
                    + fn
                )
                cd_entries.append(cd_header)
                last_valid_pos = f.tell()

            if not found_doc_xml or not cd_entries:
                return None

            cd_data = b"".join(cd_entries)
            cd_offset = last_valid_pos
            eocd = (
                b"PK\x05\x06"
                + b"\x00\x00\x00\x00"
                + struct.pack("<HHII", len(cd_entries), len(cd_entries), len(cd_data), cd_offset)
                + b"\x00\x00"
            )

            # Stream payload up to cd_offset into temporary file without buffering full file in RAM
            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".docx")
            try:
                f.seek(0)
                bytes_left = cd_offset
                while bytes_left > 0:
                    chunk = f.read(min(65536, bytes_left))
                    if not chunk:
                        break
                    tmp.write(chunk)
                    bytes_left -= len(chunk)
                tmp.write(cd_data)
                tmp.write(eocd)
                tmp.flush()
                tmp.close()
            except Exception:
                tmp.close()
                if os.path.exists(tmp.name):
                    os.remove(tmp.name)
                raise

            # Strong validation: testzip() and non-buffering word/document.xml checks
            try:
                with zipfile.ZipFile(tmp.name) as z:
                    corrupt = z.testzip()
                    if corrupt is not None:
                        logger.warning("Repaired docx failed testzip on member: %s", corrupt)
                        os.remove(tmp.name)
                        return None
                    info = z.getinfo("word/document.xml")
                    if info.file_size <= 0:
                        logger.warning("Repaired docx has empty word/document.xml")
                        os.remove(tmp.name)
                        return None
                    with z.open("word/document.xml") as doc_stream:
                        if not doc_stream.read(1):
                            logger.warning("Repaired docx word/document.xml has no readable bytes")
                            os.remove(tmp.name)
                            return None
            except Exception as exc:
                logger.warning("Failed validating repaired docx %s: %s", tmp.name, exc)
                if os.path.exists(tmp.name):
                    os.remove(tmp.name)
                return None

            return tmp.name
    except Exception as exc:
        logger.warning("Unexpected error during docx repair of %s: %s", path, exc)
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
