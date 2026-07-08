# 文档变更通知修复 — 执行计划

> **任务**：补齐蓝图"消息中心 §系统通知类 序号 5"——文档变更通知
> **分支**：`agent/zcode/document-change-notification`（基于 `origin/main`）
> **类型**：小型修改（1 新方法 + 2 调用点 + 测试）
> **创建**：2026-07-08

## 背景

消息中心差距分析发现：6 种系统通知类型中，**文档变更通知**是唯一一个枚举值存在（`NotificationType.DOCUMENT_CHANGE`）、监听分支存在（`EntityChangedNotificationListener:93-94`），但**零事件源**的"死功能"。

蓝图要求：
- 触发时机：文档变更（上传/修改/删除）
- 通知内容：`【{项目名称}】文档「{文档名称}」被 {操作人} {操作类型}`
- 接收角色：投标管理员、投标组长、投标负责人、投标辅助人员
- 消息渠道：通知中心
- 频率：实时

## 目标

让 `ProjectDocumentWorkflowService` 的 `createProjectDocument`（上传）和 `deleteProjectDocument`（删除）触发 `DOCUMENT_CHANGE` 通知，实时送达项目团队成员。

## 设计决策

### 决策 1：直接调用 ProjectNotificationService，不走 EntityChangedEvent 订阅扇出

**理由**：
- `EntityChangedNotificationListener` 依赖 `subscription` 表——无人订阅则零通知
- 蓝图要求"团队成员实时接收"，不是"订阅者接收"
- 现有同模式通知（`notifyTaskAssigned`、`notifyStageTransition`、`notifyBidReviewSubmitted`）都是直接调用
- `EntityChangedNotificationListener` 的 DOCUMENT 分支保留不动，服务于未来"主动订阅"场景

### 决策 2：接收人 = 项目团队成员

复用 `getProjectTeamMemberIds(projectId)`，与蓝图接收角色对齐，与现有通知一致。**排除操作人自己**。

### 决策 3：operationType 预留"修改"

当前只有上传/删除两种操作（产品功能层面无文档修改 API）。`operationType` 设计为字符串，未来新增修改 API 直接传"修改"，无需改枚举。

### 决策 4：best-effort 失败容忍

通知失败用 try-catch + log.warn，不影响主业务流程（与现有 `sendNotification` 一致）。

## 进度

- [x] 切分支、落计划
- [x] 改动 1：新建 `DocumentChangeNotificationService`（独立类，避免 `ProjectNotificationService` 超 300 行预算）
- [x] 改动 2：`ProjectDocumentWorkflowService` 注入 + 两处调用
- [x] 改动 3：单元测试（6 个新测试 + 3 个集成测试）
- [x] 验证：mvn test 72 通过 0 失败
- [x] 改动 4：README 更新
- [ ] 原子提交

## 决策日志

- 2026-07-08：决定走轻量计划而非 Spec Kit 门禁（属于明确缺口补齐的小型修改，见 PLANS.md §落计划约定）
- 2026-07-08：决定不删 `EntityChangedNotificationListener` 的 DOCUMENT 分支（服务于订阅场景，避免影响潜在语义）
- 2026-07-08：抽独立 `DocumentChangeNotificationService` 而非放 `ProjectNotificationService`（pre-commit line-budget=300 限制；与既有 `TaskReviewNotificationService` 同模式）
