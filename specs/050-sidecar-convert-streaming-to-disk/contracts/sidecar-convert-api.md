# API Contract: Sidecar /convert Endpoint

## POST /convert

将上传的 Office / PDF 文档转换为 Markdown 文本并返回结构化分节与哈希。

### Headers
- `Content-Type`: `multipart/form-data; boundary=...`
- `X-Sidecar-Key`: `string` (可选，当服务端配置 `SIDECAR_SHARED_KEY` 时必填)

### Form Data
- `file`: 二进制文件流 (支持 .docx, .doc, .pdf, .pptx, .xlsx 等)

### Response 200 (application/json)
```json
{
  "documentId": "张家口银行——投标6.16.docx",
  "markdown": "# 投标文件正文...",
  "sections": [
    {
      "title": "第一章 投标函",
      "level": 1,
      "characterOffset": 0
    }
  ],
  "tables": [],
  "warnings": [],
  "converter": "markitdown",
  "contentHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

### Error Responses
- `400 Bad Request`: `{"detail": "未提供文件名"}`
- `401 Unauthorized`: `{"detail": "认证失败"}`
- `413 Request Entity Too Large`: `{"detail": "请求体过大"}` (超限默认 512MB)
- `422 Unprocessable Entity`: `{"error": "文档无法解析：格式不支持或已损坏"}`
- `500 Internal Server Error`: `{"detail": "文档转换失败"}`
