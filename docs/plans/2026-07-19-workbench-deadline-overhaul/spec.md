# 工作台截止时间模块改造规格

> 创建日期：2026-07-19
> 分支：agent/trae1/workbench-deadline-overhaul
> 关联 Linear：CO-593
> 关联 PR：待创建

## 1. 背景与目标

当前工作台"截止时间"模块（`DeadlinePanels.vue`）的列表数据是从计数接口（`/api/workbench/deadline-stats` 返回 todayCount/weekCount/monthCount）**伪造派生**而来的——`useWorkbenchRebuild` 拿到计数后拼出假的条目，没有真实的名称、日期、ID，无法点击下钻。

本次改造（CO-593）将截止时间模块改为基于**真实条目数据**：后端新增列表接口返回带名称/日期/ID 的条目，前端按 4 条可见 + 滚动 + 省略号 + YYYY-MM-DD 日期 + 差异化跳转（标讯详情 vs 项目详情）渲染，并按角色做数据权限过滤、按 today/week/month 做时间筛选。

## 2. 角色与数据权限

| 角色组 | roleCode | 数据范围 |
|---|---|---|
| 全局管理角色 | `admin` / `/bidAdmin` / `bid-SystemAdmin` / `bid-TeamLeader` | 展示**所有**相关数据 |
| 其他角色 | `bid-Team` / `bid-projectLeader` / `bid-otherDept` / `bid-administration` | 只展示**自己参与**的数据（按 `ProjectAccessScopeService.getAllowedProjectIdsForCurrentUser()` 过滤） |

**后端判定**：使用 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES`（= {admin, /bidAdmin, bid-TeamLeader, bid-SystemAdmin}）判断"看全部"。**不沿用** `ProjectAccessScopeService.currentUserHasAdminAccess()`（只认 `ROLE_ADMIN`，太窄，不含投标管理员/组长）。

## 3. 三个截止时间模块

### 模块 A：报名截止时间

| 项 | 值 |
|---|---|
| 数据源 | `Tender.registrationDeadline`（非 null） |
| 显示名称 | `Tender.title`（标讯名称） |
| 显示日期 | `Tender.registrationDeadline` 格式化为 `YYYY-MM-DD` |
| 点击跳转 | 标讯详情 `/bidding/:tenderId` |
| 排序 | 按 `registrationDeadline` 升序（最近的在前） |

### 模块 B：开标时间

| 项 | 值 |
|---|---|
| 数据源 | `Tender.bidOpeningTime`（非 null） |
| 显示名称 | 关联**项目名称**（通过 `Tender.projectId` 关联 `Project.name`） |
| 显示日期 | `Tender.bidOpeningTime` 格式化为 `YYYY-MM-DD` |
| 点击跳转 | 项目详情 `navigateToProject(projectId)` |
| 排序 | 按 `bidOpeningTime` 升序 |
| 边界 | 若 `Tender.projectId` 为 null，则该条目不展示（无法跳项目详情） |

### 模块 C：保证金截止时间

| 项 | 值 |
|---|---|
| 数据源 | `Fee`（`feeType = BID_BOND` 且 `status = PENDING`）的 `feeDate`（非 null） |
| 显示名称 | 关联**项目名称**（通过 `Fee.projectId` 关联 `Project.name`） |
| 显示日期 | `Fee.feeDate` 格式化为 `YYYY-MM-DD` |
| 点击跳转 | 项目详情 `navigateToProject(projectId)` |
| 排序 | 按 `feeDate` 升序 |

## 4. UI 与交互

### 4.1 列宽与显示（与"第一部分"待办卡片一致）

- 固定列宽，名称过长显示省略号（3 个点）
- 鼠标悬停显示全称（`title` 属性）
- 名称左侧，具体时间右侧（`YYYY-MM-DD` 格式）
- 对齐 `TodoCategoryCards.vue` 的行布局：`<span class="left">{{name}}</span><span class="right">{{date}}</span>`
- 移除当前 `DeadlinePanelColumn.vue` 中的 `countdown` 列（CO-593 只要名称+日期）

### 4.2 每模块 4 条 + 滚动

- 每个模块容器高度容纳 4 条
- 超出 4 条时出现纵向滚动条
- 后端返回时间窗内全部条目（不截断），前端容器滚动

### 4.3 右上角时间筛选

- 三个 Tab：今天 / 本周 / 本月（`DeadlinePanels.vue` 已有 Tab 结构）
- 切换 Tab 时按 `period` 参数重新拉取数据
- 时间窗定义复用 `WorkbenchDeadlinePolicy.computeTimeWindows`：
  - 今天：`today 00:00:00 ~ 23:59:59`
  - 本周：`周一 00:00:00 ~ 周日 23:59:59`
  - 本月：`月初 00:00:00 ~ 月末 23:59:59`

## 5. 后端改动

### 5.1 新增接口

```
GET /api/workbench/deadline-items?period=today|week|month
```

- 权限：`@PreAuthorize("isAuthenticated()")`
- `period` 参数：枚举 `today` / `week` / `month`，必填（不传默认 `week`）
- 返回结构：

```json
{
  "registrationDeadline": [
    { "id": 101, "name": "XX标讯", "date": "2026-07-20", "targetId": 101, "targetType": "tender" }
  ],
  "bidOpening": [
    { "id": 202, "name": "XX项目", "date": "2026-07-21", "targetId": 55, "targetType": "project" }
  ],
  "depositDeadline": [
    { "id": 303, "name": "XX项目", "date": "2026-07-22", "targetId": 66, "targetType": "project" }
  ]
}
```

- `targetType`：`tender`（跳 `/bidding/:id`）或 `project`（跳 `/project/:id`）
- 每类按日期升序，返回时间窗内全部条目（前端负责 4 条可见 + 滚动）

### 5.2 新增 DTO

`WorkbenchDeadlineItemsDTO`（record）：
- `List<DeadlineItemDTO> registrationDeadline`
- `List<DeadlineItemDTO> bidOpening`
- `List<DeadlineItemDTO> depositDeadline`

`DeadlineItemDTO`（record）：
- `Long id`、`String name`、`String date`（YYYY-MM-DD）、`Long targetId`、`String targetType`

### 5.3 Repository 新查询方法

**TenderRepository**（新增返回实体的方法，区别于现有返回 `LocalDateTime` 的方法）：
- `findTendersByRegistrationDeadlineBetween(start, end)` → `List<Tender>`（含 id/title/registrationDeadline/projectId）
- `findTendersByRegistrationDeadlineAndTenderIds(tenderIds, start, end)` → `List<Tender>`
- `findTendersByBidOpeningTimeBetween(start, end)` → `List<Tender>`
- `findTendersByBidOpeningTimeAndTenderIds(tenderIds, start, end)` → `List<Tender>`

**FeeRepository**（新增返回实体的方法）：
- `findFeesByDepositDeadlineBetween(start, end)` → `List<Fee>`（含 id/projectId/feeDate）
- `findFeesByDepositDeadlineAndProjectIds(projectIds, start, end)` → `List<Fee>`

**ProjectRepository**：
- 复用现有 `findByIdIn` 或 `findTenderIdsByProjectIds`，新增按 id 批量查 name 的方法（若不存在）

### 5.4 Service 改造

`WorkbenchDeadlineQueryService` 新增方法：
```java
WorkbenchDeadlineItemsDTO getDeadlineItems(LocalDate today, DeadlinePeriod period)
```

逻辑：
1. 解析当前用户 roleCode，判断是否属于 `GLOBAL_ACCESS_ROLES`
2. 按 period 计算 `[queryStart, queryEnd]`（单窗口，不再并查三个窗口）
3. 全局管理角色：全量查询
4. 其他角色：按 `allowedProjectIds` 过滤
   - 报名截止/开标：先 `findTenderIdsByProjectIds(allowedProjectIds)`，再按 tenderIds 过滤
   - 保证金：按 `allowedProjectIds` 过滤 Fee
5. 开标/保证金需关联 Project.name（批量查 Project，建 Map<projectId, name>）
6. 过滤掉 `projectId == null` 的开标条目（无法跳项目详情）
7. 各类按日期升序排序
8. 日期格式化为 `YYYY-MM-DD`

### 5.5 Controller 改造

`WorkbenchDeadlineController` 新增：
```java
@GetMapping("/deadline-items")
public ResponseEntity<ApiResponse<WorkbenchDeadlineItemsDTO>> getDeadlineItems(
    @RequestParam(defaultValue = "week") String period)
```

- 保留现有 `/deadline-stats`（计数接口，供 metric cards 使用，向后兼容）

### 5.6 权限注解

- 新接口加 `@PreAuthorize("isAuthenticated()")`
- 新接口加 `@Auditable`（若项目有审计注解约定）

## 6. 前端改动

### 6.1 API 层

`src/api/modules/workbench.js` 新增：
```js
getDeadlineItems(period) // GET /api/workbench/deadline-items?period=xxx
```

### 6.2 useWorkbenchDeadline.js 改造

- 新增 `deadlineItems` ref + `loadDeadlineItems(period)` 方法
- `deadlinePeriod` 变化时重新调用 `loadDeadlineItems(period)`
- 保留现有 `loadDeadlineStats`（计数接口仍用）

### 6.3 workbench-deadline-core.js 改造

- 新增 `normalizeDeadlineItems(raw)`：把后端返回规范化为前端结构
- 新增 `buildDeadlinePanels(items)`：把规范化数据构建为 `DeadlinePanels` 组件所需的 `panels` 结构
- **删除**现有从计数伪造列表的逻辑（在 `useWorkbenchRebuild` 中）

### 6.4 DeadlinePanelColumn.vue 改造

- 行布局改为：`<span class="left" :title="name">{{name}}</span><span class="right">{{date}}</span>`
- 移除 `countdown` 列
- 容器高度限制 4 条 + `overflow-y: auto`

### 6.5 DeadlinePanels.vue 改造

- Tab 切换触发 `emit('update:activePeriod', key)` → 父组件重新拉取数据
- `panels` 数据来源改为真实后端数据（通过 `useWorkbenchDeadline`）

### 6.6 跳转逻辑

`handleDeadlineRowClick(item)`：
- `item.targetType === 'tender'` → `router.push('/bidding/' + item.targetId)`
- `item.targetType === 'project'` → `navigateToProject(router, item.targetId)`

### 6.7 Workbench.vue 接线

- `deadlinePanels` 数据源从 `useWorkbenchRebuild`（伪造）改为 `useWorkbenchDeadline`（真实）
- `deadlinePeriod` watch → `loadDeadlineItems(period)`

## 7. 数据库

无表结构变更，全部基于现有表查询。

## 8. 测试要求

### 后端

- `WorkbenchDeadlineQueryServiceTest`：
  - 全局管理角色 → 全量查询（3 类各返回正确条目）
  - 非管理角色 → 按 allowedProjectIds 过滤
  - 非管理角色 + 无项目权限 → 返回空
  - period = today/week/month 各自窗口正确
  - 开标条目 projectId 为 null 被过滤
  - 日期升序排序
  - 日期格式化为 YYYY-MM-DD
- `WorkbenchDeadlineControllerTest`：接口返回结构 + 默认 period=week
- Repository 测试：新查询方法返回正确实体（若项目有 Repository 集成测试基线）

### 前端

- `workbench-deadline-core.spec.js`：
  - `normalizeDeadlineItems` 规范化
  - `buildDeadlinePanels` 构建正确 panels 结构
- `useWorkbenchDeadline.spec.js`：`loadDeadlineItems` 调用 + period 变化重拉
- `DeadlinePanelColumn` 渲染：名称省略号 + 日期 YYYY-MM-DD + 4 条滚动

## 9. 验收标准

1. 报名截止模块显示标讯名称 + 日期，点击跳转 `/bidding/:tenderId`
2. 开标时间模块显示项目名称 + 日期，点击跳转 `/project/:projectId`
3. 保证金截止模块显示项目名称 + 日期，点击跳转 `/project/:projectId`
4. 每模块最多可见 4 条，超出滚动
5. 名称过长省略号，悬停显示全称
6. 日期格式 YYYY-MM-DD
7. 切换今天/本周/本月 Tab，数据按时间窗重新筛选
8. 投标管理员/组长登录，看到所有数据
9. 投标专员登录，只看到自己参与项目的数据
10. `mvn test` 通过（含新增测试）
11. `npm run build` + `npm run test` 通过
12. 无新增 Mock 数据
13. 列表效果与待办卡片（第一部分）一致
