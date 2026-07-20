# 工作台截止时间模块改造 - 任务清单

> 关联 spec：./spec.md
> 创建日期：2026-07-19
> 关联 Linear：CO-593

## 后端任务

### BE-1: 新增 DTO（DeadlineItemDTO + WorkbenchDeadlineItemsDTO）
- [ ] 新建 `WorkbenchDeadlineItemsDTO` record（registrationDeadline / bidOpening / depositDeadline 三列表）
- [ ] 新建 `DeadlineItemDTO` record（id / name / date / targetId / targetType）
- [ ] 位置：`backend/src/main/java/com/xiyu/bid/workbench/dto/`

### BE-2: TenderRepository 新增返回实体的查询方法
- [ ] `findTendersByRegistrationDeadlineBetween(start, end)` → `List<Tender>`
- [ ] `findTendersByRegistrationDeadlineAndTenderIds(tenderIds, start, end)` → `List<Tender>`
- [ ] `findTendersByBidOpeningTimeBetween(start, end)` → `List<Tender>`
- [ ] `findTendersByBidOpeningTimeAndTenderIds(tenderIds, start, end)` → `List<Tender>`
- [ ] 与现有 `findRegistrationDeadlinesBetween`（返回 LocalDateTime）并存，不删除

### BE-3: FeeRepository 新增返回实体的查询方法
- [ ] `findFeesByDepositDeadlineBetween(start, end)` → `List<Fee>`
- [ ] `findFeesByDepositDeadlineAndProjectIds(projectIds, start, end)` → `List<Fee>`
- [ ] 与现有 `findDepositDeadlinesBetween`（返回 LocalDateTime）并存

### BE-4: WorkbenchDeadlinePolicy 新增 period 窗口计算
- [ ] 新增 `DeadlinePeriod` 枚举（TODAY / WEEK / MONTH）
- [ ] 新增 `resolveWindow(LocalDate today, DeadlinePeriod period)` → 返回单窗口 `[start, end]`
- [ ] 复用 `computeTimeWindows` 内部逻辑，不重复计算三窗口
- [ ] 单元测试：3 种 period 各自窗口边界正确

### BE-5: WorkbenchDeadlineQueryService 新增 getDeadlineItems 方法
- [ ] 解析当前用户 roleCode，判断是否属于 `RoleProfileCatalog.GLOBAL_ACCESS_ROLES`
- [ ] 全局管理角色：全量查询 3 类条目
- [ ] 非管理角色：按 `allowedProjectIds` 过滤
  - 报名截止/开标：`findTenderIdsByProjectIds` → 按 tenderIds 过滤
  - 保证金：按 `allowedProjectIds` 过滤 Fee
- [ ] 开标条目：关联 Project.name（批量查 Project，建 Map）
- [ ] 保证金条目：关联 Project.name
- [ ] 过滤掉 `projectId == null` 的开标条目
- [ ] 各类按日期升序排序
- [ ] 日期格式化为 `YYYY-MM-DD`
- [ ] 单元测试：
  - 全局管理角色全量返回
  - 非管理角色按 allowedProjectIds 过滤
  - 非管理角色 + 无项目权限 → 空
  - period = today/week/month 窗口正确
  - 开标 projectId 为 null 被过滤
  - 日期升序 + YYYY-MM-DD 格式

### BE-6: WorkbenchDeadlineController 新增 /deadline-items 接口
- [ ] `@GetMapping("/deadline-items")` + `@RequestParam(defaultValue="week") String period`
- [ ] `@PreAuthorize("isAuthenticated()")`
- [ ] 保留现有 `/deadline-stats`（向后兼容）
- [ ] 单元测试：返回结构 + 默认 period=week

## 前端任务

### FE-1: API 层新增 getDeadlineItems
- [ ] `src/api/modules/workbench.js` 新增 `getDeadlineItems(period)`
- [ ] 调用 `GET /api/workbench/deadline-items?period=xxx`

### FE-2: workbench-deadline-core.js 新增规范化 + 构建函数
- [ ] 新增 `normalizeDeadlineItems(raw)`：规范化后端返回
- [ ] 新增 `buildDeadlinePanels(items)`：构建 `DeadlinePanels` 所需 panels 结构
- [ ] 单元测试：normalize + build 两个函数

### FE-3: useWorkbenchDeadline.js 新增 deadlineItems 状态
- [ ] 新增 `deadlineItems` ref + `deadlineItemsLoading` ref
- [ ] 新增 `loadDeadlineItems(period)` 方法
- [ ] 保留现有 `loadDeadlineStats`（计数接口仍用）
- [ ] 单元测试：loadDeadlineItems 调用 + 错误处理

### FE-4: DeadlinePanelColumn.vue 改造
- [ ] 行布局改为 name（left, ellipsis, title tooltip）+ date（right, YYYY-MM-DD）
- [ ] 移除 countdown 列
- [ ] 容器高度限制 4 条 + `overflow-y: auto`
- [ ] 更新组件开头注释

### FE-5: DeadlinePanels.vue 改造
- [ ] Tab 切换 emit `update:activePeriod`（已有，确认接线）
- [ ] panels 数据来源改为真实后端数据
- [ ] 更新组件开头注释

### FE-6: handleDeadlineRowClick 跳转逻辑
- [ ] `targetType === 'tender'` → `router.push('/bidding/' + targetId)`
- [ ] `targetType === 'project'` → `navigateToProject(router, targetId)`
- [ ] 单元测试：两种 targetType 跳转分支

### FE-7: Workbench.vue 接线改造
- [ ] `deadlinePanels` 数据源从 `useWorkbenchRebuild` 改为 `useWorkbenchDeadline`（真实数据）
- [ ] watch `deadlinePeriod` → `loadDeadlineItems(period)`
- [ ] 删除 `useWorkbenchRebuild` 中从计数伪造 deadlinePanels 的逻辑
- [ ] 初始加载调用 `loadDeadlineItems(deadlinePeriod.value)`

## 验证任务

### V-1: 后端编译 + 测试
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过（含新增测试）

### V-2: 前端构建 + 测试
- [ ] `npm run build` 通过
- [ ] `npm run test` 通过（含新增测试）

### V-3: 集成验证
- [ ] 报名截止点击 → 标讯详情
- [ ] 开标时间点击 → 项目详情
- [ ] 保证金截止点击 → 项目详情
- [ ] 切换 today/week/month 数据变化
- [ ] 投标管理员看到全部数据
- [ ] 投标专员只看到自己参与的数据
