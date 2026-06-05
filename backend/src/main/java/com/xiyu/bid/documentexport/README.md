# DocumentExport 模块 (文档导出模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
文档导出模块负责文档导出、归档与导出记录，是文档生命周期的输出侧。这里统一管理导出任务、文件记录和归档记录。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/DocumentExportController.java` | Controller | 文档导出与归档接口 |
| `service/DocumentExportService.java` | Service | 导出与归档编排 |
| `entity/DocumentExport.java` | Entity | 导出任务实体 |
| `entity/DocumentExportFile.java` | Entity | 导出文件记录实体 |
| `entity/DocumentArchiveRecord.java` | Entity | 文档归档记录实体 |
| `repository/DocumentExportRepository.java` | Repository | 导出任务数据访问 |
| `repository/DocumentExportFileRepository.java` | Repository | 导出文件数据访问 |
| `repository/DocumentArchiveRecordRepository.java` | Repository | 归档记录数据访问 |
| `dto/DocumentExportDTO.java` | DTO | 导出任务视图对象 |
| `dto/DocumentExportCreateRequest.java` | DTO | 创建导出任务请求 |
| `dto/DocumentArchiveRecordDTO.java` | DTO | 归档记录视图对象 |
| `dto/DocumentArchiveRecordCreateRequest.java` | DTO | 创建归档记录请求 |
