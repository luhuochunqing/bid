# Contract: WarehouseExportAsyncExecutor

**Date**: 2026-07-17
**Feature**: 039-fix-warehouse-export-async

## 接口契约

`WarehouseExportAsyncExecutor` 是 Spring `@Component`，承载从 `WarehouseExportAppService` 提取的 @Async 方法。

## 公开方法

### executeExport

```java
@Async("warehouseExportExecutor")
void executeExport(Long taskId, WarehouseFilterDTO filterDTO, Set<WarehouseAttachmentOrganizationForm> forms)
```

**职责**: 按 filter 模式异步执行仓库导出流程。

**前置条件**:
- `taskId` 对应的 `WarehouseExportTaskEntity` 已存在且 status=PENDING（由 AppService.createTask 创建）
- `filterDTO` 非 null
- `forms` 非空

**行为**:
1. 通过 `warehouseExportExecutor` 线程池异步执行（线程名前缀 `warehouse-export-`）
2. MDC 上下文透传（traceId/userId/roleCode）
3. 内部调用 doExport 执行实际导出
4. 异常不抛出，内部捕获并写入 FAILED 状态 + failureReason

**后置条件**:
- 成功：status=COMPLETED, stored_file_path 填充, expires_at=now+24h, result_summary 填充
- 失败：status=FAILED, failure_reason 填充

### executeExportByIds

```java
@Async("warehouseExportExecutor")
void executeExportByIds(Long taskId, List<Long> ids, Set<WarehouseAttachmentOrganizationForm> forms)
```

**职责**: 按 ids 模式异步执行仓库导出流程。

**前置条件**:
- `taskId` 对应的 `WarehouseExportTaskEntity` 已存在且 status=PENDING
- `ids` 非空
- `forms` 非空

**行为/后置条件**: 同 executeExport

## 内部方法（包级可见，便于测试）

### doExport

```java
void doExport(Long taskId, List<WarehouseEntity> entities, Set<WarehouseAttachmentOrganizationForm> forms)
```

**职责**: 执行实际导出流程（加载附件 → 过滤 → Excel → Word → ZIP → 保存）。

**降级语义**（CO-582 §4，保留）:
- Word 合订本生成失败 → log.warn + wordBytes=null，继续生成 ZIP（仅含 xlsx + 附件目录）
- 任务状态 COMPLETED（非 FAILED）

### markProcessing / completeTask / failTask

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
void markProcessing(Long taskId)

@Transactional(propagation = Propagation.REQUIRES_NEW)
void completeTask(Long taskId, String storedFilePath, String resultSummary)

@Transactional(propagation = Propagation.REQUIRES_NEW)
void failTask(Long taskId, String reason)
```

**职责**: 独立事务更新任务状态，确保异步线程中的状态变更能立即提交，不被外层事务回滚影响。

## 不变量

1. **API 契约不变**: `POST /api/knowledge/warehouses/export` 请求/响应格式不变
2. **任务状态机不变**: PENDING → PROCESSING → COMPLETED/FAILED
3. **文件 TTL 不变**: 24 小时
4. **降级语义不变**: Word 合订本失败不影响整体导出
5. **权限点不变**: `WAREHOUSE_MANAGE_PERMISSION`
6. **线程池不变**: `warehouseExportExecutor`（core=2, max=4, queue=20, CallerRunsPolicy, MdcTaskDecorator）

## 验证指标

1. 异步线程名以 `warehouse-export-` 开头（证明 @Async 生效）
2. HTTP 请求响应时间 < 2 秒（仅 createTask）
3. Word 合订本异常时任务仍 COMPLETED（降级不回归）
