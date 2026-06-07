> 一旦我所属的文件夹有所变化，请更新我。

# Batch 模块

批量模块负责批量分配、认领和删除等后端编排能力。
该目录仅处理批量操作的统一入口和结果回传，不承担单条业务逻辑。
对外提供批量操作 API 和请求/响应 DTO。批量关键操作只通过 `BatchOperationLogService` 写入一条汇总操作记录，facade 不再额外使用 `@Auditable` 生成重复日志。

| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/` | 子目录 | 批量操作 API 边界 |
| `controller/BatchOperationController.java` | Controller | 批量操作 API |
| `service/` | 子目录 | 批量应用服务边界 |
| `service/BatchOperationService.java` | Service | 兼容现有接口的 facade，不直接触发操作日志切面 |
| `service/BatchTenderCommandService.java` | Service | 标书批处理执行 |
| `service/BatchTaskCommandService.java` | Service | 任务批处理执行 |
| `service/BatchProjectCommandService.java` | Service | 项目批处理执行 |
| `service/BatchFeeCommandService.java` | Service | 费用批处理执行 |
| `service/BatchOperationLogService.java` | Service | 批处理操作日志唯一收口 |
| `core/` | 子目录 | 批处理纯规则内核 |
| `core/BatchValidationPolicy.java` | Core | 批量输入与用户上下文校验 |
| `core/BatchAssignmentPolicy.java` | Core | 跨部门分配与责任人规则 |
| `dto/` | 子目录 | 批量操作请求/响应边界 |
| `dto/BatchAssignRequest.java` | DTO | 批量分配请求 |
| `dto/BatchClaimRequest.java` | DTO | 批量认领请求 |
| `dto/BatchDeleteRequest.java` | DTO | 批量删除请求 |
| `dto/BatchOperationResponse.java` | DTO | 批量操作结果响应 |
