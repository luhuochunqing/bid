# Tender Upload Queue 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
- 提供大标书异步受理链路（`upload-init` / `upload-complete` / `task-status`）。
- 基于 MySQL 8 `FOR UPDATE SKIP LOCKED` 实现任务抢占。
- 落地全局并发阀值、单用户并发阀值、重试与 DLQ。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/TenderUploadController.java` | Controller | 上传会话、完成回调、任务状态接口 |
| `service/TenderUploadTaskService.java` | Service | 会话创建、文件幂等、任务入队 |
| `service/TenderTaskWorkerService.java` | Service | 定时拉取、处理、重试、DLQ |
| `service/StorageGuardService.java` | Service | 共享存储路径安全、I/O 与哈希计算 |
| `service/TenderTaskStateMachine.java` | Service | 任务状态流转（RUNNING/RETRYING/DLQ） |
| `entity/*` | Entity | 文件元数据、任务状态、死信模型 |
| `repository/*` | Repository | MySQL 抢占查询、状态统计 |
| `config/TenderProcessingProperties.java` | Config | 并发阈值、重试和资源保护参数 |
