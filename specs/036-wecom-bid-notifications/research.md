# Research Notes: 投标关键节点企微通知

**Date**: 2026-07-12

## 调研范围

- `backend/src/main/java/com/xiyu/bid/tender/` — 标讯领域与立即投标
- `backend/src/main/java/com/xiyu/bid/project/` — 项目阶段推进
- `backend/src/main/java/com/xiyu/bid/notification/` — 站内通知与企微推送链路
- `backend/src/main/java/com/xiyu/bid/wecom/` — 企微发送客户端
- `backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java` — 项目权限

## 关键发现

### 1. "立即投标"在代码中分为两步

| 入口 | 路径 | 行为 |
|---|---|---|
| `TenderController.participateBid` | `POST /api/tenders/{id}/participate` | 仅将 `Tender.Status` 从 `EVALUATED` 改为 `BIDDING`，不创建项目 |
| `TenderEvaluationController.proceedToBid` | `POST /api/tenders/{tenderId}/bid` | 在 `BIDDING` 后创建 `Project` 与 `ProjectInitiationDetails` |

需求中"已评估的标讯，点击立即投标"从用户场景看更接近 `participateBid`；但"给项目负责人发送待立项通知"需要项目对象与项目负责人信息，这些信息在 `proceedToBid` 后才存在。

**决策**：本次实现选择挂载在 `TenderEvaluationService.proceedToBid` 成功后。原因：
- 项目已创建，`ProjectInitiationDetails.ownerUserId`（项目负责人）可被解析；
- 与现有项目维度通知体系（`ProjectNotificationRecipientPolicy`）对齐；
- 若前端实际调用的就是 `proceedToBid`，则无需额外改动。

> 若后续产品确认前端分两步骤调用（先 `participateBid` 再 `proceedToBid`），可将通知点前移到 `participateBid` 之后，并在该处仅基于 `Tender.projectManagerId` 发送。

### 2. 项目阶段推进有统一收口

所有阶段转换最终走 `ProjectStageService.requestTransition(projectId, target, gateInputs, bidResult)`。

`RETROSPECTIVE → CLOSED` 由 `ProjectClosureService.approveClosure` 调用。

**决策**：在 `ProjectStageService.requestTransition` 中检测 `current == RETROSPECTIVE && target == CLOSED`，成功后发送"待结项申请"通知。原因：
- 统一收口，所有阶段推进通知都在此处触发；
- 与需求字面"推进至项目结项阶段"一致。

> 注意："待结项申请"文案与阶段已变为 CLOSED 在语义上略有张力。本次按"操作触发通知"理解，通知提醒项目负责人进入结项申请后续流程；若产品需要改为提交结项申请时（`submitClosure`）发送，可单独调整触发点。

### 3. 通知基础设施可直接复用

现有站内通知创建后会自动镜像到企微：

1. `NotificationApplicationService.createNotification(request, createdBy)` 持久化站内通知；
2. 发布 `NotificationCreatedEvent`；
3. `NotificationDeliveryTaskListener`（`AFTER_COMMIT + REQUIRES_NEW`）写入 `notification_delivery_task`；
4. `NotificationDeliveryJobService` 异步消费，调用 `WeComPushService.push`；
5. `WeComPushService` 解析 `User.employeeNumber`，经 `WecomMessageSender` / `WecomMessageCenterClient` 发送至西域统一消息中心。

**结论**：业务代码无需直接调用企微 API，只需创建站内通知并填充正确的模板变量与 `targetUrl`。

### 4. 项目负责人解析

- 权威来源：`ProjectInitiationDetails.ownerUserId`；
- 已有封装：`ProjectNotificationRecipientPolicy.collectProjectOwner(...)` / `resolveRecipients(projectId, Set.of(PROJECT_OWNER), excludeUserId)`；
- 兜底：若 `ownerUserId` 为空，可回退至 `Project.managerId`。

### 5. 权限校验

- Controller 层：`@PreAuthorize` 做角色/菜单权限粗过滤；
- Service 层：`ProjectAccessScopeService.assertCurrentUserCanAccessProject(projectId)` 做项目数据范围校验；
- 本次通知挂载在已有受保护入口之后，不再额外增加权限逻辑。

### 6. 模板管理

站内通知模板由纯核心 `NotificationMessagePolicy` 的静态工厂方法生成；企微格式由 `WeComMessageFormatter` 统一拼装为 textcard。新增"待立项"与"待结项申请"文案只需在 `NotificationMessagePolicy` 增加工厂方法。

### 7. 幂等/去重

企微投递层通过 `notification_delivery_task.business_key`（`notificationId:recipientUserId:type`）保证同一通知不重复投递；业务层若需"5 分钟内重复操作只发一条"，需要新增基于 `(sourceEntityType, sourceEntityId, actionType)` 的去重检查。本次按 FR-007 实现轻量级幂等守卫。

## 待澄清点（已做决策）

| 问题 | 决策 | 理由 |
|---|---|---|
| "立即投标"对应哪个入口 | `TenderEvaluationService.proceedToBid` 成功后 | 此时项目与项目负责人信息才存在 |
| "待结项申请"何时触发 | `ProjectStageService.requestTransition` 检测到 `RETROSPECTIVE → CLOSED` | 与"推进至项目结项阶段"字面一致，且统一收口 |
| 项目负责人为空如何兜底 | 优先 `ProjectInitiationDetails.ownerUserId`，为空则回退 `Project.managerId` | 保证通知可送达，同时尊重业务定义 |
