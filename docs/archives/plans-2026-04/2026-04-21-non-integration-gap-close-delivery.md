# 非集成缺口补齐交付说明

日期：2026-04-21

分支：`feat/non-integration-gap-close`

提交：`ea471bc8 feat: complete non-integration gap closure`

基线文档：

- [需求完成度核查-非集成范围-2026-04-21.md](/Users/user/xiyu/xiyu-bid-poc/docs/需求完成度核查-非集成范围-2026-04-21.md)
- [2026-04-21-non-integration-gap-close-eng-review.md](/Users/user/xiyu/xiyu-bid-poc/docs/plans/2026-04-21-non-integration-gap-close-eng-review.md)
- [2026-04-21-non-integration-gap-close-execution.md](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/docs/plans/2026-04-21-non-integration-gap-close-execution.md)

## 1. 交付结论

本次只覆盖 4 个非集成缺口，已按 `plan -> tdd -> code-review -> refactor-clean` 完成实现与验证：

1. 智能日程与预警
2. 标讯分发与指派跟进
3. 超前预测与市场洞察
4. AI 标书检查中的文本质量辅助

交付标准达到：

- 前端入口真实可达
- 前端 API 真实存在
- 后端 controller 闭环可调用
- 关键业务规则已拆入 policy / query / app service
- 最低必要测试已补齐

## 2. 分阶段完成情况

### 2.1 Plan

- 以 engineering review 文档冻结范围，不扩到集成项
- 建立执行单并拆成 4 个专家任务面
- 明确前端、API、controller、service、test 五类落点

### 2.2 TDD

- 先补关键 contract / policy / composable / adapter 测试
- 再补前后端缺口实现
- 对高风险点增加迁移、latest 选择、部分成功/失败、状态迁移测试

### 2.3 Code Review

- 校验真实路由、真实 API、真实 controller
- 校验前后端状态词一致性
- 校验没有重新引入 mock / demo persistence / 双模式兜底
- 校验 FP-Java Profile 与 Split-First 约束

### 2.4 Refactor-Clean

- 拆分过重文件与聚合职责
- 删除或替换本次范围内的临时桥接逻辑
- 把核心规则下沉为 policy，把读模型抽成 query service / assembler

## 3. 四项能力交付内容

### 3.1 智能日程与预警

前端：

- 工作台主读链路切到 `GET /api/workbench/schedule-overview`
- 工作台日程逻辑抽到 `useWorkbenchSchedule.js`
- 告警未处理数据改为真实接口驱动

API：

- 新增 [workbench.js](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/src/api/modules/workbench.js)
- 更新 [alerts.js](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/src/api/modules/alerts.js)

后端：

- `PATCH /api/alerts/history/{id}/acknowledge`
- `POST /api/alerts/history/{id}/resolve`
- `GET /api/alerts/history`
- `GET /api/workbench/schedule-overview`

架构拆分：

- `AlertLifecyclePolicy`
- `AlertHistoryCommandService`
- `AlertHistoryQueryService`
- `WorkbenchScheduleQueryService`

### 3.2 标讯分发与指派跟进

前端：

- 列表页状态词统一映射到 canonical status
- 批量领取、批量指派、批量状态更新走真实 API
- 成功/部分成功/失败提示与回刷语义补齐

API：

- 新增 [batch.js](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/src/api/modules/tenders/batch.js)

后端：

- `POST /api/batch/tenders/claim`
- `PATCH /api/batch/tenders/status`
- `POST /api/batch/tenders/assign`
- `GET /api/tenders/{id}/assignment`
- `GET /api/tenders/assignment-candidates`

架构拆分：

- `TenderStatusTransitionPolicy`
- `BatchTenderStatusAppService`
- `BatchTenderAssignAppService`
- `TenderAssignmentQueryService`
- `TenderAssignmentViewAssembler`

### 3.3 超前预测与市场洞察

前端：

- `CustomerOpportunityCenter.vue` 拆成容器、看板、客户池、详情、历史抽屉
- 真正接上 insights / purchases / predictions / refresh / convert API
- 转项目后增加状态回写闭环

API：

- 重写 [customerOpportunity.js](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/src/api/modules/customerOpportunity.js)

后端：

- `GET /api/customer-opportunities/insights`
- `GET /api/customer-opportunities/{purchaserHash}/purchases`
- `GET /api/customer-opportunities/{purchaserHash}/predictions`
- `POST /api/customer-opportunities/refresh`
- `PUT /api/customer-opportunities/predictions/{id}/status`
- `PUT /api/customer-opportunities/predictions/{id}/convert`

架构拆分：

- `CustomerOpportunityAppService`
- `CustomerOpportunityQueryService`
- `CustomerOpportunityLifecycleService`

补充闭环：

- [Create.vue](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/src/views/Project/Create.vue) 在项目创建成功后回写商机转化状态

### 3.4 AI 标书检查中的文本质量辅助

前端：

- 质量检查从 `useProjectDetailAI` 拆到 `useProjectDetailQuality`
- 项目详情增加真实空态、执行态、结果态、采纳、忽略动作

API：

- 新增 [quality.js](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/src/api/modules/ai/quality.js)

后端：

- `POST /api/projects/{projectId}/quality-checks`
- `GET /api/projects/{projectId}/quality-checks/latest`
- 质量 issue 采纳 / 忽略闭环

架构拆分：

- `ProjectQualityService`
- 质量问题归属校验
- latest 结果选择与 DTO 归一化

数据迁移：

- [V72__non_integration_gap_closure_schema.sql](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/backend/src/main/resources/db/migration-mysql/V72__non_integration_gap_closure_schema.sql)
- [V73__create_project_quality_tables.sql](/Users/user/xiyu/xiyu-bid-poc/.worktrees/integration/backend/src/main/resources/db/migration-mysql/V73__create_project_quality_tables.sql)

## 4. 关键测试与验证

前端验证：

- `npm run test:unit`
- `npm run build`

后端验证：

- `mvn -DskipTests compile`
- 目标测试集通过：
  - `AlertHistoryControllerTest`
  - `AlertHistoryControllerContractTest`
  - `AlertHistoryQueryServiceTest`
  - `AlertLifecyclePolicyTest`
  - `WorkbenchScheduleControllerTest`
  - `WorkbenchScheduleControllerContractTest`
  - `TenderStatusTransitionPolicyTest`
  - `BatchTenderStatusAppServiceTest`
  - `BatchTenderAssignAppServiceTest`
  - `CustomerOpportunityControllerTest`
  - `CustomerOpportunityQueryServiceTest`
  - `CustomerOpportunityLifecycleServiceTest`
  - `ProjectQualityControllerTest`
  - `ProjectQualityServiceTest`

架构验证：

- `MaintainabilityArchitectureTest`
- `FPJavaArchitectureTest`
- `ArchitectureTest`

## 5. 约束执行结果

本次新增与重构代码已按以下原则落地：

- 纯核心负责业务规则决策
- 应用服务只负责编排
- DTO 组装从 controller / service 中抽离
- 查询与写入职责拆分
- 触碰到的大文件优先按 feature 子模块拆分

本次明确处理的典型点：

- 告警状态机拆入 `AlertLifecyclePolicy`
- 标讯状态机拆入 `TenderStatusTransitionPolicy`
- 商机大页面拆为多组件 + composable
- AI 质量从通用 AI composable 中拆出

## 6. 当前状态

集成工作区当前提交已完成，分支状态为相对 `origin/main` 提前 2 个提交，可继续进入联调、演示或 PR 流程。
