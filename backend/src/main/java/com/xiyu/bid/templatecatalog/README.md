# TemplateCatalog 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
`templatecatalog` 是模板知识域的增量收敛模块，负责“产品类型 + 行业 + 文档类型”三维分类、模板版本规则，以及模板查询/创建/更新相关的用例编排。
模板使用记录属于项目关联数据：创建前必须校验当前用户可见项目，查询模板列表/详情和下载记录响应的 `useCount` 只统计当前用户可见项目与未绑定项目的记录；管理员保留全量统计。

## 分层约束
- `application/`：只做用例编排、DTO 映射与版本初始化，不承载领域规则计算。
- `domain/`：只放三维分类值对象、版本策略、分类完整性规则与仓储端口。
- `infrastructure/`：只负责 JPA 仓储适配、Specification 查询与持久化交互。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `application/service/TemplateCatalogCommandAppService.java` | Application Service | 模板创建、更新、复制编排 |
| `application/service/TemplateCatalogQueryAppService.java` | Application Service | 模板查询与详情读取 |
| `application/service/TemplateCatalogVersionAppService.java` | Application Service | 版本历史读取 |
| `application/service/TemplateCatalogActivityAppService.java` | Application Service | 下载与使用记录编排，使用记录写入前执行项目可见性校验，下载响应返回项目范围内的使用次数 |
| `domain/valueobject/*.java` | Domain VO | 三维分类受控字典 |
| `domain/service/TemplateClassificationPolicy.java` | Domain Service | 三维分类完整性校验 |
| `domain/service/TemplateVersionPolicy.java` | Domain Service | 版本初始化与递增规则 |
| `domain/port/TemplateCatalogUseRecordRepository.java` | Domain Port | 使用记录保存与按项目可见范围统计 |
| `infrastructure/persistence/TemplateCatalogRepositoryAdapter.java` | Infrastructure | 模板组合筛选仓储适配 |
| `infrastructure/persistence/TemplateCatalogUseRecordRepositoryAdapter.java` | Infrastructure | 使用记录 JPA 适配与可见范围聚合 |

## 对外关系
- `/api/knowledge/templates` 对外入口仍由 `template/` 模块承接。
- `template/service/TemplateService.java` 作为门面，转发到 `templatecatalog` 各应用服务。
- 项目可见性统一复用 `ProjectAccessScopeService` 与 `ProjectLinkedRecordVisibilityPolicy`，不在本模块建立并行权限体系。
- 本模块当前不合并 `documents/assembly` 语义，保持知识库模板域独立。
