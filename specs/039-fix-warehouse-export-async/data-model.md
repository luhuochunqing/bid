# Data Model: 修复仓库全量合订本导出任务创建失败

**Date**: 2026-07-17
**Feature**: 039-fix-warehouse-export-async

## 现有实体（不变）

### WarehouseExportTaskEntity

表名：`warehouse_export_task`（V1032 创建，V1069 新增 result_summary 列）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| status | VARCHAR | PENDING / PROCESSING / COMPLETED / FAILED |
| filter_snapshot | TEXT | 导出时的筛选条件 JSON |
| total_count | INT | 导出记录数 |
| stored_file_path | VARCHAR | ZIP 文件存储路径 |
| download_url | VARCHAR | 下载 URL |
| expires_at | TIMESTAMP | 过期时间（COMPLETED 后 24 小时） |
| created_by | VARCHAR | 创建人标识 |
| created_at | TIMESTAMP | 创建时间 |
| completed_at | TIMESTAMP | 完成时间 |
| failure_reason | TEXT | 失败原因 |
| result_summary | TEXT | 结果摘要 JSON（V1069 新增） |

**Schema 变更**: 无。本次修复不涉及数据库 schema 变更。

## 新增组件（非 JPA 实体）

### WarehouseExportAsyncExecutor

**类型**: Spring `@Component`（Imperative Shell，承载 @Async 方法）

**职责**: 接收 `WarehouseExportAppService` 委托的异步导出执行请求，在 `warehouseExportExecutor` 线程池中执行 `doExport` 流程。

**依赖**:
- `WarehouseExportTaskRepository`（任务状态更新）
- `WarehouseFilterService`（filter 模式查询仓库）
- `WarehouseRepository`（ids 模式查询仓库）
- `WarehouseAttachmentRepository`（附件加载）
- `UserRepository`（用户名解析）
- `ExcelWriter`（Excel 生成）
- `WarehouseWordBundleBuilder`（Word 合订本生成）
- `WarehouseExportZipBuilder`（ZIP 打包）
- `WarehouseExportNotificationPublisher`（通知发布）

**公开方法**:
- `void executeExport(Long taskId, WarehouseFilterDTO filterDTO, Set<WarehouseAttachmentOrganizationForm> forms)`
- `void executeExportByIds(Long taskId, List<Long> ids, Set<WarehouseAttachmentOrganizationForm> forms)`

**注解**:
- `@Async("warehouseExportExecutor")` — 确保在专用线程池异步执行
- 无 `@Transactional`（事务由内部方法管理）

**状态转移**:
```
PENDING (AppService.createTask) 
  → PROCESSING (AsyncExecutor.markProcessing, REQUIRES_NEW)
  → COMPLETED (AsyncExecutor.completeTask, REQUIRES_NEW) 
  | → FAILED (AsyncExecutor.failTask, REQUIRES_NEW)
```

## 与 WarehouseExportAppService 的关系

```
WarehouseExportAppService (编排层)
  ├── @Transactional: createTask → save(PENDING) → return taskId
  ├── 注入 WarehouseExportAsyncExecutor
  └── asyncExecutor.executeExport(taskId, ...)  ← 通过代理调用，@Async 生效
        ↓
WarehouseExportAsyncExecutor (异步执行层)
  ├── @Async: executeExport
  │   ├── markProcessing (REQUIRES_NEW)
  │   ├── doExport (无事务，IO 操作)
  │   │   ├── loadAttachments
  │   │   ├── filterAttachments
  │   │   ├── buildRows
  │   │   ├── writeExcel
  │   │   ├── buildBundle (try-catch 降级 null)
  │   │   ├── buildZip
  │   │   └── saveZip
  │   ├── completeTask (REQUIRES_NEW) 
  └── publishNotification
```
