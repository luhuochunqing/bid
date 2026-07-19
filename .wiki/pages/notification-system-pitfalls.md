---
title: 通知系统陷阱集
space: engineering
category: guide
tags: [通知, Notification, 企微, WeCom, targetUrl, SYSTEM_USER_ID, AlertNotificationOrchestrator]
sources:
  - backend/src/main/java/com/xiyu/bid/notification/
  - .wiki/pages/integration-wecom.md
backlinks:
  - _index
  - integration-wecom
created: 2026-07-10
updated: 2026-07-10
health_checked: 2026-07-19
---
# 通知系统陷阱集

> 从 8 个工作区历史对话中提取的通知系统实战陷阱。
> 涵盖任务分配通知覆盖、targetUrl 角色感知、系统通知创建者、异常静默吞掉等。

---

## 1. 任务分配通知必须覆盖所有创建/分配路径

### 1.1 事故

任务分配通知在某些场景下未发送，用户不知道自己被分配了任务。

### 1.2 根因

任务创建有多个入口：
- Controller 直接创建
- 批量导入创建
- 项目创建时自动创建子任务
- 任务转移时重新分配

只有第一个入口发送了通知，其他入口漏发。

### 1.3 规范

```java
// ✅ 正确：通知发送集中在 Service 层，所有入口都走同一方法
public class TaskAssignmentService {
    public Task assignTask(Long taskId, Long assigneeId, User assigner) {
        Task task = taskRepository.findById(taskId);
        task.setAssigneeId(assigneeId);
        taskRepository.save(task);
        // 通知发送在 Service 层，所有调用方都会触发
        notificationService.notifyTaskAssigned(task, assigneeId, assigner);
        return task;
    }
}

// Controller
@PostMapping("/tasks")
public Task create(@RequestBody TaskDto dto) {
    return assignmentService.assignTask(...);  // 通知发送
}

// 批量导入
public void importTasks(List<TaskDto> dtos) {
    for (TaskDto dto : dtos) {
        assignmentService.assignTask(...);  // 通知发送
    }
}
```

### 1.4 教训

- **通知发送要集中在 Service 层**，不要在 Controller 层
- **所有创建/分配路径都要走同一 Service 方法**，确保通知不漏发
- **新增入口时检查是否走了通知发送路径**

---

## 2. 通知 targetUrl 需角色感知

### 2.1 事故

投标专员点击通知跳转到 `/tasks/123`，但该 URL 是项目负责人视角，投标专员看到 403。

### 2.2 根因

targetUrl 硬编码为 `/tasks/{taskId}`，没有根据接收人角色生成不同的 URL。

### 2.3 正确做法

```java
public String buildTargetUrl(Task task, Long recipientId) {
    String roleCode = effectiveRoleResolver.resolveRoleCode(userRepository.findById(recipientId));

    return switch (roleCode) {
        case "bid-Team" -> "/tasks/" + task.getId() + "?view=assignee";
        case "bid-TeamLeader" -> "/tasks/" + task.getId() + "?view=team";
        case "bid-projectLeader" -> "/projects/" + task.getProjectId() + "/tasks/" + task.getId();
        default -> "/tasks/" + task.getId();
    };
}
```

### 2.4 教训

- **targetUrl 必须根据接收人角色生成**
- **不同角色看到的页面视角不同**（assignee vs team vs project）
- **通知发送时要解析接收人角色**

---

## 3. 系统生成通知 createdBy 必须设为 SYSTEM_USER_ID(0L)

### 3.1 事故

系统自动生成的通知（如证书过期提醒）的 createdBy 字段为 NULL，导致前端显示"未知用户"。

### 3.2 根因

通知实体 `createdBy` 是 `Long` 类型，NULL 时前端无法显示用户名。

### 3.3 规范

```java
public class NotificationService {
    private static final Long SYSTEM_USER_ID = 0L;

    public void sendSystemNotification(NotificationDto dto) {
        Notification notification = new Notification();
        notification.setCreatedBy(SYSTEM_USER_ID);  // 必须设为 0L
        notification.setTitle(dto.getTitle());
        // ...
        notificationRepository.save(notification);
    }
}
```

### 3.4 教训

- **系统生成的通知 createdBy 必须设为 `SYSTEM_USER_ID = 0L`**
- **不要用 NULL**，前端无法显示
- **前端对 createdBy=0 的通知显示"系统"**

---

## 4. AlertNotificationOrchestrator catch RuntimeException 静默吞掉

### 4.1 事故

告警通知发送失败时，异常被 `catch (RuntimeException e)` 静默吞掉，运维不知道通知系统挂了。

### 4.2 反模式

```java
// ❌ 错误：catch 后只 log，不抛
public class AlertNotificationOrchestrator {
    public void sendAlert(Alert alert) {
        try {
            notificationChannel.send(alert);
        } catch (RuntimeException e) {
            log.error("告警通知发送失败", e);
            // 静默吞掉，运维不知道
        }
    }
}
```

### 4.3 正确做法

```java
// ✅ 正确：记录失败 + 抛出 + 降级
public class AlertNotificationOrchestrator {
    public void sendAlert(Alert alert) {
        try {
            notificationChannel.send(alert);
        } catch (RuntimeException e) {
            log.error("告警通知发送失败: alertId={}", alert.getId(), e);
            // 1. 记录到失败表，便于重试
            failedNotificationRepository.save(new FailedNotification(alert, e.getMessage()));
            // 2. 不再抛出（避免阻断主流程），但要确保可观测
            metricsService.increment("notification.failed");
        }
    }
}
```

### 4.4 教训

- **catch RuntimeException 后不要静默吞掉**
- **至少要做三件事**：记录日志、写入失败表、上报指标
- **通知失败不能阻断主流程**，但要可观测

---

## 5. 证书过期提醒的接收人规则

### 5.1 规则

- 证书过期提醒发送给 `bid_admin` 和 `bid_lead` 角色
- **不发给证书持有者本人**
- 提醒阈值：30 天（`DEFAULT_WARNING_DAYS=30`）
- 永久有效证书（`expiry_date IS NULL`）不监控
- 离职人员（`enabled=false`）的证书立即停止提醒

### 5.2 去重规则

- 同一证书的通知在 24 小时内去重
- 避免重复提醒打扰接收人

### 5.3 教训

- **提醒通知要发给管理者**，不是执行人
- **去重逻辑要考虑时间窗口**，避免重复打扰
- **离职人员的通知要立即停止**

---

## 6. 通知渠道：站内信 + 企微

### 6.1 双渠道

通知通过两种渠道发送：
- **站内信**：写入 `notifications` 表，前端轮询
- **企业微信**：调企微 webhook 推送

### 6.2 陷阱

- 企微 webhook 失败不能阻断站内信
- 企微消息格式要简洁（text 格式，不用 markdown）
- 企微 @人 要用手机号，不是用户名

### 6.3 教训

- **通知渠道要解耦**，一个失败不影响另一个
- **企微消息要简洁**，避免长文本
- **详见 [[integration-wecom]]**

---

## 7. @TransactionalEventListener 静默失效

### 7.1 事故

`@TransactionalEventListener` 标注的通知发送方法在某些场景下不触发，用户收不到通知。

### 7.2 根因

- `@TransactionalEventListener` 默认在事务**提交后**触发
- 如果事务回滚，事件不会触发
- 如果事件发布时不在事务中，事件也会丢失

### 7.3 解决

```java
// 方案 1：确保事件在事务中发布
@Transactional
public void assignTask(...) {
    // 业务操作
    taskRepository.save(task);
    // 事件发布必须在事务内
    eventPublisher.publishEvent(new TaskAssignedEvent(task));
}

// 方案 2：用 @EventListener + 编程式事务
@EventListener
public void onTaskAssigned(TaskAssignedEvent event) {
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.execute(status -> {
        notificationService.send(event);
        return null;
    });
}
```

### 7.4 教训

- **@TransactionalEventListener 依赖事务上下文**
- **事件发布必须在事务内**
- **事务回滚时事件不会触发**，需要时改用 `@EventListener`
- **详见 [[lessons-learned]] §六（PR !820 案例）**

---

## 8. 相关文档

- [[integration-wecom]] — 企微集成规范
- [[lessons-learned]] §六 — @TransactionalEventListener 静默失效案例
- [[spring-pitfalls]] §1 — @Transactional REQUIRES_NEW + try-catch 反模式
- `backend/src/main/java/com/xiyu/bid/notification/` — 通知系统源码

---

## 9. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取通知系统陷阱 |
