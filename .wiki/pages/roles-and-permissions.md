---
title: 角色与权限
space: engineering
category: reference
tags: [角色, 权限, 用户, 认证, RBAC]
sources:
  - .wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx
  - src/router/index.js
  - AGENTS.md
  - backend/src/main/java/com/xiyu/bid/auth/README.md
backlinks:
  - _index
  - business-process
  - contract-constraints
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
  - implementation/attachment6-function-list-trace
  - overview
created: 2026-04-15
updated: 2026-05-28
health_checked: 2026-06-05
---
# 角色与权限

## 1. 角色体系

平台采用 RBAC（基于角色的访问控制）模型。当前系统内部定义 **10 个 `RoleProfile`**，同时保留 **3 个 Legacy 角色枚举** 用于向后兼容。

### 1.1 内部 RoleProfile 定义

| 角色代码 | 中文名 | 描述 | dataScope | 系统角色 |
|---|---|---|---|---|
| `admin` | 管理员 | 系统管理员，拥有所有权限 | `all` | 是 |
| `auditor` | 审计员 | 审计人员，可查看全量审计日志和个人操作日志 | `all` | 是 |
| `manager` | 经理 | 部门经理，可查看项目、知识库、资源与分析数据 | `dept` | 是 |
| `staff` | 员工 | 业务人员，可查看工作台、标讯、项目、知识库与资源 | `self` | 是 |
| `sales` | 销售/业务负责人 | 立项发起人，维护客户与开标信息 | `self` | 是 |
| `bid_lead` | 投标负责人 | 标书编制与评标推进负责人 | `self` | 是 |
| `bid_admin` | 投标部门管理员 | 复盘审核与结项闸门审批 | `dept` | 是 |
| `task_executor` | 任务执行人 | 标书任务承接与执行 | `self` | 是 |
| `bid_specialist` | 投标专员 | 投标辅助、标书审核与任务处理 | `self` | 是 |
| `admin_staff` | 行政人员 | 资质证书管理与行政事务 | `self` | 是 |

> `bid_specialist` 与 `admin_staff` 为 2026-05-16 新增角色，用于对齐产品蓝图 §1.3 目标用户。

### 1.2 Legacy 角色回退映射

部分历史接口仍使用 3 个 Legacy 角色（`ADMIN` / `MANAGER` / `STAFF`）。`RoleProfileCatalog.legacyRoleForCode()` 定义回退规则：

| RoleProfile 代码 | Legacy 回退 |
|---|---|
| `admin` | `ADMIN` |
| `manager` | `MANAGER` |
| `bid_admin`, `bid_lead`, `sales` | `MANAGER` |
| `staff`, `auditor`, `task_executor`, `bid_specialist`, `admin_staff` | `STAFF` |

> 前端路由守卫与 Sidebar 已完全迁移到 `menuPermissions` 驱动，不再依赖 Legacy 角色进行访问控制。`role` 字段仅用于工作台 Banner/指标等 UI 展示层。

### 1.3 产品蓝图角色对齐

产品蓝图《西域数智化投标管理平台 - 产品蓝图-初稿-V1.1》定义 6 个目标用户角色，全部在系统中有独立 `RoleProfile`：

| 产品蓝图角色 | RoleProfile 代码 | 说明 |
|---|---|---|
| 系统管理员 | `admin` | 已有 |
| 投标管理员 | `bid_admin` | 已有 |
| 投标组长 | `bid_lead` | 已有 |
| 投标专员 | `bid_specialist` | **新增** |
| 项目负责人 | `sales` | 已有 |
| 行政人员 | `admin_staff` | **新增** |

## 2. 联调与演示账号

联调、演示和验收统一使用 API 模式与后端真实鉴权体系。当前约定账号如下：

| 用户名 | 角色 | 用途 |
|--------|------|------|
| 小王 | 销售 / 普通员工（sales/staff） | 标讯跟进、项目执行、任务处理、费用申请 |
| 张经理 | 经理（manager） | 项目审批、任务分配、进度监控、数据分析 |
| 李总 | 管理员（admin） | 系统设置、全局数据分析、用户与权限管理 |

## 3. 路由级权限

前端路由与 Sidebar 统一通过 `meta.permissionKeys` 字段控制访问，直接对接后端返回的 `menuPermissions` 数组。未配置 `permissionKeys` 的路由对所有已登录用户开放。

### 需要特定权限的路由

| 路由路径 | 路由名称 | 页面标题 | 所需权限 |
|----------|----------|----------|----------|
| `/analytics/dashboard` | AnalyticsDashboard | 数据分析 | `analytics` |
| `/settings` | Settings | 系统设置 | `settings` |
| `/settings/workflow-forms` | WorkflowFormDesigner | 流程表单配置 | `settings`, `settings-workflow-forms` |
| `/settings/alert-rules` | AlertRules | 告警规则 | `settings` |
| `/settings/alert-history` | AlertHistory | 告警历史 | `settings` |
| `/audit-logs` | AuditLogs | 审计日志 | `audit-logs` |

### 对所有已登录用户开放的路由

| 路由路径 | 路由名称 | 页面标题 |
|----------|----------|----------|
| `/dashboard` | Dashboard | 工作台 |
| `/bidding` | Bidding | 标讯中心 |
| `/bidding/:id` | BiddingDetail | 标讯详情 |
| `/bidding/ai-analysis/:id` | BiddingAIAnalysis | AI 分析 |
| `/bidding/customer-opportunities` | CustomerOpportunityCenter | 客户商机中心 |
| `/ai-center` | AICenter | AI 智能中心 |
| `/project` | ProjectList | 投标项目 |
| `/project/create` | ProjectCreate | 创建项目 |
| `/project/:id` | ProjectDetail | 项目详情 |
| `/knowledge/qualification` | Qualification | 资质库 |
| `/knowledge/case` | Case | 案例库 |
| `/knowledge/case/detail` | CaseDetail | 案例详情 |
| `/knowledge/template` | Template | 模板库 |
| `/resource/expense` | Expense | 费用管理 |
| `/resource/account` | Account | 账户管理 |
| `/resource/bid-result` | BidResult | 投标结果闭环 |
| `/resource/bar` | BAR | 可投标能力检查 |
| `/resource/bar/sites` | BAR_SiteList | 站点台账 |
| `/resource/bar/site/:id` | BAR_SiteDetail | 站点详情 |
| `/resource/bar/sop/:siteId` | BAR_SOPDetail | 找回 SOP |
| `/document/editor/:id` | DocumentEditor | 标书编辑器 |
| `/inbox` | Inbox | 通知中心 |

### API 模式路由治理

API 模式是唯一交付路径。未纳入 SOW V1.4、蓝图确认件或正式变更单的演示性入口，应在客户环境隐藏或重定向。

| 类型 | 处理口径 |
|---|---|
| SOW/蓝图确认范围内 | 按角色权限开放 |
| 演示性、未闭环、未确认范围 | 隐藏、下线或替换处理 |
| 需要新增的权限或流程 | 进入变更评估，书面确认后实施 |

### 路由守卫逻辑

1. 未登录用户访问需要认证的页面时，自动重定向到 `/login`。
2. 已登录用户访问 `/login` 时，自动重定向到 `/dashboard`。
3. 已登录用户访问权限不足的页面时，自动重定向到第一个有权限的路由（优先 `/dashboard`，否则取 Sidebar 中第一个可见菜单）。
4. 首次加载时会尝试恢复 localStorage 中的会话状态。

## 4. 认证机制

平台采用 JWT + Spring Security 的认证授权架构。

### 认证流程

```
用户登录 -> AuthController 验证凭据 -> JwtUtil 生成 JWT 令牌 -> 返回令牌给前端
    |
前端存储令牌 -> 后续请求携带 Authorization: Bearer <token>
    |
JwtAuthenticationFilter 拦截请求 -> JwtUtil 校验令牌 -> UserDetailsServiceImpl 加载用户
    |
Spring Security 上下文注入用户信息 -> 业务接口正常响应
```

### 后端 Auth 模块组成

| 组件 | 职责 |
|------|------|
| `JwtUtil.java` | JWT 令牌的生成、解析和验证 |
| `JwtAuthenticationFilter.java` | HTTP 请求拦截器，从请求头提取并校验 JWT |
| `UserDetailsServiceImpl.java` | Spring Security 用户详情服务，从数据库加载用户信息 |
| `AuthController.java` | 登录/注册 API 端点 |
| `User.java` | 用户实体定义 |

### 前端会话管理

- 登录成功后，令牌和用户信息存储在 Pinia store 并持久化到 localStorage。
- 页面刷新时通过 `restoreSession()` 从 localStorage 恢复会话。
- 收到 401 响应时自动清除 store 状态并重定向到登录页。

详细架构说明请参阅 [[architecture]]。

## 5. 数据权限

平台支持数据范围配置，根据用户角色、组织归属、显式项目授权和项目组控制可见数据范围。本轮项目数据权限修复已完成 P0/P1/P2 收口，真实 API 单一路径下的项目关联接口统一复用 `ProjectAccessScopeService` 或等价访问守卫，不另建并行权限体系。

| RoleProfile | dataScope | 数据可见范围 |
|---|---|---|
| `admin`, `auditor`, `bid_admin`, `bid_lead` | `all` | 全部数据，跨部门、跨区域（标讯蓝图 §4.2.1：投标管理员/投标组长当前版本可见全部标讯数据） |
| `manager` | `dept` | 本部门及下属团队数据 |
| `sales`, `task_executor`, `bid_specialist`, `admin_staff`, `staff` | `self` | 仅限个人负责的数据 |

数据权限通过后端的 DataScopeConfig 机制和项目访问范围服务实现，支持按组织架构层级、项目组、团队成员、显式项目授权进行隔离。

### 项目数据权限修复收口

| 范围 | 当前状态 |
|---|---|
| P0 跨项目读写风险 | 费用、资源费用台账、文档编辑/导出/版本、投标结果、标讯、任务、批量操作已补项目访问断言或可见项目过滤。 |
| P1 统计、导出、AI/分析泄露风险 | 统计看板、导出、AI/分析、审批/聚合、项目质量已按当前用户可见项目集合收口。 |
| P2 证据不足或局部补强 | 日历、工作台、证书借阅、资质借阅、模板使用记录、告警历史已完成补强；告警历史因 `relatedId` 无可靠项目映射，已收紧到管理角色。 |
| 自动化门禁 | 新增后端项目权限覆盖门禁，扫描带 `projectId` 或引用项目关联 DTO/实体的 Controller/Service，要求命中统一项目访问守卫或显式豁免清单。 |

详细说明参见 [[data-permission-hardening]]。

## 6. 与业务流程的关联

不同角色在投标全流程（参见 [[business-process]]）各阶段承担不同职责：

| 阶段 | `admin` | `manager` / `bid_admin` | `sales` / `bid_lead` | `bid_specialist` / `task_executor` / `staff` | `admin_staff` |
|------|---------|------------------------|----------------------|-----------------------------------------------|---------------|
| 标讯获取 | 全局监控 | 分配、指派跟进 | 领取、跟进反馈 | 领取、跟进反馈 | - |
| 项目立项 | - | 立项审批、资源协调 | 创建项目、CRM 同步 | 填写信息、辅助立项 | - |
| 任务分解 | - | 审核任务分配 | 分配任务 (`task.assign`) | 执行任务、上传交付物 | - |
| 标书编制 | - | 审核标书、用印审批 | 技术方案编制、评标推进 | 标书辅助、审核 | - |
| 投标提交 | - | 用印审批、封装确认 | 提交投标文件、状态跟踪 | 辅助提交 | - |
| 结果闭环 | 全局数据分析 | 结果分析、团队复盘 | 中标登记、竞对录入 | 辅助录入 | - |
| 数据分析 | 管理驾驶舱 | 部门级看板 | 个人/项目维度 | - | - |
| 资质管理 | - | - | - | - | 资质证书管理 |
| 系统设置 | 用户/权限/日志 | - | - | - | - |

> 上表按产品蓝图 §1.3 职责描述映射。实际系统中 `bid_admin`（`dept` 范围）与 `bid_lead`（`self` 范围）的职责描述有重叠，区分点在数据范围而非功能菜单。`bid_specialist` 与 `task_executor` 也有部分重叠，前者侧重投标辅助与审核，后者侧重任务承接与执行。

## 7. 前端权限控制架构

### 7.1 设计原则

前端权限控制遵循以下原则：

1. **单一权限源**：唯一权限来源是后端 `AuthResponse` 返回的 `menuPermissions: string[]`，不再基于硬编码的 `role` 字符串判断。
2. **向后兼容**：`userStore.currentUser.role` 仍保留，但仅用于工作台 Banner 文案、指标卡片展示等 UI 层，不参与任何访问控制决策。
3. **权限判定 fallback**：当 `menuPermissions` 为空数组时，视为无限制（兼容历史会话或测试场景）。
4. **通配符支持**：`menuPermissions` 包含 `'all'` 时，无条件通过所有权限检查。

### 7.2 数据流

```
后端 RoleProfileCatalog
    │
    ▼
AuthResponse.menuPermissions (string[])
    │
    ▼
authNormalizer.js → normalizeUser()
    │
    ▼
userStore.currentUser.menuPermissions
    │
    ├──► router/index.js  hasRouteAccess()
    │         └── meta.permissionKeys 匹配
    ├──► Sidebar.vue      hasPermissionAccess()
    │         └── sidebarMenuConfig[].meta.permissionKeys 匹配
    └──► 页面组件         hasPermission() / hasAnyPermission()
              └── 按钮/Tab/操作显隐控制
```

### 7.3 核心文件职责

| 文件 | 职责 | 关键 API |
|------|------|----------|
| `src/api/authNormalizer.js` | 将后端原始 payload 中的 `menuPermissions` 原样透传到 `normalizedUser` | `normalizeUser()` |
| `src/stores/user.js` | Pinia store，暴露 `hasPermission(key)` getter 和 `menuPermissions` getter | `useUserStore()` |
| `src/utils/permission.js` | 纯函数工具，独立于 store，供任意模块调用 | `hasAnyPermission()`, `isAdminRole()` |
| `src/router/index.js` | 路由守卫，基于 `meta.permissionKeys` 判定是否放行 | `hasRouteAccess()` |
| `src/config/sidebar-menu.js` | 菜单配置，每个菜单项声明 `meta.permissionKeys` | `sidebarMenuConfig` |
| `src/components/layout/Sidebar.vue` | 渲染菜单时过滤无权限项 | `hasPermissionAccess()` |

### 7.4 与后端 `RoleProfileCatalog` 的对接

后端 `RoleProfileCatalog.seedDefinitions()` 定义了 10 个角色的 `menuPermissions` 列表。前端不硬编码这些列表，而是在登录/会话恢复时通过 `AuthResponse` 动态获取。因此：

- 新增角色只需在后端定义并分配 `menuPermissions`，前端无需任何改动即可支持。
- 调整角色的菜单权限只需修改后端 `RoleProfileCatalog`，无需重新部署前端。

## 8. 前端权限工具函数

### 8.1 `hasAnyPermission(userPermissions, requiredPermissions)`

位置：`src/utils/permission.js`

用途：检查用户权限数组是否包含任一所需权限。语义：**默认拒绝，仅当明确授权时放行**。

```javascript
hasAnyPermission(['dashboard', 'bidding'], ['settings'])        // false
hasAnyPermission(['dashboard', 'bidding'], ['bidding'])          // true
hasAnyPermission(['all'], ['settings', 'audit-logs'])            // true
hasAnyPermission([], ['settings'])                                  // false (deny-by-default)
hasAnyPermission(['dashboard'], [])                                 // true (no restriction needed)
hasAnyPermission(undefined, ['dashboard'])                         // false (undefined = no perms)
```

边界规则：
- `requiredPermissions` 为空数组 → `true`（无需限制时显式放行）
- `userPermissions` 为空或 `undefined` → **`false`**（安全默认：无权限即拒绝）
- `userPermissions` 含 `'all'` → `true`（全局授权覆盖）

### 8.2 `isAdminRole(roleCode)`

位置：`src/utils/permission.js`

用途：判断角色代码是否为 admin。仅用于极少量 UI 展示逻辑（如 Header 下拉菜单中的「系统设置」入口）。

```javascript
isAdminRole('admin')        // true
isAdminRole('bid_admin')    // false
isAdminRole('manager')      // false
```

### 8.3 `userStore.hasPermission(key)`

位置：`src/stores/user.js`（getter）

用途：Pinia store 级别的单权限检查，组件内最常用。

```javascript
const userStore = useUserStore()
userStore.hasPermission('settings')      // boolean
userStore.hasPermission('all')           // true 时所有权限通过
```

### 8.4 `userStore.menuPermissions`

位置：`src/stores/user.js`（getter）

用途：获取当前用户的完整权限数组，用于批量检查或传参给 `hasAnyPermission()`。

```javascript
const userStore = useUserStore()
const perms = userStore.menuPermissions  // ['dashboard', 'bidding', 'project']
```

## 9. 各角色 menuPermissions 映射

下表来自后端 `RoleProfileCatalog`（`backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`），是前端权限判断的事实来源。

| 角色 | `menuPermissions` |
|------|-------------------|
| `admin` | `all` |
| `auditor` | `dashboard`, `operation-logs`, `audit-logs` |
| `manager` | `dashboard`, `operation-logs`, `bidding`, `project`, `knowledge`, `resource`, `ai-center`, `analytics`, `settings` |
| `staff` | `dashboard`, `operation-logs`, `dashboard.quickStart`, `bidding`, `project`, `knowledge`, `resource`, `ai-center` |
| `sales` | `dashboard`, `bidding`, `project`, `knowledge`, `project.create`, `project.view`, `deposit.return.fill`, `bidding.create` |
| `bid_lead` | `dashboard`, `bidding`, `project`, `knowledge`, `resource`, `task.assign`, `evaluation.update`, `result.register`, `retrospective.submit`, `closure.request`, `bidding.manage`, `bidding.create`, `bidding.delete` |
| `bid_admin` | `dashboard`, `operation-logs`, `bidding`, `project`, `knowledge`, `resource`, `analytics`, `settings`, `task.review`, `retrospective.review`, `closure.review`, `lead.assign`, `bidding.manage`, `bidding.create`, `bidding.delete`, `bidding.sync` |
| `task_executor` | `dashboard`, `project`, `knowledge`, `task.view.own`, `task.handle.own` |
| `bid_specialist` | `dashboard`, `bidding`, `project`, `knowledge`, `resource`, `task.view.own`, `task.handle.own`, `evaluation.update`, `bidding.create` |
| `admin_staff` | `dashboard`, `knowledge`, `resource`, `certificate.manage`, `qualification.view` |

### 9.1 权限 Key 语义对照

| 权限 Key | 语义 | 对应菜单/功能 |
|----------|------|--------------|
| `dashboard` | 工作台基础访问 | `/dashboard` |
| `dashboard.quickStart` | 工作台快速发起 | 工作台 QuickStart 组件 |
| `dashboard:view_tender_list` | 工作台标讯列表卡片 | Workbench 条件渲染 |
| `dashboard:view_project_list` | 工作台项目列表卡片 | Workbench 条件渲染 |
| `bidding` | 标讯中心 | `/bidding` |
| `project` | 投标项目 | `/project` |
| `project.create` | 创建项目 | 项目创建按钮/表单 |
| `knowledge` | 知识库 | `/knowledge/*` |
| `resource` | 资源管理 | `/resource/*` |
| `ai-center` | AI 智能中心 | `/ai-center` |
| `analytics` | 数据分析 | `/analytics/dashboard` |
| `settings` | 系统设置 | `/settings/*` |
| `operation-logs` | 操作日志 | `/operation-logs` |
| `audit-logs` | 审计日志 | `/audit-logs` |
| `task.assign` | 任务分配 | 项目详情-任务分配 |
| `task.review` | 任务审核 | 评标/审批权限 |
| `task.view.own` | 查看自己的任务 | 任务列表过滤 |
| `task.handle.own` | 处理自己的任务 | 任务操作按钮 |
| `evaluation.update` | 更新评标 | EvaluationStage |
| `retrospective.submit` | 提交复盘 | RetrospectiveStage |
| `retrospective.review` | 复盘审核 | RetrospectiveStage 审核表单 |
| `closure.review` | 结项审核 | 结项审批 |
| `lead.assign` | 指派负责人 | 项目负责人分配 |
| `certificate.manage` | 证书管理 | 资质库管理操作 |
| `qualification.view` | 资质查看 | 资质库查看 |
| `deposit.return.fill` | 保证金退还填写 | 费用管理-保证金 |
| `bidding.manage` | 管理标讯（分配/转派） | 标讯中心-分配/转派按钮 |
| `bidding.create` | 创建标讯（人工录入/批量导入） | 标讯中心-手动录入/批量导入按钮 |
| `bidding.delete` | 删除标讯 | 标讯中心-删除按钮 |
| `bidding.sync` | 标讯源配置 | 标讯中心-标讯源配置入口 |

## 10. 页面级权限判断规范

### 10.1 应使用权限判断的场景

以下场景**必须**使用 `hasPermission()` 或 `hasAnyPermission()`，禁止硬编码 `role === 'xxx'`：

| 场景 | 反例（禁止） | 正例（推荐） |
|------|-------------|-------------|
| 按钮显隐 | `userRole === 'admin'` | `userStore.hasPermission('settings')` |
| Tab 显隐 | `['admin','auditor'].includes(role)` | `userStore.hasPermission('audit-logs')` |
| 路由访问 | `meta.roles: ['admin']` | `meta.permissionKeys: ['settings']` |
| 菜单过滤 | `hasRoleAccess(['admin'])` | `hasPermissionAccess(['settings'])` |
| 操作权限 | `role === 'manager'` | `userStore.hasPermission('task.review')` |
| 组件渲染 | `currentUserRole.value === 'staff'` | `hasAnyPermission(menuPermissions, ['bidding'])` |

### 10.2 组件内使用示例

**Vue `<script setup>` 中：**

```javascript
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { hasAnyPermission } from '@/utils/permission'

const userStore = useUserStore()

// 单权限检查
const canManageSettings = computed(() => userStore.hasPermission('settings'))

// 多权限任一满足
const canViewAnalytics = computed(() =>
  hasAnyPermission(userStore.menuPermissions, ['analytics', 'all'])
)

// 复杂条件组合
const canApprove = computed(() =>
  userStore.hasPermission('task.review') || userStore.hasPermission('all')
)
```

**模板中：**

```vue
<el-button v-if="userStore.hasPermission('settings')">系统设置</el-button>
<el-tab-pane v-if="userStore.hasPermission('audit-logs')" label="审计日志" />
```

### 10.3 路由配置示例

```javascript
{
  path: 'analytics/dashboard',
  name: 'AnalyticsDashboard',
  component: () => import('@/views/Analytics/Dashboard.vue'),
  meta: {
    title: '数据分析',
    permissionKeys: ['analytics', 'analytics-dashboard']
  }
}
```

路由守卫会自动收集 `matched` 链路上的所有 `permissionKeys` 去重后进行检查。只要用户拥有其中任一权限即可访问。

### 10.4 新增权限 Key 的流程

1. 在后端 `RoleProfileCatalog` 中为对应角色添加新的 `menuPermissions` 项。
2. 在前端 `src/config/sidebar-menu.js` 中为对应菜单添加 `meta.permissionKeys`。
3. 如为页面内按钮/功能，在对应 Vue 组件中使用 `userStore.hasPermission(key)` 控制显隐。
4. 如需单元测试覆盖，在对应 `.spec.js` 中为 mock 的 `useUserStore` 补充 `hasPermission` 和 `menuPermissions`。

## 11. 迁移历史记录

### 11.1 2026-05-17 前端角色权限迁移（PR #281）

**背景**：后端角色体系已完成扩展（PR #276），`RoleProfile` 从 8 个增至 10 个，新增 `bid_specialist`（投标专员）和 `admin_staff`（行政人员）。但前端仍大量使用硬编码的 `if (role === 'ADMIN')` 判断，导致新角色在实际使用中被路由守卫和页面级权限控制错误拦截。

**根因**：
1. `authNormalizer.js` 中 `role = String(authPayload?.roleCode).toLowerCase()`，`bid_admin` 用户的 `role` 是 `"bid_admin"`，而非 Legacy 回退的 `"manager"`。
2. 路由守卫 `meta.roles` 检查 `bid_admin` 不等于 `"admin"`，访问 `/settings` 被拒绝。
3. 页面级硬编码 `userRole === 'admin'` / `'manager'` 导致新角色无法命中功能按钮/Tab。
4. Sidebar 双检逻辑 `hasRoleAccess && hasPermissionAccess` 中前者过滤新角色。

**方案**：分三期完成迁移。

#### Phase 1 — 紧急修复（路由守卫 + Sidebar）

- `src/router/index.js`：`hasRouteAccess()` 增加 `permissionKeys` 回退；`beforeEach` 重定向逻辑优化（权限不足时不再无脑跳 `/dashboard`，而是寻找第一个有权限的路由）；为缺失 `permissionKeys` 的路由补充该字段。
- `src/components/layout/Sidebar.vue`：`filteredMenus` 改为 `roleOK || permOK` 的 OR 逻辑。
- 新建 `src/utils/permission.js`：提供 `hasAnyPermission()` 和 `isAdminRole()` 纯函数。
- 新建 `src/utils/permission.test.js`：8 个边界测试。

#### Phase 2 — 页面级硬编码清理

逐文件替换 `role === 'xxx'` 为权限驱动判断：

| 文件 | 改动 |
|------|------|
| `src/views/Bidding/list/helpers.js` | `buildPermissionFlags()` 改为接收 `menuPermissions` 数组 |
| `src/views/Bidding/list/useTenderListPage.js` | 调用方改为传入 `userStore.menuPermissions` |
| `src/views/Dashboard/Workbench.vue` | 6 个 `canViewXxx` 移除 `role === 'staff'` / `'manager'` 回退，改为 `hasAnyPermission` |
| `src/views/Project/stages/EvaluationStage.vue` | `isManager` 改为 `hasPermission('project:evaluate'/'task.review'/'lead.assign')` |
| `src/views/Project/stages/RetrospectiveStage.vue` | `isAdmin` 改为 `hasPermission('retrospective.review')` |
| `src/views/System/Settings.vue` | `isAdmin` / `canViewAuditLogs` 改为权限判断 |
| `src/views/System/Settings.spec.js` | 更新 mock，补充 `hasPermission` 与 `menuPermissions` |
| `src/components/layout/Header.vue` | `canAccessSettings` 简化为 `hasPermission('settings')` |
| `src/views/Knowledge/.../useQualificationPage.js` | `isAdmin` 改为权限判断 |
| `src/composables/projectDetail/useProjectDetailState.js` | `canApproveCurrent` 改为权限判断 |

#### Phase 3 — Legacy 角色体系退场

- `src/router/index.js`：彻底移除 `meta.roles` 和所有 legacy role 检查逻辑，路由守卫仅保留 `permissionKeys` 驱动。
- `src/config/sidebar-menu.js`：彻底移除所有菜单项的 `meta.roles`。
- `src/components/layout/Sidebar.vue`：移除 `hasRoleAccess()` 与 `hasAccess()` 包装，只保留 `hasPermissionAccess()`。
- `.wiki/pages/roles-and-permissions.md`：更新路由权限章节，移除 Legacy 角色回退映射中关于前端路由守卫的描述，增加权限 Key 语义对照表。

**验证结果**：
- `npm run build` ✅
- `npm run test:unit` ✅ 148 个测试文件 / 883 个测试全部通过
- 所有 pre-commit hook（agent-lock、line-budget、testing-gate、wiki-check）✅

**影响范围**：
- 所有新角色（`bid_admin`, `bid_lead`, `sales`, `bid_specialist`, `admin_staff`, `task_executor`）现在可以正常登录并访问其有权限的页面和菜单。
- `admin`, `manager`, `staff`, `auditor` 等既有角色的访问行为保持不变（向后兼容）。
- 后端 `RoleProfileCatalog` 是唯一权限配置源，前端无需重新部署即可支持角色权限调整。

### 11.2 未来改进方向

1. **工作台指标卡片**：当前 `workbench-role-core.js` 仍按 `admin` / `manager` / `staff` 分三支返回不同指标定义。理想情况下应改为基于 `menuPermissions` 动态组装，但因涉及 UI 展示文案和指标含义的语义差异，暂保留角色分支，仅将访问控制与数据权限分离。
2. **`authNormalizer.js` 的 `legacyRole` 字段**：当前未暴露 `legacyRole`。如未来有外部系统（如飞书/企微连接器）必须接收 Legacy 角色枚举，可在此增加 `legacyRole: RoleProfileCatalog.legacyRoleForCode(roleCode)` 字段，但前端内部不再使用。
3. **settings.json 缓存同步**：`hasMenuAccessForRole()` 仍读取 settings.json 中的角色权限缓存作为 fallback。长期目标是完全弃用该缓存，全部使用 `userStore.currentUser.menuPermissions` 实时数据。
