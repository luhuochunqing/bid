# Tasks: Project List UI Refinement

**Input**: Design documents from `/specs/012-project-list-ui-refinement/`

**Prerequisites**: plan.md (✓), spec.md (✓), research.md (✓)

**Feature Branch**: `012-project-list-ui-refinement`

**Scope**: Pure frontend UI refinements - label renames, field reordering, column removal, CSS fixes

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Composable Updates)

**Purpose**: Update composables that are imported by List.vue - must complete before UI changes

> **NOTE**: No Setup or Infrastructure phases needed - this is a pure UI refinement with no new dependencies

### Composables Update (US1, US2, US5)

| File | Changes |
|------|---------|
| `src/views/Project/composables/useProjectSearch.js` | Fix projectTypeOptions (remove SINGLE_PROJECT) |
| `src/views/Project/composables/useProjectColumns.js` | Remove 中标状态 from columnOptions |

### Tasks

- [ ] T001 [P] [US5] Remove SINGLE_PROJECT from projectTypeOptions array in `src/views/Project/composables/useProjectSearch.js`
- [ ] T002 [P] [US2] Remove bidResultStatus entry from columnOptions array in `src/views/Project/composables/useProjectColumns.js`

**Checkpoint**: Composables updated - safe to proceed with List.vue changes

---

## Phase 2: User Story 1 - Consistent Field Naming (Priority: P1) 🎯 MVP

**Goal**: Rename "业主单位" to "招标主体" and "负责人部门" to "项目负责人部门" in both search filters and table columns

**Independent Test**: Verify all labels display correctly across search filters, table columns, and match spec terminology

### Tests (OPTIONAL - per spec requirements)

> **NOTE**: Update existing tests to reflect new labels (TDD approach)

- [ ] T003 [P] [US1] Update test assertion for "业主单位" → "招标主体" in `src/views/Project/List.spec.js`
- [ ] T004 [P] [US1] Update test assertion for "负责人部门" → "项目负责人部门" in `src/views/Project/List.spec.js`

### Implementation

- [ ] T005 [US1] Rename "业主单位" to "招标主体" in search filter form item label in `src/views/Project/List.vue`
- [ ] T006 [US1] Rename "业主单位" to "招标主体" in table column header in `src/views/Project/List.vue`
- [ ] T007 [US1] Rename "负责人部门" to "项目负责人部门" in search filter form item label in `src/views/Project/List.vue`
- [ ] T008 [US1] Rename "负责人部门" to "项目负责人部门" in table column header in `src/views/Project/List.vue`

**Checkpoint**: US1 complete - field labels match spec terminology

---

## Phase 3: User Story 2 - Accurate Project Status (Priority: P1)

**Goal**: Rename "投标状态" to "项目状态" and remove "中标状态" column from table

**Independent Test**: Verify search filter shows "项目状态" and table has no "中标状态" column

### Tests

- [ ] T009 [P] [US2] Update test assertion for "投标状态" → "项目状态" in `src/views/Project/List.spec.js`
- [ ] T010 [P] [US2] Remove test assertion for "中标状态" column in `src/views/Project/List.spec.js`

### Implementation

- [ ] T011 [US2] Rename "投标状态" to "项目状态" in search filter form item label in `src/views/Project/List.vue`
- [ ] T012 [US2] Rename "投标状态" to "项目状态" in table column header in `src/views/Project/List.vue`
- [ ] T013 [US2] Remove `<el-table-column>` for "中标状态" from table template in `src/views/Project/List.vue`

**Checkpoint**: US2 complete - status labels correct, column removed

---

## Phase 4: User Story 3 - Improved Field Layout (Priority: P2)

**Goal**: Reorder search filter fields and table columns per specification

**Independent Test**: Verify visual order matches spec: 项目部门负责人 before 投标负责人 (search), 项目负责人 before 项目部门负责人 (table), 项目状态 as last column

### Search Filter Reordering

- [ ] T014 [US3] Reorder search filter: move "项目部门负责人" before "投标负责人" in `src/views/Project/List.vue`

### Table Column Reordering

- [ ] T015 [US3] Reorder table columns: move "项目负责人" before "项目部门负责人" in `src/views/Project/List.vue`
- [ ] T016 [US3] Move "项目状态" column to be the last column in `src/views/Project/List.vue`

**Checkpoint**: US3 complete - field order matches spec

---

## Phase 5: User Story 4 - Fixed Date Field Display (Priority: P2)

**Goal**: Ensure date picker labels are fully visible without truncation

**Independent Test**: Verify "创建时间" and "开标时间" labels display completely

### Implementation

- [ ] T017 [US4] Apply CSS fix for date picker label visibility in `src/views/Project/List.vue` (add explicit width similar to TenderSearchCard.vue pattern)

**Checkpoint**: US4 complete - date picker labels fully visible

---

## Phase 6: Polish & Verification

**Purpose**: Final verification and test validation

### Final Tests

- [ ] T018 Run Vitest to verify all tests pass with new labels in `src/views/Project/List.spec.js`

### Verification Tasks

- [ ] T019 [P] Visual verification: Check search filter labels display correctly (招标主体, 项目状态, 项目负责人部门)
- [ ] T020 [P] Visual verification: Check table column headers display correctly (招标主体, 项目状态, 项目负责人部门)
- [ ] T021 [P] Visual verification: Verify "中标状态" column is completely removed from table
- [ ] T022 [P] Visual verification: Check field order matches spec (search filters and table columns)
- [ ] T023 [P] Visual verification: Check date picker labels are fully visible without truncation
- [ ] T024 [P] Visual verification: Check project type filter shows 5 options (办公, 综合, 集采, 工业品, 其他) without 单项目

**Checkpoint**: All verifications complete - feature ready for PR

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Composables)**: No dependencies - can start immediately
- **Phase 2-5 (User Stories)**: All depend on Phase 1 completion
- **Phase 6 (Polish)**: Depends on all user stories being complete

### Within Each User Story

- Tests marked [P] can run in parallel
- Implementation tasks in the order listed
- Complete story before moving to next priority

### Parallel Opportunities

```
T003 || T004   # US1 tests can run in parallel
T009 || T010   # US2 tests can run in parallel
T019 || T020 || T021 || T022 || T023 || T024  # All visual verifications can run in parallel
```

---

## Implementation Strategy

### Recommended Order

1. **Phase 1**: Update composables first (T001, T002)
2. **Phase 2-5**: Implement user stories in order
3. **Phase 6**: Run tests and verify

### MVP Scope

**User Story 1 (US1)** is the MVP - rename field labels for core terminology consistency. This delivers immediate user value and can be deployed independently.

---

## Summary

| Metric | Value |
|--------|-------|
| **Total Tasks** | 24 |
| **Parallelizable Tasks** | 14 |
| **User Stories** | 5 |
| **MVP Tasks** | 8 (US1 only) |
| **Files to Modify** | 4 |
| **Lines Estimated** | ~40-50 |

### Task Count by User Story

| User Story | Tasks | Priority |
|------------|-------|----------|
| US1 - Consistent Field Naming | T003-T008 | P1 |
| US2 - Accurate Project Status | T002, T009-T013 | P1 |
| US3 - Improved Field Layout | T014-T016 | P2 |
| US4 - Fixed Date Field Display | T017 | P2 |
| US5 - Correct Project Type Options | T001 | P3 |
| Polish & Verification | T018-T024 | - |

---

## Files Reference

| File | Purpose |
|------|---------|
| `src/views/Project/List.vue` | Main list page - labels, reordering, column removal, CSS |
| `src/views/Project/composables/useProjectSearch.js` | projectTypeOptions fix |
| `src/views/Project/composables/useProjectColumns.js` | columnOptions update |
| `src/views/Project/List.spec.js` | Unit tests - update assertions |
| `src/views/Bidding/list/components/TenderSearchCard.vue` | Reference for CSS pattern |

---

**Tasks Generated**: 2026-05-29
**Feature**: 012-project-list-ui-refinement
**Status**: Ready for Implementation
