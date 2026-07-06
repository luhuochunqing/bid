# Contract: NotificationRecipientFilter API

**Feature**: 030-fix-task-review-notify-403
**Date**: 2026-07-06
**Type**: Java 纯函数契约（Pure Core，无 IO）

## 概述

`NotificationRecipientFilter` 是无状态纯函数类，用于在通知派发前**按"接收人对该资源是否有访问权"过滤候选接收人集合**。

设计对齐 Constitution I (FP-Java) 和既有 `TaskNotificationTargetUrlResolver` 风格：
- `final` class，私有构造
- 仅静态方法
- 参数显式传入，无 Spring 依赖
- 可在单元测试中直接验证

---

## 方法契约

### `filterRecipients`

**签名**:

```java
public static List<Long> filterRecipients(
    Collection<Long> candidateUserIds,
    Predicate<Long> canAccessProject
)
```

**参数**:

| 参数 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `candidateUserIds` | `Collection<Long>` | 可为 null 或空 | 候选接收人 user_id 集合（来自 `findEnabledByRoleProfileCodes` 反查） |
| `canAccessProject` | `Predicate<Long>` | 不可为 null | 接收人可访问性判定函数：输入 user_id，返回 true 表示该用户可访问目标资源。**调用方负责提供此函数的实际实现**（通常为 `uid -> projectAccessScopeService.canAccessProject(uid, projectId)`） |

**返回值**:

- `List<Long>`：过滤后的接收人 user_id 列表（保留候选集合顺序）
- 输入为 null 或空 → 返回空列表 `List.of()`
- 输入非空但全部被过滤 → 返回空列表 `List.of()`
- `canAccessProject` 为 null → 抛 `NullPointerException`（让调用方编程错误早暴露，不静默吞错）

**行为契约**:

1. **保留顺序**：输出列表保留输入候选集合的迭代顺序（不重新排序）
2. **去重**：候选集合若含重复 user_id，输出仅保留首次出现（防御性，理论上候选不会重复）
3. **null 元素**：候选集合中的 null 元素被跳过（不抛异常，不传给 predicate）
4. **异常传播**：`canAccessProject.test(uid)` 抛出的异常会向上传播（不在纯函数内吞错；调用方在 Service 层的 try-catch 兜底）

**线程安全**: 无状态，天然线程安全。

---

## 调用方契约（Imperative Shell 侧）

### `ProjectAccessScopeService.canAccessProject`

**签名**:

```java
@Transactional(readOnly = true)
public boolean canAccessProject(Long userId, Long projectId)
```

**行为**:

| 输入 | 输出 |
|---|---|
| `userId` 或 `projectId` 为 null | `false`（防御性） |
| 用户是 admin（`effectiveRoleResolver.resolveRoleCode(user)` 返回 `admin`） | `true`（短路，避免全量计算） |
| 用户 dataScope=all | `true`（短路） |
| `getAllowedProjectIds(user).contains(projectId)` | 该表达式的值 |
| 用户不存在（已禁用/已删除） | `false` |
| 内部异常 | 抛 RuntimeException（让上层 try-catch 兜底降级为"不过滤"） |

**性能**:

- admin/dataScope=all 路径 O(1)
- 其他路径复用 `getAllowedProjectIds(user)`（含 8+ SQL，约 5-15ms）
- 不缓存（避免与 OSS 角色同步产生不一致）

---

## 调用方契约（Service 接线）

### `TaskReviewNotificationService.notifyTaskReviewSubmitted`

**修改后伪代码**:

```java
public void notifyTaskReviewSubmitted(Long projectId, Long taskId, String taskTitle,
                                       String submitterName, Long submittedBy) {
    Project project = projectRepository.findById(projectId).orElse(null);
    if (project == null) return;
    List<Long> candidates = getTaskReviewerUserIds(submittedBy);
    if (candidates.isEmpty()) return;

    // ★ 新增：按项目可见性过滤候选接收人
    List<Long> recipients = filterRecipientsSafe(candidates, projectId);

    if (recipients.isEmpty()) {
        log.info("TaskReview notification skipped - no accessible recipients for project {}", projectId);
        return;
    }

    // 其余 payload 构造、send 不变
    send(projectId, project.getName(), taskId, ...,
         recipients, submittedBy, "/project/" + projectId + "/drafting");
}

/** 过滤包装：失败时降级为"不过滤"（保留原广播行为），优先保证通知送达 */
private List<Long> filterRecipientsSafe(List<Long> candidates, Long projectId) {
    try {
        return NotificationRecipientFilter.filterRecipients(
            candidates,
            uid -> projectAccessScopeService.canAccessProject(uid, projectId)
        );
    } catch (RuntimeException e) {
        log.warn("Recipient filter failed for project {}, falling back to unfiltered broadcast: {}",
                 projectId, e.getMessage());
        return candidates;
    }
}
```

**关键取舍**（与 spec FR-001 有张力，已在 research.md 决策 5 说明）:
- 过滤逻辑崩了 → 降级为原广播行为（不阻断通知派发）
- 优先保证通知送达而非精准，符合 Constitution VII §2 "装饰性 enrichment MUST 降级"精神

---

## 单元测试契约

`NotificationRecipientFilterTest`（纯函数，无需 Spring）必须覆盖：

| 用例 | 输入 | 期望输出 |
|---|---|---|
| null 候选集合 | `filterRecipients(null, predicate)` | `[]` |
| 空候选集合 | `filterRecipients([], predicate)` | `[]` |
| 全部通过 | `filterRecipients([1,2,3], uid -> true)` | `[1,2,3]` |
| 全部被过滤 | `filterRecipients([1,2,3], uid -> false)` | `[]` |
| 部分过滤（保留偶数） | `filterRecipients([1,2,3,4], uid -> uid%2==0)` | `[2,4]` |
| null 元素跳过 | `filterRecipients([1,null,3], uid -> true)` | `[1,3]` |
| 去重 | `filterRecipients([1,2,1,3], uid -> true)` | `[1,2,3]` |
| 顺序保留 | `filterRecipients([3,1,2], uid -> true)` | `[3,1,2]` |
| predicate 为 null | `filterRecipients([1], null)` | 抛 `NullPointerException` |
| predicate 抛异常 | `filterRecipients([1], uid -> {throw new RuntimeException("db down");})` | 抛 RuntimeException（不吞错） |

`TaskReviewNotificationServiceTest` 必须补充：

| 用例 | 期望 |
|---|---|
| bid-Team 用户被广播到无权项目 | 不在最终接收人列表 |
| admin 用户始终通过 | 在最终接收人列表 |
| 所有候选被过滤掉 | 不调用 `notificationService.createNotification`，打 INFO 日志 |
| ProjectAccessScopeService 抛异常 | 降级为原候选列表广播，打 WARN 日志 |
