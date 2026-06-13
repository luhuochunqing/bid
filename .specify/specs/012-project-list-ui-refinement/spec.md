# Feature Specification: Project List UI Refinement

**Feature Branch**: `012-project-list-ui-refinement`

**Created**: 2026-05-29

**Status**: Draft

**Input**: User description: "Project list needs updates: rename 业主单位 to 招标主体, rename 投标状态 to 项目状态, remove 中标状态 from table, update field labels, reorder columns and filter fields, fix date picker display issues"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consistent Field Naming Across UI (Priority: P1)

Users need consistent terminology across the project list search filters and table display to avoid confusion when managing projects.

**Why this priority**: Terminology consistency is fundamental to user understanding and reduces cognitive load when navigating the interface.

**Independent Test**: Can be fully tested by verifying all labels match across search filters, table columns, and reference documentation.

**Acceptance Scenarios**:

1. **Given** the user is on the project list page, **When** viewing the search filters, **Then** the field labeled "业主单位" should display as "招标主体"
2. **Given** the user is on the project list page, **When** viewing the table columns, **Then** the column labeled "业主单位" should display as "招标主体"
3. **Given** the user is on the project list page, **When** viewing the search filters and table, **Then** "负责人部门" should display as "项目负责人部门" in both areas

---

### User Story 2 - Accurate Project Status Display (Priority: P1)

Users need to see project status information accurately in both search filters and table columns, with correct terminology alignment.

**Why this priority**: Status information is critical for project tracking and decision-making; incorrect labels lead to user errors.

**Independent Test**: Can be tested by verifying search filter options and table column labels match the intended terminology.

**Acceptance Scenarios**:

1. **Given** the user is on the project list page, **When** viewing search filters, **Then** "投标状态" should be renamed to "项目状态"
2. **Given** the user is on the project list page, **When** viewing table columns, **Then** "投标状态" should be renamed to "项目状态" and appear as the last column
3. **Given** the user is on the project list page, **When** viewing table columns, **Then** the "中标状态" column should be removed entirely

---

### User Story 3 - Improved Field Layout and Order (Priority: P2)

Users need a logical field ordering in both search filters and table columns to efficiently locate and manage project information.

**Why this priority**: Better field ordering improves workflow efficiency and reduces time spent searching for specific information.

**Independent Test**: Can be tested by verifying the visual order of fields in search filters and columns in the table.

**Acceptance Scenarios**:

1. **Given** the user is on the project list page, **When** viewing the search filters, **Then** "项目部门负责人" should appear before "投标负责人"
2. **Given** the user is on the project list page, **When** viewing the table columns, **Then** "项目负责人" column should appear before "项目部门负责人" column
3. **Given** the user is on the project list page, **When** viewing the table columns, **Then** "项目状态" should be the rightmost column

---

### User Story 4 - Fixed Date Field Display (Priority: P2)

Users need complete visibility of date field labels in search filters to properly configure date range searches.

**Why this priority**: Truncated labels cause confusion and may lead to incorrect filter configuration.

**Independent Test**: Can be tested by verifying all date field labels are fully visible without truncation.

**Acceptance Scenarios**:

1. **Given** the user is on the project list page, **When** viewing the search filters, **Then** "创建时间" label should be fully visible
2. **Given** the user is on the project list page, **When** viewing the search filters, **Then** "开标时间" label should be fully visible

---

### User Story 5 - Correct Project Type Options (Priority: P3)

Users need project type filter options that match the product blueprint specifications.

**Why this priority**: Incorrect options may cause filtering issues and misalignment with business requirements.

**Independent Test**: Can be tested by verifying project type filter contains exactly: 办公/综合/集采/工业品/其他 (without 单项目).

**Acceptance Scenarios**:

1. **Given** the user is on the project list page, **When** viewing the project type filter, **Then** the options should be: 办公, 综合, 集采, 工业品, 其他
2. **Given** the user is on the project list page, **When** viewing the project type filter, **Then** "单项目" option should not appear

---

### Edge Cases

- What happens when table column order is changed with existing user customizations?
- How does the system handle data records that have null values in renamed/removed fields?
- What happens when filter fields are reordered during active search sessions?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST rename "业主单位" to "招标主体" in both search filter and table column labels
- **FR-002**: System MUST rename "投标状态" to "项目状态" in both search filter and table column labels
- **FR-003**: System MUST remove "中标状态" column from the project table display
- **FR-004**: System MUST rename "负责人部门" to "项目负责人部门" in both search filter and table column labels
- **FR-005**: System MUST reorder search filter fields so "项目部门负责人" appears before "投标负责人"
- **FR-006**: System MUST reorder table columns so "项目负责人" appears before "项目部门负责人"
- **FR-007**: System MUST move "项目状态" column to be the last column in the table
- **FR-008**: System MUST ensure "创建时间" and "开标时间" date picker labels are fully visible (not truncated)
- **FR-009**: System MUST update project type filter options to match blueprint: 办公/综合/集采/工业品/其他 (remove 单项目)

### Key Entities *(include if feature involves data)*

- **Project**: Represents a bidding project with attributes including name, bidding entity, status, type, managers, and departments
- **ProjectFilter**: Search filter configuration with field mappings and display labels
- **ProjectTableColumn**: Table column configuration with visibility and ordering settings

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of field labels in search filters match the specification (招标主体, 项目状态, 项目负责人部门)
- **SC-002**: 100% of column headers in table display match the specification (招标主体, 项目状态, 项目负责人部门)
- **SC-003**: "中标状态" column is completely removed from table visibility options
- **SC-004**: Search filter field order matches specification: 项目部门负责人 before 投标负责人
- **SC-005**: Table column order matches specification: 项目负责人 before 项目部门负责人, 项目状态 as last column
- **SC-006**: Project type filter shows exactly 5 options: 办公, 综合, 集采, 工业品, 其他

## Assumptions

- The changes are limited to frontend label updates and field reordering; no backend API changes required
- Column visibility preferences stored in user settings should be preserved during reordering
- Date picker label truncation is a CSS/layout issue that can be fixed with width adjustments
- Project type option changes only affect filter dropdown, not existing data classification
