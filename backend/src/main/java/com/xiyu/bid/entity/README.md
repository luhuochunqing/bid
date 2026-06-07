# Entity 模块 (JPA 实体层)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
Entity 负责表达数据库持久化模型和状态字段，只描述数据结构和生命周期标记，不承载控制器流程。这里是领域对象与表结构的映射边界。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `User.java` | Entity | 用户与权限信息 |
| `Tender.java` | Entity | 标讯信息 |
| `Project.java` | Entity | 项目核心信息 |
| `Task.java` | Entity | 任务信息 |
| `Qualification.java` | Entity | 资质信息 |
| `Case.java` | Entity | 案例信息 |
| `Template.java` | Entity | 模板信息 |
| `AuditLog.java` | Entity | 操作日志，内部沿用 AuditLog 命名 |
