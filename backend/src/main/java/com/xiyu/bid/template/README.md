# Template 模块 (模板知识模块)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
模板模块负责模板列表、复制、下载与版本信息，是前端模板库与知识复用能力的后端支撑入口。
模板使用记录通过 `templatecatalog` 应用层落库，项目关联记录遵循当前用户项目可见范围；模板视图中的 `useCount` 不向普通用户泄露不可见项目记录数量。

## 当前分层
- `template/` 保留控制器、外部 DTO 和门面 service，继续承接 `/api/knowledge/templates` 协议。
- `templatecatalog/application/` 负责模板创建、更新、查询、版本和使用记录编排。
- `templatecatalog/domain/` 负责三维分类值对象与版本规则。
- `templatecatalog/infrastructure/` 负责基于 JPA 的仓储适配与筛选实现。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/TemplateController.java` | Controller | 模板接口 |
| `service/TemplateService.java` | Facade Service | 模板门面，转发到 templatecatalog 应用层 |
| `dto/TemplateDTO.java` | DTO | 模板视图对象 |
| `dto/TemplateVersionDTO.java` | DTO | 模板版本视图对象 |
| `dto/TemplateCopyRequest.java` | DTO | 模板复制请求 |
| `dto/TemplateDownloadRecordDTO.java` | DTO | 下载记录视图对象 |
| `dto/TemplateDownloadRecordRequest.java` | DTO | 下载记录请求 |
| `dto/TemplateUseRecordDTO.java` | DTO | 使用记录视图对象 |
| `dto/TemplateUseRecordRequest.java` | DTO | 使用记录请求 |
| `../templatecatalog/application/service/*.java` | Application Service | 模板创建/更新/查询/活动编排 |
| `../templatecatalog/domain/valueobject/*.java` | Domain VO | 产品类型、行业、文档类型 |
| `../templatecatalog/domain/service/*.java` | Domain Service | 分类完整性与版本策略 |
| `../templatecatalog/domain/port/TemplateCatalogUseRecordRepository.java` | Domain Port | 使用记录保存与按项目可见范围统计 |
| `../templatecatalog/infrastructure/persistence/*.java` | Infrastructure | 模板仓储适配、组合筛选与使用记录聚合 |
