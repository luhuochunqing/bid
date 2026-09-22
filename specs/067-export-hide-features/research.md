# Phase 0: Research — 未开放功能代码级剔除

**Date**: 2026-09-22 | **Branch**: `agent/qoder/export-hide-features`

本任务无技术选型未知项（纯删除），研究聚焦"删除边界"决策。所有 NEEDS CLARIFICATION 均已通过代码引用分析 + 用户三轮确认解决。

## R1. 删除粒度：路由+页面+专属辅助，共享符号保留

- **Decision**: 三层剔除——①路由条目 ②页面文件及其专属目录 ③仅被被删页面引用的辅助模块（composable/helpers/scss/spec/e2e）；任何有≥1 个现役消费方的符号一律保留
- **Rationale**: 全量连坐删除会击穿共享能力（资质借阅、模板选择、商机转化），违背 FR-004/FR-006/FR-010
- **Alternatives considered**:
  - 整目录连坐删（如删整个 `Knowledge/components/`）→ 已否决，会误伤
  - 只删路由不删文件 → 已否决，用户明确选代码级剔除
  - 加 feature flag 隐藏 → 已否决，甲方能看到代码

## R2. 共享符号清单（保留白名单，逐项引用证据）

| 符号 | 保留原因（消费方） |
|---|---|
| `AuditLogPanel.vue` | "日志说明"页 `OperationLogPage.vue` 以 `mode="operation"` 使用（现役） |
| `customerOpportunity.js` API | 项目创建流程 `useProjectCreateSubmit.js` 的 `convertToProject`（现役） |
| `knowledgeApi.templates`（knowledge.js 内） | `useProjectDetailBoot/Init`、`useDocumentKnowledge`（现役） |
| `config/templateLibrary.js` | `api/modules/templates.js`、`TemplateListTable` 等（模板页删除后经第二轮评审确认零引用，改为随删，见 R9） |
| `components/ai/CompetitionIntel.vue`、`components/ai/ROIAnalysis.vue` | `ProjectDetailFeatureModals.vue`（现役；与被删的 `views/Analytics/*` 同名不同物） |
| `useDocumentAssembly.js` | `views/Document/Editor.vue`（现役；只删 `DocumentAssembly.vue` 页面壳） |
| `tenderFavorites.js` API + `FavoriteButton.vue` | 标讯列表收藏按钮（现役；只删无路由的 `Favorites.vue` 列表页） |
| `AI/MarketTiming.vue` 及其 `/api/market-prediction` 调用 | 路由 `/ai-center/market-timing` 现役（只删 `market-prediction/MarketPredictionPage.vue` 死页面） |
| 后端全部 Controller/Service/权限键 | 用户口径"仅剔前端" |

## R3. 死代码 API 模块（随页面删除）

- **Decision**: `api/modules/contractBorrow.js`（唯一消费方=快速发起合同模式）、`api/modules/bidResults.js` + `api/index.js` 中 `bidResultsApi` 导出（唯一消费方=BidResult 页族）、`api/modules/templates.js`（唯一消费方=`api/index.js` 导出，无任何页面 import）→ 随删
- **Rationale**: 页面删除后成为新的死代码，留着违背 FR-002/SC-002
- **注意**: `knowledge.js` 内的 `templates` API **不删**（R2）
- **Alternatives considered**: 保留 API 模块以待后端复用 → 否决，验收包要求"每个存在的模块有调用方"

## R4. 快速发起借阅对话框的手术式拆分

- **Decision**: `WorkbenchQuickStart.vue` + `workbench-quick-start-core.js` + `useWorkbenchQuickStart.js` 中删除 `mode === 'contract'` 分支、`contractBorrowApi` 接线、`buildContractBorrowPayload`/合同校验；`mode: 'qualification'` 默认路径与 `qualificationApi.createBorrow` 完整保留
- **Rationale**: 同一对话框双模式，资质借阅是现役功能（FR-004）
- **Alternatives considered**: 整个借阅入口删除 → 否决，误伤资质借阅；对话框拆两文件 → 过度工程（YAGNI）

## R5. E2E 处置

- **Decision**: `customer-opportunity-center.spec.js`、`template-advanced-flow.spec.js`、`template-three-dimensional-flow.spec.js` 整文件删除（被测页面不存在）；`commercial-main-flow.spec.js` 删除 `/resource/expense`、`/resource/bar/sites` 两段断言（保留 `/analytics/dashboard` 等现役步骤）；`audit-analytics-flow.spec.js` 中 audit-logs 断言早已移除（仅注释，无需动）；qualification 两条 flow 中的 `audit-logs` 是**资质操作日志 API**（与审计日志页面无关）→ 保留
- **Rationale**: E2E 是"测试即文档"，被测对象消失后测试必须同步消亡，否则门禁假失败

## R6. 单测处置

- **Decision**: 随页面整文件删除的 spec：`ContractBorrow.spec.js`、`Expense.qa-regression-1.spec.js`、`useExpensePageActions.regression-1.spec.js`、`useExpensePageDerived.spec.js`、`contractBorrow.spec.js`、`bidResults.spec.js`、`CustomerOpportunityCenter.spec.js`、`customerOpportunityCenter.helpers.spec.js`；**需手术修改**的 spec：`workbench-characterization.fixture.js`（删 contractBorrow mock 与 401 用例）、`useWorkbenchQuickStart.spec.js`、`workbench-core.spec.js`（删合同借阅断言、保留资质借阅断言）
- **Rationale**: Constitution III 要求测试证据；删除型任务的证据=专属测试随删 + 共享测试修改后全绿

## R7. 隐藏机制的收尾方式

- **Decision**: `hiddenApiMenuNames` 集合与 `Sidebar.vue` 的 `isApiDeliveryMode` 过滤逻辑整体移除（集合中 8 项对应的菜单/路由全部消失后，机制失去存在意义，留着是死代码）
- **Rationale**: FR-005 要求配置不残留指向不存在页面的条目
- **Alternatives considered**: 保留空集合防未来复用 → 否决，未来需要时 git 历史可恢复

## R8. 验证策略（替代删除型 TDD）

- **Decision**: 删除前记录基线（`npm test` 全绿为起点）→ 分 3 批删除（A 隐藏功能 / B 死代码 / C 联动入口与机制收尾），每批跑 `npm test` + 残留引用搜索 → 最终 `npm run build` + 全量测试 + 6 页面抽查
- **Rationale**: 删除无新行为可写红测试；"每变必测"以批为原子单位，构建与全量单测是回归证据
- **Alternatives considered**: 一次性全删再测 → 否决，失败时无法二分定位
