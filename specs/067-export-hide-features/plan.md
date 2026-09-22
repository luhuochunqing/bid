# Implementation Plan: 甲方验收导出——未开放功能代码级剔除

**Branch**: `agent/qoder/export-hide-features` | **Date**: 2026-09-22 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/067-export-hide-features/spec.md`

## Summary

在独立任务分支上，将 8 个隐藏功能（路由+页面+专属辅助+外部入口）与 8 个孤立死页面从**仅前端**范围内代码级剔除，保留后端接口与全部共享能力；以"删除清单驱动、每批删除后跑测试"的顺序执行，最终构建+全量单测全绿后打包导出。

## Technical Context

**Language/Version**: JavaScript (ESM) / Vue 3 单文件组件

**Primary Dependencies**: Vite 5、Element Plus、Vue Router 4、Pinia、Vitest（前端全量单测）、Playwright（E2E 引用需同步清理）

**Storage**: N/A（本次不涉及数据层；后端 MySQL/Flyway 不动）

**Testing**: `npm test`（Vitest 全量）+ `npm run build`（Vite 构建）+ 删除后全局残留引用搜索

**Target Platform**: Web（浏览器）

**Project Type**: Web 前端单页应用（monorepo 内 `src/` 前端部分）

**Performance Goals**: N/A（纯删除，不引入运行时路径）

**Constraints**: 删除后构建零缺失模块错误；全量单测通过率 100%；共享能力（资质借阅、收藏按钮、日志说明、项目创建商机转化）零回归

**Scale/Scope**: 删除 ≈20 个页面/目录 + ≈30 个专属辅助文件 + 对应 spec/e2e；修改 `router/index.js`、`sidebar-menu.js`、`Sidebar.vue`、`Settings.vue`、`WorkbenchQuickStart.vue`、`workbench-quick-start-core.js`、`useWorkbenchQuickStart.js`、projectDetail 导航 2 文件、`api/index.js` 导出、1 个 E2E 主流程文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪法条目 | 判定 | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS（不适用后端） | 本次仅前端删除，不触碰后端 Pure Core/Imperative Shell |
| II. Real-API Only (NON-NEGOTIABLE) | ✅ PASS | 不引入任何 Mock；删除的是无调用方的死路径。保留的前端 API 模块仍指向真实后端 |
| III. Test-Driven Development | ⚠️ 适配 | 删除型任务无"新行为"可先写红测试；以"删除前基线全绿 → 每批删除后全绿 + 残留引用搜索为零"作为等价验证证据；E2E 蓝图 Mandate 不新增小节（无新功能），受影响 E2E 同步清理 |
| IV. Split-First & Simplicity | ✅ PASS | 删除降低复杂度；不新增文件 |
| V. OSS Integration | ✅ PASS（不适用） | 不触碰 OSS 集成 |
| VI. Authorization Unification | ✅ PASS | 后端 `@PreAuthorize`/`RoleProfileCatalog` 不动；前端仅移除已隐藏菜单的配置条目与 `audit-logs` 页签入口，权限键定义保留在后端 |
| VII. Defensive Collection | ✅ PASS（不适用） | 无 Java stream 改动 |
| VIII. Boring Proven Patterns | ✅ PASS | 采用最平淡做法：删文件 + 删引用 + 跑测试 |
| Code Quality Gates | ✅ PASS | pre-commit 已验证通过；push 前跑 `pre-push-gate.sh` |
| Security & Access Control | ✅ PASS | 不删后端权限定义；`audit-logs` 后端接口与权限键保留（仅删前端页签） |

**Gate 结论**：无违规，无需 Complexity Tracking。

## Project Structure

### Documentation (this feature)

```text
specs/067-export-hide-features/
├── plan.md              # 本文件
├── research.md          # Phase 0：删除边界与共享风险决策
├── data-model.md        # Phase 1：删除单元清单（实体=删除单元）
├── quickstart.md        # Phase 1：验证跑法
├── contracts/           # Phase 1：剔除契约（允许存在/必须消失的符号清单）
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成）
```

### Source Code (repository root)

```text
src/
├── router/index.js                    # [改] 删除 8 类隐藏路由 + 客户商机 redirect
├── config/sidebar-menu.js             # [改] 删 hiddenApiMenuNames 集合与 Template 子菜单
├── components/layout/Sidebar.vue      # [改] 删 hidden 过滤逻辑（isApiDeliveryMode）
├── views/
│   ├── Resource/
│   │   ├── BAR/                       # [删目录] 可投标能力检查 + 站点台账
│   │   ├── Expense.vue + expense/     # [删目录] 费用管理
│   │   ├── ContractBorrow.vue         # [删] 合同借阅页
│   │   ├── BidResult.vue + bid-result/# [删目录] 投标结果闭环
│   │   └── Calendar.vue               # [删] 死代码
│   ├── System/
│   │   ├── AuditLogPage.vue           # [删] 审计日志独立页
│   │   ├── Settings.vue               # [改] 删审计日志页签（AuditLogPanel 共享，保留文件）
│   │   └── settings/AuditLogPanel.vue # [保留] 被"日志说明"页共用
│   ├── Bidding/
│   │   ├── CustomerOpportunityCenter.vue + customer-opportunity/  # [删目录]
│   │   ├── Favorites.vue              # [删] 死代码
│   │   └── market-prediction/         # [删目录] 死代码（MarketTiming.vue 走后端 API，保留）
│   ├── Knowledge/
│   │   ├── Template.vue + components/template/  # [删目录] 模板库
│   │   └── KbLayout/其他子页           # [保留]
│   ├── Analytics/                     # [删 4 个死页面] Dashboard/CompetitionIntel/ScoreAnalysis/ROIAnalysis
│   │                                  # [注意] src/components/ai/CompetitionIntel.vue、ROIAnalysis.vue 是共享现役组件，保留
│   ├── Document/DocumentAssembly.vue  # [删] 死代码（useDocumentAssembly.js 被 Editor 用，保留）
│   └── Dashboard/
│       ├── WorkbenchQuickStart.vue    # [改] 借阅对话框删合同模式，保留资质模式
│       ├── workbench-quick-start-core.js  # [改] 删 contract 分支
│       └── useWorkbenchQuickStart.js  # [改] 删 contractBorrowApi 接线
├── composables/projectDetail/
│   ├── useProjectDetailCore.js        # [改] 删 goToResultPage
│   └── useProjectDetailNavigation.js  # [改] 删 goToResultPage
├── composables/useExpensePage*.js(4)  # [删] 费用管理专属
├── api/
│   ├── index.js                       # [改] 删 bidResultsApi 导出（确认无调用方后）
│   └── modules/
│       ├── contractBorrow.js          # [删] 唯一调用方=快速发起合同模式（随删）
│       ├── bidResults.js              # [删] 唯一调用方=BidResult 页（随删）
│       └── customerOpportunity.js     # [保留] 项目创建流程 convertToProject 依赖
└── composables/useExpensePage*.js     # [删]

e2e/
├── customer-opportunity-center.spec.js  # [删] 被测页面已删
├── template-advanced-flow.spec.js       # [删] goto /knowledge/template
├── template-three-dimensional-flow.spec.js  # [删] goto /knowledge/template
└── commercial-main-flow.spec.js         # [改] 删 /resource/expense、/resource/bar/sites 步骤

src/views/Dashboard/__tests__/workbench-characterization.fixture.js  # [改] 删 contractBorrow mock
```

**Structure Decision**: 单项目内前端删除；不动 `backend/`、`document-converter-sidecar/`、数据库与部署物。删除单元与保留边界以 `contracts/` 清单为准。

## Complexity Tracking

无宪法违规，本节为空。
