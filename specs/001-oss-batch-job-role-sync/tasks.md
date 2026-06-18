---

description: "Task list for OSS batch job/role lookup optimization"

---

# Tasks: OSS 批量岗位/角色回查优化

**Input**: Design documents from `/specs/001-oss-batch-job-role-sync/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included as requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare the feature branch and verify existing code state.

- [x] T001 Run `git fetch origin && git rebase origin/main` on branch `001-oss-batch-job-role-sync`
- [x] T002 Run `./scripts/who-touches.sh backend/src/main/java/com/xiyu/bid/integration/organization/` to verify no conflicting agent work
- [x] T003 Verify existing tests pass: `cd backend && mvn test -Dtest=OrganizationUserSyncWriterTest,OrganizationDirectoryHttpGatewayTest,PositionToRoleMapperTest`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core data structures and configuration that MUST complete before user story implementation.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 [P] Add batch job/role lookup configuration to `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationIntegrationProperties.java` (`batchJobRoleLookupPath`, `batchQuerySize`, `batchConnectTimeoutMs`, `batchReadTimeoutMs`)
- [x] T005 Create `OssUserJobAndRoleDto` in `backend/src/main/java/com/xiyu/bid/integration/organization/dto/OssUserJobAndRoleDto.java`
- [x] T006 [P] Create `JobRoleLookupResolver` in `backend/src/main/java/com/xiyu/bid/integration/organization/domain/policy/JobRoleLookupResolver.java`
- [x] T007 [P] Create `SystemRoleListMapper` in `backend/src/main/java/com/xiyu/bid/integration/organization/domain/policy/SystemRoleListMapper.java`
- [x] T008 Add `getUserJobAndRoleListByJobNumbers` method to `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationDirectoryGateway.java`
- [x] T009 Add `getUserJobAndRoleListByJobNumbers` no-op implementation to `backend/src/main/java/com/xiyu/bid/integration/organization/application/NoOpOrganizationDirectoryGateway.java`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel.

---

## Phase 3: User Story 1 - 批量回查岗位与角色 (Priority: P1) 🎯 MVP

**Goal**: Implement the OSS batch lookup endpoint and integrate it into the sync flow to replace per-user job queries.

**Independent Test**: With a list of known job numbers, trigger batch lookup and verify each job number maps to the correct job name and sysRoleList, and that external call count does not scale linearly with user count.

### Tests for User Story 1

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation.**

- [x] T010 [P] [US1] Add contract test for `OrganizationDirectoryHttpGateway.batchJobRoleLookup` in `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java`
- [x] T011 [P] [US1] Add failure/timeout test for batch lookup in `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java`

### Implementation for User Story 1

- [x] T012 [US1] Implement `getUserJobAndRoleListByJobNumbers` in `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGateway.java` (batching, HTTP POST, response mapping)
- [x] T013 [US1] Add JSON mapping for batch response in `backend/src/main/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryJsonMapper.java`
- [x] T014 [US1] Integrate batch lookup into `OrganizationUserSyncWriter.upsert` in `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java` (replace per-jobId fallback when jobNumber is available)
- [x] T015 [US1] Add request/response logging and metrics for batch lookup in `OrganizationDirectoryHttpGateway`

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently.

---

## Phase 4: User Story 2 - 系统角色列表纳入角色映射 (Priority: P1)

**Goal**: Use `sysRoleList` as a fallback role mapping source with correct priority and case-insensitive matching.

**Independent Test**: Construct test data where a user's job name does not map to a target role but their `sysRoleList` contains a recognizable role name; verify the user is mapped to the correct internal role.

### Tests for User Story 2

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation.**

- [x] T016 [P] [US2] Add unit tests for `SystemRoleListMapper` in `backend/src/test/java/com/xiyu/bid/integration/organization/domain/policy/SystemRoleListMapperTest.java`
- [x] T017 [P] [US2] Add unit tests for `JobRoleLookupResolver` priority and case-insensitivity in `backend/src/test/java/com/xiyu/bid/integration/organization/domain/policy/JobRoleLookupResolverTest.java`
- [x] T018 [P] [US2] Extend `OrganizationUserSyncWriterTest` to cover `sysRoleList` fallback path in `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriterTest.java`

### Implementation for User Story 2

- [x] T019 [US2] Implement `SystemRoleListMapper` using existing `position-to-role-mappings` configuration with case-insensitive matching
- [x] T020 [US2] Implement `JobRoleLookupResolver` with priority order: person > department > job > sysRoleList
- [x] T021 [US2] Refactor `OrganizationUserSyncWriter` to delegate role resolution to `JobRoleLookupResolver`
- [x] T022 [US2] Ensure backward compatibility for existing person/department/position mappings

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently.

---

## Phase 5: User Story 3 - 同步性能与可观测性 (Priority: P2)

**Goal**: Quantify the batch optimization benefit and ensure operators can trace each batch lookup via logs.

**Independent Test**: Compare external call count between batch and per-user modes for 1,000 users; verify sync logs contain batch lookup summary entries.

### Tests for User Story 3

- [x] T023 [P] [US3] Add log assertion test for batch lookup summary in `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java`
- [x] T024 [P] [US3] Add performance assertion test for batch count vs user count in `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationDirectorySyncAppServiceTest.java`

### Implementation for User Story 3

- [x] T025 [US3] Configure default `batchQuerySize=50` in `OrganizationIntegrationProperties`
- [x] T026 [US3] Add structured log entries for batch lookup: request size, response count, duration, and fallback status
- [x] T027 [US3] Document performance expectation and monitoring query in `specs/001-oss-batch-job-role-sync/quickstart.md`

**Checkpoint**: All user stories should now be independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates, documentation, and final verification.

- [x] T028 [P] Run backend quality gates: `cd backend && mvn -Pjava-quality,java-quality-spotbugs,quality-strict checkstyle:check pmd:check spotbugs:check`
- [x] T029 [P] Run organization module test suite: `cd backend && mvn test -Dtest="com.xiyu.bid.integration.organization.**"` (full suite has pre-existing unrelated failures in tenderupload/collaboration modules)
- [x] T030 Update `docs/generated/db-schema.md` if any schema changed (N/A)
- [x] T031 Update `CLAUDE.md` if any startup command or environment variable changed (N/A)
- [ ] T032 Commit all changes with message: `feat(organization): batch OSS job/role lookup and sysRoleList mapping (#001)`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories.
- **User Stories (Phase 3+)**: All depend on Foundational phase completion.
  - User stories can then proceed in parallel (if staffed).
  - Or sequentially in priority order (P1 → P2 → P3).
- **Polish (Final Phase)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories.
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - Integrates with US1 components but should be independently testable.
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - Builds on US1/US2 but should be independently testable.

### Within Each User Story

- Tests MUST be written and FAIL before implementation.
- Models before services.
- Services before integration.
- Core implementation before integration.
- Story complete before moving to next priority.

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel.
- All Foundational tasks marked [P] can run in parallel (within Phase 2).
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows).
- All tests for a user story marked [P] can run in parallel.
- Models within a story marked [P] can run in parallel.
- Different user stories can be worked on in parallel by different team members.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready.
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!).
3. Add User Story 2 → Test independently → Deploy/Demo.
4. Add User Story 3 → Test independently → Deploy/Demo.
5. Each story adds value without breaking previous stories.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Each user story should be independently completable and testable.
- Verify tests fail before implementing.
- Commit after each task or logical group.
- Stop at any checkpoint to validate story independently.
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence.
