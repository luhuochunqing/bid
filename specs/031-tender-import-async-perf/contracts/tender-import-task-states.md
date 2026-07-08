# State Machine Contract: TenderImportTask

## 状态定义

| 状态 | 说明 | 可跃迁到 |
|---|---|---|
| `PENDING` | 任务已创建，等待异步线程启动 | `PROCESSING`, `FAILED` |
| `PROCESSING` | 异步线程正在处理 Excel 行 | `COMPLETED`, `PARTIAL_SUCCESS`, `FAILED` |
| `COMPLETED` | 全部行处理成功（failure_count=0） | （终态） |
| `PARTIAL_SUCCESS` | 部分行成功（0 < failure_count < total_rows） | （终态） |
| `FAILED` | 全部行失败 或 异常中断 | （终态） |

## 跃迁规则

### PENDING → PROCESSING

**触发**: `executeImportAsync` 开始执行
**动作**:
- `status = PROCESSING`
- `updated_at = now()`
- 写 Redis 进度缓存

### PROCESSING → COMPLETED

**触发**: 循环结束，`failure_count == 0`
**动作**:
- `status = COMPLETED`
- `success_count = total_rows`
- `processed_rows = total_rows`
- `completed_at = now()`
- 写 Redis 最终进度
- 24h 后清 Redis（保留 DB 记录）

### PROCESSING → PARTIAL_SUCCESS

**触发**: 循环结束，`0 < failure_count < total_rows`
**动作**:
- `status = PARTIAL_SUCCESS`
- `processed_rows = total_rows`
- `error_details = JSON.serialize(errors)`
- `completed_at = now()`
- 写 Redis 最终进度（含 errors）
- 24h 后清 Redis

### PROCESSING → FAILED

**触发 1**: 循环结束，`failure_count == total_rows`（全部行失败）
**触发 2**: `executeImportAsync` catch Throwable（异常中断）
**动作**:
- `status = FAILED`
- `error_details = JSON.serialize(errors)` 或 `[{"rowNumber":0,"field":"system","errorMessage":"<异常摘要>","tenderTitle":null}]`
- `completed_at = now()`
- 写 Redis 最终进度
- 24h 后清 Redis

### PENDING → FAILED（异常启动失败）

**触发**: `executeImportAsync` 启动前异常（如 Excel 解析失败）
**动作**:
- `status = FAILED`
- `error_details = [{"rowNumber":0,"field":"parse","errorMessage":"<异常消息>","tenderTitle":null}]`
- `completed_at = now()`

## 卡死任务恢复

### 启动时扫描

**触发**: 应用启动（`ApplicationReadyEvent`）
**扫描条件**: `status = 'PROCESSING' AND updated_at < now() - 30 minutes`
**动作**:
- `status = FAILED`
- `error_details = [{"rowNumber":0,"field":"system","errorMessage":"服务重启导致任务中断","tenderTitle":null}]`
- `completed_at = now()`
- 清 Redis 进度缓存

**理由**: 异步任务在内存中执行，服务重启时 JVM 终止，任务无法恢复。30 分钟阈值避免误伤正在处理的任务（500 行预计 <60s）。

## 并发与一致性

### 任务创建

- `@Idempotent` 保证相同 Idempotency-Key 不重复创建
- `task_id` 使用 UUID，数据库唯一索引兜底

### 状态更新

- 所有状态更新通过 `TenderImportTaskStateService` 集中处理
- 使用 `@Transactional` 更新（每次状态变更是独立事务）
- 不使用乐观锁（单线程更新，无并发冲突）

### 进度查询

- Redis 优先（高频查询）
- Redis 未命中查 DB（任务已完成或 Redis 故障）
- 进度数据最终一致（异步线程更新 Redis 可能有 ≤2s 延迟，可接受）

## 错误处理

### error_details 序列化失败

**场景**: JSON 序列化 errors 列表失败（如 errorMessage 含非法字符）
**处理**: 三层降级
1. 尝试 `taskRepository.save(task)` 含完整 error_details
2. 失败则 `taskRepository.updateStatus(taskId, FAILED)` 仅更新状态
3. 仍失败则 `progressService.clearProgress(taskId)` 清 Redis，任务在 DB 中保持 PROCESSING（下次启动扫描时标记 FAILED）

参考 [ImportPersonnelAppService.java:163-198](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/personnel/service/ImportPersonnelAppService.java#L163-L198)

### failImportTask 自身失败

**场景**: `failImportTask` 方法内部异常
**处理**: catch 并 log.error，不传播（避免 finally 块抛异常覆盖原始异常）
