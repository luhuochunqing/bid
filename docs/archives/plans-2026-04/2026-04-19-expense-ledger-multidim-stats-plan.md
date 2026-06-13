# 费用台账与多维统计实施计划

## 背景

客户要求系统能够归集所有投标相关费用，形成费用台账，并支持按项目、时间、部门等维度进行查询与统计。

当前系统已经具备以下基础能力：

- 费用申请、审批、保证金退还主流程已经存在。
- 费用记录已经关联 `projectId`，支持按项目或时间范围基础查询。
- 前端已有费用管理页面，但查询条件与统计口径仍偏弱。

当前缺口：

- 缺少部门维度数据归集与查询统计。
- 缺少统一的多维筛选接口，前端仍以页面内过滤为主。
- 缺少面向台账场景的聚合统计返回结构。
- 费用页面文件过长，明显违反 Split-First Rule。

## 目标

本次实现后，系统应满足：

1. 建立投标费用台账的统一查询接口。
2. 支持按项目、时间范围、部门、费用类型、状态筛选。
3. 返回用于页面展示的汇总统计，包括总金额、已支付、待审批、待退还、按部门汇总、按项目汇总。
4. 前端费用页面升级为真实多维筛选与统计展示。
5. 代码结构满足 FP-Java Profile + Split-First Rule。

## 设计原则

### FP-Java Profile

- 纯核心负责业务决策与规则判断，不直接访问数据库。
- 应用服务只做编排，不承载复杂规则计算。
- DTO 转换、数据访问、状态写入、规则计算分离。

### Split-First Rule

- 超过 300 行的文件优先拆分后再继续增强。
- 现有 `Expense.vue` 和 `resources.js` 本次必须拆分。

## 范围拆解

### `/plan`

- 明确费用台账目标模型与统计口径。
- 识别部门维度来源：基于项目经理/项目成员可访问部门关系，先以项目经理所属部门作为主口径返回，避免无部门字段时无法落地。
- 确认基线测试状态并记录既有失败。

### `/odd`

后端：

- 为费用台账新增查询条件对象、统计对象、行对象。
- 新增台账查询应用服务，负责编排仓储查询、项目映射、部门映射、聚合统计。
- 新增纯规则/聚合核心，负责统计汇总与分组计算。
- 扩展费用控制器，提供台账查询与统计接口。
- 补充针对项目、时间、部门筛选与统计的测试。

前端：

- 拆分费用页面为筛选区、统计区、台账表格、审批区等子组件或组合式逻辑。
- 拆分资源 API 中的费用适配逻辑。
- 新增时间范围、部门筛选与真实统计渲染。
- 保留既有审批与保证金退还主流程。

### `/code-review`

- 重点检查职责越界、状态流回归、接口字段兼容性。
- 检查是否把规则写回应用服务或控制器。
- 检查是否引入页面伪统计或前端本地推导替代后端统计。

### `/refactor-clean`

- 清理费用页面中的重复计算和死分支。
- 合并冗余映射逻辑，删除不再需要的伪 mock 分支。
- 再次运行测试与构建验证。

## 技术方案

### 后端

新增结构建议：

- `com.xiyu.bid.resources.expenseledger.application`
- `com.xiyu.bid.resources.expenseledger.domain`
- `com.xiyu.bid.resources.expenseledger.dto`

职责划分：

- `ExpenseLedgerApplicationService`
  - 负责请求编排、调用仓储、组装响应。
- `ExpenseLedgerStatisticsCalculator`
  - 纯核心，负责汇总统计和按维度聚合。
- `ExpenseLedgerQuery`
  - 查询条件对象。
- `ExpenseLedgerItemDTO` / `ExpenseLedgerSummaryDTO` / `ExpenseLedgerResponse`
  - 台账结果对象。

部门口径：

- 首版以项目经理所属部门作为项目所属部门。
- 若项目经理缺失部门，则回退 `UNASSIGNED` / `未分配部门`。

### 前端

新增结构建议：

- `src/views/Resource/expense/ExpenseFilters.vue`
- `src/views/Resource/expense/ExpenseStats.vue`
- `src/views/Resource/expense/ExpenseLedgerTable.vue`
- `src/views/Resource/expense/useExpenseLedgerPage.js`
- `src/api/modules/resources/expenses.js`

页面行为：

- 搜索条件直接调用后端台账接口。
- 统计卡片展示后端返回的聚合结果，不再完全依赖前端即时计算。
- 导出基于当前筛选结果导出。

## 测试策略

后端新增：

- 台账查询按项目筛选测试。
- 台账查询按时间范围筛选测试。
- 台账查询按部门筛选测试。
- 汇总统计正确性测试。

前端新增：

- 费用 API 适配层单测。
- 组合式逻辑对筛选参数和统计数据的映射单测。

## 风险

1. 部门维度目前不是费用表原生字段。
   - 解决：首版以项目经理部门作为台账主部门口径。
2. 前端费用页面超过 1000 行，改动时容易引入回归。
   - 解决：先拆分，再增强。
3. 仓库现有前端单测存在与本需求无关的既有失败。
   - 解决：单独记录为基线问题，不把它误判为本次回归。

## 基线验证

- `npm run test:unit`
  - 存在既有失败：`src/views/Bidding/List.spec.js` 2 条失败，与本需求无关。
- `mvn -q -Dtest=ExpenseControllerIntegrationTest test`
  - 通过。

## 执行决策

用户已经明确授权按四阶段持续执行，因此本计划文档创建后直接进入 `/odd` 阶段实现，不再额外等待确认。
