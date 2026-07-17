# 工作台待办模块角色化改造 - 任务清单

> 关联 spec：./spec.md
> 创建日期：2026-07-15

## 后端任务

### BE-1: Task 接口加 projectStage 过滤（模块1）✅
- [x] TaskRepository 新增 `findByAssigneeIdAndProjectStage` 查询方法（JOIN Project）
- [x] TaskService 新增 `getAccessibleTasksByAssigneeId(assigneeId, username, projectStage)` 重载
- [x] TaskController `/my` 接口新增 `@RequestParam(required=false) String projectStage`
- [ ] 单元测试：TaskRepositoryTest 验证 stage 过滤（后续补）
- [ ] 单元测试：TaskServiceTest 验证 stage 参数透传（后续补）

### BE-2: Tender 接口确认/扩展 status 多值过滤（模块2）✅
- [x] 确认 `GET /api/tenders` 是否支持 `status` 多值（TenderSearchCriteria.status 是 List<Tender.Status>，已支持）
- [x] 无需改动 Repository / Service（后端已原生支持多值过滤）

### BE-3: Project workbench-todos 接口（模块3）✅
- [x] 扩展现有 ProjectController（不新建 Controller）
- [x] 新增 `GET /api/projects/workbench-todos` 接口
- [x] Service 层按当前用户角色分支查询：
  - admin_lead: stage=INITIATED OR reviewerId=当前用户
  - bid-Team: primaryLeadUserId/secondaryLeadUserId=当前用户，且 stage != CLOSED（排除已结项）
  - bid-projectLeader: stage IN (INITIATED, RETROSPECTIVE) OR reviewerId=当前用户
- [x] 复用 ProjectLeadAssignmentRepository 和 BidDocumentReviewRepository 现有方法
- [x] ProjectRepository 新增 `findByStageIn` 查询方法
- [x] 单元测试：ProjectServiceWorkbenchTodosTest 覆盖 3 种角色分支 + fail-closed + 其他角色（5 用例）

### BE-4: Resource 待审批聚合接口（模块4）✅
- [x] 新增 `GET /api/dashboard/resource-pending-approvals` 接口
- [x] AccountBorrowApplicationRepository 新增 `findByStatusOrderByCreatedAtDesc` + `findByCustodianIdAndStatusOrderByCreatedAtDesc`
- [x] CaBorrowApplicationRepository 新增 `findByApproverIdAndStatusOrderByCreatedAtDesc`（带排序）
- [x] 新建 ResourcePendingApprovalDTO（统一账户+CA 展示格式）
- [x] 新建 DashboardResourcePendingService（合并两类申请，按 createdAt 倒序，取前 4 条）
- [x] 新建 DashboardResourceController（/api/dashboard 前缀）
- [x] 单元测试：DashboardResourcePendingServiceTest（4 用例：管理员合并+排序、保管员、fail-closed、limit 4）

## 前端任务

### FE-1: API 层扩展
- [ ] `dashboard.js` tasksApi.getMine 支持 `projectStage` 参数
- [ ] `tenders.js` 确保 getList 支持多 status 参数透传
- [ ] 新增 `projects.js` workbenchTodos API 调用
- [ ] 新增 `dashboard.js` resourcePendingApprovals API 调用

### FE-2: useWorkbenchRebuild 角色化逻辑
- [ ] 改造 `buildTodoCategoryCards` 接收 role + userId + 4 类数据源
- [ ] 新增 `useWorkbenchRoleTodos` composable，按角色调用不同 API
- [ ] Workbench.vue 注入新 composable
- [ ] 单元测试：buildTodoCategoryCards 按角色构建卡片

### FE-3: 跳转逻辑差异化
- [ ] `handleTodoCardClick` task 模块按角色跳转：
  - bid-otherDept → /task-board
  - 其他 → navigateToProject（标书制作阶段）
- [ ] 其他模块跳转逻辑保持不变
- [ ] 单元测试：跳转逻辑按角色分支

### FE-4: 标讯/项目模块按角色隐藏
- [ ] 标讯模块：投标专员和跨部门协同人员不显示
- [ ] 项目模块：所有角色都显示（数据源不同）
- [ ] 资源模块：所有角色都显示

## 验证任务

### V-1: 后端编译 + 测试
- [ ] `mvn compile` 通过
- [ ] `mvn test` 通过（含新增测试）

### V-2: 前端构建 + 测试
- [ ] `npm run build` 通过
- [ ] `npm run test` 通过（含新增测试）

### V-3: 集成验证
- [ ] 多角色登录验证工作台显示内容
- [ ] 跳转逻辑验证
