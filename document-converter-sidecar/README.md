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
| `SIDECAR_MAX_UPLOAD_MB` | `30` | Maximum accepted upload size in megabytes. Requests exceeding this limit receive HTTP 413. Matches the Java-side `MarkItDownSidecarTextExtractor` limit. |

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
docker run -p 8000:8000 \
  -e SIDECAR_SHARED_KEY=your-secret-here \
  document-converter-sidecar
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
