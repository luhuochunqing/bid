# Tasks: 修复项目详情页 403 错误与前端权限入口校验

**Feature**: 026-fix-project-detail-403-frontend
**Generated**: 2026-07-03
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Implementation Strategy

**MVP 优先**：先完成 User Story 2（详情页优雅降级），再完成 User Story 1（入口迁移），最后 User Story 3（架构治理验证）。

**理由**：US1 的方案是"入口侧不预处理，由详情页统一处理 403"，因此 US2 的详情页错误状态界面是 US1 入口迁移的前置依赖。US3 是验证性任务，在入口迁移完成后 grep 确认。

## Phase 1: Foundational（基础组件，所有 User Story 的前置依赖）

- [ ] T001 [P] 创建 ProjectLoadError 自定义错误类 in `src/stores/project.js`（或独立 `src/utils/projectErrors.js`）
  - 导出 `ProjectLoadError extends Error`，携带 `errorType`（'no-permission' | 'not-found' | 'network-error'）和 `cause`
  - 参见 [data-model.md](./data-model.md) §4

- [ ] T002 [P] 创建统一跳转工具函数 in `src/utils/projectNavigation.js`
  - 导出 `navigateToProject(router, projectId, options)`：projectId 为空时 ElMessage.warning 提示，否则 `router.push({ name: 'ProjectDetail', params: { id: String(projectId) } })`
  - 导出 `navigateToProjectList(router)`：`router.push('/project')`
  - 导出常量 `PROJECT_NOT_LINKED_MESSAGE = '该标讯未关联项目'`
  - 参见 [research.md](./research.md) R5

- [ ] T003 改造 `projectStore.getProjectById` 错误传播 in `src/stores/project.js`
  - 403 响应：`throw new ProjectLoadError('no-permission', '无权限访问该项目', error)`
  - 404 响应：`throw new ProjectLoadError('not-found', '项目不存在', error)`
  - 其他错误：`throw new ProjectLoadError('network-error', '加载失败', error)`
  - 不再静默返回 null；成功时返回 project 并设置 `this.currentProject = project`
  - 注意：`existingProject` 缓存命中分支保持不变（仍返回缓存项目，不抛错）
  - 参见 [research.md](./research.md) R2、R3

- [ ] T004 [P] 扩展 `useProjectDetailState.js` 新增 loadError 状态 in `src/composables/projectDetail/useProjectDetailState.js`
  - 新增 `const loadError = ref(null)`（null | 'no-permission' | 'not-found' | 'network-error'）
  - 在 return 对象中导出 `loadError`
  - 参见 [data-model.md](./data-model.md) §1

## Phase 2: User Story 2 - 项目详情页对加载失败场景优雅降级 (P1)

**Story Goal**: 详情页在 403/404/网络错误场景下展示错误状态界面，不崩溃
**Independent Test**: 直接在地址栏输入无权限项目 URL，验证展示错误状态界面

- [ ] T005 [US2] 改造 `useProjectDetailBoot.js` null 防御与错误捕获 in `src/composables/projectDetail/useProjectDetailBoot.js`
  - `onMounted` 中 `await projectStore.getProjectById(projectId)` 包裹 try-catch
  - catch 到 ProjectLoadError 时：设置 `state.loadError.value = error.errorType`，`state.loading.value = false`，提前 return（不执行后续 `loadTaskStatuses`/`initializeProjectActivities`/`loadProjectDetailDependencies` 等）
  - `initializeProjectActivities` 内部：`ensureProjectCollections()` 返回 null 时提前 return（不调用 `currentProject.id`）
  - 参见 [plan.md](./plan.md) Phase 1.4

- [ ] T006 [US2] 改造 `ProjectDetailShell.vue` 错误状态界面渲染 in `src/components/project/detail/ProjectDetailShell.vue`
  - 在 `v-if="loading"` 和 `v-else-if="!project"` 之间插入 `v-else-if="loadError"` 分支
  - `loadError === 'no-permission'`：`el-empty(description="无权限访问该项目")` + `el-button(@click="navigateToProjectList(router)")` "返回项目列表"
  - `loadError === 'not-found'`：`el-empty(description="项目不存在")` + "返回项目列表" 按钮
  - `loadError === 'network-error'`：`el-empty(description="加载失败，请重试")` + "重试" 按钮（reload 当前路由）+ "返回项目列表" 按钮
  - 从 `useProjectDetailState` 解构 `loadError`；从 `projectNavigation` 导入 `navigateToProjectList`
  - 保留现有 `v-else-if="!project"` 兜底分支（理论上不会走到，防御性保留）
  - 参见 [data-model.md](./data-model.md) §5

- [ ] T007 [US2] 单元测试：useProjectDetailBoot null 防御 in `src/composables/projectDetail/__tests__/useProjectDetailBoot.spec.js`（或对应测试目录）
  - 测试用例 1：getProjectById 抛出 ProjectLoadError('no-permission') 时，state.loadError 被设为 'no-permission'，state.loading 为 false，initializeProjectActivities 不被调用
  - 测试用例 2：getProjectById 抛出 ProjectLoadError('not-found') 时，state.loadError 被设为 'not-found'
  - 测试用例 3：getProjectById 成功时，state.loadError 保持 null，正常执行后续流程
  - Mock projectStore.getProjectById 和 projectStore.loadTaskStatuses

- [ ] T008 [US2] 单元测试：projectStore.getProjectById 错误传播 in `src/stores/__tests__/project.spec.js`（或对应测试目录）
  - 测试用例 1：API 返回 403 时，getProjectById 抛出 ProjectLoadError，errorType 为 'no-permission'
  - 测试用例 2：API 返回 404 时，getProjectById 抛出 ProjectLoadError，errorType 为 'not-found'
  - 测试用例 3：API 返回 500 时，getProjectById 抛出 ProjectLoadError，errorType 为 'network-error'
  - 测试用例 4：API 成功返回时，getProjectById 返回 project 且 currentProject 被设置
  - 测试用例 5：existingProject 缓存命中时，直接返回缓存项目不调 API
  - Mock projectsApi.getDetail

## Phase 3: User Story 1 - 用户从外部入口跳转无权限项目时获得友好提示 (P1)

**Story Goal**: 所有跳转入口迁移到统一工具函数，由详情页统一处理 403
**Independent Test**: bid-projectLeader 从标讯详情页点击"查看投标项目"跳转无权限项目，看到友好提示
**依赖**: Phase 2 完成（详情页能正确处理 403）

- [ ] T009 [P] [US1] 迁移标讯详情页跳转入口 in `src/views/Bidding/detail/DetailPage.vue`
  - 第 416-422 行 `viewProject` 函数：替换 `router.push({ name: 'ProjectDetail', params: { id: tender.value.projectId } })` 为 `navigateToProject(router, tender.value.projectId)`
  - `tender.value.projectId` 为空时：navigateToProject 内部已处理 ElMessage.warning，无需额外逻辑（但需确认 navigateToProject 的空值提示文案，如需"该标讯未关联项目"则传入 options 或在调用前判断）
  - 导入 `navigateToProject` from `@/utils/projectNavigation`

- [ ] T010 [P] [US1] 迁移工作台项目点击跳转入口 in `src/views/Dashboard/Workbench.vue`
  - 第 315-322 行 `handleProjectClick` 函数：替换 `router.push(\`/project/${projectId}\`)` 为 `navigateToProject(router, projectId)`
  - demoProjectId 分支保留不变（非数字 ID 走 query 模式，不在本次修复范围）
  - 导入 `navigateToProject`

- [ ] T011 [P] [US1] 迁移工作台日程事件跳转入口 in `src/views/Dashboard/useWorkbenchSchedule.js`
  - 第 67-71 行 `handleCalendarAction` 函数：替换 `router.push(\`/project/${event.projectId}\`)` 为 `navigateToProject(router, event.projectId)`
  - 导入 `navigateToProject`

- [ ] T012 [P] [US1] 迁移分析看板下钻跳转入口 in `src/views/Analytics/Dashboard.vue`
  - 第 243 行 `goToProject` 函数：替换 `router.push({ name: 'ProjectDetail', params: { id: projectId } })` 为 `navigateToProject(router, projectId)`
  - 导入 `navigateToProject`

- [ ] T013 [P] [US1] 迁移分析看板指标下钻跳转入口 in `src/views/Analytics/dashboard/composables/useMetricDrillDown.js`
  - 第 220 行 `handleRowAction`：替换为 `navigateToProject(router, row.id)`
  - 第 225 行：替换为 `navigateToProject(router, projectId)`
  - 导入 `navigateToProject`

- [ ] T014 [P] [US1] 迁移保证金管理跳转入口 in `src/views/Resource/MarginManagement.vue`
  - 第 246 行 `goToProject` 函数：替换 `router.push(\`/project/${id}\`)` 为 `navigateToProject(router, id)`
  - 导入 `navigateToProject`

- [ ] T015 [P] [US1] 迁移二次招标新项目跳转入口 in `src/views/Project/stages/ClosureStage.vue`
  - 第 505 行：替换 `router.push({ name: 'ProjectDetail', params: { id: String(newProjectId) } })` 为 `navigateToProject(router, newProjectId)`
  - 导入 `navigateToProject`

- [ ] T016 [US1] 单元测试：projectNavigation 工具函数 in `src/utils/__tests__/projectNavigation.spec.js`
  - 测试用例 1：navigateToProject(router, 138) 调用 router.push 正确参数 `{ name: 'ProjectDetail', params: { id: '138' } }`
  - 测试用例 2：navigateToProject(router, null) 不调用 router.push，调用 ElMessage.warning
  - 测试用例 3：navigateToProject(router, undefined) 同上
  - 测试用例 4：navigateToProject(router, '') 同上
  - 测试用例 5：navigateToProjectList(router) 调用 router.push('/project')
  - Mock router 和 ElMessage

## Phase 4: User Story 3 - 统一项目跳转入口的权限校验机制 (P2)

**Story Goal**: 验证所有入口已迁移，无裸 router.push 跳转 ProjectDetail
**Independent Test**: 全局搜索 `router.push.*ProjectDetail` 和 `router.push('/project/'`，确认均通过 navigateToProject 调用
**依赖**: Phase 3 完成

- [ ] T017 [US3] 全局验证无裸 router.push 跳转 ProjectDetail
  - 运行 `grep -rn "router\.push.*ProjectDetail\|router\.push.*['\"]\/project\/" src/ --include="*.vue" --include="*.js"` 排除 `projectNavigation.js` 自身和 `router/index.js` 路由定义
  - 预期：无匹配结果（所有入口均通过 navigateToProject 调用）
  - 如有遗漏，补充迁移

## Phase 5: Polish & Cross-Cutting Concerns

- [ ] T018 [P] E2E 测试：项目详情页 403 场景 in `e2e/project-detail-403.spec.js`
  - 测试场景 1：bid-projectLeader 账号（xiaozhang）直接访问无权限项目 URL → 看到错误状态界面 + "返回项目列表"按钮，无 403 控制台错误和 TypeError
  - 测试场景 2：bid-projectLeader 账号从标讯详情页"查看投标项目"跳转无权限项目 → 看到友好提示
  - 测试场景 3：有权限账号（admin）正常跳转 → 无影响
  - 测试数据准备：通过 API 查询一个 xiaozhang 无权限的项目 ID
  - 注意：E2E 在主工作区 trae 运行

- [ ] T019 门禁全量验证
  - `npm run check:front-data-boundaries`
  - `npm run check:doc-governance`
  - `npm run check:line-budgets`
  - `npm run build`
  - `npm run test:unit`
  - `npm run test:e2e`（在主工作区 trae 运行）
  - 所有门禁必须全绿

- [ ] T020 [P] 更新被修改文件的头部注释（如 `src/stores/project.js` 第 1-4 行的 Input/Output/Pos 注释）
  - `src/stores/project.js`：更新 getProjectById 行为说明（错误传播）
  - `src/utils/projectNavigation.js`：新增文件的头部注释
  - 检查 line-budgets 门禁是否因新增文件需要更新预算配置

## Dependencies

```
Phase 1 (T001-T004) → Phase 2 (T005-T008) → Phase 3 (T009-T016) → Phase 4 (T017) → Phase 5 (T018-T020)
```

**关键依赖**:
- T003（store 改造）→ T005（boot 改造）：boot 依赖 store 抛出的错误类型
- T004（state 扩展）→ T005（boot 改造）：boot 需设置 state.loadError
- T001（错误类）→ T003（store 改造）：store 需抛出 ProjectLoadError
- T005+T006（boot+shell 改造）→ T009-T015（入口迁移）：入口迁移前详情页必须能正确处理 403
- T002（工具函数）→ T009-T015（入口迁移）：入口迁移需导入 navigateToProject

**并行机会**:
- Phase 1: T001、T002、T004 可并行（不同文件）；T003 依赖 T001
- Phase 2: T007、T008 可并行（不同测试文件）；T005、T006 需顺序执行（T005 设置 loadError，T006 读取 loadError）
- Phase 3: T009-T015 全部可并行（不同入口文件，互不影响）；T016 需在 T002 完成后
- Phase 5: T018、T020 可并行；T019 需在所有任务完成后

## Parallel Execution Examples

**Phase 1 并行批次**:
```
Batch 1: T001 (错误类) + T002 (工具函数) + T004 (state 扩展)
Batch 2: T003 (store 改造，依赖 T001)
```

**Phase 3 并行批次**:
```
Batch 1: T009 + T010 + T011 + T012 + T013 + T014 + T015（7 个入口迁移，全部并行）
Batch 2: T016（工具函数测试，依赖 T002）
```

## MVP Scope

**最小可交付**: Phase 1 + Phase 2（T001-T008）
- 完成后用户直接输入无权限项目 URL 不再崩溃，展示错误状态界面
- 入口迁移（Phase 3）可在 MVP 后继续，但建议同 PR 交付以保证完整性

## Task Count Summary

- Phase 1 Foundational: 4 tasks (T001-T004)
- Phase 2 User Story 2 (P1): 4 tasks (T005-T008)
- Phase 3 User Story 1 (P1): 8 tasks (T009-T016)
- Phase 4 User Story 3 (P2): 1 task (T017)
- Phase 5 Polish: 3 tasks (T018-T020)
- **Total**: 20 tasks
