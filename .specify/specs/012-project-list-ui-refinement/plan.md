# Implementation Plan: Project List UI Refinement

**Branch**: `012-project-list-ui-refinement` | **Date**: 2026-05-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification for project list UI label changes, field reordering, and date picker layout fixes. Pure frontend changes, no backend API modifications required.

## Summary

Refine the project list UI by updating field labels to match product blueprint terminology (业主单位 → 招标主体, 投标状态 → 项目状态, 负责人部门 → 项目负责人部门), reordering search filters and table columns per specification, removing the 中标状态 column, fixing date picker label truncation, and correcting project type filter options.

## Technical Context

**Language/Version**: JavaScript (ES2022+), Vue 3.4+

**Primary Dependencies**: Vue 3 Composition API, Element Plus UI library, Pinia store

**Storage**: N/A (frontend-only UI changes, no backend API or database modifications)

**Testing**: Vitest (unit tests via `src/views/Project/List.spec.js`)

**Target Platform**: Web browser (modern browsers, SPA)

**Project Type**: Single-page application (Vue 3 frontend)

**Performance Goals**: No performance impact expected; UI changes only

**Constraints**: Must maintain backward compatibility with existing localStorage column visibility preferences

**Scale/Scope**: 5 files across 1 feature module

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. FP-Java Architecture | N/A | Frontend-only changes, no Java backend |
| II. Real-API Only | PASS | No API changes; frontend label/UI only |
| III. TDD | PASS | Update existing Vitest tests to match new labels |
| IV. Split-First & Simplicity | PASS | Minimal changes across existing composables |
| V. Boring Proven Patterns | PASS | Simple string replacements and reordering |

No Constitution violations detected.

## Project Structure

### Documentation (this feature)

```text
specs/012-project-list-ui-refinement/
├── spec.md              # Feature specification (user stories, requirements)
├── checklists/           # Quality checklists
│   └── requirements.md
├── plan.md              # This file (implementation plan)
├── research.md          # Phase 0 output (this feature)
├── data-model.md        # Phase 1 output (not applicable - no data model)
└── tasks.md             # Phase 2 output (NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/
├── views/
│   └── Project/
│       ├── List.vue                    # Main list page - label updates + column reorder
│       ├── List.spec.js                # Unit tests - update assertions for new labels
│       └── composables/
│           ├── useProjectSearch.js     # Search filters - projectTypeOptions fix + label updates
│           └── useProjectColumns.js    # Column visibility - update labels in columnOptions
```

**Structure Decision**: Single module (Project List) with UI refinements. Changes isolated to frontend view layer. No backend, no new services, no data model changes.

## Complexity Tracking

> No Constitution violations requiring justification.

## Phase 0: Research

### Findings Summary

| Change Type | Current State | Target State | Files to Modify |
|-------------|---------------|--------------|------------------|
| Label: 业主单位 → 招标主体 | "业主单位" in search filter and table | "招标主体" | List.vue, useProjectSearch.js |
| Label: 投标状态 → 项目状态 | "投标状态" in search filter and table | "项目状态" | List.vue |
| Label: 负责人部门 → 项目负责人部门 | "负责人部门" in search filter and table | "项目负责人部门" | List.vue |
| Remove: 中标状态 column | Column visible with v-if toggle | Remove from table and toggle | List.vue, useProjectColumns.js |
| Reorder: 项目部门负责人 before 投标负责人 | Reverse order in search filters | Swap positions | List.vue |
| Reorder: 项目负责人 before 项目部门负责人 | Reverse order in table columns | Swap positions | List.vue |
| Reorder: 项目状态 as last column | Position 3 in table | Move to end | List.vue |
| Fix: Date picker label truncation | Labels may be truncated | Ensure full visibility | List.vue (CSS fixes) |
| Fix: projectTypeOptions | Includes "单项目" | Remove, keep only 5 options | useProjectSearch.js |

### Technical Approach

1. **Label Changes**: Direct string replacement in template labels and column options
2. **Column Removal**: Remove `<el-table-column>` for 中标状态 and remove from `columnOptions` array
3. **Field Reordering**: Move template elements within `<el-form>` and reorder table columns
4. **Date Picker Fix**: Add explicit width/style to prevent label truncation
5. **projectTypeOptions**: Remove SINGLE_PROJECT entry from the array

### Dependencies

- No external dependencies required
- Changes are CSS and string modifications only

## Phase 1: Design & Contracts

### Not Applicable

This is a pure UI/label refinement feature with no:
- Data model changes
- API interface changes
- New entities or relationships
- State transitions

All changes are cosmetic and do not affect data flow or external contracts.

### Implementation Approach

1. **List.vue**:
   - Update `<el-form-item label="...">` for 招标主体, 项目状态, 项目负责人部门
   - Reorder 项目部门负责人 and 投标负责人 form items
   - Reorder table columns: 项目负责人 before 项目部门负责人
   - Move 项目状态 column to end
   - Remove 中标状态 table column
   - Add CSS fix for date picker label visibility

2. **useProjectSearch.js**:
   - Update `projectTypeOptions` to remove SINGLE_PROJECT
   - No label changes needed here (labels come from List.vue)

3. **useProjectColumns.js**:
   - Update `columnOptions` label from "中标状态" to remove it entirely
   - No other changes needed

4. **List.spec.js**:
   - Update test assertions from "业主单位" to "招标主体"
   - Update test assertions from "投标状态" to "项目状态"
   - Update test assertions from "中标状态" to verify removal
   - Update test assertions for "负责人部门" to "项目负责人部门"

### Edge Cases

| Edge Case | Handling |
|-----------|----------|
| Column visibility preferences stored in localStorage | Remove bidResultStatus key from stored preferences on migration (handled by composable default) |
| Active search sessions during reorder | Vue reactivity handles re-render automatically |
| Existing tests expecting old labels | Update test assertions to match new labels |

## Implementation Checklist

- [ ] Update List.vue: Rename labels (业主单位 → 招标主体, 投标状态 → 项目状态, 负责人部门 → 项目负责人部门)
- [ ] Update List.vue: Reorder search filter fields (项目部门负责人 before 投标负责人)
- [ ] Update List.vue: Reorder table columns (项目负责人 before 项目部门负责人, 项目状态 last)
- [ ] Update List.vue: Remove 中标状态 column from table
- [ ] Update List.vue: Fix date picker label CSS
- [ ] Update useProjectSearch.js: Fix projectTypeOptions (remove SINGLE_PROJECT)
- [ ] Update useProjectColumns.js: Remove bidResultStatus from columnOptions
- [ ] Update List.spec.js: Update test assertions for new labels
- [ ] Run Vitest to verify all tests pass
- [ ] Visual verification of UI changes

## Files to Modify

| File | Change Type | Lines Affected (est.) |
|------|-------------|----------------------|
| `src/views/Project/List.vue` | Labels, reordering, removal | ~20-30 |
| `src/views/Project/composables/useProjectSearch.js` | Array update | ~5 |
| `src/views/Project/composables/useProjectColumns.js` | Array update | ~2 |
| `src/views/Project/List.spec.js` | Test assertions | ~10-15 |

Total estimated changes: ~40-50 lines across 4 files.

---

**Plan Generated**: 2026-05-29  
**Feature**: 012-project-list-ui-refinement  
**Status**: Ready for Tasks Generation
