# Data Model: 标讯批量导入异步化

## 新增实体

### TenderImportTask（异步导入任务）

**表名**: `tender_import_task`

**用途**: 持久化标讯批量导入异步任务状态，支持服务重启后恢复/标记失败

#### 字段设计

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | 主键 |
| `task_id` | VARCHAR(36) | NOT NULL, UNIQUE, INDEX | 业务任务 ID（UUID），对外暴露防猜测 |
| `user_id` | BIGINT | NOT NULL, INDEX | 发起用户 ID（关联 users.id） |
| `file_name` | VARCHAR(255) | NOT NULL | 原始文件名（仅记录，不存文件内容） |
| `total_rows` | INT | NOT NULL DEFAULT 0 | Excel 总行数（解析后填充） |
| `processed_rows` | INT | NOT NULL DEFAULT 0 | 已处理行数 |
| `success_count` | INT | NOT NULL DEFAULT 0 | 成功行数 |
| `failure_count` | INT | NOT NULL DEFAULT 0 | 失败行数 |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'PENDING', INDEX | 状态机：PENDING/PROCESSING/COMPLETED/PARTIAL_SUCCESS/FAILED |
| `error_details` | JSON | NULL | 失败行明细数组（见下文结构） |
| `created_at` | DATETIME | NOT NULL, INDEX | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 状态更新时间（用于卡死检测） |
| `completed_at` | DATETIME | NULL | 完成时间（COMPLETED/PARTIAL_SUCCESS/FAILED 时填充） |

**索引**:
- `UNIQUE INDEX uk_task_id (task_id)`
- `INDEX idx_status_updated (status, updated_at)` — 卡死任务扫描
- `INDEX idx_user_created (user_id, created_at)` — 用户任务列表查询

**参考范式**: [V1022__personnel_batch_import_task.sql](file:///Users/user/xiyu/worktrees/trae/backend/src/main/resources/db/migration-mysql/V1022__personnel_batch_import_task.sql)

#### error_details JSON 结构

```json
[
  {
    "rowNumber": 2,
    "field": "purchaserName",
    "errorMessage": "采购人名称不能为空",
    "tenderTitle": "某市政府采购项目"
  },
  {
    "rowNumber": 5,
    "field": "duplicate",
    "errorMessage": "标讯已存在：采购人=某公司，项目编号=XYZ-2026-001",
    "tenderTitle": "某公司办公设备采购"
  }
]
```

#### 状态机

```
PENDING ──→ PROCESSING ──→ COMPLETED (failure_count=0)
                    │
                    ├──→ PARTIAL_SUCCESS (0 < failure_count < total_rows)
                    │
                    └──→ FAILED (failure_count = total_rows 或 异常中断)
```

详见 [contracts/tender-import-task-states.md](./contracts/tender-import-task-states.md)

---

### TenderImportTaskError（失败行明细，值对象）

**不建表**，作为 `TenderImportTask.error_details` JSON 字段的 Java 值对象

#### 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `rowNumber` | int | Excel 行号（从 2 开始，1 是表头） |
| `field` | String | 失败字段名（`purchaserName`/`projectNo`/`duplicate`/`row`） |
| `errorMessage` | String | 错误描述（用户可读） |
| `tenderTitle` | String | 标讯标题（用于用户定位是哪条标讯失败） |

**Java 类型**: `record TenderImportTaskError(int rowNumber, String field, String errorMessage, String tenderTitle)`

---

### TenderImportProgressDTO（进度查询响应，DTO）

**不建表**，仅用于 API 响应

#### 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `taskId` | String | 任务 ID（UUID） |
| `status` | String | 任务状态 |
| `totalRows` | int | 总行数 |
| `processedRows` | int | 已处理行数 |
| `successCount` | int | 成功数 |
| `failureCount` | int | 失败数 |
| `percent` | int | 进度百分比（0-100） |
| `errors` | List<TenderImportTaskError> | 失败明细（仅 COMPLETED/PARTIAL_SUCCESS/FAILED 时返回） |
| `createdAt` | LocalDateTime | 创建时间 |
| `completedAt` | LocalDateTime | 完成时间 |

**Java 类型**: `record TenderImportProgressDTO(String taskId, String status, int totalRows, int processedRows, int successCount, int failureCount, int percent, List<TenderImportTaskError> errors, LocalDateTime createdAt, LocalDateTime completedAt)`

---

## 现有实体（不修改）

### Tender（标讯）
- 不修改 schema
- 异步任务通过 `TenderCommandService.createTender` 创建，走现有 Tender 实体

### User（用户）
- 不修改
- `TenderImportTask.user_id` 关联 `users.id`

---

## Redis 键设计

### 进度缓存

**Key**: `tender:import:progress:{taskId}`
**Value**: JSON 序列化的 `TenderImportProgressDTO`（不含 errors，仅状态+计数）
**TTL**: 7 天（参考 `PersonnelImportProgressService` 的 `REDIS_TTL = Duration.ofDays(7)`）
**用途**: 高频进度查询，避免每次查 DB

**清理时机**: 任务完成（COMPLETED/PARTIAL_SUCCESS/FAILED）后保留 24h（供前端回看），之后由 `clearProgress` 清除

### 操作员信息缓存

**Key**: `tender:import:operator:{taskId}`
**Value**: `userId`（Long）
**TTL**: 1 小时
**用途**: 异步线程内通过 taskId 反查 userId（MDC 传递的补充）

---

## Flyway 迁移脚本

**文件**: `backend/src/main/resources/db/migration-mysql/V{next}__create_tender_import_task.sql`
**版本号**: 使用 `scripts/new-migration.sh` 自动预约（禁止手动猜测）
**回滚**: `backend/src/main/resources/db/rollback/migration-mysql/U{next}__drop_tender_import_task.sql`
