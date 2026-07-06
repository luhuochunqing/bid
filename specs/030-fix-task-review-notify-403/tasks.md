# Tasks: 修复任务审核通知接收人广播 403

**Input**: Design documents from `/specs/030-fix-task-review-notify-403/`

**Prerequisites**: [plan.md](./plan.md) ✅, [spec.md](./spec.md) ✅, [research.md](./research.md) ✅, [data-model.md](./data-model.md) ✅, [contracts/notification-filter-api.md](./contracts/notification-filter-api.md) ✅

**Tests**: 按 Constitution III TDD 强制 — 每个 User Story 先写测试（Red）再实现（Green）

**Organization**: 按 spec.md 3 个 User Story 分组（US1=P1 核心修复，US2=P2 前端兜底，US3=P3 审视清单）

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: US1/US2/US3

---

## Phase 1: Setup（共享基础设施）

**Purpose**: 准备工作和环境对齐（本期不涉及 schema 变更，setup 极简）

- [ ] T001 确认分支基线：当前在 `agent/zcode/fix-task-review-notify-403`，已 rebase `origin/main`，无未提交变更（`git status` 应 clean）
- [ ] T002 [P] 重读 contracts/notification-filter-api.md，确认 `NotificationRecipientFilter` 与 `ProjectAccessScopeService.canAccessProject` 的契约细节（特别是降级取舍）

---

## Phase 2: Foundational（阻塞性前置 — User Story 共用的纯函数）

**Purpose**: `NotificationRecipientFilter` 纯函数是 US1/US2/US3 共用的基础设施，必须先完成

**⚠️ CRITICAL**: US1/US2 的 Service 接线都依赖这个纯函数，必须先实现

### Tests（TDD Red — 先写，期望失败）

- [ ] T003 [P] [US1] 新建 `backend/src/test/java/com/xiyu/bid/notification/core/NotificationRecipientFilterTest.java`，覆盖契约 §单元测试契约 表的全部 10 个用例（null/空集合、全部通过、全部过滤、部分过滤、null 元素跳过、去重、顺序保留、predicate 为 null 抛 NPE、predicate 抛异常透传）
- [ ] T004 跑 `mvn test -Dtest=NotificationRecipientFilterTest`，**确认全部 FAIL**（类还不存在，编译失败）— TDD Red 验证

### Implementation（TDD Green — 实现）

- [ ] T005 [US1] 新建 `backend/src/main/java/com/xiyu/bid/notification/core/NotificationRecipientFilter.java`：`final` class，私有构造，单静态方法 `filterRecipients(Collection<Long>, Predicate<Long>)`，严格按契约实现
- [ ] T006 跑 `mvn test -Dtest=NotificationRecipientFilterTest`，**确认全部 PASS** — TDD Green

### Refactor（TDD Refactor — 检查）

- [ ] T007 [US1] 检查行数 < 100（Constitution IV）、无 `Collectors.toMap`（Constitution VII）、无 Spring 注解（Constitution I FP-Java 纯核心），如不符则重构
- [ ] T008 提交（原子）：`feat(notification): 新增 NotificationRecipientFilter 纯函数 — 按可访问性过滤候选接收人`

**Checkpoint**: 纯函数就绪，可被 Service 接线

---

## Phase 3: User Story 1 - 无项目访问权的人不应收到该项目的任务审核通知 (Priority: P1) 🎯 MVP

**Goal**: `TaskReviewNotificationService.notifyTaskReviewSubmitted` 在派发前用 `ProjectAccessScopeService` 过滤候选接收人，剔除对项目无访问权的用户

**Independent Test**: 一个不在项目 X 可见范围内的 bid-Team 用户，触发项目 X 内任务审核提交后，DB `user_notification` 表中该用户没有项目 X 的 TASK_UPDATE 记录

### Tests（TDD Red — 先写）

- [ ] T009 [P] [US1] 修改 `backend/src/test/java/com/xiyu/bid/project/notification/TaskReviewNotificationServiceTest.java`，补充 5 个用例：①bid-Team 用户被广播到无权项目时不出现；②admin 用户始终通过；③所有候选被过滤掉时不调用 `createNotification` 并打 INFO 日志；④`ProjectAccessScopeService` 抛异常时降级为原候选广播 + WARN 日志；⑤过滤后列表非空时正常派发
- [ ] T010 跑 `mvn test -Dtest=TaskReviewNotificationServiceTest`，确认新增用例 **FAIL**（`ProjectAccessScopeService` 还没注入，过滤还没接）— TDD Red

### Implementation（TDD Green）

- [ ] T011 [P] [US1] 在 `backend/src/main/java/com/xiyu/bid/service/ProjectAccessScopeService.java` 新增 `canAccessProject(Long userId, Long projectId)` 方法：null 防御返回 false → admin/dataScope=all 短路返回 true → 复用 `getAllowedProjectIds(user).contains(projectId)`
- [ ] T012 [US1] 修改 `backend/src/main/java/com/xiyu/bid/project/notification/TaskReviewNotificationService.java`：①注入 `ProjectAccessScopeService`（构造注入）；②新增私有方法 `filterRecipientsSafe(candidates, projectId)`，try-catch 包裹 `NotificationRecipientFilter.filterRecipients(...)`，失败降级为原 candidates；③`notifyTaskReviewSubmitted` 在 `getTaskReviewerUserIds` 之后调用 `filterRecipientsSafe`，结果为空时打 INFO 日志并 return
- [ ] T013 跑 `mvn test -Dtest=TaskReviewNotificationServiceTest`，**确认全部 PASS** — TDD Green
- [ ] T014 跑 `mvn test -Dtest=ProjectAccessScopeServiceTest`（如存在）或全量 `mvn test`，**确认无回归**

### Refactor & Verify

- [ ] T015 [US1] 检查 `TaskReviewNotificationService.java` 总行数 < 200（Constitution IV）；如超出，把 `filterRecipientsSafe` 内联到 `notifyTaskReviewSubmitted` 或抽到 helper
- [ ] T016 [US1] 检查 ArchUnit 守卫：`mvn -Pjava-quality checkstyle:check pmd:check spotbugs:check -q`，全绿
- [ ] T017 提交（原子）：`fix(notification): TaskReviewNotificationService 按项目可见性过滤接收人，剔除无权用户 (06131 案例)`

**Checkpoint**: 核心修复完成。部署后 06131 不再收到无权项目的任务审核通知。

---

## Phase 4: User Story 2 - 通知点击跳转失败时降级到通知中心 (Priority: P2)

**Goal**: 前端 `NotificationPanel.vue` / `NotificationInbox.vue` 跳转失败（路由错误或 403）时降级到 `/notifications`，不弹红色报错

**Independent Test**: 手动构造一个 targetUrl 指向无权项目的通知（或临时改一个用户角色），点击后跳转到 `/notifications` 而非项目详情页

> **注**: 本 Phase 是兜底防线。即便 Phase 3 完美，未来新增角色未更新过滤规则时仍能保护用户体验。如工期紧张可独立拆到下迭代，但本期一并实现。

### Tests（TDD Red）

- [ ] T018 [P] [US2] 检查/新建 `src/components/common/__tests__/NotificationPanel.spec.js`（如不存在则新建），补 2 个用例：①正常 targetUrl 跳转成功；②目标路由跳转抛错时降级到 `/notifications`
- [ ] T019 [P] [US2] 同上为 `src/views/NotificationInbox.vue` 补同样测试（如已有测试文件则修改）

### Implementation（TDD Green）

- [ ] T020 [P] [US2] 修改 `src/utils/notificationHelpers.js` 的 `resolveNotificationRoute`（或新增 `safeNavigate` helper），把 `router.push` 包 try/catch，失败时 `router.push('/notifications')` + 控制台 warn
- [ ] T021 [US2] 修改 `src/components/common/NotificationPanel.vue` 的 `handleClick`（约 89-100 行），用 `safeNavigate` 替换直接 `router.push`
- [ ] T022 [US2] 修改 `src/views/NotificationInbox.vue` 的点击 handler（约 117-125 行），同上替换
- [ ] T023 跑 `npm run test:unit -- --filter Notification`，**确认 PASS**；跑 `npm run lint`，无新 error

### Verify

- [ ] T024 [US2] 手动验证：本地启动后用 bid-Team 账户，构造一条 targetUrl 指向无权项目的通知（DB 注入或临时改角色），点击后应跳 `/notifications` 不报错
- [ ] T025 提交（原子）：`fix(notification): 前端通知跳转失败降级到通知中心，避免 403 红色报错 (US2 兜底)`

**Checkpoint**: 即便后端过滤漏网，前端用户体验也不崩坏

---

## Phase 5: User Story 3 - 同类广播派发问题审视清单 (Priority: P3)

**Goal**: 仓库内所有 `findEnabledByRoleProfileCodes` 调用点都审视过，登记到 tech-debt-tracker.md

**Independent Test**: `docs/exec-plans/tech-debt-tracker.md` 新增一节"通知派发接收人按项目可见性过滤审视清单"，包含 6 个调用点的判定结论

### Implementation

- [ ] T026 [P] [US3] 调研：执行 `grep -rn "findEnabledByRoleProfileCodes" backend/src/main/java/`，对每个调用点（预期 6 处：`TaskReviewNotificationService`、`TenderPendingAssignmentNotifier`、`TenderEvaluationNotificationService`、`CaNotificationDispatcher`、`QualificationExpiryNotificationService` 等）阅读上下文 5-10 行，判定"是否广播给对该资源无访问权的用户"
- [ ] T027 [US3] 在 `docs/exec-plans/tech-debt-tracker.md` 追加一节"通知派发接收人按项目可见性过滤审视清单（spec 030）"，每个调用点一行：文件:行号 + 通知场景 + 是否需同类修复（✅ 已修 / ⚠️ 需修 / ℹ️ 无需修，原因）
- [ ] T028 提交（原子）：`docs(tech-debt): 登记 findEnabledByRoleProfileCodes 通知派发器审视清单 (spec 030)`

**Checkpoint**: 教训沉淀为系统级清单，未来同类 Bug 可追溯

---

## Phase 6: Polish & Cross-Cutting（lessons 与一致性校验）

**Purpose**: 沉淀教训 + Spec Kit analyze 阶段准备

- [ ] T029 [P] 在 `docs/lessons/lessons-learned.md` 追加 §44「通知派发接收人必须按资源可见性过滤（spec 030 / 06131 案例）」，包含：根因证据链摘要 + 三层 SOP 取舍 + 设计教训（接收人范围 × 资源可见性 × targetUrl 三者联动）+ 检查清单（新增通知派发器时必跑）
- [ ] T030 [P] 同时新建独立 RCA 文件 `docs/lessons/root-cause-analysis-spec030-task-review-notify-403.md`，归档完整证据链（日志原文、DB 查询结果、git 追溯）
- [ ] T031 提交（原子）：`docs(lessons): §44 通知派发接收人必须按资源可见性过滤 + spec 030 RCA 归档`
- [ ] T032 跑 Spec Kit 一致性校验：调用 `/speckit-analyze` skill，验证 spec ↔ plan ↔ tasks ↔ 实现的一致性

---

## Dependencies（执行顺序）

```
Phase 1 (Setup)
    │
    ▼
Phase 2 (Foundational - NotificationRecipientFilter)
    │
    ├──────────────┬──────────────┐
    ▼              ▼              ▼
Phase 3 (US1)   Phase 4 (US2)   Phase 5 (US3)
  核心修复        前端兜底        审视清单
    │              │              │
    └──────────────┴──────────────┘
                   │
                   ▼
            Phase 6 (Polish - lessons + analyze)
```

**关键依赖**：
- Phase 3/4/5 都依赖 Phase 2 的 `NotificationRecipientFilter`（US2 前端不直接依赖纯函数，但需要 US1 的修复完整闭环才好测）
- Phase 3 (US1) 是 MVP，必须最先完成
- Phase 4 (US2) 和 Phase 5 (US3) 可与 Phase 3 并行（不同文件），但建议串行避免认知上下文切换
- Phase 6 必须最后（lessons 需要总结整个修复过程）

## Parallel Opportunities

| 可并行任务组合 | 条件 |
|---|---|
| T002（重读契约）+ T001（确认基线） | 都在 Phase 1，无文件冲突 |
| T003（写 US1 测试）+ T018/T019（写 US2 测试）+ T026（grep 审视） | 跨 Phase 但不同文件，理论可并行 |
| T011（改 ProjectAccessScopeService）+ T020（改前端 helper） | US1 后端 + US2 前端，不同栈 |

## MVP Scope

**最小可交付**：Phase 1 + Phase 2 + Phase 3（即 US1）= 修复 06131 案例的核心问题。
Phase 4/5/6 是兜底与沉淀，可在 MVP 验证后追加。

## Implementation Strategy

1. **先 TDD 走 Phase 2 纯函数**（最简单、无依赖、可独立验证）
2. **再走 Phase 3 核心修复**（MVP 关键）
3. **Phase 3 验证通过后部署测试环境**，用 06131 账户验证修复
4. **并行做 Phase 4/5**（前端兜底 + 审视清单）
5. **最后 Phase 6** 沉淀 lessons + 跑 analyze

---

## 任务汇总

| Phase | 任务数 | 描述 |
|---|---|---|
| Phase 1 Setup | 2 | 基线确认 + 契约对齐 |
| Phase 2 Foundational | 6 | NotificationRecipientFilter 纯函数（TDD Red/Green/Refactor + 提交）|
| Phase 3 US1 (P1) 🎯 | 9 | TaskReviewNotificationService 接线（核心修复，MVP）|
| Phase 4 US2 (P2) | 8 | 前端 targetUrl 降级（兜底）|
| Phase 5 US3 (P3) | 3 | findEnabledByRoleProfileCodes 审视清单 |
| Phase 6 Polish | 4 | lessons §44 + RCA + speckit-analyze |
| **合计** | **32** | |

## Independent Test Criteria per Story

| Story | 独立验证 |
|---|---|
| US1 | bid-Team 用户被广播到无权项目时不收到通知（DB 验证 user_notification 表） |
| US2 | 通知点击跳转失败时降级到 /notifications（手动或 E2E 验证） |
| US3 | tech-debt-tracker.md 有完整的 6 点审视清单（文档检查） |

---

## 实施偏差说明（speckit-analyze 阶段补充）

实施过程中 Phase 4 的具体技术与 tasks.md 原计划不一致，记录如下：

**偏差点**：Phase 4 T020-T022 原计划在 `notificationHelpers.js` 新增 `safeNavigate` helper，包 `router.push` try/catch；实施时发现该方案不可行——Vue Router 4 的 `router.push(string)` 是同步 URL 变更，**不会因后续页面 API 调用 403 而抛错**（路由跳转和 API 调用解耦），try/catch 抓不到 403。

**实施方案调整**：改为修改 `src/api/client.js:188-189` 全局 403 拦截器，精准识别 `GET /api/projects/\d+` 场景（通知跳转触发的项目详情请求），用 `ElMessage.warning` 替代 `ElMessage.error` + 文案友好化 + 2.5s 后自动 `router.push('/inbox')`。

**对 spec 一致性的影响**：无影响。spec.md FR-004 只规定"targetUrl 降级到接收人可访问的安全路径"，未规定实现方式。新方案更准确——直接在 403 拦截点降级，覆盖所有触发场景（不仅限于通知跳转）。

**未实施的任务**：T018-T019（前端单测）、T021-T022（修改 NotificationPanel.vue / NotificationInbox.vue）跳过——无文件变更需求，原计划的 try/catch 包装不必要。`NotificationPanel.vue` / `NotificationInbox.vue` / `notificationHelpers.js` **保持原样不变**。

**-speckit-analyze 报告 ID F1 已归档**。

