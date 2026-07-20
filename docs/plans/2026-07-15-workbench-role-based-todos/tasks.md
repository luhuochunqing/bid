# 工作台待办模块角色化改造 - 任务清单

> 关联 spec：./spec.md
> 创建日期：2026-07-15

## 后端任务

### BE-1: Task 接口加 projectStage 过滤（模块1）✅
- [x] TaskRepository 新增 `findByAssigneeIdAndProjectStage` 查询方法（JOIN Project）
- [x] TaskService 新增 `getAccessibleTasksByAssigneeId(assigneeId, username, projectStage)` 重载
- [x] TaskController `/my` 接口新增 `@RequestParam(required=false) String projectStage`
- [x] 单元测试：TaskRepositoryTest 验证 stage 过滤（3 用例：执行人+阶段匹配、枚举/字符串重载一致、无匹配返回空）
- [x] 单元测试：TaskServiceTest 验证 stage 参数透传（4 用例：枚举委托、null/空白回退、非法值 400）

### BE-2: Tender 接口确认/扩展 status 多值过滤（模块2）✅
- [x] 确认 `GET /api/tenders` 是否支持 `status` 多值（TenderSearchCriteria.status 是 List<Tender.Status>，已支持）
- [x] 无需改动 Repository / Service（后端已原生支持多值过滤）

### BE-3: Project workbench-todos 接口（模块3）✅
- [x] 扩展现有 ProjectController（不新建 Controller）
- [x] 新增 `GET /api/workbench/project-todos` 接口（后收编到 `WorkbenchTodoController`，统一 workbench 命名空间）
- [x] Service 层按当前用户角色分支查询：
  - admin_lead: stage=INITIATED OR reviewerId=当前用户
  - bid-Team: primaryLeadUserId/secondaryLeadUserId=当前用户，且 stage != CLOSED（排除已结项，单次 findAllById 内存过滤）
  - bid-projectLeader: stage IN (INITIATED, RETROSPECTIVE) OR reviewerId=当前用户
- [x] 复用 ProjectLeadAssignmentRepository 和 BidDocumentReviewRepository 现有方法
- [x] ProjectRepository 新增 `findByStageIn` 查询方法（枚举 default 方法收口，调用方传 ProjectStage）
- [x] 单元测试：ProjectServiceWorkbenchTodosTest 覆盖 3 种角色分支 + fail-closed + 其他角色（5 用例）

### BE-4: Resource 待审批聚合接口（模块4）✅
- [x] 新增 `GET /api/workbench/resource-pending-approvals` 接口（后收编到 `WorkbenchTodoController`）
- [x] AccountBorrowApplicationRepository 按需查询（保管员过滤 + 数据库分页，未使用的全量方法已删除）
- [x] CaBorrowApplicationRepository 按需查询（审批人过滤 + 数据库分页，未使用的全量方法已删除）
- [x] 新建 ResourcePendingApprovalDTO（统一账户+CA 展示格式）
- [x] 新建 WorkbenchResourcePendingQueryService（合并两类申请，按 createdAt 倒序，取前 4 条）
- [x] 新建 WorkbenchTodoController（/api/workbench 前缀，统一收编项目待办 + 资源待审批，含 @PreAuthorize + @Auditable）
- [x] 单元测试：DashboardResourcePendingServiceTest（4 用例：管理员合并+排序、保管员、fail-closed、limit 4）

## 前端任务

### FE-1: API 层扩展 ✅
- [x] `dashboard.js` tasksApi.getMine 支持 `projectStage` 参数
- [x] `tenders.js` 确保 getList 支持多 status 参数透传
- [x] 新增 `workbench.js` workbenchApi.getProjectTodos 调用（/api/workbench 命名空间统一收编）
- [x] 新增 `workbench.js` workbenchApi.getResourcePendingApprovals 调用

### FE-2: useWorkbenchRebuild 角色化逻辑 ✅
- [x] 改造 `buildTodoCategoryCards` 接收 role + userId + 4 类数据源
- [x] 新增 `useWorkbenchRoleTodos` composable，按角色调用不同 API
- [x] Workbench.vue 注入新 composable
- [x] 单元测试：buildTodoCategoryCards 按角色构建卡片

### FE-3: 跳转逻辑差异化 ✅
- [x] `handleTodoCardClick` task 模块按角色跳转：
  - bid-otherDept → /task-board
  - 其他 → navigateToProject（标书制作阶段，走 ProjectDetailStage 路径参数 /project/:id/drafting）
- [x] 其他模块跳转逻辑保持不变
- [x] 单元测试：跳转逻辑按角色分支（含 projectNavigation.spec.js stage 路径参数用例）

### FE-4: 标讯/项目模块按角色隐藏 ✅
- [x] 标讯模块：投标专员和跨部门协同人员不显示
- [x] 项目模块：所有角色都显示（数据源不同）
- [x] 资源模块：所有角色都显示

## 验证任务

### V-1: 后端编译 + 测试 ✅
- [x] `mvn test -Dtest='TaskServiceTest,TaskRepositoryTest,WorkbenchProjectTodoQueryServiceTest'` 通过（3+8+5 全绿，2026-07-17）

### V-2: 前端构建 + 测试 ✅
- [x] `npm run build` 通过（2026-07-17；首次失败为 node_modules 缺 esdk-obs-browserjs 环境不同步，pnpm install 后通过，非代码问题）
- [x] `npm run test:unit` 本次改动相关全部通过（workbench/projectNavigation/workbench-rebuild-core/dashboard/projects 5 个 spec 共 55 用例全绿）
  - 存量失败（与本次改动无关，git stash 基线验证同样失败）：`scripts/sidecar-dev-services.spec.js`、`scripts/start-env-detection.spec.js`（qoder 等非主工作区按规范不启动开发环境，测试期望停留在多 worktree 独立端口时代）

### V-3: 集成验证（后续）
- [ ] 多角色登录验证工作台显示内容（建议：lizong=admin、xiaowang=bid-Team、xiaozhang=bid-projectLeader、xiaozheng=bid-administration）
- [ ] 跳转逻辑验证（任务卡片 → /project/:id/drafting 标书制作 Tab；跨部门 → /task-board）
- [ ] E2E：多角色工作台待办差异化展示（可补 e2e 用例，非阻塞）
