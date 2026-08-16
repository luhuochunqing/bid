# Contract: 评分标准解析 REST API

**Base**: `/api/projects/{projectId}/score-parse` | **鉴权**: 类级 `@PreAuthorize("isAuthenticated()")` + Service 层 `assertCurrentUserCanAccessProject(projectId)`（无权限 → 403，统一语义"您无权限查看此任务的评分解析结果"）

## 1. 触发解析（手动重新解析）

`POST /parse`

请求：`{ "force": true }`（可选，默认 false；已有进行中任务时返回该任务而非报错）

响应 `202`：
```json
{ "taskId": "uuid", "status": "PENDING" }
```
前置条件：该项目已有招标文件快照（`bid_tender_document_snapshots` 存在）。无文件 → `400 { "code": "NO_TENDER_DOCUMENT" }`。

## 2. 解析状态查询（轮询）

`GET /parse/status`

响应 `200`：
```json
{
  "taskId": "uuid", "status": "PROCESSING", "progress": 45,
  "stage": "STRUCTURED_EXTRACT",
  "errorMessage": null, "startedAt": "...", "completedAt": null
}
```
`status`：`PENDING | PROCESSING | COMPLETED | FAILED`。解析项为 0 时终态 `FAILED` + `errorMessage: "未在文件中识别到评分标准章节"`（FR-007/5.1）。

## 3. 评分项清单（阶段 1 结果）

`GET /items`

响应 `200`：
```json
{
  "items": [{
    "id": 1, "code": "D2", "dim": "资质业绩", "detail": "...原文完整表述...",
    "weight": 5, "scoreType": "OBJECTIVE", "status": "PENDING",
    "estScore": 3, "estBasis": "知识库匹配到 CMMI 3 级（替代方案），部分满足",
    "kbHit": true, "location": "P47 评分办法表"
  }],
  "summary": {
    "totalWeight": 100, "totalEstScore": 38,
    "okCount": 5, "dangerCount": 1, "pendingCount": 7,
    "objectiveWeight": 41, "subjectiveWeight": 59,
    "weightWarning": false
  }
}
```
规则：主观项 `estScore=null, kbHit=null`；`weightWarning=true` 时 summary 附实际总分（FR-022）。`detail` 禁止摘要（FR-002）。

## 4. 上传投标文件

`POST /bid-documents`（multipart `file`）

- 格式校验：PDF/docx（FR 对应 spec Edge Cases；>50MB 拒绝 `413`）
- 存储：`TenderDocumentStorage`，category=`bid-file`；元数据记 `project_document`（documentCategory=`BID_FILE`）
- 响应 `201`：`{ "documentId": 1, "fileUrl": "doc-insight://..." }`

## 5. 触发实际打分（阶段 2）

`POST /scoring`

响应 `202`：`{ "taskId": "uuid", "status": "PENDING" }`

前置条件（FR-019，不满足返回 400 + 明确 code）：
- 无投标文件 → `400 { "code": "NO_BID_DOCUMENT", "message": "需先上传投标文件" }`
- 评分标准未解析完成 → `400 { "code": "SCORE_ITEMS_NOT_READY", "message": "请等待招标文件解析完成后再进行打分" }`
- 已有进行中打分任务 → `409 { "code": "TASK_IN_PROGRESS" }`（互斥）

## 6. 打分状态查询（轮询）

`GET /scoring/status` — 结构同 §2。

## 7. 打分结果（阶段 2）

`GET /results`

响应 `200`：
```json
{
  "results": [{
    "scoreItemId": 1, "code": "D2", "dim": "资质业绩", "detail": "...",
    "weight": 5, "scoreType": "OBJECTIVE", "status": "PENDING",
    "actualScore": 3, "evidence": "标书已补充 CMMI 3 级证书说明...（匹配比例 60%）",
    "quote": "我方虽未取得 CMMI 5 级认证...（第 3.2 节 P15）",
    "missedReason": "CMMI 5 级认证未找到匹配证书", "suggestion": "建议尽快启动...",
    "matchRatio": 60
  }],
  "summary": { "totalWeight": 100, "totalActualScore": 38, "...": "同 §3 summary" }
}
```
规则：主观项 `actualScore=null`；quote 为 null 时前端显示"标书引用：无"；阶段 2 响应不含 kbHit 字段（FR-018）。

## 8. 自动触发（服务间事件，非 REST）

`TenderDocumentStoredEvent(projectId, documentId, fileUrl)` 由 `BidTenderDocumentImportAppService` 在招标文档保存后发布；scoreparse `@Async @EventListener` 消费 → 创建 PARSE 任务（互斥：进行中则跳过并 log.info）。

## 错误形态统一

| 场景 | HTTP | code |
|---|---|---|
| 无项目权限 | 403 | `PROJECT_ACCESS_DENIED` |
| 任务超时（30min） | 状态查询返回 | status=FAILED + errorMessage="任务超时终止，保留上次成功结果" |
| LLM 输出超区间 | 200（该项） | actualScore=null + status=PENDING（FR-016） |
