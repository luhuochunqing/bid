# DTO 模块 (数据传输对象包)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
DTO 负责 API 请求/响应结构和跨模块数据传递，只做字段承载与映射，不承载业务规则。这里是前后端契约的稳定出口。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `ApiResponse.java` | DTO | 统一 API 响应包装 |
| `LoginRequest.java` | DTO | 登录请求 |
| `RegisterRequest.java` | DTO | 注册请求 |
| `AuthResponse.java` | DTO | 认证响应 |
| `TenderDTO.java` | DTO | 标讯数据传输对象 |
| `ProjectDTO.java` | DTO | 项目数据传输对象 |
| `TaskDTO.java` | DTO | 任务数据传输对象 |
| `QualificationDTO.java` | DTO | 资质数据传输对象 |
| `CaseDTO.java` | DTO | 案例数据传输对象 |
| `TemplateDTO.java` | DTO | 模板数据传输对象 |
