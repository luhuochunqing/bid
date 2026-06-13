# 费用申请与支付跟踪改造计划

## 背景

客户要求系统线上化完成以下流程：

1. 标书购买费、投标保证金的申请、审批与支付记录跟踪。
2. 投标差旅费用、标书制作费等相关费用归纳汇总到具体投标项目。
3. 项目详情与费用台账保持一致，形成真实归集展示，而不是演示数据。

当前仓库已有费用申请、审批、保证金退还、费用台账等基础能力，但缺少独立支付记录闭环，项目详情费用汇总仍是前端 mock 数据。

## 现状差距

### 已有能力

- `Expense` 已支持申请、审批、退还状态。
- `ExpenseApprovalRecord` 已记录审批历史。
- `/api/resources/expenses` 已支持创建、审批、退还、按项目统计。
- `src/views/Resource/Expense.vue` 已有费用台账、审批、退还页面。

### 缺口

- 没有独立的支付记录实体、接口、历史查询能力。
- 费用状态 `PAID` 只能展示，缺少“登记支付”动作和支付详情。
- 项目详情页的费用汇总仍使用硬编码 mock 数据。
- `Expense.vue` 与 `Project/Detail.vue` 严重超长，不符合 Split-First Rule。
- `ExpenseService` 仍同时承担规则决策、状态写入、记录写入、查询拼装，违反 FP-Java Profile。

## 目标

### 业务目标

- 支持费用申请。
- 支持费用审批。
- 支持已审批费用登记支付，并保留支付记录。
- 支持保证金退还申请与确认。
- 支持在项目详情中查看真实费用归集与汇总。

### 架构目标

- 纯核心负责状态流转和业务判断。
- 应用服务只做编排，不承担规则计算。
- DTO 映射与状态写入、查询拼装分离。
- 新增或改造的单文件超过 300 行前必须拆分。

## 设计方案

### 后端分层

在 `resources/expense` 子域内拆分：

- `domain`
  - `ExpensePaymentPolicy` 或由 `Expense` 聚合负责支付/退还可执行性判断。
- `application`
  - `ExpenseCommandApplicationService`
  - `ExpenseQueryApplicationService`
  - `ExpensePaymentApplicationService`
- `infrastructure`
  - `ExpenseRepository`
  - `ExpenseApprovalRecordRepository`
  - `ExpensePaymentRecordRepository`
- `dto/mapper`
  - `ExpenseResponseMapper`
  - `ExpensePaymentRecordDTO`
  - `ExpensePaymentCreateRequest`

### 数据模型

新增 `expense_payment_records` 表：

- `id`
- `expense_id`
- `amount`
- `paid_at`
- `paid_by`
- `payment_reference`
- `payment_method`
- `remark`
- `created_at`

规则：

- 只有 `APPROVED` 状态费用允许登记支付。
- 支付后 `Expense.status = PAID`。
- 支付记录按费用维度保留历史。
- 保证金退还仍以 `Expense` 主状态驱动，但允许查看支付记录与退还记录。

### API 合同

新增或补充：

- `POST /api/resources/expenses/{id}/payments`
- `GET /api/resources/expenses/{id}/payments`
- `GET /api/resources/expenses/project/{projectId}` 前端正式接入

现有接口继续保留：

- `POST /api/resources/expenses`
- `POST /api/resources/expenses/{id}/approve`
- `POST /api/resources/expenses/{id}/return-request`
- `POST /api/resources/expenses/{id}/confirm-return`

### 前端改造

`Expense.vue`

- 拆为：
  - `components/expense/ExpenseToolbar.vue`
  - `components/expense/ExpenseLedgerTable.vue`
  - `components/expense/ExpenseApprovalTable.vue`
  - `components/expense/ExpenseDepositTrackingTable.vue`
  - `components/expense/dialogs/ExpenseApplyDialog.vue`
  - `components/expense/dialogs/ExpenseApproveDialog.vue`
  - `components/expense/dialogs/ExpensePaymentDialog.vue`
  - `components/expense/dialogs/ExpenseDetailDialog.vue`
  - `composables/useExpensePage.ts|js`

功能补齐：

- 已审批未支付费用展示“登记支付”按钮。
- 详情弹窗展示支付记录。
- 保证金退还跟踪保留，但基于真实状态与真实支付记录展示。

`Project/Detail.vue`

- 去掉本地 `projectExpenses` mock。
- 新增项目费用加载逻辑，调用真实费用接口。
- 把费用卡片抽离为独立组件，例如 `ProjectExpenseSummaryCard.vue`。

## 并行拆分

### Agent A: 后端核心与支付记录

- 新增支付记录实体、仓储、DTO、mapper、应用服务。
- 改造费用命令流程与控制器。
- 补集成测试。

### Agent B: 前端费用台账与支付登记

- 拆分 `Expense.vue`。
- 接入支付登记、支付记录展示、项目查询。

### Agent C: 项目详情真实归集

- 拆分项目详情中的费用卡片。
- 接入按项目加载费用数据与汇总。

### 主控集成

- 统一 API 契约。
- 收敛文件职责。
- 跑验证、做 code review、refactor-clean。

## 测试策略

### 后端

- `ExpenseControllerIntegrationTest` 扩展：
  - 审批后登记支付成功。
  - 未审批费用登记支付失败。
  - 查询支付记录成功。
  - 项目维度查询能拿到真实费用。

### 前端

- 至少补 API 层契约与关键状态映射测试。
- 若仓库已有组件测试基础，则补支付登记与项目归集场景。

### 回归

- 费用申请。
- 审批。
- 登记支付。
- 保证金退还申请与确认。
- 项目详情费用汇总与费用台账一致。

## 风险

- `Project/Detail.vue` 过大，局部改动容易引入回归。
- 旧的 `ResourceResponseMapper` 与平铺式 `resources` 包结构可能与新拆分有耦合。
- Flyway baseline 与增量 migration 需要同时兼容。

## 完成标准

- 客户口径可升级为“已实现费用申请、审批、支付记录跟踪、项目归集展示”。
- 支付记录具备真实落库、可查询、可展示能力。
- 项目详情不再依赖 mock 费用数据。
- 新增与改造代码符合 FP-Java Profile + Split-First Rule。
