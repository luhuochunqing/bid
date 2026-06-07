# Dashboard 工作台模块按角色显隐 + 数据范围 — 产品需求与实施计划

| 项目 | 内容 |
| --- | --- |
| 状态 | **待实施**（已完成需求分析与方案设计，等排期） |
| 提出日期 | 2026-05-13 |
| 优先级 | P2（功能增强，非阻塞） |
| 工作量评估 | 前端 2-3h + 后端 0-2h + 手工回归 1h，无 DB 迁移 |
| 实施分支建议 | `feature/dashboard-role-visibility-data-scope` |

---

## 一、需求背景

### 1.1 用户原始诉求

> "http://127.0.0.1:1315/dashboard 工作台有很多元素（就是每个展示的内容块）这个能不能根据角色进行设置 就是显示什么 不显示什么 后台需要有一个配置的地方 然后每个角色看到的数据范围也要设置"

### 1.2 拆解后的需求

1. **模块显隐**：`/dashboard` 工作台上的每个卡片/模块，按**角色**控制可见性
2. **数据范围**：同一模块在不同角色下展示的数据范围不同（全部 / 本部门 / 本人）
3. **管理后台**：管理员能在某个配置页里维护"角色 × 模块 × 数据范围"的映射

### 1.3 已对齐的产品决策

| 维度 | 决策 | 备注 |
| --- | --- | --- |
| 数据范围粒度 | **角色级统一 scope** | 复用 `roles.data_scope`，不按 widget 拆 |
| 配置入口 | **并入现有角色管理页** | `RoleManagementPanel.vue` 里加分组，不新做独立页 |
| 模块顺序 | **不提供顺序配置** | 静态布局顺序写死 |
| 用户级覆盖 | **不做** | 只做角色级；后续如需，`UserScopeRule` 后端基础设施已就绪 |

---

## 二、现状勘察

> 本节为代码实地勘察结论，路径全为绝对路径，作为下次直接开干的"地图"。

### 2.1 后端角色与权限体系

#### 2.1.1 双层角色模型（legacy 枚举 + 动态实体）

- **枚举（legacy）**：`User.Role { ADMIN, MANAGER, STAFF }`，仍作为 `users` 表非空列存在
  - 文件：`backend/src/main/java/com/xiyu/bid/entity/User.java`
  - `getRoleCode()` 优先取 `roleProfile.code`，回退到枚举小写
  - Spring Security `@PreAuthorize("hasRole('ADMIN')")` 看的还是这个枚举映射出的 `ROLE_ADMIN`

- **动态实体**：`RoleProfile`（表 `roles`，列 `role_id` 关联 `users`）
  - 文件：`backend/src/main/java/com/xiyu/bid/entity/RoleProfile.java`
  - 关键字段：`code`, `name`, `dataScope`, `menuPermissionsValue`(逗号拼接), `allowedProjectsValue`, `allowedDeptsValue`, `isSystem`, `enabled`

#### 2.1.2 系统内置角色

- 文件：`backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`
- 启动时由 `RoleProfileBootstrap.ensureSystemRoles()` 幂等写入

| code | 中文名 | 默认 dataScope | 默认 menu 权限 |
| --- | --- | --- | --- |
| `admin` | 管理员 | `all` | `all`（全量短路） |
| `auditor` | 审计员 | `all` | dashboard, operation-logs, audit-logs |
| `manager` | 经理 | `dept` | dashboard, operation-logs, bidding, project, knowledge, resource, ai-center, analytics, settings |
| `staff` | 员工 | `self` | dashboard, operation-logs, dashboard.quickStart, bidding, project, knowledge, resource, ai-center |
| `sales` | 销售/业务负责人 | `self` | dashboard, bidding, project, knowledge, project.create, project.view, deposit.return.fill |
| `bid_lead` | 投标负责人 | `self` | dashboard, bidding, project, knowledge, resource, … |
| `bid_admin` | 投标部门管理员 | — | — |
| `task_executor` | 任务执行人 | — | — |

#### 2.1.3 数据范围（DataScope）基础设施 —— **已经齐全可用**

- **常量集合**：`backend/src/main/java/com/xiyu/bid/admin/settings/core/DataScopePolicy.java`
  ```java
  ALLOWED_SCOPES = { "all", "dept", "deptAndSub", "self" };
  DEFAULT_SCOPE = "self";
  ```
  解析顺序：`UserScopeRule`（用户级覆盖）→ `DepartmentScopeRule`（部门级覆盖）→ `RoleAccessRule`（角色默认）→ `self`

- **核心 records**：`backend/src/main/java/com/xiyu/bid/admin/settings/core/`
  - `RoleAccessRule(dataScope, allowedProjectIds, allowedDeptCodes)`
  - `UserScopeRule(userId, dataScope, allowedProjectIds, allowedDeptCodes)`
  - `DepartmentScopeRule(departmentCode, dataScope, allowedDeptCodes)`
  - `CoreAccessProfile(dataScope, explicitProjectIds, allowedDepartmentCodes)`

- **持久化**：`backend/src/main/java/com/xiyu/bid/admin/service/DataScopeConfigService.java`（存 `system_settings` 单 JSON）
- **请求上下文**：`backend/src/main/java/com/xiyu/bid/security/DataScopeContext.java` + `DataScopeContextHolder.java`（ThreadLocal）
- **AOP 切入**：`backend/src/main/java/com/xiyu/bid/aspect/DataScopeAspect.java` + `annotation/DataScope.java`（注解是存根，业务靠手动拼）

- **业务侧已经在用 scope 的 Guard**（不是 TODO）：
  - `service/ProjectAccessScopeService.java`（前置 Service）
  - `tender/service/TenderProjectAccessGuard.java`
  - `bidresult/service/BidResultProjectAccessGuard.java`
  - `compliance/service/ComplianceProjectAccessGuard.java`
  - `documentexport/service/DocumentExportAccessGuard.java`
  - `documenteditor/service/DocumentEditorGuard.java`
  - `resources/service/expense/ExpenseAccessGuard.java`
  - `fees/service/FeeService.java`
  - `scoreanalysis/service/ScoreAnalysisQueryService.java`
  - `roi/service/ROIAnalysisService.java`
  - `calendar/service/CalendarService.java`
  - `competitionintel/service/CompetitionIntelService.java`
  - `templatecatalog/application/service/TemplateCatalogQueryAppService.java`
  - `resources/service/BarCertificateService.java`
  - `security/service/ProjectMemberService.java`

#### 2.1.4 角色管理 API

- Controller：`backend/src/main/java/com/xiyu/bid/controller/AdminRoleController.java` (`/api/admin/roles`)
- Service：`backend/src/main/java/com/xiyu/bid/service/RoleProfileService.java`
- Repository：`backend/src/main/java/com/xiyu/bid/repository/RoleProfileRepository.java`
- 表 DDL：`backend/src/main/resources/db/migration-mysql/B73__full_schema_baseline.sql`（行 1049+）
- 后续累积迁移：`V89__staff_quick_start_permission.sql`、`V90__ai_center_menu_permission.sql`、`V99__operation_log_role_permissions.sql`（用 `FIND_IN_SET` 追加 menu_permissions）

### 2.2 前端权限体系

#### 2.2.1 三层叠加：`meta.roles` + `meta.permissionKeys` + 用户 `menuPermissions`

- **路由守卫**（角色白名单）：`src/router/index.js`
  ```js
  const hasRouteAccess = (to, role) => {
    const required = to.matched.flatMap(r => r.meta?.roles || [])
    return required.length === 0 || required.includes(role)
  }
  ```
  - 已限路由：`analytics/dashboard`→admin/manager；`audit-logs`→admin/auditor；`settings`→admin
  - **`/dashboard` 路由本身没加角色限制**，人人可进，靠页内显隐控制

- **Pinia store**：`src/stores/user.js`
  ```js
  hasPermission: (state) => (key) => {
    const perms = state.currentUser?.menuPermissions || []
    if (perms.includes('all')) return true
    return perms.includes(key)
  }
  ```

- **侧边栏过滤**：`src/components/layout/Sidebar.vue` (~L170-230) + `src/config/sidebar-menu.js`

- **登录归一**：`src/api/authNormalizer.js` 把 `menuPermissions / allowedProjectIds / allowedDepts / dataScope / roleCode / roleName` 写进 store

- **接口权限矩阵**：`src/api/modules/permissionMatrix.js` → `/api/admin/endpoint-permissions`

#### 2.2.2 Workbench 现状

- **路由**：`src/router/index.js:49-53`，`path: 'dashboard' → @/views/Dashboard/Workbench.vue`
- **主页面**：`src/views/Dashboard/Workbench.vue`
- **静态布局**：`src/views/Dashboard/components/WorkbenchStaticLayout.vue`（默认）
- **动态布局**：`src/views/Dashboard/components/DynamicLayoutRenderer.vue`（当 `/api/dashboard/layout/my` 返回非空 layoutJson 时启用）
- **widget 装配**：`src/views/Dashboard/useWorkbenchDynamicWidgets.js`（已有 14 个 widget 的 registry）
- **角色驱动数据**：`src/views/Dashboard/workbench-role-core.js` + `workbench-quick-start-core.js`

#### 2.2.3 Workbench 的 14 个模块清单

| # | 模块 | 组件文件（`src/views/Dashboard/components/` 下） | 数据来源 |
| --- | --- | --- | --- |
| 1 | 欢迎横幅 | `WelcomeBanner.vue` | 拼接 userStore + summary |
| 2 | 核心指标卡片 | `MetricCards.vue` | `GET /api/analytics/summary`（`useWorkbenchMetrics.js`） |
| 3 | 工作日程日历 | `WorkCalendar.vue` | `useWorkbenchSchedule.js` |
| 4 | 快速发起工单 | `WorkbenchQuickStart.vue` | `useSupportRequest.js` |
| 5 | 热门标讯 | `TenderList.vue` | `tendersApi.getList()` |
| 6 | 技术任务 | `TechnicalTaskList.vue` | 派生自 priorityTodos |
| 7 | 待评审任务 | `ReviewList.vue` | 派生自 pendingApprovals |
| 8 | 客户跟进 | `CustomerFollowUpList.vue` | 派生 |
| 9 | 负责项目 | `ProjectList.vue`（标题"负责项目"） | `projectsApi.getList()` |
| 10 | 团队任务 | `TeamTaskList.vue` | 派生 |
| 11 | 项目总览 | `ProjectList.vue`（标题"项目总览"） | 同上 |
| 12 | 进行中项目 | `ProjectList.vue`（标题"进行中项目"，**当前无门控**） | 同上 |
| 13 | 团队绩效 | `TeamPerformance.vue`（仅 admin） | 派生 |
| 14 | 待审批 | `ApprovalList.vue`（仅 admin） | `useWorkbenchApprovals.js` |
| 15 | 流程时间线 | `ProcessTimeline.vue` | `useWorkbenchApprovals.js`（`loadMyProcesses`） |
| 16 | 活动流 | `ActivityList.vue` | 前端派生 |
| 17 | 优先级待办 | `PriorityTodos.vue` | `useWorkbenchTodos.js` → `tasksApi` + 告警待办 |

#### 2.2.4 现存权限控制（混乱点 —— 本次要统一）

**a) 走权限键的（6 个）—— `Workbench.vue:197-202`**
```js
canViewTenderList     = hasPermission('dashboard:view_tender_list')     || role === 'staff'
canViewTechnicalTask  = hasPermission('dashboard:view_technical_task')  || role === 'staff'
canViewReviewList     = hasPermission('dashboard:view_review_list')     || role in ['staff','manager']
canViewProjectList    = hasPermission('dashboard:view_project_list')    || role === 'manager'
canViewTeamTask       = hasPermission('dashboard:view_team_task')       || role === 'manager'
canViewGlobalProjects = hasPermission('dashboard:view_global_projects') // 仅权限键
```

**b) 角色硬比较（2 个）—— `WorkbenchStaticLayout.vue:75`**
```html
<template v-if="currentUserRole === 'admin'">
  <TeamPerformance ... />
  <ApprovalList ... />
</template>
```

**c) 完全无门控（1 个）—— `WorkbenchStaticLayout.vue:64-72`**
- "进行中项目" `ProjectList` 任何人都能看

**d) 动态分支不消费权限**：`DynamicLayoutRenderer.vue` 直接按后端下发 layoutJson 渲染，没有按 `canViewXxx` 过滤

#### 2.2.5 角色管理页

- 主入口：`src/views/System/Settings.vue` 聚合多 panel
- 角色 panel：`src/views/System/settings/RoleManagementPanel.vue`（180+ 行）
  - 列表 + 新增 / 编辑 / 启停 / 重置 + Dialog 编辑
  - "数据范围"下拉已有：`all / dept / self`
  - "菜单权限"用分组 checkbox + 子节点 checkbox（`role-menu-permission-tree.js` 提供工具）
- 权限键定义：`src/config/sidebar-menu.js`（`roleMenuOptions` + `roleMenuGroups`）

### 2.3 其它配置类页面（脚手架参考）

| 模块 | 前端 | 后端 |
| --- | --- | --- |
| 角色管理 | `RoleManagementPanel.vue` | `AdminRoleController` |
| 接口权限矩阵 | `InterfacePermissionMatrixPanel.vue` | `AdminEndpointPermissionController` |
| 部门/组织 | `DepartmentTreePanel.vue`、`UserOrganizationPanel.vue` | `AdminSettingsController` |
| 数据范围配置 | 同 panel 中段 | `DataScopeConfigService` |
| 流程表单 | `WorkflowFormDesigner.vue` | `workflowform/*` |
| 任务字典 | `TaskStatusDictPanel.vue` | `task/*` |
| BidMatch 评分 | `BidMatchScoringSettingsPanel.vue` | `SettingsService` |
| 告警规则 | `AlertRules.vue` | `alerts/*` |
| AI 模型 | `AiModelSettingsPanel.vue` | `SettingsService` |
| 企微集成 | `SystemIntegrationPanel.vue` | `WeComAuthController` |

---

## 三、实施方案

### 3.1 思路总览

1. 工作台每个可控模块对应一个权限键 `dashboard:view_xxx`
2. 角色勾上 → 看得见；没勾 → 不渲染
3. 数据范围继续走 `roles.data_scope`，Workbench 数据 API 把 scope 带给后端，后端 Guard 已就绪

### 3.2 改动清单

#### 3.2.1 权限键扩充（前端 JS，无 DB 迁移）

**文件**：`src/config/sidebar-menu.js`（~L141 `roleMenuOptions`）

把现有 6 个 dashboard 键扩到覆盖 17 个 widget。已有键不改名以保持兼容。

| 权限键 | widget | 状态 |
| --- | --- | --- |
| `dashboard:view_welcome_banner` | WelcomeBanner | **新增**（默认勾选）|
| `dashboard:view_metric_cards` | MetricCards | **新增** |
| `dashboard:view_calendar` | WorkCalendar | **新增** |
| `dashboard:view_quick_start` | WorkbenchQuickStart | 兼容现有 `workbench:quick_start` |
| `dashboard:view_tender_list` | TenderList | 已有 |
| `dashboard:view_technical_task` | TechnicalTaskList | 已有 |
| `dashboard:view_review_list` | ReviewList | 已有 |
| `dashboard:view_customer_followup` | CustomerFollowUpList | **新增** |
| `dashboard:view_project_list` | ProjectList（负责项目） | 已有 |
| `dashboard:view_team_task` | TeamTaskList | 已有 |
| `dashboard:view_global_projects` | ProjectList（项目总览） | 已有 |
| `dashboard:view_active_projects` | ProjectList（进行中项目） | **新增** |
| `dashboard:view_team_performance` | TeamPerformance | **新增**（替换硬编码） |
| `dashboard:view_approval_list` | ApprovalList | **新增**（替换硬编码） |
| `dashboard:view_process_timeline` | ProcessTimeline | **新增** |
| `dashboard:view_activity_list` | ActivityList | **新增** |
| `dashboard:view_priority_todos` | PriorityTodos | **新增** |

同步：`src/config/sidebar-menu.spec.js` 的断言清单。

`roleMenuGroups` 新增："工作台模块"分组，挂全部 17 项作为 children。

#### 3.2.2 角色管理面板

**文件**：`src/views/System/settings/RoleManagementPanel.vue`

零代码改动。`menuGroups` 来自 `roleMenuGroups`，自动呈现新分组。"数据范围"下拉保留现有 3 项。

#### 3.2.3 Workbench 主页面消费权限

**文件**：`src/views/Dashboard/Workbench.vue`（~L197-L202）

- 为 17 个 widget 每个建 `canViewXxx = userStore.hasPermission('dashboard:view_xxx')`
- **删掉所有 `|| role === 'xxx'` fallback**（权限即真理；admin 靠 `menuPermissions=['all']` 短路自动看全）
- 所有 `canViewXxx` 传给 `WorkbenchStaticLayout` props
- 同时塞进 `useWorkbenchDynamicWidgets` 的 `state`

**文件**：`src/views/Dashboard/components/WorkbenchStaticLayout.vue`

- `<template v-if="currentUserRole === 'admin'">`（L75-L87）改成基于 `canViewTeamPerformance` / `canViewApprovalList`
- 给目前无门控的 widget 全部包 `v-if="canViewXxx"`：进行中项目 ProjectList、WorkCalendar、ProcessTimeline、ActivityList、PriorityTodos、WelcomeBanner（在 Workbench.vue 顶层）、MetricCards（同）
- props 同步扩展（17 个 boolean）

**文件**：`src/views/Dashboard/components/DynamicLayoutRenderer.vue` + `useWorkbenchDynamicWidgets.js`

- `widgetRegistry` 每个 widget 挂 `permissionKey` 字段
- `DynamicLayoutRenderer` 渲染前按 `state.canViewXxx` 过滤 layoutJson 中的 widget

#### 3.2.4 数据范围链路确认（后端 + 前端）

**现状**：`roles.data_scope` 已存，登录后 `applyAuthSession` 把 `dataScope / allowedDepts / allowedProjectIds` 塞进 `userStore`。后端 `DataScopeContextHolder` + Guard 已就绪。

**需要确认/补齐**：

| Workbench API | 路径 | 是否已接 dataScope |
| --- | --- | --- |
| `dashboardApi.getSummary()` | `/api/analytics/summary` | **需读 `AnalyticsController`/Service 验证**；未接则注入 `AccessProfile` 过滤 |
| `projectsApi.getList()` | `/api/projects` | 已走 `ProjectAccessScopeService`，确认即可 |
| `tendersApi.getList()` | `/api/tenders` | **需验证**：tender 是否有 scope Service |
| `tasksApi`（priority todos） | `/api/tasks` | **需验证** |

> 实施时用 `git grep "@DataScope"` 和 `git grep "accessProfile"` 逐个核对，不改已经接通的链路。

#### 3.2.5 系统角色默认 menu 权限刷新（可选但推荐）

**文件**：`backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`

给 `admin / manager / staff / sales / bid_lead / bid_admin / task_executor` 默认 `menuPermissions` 列表追加新增的 11 个 dashboard 键，按业务语义勾选：

- `admin`：全勾（其实有 `all` 短路，加不加都行）
- `manager`：除了不和"团队/审批"无关的之外都勾
- `staff`：勾基础卡片（calendar、tender_list、technical_task、project_list、priority_todos、welcome_banner、metric_cards、quick_start）
- `sales` / `bid_lead`：按业务定
- `auditor`：只勾和审计相关的

`RoleProfileBootstrap.ensureSystemRoles()` 幂等写入（已存在角色不会被覆盖管理员的修改）。

> **不做这步的代价**：升级后旧角色未配新 key → 工作台对非 admin 用户"秃"，需要管理员手动逐角色勾。**强烈建议做**。

### 3.3 删除/简化

- `Workbench.vue:197-202` 6 处 `|| role === 'xxx'` fallback → 删
- `WorkbenchStaticLayout.vue:75` `currentUserRole === 'admin'` 硬比较 → 删
- `workbench-quick-start-core.js:10-13` `all` 特例**保留**（全局规则，不改）

### 3.4 关键文件速览

**后端**（多数只读）

- `backend/src/main/java/com/xiyu/bid/entity/RoleProfile.java` — 不改
- `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` — 加默认 menu 键（可选）
- `backend/src/main/java/com/xiyu/bid/controller/AdminRoleController.java` — 不改
- `backend/src/main/java/com/xiyu/bid/service/RoleProfileService.java` — 不改
- analytics / tenders / tasks 三个 Controller / Service — 视核对结果决定是否加 scope 过滤

**前端**（主战场）

- `src/config/sidebar-menu.js` — 扩 `roleMenuOptions` + `roleMenuGroups`
- `src/config/sidebar-menu.spec.js` — 同步断言
- `src/views/System/settings/RoleManagementPanel.vue` — 零改
- `src/views/System/settings/role-menu-permission-tree.js` — 零改
- `src/views/Dashboard/Workbench.vue` — 17 个 `canViewXxx` + 删 fallback
- `src/views/Dashboard/components/WorkbenchStaticLayout.vue` — 加 `v-if` + 扩 props
- `src/views/Dashboard/components/DynamicLayoutRenderer.vue` — 动态分支权限过滤
- `src/views/Dashboard/useWorkbenchDynamicWidgets.js` — registry 挂 `permissionKey`
- `src/stores/user.js` — 零改

### 3.5 数据库迁移

**无**。新权限键纯字符串写进 `roles.menu_permissions` 列。

---

## 四、验证方案

### 4.1 手工验证

1. `XIYU_DEV_CONFIRMED=1 pnpm agent:up` 启服务
2. `admin / XiyuAdmin2026!` 登录 → `/dashboard` → 全部 widget 可见（`menuPermissions=['all']` 短路）
3. `/settings` → 角色管理 → 编辑 manager → 取消勾"团队任务"→ 保存
4. 切 `zhangjingli / 123456`（manager）→ `/dashboard` → "团队任务"消失，其它保留
5. 编辑 staff → 数据范围设 `self` → 切 `xiaowang / 123456` → 项目/标讯/任务只含本人
6. admin 改 `dept`（极端测试）→ 应收敛到 admin 所在部门

### 4.2 自动化验证

- `pnpm test:unit -- sidebar-menu` — 新增权限键
- `pnpm test:unit -- role-menu-permission-tree` — 分组勾选
- `pnpm lint` + `pnpm build`
- `cd backend && mvn -q test -Dtest=RoleProfileServiceTest,AdminRoleControllerTest`
- 若动 analytics/tenders/tasks Controller，跑对应 ControllerTest

### 4.3 边界检查

- admin 走 `all` 短路，新增 key 不在 catalog 也能看
- 路由守卫兜底未登录场景
- "重置"按钮走 `RoleProfileBootstrap` 默认值 → 所以 §3.2.5 默认 menu 刷新更优
- 动态布局 `/api/dashboard/layout/my` 返回非空时也按权限过滤（§3.2.3 已包含）

---

## 五、风险与未决项

| 风险 | 缓解 |
| --- | --- |
| 默认 menu 权限刷新后，已配置好的非系统角色被覆盖？ | `RoleProfileBootstrap` 已经是幂等且**不覆盖管理员修改**（`isSystem=true` 的才升级，其它新增字段走 add-not-replace）。需在 PR 里单测覆盖。 |
| analytics/summary、tenders、tasks 三条数据链路若没接 scope 过滤，会泄数据 | 实施时强制核对，未接的必须先补 scope，再上线 |
| 静态布局 props 数量从 ~30 → ~50，组件可读性下降 | 接受。考虑后续把 17 个 `canViewXxx` 收成单个对象 prop（不在本期范围）|
| 动态布局后端 `/api/dashboard/layout/my` 是否真在用？ | 实施前 `git log -p src/views/Dashboard/Workbench.vue \| grep dashboardApi.getLayout` 看历史。**若未启用**，动态分支权限过滤可降级为后置任务 |

### 未决项（实施时决定）

- **U1**：是否给 `dashboard:view_active_projects`（"进行中项目"）这个新增 widget 做**默认全员可见**？目前完全无门控，启用门控后默认勾不勾会改变现状用户体验
- **U2**：`dashboard:view_quick_start` 与 `workbench:quick_start` 的双键策略 —— 选保留旧键 OR 在 `hasPermission` 里加映射。倾向保留双键 + 同步勾选，避免改动 `workbench-quick-start-core.js`
- **U3**：`canViewWelcomeBanner` 默认应该是 true（人人可见）—— 那它需不需要存在？**结论建议**：保留为权限键但 catalog 默认全角色勾选，给客户极端定制留余地
- **U4**：将来如果业务方提"用户级覆盖"，怎么扩？后端 `UserScopeRule` 已就绪，前端只需在用户管理页加"个人 widget 覆盖"字段；本期不做不影响后续

---

## 六、参考资料

- 用户首次提需求时间：2026-05-13 session
- 相关 wiki：
  - `.omc/wiki/page-06235714.md`（默认登录凭据与角色权限）
  - `.wiki/pages/agent-sop-quickref.md`（开发 SOP）
- 已有相似设计文档：
  - `docs/plans/2026-03-18-project-data-scope-implementation-plan.md`（项目数据范围实施计划）
- 临时草稿（可删）：`/Users/user/.claude/plans/http-127-0-0-1-1315-dashboard-jiggly-piglet.md`
