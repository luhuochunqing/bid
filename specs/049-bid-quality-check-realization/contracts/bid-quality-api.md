# Contract: 标书质量核查保留链 API（049）

**Base path**: `/api/compliance` | **鉴权**: `@PreAuthorize("isAuthenticated()")`（沿用现状，不新增权限形态）

> 本契约覆盖保留链三端点的**行为变化**（出入参结构沿用现有 DTO，不破坏前端消费）。
> 第零步删除的旧端点（`POST /bid-document/check/{projectId}`、`GET /bid-document/results/{projectId}`）不在本契约范围——删除后全仓 grep 零命中即验收。

---

## 1. POST /bid-quality/trigger/{projectId}?force={boolean}

触发异步核查任务。

**请求**: 路径参数 `projectId: Long`；查询参数 `force`（默认 false，true 跳过 hash 缓存强制重跑）。

**行为变化（第一步，本轮新增）**：

| 前置条件 | 现行行为 | 新契约 |
|---|---|---|
| 项目无 BID 类 ProjectDocument | 创建任务，核查降级为元数据 | **同步返回 4xx**，body：`ApiResponse` 错误体，message = "无法执行核查，请先上传投标文件"；**不创建任务** |
| 有文档但 Sidecar 不可用/文件不可解析 | 同上降级 | 同上 4xx（message 注明解析失败原因），不创建任务 |
| 正常 | 创建任务 | 不变：返回 taskId |

**成功响应**（200，沿用 `QualityCheckTaskDTO`）：

```json
{
  "code": 0,
  "message": "Quality check task triggered",
  "data": {
    "taskId": "qc-xxxx",
    "projectId": 123,
    "status": "PENDING",
    "progress": 0,
    "currentStep": null,
    "errorMessage": null,
    "bidFileName": "投标文件.pdf",
    "createdAt": "2026-08-20T10:00:00",
    "updatedAt": "2026-08-20T10:00:00"
  }
}
```

**失败响应（新增 4xx）**：HTTP 400/422（实施时对齐仓库既有业务异常 → HTTP 码映射），`data` 为 null，`message` 为用户可读中文提示。

**前端消费变化**: `useQualityCheckDrawer.js` 捕获 trigger 4xx → ElMessage 提示，不进入轮询。

---

## 2. GET /bid-quality/progress/{taskId}

轮询任务进度（前端 2s 间隔、120s 超时，沿用）。

**响应**（200，`QualityCheckTaskDTO`）：`status` ∈ PENDING / PARSING / CHECKING / COMPLETED / FAILED；`progress` 0~100。

**行为变化（第五步）**：LLM lane 执行期间经 `ProgressReporter` 持续更新 `progress` 与 `currentStep`（如 "AI 裁决中 12/7"），保证 120s 轮询窗口内前端可见活跃进度。单项 LLM 失败**不置 FAILED**（降级单项 MANUAL，任务正常 COMPLETED）。

---

## 3. GET /bid-quality/report/{projectId}

获取最新核查报告。

**响应**（200，沿用 `QualityCheckReportDTO`）：

```json
{
  "code": 0,
  "data": {
    "id": 1, "taskId": "qc-xxxx", "projectId": 123,
    "status": "COMPLETED", "overallStatus": "FAIL",
    "riskScore": 62,
    "passedCount": 18, "failedCount": 5, "manualReviewCount": 2, "totalCount": 25,
    "bidFileName": "投标文件.pdf",
    "bidFileHash": "<sha256>", "tenderFileHash": "<sha256>",
    "checkedAt": "2026-08-20T10:02:00",
    "items": [ "QualityCheckItemResult × 25（见下）" ]
  }
}
```

**单项 `QualityCheckItemResult`（结构不变，内容约束收紧）**：

```json
{
  "itemCode": "COVER_INFO",
  "category": "BASIC_INFO",
  "title": "封面信息核对",
  "status": "FAIL",
  "severity": "HIGH",
  "order": 1,
  "conclusion": "[EVIDENCE_INSUFFICIENT] ...（MANUAL 态必含 reasonCode 前缀）",
  "suggestion": "...",
  "evidences": [
    {
      "sourceType": "BID",
      "sourceTitle": "投标文件.pdf",
      "pageNumber": 3,
      "quote": "……投标人名称：XX 科技有限公司……（真实原文摘录）",
      "sourceRecordId": null,
      "confidence": 1.0,
      "knowledgeSource": null,
      "matched": null
    },
    {
      "sourceType": "KNOWLEDGE_BASE",
      "sourceTitle": "ISO27001认证",
      "pageNumber": null,
      "quote": "ISO27001认证（有效期至 2027-03）",
      "sourceRecordId": "12345",
      "confidence": 1.0,
      "knowledgeSource": "qualification",
      "matched": true
    }
  ]
}
```

**内容契约（本轮验收硬约束，对齐 spec §2）**：

1. BID/TENDER 类 evidence 的 `pageNumber` 必须是 `PageContent` 真实页码或 null，禁止常量 1
2. `quote` 必须是文档原文子串或 DB 记录内容，禁止固定文案
3. KNOWLEDGE_BASE 类 evidence 的 `sourceRecordId` 非空、`knowledgeSource` ∈ 四库枚举、`matched` 非空
4. 招标文件未上传时：比对项 MANUAL + `[REFERENCE_MISSING]`，独立项照常出结果
5. `bidFileHash` / `tenderFileHash` 为真实文件 SHA-256（版本缓存键）

**前端消费变化**: `QualityCheckItemCard.vue` 证据链面板新增渲染 `knowledgeSource` 标签与 `matched` 结论（有/无匹配），其余沿用。
