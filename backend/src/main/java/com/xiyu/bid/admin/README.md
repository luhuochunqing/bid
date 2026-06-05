# admin 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
提供管理后台能力，覆盖项目组管理、数据权限配置、接口权限矩阵等管理员操作。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/AdminProjectGroupController.java` | Controller | 暴露 `/api/admin/project-groups` 管理接口 |
| `controller/AdminSettingsController.java` | Controller | 暴露 `/api/admin/settings` 数据权限配置接口 |
| `permissions/` | Admin permissions | 只读扫描 Spring MVC 接口和后端权限表达式，暴露接口权限矩阵 |
| `service/DataScopeConfigService.java` | Service | 数据权限范围配置的读取与更新逻辑 |
| `service/ProjectGroupService.java` | Service | 项目组 CRUD 业务逻辑 |

## 当前边界
- 仅处理管理员级别的配置与组织管理，不承载普通用户业务流程。
- 接口权限矩阵只做入口层可视化和核对，不反向改写 Spring Security 真实授权规则；Service 内部 `hasAuthority` 等二次授权需结合专项审查。
- 从 `com.xiyu.bid.service` 迁移而来，遵循按领域分包的架构规范。
