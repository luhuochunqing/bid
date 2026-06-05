# Project 模块 (项目主数据模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
项目模块负责项目主数据的创建、查询与更新，是投标项目生命周期管理的核心入口，承接项目列表、详情与创建流程的后端契约。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/ProjectController.java` | Controller | 项目接口 |
| `service/ProjectService.java` | Service | 项目业务编排 |
| `dto/ProjectDTO.java` | DTO | 项目视图对象 |
| `dto/ProjectRequest.java` | DTO | 项目创建/更新请求 |
