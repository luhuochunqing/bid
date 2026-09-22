# 剔除契约（Removal Contract）

**Date**: 2026-09-22

本契约是机器可验证的"必须消失 / 必须存在"符号清单，实现完成的定义（DoD）= 下列断言全部成立。

## Part I — 必须消失（MUST BE ABSENT）

对仓库根执行，全部应为 **0 命中**（排除 `specs/`、`docs/`、`.wiki/`、`e2e/coverage` 等文档域；`backend/` 不在检查范围）：

```bash
# 1. 被删路由地址（src/ + e2e/）
grep -rn "customer-opportunities\|resource/expense\|resource/contract-borrow\|resource/bid-result\|resource/bar\b\|audit-logs\b\|knowledge/template\b" src e2e --include='*.js' --include='*.vue'
#    例外（允许保留）：/api/qualifications/…/audit-logs、/api/tenders/…/audit-logs 等后端 API 路径字符串
#    例外：e2e/qualification-*.spec.js 中的资质操作日志 API 断言

# 2. 被删路由 name
grep -rn "'CustomerOpportunityCenter'\|'AuditLogs'\|'BAR'\|'BAR_SiteList'\|'BAR_SiteDetail'\|'BAR_SOPDetail'\|'Expense'\|'ContractBorrow'\|'BidResult'" src --include='*.js' --include='*.vue'

# 3. 被删文件路径符号
grep -rn "views/Resource/BAR\|views/Resource/expense\|Resource/Expense.vue\|Resource/ContractBorrow\|Resource/BidResult\|Resource/Calendar\|Resource/bid-result\|AuditLogPage\|CustomerOpportunityCenter\|customer-opportunity/\|Knowledge/Template.vue\|components/template/\|Analytics/Dashboard.vue\|Analytics/CompetitionIntel\|Analytics/ScoreAnalysis\|Analytics/ROIAnalysis\|Bidding/Favorites\|market-prediction\|Document/DocumentAssembly\|modules/contractBorrow\|modules/bidResults\|modules/templates.js" src e2e --include='*.js' --include='*.vue'

# 4. 隐藏机制与联动符号
grep -rn "hiddenApiMenuNames\|isApiDeliveryMode\|goToResultPage\|contractBorrowApi\|bidResultsApi" src --include='*.js' --include='*.vue'

# 5. 文件不存在断言（示例）
test ! -e src/views/Resource/BAR && test ! -e src/views/Knowledge/Template.vue && test ! -e e2e/customer-opportunity-center.spec.js
```

## Part II — 必须存在（MUST BE PRESENT）

```bash
# 共享能力文件仍在
test -e src/views/System/settings/AuditLogPanel.vue        # 日志说明页共用
test -e src/api/modules/customerOpportunity.js             # 项目创建商机转化
test -e src/components/ai/CompetitionIntel.vue             # 项目详情竞争情报
test -e src/components/ai/ROIAnalysis.vue                  # 项目详情 ROI
test -e src/composables/useDocumentAssembly.js             # 标书编辑器
test -e src/api/modules/tenderFavorites.js                 # 标讯收藏按钮
test -e src/views/Bidding/list/components/FavoriteButton.vue
test -e src/views/AI/MarketTiming.vue                      # 商机时间预测
test -e src/views/System/OperationLogPage.vue              # 日志说明页

# 共享能力调用链仍在
grep -q "knowledgeApi.templates" src/composables/projectDetail/useProjectDetailBoot.js   # 模板 API（文档/项目详情用）
grep -q "qualificationApi.createBorrow\|createBorrow" src/views/Dashboard/useWorkbenchQuickStart.js  # 资质借阅
grep -q "convertToProject" src/views/Project/create/composables/useProjectCreateSubmit.js # 商机转化
```

## Part III — 行为契约（MUST PASS）

| 断言 | 命令 |
|---|---|
| 构建成功 | `npm run build` |
| 全量单测通过 | `npm test` |
| 残留引用为零 | Part I 5 条 grep + test 全部 0 命中/真 |

## 例外登记（允许的合法残留）

| 符号 | 允许存在的位置 | 理由 |
|---|---|---|
| `/api/**/audit-logs` 字符串 | `src/api/modules/audit.js`、`accounts.js`、`ca.js`、`e2e/qualification-*.spec.js` | 后端接口保留（FR-009），这些是资质/账户操作日志 API，与"审计日志页面"无关 |
| `templates`（词根） | `knowledge.js`、`config/templateLibrary.js`、`useProjectDetailBoot/Init`、`useDocumentKnowledge` | 现役模板选择能力 |
| `borrow`（词根） | `useWorkbenchQuickStart.js`、`workbench-quick-start-core.js` | 资质借阅现役 |
