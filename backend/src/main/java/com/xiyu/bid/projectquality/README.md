# Project Quality 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
项目文本质量模块负责项目文档的文本质量检查结果持久化、问题归一化以及采纳/忽略闭环。
该模块只处理文书质量，不复用合规检查 DTO 或服务。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ProjectQualityController.java` | Controller | 项目文本质量检查 API |
| `service/ProjectQualityService.java` | Service | 文本质量检查编排服务 |
| `service/ProjectQualityAssembler.java` | Service | 文本质量结果 DTO 组装 |
| `entity/ProjectQualityCheck.java` | Entity | 质量检查记录实体 |
| `entity/ProjectQualityIssue.java` | Entity | 质量问题实体 |
| `repository/ProjectQualityCheckRepository.java` | Repository | 检查记录仓储 |
| `repository/ProjectQualityIssueRepository.java` | Repository | 质量问题仓储 |
| `dto/ProjectQualityCheckResponse.java` | DTO | 检查结果 DTO |
| `dto/ProjectQualityIssueResponse.java` | DTO | 质量问题 DTO |
