# Auth 模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
认证授权模块提供基于 JWT 的用户认证和授权能力，用于保护 API 端点安全。
该目录负责登录态校验、令牌生成与用户详情加载，不承载业务领域逻辑。
对外提供认证入口和 Spring Security 集成边界。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `JwtUtil.java` | Util | JWT 令牌生成和验证工具 |
| `JwtAuthenticationFilter.java` | Filter | JWT 认证过滤器 |
| `UserDetailsServiceImpl.java` | Service | Spring Security 用户详情服务实现 |
| `controller/` | 子目录 | 登录/注册 API 边界 |
| `controller/AuthController.java` | Controller | 登录/注册端点 |
| `entity/` | 子目录 | 用户实体边界 |
| `entity/User.java` | Entity | 用户实体 |

## 角色授权口径

- Spring Security 基础权限继续使用 `users.role` 对应的 `ROLE_ADMIN`、`ROLE_MANAGER`、`ROLE_STAFF`，避免自定义角色破坏既有业务 API 放行规则。
- `auditor` 是角色档案能力，不写入 `users.role` 枚举；登录时额外授予 `ROLE_AUDITOR`，用于访问审计日志 API。
