# API Contract: AI 案例切片语义检索

## Base Path

`/api/case-slices`

## Authentication & Authorization

- Authentication: Required (via JWT session)
- Authorization: `isAuthenticated()` + `ProjectAccessScopeService.assertCurrentUserCanAccessProject(projectId)`

---

## 1. Recommend by Scoring Item

### Request

```http
GET /api/case-slices/recommend?projectId={projectId}&scoringItemId={scoringItemId}&topK={topK}
```

### Parameters

| Name | Type | Required | Description |
|---|---|---|---|
| `projectId` | Long | YES | 当前项目 ID，用于权限校验 |
| `scoringItemId` | Long | YES | `project_score_drafts.id` |
| `topK` | Integer | NO | 返回条数上限，默认 20，最大 50 |

### Response

```json
{
  "data": [
    {
      "sliceId": 123,
      "projectDir": "2026.01.05-中广核办公",
      "docxFile": "技术文件/中广核办公技术方案.docx",
      "docxLabel": "技术",
      "sectionTitle": "狮行物流技术与系统优势",
      "textPreview": "强大的计划管理系统PMS...",
      "textLength": 308,
      "paraCount": 5,
      "cosineScore": 0.872,
      "finalScore": 88,
      "matchReason": "语义相似、标题匹配、技术文件"
    }
  ],
  "total": 1
}
```

### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | `projectId` 或 `scoringItemId` 缺失 |
| 403 | `PROJECT_ACCESS_DENIED` | 当前用户无权访问该项目 |
| 404 | `SCORING_ITEM_NOT_FOUND` | 评分项不存在 |
| 503 | `EMBEDDING_SERVICE_UNAVAILABLE` | 向量未加载或 embedding 服务不可用 |

---

## 2. Recommend by Query Text

### Request

```http
GET /api/case-slices/recommend/by-query?query={query}&topK={topK}
```

### Parameters

| Name | Type | Required | Description |
|---|---|---|---|
| `query` | String | YES | 任意查询文本，长度 2~3000 字符 |
| `topK` | Integer | NO | 返回条数上限，默认 20，最大 50 |

### Response

同 Recommend by Scoring Item。

### Error Responses

| Status | Code | Description |
|---|---|---|
| 400 | `INVALID_REQUEST` | `query` 为空或超长 |
| 503 | `EMBEDDING_SERVICE_UNAVAILABLE` | 向量未加载或 embedding 服务不可用 |

---

## 3. Get Slice Detail

### Request

```http
GET /api/case-slices/{id}
```

### Response

```json
{
  "sliceId": 123,
  "projectDir": "2026.01.05-中广核办公",
  "docxFile": "技术文件/中广核办公技术方案.docx",
  "docxLabel": "技术",
  "sectionTitle": "狮行物流技术与系统优势",
  "textPreview": "强大的计划管理系统PMS...",
  "textLength": 308,
  "paraCount": 5,
  "createdAt": "2026-07-04T10:00:00"
}
```

---

## 4. Batch Embedding Job (Admin / Internal)

### Request

```http
POST /api/case-slices/admin/batch-embed?batchSize={batchSize}
```

### Parameters

| Name | Type | Required | Description |
|---|---|---|---|
| `batchSize` | Integer | NO | 每批处理条数，默认 100，最大 200 |

### Response

```json
{
  "processed": 100,
  "failed": 2,
  "remaining": 8042
}
```

**Note**: 该接口仅用于调试或手动触发；正式上线后应由 CLI runner 或后台任务完成。

---

## Common HTTP Status Codes

- `200 OK`：请求成功
- `400 Bad Request`：参数校验失败
- `403 Forbidden`：权限不足
- `404 Not Found`：资源不存在
- `503 Service Unavailable`：embedding 服务或向量缓存未就绪
