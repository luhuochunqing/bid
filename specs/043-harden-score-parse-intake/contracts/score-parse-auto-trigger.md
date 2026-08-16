# Contract: 评分解析自动触发与取正文

基线：`specs/041-ai-score-parse-backend/contracts/score-parse-api.md`。本文件只写 **增量**。

## GET `/api/projects/{projectId}/score-parse/items`

`meta` 增补（均可空，旧前端忽略）：

| 字段 | 类型 | 含义 |
|---|---|---|
| lastParseStatus | string \| null | 该项目最近一条 PARSE 任务状态：`PENDING` / `PROCESSING` / `COMPLETED` / `FAILED`。**没有任何 PARSE 任务时为 `null`** |
| lastParseError | string \| null | 最近一条失败原因；非 FAILED 时为 `null` |

其余 `items` / `summary` / 原有 meta 字段不变。

## POST `/api/projects/{projectId}/score-parse/parse`

语义不变：有进行中任务则**返回该任务、不另建**；否则建新任务。

前置：立项正文或历史底稿至少一份可用。不可用时 **400**，并落一条 `FAILED` PARSE 任务（避免 `lastParseStatus` 仍为 null）。文案：

| 原因 | 400 / lastParseError |
|---|---|
| 立项与底稿都没有可用正文 | `请先在立项阶段上传招标文件` |
| 立项超过 50MB 且无可用底稿 | `招标文件超过 50MB，无法解析` |

前端门闩（后端不因「已有历史任务」拒绝手动 POST）：

| 条件 | 行为 |
|---|---|
| `items.length === 0 && lastParseStatus == null` | 自动 **POST** 新建 |
| `items.length === 0 && lastParseStatus` 为 `PENDING` / `PROCESSING` | **跟随**：POST 复用或轮询 `GET /parse/status`，不新建 |
| `lastParseStatus === FAILED` | 展示 `lastParseError`，不自动 POST |
| `lastParseStatus === COMPLETED` | 不自动 POST |

## GET `/api/projects/{projectId}/score-parse/parse/status`

不改。无任务时仍按现网处理。是否「从未解析」以 items.meta 为准；进行中跟随用本接口轮询。

## 取正文（服务端内部契约）

1. 立项 `TENDER`（否则 `TENDER_FILE`）最新文件  
2. 大小已知且 > 50MB → 失败（该来源作废，尝试底稿）  
3. 读取时累计超过 50MB → 该来源作废，尝试底稿  
4. 抽出正文非空 → 使用立项  
5. 否则最新快照 `extractedText` 非空 → 使用底稿  
6. 否则无可用正文  

「是否有源」与「取正文」必须走上述同一套结果。
