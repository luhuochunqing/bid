---

description: "CO-498 项目复盘阶段提交后解锁结项阶段 tab"
---

# Tasks: CO-498 项目复盘阶段提交后解锁结项阶段 tab

**Input**: Design documents from `.specify/specs/024-co498-retrospective-closure-tab-unlock/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Organization**: Tasks grouped by user story. 本变更是单一后端 Controller 微调，无并行 Phase，但仍按 user story 分组以便独立验证。

---

## Phase 1: Foundational（无独立 Foundation 阶段）

本变更**无**数据库迁移、**无**新依赖、**无**新 Bean、**无**新 Service 方法。前置条件已就绪：
- `ProjectStageController` 已注入 `closureRepository`、`service` 等所有依赖
- `ProjectStageService.hasClosureSubmission()` 已存在并被 CO-443 使用
- 测试基础 `ProjectStageControllerTest` 已存在（5 个用例）

直接进入用户故事实现。

---

## Phase 2: User Story 1 - 项目负责人提交复盘后能进入结项 tab（Priority: P1）🎯 MVP

**Goal**: 修复导航断层 — `ProjectStageController.get()` 在 stage=RETROSPECTIVE 且无 closure 记录时，把 CLOSED 加入 `accessibleStages`，覆盖 AC-1、AC-3。

### TDD Red — 先写失败的测试

- [ ] T001 [US1] 新增测试 `co498_retrospectiveWithoutClosure_unlocksClosedTab` 到 `backend/src/test/java/com/xiyu/bid/project/controller/ProjectStageControllerTest.java`。Mock `stageService.currentStage(42L)` 返回 `RETROSPECTIVE`、`stageService.hasClosureSubmission(42L)` 返回 `false`、`closureRepository.findByProjectId(42L)` 返回 `Optional.empty()`。断言 `accessibleStages` 包含 `"CLOSED"`、`currentStage=RETROSPECTIVE`、`terminal=false`。**验证 Red：运行该测试应 FAIL（CLOSED 不在 accessibleStages）。**
- [ ] T002 [US1] 新增测试 `co498_resultPendingStage_doesNotUnlockClosedTab`（边界守护，覆盖 AC-3）。Mock `currentStage` 返回 `RESULT_PENDING`、`hasClosureSubmission` 返回 `false`。断言 `accessibleStages` **不**包含 `"CLOSED"`。**验证 Red：运行应 FAIL**（当前实现不会解锁任何阶段，但此测试在 Green 后会通过 — 该测试主要保障后续 refactor 不破坏边界。Red 阶段可仅验证测试编译通过）。

### TDD Green — 最小实现使测试通过

- [ ] T003 [US1] 修改 `backend/src/main/java/com/xiyu/bid/project/controller/ProjectStageController.java` 的 `get()` 方法：
  - 抽局部变量 `boolean hasClosureSubmission = service.hasClosureSubmission(projectId);`
  - 把第 52 行 `(actual != ProjectStage.CLOSED && service.hasClosureSubmission(projectId))` 改用局部变量
  - 在 `accessible.add(current.name());` 之后追加：`if (actual == ProjectStage.RETROSPECTIVE && !hasClosureSubmission) { accessible.add(ProjectStage.CLOSED.name()); }`
  - 添加注释引用 CO-498 + spec.md FR-1
- [ ] T004 [US1] 运行 T001 + T002 测试，验证两个测试均 Green。**如有既有用例 Red，回滚并修正。**

### TDD Refactor — 消除重复 + 守护

- [ ] T005 [US1] 新增测试 `co498_retrospectiveWithClosureDraft_doesNotUnlockClosedTwice`（覆盖 AC-2 + 重复守护）。Mock `currentStage` 返回 `RETROSPECTIVE`、`hasClosureSubmission` 返回 `true`、`closureRepository.findByProjectId(42L)` 返回 `Optional.of(ProjectClosure with reviewStatus="DRAFT")`。断言 `currentStage=CLOSED`（CO-443 假 CLOSED）、`accessibleStages` 含 CLOSED 但 `not(hasSize(7))`（无重复）。**此测试在 T004 后应直接 Green。**
- [ ] T006 [US1] 新增测试 `co498_retrospectiveWithoutClosure_unlocksClosedForAllRoles`（覆盖 AC-4 角色一致性）。用两个不同 username（`06234` admin / `09118` 审核人）跑同一断言。**应直接 Green。**

### Phase 2 验收门禁

- [ ] T007 [US1] 跑 `ProjectStageControllerTest` 全部用例（应有 9 个：原 5 个 + 新 4 个），全部 Green。
- [ ] T008 [US1] 跑 `ProjectRetrospectiveServiceTest` 全部用例（AC-6 回归，验证复盘提交行为不变），全部 Green。

---

## Phase 3: User Story 2 - 审核人能进入结项 tab 查看申请（Priority: P2）

**Goal**: 验证角色矩阵不回归 — US-1 解锁后，审核人能正常访问结项 tab。本故事**不写新代码**，由既有 `ClosureStage.canApprove` 保障，仅补充回归测试。

- [ ] T009 [US2] [P] 验证 `co498_retrospectiveWithoutClosure_unlocksClosedForAllRoles`（T006）已覆盖审核人（`09118`）角色 — 若已覆盖，标记完成。**无需新代码。**
- [ ] T010 [US2] 手工验证：本地或预发环境，项目 stage=RETROSPECTIVE + closure.review_status=PENDING 时，审核角色访问 `/project/{id}` 进入结项 tab，确认 ClosureStage 加载且 `canApprove` 计算正确（admin/组长/投标负责人/辅助 看到"审核通过/驳回"按钮）。**记录到 implementation-notes。**

---

## Phase 4: User Story 3 - admin 角色可见 tab 但提交受角色矩阵限制（Priority: P3）

**Goal**: 验证 admin 体验不回归 — admin 能看到 CLOSED tab 但不显示"提交结项"按钮。

- [ ] T011 [US3] [P] 验证 `co498_retrospectiveWithoutClosure_unlocksClosedTab`（T001）已使用 `06234`（admin）身份 — 若已覆盖，标记完成。**无需新代码。**
- [ ] T012 [US3] 手工验证：本地或预发环境，admin 进入项目 157 结项 tab，确认 ClosureStage 不显示"提交结项"按钮（`canSubmitClosure=false` 因 admin != bid-projectLeader）。**记录到 implementation-notes。**

---

## Phase 5: 端到端验证 + 收尾

- [ ] T013 端到端验证生产环境（项目 157）：本修复部署后，登录项目 157，确认 CLOSED tab 可见可点（覆盖 AC-5）。**注意：生产部署必须走 `xiyu-deploy` skill，不得本地脚本部署。**
- [ ] T014 运行 `cd backend && mvn test -Dtest='ProjectStageControllerTest,ProjectRetrospectiveServiceTest'` 整体回归。
- [ ] T015 更新 `implementation-notes.md` 阶段 4/5 章节，记录所有 tradeoff、决策、验证证据。
- [ ] T016 提交 PR（含 spec.md / plan.md / tasks.md / 实现代码 / 测试），描述引用本 feature 目录。
- [ ] T017 可选：把 spec.md 的"问题分析"章节抽到 `docs/lessons/root-cause-analysis-co-498.md`，更新 `docs/lessons/lessons-learned.md` 索引（按 lessons-learned 惯例）。

---

## 任务依赖图

```
T001 (Red: 核心)  ─┐
T002 (Red: 边界)  ─┤
                   ├─→ T003 (Green: 实现) ──→ T004 (验证 Green) ──→ T005 (重复守护)
                   │                                                       │
                   │                                                       ↓
                   │                                                      T006 (角色一致性)
                   │                                                       │
                   │                                                       ↓
                   │                                              T007 (Phase2 门禁)
                   │                                                       │
                   │                                                       ↓
                   │                                              T008 (Retrospective 回归)
                   │                                                       │
T009 [P] ──────────┤                                                       │
T011 [P] ──────────┤                                                       │
                   └─────────────────────────────────────────────────────→ T010/T012 (手工)
                                                                            │
                                                                            ↓
                                                                   T013-T017 (收尾)
```

**关键路径**：T001 → T003 → T004 → T007 → T008 → T013 → T016
**可并行**：T009（US2 验证）、T011（US3 验证）在 T006 完成后可并行确认
