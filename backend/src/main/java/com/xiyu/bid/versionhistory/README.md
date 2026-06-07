# VersionHistory 模块 (版本历史模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
版本历史模块负责记录文档版本、对比差异和回滚历史，支撑投标文档的版本治理。这里是文档协作的版本边界，关注快照、差异和回滚动作。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/DocumentVersionController.java` | Controller | 文档版本接口 |
| `service/VersionHistoryService.java` | Service | 版本历史编排 |
| `entity/DocumentVersion.java` | Entity | 文档版本实体 |
| `repository/DocumentVersionRepository.java` | Repository | 版本数据访问 |
| `dto/DocumentVersionDTO.java` | DTO | 版本视图对象 |
| `dto/VersionDiffDTO.java` | DTO | 版本差异视图对象 |
| `dto/VersionCreateRequest.java` | DTO | 创建版本请求 |
