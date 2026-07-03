# 跨部门执行人任务通知跳转修复 — 实施计划

> 需求：跨部门执行人（bid-otherDept 角色）点击任务通知需跳转到任务看板，不可以直接跳转项目详情页
> 分支：`agent/qoder/cross-dept-task-notify-to-board`
> 工作流：Everything Claude Code 标准作业流程（plan → tdd → code-review → refactor-clean）

## 一、现状分析

### 1.1 CO-474 已实现的部分（commit 5251c2a1e / fc4dbd674）

`ProjectNotificationService.notifyTaskAssigned`（L97-115）已根据被分配人角色决定 targetUrl：
- `bid-otherDept` → `/task-board?taskId=X&projectId=Y`
- 其他角色 → `/project/{projectId}/drafting`

5 个调用点都走同一方法：
- `TaskService.java:97`（创建任务）、`:160`（更新任务）、`:242`（分配任务）
- `BatchTaskCommandService.java:110`（批量分配）
- `ProjectTaskWorkflowService.java:204`（项目任务工作流）

前端链路完整：
- `src/utils/notificationHelpers.js:74-76` 优先读 `payload.targetUrl`
- `src/views/TaskBoard/TaskBoardPage.vue:84-95` `openTaskFromQuery` 已实现 `?taskId=X` 自动打开任务详情抽屉

测试覆盖：`ProjectNotificationServiceTest.TaskAssigned.sendsToBidOtherDeptWithTaskBoardUrl`（L273-287）

### 1.2 本次发现的 Bug

`TaskReviewNotificationService` 是独立服务，**未走 CO-474 的角色判定逻辑**：

| 方法 | 接收人 | targetUrl | 是否需修复 |
|---|---|---|---|
| `notifyTaskReviewSubmitted`（L38-50） | `TASK_MUTATION_ALLOWED_ROLES`（admin/bidAdmin/bid-TeamLeader/bid-Team） | 硬编码 `/project/{id}/drafting` | ❌ 无需改（bid-otherDept 不在审核人集合） |
| **`notifyTaskReviewResult`（L55-66）** | **任务执行人 `assigneeId`** | **硬编码 `/project/{id}/drafting`** | **✅ 需修复** |

**触发场景**（`ProjectTaskWorkflowService.java:176-182`）：
- 任务审核通过（REVIEW → COMPLETED）或驳回（REVIEW → TODO）时
- 通知 `saved.getAssigneeId()`（任务执行人）
- 如果执行人是 bid-otherDept，会收到通知，跳转 `/project/{id}/drafting` → **越权进入项目详情页**

### 1.3 根因

CO-474 修复时只覆盖了「任务分配通知」（`notifyTaskAssigned`），遗漏了「任务审核结果通知」（`notifyTaskReviewResult`）。两个方法分属不同 Service（`ProjectNotificationService` vs `TaskReviewNotificationService`），targetUrl 决策逻辑未统一抽取，导致遗漏。

## 二、需求边界（用户已确认）

- **角色维度**：仅指 `bid-otherDept` 角色的执行人（CO-474 已实现的场景）
- **任务通知范围**：所有 bid-otherDept 角色接收的、与任务相关的通知
  - 任务分配通知 ✅ 已修复
  - 任务审核结果通知 ❌ 本次修复
  - 任务审核提审通知 — 接收人不会是 bid-otherDept，无需改

## 三、修复方案

### 3.1 FP-Java + Split-First Rule 设计

targetUrl 决策是**业务决策**（根据角色决定路由），应抽取为**纯核心**类：

```
notification/core/
└── TaskNotificationTargetUrlResolver.java    ← 纯核心，无 Spring 依赖
    └── 方法：resolveTargetUrl(projectId, taskId, roleCode) → String
```

- 输入：projectId、taskId、roleCode
- 输出：`/task-board?taskId=X&projectId=Y`（bid-otherDept）或 `/project/{id}/drafting`（其他）
- 纯函数，无 IO，可单测

应用服务（`ProjectNotificationService`、`TaskReviewNotificationService`）只做编排：
- 解析接收人角色（依赖 `EffectiveRoleResolver`）
- 调用纯核心 `TaskNotificationTargetUrlResolver.resolveTargetUrl`
- 调用 `notificationService.createNotification`

### 3.2 改造点

| 文件 | 改动 | 职责数 |
|---|---|---|
| `notification/core/TaskNotificationTargetUrlResolver.java`（新建） | 纯核心，targetUrl 决策 | 1（规则计算） |
| `ProjectNotificationService.java`（L110-115） | 删除私有方法，改调纯核心 | 1（编排） |
| `TaskReviewNotificationService.java`（L55-66） | 注入 `EffectiveRoleResolver`，根据接收人角色决定 targetUrl | 1（编排） |

### 3.3 测试策略（TDD）

| 测试文件 | 覆盖点 |
|---|---|
| `TaskNotificationTargetUrlResolverTest.java`（新建） | 纯核心：bid-otherDept → task-board，其他角色 → project drafting，null/空角色 → project drafting |
| `ProjectNotificationServiceTest.java`（已有，微调） | 现有 2 个用例保持绿，验证改调纯核心后行为不变 |
| `TaskReviewNotificationServiceTest.java`（新建或扩展） | bid-otherDept 执行人 → task-board，bid-Team 执行人 → project drafting，null assignee → skip |

## 四、任务拆解（并行执行）

### 任务 A：抽取纯核心 TaskNotificationTargetUrlResolver（基础，无依赖）
- 新建 `backend/src/main/java/com/xiyu/bid/notification/core/TaskNotificationTargetUrlResolver.java`
- 新建 `backend/src/test/java/com/xiyu/bid/notification/core/TaskNotificationTargetUrlResolverTest.java`
- TDD：先写测试（RED）→ 实现（GREEN）

### 任务 B：改造 ProjectNotificationService（依赖 A）
- 修改 `ProjectNotificationService.resolveTaskAssignedTargetUrl`
- 删除私有方法，改调 `TaskNotificationTargetUrlResolver.resolveTargetUrl`
- 更新现有测试（保持绿）

### 任务 C：修复 TaskReviewNotificationService（依赖 A）⭐ 核心 bug 修复
- 注入 `EffectiveRoleResolver` 和 `UserRepository`
- 修改 `notifyTaskReviewResult`，根据接收人角色决定 targetUrl
- 添加测试用例（bid-otherDept → task-board，其他 → project drafting）

### 任务 D：前端验证（无依赖，可并行）
- 确认 `notificationHelpers.js` 和 `TaskBoardPage.vue` 已正确处理（CO-474 已实现）
- 添加/补充 `notificationHelpers.spec.js` 测试用例（bid-otherDept 审核结果通知跳转 task-board）

### 依赖关系
```
A (纯核心) ──┬── B (改造 ProjectNotificationService)
             └── C (修复 TaskReviewNotificationService) ⭐
D (前端验证) ── 独立并行
```

## 五、风险识别

| 风险 | 影响 | 缓解 |
|---|---|---|
| 改造 ProjectNotificationService 破坏现有测试 | 中 | 改造仅删除私有方法、改调纯核心，行为等价；现有 2 个用例验证不变 |
| TaskReviewNotificationService 注入新依赖可能影响其他测试 | 低 | 仅新增 `EffectiveRoleResolver`，已有 `UserRepository` |
| 纯核心类放置位置可能违反 ArchitectureTest | 中 | 放在 `notification/core/` 包，符合 FP-Java 纯核心约定；改完跑 `ArchitectureTest` |
| 前端 D 的测试可能因 CO-474 已实现而无需新增 | 低 | 任务 D 先验证，若已覆盖则跳过 |

## 六、验收标准

1. `TaskReviewNotificationService.notifyTaskReviewResult` 对 bid-otherDept 执行人返回 `/task-board?taskId=X&projectId=Y`
2. `ProjectNotificationService.notifyTaskAssigned` 行为不变（CO-474 修复保持）
3. `TaskNotificationTargetUrlResolver` 纯核心类 + 单测覆盖
4. `ArchitectureTest` 全绿
5. 前端 `notificationHelpers.spec.js` 覆盖 bid-otherDept 审核结果通知跳转
6. `npm run build` + `mvn test` 全绿
7. 单文件不超过 300 行（FP-Java 约束）

## 七、执行节奏

- **阶段 1 plan**：本文档，等待用户确认
- **阶段 2 tdd**：并行调度 subagent 执行任务 A/B/C/D
- **阶段 3 code-review**：调用 xiyu-code-review skill 审查
- **阶段 4 refactor-clean**：清理死代码、优化结构
