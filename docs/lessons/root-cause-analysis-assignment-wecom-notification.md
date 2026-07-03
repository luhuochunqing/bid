# 根因分析：任务分配后被分配人未收到企微消息

## 问题描述

用户反馈"分配任务后，被分配任务的执行人未收到企微消息"，表现为**特定角色/特定项目才不收**，从 2026-07-02 下午 4 点左右开始出现。

---

## 证据链（按 §23 SOP Layer 2 代码溯源）

### 完整调用链

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ Layer 1: 任务分配入口                                                           │
│                                                                                 │
│ TaskService.createTask() @Transactional                                         │
│   │                                                                             │
│   └─→ notifyAssigneeIfNeeded(task, assignedBy) [TaskService.java:95-99]        │
│         │                                                                       │
│         └─→ ProjectNotificationService.notifyTaskAssigned(projectId, taskId,    │
│              assigneeId, assignedBy) [ProjectNotificationService.java:97-103]   │
│               │                                                                 │
│               └─→ sendTaskAssignedNotification(...) [ProjectNotificationService.java:117-144]
│                    │                                                           │
│                    ├─→ notificationService.createNotification(...)              │
│                    │     ├─→ 返回 DispatchResult（不抛异常，调用方忽略返回值）     │
│                    │     └─→ publishEvent(NotificationCreatedEvent)            │
│                    │                                                           │
│                    └─→ catch (RuntimeException e) { log.warn(...); } ← §25 静默吞错
│                          （即使 createNotification 返回 invalid，也被吞掉）       │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Layer 2: 异步派发（AFTER_COMMIT）                                                │
│                                                                                 │
│ NotificationDeliveryTaskListener.onNotificationCreated()                        │
│   @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)            │
│   @Transactional(propagation = Propagation.REQUIRES_NEW)                        │
│   │                                                                             │
│   └─→ 入库 notification_delivery_task（status=PENDING）                          │
│         （无收件人过滤，全员入队）                                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│ Layer 3: 定时拉取 + 企微推送                                                     │
│                                                                                 │
│ NotificationDeliveryJobService.processDueTasks() @Scheduled(5s)                 │
│   │                                                                             │
│   └─→ processTaskSafely(task)                                                   │
│         │                                                                       │
│         └─→ pushService.push(command) [WeComPushService.java:48-68]            │
│              │                                                                 │
│              ├─→ userRepository.findById(command.recipientUserId())            │
│              │                                                                 │
│              │  ★★★ ROOT CAUSE ★★★                                             │
│              │                                                                 │
│              └─→ [WeComPushService.java:50-52]                                  │
│                    if (userOpt.isEmpty() || isBlank(userOpt.get().getEmployeeNumber())) {
│                        return NotificationDeliveryResult.skip(                   │
│                            "recipient has no employee number");                 │
│                    }                                                           │
│                                                                                 │
│                    ↓                                                           │
│                    ↓                                                           │
│                    ↓ skip() 返回：successful=true, skipped=true                 │
│                                                                                 │
│                    ↓                                                           │
│                    ↓                                                           │
│              └─→ [NotificationDeliveryJobService.java:82-91]                   │
│                    任务标 DELIVERED + OutboundLog(SKIPPED, NOT_BOUND)           │
│                    无 Sentry 上报、无 retry、无告警                               │
│                    企微侧从未收到推送                                            │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 根因分析

### 直接根因

**[WeComPushService.java:50-52](file:///workspace/backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java#L50-L52)** — 当收件人（被分配任务的执行人）的 `user.employee_number`（工号）字段为空时：

```java
if (userOpt.isEmpty() || isBlank(userOpt.get().getEmployeeNumber())) {
    return NotificationDeliveryResult.skip("recipient has no employee number");
}
```

### 导致的后果

| 步骤 | 行为 | 影响 |
|------|------|------|
| 1 | `push()` 返回 `skip(...)` | `successful()=true`, `skipped()=true` |
| 2 | `NotificationDeliveryJobService.handleSuccess()` 处理 | 任务标为 `DELIVERED`（看起来成功） |
| 3 | 记录 `OutboundLog` | `status=SKIPPED`, `skip_reason=NOT_BOUND` |
| 4 | 无 retry、无告警 | 静默死亡，无人发现 |
| 5 | 企微侧无推送 | 用户收不到消息 |

### 间接根因（§25 静默吞错放大了问题）

**[ProjectNotificationService.java:140-143](file:///workspace/backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java#L140-L143)** — `sendTaskAssignedNotification` 使用 `try-catch (RuntimeException)` 静默吞错：

```java
} catch (RuntimeException e) {
    log.warn("sendTaskAssignedNotification failed for project={}, task={}: {}",
            projectId, taskId, e.getMessage());
}
```

问题在于：
1. `NotificationApplicationService.createNotification()` **不抛异常**（返回 `DispatchResult` 值对象），所以 `catch (RuntimeException)` 根本捕获不到业务错误
2. 调用方**完全忽略返回值**，即使派发失败也"成功"返回给前端
3. Sentry 看不到任何异常（§30 教训：业务异常被吞，Sentry 无法检测）

### 与"特定角色/特定项目"特征的对应关系

- **特定角色**：该角色下的用户可能是通过 OSS 同步或手动创建的，`employee_number` 字段未维护
- **特定项目**：该项目的任务被分配给了 `employee_number` 为空的用户
- **时间窗口（昨天下午 4 点）**：可能是某次用户导入/同步时 `employee_number` 字段未同步，或某次数据修复误清空

---

## 影响范围

| 范围 | 描述 |
|------|------|
| 功能 | 任务分配通知的企微推送 |
| 用户群体 | `employee_number` 为空的被分配人 |
| 表现 | 站内通知正常（数据库有记录），但企微无推送 |
| Sentry | 无异常上报（业务"成功"） |
| 日志 | 仅 `OutboundLog` 表有 `SKIPPED` 记录 |

---

## 代码审查发现的问题

### 问题 1：skip 路径无监控（最严重）

**文件**：[NotificationDeliveryJobService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/notification/outbound/application/NotificationDeliveryJobService.java#L82-L91)

```java
if (result.successful()) {
    handleSuccess(managedTask, command, result);
    return;
}
```

`handleSuccess()` 中：
```java
outboundLogRepository.save(OutboundLog.builder()
        .status(result.skipped() ? OutboundStatus.SKIPPED : OutboundStatus.SENT)
        .skipReason(result.skipped() ? SkipReason.NOT_BOUND : null)
        ...
);
```

**问题**：`SKIPPED` + `NOT_BOUND` 被当作"成功"处理，无告警、无监控。

### 问题 2：调用方忽略返回值

**文件**：[ProjectNotificationService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java#L131-L139)

```java
notificationService.createNotification(new CreateNotificationRequest(...),
        assignedBy == null ? SYSTEM_USER_ID : assignedBy);
```

**问题**：`createNotification()` 返回 `DispatchResult`，但调用方完全不检查。

### 问题 3：静默吞错反模式

**文件**：[ProjectNotificationService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java#L140-L143)、[TenderAssignmentNotifier.java](file:///workspace/backend/src/main/java/com/xiyu/bid/tender/service/TenderAssignmentNotifier.java#L54-L57)

```java
} catch (RuntimeException e) {
    log.warn("Failed to send assignment notification...");
}
```

**问题**：按 §25 教训，静默吞错违反"快速失败"原则。

---

## 修复方案

### 方案 A：skip 路径增加 Sentry 上报（最低成本，立即生效）

**修改文件**：[NotificationDeliveryJobService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/notification/outbound/application/NotificationDeliveryJobService.java#L61-L65)

```java
NotificationDeliveryResult result = pushService.push(command);
if (result.successful()) {
    if (result.skipped()) {
        // skip 路径对业务无功能影响，但对用户体验是隐形 bug
        log.warn("WeCom notification skipped: user {} has no employee_number",
                command.recipientUserId());
        // 上报 Sentry，触发告警
        Sentry.captureMessage("WeCom notification skipped: user has no employee_number",
                Level.WARNING);
    }
    handleSuccess(managedTask, command, result);
    return;
}
```

### 方案 B：用户导入/创建时强制要求 employee_number（数据治理层）

在用户创建/导入流程中增加校验，确保 `employee_number` 不为空。这是治本方案，但需要同步修改多个入口。

### 方案 C：通知派发失败时回退到站内通知 + 前端提示（用户体验层）

当企微推送 skip 时，前端显示"通知已发送至站内消息，请登录系统查看"。

---

## 验证清单

### 修复前验证（确认根因）

```bash
# 数据库验证（需生产库访问）
mysql -u ea_bid -p"ra(D7np+Z" winbid -e "
SELECT status, skip_reason, COUNT(*) AS cnt
FROM outbound_log
WHERE create_time >= '2026-07-02 16:00:00'
GROUP BY status, skip_reason
ORDER BY cnt DESC;
"
```

### 修复后验证

```bash
# 1. 单元测试
cd backend && mvn test -Dtest=WeComPushServiceTest

# 2. 端到端验证
# 创建任务给 employee_number 为空的用户，确认 Sentry 收到告警
# 创建任务给 employee_number 正常的用户，确认企微收到消息
```

---

## 经验教训（参照 §23/§25/§30）

| 教训编号 | 教训内容 | 对应问题 |
|----------|----------|----------|
| §23 | 全链路日志排查 SOP：先抓 TraceId/OutboundLog，再看代码 | skip 路径无日志、无 Sentry，导致定位困难 |
| §25 | 前端禁止 catch {} 吞掉 API 错误 | 后端同样禁止 catch {} 吞掉业务错误 |
| §30 | REQUIRES_NEW + try-catch 反模式导致 UnexpectedRollbackException | 本案例虽然没有 REQUIRES_NEW，但 skip 路径的"静默成功"与 §30 的"静默失败"本质相同——都是业务异常被吞 |

---

## 关联文件

| 文件 | 角色 |
|------|------|
| [WeComPushService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/notification/outbound/service/WeComPushService.java) | 企微推送核心，根因命中点 |
| [NotificationDeliveryJobService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/notification/outbound/application/NotificationDeliveryJobService.java) | 异步派发任务处理，静默死亡点 |
| [ProjectNotificationService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/project/notification/ProjectNotificationService.java) | 任务分配通知入口，§25 静默吞错 |
| [TenderAssignmentNotifier.java](file:///workspace/backend/src/main/java/com/xiyu/bid/tender/service/TenderAssignmentNotifier.java) | 标讯分配通知，同款静默吞错 |
| [TaskService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/task/service/TaskService.java) | 任务创建入口 |
| [NotificationApplicationService.java](file:///workspace/backend/src/main/java/com/xiyu/bid/notification/service/NotificationApplicationService.java) | 站内通知创建 |
