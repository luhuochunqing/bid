# Resources 最小真实契约补口计划

## 目标

在不破坏现有双模式架构和 `mock.js` 演示能力的前提下，为 `resources` 模块补齐两条最小真实后端闭环：

1. `BAR certificates + borrow flow`
2. `expense approval/return flow`

本轮不追求把 BAR 的所有子资源全部后端化，而是补足当前前端已经暴露、且最影响真实系统可用性的两条流程。核心原则：
- 后端继续作为真实系统事实源
- 前端统一 API 层继续做模式切换和字段归一化
- `mock` 模式保留完整演示态
- 不新增第二套并行资源模型

## 当前事实与边界

### 1. BAR 现状
- 后端目前只有扁平 `BarAsset` 实体与 `/api/resources/bar-assets` CRUD。
- 前端 BAR 页面消费的是“站点 + 账号 + UK + SOP + 附件 + 借用审批”的复合视图模型。
- 现有前端已经通过 `resourcesApi.barSites` 将扁平 `BarAsset` 适配为站点台账，但账号/证书/借用审批仍是 `mock` 逻辑或显式降级。

### 2. Expense 现状
- 后端 `Expense` 只有基础台账字段：`projectId/category/amount/date/description/createdBy`。
- 前端费用页已经展示并依赖 `approvalStatus`、保证金退还状态、审批记录、审批动作、退还确认动作。
- 当前 API 层已把这些动作显式降级，但真实流程不存在。

### 3. 本轮设计约束
- 不额外引入全新的 BAR 站点表；继续以现有 `BarAsset` 为 BAR 资产主表。
- 不建立通用工作流引擎或复杂审批表，只补能驱动现有页面的最小状态机和记录字段。
- 只补当前页面确实会调用的接口；其余高级能力继续保留 mock 或显式降级。

## 方案概览

## A. BAR certificates + borrow flow

### A1. 后端数据模型
在 `resources` 域新增证书子资源和借用记录，挂靠到 `BarAsset`：

- `BarCertificate`
  - `id`
  - `barAssetId`
  - `type`
  - `provider`
  - `serialNo`
  - `holder`
  - `location`
  - `expiryDate`
  - `status` (`AVAILABLE`, `BORROWED`, `EXPIRED`, `DISABLED`)
  - `currentBorrower`
  - `currentProjectId`
  - `borrowPurpose`
  - `expectedReturnDate`
  - `remark`
  - `createdAt/updatedAt`

- `BarCertificateBorrowRecord`
  - `id`
  - `certificateId`
  - `borrower`
  - `projectId`
  - `purpose`
  - `remark`
  - `borrowedAt`
  - `expectedReturnDate`
  - `returnedAt`
  - `status` (`BORROWED`, `RETURNED`)

说明：
- 不单独做审批单实体。当前 UI 的“审批流”仍保留展示感，但真实系统最小闭环只保证借用与归还可持久化。
- 若后续要扩审批，可在借用记录上加 `approvalStatus`，但不在本轮做。

### A2. 后端接口
新增到 `resources` 域，且路由明确挂在 `bar-assets` 下，避免再造第二套 `/sites` 语义：

- `GET /api/resources/bar-assets/{assetId}/certificates`
- `POST /api/resources/bar-assets/{assetId}/certificates`
- `PUT /api/resources/bar-assets/{assetId}/certificates/{certificateId}`
- `DELETE /api/resources/bar-assets/{assetId}/certificates/{certificateId}`
- `POST /api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow`
- `POST /api/resources/bar-assets/{assetId}/certificates/{certificateId}/return`
- `GET /api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow-records`

请求策略：
- `borrow` 使用 body，包含 `borrower/projectId/purpose/remark/expectedReturnDate`
- `return` 使用 body，至少包含 `remark`，其余由服务端补当前时间

### A3. 后端实现点
- 新增 entity/repository/service/controller/dto
- 在 service 中实现状态转换约束：
  - 只有 `AVAILABLE` 才能借用
  - 只有 `BORROWED` 才能归还
  - 归还时写回 `RETURNED` 借用记录并清空当前借用字段
- 在 list/detail 返回中允许前端单独查证书列表，不强塞嵌套 JSON 到现有 `BarAsset` 响应

### A4. 前端改造
- `resourcesApi.certificates` 从“显式降级”改为真实 API：
  - `getList/create/update/delete/borrow/return/getBorrowRecords`
- `barStore` 中 `addUk/updateUk/deleteUk/borrowUk/returnUk`：
  - `mock` 模式继续走当前内存逻辑
  - `api` 模式改为调用 `resourcesApi.certificates`
- 迁移页面：
  - `BorrowDialog.vue` 提交真实 borrow
  - `SiteDetail.vue` 的 UK 管理操作接真实接口
  - `CheckPanel.vue` 的借用动作接真实接口
- 保留边界：
  - BAR 账号子资源仍不补后端，继续显式提示未接入
  - SOP/附件/审计日志继续保留 mock 或只读展示，不在本轮补口

## B. Expense approval/return flow

### B1. 后端数据模型扩展
在现有 `Expense` 实体上补充状态字段，不新建第二张费用主表：

- `status`
  - `PENDING_APPROVAL`
  - `APPROVED`
  - `REJECTED`
  - `PAID`
  - `RETURN_REQUESTED`
  - `RETURNED`
- `approvalComment`
- `approvedBy`
- `approvedAt`
- `returnRequestedAt`
- `returnConfirmedAt`
- `returnComment`

同时新增 `ExpenseApprovalRecord` 记录审批历史：
- `id`
- `expenseId`
- `result` (`APPROVED`, `REJECTED`)
- `comment`
- `approver`
- `actedAt`

说明：
- 前端当前的“审批记录表”可以由这张历史表提供。
- 保证金退还确认不新建独立表，直接落到 `Expense` 状态字段。

### B2. 后端接口
在现有 `ExpenseController` 基础上补最小动作接口：

- `GET /api/resources/expenses/approval-records`
  - 可先支持按 `projectId` 可选过滤
- `POST /api/resources/expenses/{id}/approve`
- `POST /api/resources/expenses/{id}/return-request`
- `POST /api/resources/expenses/{id}/confirm-return`

请求策略：
- `approve` body: `result/comment/approver`
- `return-request` body: `comment/requestedBy`
- `confirm-return` body: `comment/confirmedBy`

### B3. 后端实现点
- 创建费用时默认 `status = PENDING_APPROVAL`
- 审批通过后置为 `APPROVED`；页面语义上仍可映射为“待支付”
- 审批拒绝后置为 `REJECTED`
- 保证金类费用允许 `return-request` 和 `confirm-return`
- 非保证金费用调用退还接口时报业务异常
- 更新 `getAllExpenses` 返回数据，允许前端读到新增状态字段

### B4. 前端改造
- `resourcesApi.expenses` 新增：
  - `getApprovalRecords`
  - `approve`
  - `requestReturn`
  - `confirmReturn`
- `Expense.vue`：
  - `api` 模式下审批记录区切到真实列表
  - 审批弹窗走真实 approve 接口
  - 保证金“申请退还/确认退还”走真实接口
  - 列表状态标签改由后端真实状态映射
- `mock` 模式继续保留当前审批记录和退还演示逻辑

## 实施顺序

1. 扩展后端 `Expense` 状态与审批记录
2. 落地 `Expense` 审批/退还接口
3. 扩展后端 `BarCertificate` 与借用记录
4. 落地 BAR 证书与借用/归还接口
5. 改前端 `resourcesApi` 对接上述新接口
6. 改 `barStore` 与 `Expense.vue`，从显式降级切到真实 API
7. 保持 `mock` 模式回归通过

## 验证计划

### 后端
- `mvn -DskipTests compile`
- 若当前测试基线允许，补最小 service/controller 测试：
  - 证书借用只能从 `AVAILABLE -> BORROWED`
  - 证书归还只能从 `BORROWED -> AVAILABLE`
  - 费用审批状态流转正确
  - 非保证金退还请求被拒绝

### 前端
- `npm run build`
- `VITE_API_MODE=api npm run build`
- 手工验证：
  - `mock` 模式下现有 BAR/费用演示不退化
  - `api` 模式下证书新增/编辑/借用/归还真实落库
  - `api` 模式下费用审批记录、审批动作、退还动作真实可用

## 风险与处理

- 风险：当前 `BarAsset` 被前端映射成“站点”，而真实后端只是资产。
  - 处理：本轮只把“证书”挂在 `BarAsset` 下，不进一步扩散 `site` 概念到后端路由层。
- 风险：费用前端现有标签语义和后端基础实体语义不一致。
  - 处理：通过前端 API 层做状态映射，避免页面直接耦合后端枚举名。
- 风险：审批人与借用人当前前端是中文姓名字符串，真实系统未统一用户 ID。
  - 处理：本轮后端字段允许先存字符串显示名，不强行引入用户外键耦合。

## 完成标准

- `api` 模式下 BAR 证书管理与借用/归还可真实执行
- `api` 模式下费用审批与保证金退还流程可真实执行
- `mock` 模式保持不变
- 前端不再对这两条能力显示“未接入真实后端”
- 其余未纳入本轮的 BAR 账号、SOP、附件能力继续明确保留演示/降级边界
