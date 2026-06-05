# Repository 模块 (数据访问层包)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
Repository 只定义数据访问接口和查询边界，不写业务判断。这里负责把领域对象与数据库读写隔离开，供 service 层调用。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `UserRepository.java` | Repository | 用户数据访问 |
| `TenderRepository.java` | Repository | 标讯数据访问 |
| `ProjectRepository.java` | Repository | 项目数据访问 |
| `TaskRepository.java` | Repository | 任务数据访问 |
| `QualificationRepository.java` | Repository | 资质数据访问 |
| `CaseRepository.java` | Repository | 案例数据访问 |
| `TemplateRepository.java` | Repository | 模板数据访问 |
| `AuditLogRepository.java` | Repository | 操作日志数据访问，内部沿用 AuditLog 命名 |
