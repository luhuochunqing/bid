# Tasks: 甲方验收导出——未开放功能代码级剔除

**Input**: Design documents from `specs/067-export-hide-features/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/removal-contract.md, quickstart.md

**Tests**: 本任务为删除型改动，不新增测试；验证 = 每批 `npm test` 全绿 + 契约 grep 断言（research.md R8）。被删页面的专属 spec 随页面删除，共享 spec 手术修改。

**Organization**: 按用户故事分组（US1 隐藏功能剔除 / US2 死代码剔除 / US3 回归无损）。

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup（基线与验证脚手架）

**Purpose**: 删除前建立可回滚、可验证的基线

- [ ] T001 安装依赖并记录基线：`npm ci` → `npm test` → `npm run build`，全绿后记录用例数（不绿则停止并上报，见 quickstart.md 基线节）
- [ ] T002 [P] 生成契约断言脚本 `specs/067-export-hide-features/verify-removal.sh`（内容=contracts/removal-contract.md Part I/II 的 grep/test 断言，退出码非 0 即失败；例外登记项显式豁免）

**Checkpoint**: 基线全绿 + 验证脚本就绪

---

## Phase 2: Foundational（无独立基础任务）

本任务无跨故事共享的基础设施；数据模型（data-model.md 删除单元）即事实源，已就绪。

**Checkpoint**: 直接进入 US1

---

## Phase 3: User Story 1 - 剔除隐藏菜单对应的功能页面（Priority: P1）🎯 MVP

**Goal**: 8 个隐藏功能的路由、页面、专属辅助、外部入口全部消失，共享能力零损伤

**Independent Test**: `verify-removal.sh` Part I 中路由/文件断言通过；`npm test` 全绿；快速发起资质借阅、日志说明页、项目创建商机转化仍可用

### 删除路由（US1）

- [ ] T003 [US1] 删除 `src/router/index.js` 中 `CustomerOpportunityCenter` redirect 路由（DU-001）
- [ ] T004 [US1] 删除 `src/router/index.js` 中 `AuditLogs`（/audit-logs）路由（DU-003）
- [ ] T005 [US1] 删除 `src/router/index.js` 中 `BAR`/`BAR_SiteList`/`BAR_SiteDetail`/`BAR_SOPDetail` 4 条路由（DU-006）
- [ ] T006 [US1] 删除 `src/router/index.js` 中 `Expense`（/resource/expense）路由（DU-008）
- [ ] T007 [US1] 删除 `src/router/index.js` 中 `ContractBorrow`（/resource/contract-borrow）路由（DU-011）
- [ ] T008 [US1] 删除 `src/router/index.js` 中 `BidResult`（/resource/bid-result）路由（DU-013）
- [ ] T009 [US1] 删除 `src/router/index.js` 中 knowledge 子路由 `Template`（/knowledge/template）（DU-015）

### 删除页面与专属目录（US1，T010-T016 相互并行）

- [ ] T010 [P] [US1] 删除 `src/views/Bidding/CustomerOpportunityCenter.vue` + `src/views/Bidding/customer-opportunity/` 整目录（含 2 个 spec）（DU-002）
- [ ] T011 [P] [US1] 删除 `src/views/System/AuditLogPage.vue`（DU-004）
- [ ] T012 [P] [US1] 删除 `src/views/Resource/BAR/` 整目录（9 文件）（DU-007）
- [ ] T013 [P] [US1] 删除 `src/views/Resource/Expense.vue` + `src/views/Resource/expense/` 整目录 + `src/composables/useExpensePage.js`、`useExpensePageLoaders.js`、`useExpensePageActions.js`、`useExpensePageDerived.js` + 专属 spec（`useExpensePageActions.regression-1.spec.js`、`useExpensePageDerived.spec.js`、`Expense.qa-regression-1.spec.js`）（DU-009/DU-010）
- [ ] T014 [P] [US1] 删除 `src/views/Resource/ContractBorrow.vue` + `src/views/Resource/ContractBorrow.spec.js`（DU-012；⚠️ contractBorrow API 删除放在 US3 与快速发起拆分联动）
- [ ] T015 [P] [US1] 删除 `src/views/Resource/BidResult.vue` + `src/views/Resource/bid-result/` 整目录（DU-014）
- [ ] T016 [P] [US1] 删除 `src/views/Knowledge/Template.vue` + `src/views/Knowledge/components/template/` 整目录（DU-016；⚠️ 不动 `config/templateLibrary.js` 与 `knowledge.js`）

### 联动入口手术（US1）

- [ ] T017 [US1] `src/views/System/Settings.vue`：删除 audit 页签、`canViewAuditLogs`、audit 模式 `AuditLogPanel` 引用与相关 watch/tab 白名单（DU-005；⚠️ `AuditLogPanel.vue` 文件与 `OperationLogPage.vue` 保留）
- [ ] T018 [US1] `src/composables/projectDetail/useProjectDetailNavigation.js` + `useProjectDetailCore.js` + `useProjectDetailResultActions.js`：删除 `goToResultPage` 定义与透传；若 `src/components/project/detail/` 下有消费按钮一并删除（DU-025）
- [ ] T019 [US1] 删除 `src/api/modules/bidResults.js` + `src/api/modules/bidResults.spec.js` + `src/api/index.js` 中 `bidResultsApi` import/导出（DU-017）
- [ ] T020 [US1] 删除 E2E：`e2e/customer-opportunity-center.spec.js`、`e2e/template-advanced-flow.spec.js`、`e2e/template-three-dimensional-flow.spec.js`（DU-029）
- [ ] T021 [US1] 修改 `e2e/commercial-main-flow.spec.js`：删除 `/resource/expense`、`/resource/bar/sites` 两段 goto+断言，保留 `/analytics/dashboard` 及之后步骤（DU-030）
- [ ] T022 [US1] 批 A 收口：`npm test` 全绿 + 残留 grep（契约 Part I 第 1/2/3 条路由与文件断言）零命中 → 原子提交 `feat: 剔除 8 个隐藏功能前端实现（067 批次A）`

**Checkpoint**: US1 独立可验——旧地址不再渲染、菜单配置干净、共享能力测试全绿

---

## Phase 4: User Story 2 - 剔除死代码孤立页面（Priority: P2）

**Goal**: 8 个从未接入的孤立页面消失，现役同名/共享组件零误伤

**Independent Test**: 契约文件断言通过；`components/ai/CompetitionIntel.vue`、`components/ai/ROIAnalysis.vue`、`FavoriteButton.vue`、`AI/MarketTiming.vue`、`useDocumentAssembly.js` 仍存在且其消费方测试全绿

### Implementation for User Story 2（T023-T027 相互并行）

- [ ] T023 [P] [US2] 删除 `src/views/Analytics/Dashboard.vue`、`src/views/Analytics/CompetitionIntel.vue`、`src/views/Analytics/ScoreAnalysis.vue`、`src/views/Analytics/ROIAnalysis.vue`（DU-020a；⚠️ 保留 `src/components/ai/` 下同名组件；若 `src/views/Analytics/components/` 内有仅被这 4 页引用的组件，一并删除并先 grep 验证）
- [ ] T024 [P] [US2] 删除 `src/views/Bidding/Favorites.vue`（DU-020b；⚠️ 保留 `FavoriteButton.vue`、`tenderFavorites.js` 及 `api/index.js` 导出）
- [ ] T025 [P] [US2] 删除 `src/views/Bidding/market-prediction/` 整目录（.vue+.scss）（DU-020c；⚠️ 保留 `src/views/AI/MarketTiming.vue`）
- [ ] T026 [P] [US2] 删除 `src/views/Document/DocumentAssembly.vue`（DU-020d；⚠️ 保留 `useDocumentAssembly.js` 与其 `Editor.vue` 引用）
- [ ] T027 [P] [US2] 删除 `src/views/Resource/Calendar.vue`（DU-020e；先 grep 确认无 `from './Calendar.vue'` 引用）
- [ ] T028 [US2] 批 B 收口：`npm test` 全绿 + 白名单存在性断言（契约 Part II 的 `components/ai/*`、`FavoriteButton`、`MarketTiming`、`useDocumentAssembly` 行）→ 原子提交 `refactor: 剔除 8 个孤立死代码页面（067 批次B）`

**Checkpoint**: US2 独立可验——死代码归零、现役功能无回归

---

## Phase 5: User Story 3 - 剔除后系统完整回归无损（Priority: P1）

**Goal**: 联动入口手术、隐藏机制收尾、共享 spec 修订，最终构建+全量测试+契约三件套全绿

**Independent Test**: quickstart.md 终验三件套全部通过；契约 Part I/II 全部断言成立

### 快速发起借阅拆分（US3）

- [ ] T029 [US3] `src/views/Dashboard/workbench-quick-start-core.js`：删除合同模式分支（`contractNo/contractName` 校验与 payload 构建），保留 `mode: 'qualification'` 默认路径（DU-022）
- [ ] T030 [US3] `src/views/Dashboard/useWorkbenchQuickStart.js`：删除 `contractBorrowApi` import/接线与合同提交分支（DU-023；依赖 T029）
- [ ] T031 [US3] `src/views/Dashboard/components/WorkbenchQuickStart.vue`：删除借阅对话框合同模式 UI（合同编号/名称字段与模式切换），保留资质选择与提交（DU-021）
- [ ] T032 [US3] 修订共享 spec：`src/views/Dashboard/__tests__/workbench-characterization.fixture.js`（删 contractBorrow mock 与 401 用例）、`src/views/Dashboard/useWorkbenchQuickStart.spec.js`、`src/views/Dashboard/__tests__/workbench-core.spec.js`（删合同借阅断言、保留资质借阅断言）（DU-024）
- [ ] T033 [US3] 删除 `src/api/modules/contractBorrow.js` + `src/api/modules/contractBorrow.spec.js`（DU-018；依赖 T030 完成后无消费方）

### 隐藏机制与死导出收尾（US3）

- [ ] T034 [P] [US3] `src/config/sidebar-menu.js`：删除 `hiddenApiMenuNames` 集合、Template 子菜单项及 `roleMenuGroups` 过滤引用；同步修订 `src/config/sidebar-menu.spec.js`（DU-026）
- [ ] T035 [P] [US3] `src/components/layout/Sidebar.vue`：删除 `hiddenApiMenuNames` import、`isApiDeliveryMode` 及两处过滤分支（DU-027；依赖 T034 同文件域，顺序执行）
- [ ] T036 [P] [US3] 删除 `src/api/modules/templates.js` + `src/api/index.js` 中其 import/导出（DU-028；⚠️ 保留 `knowledge.js` 内 templates API 与 `config/templateLibrary.js`；先 grep 确认无页面消费方）

### 终验（US3）

- [ ] T037 [US3] 执行 `bash specs/067-export-hide-features/verify-removal.sh`：Part I 全 0 命中、Part II 全存在，例外项（资质 audit-logs API、knowledgeApi.templates、borrow 词根）按登记豁免
- [ ] T038 [US3] 终验三件套：`npm run build` 成功 + `npm test` 全绿（用例数与基线差值 = 被删专属用例数，人工核对无额外丢失）→ 原子提交 `refactor: 联动入口手术+隐藏机制收尾（067 批次C）`
- [ ] T039 [US3] 运行 `bash scripts/pre-push-gate.sh` 全门禁 → 修正直至通过

**Checkpoint**: 全部用户故事完成，契约三件套全绿

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T040 [P] 知识沉淀：按 knowledge-capture 纪律将"隐藏菜单≠代码剔除、共享符号白名单法、删除型任务契约验证"追加到 `docs/lessons/lessons-learned.md`（追加既有文件，禁新建顶层文档）+ 检查 `.wiki/` 回填触发
- [ ] T041 更新 `specs/067-export-hide-features/spec.md` Status: Draft → Approved，checklist 复核
- [ ] T042 与用户确认导出打包方案（zip/目录、排除 `docs/`、`.wiki/`、`specs/` 等文档域）并执行导出

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: 无依赖 — 立即开始
- **Phase 2 (Foundational)**: 空 — 不阻塞
- **Phase 3 (US1)**: 依赖 T001/T002；内部顺序 = 路由(T003-T009) → 页面(T010-T016 可并行) → 联动(T017-T021) → 收口(T022)
- **Phase 4 (US2)**: 依赖 US1 收口 T022（避免同批改 `api/index.js`/e2e 冲突）；内部 T023-T027 可并行 → T028
- **Phase 5 (US3)**: 依赖 US2 收口 T028；T029→T030→T033 严格串行（合同借阅链）；T034→T035 顺序执行；T036 可与 T029-T035 并行
- **Phase 6 (Polish)**: 依赖 T039 全门禁通过

### User Story Dependencies

- **US1 (P1)**: Setup 后即可开始，无故事间依赖 → MVP
- **US2 (P2)**: 依赖 US1 完成（共享 `api/index.js` 与 e2e 文件域）
- **US3 (P1)**: 依赖 US1+US2 的删除落地（手术对象消失后才有意义）

### Parallel Opportunities

- T003-T009（不同路由块，同文件建议顺序执行防冲突 → 实际顺序）
- T010-T016 全并行（不同文件）
- T023-T027 全并行（不同文件）
- T034/T036 可并行（不同文件）
- 每批收口任务（T022/T028/T038）是串行门，不可并行

---

## Implementation Strategy

### MVP（US1 only）

1. Phase 1 基线 + 契约脚本
2. Phase 3 全部任务 → T022 批 A 收口
3. **STOP & VALIDATE**: `verify-removal.sh` 路由/文件断言 + `npm test` → 向用户展示隐藏功能剔除结果

### Incremental Delivery

1. Setup → US1（隐藏功能剔除，MVP）→ 收口提交
2. → US2（死代码剔除）→ 收口提交
3. → US3（联动手术+机制收尾+终验）→ 收口提交
4. → Polish（知识沉淀 + 导出打包）

每批一个原子提交，失败可 `git reset` 单批回滚（research.md R8 二分定位策略）。

---

## Notes

- [P] 任务 = 不同文件、无未完成依赖；同一文件内的多处删除按任务编号顺序执行
- ⚠️ 标记 = 误伤高风险点，执行前必读 research.md R2 保留白名单
- 每批收口 = `npm test` + 契约断言 + 原子提交，符合"每变必测 + 原子提交"底线
- 禁止触碰 `backend/`（用户口径仅剔前端）
