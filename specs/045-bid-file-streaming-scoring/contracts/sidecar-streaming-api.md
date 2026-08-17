# Contract: markitdown sidecar 流式转换接口

**Feature**: 045-bid-file-streaming-scoring | **Date**: 2026-08-17

> sidecar（document-converter-sidecar, Python/FastAPI）对外暴露的新接口。现有 `POST /convert`（docinsight 在用）与 `GET /health` **不动**。

## POST /convert-stream

流式上传文件 → 流式返回提取的纯文本（markdown）。

### 请求

```
POST /convert-stream HTTP/1.1
Content-Type: multipart/form-data; boundary=...
X-Sidecar-Key: <共享密钥>          # 复用现有认证头；dev 模式可关闭（与 /convert 一致）
```

- multipart 单字段 `file`，**chunked 传输编码**（Java 端以 `FileSystemResource` 流式发送 body，contentLength 已知时以 Content-Length 发送）。
- 认证失败：`401`（与现有 /convert 行为一致）。
- 大小上限：`SIDECAR_MAX_UPLOAD_MB`（默认调至 2048，对齐后端单文件配额 2GB）；流式读循环中累计超限即中止连接（复用现有 64KB chunk + 413 逻辑骨架）。

### 响应（成功）

```
HTTP/1.1 200 OK
Content-Type: text/plain; charset=utf-8
Transfer-Encoding: chunked
```

- Body：提取的 markdown 纯文本，**流式写出**（FastAPI `StreamingResponse`），不包装 JSON、不分页、无元数据头。
- 文档无文本层（如纯扫描 PDF）→ 返回空 body + `200`（调用方按现有「解析失败/无正文」语义处理，spec Edge Case）。

### 响应（失败）

| 状态码 | 场景 | Body |
|---|---|---|
| `401` | 密钥校验失败 | 现有错误 JSON |
| `413` | 上传超 `SIDECAR_MAX_UPLOAD_MB` | 现有错误 JSON（连接可中止） |
| `422` | markitdown 无法识别/解析该文件 | `{"error": "<原因摘要>"}` |

### Java 侧消费契约

- 调用方：scoreparse 模块新增的流式提取客户端（非 docinsight 的 `MarkItDownSidecarExtractor`——后者继续用 /convert）。
- 读响应：流式读 + **char 计数**，累计 > `max-text-chars`（默认 5,000,000）即中止连接 → 上层转 `OversizedBidTextException`（FR-004）。**不得**先缓冲完整文本再判。
- 读满即止后正常关闭连接；已读部分不使用（明确拒绝语义，spec US3）。

### 非目标（显式排除）

- 不返回 sections/tables/warnings 元数据（打分链路不需要；需要元数据的走 /convert）。
- 不支持文件路径输入（research R2：部署拓扑耦合，已否决）。
- 不做 sidecar 侧文本量截断/判断（上限判断唯一归属 Java 调用方，保持 sidecar 无业务策略）。

## GET /health（现有，不变）

liveness 探针；Java 侧流式提取客户端在调用前可复用其做可用性预检（失败走降级语义，具体降级行为在 tasks 阶段定，spec FR-010）。
