# 前端角色权限迁移 — 变更记录

> 变更日期：2026-05-17
> 合并 PR：#281
> 分支：`002-frontend-permission-migration`
> 作者：Claude Opus 4.6

---

## 1. 变更概述

将前端权限控制从**硬编码 Legacy 角色字符串**（`admin` / `manager` / `staff`）迁移到**后端 `menuPermissions` 数组驱动**，以支持后端已扩展的 10 个 `RoleProfile` 角色（含新增的 `bid_specialist`、`admin_staff`）在前端正常登录、路由访问、菜单渲染和页面功能控制。

**变更规模**：27 个文件，+838 行 / -83 行（净增 ~755 行，主要为测试和文档）。

---

## 2. 问题背景

### 2.1 后端已完成角色扩展

2026-05-16 后端完成角色体系扩展（PR #276）：

- `RoleProfileCatalog` 定义 **10 个角色**：`admin`, `auditor`, `manager`, `staff`, `sales`, `bid_lead`, `bid_admin`, `task_executor`, `bid_specialist`, `admin_staff`
- 每个角色有独立的 `menuPermissions` 列表
- `AuthResponse` 返回 `menuPermissions: string[]`

### 2.2 前端仍使用 Legacy 硬编码

前端存在三类硬编码问题：

1. **路由守卫 `meta.roles`**：
   ```javascript
   meta: { roles: ['admin'] }
   ```
   `bid_admin` 用户的 `role` 是 `"bid_admin"`，不等于 `"admin"`，访问 `/settings` 被拒绝。

2. **Sidebar 双检逻辑**：
   ```javascript
   hasRoleAccess(menu.meta?.roles) && hasPermissionAccess(menu.meta?.permissionKeys)
   ```
   `hasRoleAccess` 对 `bid_admin` 返回 `false`，AND 逻辑导致菜单被过滤。

3. **页面级硬编码**（15+ 文件）：
   ```javascript
   const isAdmin = computed(() => userRole === 'admin')
   const canManage = computed(() => role === 'admin' || role === 'manager')
   ```
   新角色（`bid_admin`, `bid_lead`, `sales`, `bid_specialist`, `admin_staff`, `task_executor`）无法命中。

### 2.3 影响

- **上线阻塞**：新角色在测试环境已无法正常访问部分页面（如 `/settings`、`/analytics/dashboard`）。
- **功能缺失**：某些按钮/Tab 对新角色不可见，导致业务流程中断。
- **维护困难**：新增角色必须同时修改前端硬编码，违背"后端单一配置源"原则。

---

## 3. 技术方案

### 3.1 核心决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 权限来源 | `userStore.currentUser.menuPermissions` | 后端 `AuthResponse` 实时返回，是唯一可信源 |
| 路由控制字段 | `meta.permissionKeys` 替代 `meta.roles` | `permissionKeys` 已与后端 `menuPermissions` 对齐 |
| Sidebar 过滤逻辑 | `hasPermissionAccess()` 单一检查 | 彻底移除 `hasRoleAccess()` |
| 工具函数 | 新建 `src/utils/permission.js` | 纯函数，不依赖 store，方便测试和复用 |
| 向后兼容 | `role` 字段保留但不参与权限判断 | 仅用于工作台 UI 展示层（Banner 文案、指标卡片） |

### 3.2 架构变更

**变更前**：

```
AuthResponse.role ──► authNormalizer.role ──► userStore.userRole
       │                                              │
       ▼                                              ▼
router.meta.roles ◄───────────────────────────── hasRouteAccess()
       │
       ▼
Sidebar.hasRoleAccess()
```

**变更后**：

```
AuthResponse.menuPermissions ──► authNormalizer.menuPermissions ──► userStore.menuPermissions
                                          │
           ┌──────────────────────────────┼──────────────────────────────┐
           ▼                              ▼                              ▼
router.meta.permissionKeys          Sidebar.hasPermissionAccess()     页面组件 hasPermission()
       │
       ▼
hasRouteAccess() ──► userStore.hasPermission(key)
```

### 3.3 权限判定规则

```
hasAnyPermission(userPerms, requiredPerms):
  requiredPerms 为空  → true
  userPerms 为空      → true (fallback，兼容旧会话)
  userPerms 含 'all'  → true
  否则                → requiredPerms 任一在 userPerms 中
```

---

## 4. 改动清单

### 4.1 新增文件

| 文件 | 说明 |
|------|------|
| `src/utils/permission.js` | `hasAnyPermission()`, `isAdminRole()` |
| `src/utils/permission.test.js` | 8 个边界测试 |

### 4.2 修改文件

| 文件 | 改动内容 |
|------|----------|
| `src/router/index.js` | 移除 `meta.roles`；路由守卫改为 `permissionKeys` 驱动；`beforeEach` 重定向优化 |
| `src/config/sidebar-menu.js` | 移除所有 `meta.roles`，保留 `meta.permissionKeys` |
| `src/components/layout/Sidebar.vue` | 移除 `hasRoleAccess()` 和 `hasAccess()`，只保留 `hasPermissionAccess()` |
| `src/components/layout/Header.vue` | `canAccessSettings` 简化为 `hasPermission('settings')` |
| `src/views/Bidding/list/helpers.js` | `buildPermissionFlags()` 改为基于 `menuPermissions` |
| `src/views/Bidding/list/useTenderListPage.js` | 调用方改为传入 `userStore.menuPermissions` |
| `src/views/Bidding/list/helpers.spec.js` | 测试用例改为基于 `menuPermissions` |
| `src/views/Dashboard/Workbench.vue` | 6 个 `canViewXxx` 改为 `hasAnyPermission` |
| `src/views/Project/stages/EvaluationStage.vue` | `isManager` 改为权限判断 |
| `src/views/Project/stages/RetrospectiveStage.vue` | `isAdmin` 改为权限判断 |
| `src/views/System/Settings.vue` | `isAdmin` / `canViewAuditLogs` 改为权限判断 |
| `src/views/System/Settings.spec.js` | mock 补充 `hasPermission` 与 `menuPermissions` |
| `src/views/Knowledge/components/qualification/useQualificationPage.js` | `isAdmin` 改为权限判断 |
| `src/composables/projectDetail/useProjectDetailState.js` | `canApproveCurrent` 改为权限判断 |
| `.wiki/pages/roles-and-permissions.md` | 大幅扩充，增加前端权限架构、工具函数、映射表、迁移历史 |

---

## 5. 验证

### 5.1 构建验证

```bash
npm run build
# ✅ version-sync, front-data-boundaries, doc-governance, task-status-literal, vite build 全部通过
```

### 5.2 单元测试

```bash
npm run test:unit
# ✅ 148 个测试文件 / 883 个测试全部通过
```

### 5.3 Pre-commit Hook

- `agent-lock-check` ✅
- `agent-worktree-guard` ✅
- `line-budget` ✅（Workbench.vue 从 445 压到 445，通过）
- `testing-gate` ✅（自动运行 vitest，全部通过）
- `wiki:check` ✅（35 个页面通过）

### 5.4 E2E 验证建议（后续补充）

以下验证应在合并后的集成环境中执行：

| 账号 | 角色 | 验证点 |
|------|------|--------|
| `auditor` / `Test@123` | auditor | 可访问 `/audit-logs`、`/operation-logs`，不可访问 `/settings` |
| `task_executor` / `Test@123` | task_executor | 可访问任务相关页面，可承接标书任务 |
| `bid_admin` / `Test@123` | bid_admin | 可访问 `/settings`、`/analytics/dashboard`、`/operation-logs` |
| `bid_lead` / `Test@123` | bid_lead | 可访问 `/project`、`/bidding`，不可访问 `/settings` |
| `bid_specialist` / `Test@123` | bid_specialist | 可访问任务相关页面，可更新评标 |
| `admin_staff` / `Test@123` | admin_staff | 仅可见资质管理相关菜单 |
| `sales` / `Test@123` | sales | 可访问 `/bidding`、`/project`，有项目创建权限 |
| `admin` / `XiyuAdmin2026!` | admin | 所有页面和菜单正常访问 |

---

## 6. 影响分析

### 6.1 用户影响

| 用户群体 | 影响 |
|----------|------|
| 现有 `admin` / `manager` / `staff` / `auditor` 用户 | **无影响**。向后兼容，访问行为完全一致。 |
| 新角色用户（`bid_admin`, `bid_lead`, `sales`, `bid_specialist`, `admin_staff`, `task_executor`） | **正面影响**。此前被错误拦截的页面和菜单现在正常可用。 |
| 联调/测试团队 | **正面影响**。无需再为每个新角色手动修改前端代码即可测试。 |

### 6.2 开发影响

| 场景 | 影响 |
|------|------|
| 新增角色 | 只需在后端 `RoleProfileCatalog` 定义，前端零改动。 |
| 调整角色权限 | 修改后端 `menuPermissions` 列表即可，前端无需重新部署。 |
| 新增菜单/路由 | 在 `sidebar-menu.js` 和 `router/index.js` 中配置 `meta.permissionKeys`，不再配置 `meta.roles`。 |
| 页面内新增权限控制 | 使用 `userStore.hasPermission(key)` 或 `hasAnyPermission()`。 |
| 编写单元测试 | mock `useUserStore` 时必须提供 `hasPermission` 和 `menuPermissions`。 |

### 6.3 已知限制

1. **工作台指标卡片**：`workbench-role-core.js` 仍按 `admin` / `manager` / `staff` 三支返回不同指标定义。这是 UI 展示语义差异（管理员看团队总览，员工看个人任务），非访问控制问题。如需要按新角色细分指标，需单独设计。
2. **`settings.json` 缓存**：`hasMenuAccessForRole()` 仍读取 settings.json 中的角色权限缓存作为 fallback。长期目标是完全弃用该缓存。
3. **Legacy 角色枚举**：后端 `User.Role` 枚举（`ADMIN` / `MANAGER` / `STAFF`）仍用于部分历史接口和数据库字段。该枚举的退场是后端独立工作项，不在本次前端变更范围内。

---

## 7. 回滚方案

如需回滚，可执行以下任一方案：

### 方案 A：Git 回滚（完整回滚）

```bash
git revert 74611110  # 回滚合并提交
```

**风险**：会丢失所有 Phase 1-3 的改动，新角色再次无法使用。

### 方案 B：兼容性热修复（保留改动，修复问题）

如迁移后某个角色访问异常，最可能的根因是：
1. 后端 `RoleProfileCatalog` 中该角色的 `menuPermissions` 缺失某 key；
2. 前端路由/菜单的 `permissionKeys` 配置错误。

**修复步骤**：
1. 检查后端该角色的 `menuPermissions` 是否包含所需 key。
2. 检查 `src/config/sidebar-menu.js` 或 `src/router/index.js` 中 `permissionKeys` 是否正确。
3. 无需回滚代码，通常只需调整配置即可。

---

## 8. 相关链接

- 合并 PR：#281
- 后端角色扩展 PR：#276
- 规范文档：`.wiki/pages/roles-and-permissions.md`
- 后端角色定义：`backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`
- 前端权限工具：`src/utils/permission.js`
- 路由配置：`src/router/index.js`
- 菜单配置：`src/config/sidebar-menu.js`

---

## 9. 附录：新旧代码对比

### 9.1 路由守卫（旧 → 新）

**旧：**
```javascript
const hasRouteAccess = (to, role) => {
  const requiredRoles = getRequiredRoles(to)
  return requiredRoles.length === 0 || requiredRoles.includes(role)
}
```

**新：**
```javascript
const hasRouteAccess = (to, userStore) => {
  const requiredPermissions = getRequiredPermissions(to)
  if (requiredPermissions.length > 0) {
    return requiredPermissions.some((key) => userStore.hasPermission(key))
  }
  return true
}
```

### 9.2 Sidebar 过滤（旧 → 新）

**旧：**
```javascript
const hasRoleAccess = (roles) => !roles || roles.includes(userStore.userRole)
// ...
if (!hasRoleAccess(menu.meta?.roles) || !hasPermissionAccess(menu.meta?.permissionKeys)) {
  return null
}
```

**新：**
```javascript
// hasRoleAccess 已移除
if (!hasPermissionAccess(menu.meta?.permissionKeys)) {
  return null
}
```

### 9.3 页面权限（旧 → 新）

**旧：**
```javascript
const isAdmin = computed(() => String(userStore.userRole).toLowerCase() === 'admin')
```

**新：**
```javascript
const isAdmin = computed(() => userStore.hasPermission('settings'))
// 或更精确的业务权限：
const canManage = computed(() => userStore.hasPermission('knowledge:qualification:manage'))
```
