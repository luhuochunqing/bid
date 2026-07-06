# 根因分析：任务审核通知接收人广播导致 06131 跳转 403（spec 030）

> **报告日期**：2026-07-06
> **影响范围**：所有 `bid-Team` / `bid-projectLeader` 用户被广播到无权访问项目的任务审核通知时复现
> **修复分支**：`agent/zcode/fix-task-review-notify-403`
> **修复 commits**：`154eeba0d` / `8527766c0` / `e63ef8043` / `008ff2679`
> **排查方法**：§23 全链路日志排查 SOP（Layer 2 主场 + Layer 3 辅助）

## 1. 问题描述

用户 06131（王晓莉，bid-Team 投标专员）反馈：收到大量通知，点击跳转都报错"没有权限"。

## 2. 全链路证据链

### 2.1 服务器日志现场（§23 Layer 2 操作规范 1-2）

服务器 `/var/log/xiyu-bid/application.json.log`，按 06131 + GlobalExceptionHandler 检索：

```
2026-07-06T17:55:57  WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/172, User: 06131, Message: 权限不足，无法访问该项目  traceId=34858d95640b41aea0b0cd2c4cc53d2d
2026-07-06T17:56:53  WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/171, User: 06131, ...                                            traceId=6303175138734199953f2a750e11a0fb
2026-07-06T17:57:06  WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/162, User: 06131, ...                                            traceId=dbe0f91fc1d44b0484970e033d8cbc00
2026-07-06T17:59:50  WARN GlobalExceptionHandler: 权限不足 - URI: /api/projects/172, User: 06131, ...                                            traceId=389af17d926a4189a64b1469e4f20a8a
```

`UserDetailsServiceImpl` 同时段打印 06131 真实角色：
```
user=06131 isOssUser=true roleCode=bid-Team skipLegacyCompat=true
authorities=[bid-Team, ROLE_BID_TEAM, task.view.own, task.handle.own, ...]
```

### 2.2 DB 直查证据（§23 Layer 2 操作规范 3 — 真实通知 payload）

DB：`winbid-01.test.rds.ehsy.com:3306 / winbid / ea_bid`

**用户 06131 身份**：
```
SELECT id, username, full_name, role, role_id FROM users WHERE username='06131';
-- 结果：1471 | 06131 | 王晓莉 | MANAGER | 6
-- 注：role=MANAGER 是 OSS 同步脏数据；role_id=6 → roles.code='bid-Team'
-- UserDetailsServiceImpl 正确按 role_id 解析（日志显示 roleCode=bid-Team）
```

**06131 收到的 TASK_UPDATE 通知 payload**：
```
notification.id=4393 source_entity_id=173 payload_json={"projectName":"欢乐谷","projectId":"173","targetUrl":"/project/173/drafting","taskId":"2973"}
notification.id=4368 source_entity_id=172 payload_json={"projectName":"西安地铁","projectId":"172","targetUrl":"/project/172/drafting","taskId":"2967"}
notification.id=4367 source_entity_id=172 payload_json={"projectName":"西安地铁","projectId":"172","targetUrl":"/project/172/drafting","taskId":"2970"}
```

**06131 在这些项目的可访问来源全部不命中**：
```sql
SELECT id FROM tasks WHERE assignee_id=1471 AND project_id IN (160,161,162,163,164,171,172,173);
-- 结果：空集（06131 不是任何这些项目的 assignee）
```

`ProjectAccessScopeService.getAllowedProjectIds(06131)` 算出的可访问项目集**不含 162/171/172/173** → 跳转必然 403。

**通知接收人列表（每条通知 12 个接收人，全局广播）**：
```
user_notification(notification_id=4359):
  user_id ∈ {1, 493, 587, 1043, 1471(06131), 2556, 7220, 7246, 7396, 8426, 8556, 8578}
```

**任务真实 assignee**（不是 06131）：
```
task 2967 → assignee 585
task 2970 → assignee 7220
task 2973 → assignee 7220
task 2974 → assignee 5052
```

### 2.3 git 追溯（§23 Layer 3）

```
commit c8446b0ea  2026-07-03 10:45  Test User
feat: 任务审核通知 - 提交审核和驳回时发送小铃铛通知
  - 新增 TaskReviewNotificationService
  - 通知接收人策略：基于 RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES
```

**判定**：不是回归，是原设计缺陷。该 commit 从一开始就用 `findEnabledByRoleProfileCodes` 广播式群发，从未对接收人做"项目可见性"过滤。

CO-474（commit `0a1dd4a94`）后续修复了 `notifyTaskReviewResult`（审核结果通知→当事人）的 targetUrl 跳转，但**完全没碰 `notifyTaskReviewSubmitted`（提交审核→群发审核人）的接收人策略**。

## 3. 根因代码定位

### 3.1 广播式接收人（`TaskReviewNotificationService.java:79-86`）

```java
private List<Long> getTaskReviewerUserIds(Long excludedUserId) {
    List<Long> ids = userRepository.findEnabledByRoleProfileCodes(
            RoleProfileCatalog.TASK_MUTATION_ALLOWED_ROLES  // {admin, /bidAdmin, bid-projectLeader, bid-Team}
    ).stream().map(User::getId).toList();                    // ← 全球广播，无项目维度过滤
    if (excludedUserId != null) {
        ids = ids.stream().filter(id -> !id.equals(excludedUserId)).toList();
    }
    return ids;
}
```

### 3.2 硬编码 targetUrl（`TaskReviewNotificationService.java:50-52`）

```java
send(projectId, project.getName(), taskId,
    "任务审核通知 - " + project.getName() + " - " + safeTitle, body,
    reviewerIds, submittedBy, "/project/" + projectId + "/drafting");  // ← 硬编码
```

### 3.3 权限闸门（`ProjectAccessScopeService.java:159-170`）

```java
public void assertCurrentUserCanAccessProject(Long projectId) {
    ...
    if (!new LinkedHashSet<>(getAllowedProjectIds(user)).contains(projectId)) {
        throw new AccessDeniedException("权限不足，无法访问该项目");
    }
}
```

`getAllowedProjectIds(06131)` 不含 162/171/172/173 → 403。

### 3.4 前端 403 全局拦截器（`src/api/client.js:188-189`）

```javascript
case 403:
    ElMessage.error(serverMsg || '没有操作权限，请联系管理员')  // ← 红色 toast，用户看到的"没有权限"
    break
```

## 4. 三层根因总结

| 层级 | 表象 | 真相 |
|------|------|------|
| L1（表层） | 06131 点击通知跳转 403 | 后端 `ProjectAccessScopeService` 抛 `AccessDeniedException`，前端 `client.js:188` 全局 403 拦截器弹红色 toast |
| L2（中层） | 06131 不该收到这些项目通知 | `notifyTaskReviewSubmitted` 用 `findEnabledByRoleProfileCodes` 广播给所有投标专员/负责人，未过滤接收人对项目的访问权 |
| L3（根因） | 接收人策略与资源访问权脱节 + targetUrl 硬编码 | 通知派发的"接收人范围"、"资源访问权"、"跳转 URL"三者各自独立设计，无约束关系 |

## 5. 修复方案（三层防御，spec 030 实施）

### L1 后端核心修复（commit `8527766c0`）

新增 Pure Core 纯函数 `NotificationRecipientFilter` + Service 接线：

```java
// 新增纯函数（notification/core/NotificationRecipientFilter.java）
public static List<Long> filterRecipients(
        Collection<Long> candidateUserIds,
        Predicate<Long> canAccessProject) {
    // null/空集合→空列表；null 元素跳过；LinkedHashSet 去重保序；
    // predicate 为 null→NPE；predicate 抛异常→透传
}

// 新增轻量方法（service/ProjectAccessScopeService.java）
public boolean canAccessProject(Long userId, Long projectId) {
    if (userId == null || projectId == null) return false;
    User user = userRepository.findById(userId).orElse(null);
    if (user == null) return false;
    String roleCode = effectiveRoleResolver.resolveRoleCode(user);
    if (ADMIN_CODE.equalsIgnoreCase(roleCode)) return true;        // admin 短路
    if ("all".equals(getAccessProfile(user).getDataScope())) return true;  // dataScope=all 短路
    return getAllowedProjectIds(user).contains(projectId);
}

// Service 接线（project/notification/TaskReviewNotificationService.java）
private List<Long> filterRecipientsSafe(List<Long> candidates, Long projectId) {
    try {
        return NotificationRecipientFilter.filterRecipients(candidates,
                uid -> projectAccessScopeService.canAccessProject(uid, projectId));
    } catch (RuntimeException e) {
        log.warn("Recipient filter failed, falling back to unfiltered broadcast: {}", e.getMessage());
        return candidates;  // 降级：通知送达优先于精准
    }
}
```

### L2 前端兜底（commit `e63ef8043`）

`src/api/client.js` 全局 403 拦截器精准识别 + 友好降级：

```javascript
case 403: {
    const isProjectDetailAccess = /^\/api\/projects\/\d+(?:\/|$|\?)/.test(config?.url || '')
        && String(config?.method || 'get').toLowerCase() === 'get'
    if (isProjectDetailAccess) {
        ElMessage.warning('您没有该项目的访问权限，已为您返回通知中心')
        setTimeout(() => router.push('/inbox'), 2500)  // 异步跳转，不阻塞 reject
    } else {
        ElMessage.error(serverMsg || '没有操作权限，请联系管理员')  // 其他 403 保持原样
    }
}
```

### L3 教训沉淀

- `docs/exec-plans/tech-debt-tracker.md` 登记 11 处 `findEnabledByRoleProfileCodes` 审视清单（commit `008ff2679`）
- `docs/lessons/lessons-learned.md` §44 沉淀设计教训 + 检查清单
- 本文件（RCA）归档完整证据链

## 6. 测试证据

| 测试 | 用例数 | 结果 |
|---|---|---|
| `NotificationRecipientFilterTest` | 10 | ✅ 全绿（0.086s）|
| `TaskReviewNotificationServiceTest` | 17（含新增 4 spec030 用例） | ✅ 全绿 |
| 相关 62 测试（ProjectTaskWorkflow/ProjectNotification 等）| 62 | ✅ 全绿 |
| checkstyle + pmd | — | ✅ 全绿 |

## 7. 防复发机制

1. **代码层**：`NotificationRecipientFilter` 是 Pure Core 纯函数，复用门槛低，新增通知派发器容易采纳
2. **审视层**：tech-debt-tracker.md 已登记全仓 11 处调用点的判定结论，未来可快速复审
3. **设计层**：lessons §44 沉淀"广播范围 × 资源权限 × targetUrl 三者联动"设计教训 + 新增通知派发器的 6 项检查清单
4. **前端层**：client.js 全局 403 友好降级，即便后端过滤漏网用户体验也不崩坏

## 8. SOP 取舍说明（给后续 Agent）

| §23 Layer | 是否适用 | 原因 |
|---|---|---|
| Layer 1 Sentry | ❌ 不适用 | `AccessDeniedException` 属 `NON_CRITICAL_EXCEPTIONS`，不上报 Sentry（§23 明确说明）|
| Layer 2 日志+TraceId | ✅ 主场 | 业务/权限校验问题主场，GlobalExceptionHandler 现场日志 + DB payload 直查定位根因 |
| Layer 3 git 追溯 | ✅ 辅助 | 判定是回归还是原设计缺陷 → 结论：原设计缺陷（c8446b0ea，非回归）|

## 9. 关联文件

| 文件 | 角色 |
|---|---|
| `backend/.../notification/core/NotificationRecipientFilter.java` | 新增 Pure Core 纯函数 |
| `backend/.../service/ProjectAccessScopeService.java` | 新增 `canAccessProject` 方法 |
| `backend/.../project/notification/TaskReviewNotificationService.java` | 核心修复点 |
| `src/api/client.js` | 前端 403 友好降级 |
| `docs/exec-plans/tech-debt-tracker.md` | 11 处审视清单 |
| `docs/lessons/lessons-learned.md` §44 | 设计教训 + 检查清单 |
| `specs/030-fix-task-review-notify-403/` | Spec Kit 门禁产物（spec/plan/tasks/contracts/data-model/quickstart）|
