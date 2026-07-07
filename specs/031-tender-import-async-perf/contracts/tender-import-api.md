# API Contract: 标讯批量导入异步化

## 端点 1: 创建导入任务

### Request

```
POST /api/tenders/import
Content-Type: multipart/form-data
X-Idempotency-Key: {client-generated-uuid}
Authorization: Bearer {jwt}

Body:
  file: <excel-file.xlsx>  (required, ≤5MB, .xlsx)
```

### Response 202 Accepted（任务已创建）

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "totalRows": 0,
  "processedRows": 0,
  "successCount": 0,
  "failureCount": 0,
  "message": "导入任务已创建，正在处理"
}
```

### Response 400 Bad Request（同步校验失败）

```json
{
  "code": "INVALID_FILE",
  "message": "导入文件不能超过 5MB"
}
```

**同步校验项**（不进入异步阶段）:
- file 为空 → `请上传导入文件`
- file.size > 5MB → `导入文件不能超过 5MB`
- 文件名不以 `.xlsx` 结尾 → `仅支持 .xlsx 模板，请使用下载的模板`

### Response 409 Conflict（Idempotency 命中）

返回首次请求的 202 响应体（由 `@Idempotent` 注解处理，不重复创建任务）

### Response 401 Unauthorized

未登录（由现有 `JwtAuthenticationFilter` 处理）

---

## 端点 2: 查询导入进度

### Request

```
GET /api/tenders/import/{taskId}/progress
Authorization: Bearer {jwt}
```

### Response 200 OK（处理中）

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "totalRows": 180,
  "processedRows": 90,
  "successCount": 88,
  "failureCount": 2,
  "percent": 50,
  "errors": null,
  "createdAt": "2026-07-07T18:05:12",
  "completedAt": null
}
```

### Response 200 OK（部分成功）

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PARTIAL_SUCCESS",
  "totalRows": 180,
  "processedRows": 180,
  "successCount": 178,
  "failureCount": 2,
  "percent": 100,
  "errors": [
    {
      "rowNumber": 5,
      "field": "duplicate",
      "errorMessage": "标讯已存在：采购人=某公司，项目编号=XYZ-2026-001",
      "tenderTitle": "某公司办公设备采购"
    },
    {
      "rowNumber": 12,
      "field": "purchaserName",
      "errorMessage": "采购人名称不能为空",
      "tenderTitle": null
    }
  ],
  "createdAt": "2026-07-07T18:05:12",
  "completedAt": "2026-07-07T18:06:56"
}
```

### Response 200 OK（全部成功）

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "totalRows": 180,
  "processedRows": 180,
  "successCount": 180,
  "failureCount": 0,
  "percent": 100,
  "errors": [],
  "createdAt": "2026-07-07T18:05:12",
  "completedAt": "2026-07-07T18:06:56"
}
```

### Response 200 OK（失败）

```json
{
  "taskId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "FAILED",
  "totalRows": 180,
  "processedRows": 50,
  "successCount": 0,
  "failureCount": 50,
  "percent": 28,
  "errors": [...],
  "createdAt": "2026-07-07T18:05:12",
  "completedAt": "2026-07-07T18:06:00"
}
```

**FAILED 场景**:
- 异常中断（服务重启、OOM 等）
- 全部行失败（failure_count = total_rows）

### Response 403 Forbidden

任务不属于当前用户（`user_id` 不匹配）

### Response 404 Not Found

taskId 不存在

---

## 端点 3: 下载导入模板（保持现有）

```
GET /api/tenders/import/template
```

不修改，保持现有 `TenderImportService.generateTemplate()` 行为

---

## 前端轮询契约

### 轮询频率

- 状态为 `PENDING` 或 `PROCESSING` 时：每 2 秒查询一次
- 状态为 `COMPLETED` / `PARTIAL_SUCCESS` / `FAILED` 时：停止轮询
- 网络失败：重试 3 次，每次间隔 2 秒，3 次失败后显示"进度查询失败，请刷新页面"

### 超时

- 进度查询接口 axios timeout: 10000ms（10 秒，远大于预期 <200ms）
- 创建导入任务接口 axios timeout: 30000ms（30 秒，覆盖同步阶段文件校验）

---

## 向后兼容性

### 破坏性变更

**POST /api/tenders/import 响应结构变更**:
- 旧：同步返回 `TenderImportResultDTO`（totalRows/successCount/failureCount/errors）
- 新：异步返回 `TenderImportTaskDTO`（taskId/status/message）

**应对**: 前端 `BulkImportDialog.vue` 同步改造，适配新响应结构。因标讯导入是内部功能，不涉及外部 API 消费者，无需版本化。

### 非破坏性变更

- `GET /api/tenders/import/template` 不变
- 现有 `@Idempotent` 语义不变（缓存的是新响应结构）
- `TenderCommandService.createTender` 内部逻辑不变
