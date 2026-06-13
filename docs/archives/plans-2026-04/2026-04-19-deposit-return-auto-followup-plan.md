# 保证金归还自动跟踪提醒实现计划

## 背景

客户要求：根据开标结果以及退款时间，系统自动触发对保证金退还情况的跟踪提醒。

当前代码已具备：

- 保证金费用申请、审批、退还申请、确认退还接口
- 投标结果确认链路
- 告警历史与定时调度框架

当前缺口：

- `Expense` 缺少预计退还时间、最近提醒时间等事实字段
- 保证金跟踪页用前端 `缴纳日期 + 60 天` 假算应退日期
- “提醒”按钮只有 toast，没有真实提醒落库
- 告警调度没有“保证金退还提醒”类型，也没有消费 `depositWarnDays`
- “开标结果”与“保证金退还跟踪”没有真实联动

## 目标

形成真实 API 单一路径下的最小闭环：

1. 保证金费用支持维护预计退还日期
2. 系统基于已确认的开标结果 + 预计退还日期自动扫描未退还保证金
3. 系统在临近到期或已逾期时生成真实提醒记录
4. 费用页展示真实的应退日期、跟踪状态、最近提醒时间
5. 手工“提醒”按钮改为真实接口，而非 toast

## 设计原则

- 遵循 FP-Java Profile：
  - 纯核心只做业务判定
  - 应用服务只做查询、判定、写入编排
- 遵循 Split-First Rule：
  - 触碰超过 300 行的文件前先拆分
  - `Expense.vue`、`AlertSchedulerService.java` 本次必须收敛
- 不新增 mock/demo 路径

## 方案拆解

### Phase 1: Plan

- 明确真实数据源：`Expense`、`BidResultFetchResult`、`AlertHistory`
- 约束自动提醒触发条件：
  - 费用类型为保证金
  - 状态不是 `RETURNED`
  - 已存在已确认的开标结果
  - 已维护预计退还日期
- 使用 `depositWarnDays` 作为提前提醒阈值

### Phase 2: ODD / 开发

#### 后端

- 为 `Expense` 增加字段：
  - `expectedReturnDate`
  - `lastReturnReminderAt`
- 新增纯核心：
  - `DepositReturnTrackingPolicy`
  - `DepositReturnTrackingSnapshot`
  - `DepositReturnTrackingDecision`
- 新增应用服务：
  - `ScanDepositReturnTrackingAppService`
  - `SendExpenseReturnReminderAppService`
- 为 `AlertRule.AlertType` 增加 `DEPOSIT_RETURN`
- 拆分 `AlertSchedulerService`，将保证金扫描委托给新的应用服务
- 扩展 `ExpenseController`：
  - 创建/更新支持 `expectedReturnDate`
  - 新增手工发送退还提醒接口
- 扩展 `ExpenseDTO` 与前端 API 返回字段
- 新增 Flyway migration 更新 `expenses` 与 `alert_rules` 约束

#### 前端

- 拆分 `src/views/Resource/Expense.vue`
  - 页面容器保留
  - 逻辑抽到 `useExpensePage.js`
  - 将费用表、保证金跟踪表、对话框拆为子组件
- 费用申请表单支持录入预计退还日期
- 保证金跟踪改用后端真实字段：
  - `expectedReturnDate`
  - `lastReturnReminderAt`
  - 真实状态派生
- 手工提醒按钮接真实接口

#### 测试

- 后端单测：纯核心判定规则
- 后端单测：自动扫描应用服务
- 后端集成：费用创建/详情/提醒接口
- 前端单测：`resources.js` 费用归一化

### Phase 3: Code Review

- 检查模块边界：
  - Controller 不直连 Repository
  - 纯核心不依赖 Spring / JPA
- 检查大文件是否已拆分
- 检查新增字段是否全部走真实链路
- 检查是否扩大了 mock 遗留

### Phase 4: Refactor-Clean

- 删除前端“+60天”假算逻辑
- 删除提醒 toast 的伪完成路径
- 更新模块 README 与计划文档
- 重新跑验证

## 验证计划

前端：

- `npx vitest run src/api/modules/resources.spec.js`
- `npm run check:front-data-boundaries`
- `npm run check:doc-governance`
- `npm run build`

后端：

- `mvn test -Dtest=AlertSchedulerServiceTest,ExpenseControllerIntegrationTest,SettingsServiceTest`
- `mvn test -Dtest=ArchitectureTest`

## 风险

- `AlertSchedulerService` 当前已超 300 行，不能继续堆逻辑
- `Expense.vue` 当前超过 1100 行，必须拆分后再扩展
- `alert_rules` 基线 SQL 有 check constraint，需要 migration 兼容 `DEPOSIT_RETURN`
