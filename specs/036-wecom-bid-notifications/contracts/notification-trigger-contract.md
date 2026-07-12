# Internal Contract: 投标关键节点通知触发

## 1. Tender → Project 立项通知

### Trigger

`TenderEvaluationService.proceedToBid(Long tenderId, Long adminId)` 成功创建 `Project` 之后。

### Input

```java
record PendingInitiationNotificationCommand(
    Long tenderId,
    Long projectId,
    String tenderName,
    Long projectOwnerUserId,
    Long triggeredByUserId
) {}
```

### Precondition

- `Tender.status == BIDDING`（已在 `proceedToBid` 流程中保证）；
- `Project` 与 `ProjectInitiationDetails` 已持久化；
- 当前用户具备 `bidding.manage` 权限（Controller 层已校验）。

### Output

- 向 `PROJECT_OWNER` 创建一条站内通知，类型为 `PENDING_INITIATION`；
- 通知自动镜像为企微 textcard；
- 返回 `NotificationResult`（成功/无接收人/去重跳过）。

### Failure Handling

- 通知创建失败不得影响 `proceedToBid` 主事务；
- 通过 `AFTER_COMMIT` 事件 + 独立投递任务保证异步发送；
- 失败记录在 `OutboundLog` / `notification_delivery_task` 中。

## 2. Project 阶段推进 → 结项申请通知

### Trigger

`ProjectStageService.requestTransition(Long projectId, ProjectStage target, GateInputs gateInputs, String bidResult)` 成功将阶段从 `RETROSPECTIVE` 推进至 `CLOSED` 之后。

### Input

```java
record PendingClosureApplicationNotificationCommand(
    Long projectId,
    String projectName,
    Long projectOwnerUserId,
    Long triggeredByUserId,
    ProjectStage previousStage,
    ProjectStage targetStage
) {}
```

### Precondition

- `previousStage == RETROSPECTIVE && targetStage == CLOSED`；
- 阶段转换已通过 `ProjectStageTransitionPolicy.decide(...)` 校验；
- 当前用户具备结项审核权限（Controller 层已校验）。

### Output

- 向 `PROJECT_OWNER` 创建一条站内通知，类型为 `PENDING_CLOSURE_APPLICATION`；
- 通知自动镜像为企微 textcard；
- 返回 `NotificationResult`。

### Failure Handling

- 同立项通知，失败不得阻塞主流程。

## 3. 去重契约

### Key Format

`(sourceEntityType, sourceEntityId, actionType)`

### Window

5 分钟内同一 key 只允许创建一次通知。

### Implementation Note

- 去重查询在各自 Application Service 调用通知创建前执行；
- 使用现有 `NotificationRepository` 按 `(sourceEntityType, sourceEntityId, type, createdAt >= windowStart)` 查询；
- 若记录已存在，直接返回 `DUPLICATED`，不创建新通知。
