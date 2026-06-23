# Tasks: 移除 staff 角色并统一用户启用状态为 OSS

**Input**: Design documents from `/specs/003-remove-staff-unify-oss-enabled/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Included. Follow TDD: write tests first, ensure they fail, then implement.

**Organization**: Tasks grouped by user story to enable independent implementation and testing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create task branch, run main-forward sync, reserve migration version, acquire locks.

- [ ] T001 Run `./scripts/sync-env.sh .` and `./scripts/who-touches.sh` on affected paths
- [ ] T002 Create task branch `agent/codex/003-remove-staff-unify-oss-enabled` from `origin/main`
- [ ] T003 Reserve next Flyway migration version via `scripts/next-migration-version.sh --reserve`
- [ ] T004 Acquire hot-path locks for `backend/src/main/java/com/xiyu/bid/entity/User.java`, `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`, `backend/src/main/java/com/xiyu/bid/service/AuthService.java`, `backend/src/main/java/com/xiyu/bid/crm/application/OssLoginFlowService.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Role whitelist contract, staff removal from core entities, and migration baseline.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T005 [P] Create `LoginRoleWhitelist` pure core in `backend/src/main/java/com/xiyu/bid/auth/domain/LoginRoleWhitelist.java` with 7 allowed role codes
- [ ] T006 [P] Create `RoleCode` value object / validator in `backend/src/main/java/com/xiyu/bid/auth/domain/ValidRoleCode.java` to reject staff and unknown roles
- [ ] T007 Remove `STAFF` from `User.Role` enum in `backend/src/main/java/com/xiyu/bid/entity/User.java`
- [ ] T008 Remove `STAFF_CODE` and staff seed from `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java`
- [ ] T009 Update `OrganizationSyncPolicy` in `backend/src/main/java/com/xiyu/bid/integration/organization/domain/OrganizationSyncPolicy.java` to return `Optional.empty()` instead of staff fallback
- [ ] T010 Update `JobRoleLookupResolver` in `backend/src/main/java/com/xiyu/bid/integration/organization/domain/policy/JobRoleLookupResolver.java` to remove staff fallback path
- [ ] T011 Write Flyway migration `backend/src/main/resources/db/migration-mysql/V{reserved}__migrate_staff_users.sql` to disable existing staff users and clear role_code
- [ ] T012 Write rollback script `backend/src/main/resources/db/rollback/migration-mysql/V{reserved}__migrate_staff_users.sql`

**Checkpoint**: Foundation ready - staff is no longer a valid role anywhere in the system; migration script is in place.

---

## Phase 3: User Story 1 - 普通员工无法登录系统 (Priority: P1) 🎯 MVP

**Goal**: OSS users whose role is not in the 7-role whitelist cannot log in; staff is rejected explicitly.

**Independent Test**: `mvn test -Dtest=AuthServiceTest,OssLoginFlowServiceTest` plus manual login attempt with unmapped OSS role.

### Tests for User Story 1

- [ ] T013 [P] [US1] Add unit test in `backend/src/test/java/com/xiyu/bid/auth/domain/LoginRoleWhitelistTest.java` asserting staff and unknown roles are rejected
- [ ] T014 [P] [US1] Add test in `backend/src/test/java/com/xiyu/bid/service/AuthServiceTest.java`: OSS user with unmapped role receives `ROLE_NOT_AUTHORIZED`
- [ ] T015 [P] [US1] Add test in `backend/src/test/java/com/xiyu/bid/crm/application/OssLoginFlowServiceTest.java`: login refreshes OSS role and rejects staff
- [ ] T016 [P] [US1] Add contract test for `POST /api/auth/sessions` returning 403 with `ROLE_NOT_AUTHORIZED` for unmapped role

### Implementation for User Story 1

- [ ] T017 [US1] Integrate `LoginRoleWhitelist` into `AuthService.loginWithoutPassword` in `backend/src/main/java/com/xiyu/bid/service/AuthService.java`
- [ ] T018 [US1] Update `OssLoginFlowService.refreshPermissionCache` in `backend/src/main/java/com/xiyu/bid/crm/application/OssLoginFlowService.java` to reject staff role and clear invalid cache
- [ ] T019 [US1] Update `AuthController` in `backend/src/main/java/com/xiyu/bid/controller/AuthController.java` to return distinct error codes (`ROLE_NOT_AUTHORIZED`, `ACCOUNT_DISABLED`, `AUTHENTICATION_FAILED`)
- [ ] T020 [US1] Update `UserDetailsServiceImpl` in `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java` to reject users with null/invalid roleCode
- [ ] T021 [US1] Remove staff role checks/branches from backend security expressions and `@PreAuthorize` annotations

**Checkpoint**: At this point, staff/unmapped OSS users cannot log in; tests verify distinct error responses.

---

## Phase 4: User Story 2 - 用户启用状态完全由 OSS 决定 (Priority: P1)

**Goal**: `User.enabled` for OSS users is driven solely by OSS sync; local admin toggle is removed/hidden.

**Independent Test**: `mvn test -Dtest=OrganizationUserSyncWriterTest,UserEnabledDetectorTest` plus verify local admin cannot toggle OSS user enabled state.

### Tests for User Story 2

- [ ] T022 [P] [US2] Add test in `backend/src/test/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriterTest.java`: unmapped OSS user is created/synced with `enabled=false` and `roleCode=null`
- [ ] T023 [P] [US2] Add test in `backend/src/test/java/com/xiyu/bid/integration/organization/infrastructure/client/UserEnabledDetectorTest.java`: inactive employee status sets `enabled=false`
- [ ] T024 [P] [US2] Add test in `backend/src/test/java/com/xiyu/bid/service/AdminUserServiceTest.java`: `updateStatus` throws when called on OSS-synced user

### Implementation for User Story 2

- [ ] T025 [US2] Update `OrganizationUserSyncWriter.applyRole` in `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java` to set `enabled=false` and `roleCode=null` when role is unmapped
- [ ] T026 [US2] Update `OrganizationUserSyncWriter.applyEnabled` in `backend/src/main/java/com/xiyu/bid/integration/organization/application/OrganizationUserSyncWriter.java` to ensure enabled is only set from OSS detector
- [ ] T027 [US2] Guard `AdminUserService.updateStatus` in `backend/src/main/java/com/xiyu/bid/service/AdminUserService.java` to reject changes for OSS-synced users (`externalOrgSourceApp != null`)
- [ ] T028 [US2] Update `AuthService.login` in `backend/src/main/java/com/xiyu/bid/service/AuthService.java` to refresh local `enabled` from OSS during login
- [ ] T029 [US2] Update `OssLoginFlowService` to write back refreshed `enabled` and `roleCode` to `User` entity after OSS auth

**Checkpoint**: OSS-synced user enabled state is read-only locally; sync and login both enforce OSS as source of truth.

---

## Phase 5: User Story 3 - 选人控件统一返回启用用户 (Priority: P2)

**Goal**: All user picker endpoints return `enabled=true` users without role filtering.

**Independent Test**: `mvn test -Dtest=TenderAssignmentQueryServiceTest,TaskAssignmentSupportTest,UserSearchServiceTest` and manual picker verification.

### Tests for User Story 3

- [ ] T030 [P] [US3] Add test in `backend/src/test/java/com/xiyu/bid/batch/service/TenderAssignmentQueryServiceTest.java`: disabled user is excluded; enabled user with any valid role is included
- [ ] T031 [P] [US3] Add test in `backend/src/test/java/com/xiyu/bid/task/service/TaskAssignmentSupportTest.java`: disabled user is excluded; role filter is optional and not default
- [ ] T032 [P] [US3] Add test in `backend/src/test/java/com/xiyu/bid/mention/service/UserSearchServiceTest.java`: search returns enabled users regardless of role

### Implementation for User Story 3

- [ ] T033 [US3] Verify `TenderAssignmentQueryService.getCandidates` in `backend/src/main/java/com/xiyu/bid/batch/service/TenderAssignmentQueryService.java` only filters by `enabled=true`
- [ ] T034 [US3] Verify `TaskAssignmentSupport.getAssignmentCandidates` in `backend/src/main/java/com/xiyu/bid/task/service/TaskAssignmentSupport.java` only filters by `enabled=true` unless explicit role param provided
- [ ] T035 [US3] Verify `UserSearchService.search` in `backend/src/main/java/com/xiyu/bid/mention/service/UserSearchService.java` uses `searchActiveUsers` (enabled + keyword)
- [ ] T036 [US3] Update any remaining picker queries in repository layer to ensure they join/filter on `enabled=true` and do not exclude valid roles

**Checkpoint**: All pickers consistently return enabled users; no role-based filtering in default picker APIs.

---

## Phase 6: User Story 4 - 系统保留账号不受 OSS 同步影响 (Priority: P2)

**Goal**: Local system accounts and E2E/demo accounts remain login-capable and unaffected by OSS sync.

**Independent Test**: `mvn test -Dtest=DefaultAdminInitializerTest,LocalDevAccountInitializerTest,E2eDemoDataInitializerTest` and local-only startup login.

### Tests for User Story 4

- [ ] T037 [P] [US4] Add test in `backend/src/test/java/com/xiyu/bid/bootstrap/DefaultAdminInitializerTest.java`: admin account remains enabled without OSS
- [ ] T038 [P] [US4] Update `backend/src/test/java/com/xiyu/bid/bootstrap/E2eDemoDataInitializerTest.java`: replace `xiaowang` staff role with `admin_staff`
- [ ] T039 [P] [US4] Add test in `backend/src/test/java/com/xiyu/bid/service/AuthServiceTest.java`: local admin login bypasses OSS role whitelist

### Implementation for User Story 4

- [ ] T040 [US4] Ensure `DefaultAdminInitializer` in `backend/src/main/java/com/xiyu/bid/bootstrap/DefaultAdminInitializer.java` seeds `admin` with `enabled=true`
- [ ] T041 [US4] Ensure `LocalDevAccountInitializer` in `backend/src/main/java/com/xiyu/bid/bootstrap/LocalDevAccountInitializer.java` seeds valid roles and keeps `enabled=true`
- [ ] T042 [US4] Update `E2eDemoDataInitializer` in `backend/src/main/java/com/xiyu/bid/bootstrap/E2eDemoDataInitializer.java` to change `xiaowang` role from `staff` to `admin_staff`
- [ ] T043 [US4] Update `AuthService.login` logic to skip OSS role whitelist for local accounts (`externalOrgSourceApp == null`)
- [ ] T044 [US4] Ensure `OrganizationUserSyncWriter` does not overwrite or disable local-init users (`externalOrgSourceApp == null`)

**Checkpoint**: Local accounts and E2E demo accounts still work; xiaowang is no longer staff.

---

## Phase 7: Frontend Cleanup (Cross-Cutting)

**Purpose**: Remove staff from UI, role labels, permission checks, and admin toggles.

### Tests for Frontend

- [ ] T045 [P] [US1] Update or remove frontend unit tests referencing `staff` in `src/**/__tests__/**/*.test.js`
- [ ] T046 [P] [US2] Add/update test for `RoleManagementPanel.vue` to assert OSS user enable toggle is not rendered

### Implementation for Frontend

- [ ] T047 [P] [US1] Remove `staff` branches from `src/router/permissions.js` and `src/utils/workbench-role-core.js`
- [ ] T048 [P] [US1] Remove `staff` role label/option from `src/views/System/settings/RoleManagementPanel.vue`
- [ ] T049 [P] [US2] Disable or remove "启用/停用" toggle for OSS-synced users in `src/views/System/settings/RoleManagementPanel.vue`
- [ ] T050 [P] [US3] Verify user picker components do not filter by role in `src/components/common/UserPicker.vue` and related picker components
- [ ] T051 [P] [US1] Remove `staff` references from `src/stores/user.js` and any role-based rendering
- [ ] T052 [P] [US1] Search and remove all remaining `staff` literals in `src/` (role labels, permissions, constants)

**Checkpoint**: Frontend no longer references staff; admin cannot toggle OSS user enabled state; pickers do not role-filter.

---

## Phase 8: E2E & Test Cleanup (Cross-Cutting)

**Purpose**: Ensure E2E and integration tests align with new model.

- [ ] T053 [P] Update E2E login tests in `e2e/tests/` to assert staff/unmapped user cannot log in
- [ ] T054 [P] Replace `xiaowang` staff usage in `e2e/tests/` with `admin_staff` or other valid role
- [ ] T055 [P] Remove or update backend tests that assert staff-specific behavior across `backend/src/test/java/`
- [ ] T056 Run `mvn test -Dtest=ArchitectureTest` and fix any new violations
- [ ] T057 Run `npm run check:front-data-boundaries`, `npm run check:doc-governance`, `npm run check:line-budgets`
- [ ] T058 Run `npm run test:unit`
- [ ] T059 Run `npm run build`

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, documentation, and PR preparation.

- [ ] T060 Update `CLAUDE.md` and `AGENTS.md` if role/permission behavior changes require doc updates
- [ ] T061 Update `docs/` if role matrix or API docs mention staff
- [ ] T062 Run full backend test suite `cd backend && mvn test`
- [ ] T063 Run full E2E smoke if applicable: `npm run test:e2e`
- [ ] T064 Verify migration applies cleanly on fresh and existing databases
- [ ] T065 Run `bash scripts/check-git-wrapper.sh` and `npm run agent:lock-check:changed`
- [ ] T066 Create PR with description referencing spec, plan, and manual verification results

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Depends on Setup. Blocks all user stories.
- **User Stories (Phase 3-6)**: Depend on Foundational. Can run in parallel after foundation (US1/US2 are P1 and may need to be first).
- **Frontend Cleanup (Phase 7)**: Depends on Foundational + US1/US2 (to know staff is removed and enabled is read-only).
- **E2E & Test Cleanup (Phase 8)**: Depends on all implementation phases.
- **Polish (Phase 9)**: Depends on all prior phases.

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories.
- **US2 (P1)**: No dependencies on other stories; can run in parallel with US1 after foundation.
- **US3 (P2)**: Depends on US2 (enabled semantics stable).
- **US4 (P2)**: Can run in parallel with US1-US3 after foundation.

### Parallel Opportunities

- All Foundational entity/policy changes marked [P] can run in parallel.
- US1 and US2 test writing can run in parallel.
- Frontend cleanup tasks (T047-T052) can run in parallel.
- E2E/test cleanup tasks (T053-T055) can run in parallel.

---

## Implementation Strategy

### MVP First (US1 + Foundational)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1): staff cannot log in.
3. **STOP and VALIDATE**: Run `mvn test -Dtest=AuthServiceTest,OssLoginFlowServiceTest,LoginRoleWhitelistTest`.
4. Then proceed to US2, US3, US4, frontend, and cleanup.

### Incremental Delivery

- Each user story is independently testable.
- Frontend cleanup can follow backend completion.
- E2E cleanup is the final integration checkpoint.

### Risk Mitigation

- The migration script disables staff users; run it on a staging copy first.
- JWT sessions held by staff users will be rejected on next request after deployment.
- Communicate to admins that "启用/停用" toggle is removed for OSS users.
