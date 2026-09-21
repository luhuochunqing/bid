# Contract: sidecar 图片抽取接口

**Feature**: 048-score-parse-image-multimodal | **Date**: 2026-08-18

> sidecar（document-converter-sidecar, Python/FastAPI）新增图片抽取端点。现有 `POST /convert`、
> `POST /convert-stream`、`GET /health` **不动**（科研 R2/D2）。

## POST /convert-images

流式上传文件 → 返回 JSON（图片清单 + base64 + 章节/页码归属 + 截断信号）。

### 请求

```
POST /convert-images HTTP/1.1
Content-Type: multipart/form-data; boundary=...
X-Sidecar-Key: <共享密钥>            # 复用现有认证；dev 可关闭（与 /convert 一致）
```

- multipart 单字段 `file`，流式读取（复用 /convert-stream 的 64KB chunk + 413 size guard）。
- 大小上限：`SIDECAR_MAX_UPLOAD_MB`（与 /convert-stream 一致）。

### 响应（成功）

```
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
```

```json
{
  "images": [
    {
      "index": 0,
      "mimeType": "image/png",
      "section": "3.2 业绩要求",
      "pageHint": null,
      "width": 800,
      "height": 600,
      "base64": "iVBORw0KGgoAAAANSUhEUg..."
    }
  ],
  "truncated": false,
  "warnings": []
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `index` | int | 图片序号（0 起，文档顺序稳定） |
| `mimeType` | string | `image/png`\|`image/jpeg`\|`image/gif`\|`image/webp` |
| `section` | string\|null | 章节标题；PDF 无此概念时可为 null（用 `pageHint`） |
| `pageHint` | int\|null | PDF 页码句柄，其它格式 null |
| `width`/`height` | int | 像素尺寸（供 token 估算） |
| `base64` | string | 图像裸 base64（**不含** `data:` 前缀） |
| `truncated` | bool | 抽图遇单文件总图预算截断时为 true |
| `warnings` | string[] | 降级说明（如"图片预算截断"、"扫描件无章节归属"） |

### 抽取类型分派
| 类型 | 库 | section 归属 | pageHint |
|---|---|---|---|
| .docx | python-docx | 图所在段前最近标题 | null |
| .pptx | python-pptx | 所在 slide 标题 | null |
| .pdf | PyMuPDF(fitz) | **有文本层**：页码→最近标题映射回填；**纯扫描无文本层**：null（warnings 明示"扫描件无章节归属"） | 所在页页码；纯扫描 PDF 整页转为一张图 |
| 其它 | — | 返回 `images:[]`，走纯文本 | — |

### 预算控制
- 抽图设单文件总图字节预算 `SIDECAR_MAX_IMAGE_BYTES`（默认 `15728640` = 15MB）。累计抽取字节超限即停止，
  返回 `truncated:true` + 已抽图片。Java 侧配给（`ImageAssigner`）再做二次裁剪（research R4）。
- 默认值按 base64 ×4/3 膨胀与 Java 响应上限 25MB（`max-extract-response-bytes`）校准：15MB 二进制 ≈ 20MB
  base64，留 5MB 余量；上调任一侧须同步校验另一侧（review H1）。
- **内存预算（2026-09-21 项目 1382 OOM 修复）**：响应预算只裁剪返回体，不约束抽取期间的驻留内存，故另设两道
  峰值护栏：
  - `.docx`：`SIDECAR_MAX_DOCX_MEDIA_BYTES`（默认 `67108864` = 64MB）。抽取前读 zip 中央目录统计
    `word/media/` **未压缩**总量，超预算**不进入 markitdown**，直接返回 `images:[]` + warning
    （mammoth 会把每张内嵌图 base64 内联进 HTML，实测峰值 ≈ 媒体体积的 14.7 倍，4GB 容器下 300MB 级媒体即可 OOM）；
    同时在 `/convert-images` 内幂等绕过 markitdown `pre_process_docx`（与文本 worker 同款护栏）。
  - `.pdf`（`renderPages=true` 与内嵌图两条分支）：`SIDECAR_MAX_SOURCE_IMAGE_BYTES`（默认 64MB）累计源图
    字节超限即停止抽取并附 warning，峰值不随总页数增长。
  - 两个预算的降级均为**有信号降级**：warning 落 sidecar 日志；Java 侧抽到空图时
    `SidecarComplianceDocumentAdapter.withPageImages` 显式 WARN，签章视觉核验按既有语义转人工复核。

### 响应（失败）
| 状态码 | 场景 | Body |
|---|---|---|
| `401` | 密钥校验失败 | 现有错误 JSON |
| `413` | 上传超 `SIDECAR_MAX_UPLOAD_MB` | 现有错误 JSON |
| `422` | 无法解析（与 /convert-stream 语义一致） | `{"error": "<原因>"}` |

### Java 侧消费契约
- 调用方：scoreparse 新增 `SidecarImageExtractor`（复用 `app.score-parse.sidecar-url` 与 `SIDECAR_SHARED_KEY`）。
- 请求：OnDisk 临时文件流式上传（`HttpRequest.BodyPublishers.ofFile`，同 `SidecarStreamTextExtractor`）。
- 响应：JSON 整包读；**整包字节上限** `app.score-parse.multimodal.max-extract-response-bytes`（默认如 25MB），
  超限即抛 → 调用层该评分项降级纯文本（FR-005）。逐项防御式跳过非法 `mimeType`/空 `base64`（Principle VII）。

### 非目标
- 不做"两阶段抽图/按需取图"（research D3），一次性返回清单 + 预算截断。
- 不落库（research D6），图片生命周期随打分任务内存。
- 不为 RBAC 建模：图片归属投标文件既有安全域，不外泄。