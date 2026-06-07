# Export 模块 (通用导出模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
通用导出模块提供 Excel 导出与导出任务编排能力，供多个业务域复用。这里是跨域导出的基础设施边界，不承载具体业务规则。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ExportController.java` | Controller | 导出任务接口 |
| `service/ExcelExportService.java` | Service | Excel 导出生成与记录数元数据返回 |
| `entity/ExportTask.java` | Entity | 导出任务实体 |
| `repository/ExportTaskRepository.java` | Repository | 导出任务数据访问 |
| `dto/ExportRequest.java` | DTO | 导出请求对象 |
| `dto/ExportResponse.java` | DTO | 导出响应对象 |
