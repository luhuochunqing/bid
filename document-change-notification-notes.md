# 实现笔记 — 文档变更通知修复

> 任务：补齐蓝图"消息中心 §系统通知类 序号 5"
> 分支：`agent/zcode/document-change-notification`
> 起始：2026-07-08

## 决策记录

### 1. 为什么不走 EntityChangedEvent 订阅扇出？

**蓝图原文**："文档变更 → 投标管理员/投标组长/投标负责人/投标辅助人员 → 通知中心 → 实时"。

现有 `EntityChangedNotificationListener` 的 DOCUMENT_CHANGE 分支依赖 `subscription` 表：
- 需要用户**主动订阅**该文档
- 无人订阅 → 零通知
- 与蓝图"团队成员实时接收"不符

而现有 6 种通知类型中其他 5 种（任务分配、阶段推进、@提及、结项归档、任务审核）**全部直接调用** `NotificationApplicationService`，不走订阅扇出。为保持代码一致性，本次也走直接调用。

**保留** `EntityChangedNotificationListener` 的 DOCUMENT 分支不动——它服务于未来"用户主动订阅某文档"场景，本次修复不影响它。

### 2. 接收人解析策略

蓝图列了 4 个角色（投标管理员/组长/负责人/辅助人员），但代码中：
- 这些角色散落在 `RoleProfileCatalog`（admin/bidAdmin/bidLead）和 `project_member` 表
- `notifyStageTransition`、`notifyAbandonBid` 等现有通知用 `getProjectTeamMemberIds(projectId)`（`project_member` 全员）
- 项目团队成员 ≈ 蓝图的 4 个角色（项目成员都是这些角色的人）

**决定**：复用 `getProjectTeamMemberIds(projectId)`，与现有通知完全一致，避免引入新的接收人解析逻辑。**排除操作人自己**（避免自己上传文档还收到通知，与 `EntityChangedNotificationListener:49` 的过滤逻辑一致）。

### 3. operationType 用字符串而非枚举

蓝图列了"上传/修改/删除"三种操作。代码中只有上传（`createProjectDocument`）和删除（`deleteProjectDocument`），**无修改 API**（修改=删除+重传，这是产品功能层面的缺口，不在通知修复范围）。

**权衡**：
- 方案 A：新建 `DocumentChangeType` 枚举（UPLOAD/MODIFY/DELETE）
- 方案 B：用字符串字面量

选 B：当前只有两个调用点，枚举有过度设计之嫌；未来新增修改 API 时，重构为枚举的成本极低。这是 YAGNI 原则的体现。

### 4. 删除通知时机：delete 之前通知

`deleteProjectDocument` 中，通知调用放在 `repository.delete(document)` **之前**：
- 通知用到 `document.getName()`、`document.getId()`，删除前实体信息完整
- best-effort try-catch 保证通知失败不阻断删除
- 语义上"先通知后删"与"先删后通知"在事务提交后才能被其他事务看到，无实际差异

### 5. 抽出独立 DocumentChangeNotificationService（而非放 ProjectNotificationService）

**根因**：pre-commit hook 的 `line-budget` 检查限制单文件 300 行。`ProjectNotificationService` 原 293 行，加入 `notifyDocumentChanged` 后 350 行，超限。

**权衡**：
- 方案 A：精简注释/空行让 `ProjectNotificationService` 容纳新方法
- 方案 B：抽出独立 `DocumentChangeNotificationService`（与既有 `TaskReviewNotificationService` 同模式）

选 B：
- 项目已有先例（`TaskReviewNotificationService` 就是独立的通知器类）
- 文档变更通知的职责单一，独立类更清晰
- 避免 `ProjectNotificationService` 持续膨胀（13 个方法已经不小）
- 与单一职责原则一致

## 规范外的事项

### 范围之外（明确不修复）

| 项 | 原因 |
|---|---|
| 文档修改 API 缺失 | 产品功能缺口，通知层只预留 operationType |
| 任务状态变更 assignee 无条件必达 | 下一个 PR（用户已确认方案） |
| 结项归档通知文案补全 | 中优先级，下一个 PR |
| 频率标记/通知方式/模板体系抽象 | 架构性优化，低优先级 |
| `EntityChangedNotificationListener` DOCUMENT 分支 | 保留不动 |

## 遇到的坑

（实施过程中遇到再补充）

## 改动清单

1. `DocumentChangeNotificationService.java`（新建）— 文档变更通知独立服务，含 `notifyDocumentChanged` 方法
2. `ProjectDocumentWorkflowService.java` — 注入 + 上传/删除两处调用
3. `DocumentChangeNotificationServiceTest.java`（新建）— 6 个分支测试
4. `ProjectDocumentWorkflowServiceTest.java` — setUp mock + 3 个新测试
5. `ProjectWorkflowServiceTest.java` — 构造器参数适配
6. 模块 README 更新（notification + projectworkflow）
