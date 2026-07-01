# CO-458 开发计划：任务提交审核时交付物和完成情况必填

## 需求重述

任务从 TODO 提交为 REVIEW 时，必须满足两个条件：
1. 至少有 1 个交付物
2. 完成情况说明（completionNotes）非空

影响两个看板场景，统一走同一套后端校验：
- 项目详情 → 标书制作阶段 → 任务看板
- 跨部门执行人独立任务看板（也是走 projectsApi）

## 架构原则

- **FP-Java Split-First Rule**：纯核心做业务决策，应用服务只做编排
- 单文件不超过 300 行，超过先拆分
- 任何类不同时承担 3 类以上职责（规则计算/数据访问/DTO转换/状态写入）

## 任务拆解

### Task 1：纯核心层 - TaskTransitionPolicy 加 validateSubmission
- **文件**：`backend/src/main/java/com/xiyu/bid/task/core/TaskTransitionPolicy.java`
- **内容**：新增 `validateSubmission(current, target, deliverableCount, completionNotes)` 静态方法
- **测试**：`TaskTransitionPolicyTest.java` 加 4 个用例（无交付物/无完成情况/都有/非TODO转REVIEW跳过）
- **职责**：纯核心，无副作用，只做决策

### Task 2：应用层 - ProjectTaskWorkflowService 集成校验+持久化
- **文件**：`backend/src/main/java/com/xiyu/bid/projectworkflow/service/ProjectTaskWorkflowService.java`
- **内容**：
  1. `updateProjectTaskStatus` 中 TODO→REVIEW 时调用 `TaskTransitionPolicy.validateSubmission`
  2. 持久化 `completionNotes`（从 request 中读取）
  3. DTO `ProjectTaskStatusUpdateRequest` 加 `completionNotes` 字段
- **测试**：更新相关测试类的 mock（新增 TaskDeliverableRepository 依赖）
- **职责**：编排（调核心策略、读数据、写状态），不做业务决策

### Task 3：前端 - API 层原子化（projects.js + useProjectDetailTaskActions.js）
- **文件**：
  - `src/api/modules/projects.js` - `updateTaskStatus` 加 `completionNotes` 参数
  - `src/composables/projectDetail/useProjectDetailTaskActions.js` - `handleSubmitReview` 合并两次 API 调用为一次
- **内容**：completionNotes 随状态更新一次请求完成，消除竞态
- **职责**：API 编排

### Task 4：前端 - 独立任务看板原子化（TaskBoardPage.vue）
- **文件**：`src/views/TaskBoard/TaskBoardPage.vue`
- **内容**：
  1. `handleSubmitForReview` 合并 `updateTask` + `updateTaskStatus` 为一次 `updateTaskStatus` 调用
  2. 加前端预校验（交付物+完成情况必填）
- **职责**：用户体验层提前拦截

### Task 5：前端 - 校验收敛 + UI 必填视觉
- **文件**：
  - `src/components/project/TaskForm.vue` - `submitForReview` 加校验 + "完成情况说明"标必填
  - `src/components/common/TaskBoard.vue` - 下拉菜单 TODO→REVIEW 加禁用判断
  - `src/components/project/ProjectTaskBoardCard.vue` - 抽屉"提交审核"按钮走 TaskForm 校验
  - `src/composables/useTaskActions.js` - 弹窗加 completionNotes 必填校验
- **内容**：前端统一预校验逻辑（最终以后端为准）
- **职责**：用户体验层

### Task 6：Code Review + 架构验证
- 跑 `ArchitectureTest`
- 跑相关单元测试
- line-budget 检查
- 安全检查（无新的敏感操作）

## 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| ProjectTaskWorkflowService 超 300 行 | 架构违规 | 加逻辑前先评估，超过则拆分 |
| 前端校验散落各处易漏改 | 维护成本高 | 后端是唯一真相源，前端只做体验优化 |
| 两个看板入口不统一 | 漏校验 | 确认都走 projectsApi → ProjectTaskWorkflowService |

## 验收标准

1. 后端：TODO→REVIEW 无交付物 → 返回 422 + "提交审核时必须上传交付物"
2. 后端：TODO→REVIEW 无完成情况 → 返回 422 + "提交审核时必须填写完成情况"
3. 后端：非 TODO→REVIEW 转换不受影响
4. 前端：两个看板的提交审核都做预校验提示
5. 架构测试全绿
6. line-budget 通过
