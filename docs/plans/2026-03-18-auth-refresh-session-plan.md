# P0 认证加固实施计划（2026-03-18）

## 目标

把当前“纯 access JWT + 浏览器存储 + 伪 refresh/logout”升级为最小可发布的认证闭环：

1. `login` 创建 refresh session，并通过 HttpOnly cookie 返回
2. `refresh` 基于 cookie 校验并轮换 refresh session，返回新的 access token
3. `logout` 撤销当前 refresh session，并清理 cookie
4. 前端 access token 改为内存态，页面刷新后通过 refresh cookie 恢复会话

## 当前现状

- 后端 `logout` 只有日志语义，没有撤销语义
- 后端 `refresh` 依赖当前 access token，未使用 refresh token
- 前端运行主链路依赖 `localStorage/sessionStorage` 里的 `token/user`
- Playwright 多数用例直接向 `sessionStorage` 注入登录态

## 最小实现范围

### 后端

- 新增 `refresh_sessions` 表与 JPA 实体、repository
- `AuthService.login()` 生成随机 refresh token，保存哈希后的 session 记录
- `AuthController.login()` 设置 HttpOnly refresh cookie
- `AuthController.refresh()` 改为从 cookie 读取 refresh token，不再要求现有 access token
- `AuthService.refreshToken()` 校验、轮换并撤销旧 refresh session
- `AuthController.logout()` 改为从 cookie 撤销当前 refresh session，并清理 cookie
- 保持 access token 仍走 `Authorization: Bearer`

### 前端

- 新增内存态 token/session 模块
- `httpClient` 改为从内存读取 access token，并在 401 时尝试 refresh-and-retry
- `authApi.login/refresh/logout` 与 cookie 模式对齐
- `userStore` 不再把 token 持久化到 browser storage
- `router` 改为基于 `restoreSession()` 的真实恢复逻辑判断登录状态

### 测试

- 补后端 controller/service 测试，覆盖 cookie 设置、refresh 轮换、logout 撤销
- 至少改造一条关键 Playwright 认证用例，避免直接写 `sessionStorage.token`

## 风险与边界

- 当前 `SecurityConfig` 全局关闭 CSRF；本轮只做最小闭环，不扩展到完整 cookie-CSRF 防护体系
- Playwright 全量改造范围较大，本轮优先覆盖关键主链路和一条代表性权限用例
- 若需要“记住我”与 session cookie 区分，可在本轮保留接口字段，但先不做复杂策略扩展
