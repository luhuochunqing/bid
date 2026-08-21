# API Contract: Sidecar /convert

## 请求与响应契约
- 端点：`POST /convert`
- Content-Type: `multipart/form-data`
- 请求字段：`file: UploadFile`
- 响应结构：
  ```json
  {
    "documentId": "string",
    "markdown": "string",
    "sections": [{"title": "string", "level": 1, "startChar": 0, "endChar": 100}],
    "tables": [],
    "warnings": [],
    "converter": "markitdown",
    "contentHash": "sha256 hex string"
  }
  ```
- 异常行为：
  - 文件超限（>512MB）：HTTP 413
  - 格式不支持/损坏：HTTP 422
  - Worker 崩溃/超载：HTTP 503
