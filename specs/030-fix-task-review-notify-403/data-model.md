# Phase 1 Data Model: 修复任务审核通知接收人广播 403

**Feature**: 030-fix-task-review-notify-403
**Date**: 2026-07-06

## 概述

本期**不涉及数据库 schema 变更**（无新增表、无新增列、无 Flyway 迁移）。仅复用既有 `notification` / `user_notification` 两张表，以及既有 `ProjectAccessScopeService` 计算的可访问项目集合。

本文档记录涉及的实体关系，便于编码阶段参考。

---

## 实体关系（既有，本期复用）

### 1. Notification（通知主表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 通知主键 |
| `type` | VARCHAR | `NotificationType` 枚举名（本期涉及 `TASK_UPDATE`） |
| `source_entity_type` | VARCHAR | "PROJECT" |
| `source_entity_id` | BIGINT | 项目 ID |
| `title` | VARCHAR | 通知标题 |
| `body` | TEXT | 通知正文 |
| `payload_json` | JSON | **包含 `targetUrl` 字段**，本期修复会改变 targetUrl 生成逻辑 |
| `created_by` | BIGINT | 创建者 user_id |
| `created_at` | DATETIME | 创建时间 |

### 2. UserNotification（用户-通知关联表）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT PK | 主键 |
| `notification_id` | BIGINT FK | 指向 notification.id |
| `user_id` | BIGINT | **接收人 user_id（本期修复会按可见性过滤这个集合）** |
| `read_at` | DATETIME | 已读时间，null 表示未读 |
| `created_at` | DATETIME | 创建时间 |

**一对多关系**：一条 `notification` 对应多条 `user_notification`（一个通知派发给多个接收人）。本期修复在派发前**缩小 user_id 集合**。

---

## 新增/修改的"逻辑实体"（非 DB 表）

### 3. NotificationRecipientFilter（新增，Pure Core 纯函数）

| 属性 | 说明 |
|---|---|
| **位置** | `backend/src/main/java/com/xiyu/bid/notification/core/NotificationRecipientFilter.java` |
| **类型** | 无状态纯函数（`final` class，私有构造，静态方法） |
| **依赖** | 仅依赖 JDK（`java.util.function.Predicate`、`java.util.Collection`） |
| **不依赖** | Spring、Repository、任何 IO |

**核心方法签名**（详细契约见 `contracts/notification-filter-api.md`）：

```java
public static List<Long> filterRecipients(
    Collection<Long> candidateUserIds,
    Predicate<Long> canAccessProject
)
```

### 4. ProjectAccessScopeService.canAccessProject（新增方法，Imperative Shell）

| 属性 | 说明 |
|---|---|
| **位置** | `backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java` |
| **类型** | 实例方法（`@Transactional(readOnly = true)`） |
| **依赖** | 复用现有 `getAllowedProjectIds(user)` 逻辑 + `EffectiveRoleResolver` |

**核心方法签名**：

```java
public boolean canAccessProject(Long userId, Long projectId)
```

---

## 数据流

```
任务审核提交 → TaskReviewNotificationService.notifyTaskReviewSubmitted(projectId, taskId, ...)
                  │
                  ▼
              ① 候选接收人：userRepository.findEnabledByRoleProfileCodes(TASK_MUTATION_ALLOWED_ROLES)
                  │  （12-20 个候选，包括 admin/bid-Team/bid-projectLeader）
                  ▼
              ② 过滤：NotificationRecipientFilter.filterRecipients(
                  candidates,
                  uid -> projectAccessScopeService.canAccessProject(uid, projectId)  ← Predicate
              )
                  │  （剔除对该项目无访问权的接收人）
                  ▼
              ③ 派发：notificationService.createNotification(...)
                  │  （为过滤后的接收人批量创建 user_notification 记录）
                  ▼
              ④ payload_json.targetUrl = "/project/{projectId}/drafting"
                  （不变；前端兜底降级保证无权用户即使收到也不会卡在 403）
```

---

## 不涉及的数据

- **历史脏数据清理**：已派发给 06131 的历史"无权访问项目"通知不在本期范围（spec Assumption 声明）。后续可单独执行清理 SQL：`DELETE FROM user_notification WHERE user_id=1471 AND notification_id IN (无权访问项目的通知)`。
- **存量通知的 targetUrl 改写**：历史通知的 payload_json 不回填，本期只改增量派发逻辑。
