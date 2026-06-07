# Service 模块 (业务逻辑层)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
Service 层承接用例编排、事务边界和跨仓储协作，是领域规则真正落地的位置。这里负责调用 repository、校验状态转移，并向 controller 输出稳定的业务结果。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `AuthService.java` | Service | 认证、注册和令牌管理 |
| `TenderService.java` | Service | 标讯处理与分析 |
| `ProjectService.java` | Service | 项目生命周期与状态流转 |
| `TaskService.java` | Service | 任务分配与进度跟踪 |
| `QualificationService.java` | Service | 资质管理 |
| `CaseService.java` | Service | 案例管理 |
| `TemplateService.java` | Service | 模板管理 |
| `KnowledgeService.java` | Service | 知识能力聚合服务 |
| `AuditLogService.java` | Service | 审计日志记录 |
