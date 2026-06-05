# DocumentAssembly 模块 (文档组装模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
文档组装模块负责模板化文档组装与变量替换，是生成投标文档的输出能力。这里聚合模板、组装记录和组装请求边界。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/DocumentAssemblyController.java` | Controller | 文档组装接口 |
| `service/DocumentAssemblyService.java` | Service | 文档组装编排 |
| `entity/AssemblyTemplate.java` | Entity | 组装模板实体 |
| `entity/DocumentAssembly.java` | Entity | 文档组装记录实体 |
| `repository/AssemblyTemplateRepository.java` | Repository | 模板数据访问 |
| `repository/DocumentAssemblyRepository.java` | Repository | 组装记录数据访问 |
| `dto/AssemblyTemplateDTO.java` | DTO | 模板视图对象 |
| `dto/DocumentAssemblyDTO.java` | DTO | 组装记录视图对象 |
| `dto/TemplateCreateRequest.java` | DTO | 创建模板请求 |
| `dto/AssemblyRequest.java` | DTO | 文档组装请求 |
