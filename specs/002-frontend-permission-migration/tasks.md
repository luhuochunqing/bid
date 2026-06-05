# Tasks: Frontend Permission Migration

**Input**: Design documents from `/specs/002-frontend-permission-migration/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, quickstart.md

**Tests**: The examples below include test tasks. Tests are OPTIONAL - only include them if explicitly requested in the feature specification.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify existing infrastructure readiness

- [ ] T001 Audit `sidebar-menu.js` for missing `permissionKeys` on routes that currently only have `meta.roles`
- [ ] T002 Verify `userStore.hasPermission()` method works correctly with `menuPermissions` array from backend

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core permission utility that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Create `src/utils/permission.js` with `hasAnyPermission()`, `isAdminRole()`, and `resolveAccessDecision()`
- [ ] T004 [P] Create `src/utils/permission.test.js` with unit tests for all utility functions (boundary cases: empty arrays, 'all' wildcard, null inputs)
- [ ] T005 [P] Audit all `meta.permissionKeys` in `src/config/sidebar-menu.js` and add missing keys (e.g., `settings`, `analytics`)

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Route and Menu Access for New Roles (Priority: P1) 🎯 MVP

**Goal**: Unblock new roles (`bid_admin`, `bid_lead`, `bid_specialist`, `admin_staff`) from being incorrectly denied access by route guards and sidebar filters.

**Independent Test**: Login as `xiaochen` (bid_admin) → navigate to `/settings` → page loads without redirect.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T006 [P] [US1] Add e2e test: `bid_admin` user can access `/settings` route
- [ ] T007 [P] [US1] Add e2e test: `admin_staff` user sees only permitted sidebar menus

### Implementation for User Story 1

- [ ] T008 [US1] Modify `src/router/index.js` `hasRouteAccess()` to check `meta.permissionKeys` when `meta.roles` does not match
- [ ] T009 [US1] Modify `src/router/index.js` `beforeEach` redirect logic: redirect to first permitted route instead of hardcoded `/dashboard` when access denied
- [ ] T010 [US1] Modify `src/components/layout/Sidebar.vue` `filteredMenus` to use `roleOK || permOK` logic
- [ ] T011 [P] [US1] Update `src/config/sidebar-menu.js`: add `permissionKeys` to `/analytics/dashboard` (`['analytics']`) and `/settings` (`['settings']`)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Page-Level Feature Visibility (Priority: P2)

**Goal**: Replace page-level hardcoded role checks with `menuPermissions`-based checks so buttons and actions are correctly visible for new roles.

**Independent Test**: Login as `xiaochen` (bid_admin) → open Project List → "Create Project" button is visible.

### Tests for User Story 2 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T012 [P] [US2] Add unit test: `buildPermissionFlags` returns correct flags based on `menuPermissions` instead of role string
- [ ] T013 [P] [US2] Add e2e test: `bid_admin` sees "Create Project" button on Project List

### Implementation for User Story 2

- [ ] T014 [P] [US2] Refactor `src/views/Bidding/list/helpers.js` `buildPermissionFlags()` to accept `menuPermissions` array instead of role string
- [ ] T015 [P] [US2] Refactor `src/views/Dashboard/Workbench.vue`: remove `role === 'staff'` fallback, use `hasPermission()` only
- [ ] T016 [P] [US2] Refactor `src/views/Project/stages/EvaluationStage.vue`: replace `isManager` (role check) with `hasPermission('project:evaluate')`
- [ ] T017 [P] [US2] Refactor `src/views/System/Settings.vue`: replace `isAdmin` and `canViewAuditLogs` role checks with `hasPermission('settings')` and `hasPermission('audit')`
- [ ] T018 [P] [US2] Refactor `src/components/layout/Header.vue`: replace `isAdmin` checks with permission checks
- [ ] T019 [P] [US2] Refactor `src/views/Knowledge/Qualification/index.vue`: replace `role === 'admin'` with `hasPermission('knowledge:qualification:manage')`
- [ ] T020 [P] [US2] Refactor `src/views/Resource/Expense/index.vue`: replace `role === 'manager'` with `hasPermission('resource:expense:audit')`
- [ ] T021 [P] [US2] Refactor `src/views/Project/ProjectList.vue`: replace `role === 'admin' || role === 'manager'` with `hasPermission('project:create')`

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Legacy Role Cleanup (Priority: P3)

**Goal**: Remove deprecated `meta.roles` from route and sidebar configuration, leaving `menuPermissions` as the single source of truth.

**Independent Test**: Codebase search for `meta.roles` in business logic returns zero results.

### Tests for User Story 3 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T022 [P] [US3] Verify e2e tests still pass for legacy `admin`, `manager`, and `staff` roles after `meta.roles` removal

### Implementation for User Story 3

- [ ] T023 [US3] Remove `meta.roles` from all entries in `src/config/sidebar-menu.js` (keep only `permissionKeys`)
- [ ] T024 [US3] Remove `meta.roles` fallback from `src/router/index.js` `hasRouteAccess()` (check only `permissionKeys`)
- [ ] T025 [US3] Remove `meta.roles` fallback from `src/components/layout/Sidebar.vue` (check only `permissionKeys`)
- [ ] T026 [P] [US3] Evaluate and simplify `src/api/authNormalizer.js`: remove `legacyRole` mapping if no consumers remain
- [ ] T027 [P] [US3] Update `.wiki/pages/roles-and-permissions.md`: document frontend permission model, remove Legacy route guard references

**Checkpoint**: All user stories should now be independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T028 [P] Run `npm run build` and fix any TypeScript/Vite build errors introduced by refactors
- [ ] T029 [P] Run `npm run test:unit` and fix failures
- [ ] T030 [P] Run `npm run test:e2e` and fix failures (focus on role-based journeys)
- [ ] T031 Run `cd backend && mvn test -Dtest=ArchitectureTest` (backend should be unaffected, but verify)
- [ ] T032 Code cleanup: remove any transitional TODO comments and unused imports
- [ ] T033 [P] Update `.wiki/pages/roles-and-permissions.md` §3 (路由级权限) to reflect `permissionKeys`-driven model

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after User Stories 1 and 2 are stable - Removes transitional code

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Utility functions before page components
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, US1 and US2 can start in parallel (if team capacity allows)
- All page-level refactors in US2 marked [P] can run in parallel
- US3 cleanup tasks marked [P] can run in parallel
- All tests for a user story marked [P] can run in parallel
- Polish phase tasks marked [P] can run in parallel

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
   - `xiaochen` (bid_admin) can access `/settings`
   - `xiaoliu` (bid_lead) can access `/project`
   - `xiaozheng` (admin_staff) sees limited sidebar
   - `lizong` (admin) still has full access
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 (route guards + sidebar)
   - Developer B: User Story 2 (page-level refactors - can work in parallel since pages are independent files)
3. Stories complete and integrate independently
4. User Story 3 (cleanup) done by either developer after US1+US2 stable

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
