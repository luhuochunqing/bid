# Controller 模块 (REST API 控制器层)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
控制器只负责 HTTP 协议适配、参数校验、权限入口和响应封装，不承载领域规则。这里是对外 API 的第一层边界，所有业务流程都下沉到 service 层。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `AuthController.java` | Controller | 认证、注册和令牌接口 |
| `TenderController.java` | Controller | 标讯管理接口 |
| `ProjectController.java` | Controller | 项目管理与状态流转接口 |
| `TaskController.java` | Controller | 任务分配与进度接口 |
| `QualificationController.java` | Controller | 资质管理接口 |
| `CaseController.java` | Controller | 案例管理接口 |
| `TemplateController.java` | Controller | 模板管理接口 |
| `TestController.java` | Controller | 测试与联调接口 |
