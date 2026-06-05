# Tasks: 工作台产品蓝图对齐

**Branch**: `codex/workbench-blueprint-alignment` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Task 0: 环境准备 ✅

- [x] 同步远端代码（含 PR #281 角色重构）
- [x] 创建分支 `codex/workbench-blueprint-alignment`
- [x] 拉取最新 origin/main

---

## Task 1: 后端纯核心 WorkbenchDeadlinePolicy

**Files**: `backend/src/main/java/com/xiyu/bid/workbench/domain/WorkbenchDeadlinePolicy.java`, `...Test.java`

**Estimate**: 15 min

- [ ] 写测试：时间窗口计算（today/week/month bounds）
- [ ] 写测试：按窗口计数聚合（含 null 处理）
- [ ] 写测试：完整 deadline stats 构建
- [ ] 实现纯核心 record 类（零框架依赖）
- [ ] 运行 `mvn test -Dtest=WorkbenchDeadlinePolicyTest` → PASS
- [ ] Commit: `feat(workbench): add WorkbenchDeadlinePolicy pure core`

---

## Task 2: 后端查询服务 + DTO（含权限过滤）

**Files**: `WorkbenchDeadlineStatsDTO.java`, `WorkbenchDeadlineQueryService.java`, `TenderRepository.java`, `ProjectRepository.java`, `FeeRepository.java`

**Estimate**: 25 min

- [ ] 创建 `WorkbenchDeadlineStatsDTO` record
- [ ] TenderRepository: 加 `findRegistrationDeadlinesBetween`, `findBidOpeningTimesBetween`（全量）和 `findRegistrationDeadlinesByTenderIds`, `findBidOpeningTimesByTenderIds`（按项目）
- [ ] ProjectRepository: 加 `findTenderIdsByProjectIds`
- [ ] FeeRepository: 加 `findDepositDeadlinesBetween`（全量）和 `findDepositDeadlinesByProjectIds`（按项目）
- [ ] 创建 `WorkbenchDeadlineQueryService`：注入 `ProjectAccessScopeService`，Admin 全量 / 非 Admin 按项目过滤
- [ ] 写测试：Admin 全量场景、Manager 过滤场景、无权限零计数场景
- [ ] 运行 `mvn test -Dtest=WorkbenchDeadlineQueryServiceTest` → PASS
- [ ] Commit: `feat(workbench): add permission-scoped deadline query service`

---

## Task 3: 后端 Controller + 清理 Demo 引用

**Files**: `WorkbenchDeadlineController.java`, `WorkbenchScheduleQueryService.java`

**Estimate**: 15 min

- [ ] 创建 `WorkbenchDeadlineController` → `GET /api/workbench/deadline-stats`
- [ ] 写 `@WebMvcTest` 集成测试
- [ ] 清理 `WorkbenchScheduleQueryService` 中的 `DemoModeService`/`DemoDataProvider`/`DemoFusionService` 引用
- [ ] 运行 `mvn test -Dtest=WorkbenchDeadlineControllerTest` → PASS
- [ ] Commit: `feat(workbench): add deadline-stats endpoint, clean demo refs`

---

## Task 4: 前端纯核心 + API 模块

**Files**: `src/views/Dashboard/workbench-deadline-core.js`, `src/api/modules/workbench.js`

**Estimate**: 15 min

- [ ] 创建 `workbench-deadline-core.js`：`normalizeDeadlineStats()`, `selectDeadlineMetrics()`（用 `hasAnyPermission` 驱动，analytics→4卡, project→3卡, default→3卡）
- [ ] 写 spec 测试
- [ ] `workbench.js` API 模块加 `getDeadlineStats()` 方法
- [ ] 运行 `npm run test:unit -- workbench-deadline-core` → PASS
- [ ] Commit: `feat(workbench): add deadline core + API for frontend`

---

## Task 5: 前端 DeadlineMetricCards 组件 + Workbench 集成

**Files**: `src/views/Dashboard/components/DeadlineMetricCards.vue`, `Workbench.vue`

**Estimate**: 20 min

- [ ] 创建 `DeadlineMetricCards.vue`（4卡片 grid 布局，复用现有 metric-card 样式）
- [ ] 写组件测试
- [ ] 在 `Workbench.vue` 中导入并挂载（在 MetricCards 之后）
- [ ] 更新 `useWorkbenchMetrics.js` 增加 deadline 数据加载
- [ ] 运行组件测试 → PASS
- [ ] Commit: `feat(workbench): add DeadlineMetricCards component`

---

## Task 6: 前端 QuickEntrySection 快捷入口

**Files**: `src/views/Dashboard/components/QuickEntrySection.vue`, `Workbench.vue`

**Estimate**: 15 min

- [ ] 创建 `QuickEntrySection.vue`（三按钮：「发起项目」「查看标讯」「处理待办」）
- [ ] 写组件测试
- [ ] 在 `Workbench.vue` 的 WelcomeBanner 下方挂载
- [ ] 运行组件测试 → PASS
- [ ] Commit: `feat(workbench): add QuickEntrySection component`

---

## Task 7: 前端 日历里程碑增强 + AI 预测占位

**Files**: `WorkCalendar.vue`, `AiPredictionPlaceholder.vue`, `Workbench.vue`

**Estimate**: 15 min

- [ ] 在 WorkCalendar 事件渲染中为里程碑类型加特殊图标和标签
- [ ] 创建 `AiPredictionPlaceholder.vue`（规划中状态卡片）
- [ ] 在 Workbench.vue 底部挂载占位组件
- [ ] 运行组件测试 → PASS
- [ ] Commit: `feat(workbench): enhance calendar milestones + AI prediction placeholder`

---

## Task 8: 前端 标书评审待办集成

**Files**: `useWorkbenchTodos.js`, `useWorkbenchApprovals.js`, `WorkbenchStaticLayout.vue`

**Estimate**: 15 min

- [ ] `useWorkbenchTodos.js`：接入 `useBidReviewStatus` composable
- [ ] `useWorkbenchApprovals.js`：确保标书评审类型可区分展示
- [ ] 运行测试 → PASS
- [ ] Commit: `feat(workbench): integrate bid review into workbench todos`

---

## Task 9: 全量验证 + 架构门禁

**Estimate**: 10 min

- [ ] 后端：`mvn test -pl . -Dtest="com.xiyu.bid.workbench.**"` → ALL PASS
- [ ] 后端架构门禁：`mvn test -Dtest="FPJavaArchitectureTest,MaintainabilityArchitectureTest"` → PASS
- [ ] 前端测试：`npm run test:unit -- src/views/Dashboard/` → ALL PASS
- [ ] 前端 lint：`npm run lint -- src/views/Dashboard/` → no errors
- [ ] 前端构建：`npm run build` → succeeds
- [ ] Commit: `chore(workbench): full test verification`

---

## 依赖关系

```
Task 0 ──→ Task 1 ──→ Task 2 ──→ Task 3
              │                      │
              └──→ Task 4 ──→ Task 5 ─┬─→ Task 6
                                      ├─→ Task 7
                                      └─→ Task 8
                                              │
                                              └──→ Task 9
```

- Task 1 和 Task 4 可并行（后端纯核心 vs 前端纯核心）
- Task 2 依赖 Task 1
- Task 5-8 可完全并行（互不冲突的文件写集）
- Task 9 阻塞全部
