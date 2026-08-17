# Contract: 打分 API 错误语义（spec 045 后）

**Feature**: 045-bid-file-streaming-scoring | **Date**: 2026-08-17

> `POST /api/projects/{projectId}/score-parse/scoring` 对外错误语义的变化。权限守卫、任务状态机、成功响应结构均不变（FR-009）。

## 同步触发阶段（400 家族）

沿用现有 `IllegalArgumentException` 语义码 + Controller 映射模式（`ScoreParseController` L102-112）：

| 语义码 | 触发 | 对外文案（MESSAGE 常量唯一源） | HTTP |
|---|---|---|---|
| `SCORE_ITEMS_NOT_READY`（现有） | 评分项未解析完成 | 「请等待招标文件解析完成后再进行打分」 | 400 |
| `NO_BID_DOCUMENT`（现有） | 无投标文件 | 「请先上传投标文件后再进行打分」 | 400 |
| `BID_FILE_QUOTA_EXCEEDED`（新） | 单文件 >2GB 或总量 >4GB（FR-002） | `BidFileQuotaException.MESSAGE`：「投标文件过大超出服务器处理配额，请联系管理员」 | 400 |
| `OVERSIZED_BID_FILE`（**废止**） | ~~字节 >50MB~~（FR-006：字节硬限废止） | —— 移除映射与 `OversizedBidFileException` | — |

- 配额拒绝在**同步段**完成（下载前 Content-Length 预检 / DB 无关；下载中流式累计发现超限则任务转 FAILED 走异步段文案）。

## 异步执行阶段（任务终态 FAILED + errorMessage）

| 异常 | errorMessage（写入 score_parse_task） | ScoreResult.fallbackPending 文案 |
|---|---|---|
| `OversizedBidTextException`（新） | `OversizedBidTextException.MESSAGE`：「投标文件文本量超过上限（约 500 万字），无法完成打分，请检查是否误传或拆分文档」 | 同左 |
| `BidFileQuotaException`（新，下载中触发时） | `BidFileQuotaException.MESSAGE` | 同左 |
| 其他 RuntimeException（现有） | 原始 message | 「投标文件解析失败，无法完成打分，请检查文件内容或重新上传」（现有） |

- 复用 `failScoringWithPending` 统一出口（现有方法，PR !2314 引入）：删结果 → fallbackPending → failTask → clearProgress。

## 语义类别区分（FR-002 要求「可区分」）

| 类别 | 异常 | 用户可执行动作 |
|---|---|---|
| 业务类（文本量） | `OversizedBidTextException` | 检查误传 / 拆分文档（用户可自救） |
| 资源类（磁盘配额） | `BidFileQuotaException` | 联系管理员（用户不可自救，需运维调配额） |

类型级区分（独立异常类）+ 文案级区分（动作指引不同）双层落实。

## 前端

无独立前端文案文件（research 事实 9）：错误经通用 `ApiResponse.error(400, msg)` 通道展示，改 MESSAGE 常量即全链路生效。`src/` 中现有两处「超过 50MB」为招标文件预校验（`useTenderAiParse.js:42` / `useManualTenderCreate.js:33`），与投标链路无关，**不改**。

## 全局 grep 验收锚点（SC-005）

spec 045 合入后：`grep -rn "投标文件超过 50MB" backend/ src/` 命中数 = 0（`OversizedBidFileException` 删除后自然清零）。
