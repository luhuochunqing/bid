# Expense 子模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责说明
这里承接费用页的页面级拆分，包含筛选区、统计卡片、台账表格、保证金跟踪、审批记录，以及页面级组合式逻辑。
该目录只处理费用台账前端编排，不承载跨资源域 API 聚合。

## 边界清单

| 文件 | 地位 | 功能 |
|------|------|------|
| `ExpensePageContent.vue` | View Part | 费用页主编排层 |
| `useExpenseLedgerPage.js` | Composable | 聚合费用台账、审批记录、申请/审批/退还动作 |
| `ExpenseFiltersBar.vue` | Component | 项目、时间范围、部门、费用类型、状态筛选 |
| `ExpenseSummaryCards.vue` | Component | 后端真实统计卡片 |
| `ExpenseLedgerTable.vue` | Component | 费用台账主表 |
| `DepositTrackingTable.vue` | Component | 保证金归还跟踪 |
| `ApprovalRecordsTable.vue` | Component | 审批记录 |
