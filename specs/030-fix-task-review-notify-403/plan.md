# Implementation Plan: 修复任务审核通知接收人广播导致的无权限跳转

**Branch**: `agent/zcode/fix-task-review-notify-403` | **Date**: 2026-07-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/030-fix-task-review-notify-403/spec.md`

## Summary

`TaskReviewNotificationService.notifyTaskReviewSubmitted` 用 `findEnabledByRoleProfileCodes(TASK_MUTATION_ALLOWED_ROLES)` 把"任务已提交审核"通知广播给全球所有投标专员/负责人/admin，**未对接收人做项目可见性过滤**，且 targetUrl 硬编码 `/project/{id}/drafting`。当被广播到的投标专员（如 06131）不在该项目的可访问范围时，点击通知 → `GET /api/projects/{id}` → `ProjectAccessScopeService` 抛 `AccessDeniedException("权限不足，无法访问该项目")` → 前端弹"没有权限"。

**修复策略**（按 Constitution I FP-Java 拆分）：

1. **Pure Core**：新增无状态纯函数 `NotificationRecipientFilter`（仿 `TaskNotificationTargetUrlResolver` 风格），接收候选接收人 ID 集合 + 一个"可访问性判定函数" `Predicate<Long>`，输出过滤后的接收人集合。admin 角色直接放行短路。
2. **Imperative Shell**：`ProjectAccessScopeService` 新增 `canAccessProject(Long userId, Long projectId)` 轻量方法（复用现有 `getAllowedProjectIds`，admin/dataScope=all 短路）。
3. **Service 层接线**：`TaskReviewNotificationService` 注入 `ProjectAccessScopeService`，把候选接收人列表传给 `NotificationRecipientFilter.filter(projectId, candidateIds, accessPredicate)`。
4. **前端兜底**：通知点击跳转若目标项目无权访问，降级到 `/inbox`（可拆下迭代，本期实现）。
5. **审视清单**：grep `findEnabledByRoleProfileCodes` 6 处调用点，登记到 `tech-debt-tracker.md`。

## Technical Context

**Language/Version**: Java 21（backend）、Vue 3 + Vite 5（frontend）

**Primary Dependencies**: Spring Boot 3.2 + JPA + MySQL 8.0 + Flyway；Element Plus + Pinia

**Storage**: MySQL（`notification` / `user_notification` 两张表），不涉及 schema 变更

**Testing**: JUnit 5 + Mockito（backend 单测）；Vitest（前端单测）；Playwright（E2E，本期纯后端修复可豁免）

**Target Platform**: Linux server（生产 systemd 部署），浏览器端 Vue SPA

**Project Type**: web-service（前后端分离）

**Performance Goals**: 单次 `notifyTaskReviewSubmitted` 相比修复前增量 < 200ms（SC-003）；候选接收人通常 10-20 个

**Constraints**: FP-Java 纯核心可单测；禁止 Mock；现有 try-catch 容错语义保留；不改变 `TASK_MUTATION_ALLOWED_ROLES` 定义

**Scale/Scope**: 改动 3 个后端文件（新增 1 个 + 修改 2 个）+ 2 个前端文件（兜底）+ 1 个测试文件 + 2 处文档（lessons + tech-debt），<300 行变更

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 是否冲突 | 说明 |
|---|---|---|
| **I. FP-Java**（纯核心/命令壳分离） | ✅ 符合 | 新增 `NotificationRecipientFilter` 为无状态纯函数，参数显式传入；ProjectAccessScopeService 作为 imperative shell 提供可访问性判定 |
| **II. Real-API Only** | ✅ 符合 | 复用真实 Repository（UserRepository、ProjectAccessScopeService），不引入 Mock |
| **III. TDD** | ✅ 符合 | 先写 `NotificationRecipientFilterTest`（Red），再实现（Green），再重构 |
| **IV. Split-First**（<200/300 行） | ✅ 符合 | 新增 `NotificationRecipientFilter` <80 行；`TaskReviewNotificationService` 修改后仍 <110 行 |
| **V. OSS Integration** | ✅ 符合 | 复用 `EffectiveRoleResolver`，不重写角色解析；不触发 OSS 同步 |
| **VI. Authorization Unification** | ✅ 符合 | 不引入 `@PreAuthorize` 白名单；权限校验通过 `ProjectAccessScopeService`（已在 constitution "Project Access Guard" 列为唯一源）|
| **VII. Defensive Collection** | ✅ 符合 | 不新增 `Collectors.toMap`；通知派发失败 try-catch 降级保留（与 spec FR-007 一致）|
| **VIII. Boring Proven Patterns** | ✅ 符合 | 不引入新框架；纯函数 + Predicate 是 Java 标准模式（与现有 `TaskNotificationTargetUrlResolver` 一致）|

**结论**：0 项违规，无需 Complexity Tracking 豁免。可进入 Phase 0。

## Project Structure

### Documentation (this feature)

```text
specs/030-fix-task-review-notify-403/
├── plan.md              # 本文件
├── research.md          # Phase 0：技术决策与权衡
├── data-model.md        # Phase 1：实体与字段
├── quickstart.md        # Phase 1：本地验证步骤
├── contracts/
│   └── notification-filter-api.md  # Phase 1：NotificationRecipientFilter 接口契约
├── checklists/
│   └── requirements.md  # specify 阶段产出
└── tasks.md             # 由 /speckit-tasks 生成（下一阶段）
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/
├── notification/
│   └── core/
│       ├── NotificationRecipientFilter.java   # 新增：纯函数（Pure Core）
│       └── TaskNotificationTargetUrlResolver.java  # 既有，参考风格
├── project/notification/
│   └── TaskReviewNotificationService.java     # 修改：注入 ProjectAccessScopeService
└── service/
    └── ProjectAccessScopeService.java         # 修改：新增 canAccessProject 方法

backend/src/test/java/com/xiyu/bid/
├── notification/core/
│   └── NotificationRecipientFilterTest.java   # 新增：纯函数单测
└── project/notification/
    └── TaskReviewNotificationServiceTest.java # 修改：补充"无权用户不收通知"用例

src/                                              # 前端
├── components/common/NotificationPanel.vue      # 修改：跳转失败降级
├── views/NotificationInbox.vue                  # 修改：跳转失败降级
└── utils/notificationHelpers.js                 # 既有，无需改

docs/
├── lessons/lessons-learned.md                   # 追加 §44
└── exec-plans/tech-debt-tracker.md              # 追加 findEnabledByRoleProfileCodes 审视清单
```

**Structure Decision**: 选用既有 web-service 结构（前后端分离）。`NotificationRecipientFilter` 放在 `notification/core/` 包（与 `TaskNotificationTargetUrlResolver` 同包，Pure Core 集中），保持 FP-Java 分层一致。

## Complexity Tracking

无 Constitution Check 违规，本表为空。
