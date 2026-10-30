# Document Converter Sidecar

Lightweight FastAPI service that converts PDF and Office documents to Markdown.
Called internally by the Java backend (`MarkItDownSidecarTextExtractor`) — not
exposed to end-users directly.

## Purpose

The Java backend cannot natively extract rich text from binary formats such as
`.pdf`, `.docx`, `.doc`, `.pptx`, `.xlsx`.  
This sidecar accepts a multipart file upload, passes it through
[markitdown](https://github.com/microsoft/markitdown), and returns structured
JSON containing the full Markdown text plus section/heading metadata.

---

## Local run

```bash
# 1. Create and activate a virtual environment
python -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate

# 2. Install runtime dependencies
pip install -r requirements.txt

# 3. Start the server (development — auto-reload)
uvicorn app:app --reload --host 127.0.0.1 --port 8000
```

The service will be available at `http://127.0.0.1:8000`.

---

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `SIDECAR_HOST` | `127.0.0.1` | Bind address for the uvicorn server |
| `SIDECAR_PORT` | `8000` | Bind port for the uvicorn server |
| `SIDECAR_SHARED_KEY` | *(unset)* | Shared secret enforced via `X-Sidecar-Key` request header. **Must be set in production.** When unset a warning is logged and auth is skipped (dev convenience). |
| `SIDECAR_MAX_UPLOAD_MB` | `1024` | Maximum accepted upload size in megabytes. Requests exceeding this limit receive HTTP 413. Aligned with the Java-side `app.score-parse.bid-file.max-file-bytes` (StreamingBidFileFetcher); production may explicitly raise both values together (currently 3072MB). |
| `SIDECAR_MAX_CONVERSIONS_PER_WORKER` | `16` | Maximum conversions a worker process handles before it is recycled (`ProcessPoolExecutor.max_tasks_per_child`). Bounds per-worker RSS growth; after this many `/convert` or `/convert-stream` jobs the worker is replaced so memory returns to the OS. Worker OOM still returns HTTP 503 and the pool is rebuilt automatically. |
| `SIDECAR_PDF_CHUNK_PAGES` | `100` | Pages per open/close chunk for PDF conversion (project 1373 root fix: whole-document load OOM-killed the worker at ~2.98GB RSS on a 428MB PDF; spec 066 raised the default 10→100 because each `pdfplumber.open` costs ~2.5s on a 441MB PDF and 411 reopens dominated 90% of a 1133s conversion). Peak stays O(chunk) regardless of page count (measured 182MB @100 pages / 238MB @400 pages on a synthetic heavy fixture vs 734MB whole-document); output is byte-identical to whole-document conversion. |
| `SIDECAR_MAX_IMAGE_BYTES` | `15728640` (15MB) | Cumulative extracted-image byte budget for `POST /convert-images`. Extraction stops early with `truncated=true` and keeps what was already collected. Calibrated against the Java-side `max-extract-response-bytes` of 25MB (15MB binary ≈ 20MB after base64 ×4/3); bump either side only in sync (spec 048). |
| `SIDECAR_MAX_DOCX_MEDIA_BYTES` | `67108864` (64MB) | Peak-memory guard for `.docx` image extraction (project 1382 OOM fix). The uncompressed `word/media/` total is read from the zip central directory **before** MarkItDown runs; over budget returns `images:[]` plus a warning instead of materializing the media (mammoth inlines every embedded image as base64 — measured peak ≈ 14.7× media size, so a 300MB-media docx OOM-kills the 4GB container). In-budget documents keep the previous behaviour and `pre_process_docx` is bypassed on this path too. |
| `SIDECAR_MAX_SOURCE_IMAGE_BYTES` | `67108864` (64MB) | Peak-memory guard for the PDF branches of `POST /convert-images` (page rendering and embedded-image extraction): accumulation stops with a warning once the raw bytes exceed the budget, so residency does not grow with page count. The response-level budget above still governs what is returned. |

仓库级 `scripts/dev-services.sh` 会在本地运行时生成 `.runtime/dev-services/sidecar.shared-key`，
并同时注入 sidecar 与 Java 后端；不要把该密钥写入源码或提交到 Git。

---

## API contract

### `POST /convert`

Convert a document to Markdown.

**Request** — `multipart/form-data`

| Field | Type | Required | Description |
|---|---|---|---|
| `file` | binary | yes | The document to convert (PDF, DOCX, DOC, PPTX, XLSX, …) |

**Headers**

| Header | Required | Description |
|---|---|---|
| `X-Sidecar-Key` | When `SIDECAR_SHARED_KEY` is set | Shared secret for authentication |

**Response** — `application/json`

```jsonc
{
  "documentId": "tender.pdf",          // original filename
  "markdown": "# Heading\n\nBody…",   // full converted text
  "sections": [                         // heading tree
    {
      "heading": "Heading",
      "level": 1,
      "charStart": 0,
      "charEnd": 42,
      "path": ["Heading"]
    }
  ],
  "pages": [                            // physical-page slices (PDF via pdfplumber; [] for docx).
    {                                   // consumed by the compliance quality-check pipeline
      "pageNumber": 1,                  // (SidecarStreamDocumentAdapter); 1-based, blanks skipped
      "text": "# Heading\n\nBody of page 1…"
    }
  ],
  "tables": [],                         // reserved; table extraction is on the Java side
  "warnings": [],                       // e.g. ["low_text_density"]
  "converter": "markitdown",
  "contentHash": "<sha256-hex>"        // SHA-256 of the raw uploaded bytes
}
```

**Error responses**

| Status | `detail` | Cause |
|---|---|---|
| 400 | `未提供文件名` | `file` field has no filename |
| 401 | `认证失败` | `X-Sidecar-Key` missing or wrong when key is configured |
| 413 | `请求体过大` | Upload exceeds `SIDECAR_MAX_UPLOAD_MB` |
| 500 | `文档转换失败` | Internal conversion error (details logged server-side only) |
| 503 | `文档转换服务繁忙，请稍后重试` | Conversion worker process died (likely OOM); the pool is recycled automatically and the next request starts a fresh worker. Also returned by `/convert-stream`. |

---

### `GET /health`

Liveness probe.

```json
{"status": "up"}
```

---

## Running tests

```bash
# Install dev dependencies into the same venv
pip install -r requirements-dev.txt

# Run all tests
pytest tests/ -v
```

---

## Docker

```bash
# Build
docker build -t document-converter-sidecar .

# Run (production — supply a real key)
# --memory flags are REQUIRED: the converter accumulates memory across large
# documents and can starve the host, OOM-killing the backend JVM
# (incident 2026-08-21 on test). Use 2g in production, 1g on smaller test hosts.
docker run -p 8000:8000 \
  --memory=2g --memory-swap=2g \
  -e SIDECAR_SHARED_KEY=your-secret-here \
  document-converter-sidecar
```

Apply the limit to an already-running container (lost if the container is
recreated — always re-check after rebuilds):

```bash
docker update --memory=2g --memory-swap=2g xiyu-sidecar
```

---

## Troubleshooting

### `markitdown` fails to import
Ensure all extras are installed: `pip install "markitdown[all]"`. The `[all]`
extra includes support for PDF (pdfminer), PowerPoint, and image OCR.

### PDF extraction produces very little text
If `warnings` contains `low_text_density`, the PDF is likely scanned (image-only).
Install [Tesseract OCR](https://tesseract-ocr.github.io/tessdoc/Installation.html)
and the `pytesseract` Python package, then re-run. `markitdown` will attempt OCR
automatically when Tesseract is on `PATH`.

### Legacy `.doc` files not converting
The sidecar uses macOS `textutil` to pre-convert `.doc` → `.docx` before passing
to markitdown. On Linux, install `libreoffice` and update the subprocess call in
`app.py` accordingly (flagged as a future improvement).

### `SIDECAR_SHARED_KEY is not set` warning at startup
Expected in local development. Set the env var for any environment reachable over
a network.

PDF scoring tables with repeated page headers are linked only when adjacent source pages, a shared named section, a continued rule row and consecutive item numbers provide evidence of the same table. Each cell keeps its original page and coordinates; closed, ambiguous and unrelated tables remain separate.
