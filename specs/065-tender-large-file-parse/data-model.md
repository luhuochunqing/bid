# Data Model: 招标文件大文件解析放行

**Feature**: `specs/065-tender-large-file-parse` | **Date**: 2026-09-18

> 本特性不改表、无 Flyway 迁移。以下为涉及的既有/新增领域概念（内存态 + 配置态）。

## TenderIntake（既有，增强失败分类）

取正文结果二态：有源（含 `TenderTextSource`：文件名/文件 URL/正文）或无源（含 `emptyReason` 400/FAILED 文案）。

| 字段 | 含义 | 本特性变化 |
|---|---|---|
| `source` | 可用正文来源（立项文件或历史底稿） | 不变；超大文件 + 有可用底稿仍回退底稿 |
| `emptyReason` | 无源原因（三选一） | **变化**：`tooLarge` 分支从“招标文件超过 50MB，无法解析”（字节）改为新文本量文案；“请先在立项阶段上传招标文件”（无文件）与读取失败保持 |

状态机：`found → 解析`；`unavailable(无文件) → FAILED + 400 无文件文案`；`unavailable(文本超限) → FAILED + 400 文本量文案`；`unavailable(读失败) → 打日志 + 试底稿`。

## LoadedBidFile 二态（复用，不新建）

`InMemory(byte[], fileName, contentType)`（≤50MB 快路径）/ `OnDisk(Path, fileName, contentType)`（spill 落盘）。招标侧复用打分侧同一类型：内存态走既有 `textExtractor`，落盘态走 `SidecarStreamTextExtractor`，提取后临时文件立即释放。

## OversizedTenderTextException（新增，纯异常）

- 含义：招标文件提取后文本量超过上限的业务类拒绝（用户可自救：检查误传/拆分文档）。
- `MESSAGE = "招标文件文本量超过上限（约 500 万字），无法解析，请检查是否误传或拆分文档"`。
- 与 `BidFileQuotaException`（复用，资源类：单文件 >512MB/并发总量超限，联系管理员）区分，两类走同一 `failTask` 出口但文案不同。

## Parse Task（既有，行为收敛）

- 文本量超限失败 MUST 落 `FAILED` 并记录文本量文案，MUST NOT 覆盖已有可用评分项（沿用 `AUTO_APPROVED` 方可覆盖纪律）。
- `GET /items` 的 `meta.lastParseStatus/lastParseError` 透传新文案，前端抽屉原样展示。

## Config（新增 3 键，`application.yml`）

| 键 | 默认 | 说明 |
|---|---|---|
| `app.score-parse.tender-file.max-file-bytes` | 512MB | 招标下载单文件配额（含 Content-Length 预检与流式累计） |
| `app.score-parse.tender-file.ram-buffer-bytes` | 50MB | 内存快路径上限，超即 spill 落盘 |
| `app.score-parse.tender-file.max-text-chars` | 500 万字 | 提取后文本量上限，超即明确拒绝 |

## Validation Rules

- 字节配额检查点：Content-Length 预检 + 流式累计双卡（与打分侧同口径）。
- 文本量检查点：内存态提取后量长度；落盘态 sidecar 流式响应 char 计数超限即中止。
- 上传前置检查点（前端 + multipart 后端既有校验）：超上传配额直接拒，不进入解析。
