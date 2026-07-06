# Phase 0 Research: 修复任务审核通知接收人广播 403

**Feature**: 030-fix-task-review-notify-403
**Date**: 2026-07-06

## 研究任务清单

本 spec 在 specify 阶段已通过根因调查（§23 全链路日志 SOP）明确所有关键决策，**没有 NEEDS CLARIFICATION 项**。Phase 0 仅记录关键决策的依据与权衡，便于后续审阅。

---

## 决策 1：纯函数 vs 直接在 Service 内联过滤

**Decision**: 抽出无状态纯函数 `NotificationRecipientFilter`，放在 `notification/core/` 包。

**Rationale**:
- Constitution I (FP-Java) 要求纯核心/命令壳分离
- 现有同包 `TaskNotificationTargetUrlResolver` 已经是这种风格的纯函数，沿用一致
- 接收人过滤逻辑未来会被其他通知派发器复用（见 tech-debt 审视清单），抽出来避免复制
- 纯函数可单测，无需 Spring context（Constitution III TDD 友好）

**Alternatives considered**:
- **A. 直接在 `TaskReviewNotificationService.getTaskReviewerUserIds` 内联 stream.filter**：被否，因为未来 5+ 个通知派发器都要复制同一段逻辑，违反 DRY
- **B. 抽成 Spring `@Component` 注入 ProjectAccessScopeService**：被否，因为这样就把"过滤策略"和"权限查询"耦合了，纯函数 + Predicate 更灵活可测
- **C. 用 AOP 拦截通知派发统一过滤**：被否，过度工程，违反 Constitution VIII (Boring Proven Patterns)

---

## 决策 2：ProjectAccessScopeService 新增方法签名

**Decision**: 新增 `boolean canAccessProject(Long userId, Long projectId)` 单点判定方法。

**Rationale**:
- 现有 `getAllowedProjectIds(User)` 是"用户 → 项目集合"的正向查询，本次需要"用户 × 项目 → bool"的单点判定
- 候选接收人通常 10-20 个（`TASK_MUTATION_ALLOWED_ROLES` 反查），逐个调用可接受
- admin/dataScope=all 短路避免对管理员全量计算（管理员能访问所有项目）

**性能权衡**（满足 SC-003 增量 < 200ms）：
- 假设 15 个候选接收人，其中 1-2 个 admin 短路（O(1)），其余 13 个非 admin 各跑一次 `getAllowedProjectIds(user)`
- 单次 `getAllowedProjectIds` 约 5-15ms（8 条 SQL），13 × 15ms ≈ 195ms，**刚好临界**
- 如果实测超 200ms，优化方向：在 `ProjectAccessScopeService` 内增加 batch 缓存或反向索引（**不在本期范围，记入 tech-debt**）

**Alternatives considered**:
- **A. 批量方法 `Map<Long, Boolean> canAccessProjectBatch(userIds, projectId)`**：被否，因为现有 `getAllowedProjectIds` 是 per-user 的，批量改造工程量大，先用单点 + admin 短路验证可行性
- **B. 在派发时缓存所有候选用户的 allowedProjectIds**：被否，过度优化，违反 Constitution VIII

---

## 决策 3：admin/dataScope=all 短路策略

**Decision**: `NotificationRecipientFilter` 不识别角色，统一通过传入的 `Predicate<Long>` 判定；admin 短路放在 `ProjectAccessScopeService.canAccessProject` 内部。

**Rationale**:
- `NotificationRecipientFilter` 保持纯粹（只懂集合过滤，不懂角色语义）
- `ProjectAccessScopeService` 已有 admin 短路逻辑（`getAllowedProjectIds` 第 56-58 行），`canAccessProject` 复用同一短路
- 角色解析用 `EffectiveRoleResolver.resolveRoleCode(user)`，与 constitution V (OSS Integration) 一致

---

## 决策 4：前端 targetUrl 降级策略

**Decision**: 在 `NotificationPanel.vue` / `NotificationInbox.vue` 的 `handleClick` 路由跳转处包 try/catch，失败时降级到 `/inbox`。**但本期以后端过滤为主修复，前端降级作为兜底防线一并实现**。

**Rationale**:
- 即便后端过滤逻辑漏网（如未来新增角色未更新规则），用户体验也不应崩坏（spec User Story 2）
- 前端有现成的 axios 403 拦截器可复用（待编码阶段确认）
- 不需要后端把 targetUrl 改成接收人相关动态值（避免每条通知按接收人存不同 payload，破坏通知表结构）

**Alternatives considered**:
- **A. 后端为每个接收人生成不同 targetUrl**：被否，破坏 notification/user_notification 一对多结构（一条通知 N 个接收人对应同一 payload_json）
- **B. 不做前端降级，纯靠后端过滤**：被否，单点防线，未来回归无兜底

---

## 决策 5：通知派发失败容错语义保留

**Decision**: 现有 `TaskReviewNotificationService.send` 的 try-catch 保留，过滤逻辑包裹在同一 try 块内。

**Rationale**:
- Constitution VII §2 (装饰性 enrichment MUST 降级) 精神适用：通知派发是副作用，失败不应阻断主业务（任务审核提交）
- spec FR-007 明确要求保留容错语义

**Edge case**:
- 过滤后接收人列表为空 → 安全跳过通知创建（不报错）+ INFO 日志记录
- ProjectAccessScopeService 抛异常 → catch 后降级为"不过滤"（保留原广播行为）+ WARN 日志，**优先保证通知送达**而非精准

  > 注：这个降级偏好与 spec FR-001 略有张力——spec 要求"必须过滤"，但 constitution VII §2 要求"装饰性操作失败必须降级"。**取舍**：通知派发的精准性 < 主业务可用性。如果过滤逻辑本身崩了，宁可不过滤也把通知发出去（用户最多看到几条无权访问的通知，比通知完全发不出去好）。这一取舍会在 lessons §44 中说明。

---

## 决策 6：审视清单输出位置

**Decision**: 把 `findEnabledByRoleProfileCodes` 6 处调用点的审视结果登记到 `docs/exec-plans/tech-debt-tracker.md`。

**Rationale**:
- PLANS.md §落计划约定：技术债登记到 `docs/exec-plans/tech-debt-tracker.md`
- 本期只修 `TaskReviewNotificationService`，其他 5 处（`TenderPendingAssignmentNotifier` 等）的同类问题不一定存在（标讯/CA 资质可能是全局通知场景，所有投标专员都该收），需逐个审视后判定

---

## 不需要研究的项（已在根因调查阶段明确）

- **Bug 根因**：`TaskReviewNotificationService.java:79-86` + `:50-52`（日志/DB/git 三重证据已锁）
- **06131 真实角色**：bid-Team（王晓莉，user_id=1471）——日志 `UserDetails authorities built: user=06131 roleCode=bid-Team`
- **通知 payload 真实值**：`targetUrl=/project/172/drafting`（DB 直查）
- **引入 commit**：`c8446b0ea`（2026-07-03，feat: 任务审核通知）——非回归，原设计缺陷
- **CO-474 修复范围**：只覆盖 `notifyTaskReviewResult`（结果通知给当事人），未碰 `notifyTaskReviewSubmitted`（广播给审核人）
