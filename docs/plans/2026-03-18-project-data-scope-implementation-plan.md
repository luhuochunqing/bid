# 2026-03-18 项目级数据权限接线计划

## 目标
- 在现有 refresh session 会话模型上，补一条可真实上线的“项目级数据权限”主链路。
- 让后端对项目列表、项目详情、项目团队相关读取做真实过滤和拒绝，而不是只靠前端菜单/路由控制。
- 让前端登录恢复后的用户上下文携带项目范围快照，为后续部门级/更复杂规则预留扩展位。

## 范围边界
- 本轮只做“项目优先”，不做部门级过滤。
- 不尝试把 Settings 页里的整套 `allowedDepts / deptAndSub / projectGroupScope` 全量产品化。
- 不重构全站所有资源模块；优先接入 `projects` 主链路，必要时补 `project workflow` 读取链路。

## 当前现状
- `User` 只有 `id/username/email/fullName/role`，没有部门和数据范围字段。
- `AuthResponse` 只返回基础身份信息，不返回数据范围快照。
- `ProjectController` / `ProjectService` 当前对 `ADMIN/MANAGER/STAFF` 通过角色放行后直接 `findAll/findById`。
- `Settings.vue` 中的数据权限配置是前端本地 mock，没有真实后端持久化契约。

## 最小实现方案

### 1. 建立后端可持久化的数据范围来源
- 新增用户项目访问范围持久化表，按 `user_id -> project_id` 建关系。
- 新增对应 JPA entity / repository。
- 提供一个小型权限服务，负责：
  - 读取当前认证用户
  - 判断是否管理员
  - 解析用户允许访问的项目 ID 集合
  - 对项目读取动作执行 `canAccessProject(projectId)` 校验

### 2. 把项目权限接入认证上下文返回
- 扩展 `AuthResponse`，增加最小权限快照字段：
  - `allowedProjectIds`
- 登录、刷新、`/api/auth/me` 都返回该字段。
- 前端 `authApi` / `userStore` 保留该字段，作为恢复后的只读权限快照。

### 3. 把项目读取链路改成真实后端过滤
- `GET /api/projects`
  - `ADMIN` 看全部
  - 非 `ADMIN` 仅返回 `allowedProjectIds` 内项目
- `GET /api/projects/{id}`
  - 非 `ADMIN` 若不在允许范围内则返回 403
- `GET /api/projects/active`
  - 同样按项目范围过滤
- `GET /api/projects/status/{status}`
  - 同样按项目范围过滤
- `GET /api/projects/manager/{managerId}`、`/tender/{tenderId}`、`/search`
  - 查询结果统一再按允许项目过滤

### 4. 前端最小接线
- `auth.js`、`user.js` 扩展用户对象，保留 `allowedProjectIds`
- 项目列表/详情页不做前端假过滤，以后端返回为准
- 如已有页面需要根据权限隐藏入口，只做轻量补充，不把前端变成权限真源

### 5. 测试与验证
- 后端新增：
  - 权限服务单测
  - `ProjectService` 项目范围过滤单测
  - `ProjectController` 403 行为测试
  - Flyway/Testcontainers 迁移断言
- 前端新增/更新：
  - 认证恢复后用户快照保留测试
  - 项目访问受限的 E2E 或接口驱动用例

## 执行步骤
1. 补 migration 与实体：用户项目权限关系表。
2. 补权限服务与认证响应扩展。
3. 改 `ProjectService` / `ProjectController` 读取链路。
4. 改前端 auth store 的用户快照承载。
5. 补测试并执行构建、后端定向测试、E2E。

## 风险与控制
- 风险：当前用户表没有部门字段，若强推部门级权限会把范围扩大过头。
  - 控制：本轮只做项目级范围。
- 风险：部分项目相关旁路接口可能仍能绕过主列表。
  - 控制：优先覆盖 `projects` 主读取接口，并检查 workflow 相关读取入口是否需要同步。
- 风险：前端 Settings 页与真实后端契约仍有差距。
  - 控制：本轮不承诺把整页产品化，只把会话模型与真实项目范围接起来。

## 完成定义
- 非管理员登录后，后端只返回其允许访问的项目。
- 非管理员访问未授权项目详情时返回 403。
- 登录、刷新、`/me` 返回一致的 `allowedProjectIds`。
- 前端会话恢复后仍持有该快照。
- 定向后端测试、前端构建和关键权限 E2E 通过。
