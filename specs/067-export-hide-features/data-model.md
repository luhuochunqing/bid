# Phase 1: Data Model — 删除单元清单

**Date**: 2026-09-22 | **Branch**: `agent/qoder/export-hide-features`

> 本任务无新增数据实体。"实体"=**删除单元（Deletion Unit）**，字段即其属性。
> 每个删除单元必须可独立验证：文件不存在 + 引用为零 + 测试通过。

## 实体：Deletion Unit（删除单元）

| 字段 | 含义 |
|---|---|
| id | 唯一编号（DU-xxx） |
| batch | 所属批次（A=隐藏功能 / B=死代码 / C=联动入口与机制收尾） |
| kind | delete-route / delete-file / delete-dir / edit-file / delete-test / delete-e2e |
| target | 路由名 / 文件路径 |
| dependsOn | 前置单元（同批内必须顺序满足） |
| verify | 验证方式（文件不存在 / grep 零命中 / 测试通过） |

## 批次 A：隐藏功能（8 项）

| id | kind | target | verify |
|---|---|---|---|
| DU-001 | delete-route | `CustomerOpportunityCenter`（/bidding/customer-opportunities redirect） | router grep 零命中 |
| DU-002 | delete-dir | `src/views/Bidding/CustomerOpportunityCenter.vue` + `src/views/Bidding/customer-opportunity/`（含 2 spec） | 文件不存在 |
| DU-003 | delete-route | `AuditLogs`（/audit-logs） | router grep 零命中 |
| DU-004 | delete-file | `src/views/System/AuditLogPage.vue` | 文件不存在 |
| DU-005 | edit-file | `src/views/System/Settings.vue`（删 audit 页签、`canViewAuditLogs`、`AuditLogPanel` audit 引用；`mode="operation"` 路径不受影响） | 测试通过 + 页内 grep 无 audit 页签 |
| DU-006 | delete-route | `BAR`/`BAR_SiteList`/`BAR_SiteDetail`/`BAR_SOPDetail`（4 条） | router grep 零命中 |
| DU-007 | delete-dir | `src/views/Resource/BAR/`（整目录 9 文件） | 目录不存在 |
| DU-008 | delete-route | `Expense`（/resource/expense） | router grep 零命中 |
| DU-009 | delete-file | `src/views/Resource/Expense.vue` + `src/views/Resource/expense/`（整目录） | 文件不存在 |
| DU-010 | delete-file | `src/composables/useExpensePage.js` / `useExpensePageLoaders.js` / `useExpensePageActions.js` / `useExpensePageDerived.js` + 2 个专属 spec | 文件不存在 + grep 零命中 |
| DU-011 | delete-route | `ContractBorrow`（/resource/contract-borrow） | router grep 零命中 |
| DU-012 | delete-file | `src/views/Resource/ContractBorrow.vue` + `ContractBorrow.spec.js` | 文件不存在 |
| DU-013 | delete-route | `BidResult`（/resource/bid-result） | router grep 零命中 |
| DU-014 | delete-dir | `src/views/Resource/BidResult.vue` + `src/views/Resource/bid-result/`（整目录） | 文件不存在 |
| DU-015 | delete-route | `Template`（/knowledge/template） | router grep 零命中 |
| DU-016 | delete-file | `src/views/Knowledge/Template.vue` + `src/views/Knowledge/components/template/`（整目录） | 文件不存在 |
| DU-017 | delete-file | `src/api/modules/bidResults.js` + spec + `api/index.js` 导出 `bidResultsApi` | grep 零命中 |
| DU-018 | delete-file | `src/api/modules/contractBorrow.js` + spec + `api/index.js`/`modules/templates.js` 中相关导出 | grep 零命中 |

**依赖**：DU-017 依赖 DU-014；DU-018 依赖 DU-012 与批次 C 的 DU-023（快速发起合同模式先摘）。

## 批次 B：死代码（8 项）

| id | kind | target | verify |
|---|---|---|---|
| DU-020a | delete-file | `src/views/Analytics/Dashboard.vue`、`CompetitionIntel.vue`、`ScoreAnalysis.vue`、`ROIAnalysis.vue`（⚠️ 保留 `src/components/ai/` 同名组件） | 文件不存在 + `components/ai/` 两文件仍在 |
| DU-020b | delete-file | `src/views/Bidding/Favorites.vue`（⚠️ 保留 `FavoriteButton.vue` 与 `tenderFavorites.js`） | 同上 |
| DU-020c | delete-dir | `src/views/Bidding/market-prediction/`（.vue+.scss；⚠️ 保留 `AI/MarketTiming.vue`） | 目录不存在 + MarketTiming 存在 |
| DU-020d | delete-file | `src/views/Document/DocumentAssembly.vue`（⚠️ 保留 `useDocumentAssembly.js`） | 同上 |
| DU-020e | delete-file | `src/views/Resource/Calendar.vue` | 文件不存在 |

## 批次 C：联动入口与机制收尾

| id | kind | target | verify |
|---|---|---|---|
| DU-021 | edit-file | `src/views/Dashboard/WorkbenchQuickStart.vue`：删合同借阅模式 UI（contract 字段/校验提示），保留资质模式 | 打开对话框仅资质模式 |
| DU-022 | edit-file | `src/views/Dashboard/workbench-quick-start-core.js`：删 `contractNo/contractName` 校验与 payload 构建分支 | `workbench-core.spec.js` 修改后通过 |
| DU-023 | edit-file | `src/views/Dashboard/useWorkbenchQuickStart.js`：删 `contractBorrowApi` import/接线与 contract 提交分支 | `useWorkbenchQuickStart.spec.js` 修改后通过 |
| DU-024 | edit-file | `src/views/Dashboard/__tests__/workbench-characterization.fixture.js`：删 contractBorrow mock 与 401 用例 | fixture grep 零命中 + 测试通过 |
| DU-025 | edit-file | `src/composables/projectDetail/useProjectDetailCore.js` + `useProjectDetailNavigation.js` + `useProjectDetailResultActions.js`：删 `goToResultPage`（含消费端 `ProjectDetailShell.vue` 若有按钮） | grep `goToResultPage` 零命中 |
| DU-026 | edit-file | `src/config/sidebar-menu.js`：删 `hiddenApiMenuNames` 集合（8 项）、Template 子菜单项、`roleMenuGroups` 中相关过滤 | grep 零命中 + spec 通过 |
| DU-027 | edit-file | `src/components/layout/Sidebar.vue`：删 `hiddenApiMenuNames` import、`isApiDeliveryMode` 及过滤分支 | 测试通过 |
| DU-028 | delete-file | `src/api/modules/templates.js` + `api/index.js` 导出（无页面消费方的死导出；⚠️ 保留 `knowledge.js` 内 templates API 与 `config/templateLibrary.js`） | grep 确认 `knowledgeApi.templates` 消费方仍绿 |
| DU-029 | delete-e2e | `e2e/customer-opportunity-center.spec.js`、`template-advanced-flow.spec.js`、`template-three-dimensional-flow.spec.js` | 文件不存在 |
| DU-030 | edit-e2e | `e2e/commercial-main-flow.spec.js`：删 `/resource/expense`、`/resource/bar/sites` 步骤 | 文件仍存在且无被删路由 |

## 保留白名单（任何批次不得触碰）

`AuditLogPanel.vue`、`OperationLogPage.vue`、`customerOpportunity.js`、`knowledge.js`、`components/ai/CompetitionIntel.vue`、`components/ai/ROIAnalysis.vue`、`useDocumentAssembly.js`、`tenderFavorites.js`、`FavoriteButton.vue`、`AI/MarketTiming.vue`、`backend/` 全部、`role-menu-permission-tree.js`（无被删菜单引用，已验证）

## 第二轮评审补删（R9：评审后确认零引用的孤儿）

`config/templateLibrary.js`（原白名单，模板链路删除后零引用）、`views/Dashboard/workbench-demo-data.js`（main 既有孤儿，含"资质/合同借阅"文案）、`api/modules/roi.js`、`api/modules/scoreAnalysis.js`、`api/modules/bidResults.normalizers.js`、`views/Resource/contractBorrow.css`、`components/expense/`（9 文件）。

## 状态流转

`pending → deleted(git rm) → verified(批内 grep+test) → committed(原子提交)`
任一 verify 失败 → 回滚该单元（git checkout）→ 修复后重跑。
