# Tasks: 投标关键节点企微通知

**Input**: Design documents from `/specs/036-wecom-bid-notifications/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: TDD required — tests MUST be written first and fail before implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify existing notification infrastructure and prepare shared constants.

- [x] T001 Inspect existing `NotificationApplicationService.createNotification` and `ProjectNotificationRecipientPolicy.resolveRecipients` in `backend/src/main/java/com/xiyu/bid/notification/`
- [x] T002 Inspect `TenderEvaluationService.proceedToBid` in `backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationService.java`
- [x] T003 Inspect `ProjectStageService.requestTransition` in `backend/src/main/java/com/xiyu/bid/project/service/ProjectStageService.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared pure-core policies and notification action constants used by both user stories.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests (write first)

- [x] T004 [P] Write unit test for `NotificationDedupPolicy` in `backend/src/test/java/com/xiyu/bid/notification/core/NotificationDedupPolicyTest.java`

### Implementation

- [x] T005 Create notification action enum `NotificationBusinessAction` in `backend/src/main/java/com/xiyu/bid/notification/core/NotificationBusinessAction.java`
- [x] T006 Create pure-core `NotificationDedupPolicy` in `backend/src/main/java/com/xiyu/bid/notification/core/NotificationDedupPolicy.java`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel.

---

## Phase 3: User Story 1 - 立即投标触发待立项通知 (Priority: P1) 🎯 MVP

**Goal**: 当投标管理员在已评估标讯点击"立即投标"并成功创建项目后，向项目负责人发送"待立项"企微通知。

**Independent Test**: 在 `TenderEvaluationService.proceedToBid` 单元测试中 mock 通知应用服务，验证项目创建成功后调用了待立项通知创建方法；`BidNotificationPolicyTest` 验证模板变量与去重决策。

### Tests for User Story 1 (write first)

- [x] T007 [P] [US1] Write unit test for `BidNotificationPolicy` in `backend/src/test/java/com/xiyu/bid/notification/core/BidNotificationPolicyTest.java`
- [x] T008 [P] [US1] Write unit test for `BidNotificationApplicationService` in `backend/src/test/java/com/xiyu/bid/notification/application/BidNotificationApplicationServiceTest.java`
- [x] T009 [US1] Write integration/behavior test for `TenderEvaluationService.proceedToBid` notification trigger in `backend/src/test/java/com/xiyu/bid/tender/service/TenderEvaluationServiceNotificationTest.java`

### Implementation for User Story 1

- [x] T010 [US1] Create pure-core `BidNotificationPolicy` in `backend/src/main/java/com/xiyu/bid/notification/core/BidNotificationPolicy.java`
- [x] T011 [US1] Create application service `BidNotificationApplicationService` in `backend/src/main/java/com/xiyu/bid/notification/application/BidNotificationApplicationService.java`
- [x] T012 [US1] Add factory method `forPendingInitiation` to `NotificationMessagePolicy` in `backend/src/main/java/com/xiyu/bid/notification/core/NotificationMessagePolicy.java`
- [x] T013 [US1] Wire `BidNotificationApplicationService` into `TenderEvaluationService.proceedToBid` in `backend/src/main/java/com/xiyu/bid/tender/service/TenderEvaluationService.java`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - 项目结项推进触发待结项申请通知 (Priority: P1)

**Goal**: 当投标负责人将项目从"复盘阶段"推进至"项目结项阶段"后，向项目负责人发送"待结项申请"企微通知。

**Independent Test**: 在 `ProjectStageService.requestTransition` 单元测试中 mock 通知应用服务，验证 `RETROSPECTIVE → CLOSED` 成功后调用了待结项申请通知创建方法；`ProjectClosureNotificationPolicyTest` 验证模板变量与去重决策。

### Tests for User Story 2 (write first)

- [x] T014 [P] [US2] Write unit test for `ProjectClosureNotificationPolicy` in `backend/src/test/java/com/xiyu/bid/notification/core/ProjectClosureNotificationPolicyTest.java`
- [x] T015 [P] [US2] Write unit test for `ProjectClosureNotificationApplicationService` in `backend/src/test/java/com/xiyu/bid/notification/application/ProjectClosureNotificationApplicationServiceTest.java`
- [x] T016 [US2] Write integration/behavior test for `ProjectStageService.requestTransition` notification trigger in `backend/src/test/java/com/xiyu/bid/project/service/ProjectStageServiceNotificationTest.java`

### Implementation for User Story 2

- [x] T017 [US2] Create pure-core `ProjectClosureNotificationPolicy` in `backend/src/main/java/com/xiyu/bid/notification/core/ProjectClosureNotificationPolicy.java`
- [x] T018 [US2] Create application service `ProjectClosureNotificationApplicationService` in `backend/src/main/java/com/xiyu/bid/notification/application/ProjectClosureNotificationApplicationService.java`
- [x] T019 [US2] Add factory method `forPendingClosureApplication` to `NotificationMessagePolicy` in `backend/src/main/java/com/xiyu/bid/notification/core/NotificationMessagePolicy.java`
- [x] T020 [US2] Wire `ProjectClosureNotificationApplicationService` into `ProjectStageService.requestTransition` in `backend/src/main/java/com/xiyu/bid/project/service/ProjectStageService.java`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Architecture gates, documentation, and final validation.

- [x] T021 [P] Run ArchUnit tests `FPJavaArchitectureTest` and `MaintainabilityArchitectureTest`
- [x] T022 [P] Run Checkstyle / PMD / SpotBugs quality gates
- [x] T023 Update implementation notes in `specs/036-wecom-bid-notifications/plan.md` if decisions changed
- [x] T024 Run `mvn test` in `backend/` and verify all new and existing tests pass
- [x] T025 Run `npm run ci:pre-pr` (or `npm run ci:local:quick` if Docker unavailable)
- [x] T026 Commit all changes atomically with feature branch `agent/kimi/enterprise-wecom-notifications`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion.
  - US1 and US2 can proceed in parallel (different files, no shared implementation besides Phase 2).
- **Polish (Final Phase)**: Depends on all user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on US2.
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - No dependencies on US1.

### Within Each User Story

- Tests MUST be written and FAIL before implementation.
- Pure-core policy before application service.
- Application service before wiring into existing `TenderEvaluationService` / `ProjectStageService`.
- Story complete before moving to next priority.

### Parallel Opportunities

- T007, T008, T009 (US1 tests) can run in parallel.
- T010, T011 (US1 implementation) can run in parallel after tests are written.
- T014, T015, T016 (US2 tests) can run in parallel.
- T017, T018 (US2 implementation) can run in parallel after tests are written.
- US1 and US2 can be implemented in parallel by different agents once Phase 2 is done.

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Write unit test for BidNotificationPolicy"
Task: "Write unit test for BidNotificationApplicationService"
Task: "Write integration test for TenderEvaluationService notification trigger"

# Launch implementation tasks (after tests exist):
Task: "Create pure-core BidNotificationPolicy"
Task: "Create application service BidNotificationApplicationService"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Run US1 tests and relevant service tests
5. Then proceed to User Story 2

### Incremental Delivery

1. Setup + Foundational → shared constants and policies ready.
2. US1 → immediate bid notification.
3. US2 → closure application notification.
4. Polish → gates, docs, commit.

### Parallel Team Strategy

With multiple agents:

1. Agent A: Phase 2 + US1 tests + US1 implementation.
2. Agent B: US2 tests + US2 implementation.
3. Agent C: Polish phase (architecture gates + final validation).

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Each user story should be independently completable and testable.
- Verify tests fail before implementing.
- Commit after each logical group.
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence.
