# Research: Project List UI Refinement

**Feature**: 012-project-list-ui-refinement  
**Date**: 2026-05-29

## Research Questions

### Q1: What files contain the current field labels?

**Decision**: `List.vue` contains all search filter labels and table column headers. Composable files provide option arrays.

**Rationale**: Analysis of `src/views/Project/List.vue` shows:
- Search filter labels defined inline in template `<el-form-item label="...">`
- Table column headers defined in `<el-table-column label="...">`
- Option arrays (statusOptions, projectTypeOptions) imported from composables

### Q2: How to handle column visibility localStorage during 中标状态 removal?

**Decision**: Remove `bidResultStatus` from `columnOptions` array. Existing localStorage entries will be ignored as the key is no longer used.

**Rationale**: `useProjectColumns.js` loads column visibility from localStorage keyed by user ID. Removing the entry from `columnOptions` means it won't appear in the toggle dropdown. The localStorage data is not harmful (unused key) and clearing it adds unnecessary complexity.

### Q3: How to fix date picker label truncation?

**Decision**: Apply explicit width styling to date picker elements similar to `TenderSearchCard.vue` pattern.

**Rationale**: `TenderSearchCard.vue` demonstrates a working solution with:
- Fixed width fields (`.search-field--date { flex: 0 0 260px }`)
- Date picker fills container width (`filter-date-picker { width: 100% }`)

Current `List.vue` uses `el-date-picker` without explicit width, causing labels to truncate in narrow containers.

### Q4: What is the correct project type options order?

**Decision**: Per product blueprint: 办公, 综合, 集采, 工业品, 其他 (without 单项目)

**Rationale**: The spec requirement states exact 5 options without SINGLE_PROJECT. Current `useProjectSearch.js` has incorrect options including SINGLE_PROJECT (value: 'SINGLE_PROJECT', label: '单项目').

## Alternatives Considered

### Label Changes
- **Alternative**: Use i18n for all labels
- **Rejected**: Overkill for one-time rename; current hardcoded labels are acceptable for this scope

### Column Reordering
- **Alternative**: Store column order in localStorage
- **Rejected**: Unnecessary complexity; hardcoded order is sufficient

### Date Picker Fix
- **Alternative**: Use CSS `white-space: nowrap`
- **Rejected**: May cause horizontal overflow; width-based solution is more robust

## Findings

### Current State Analysis

| Component | Current Labels | Issues |
|-----------|---------------|--------|
| Search Filter - Unit | 业主单位 | Needs rename to 招标主体 |
| Search Filter - Status | 投标状态 | Needs rename to 项目状态 |
| Search Filter - Department | 负责人部门 | Needs rename to 项目负责人部门 |
| Table - Unit | 业主单位 | Needs rename to 招标主体 |
| Table - Status | 投标状态 | Needs rename to 项目状态 |
| Table - Result | 中标状态 | Needs complete removal |
| Table - Department | 负责人部门 | Needs rename to 项目负责人部门 |
| Filter - Project Type | Includes 单项目 | Should not include SINGLE_PROJECT |
| Date Pickers | 创建时间, 开标时间 | Labels may truncate |

### Reference Implementation

`TenderSearchCard.vue` provides good patterns for:
- Fixed-width search fields with flex layout
- Date picker width handling
- Consistent form styling

### Test Coverage

Current `List.spec.js` contains assertions for:
- `label="业主单位"` - needs update to "招标主体"
- `label="投标状态"` - needs update to "项目状态"
- `label="中标状态"` - needs update to verify removal

---

**Research Completed**: 2026-05-29
