# Export 模块 (通用导出模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
通用导出模块提供 Excel 导出能力。表 `export_tasks` 是**跨模块异步导出任务**的唯一落点（`data_type` 区分业务）。

**现状**：`/api/export/excel` 仍是同步写临时文件，**没有**写入 `export_tasks`（Repository 无生产注入）。新增异步导出/导入必须接这张表 + `AbstractExportTaskStateService`，禁止再 `CREATE TABLE xxx_export_task`。门禁：`scripts/check-async-task-table.sh`（lessons-learned §139）。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ExportController.java` | Controller | 导出任务接口 |
| `service/ExcelExportService.java` | Service | Excel 导出生成与记录数元数据返回 |
| `entity/ExportTask.java` | Entity | 导出任务实体 |
| `repository/ExportTaskRepository.java` | Repository | 导出任务数据访问 |
| `dto/ExportRequest.java` | DTO | 导出请求对象 |
| `dto/ExportResponse.java` | DTO | 导出响应对象 |
