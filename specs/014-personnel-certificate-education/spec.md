# Feature Specification: Personnel Certificate - Add with Education History (4.3 新增证书 first atomic subsection)

**Feature Branch**: `014-personnel-certificate-education`

**Created**: 2026-05-30

**Status**: Draft

**Input**: Implement the first smallest subsection of blueprint 4.3 "人员证书" — "新增证书". Follow the exact 6-step execution order from prior detailed plan:
1. Create V1013__personnel_education.sql + rollback (zero-risk new table)
2. Domain model extension (Education value object + Personnel.educations list)
3. Command / DTO / Mapper / Service adjustments
4. Controller permission convergence to bid_admin/bid_lead/bid_specialist only
5. Frontend 3-tab form (基础信息 / 教育经历 / 证书与职称) + dynamic education rows + exact validation messages
6. Basic verification (manual + unit)

This is strictly scoped to the "新增证书" h5 only (block-id UEl8d4EN9owkpExa6JDcLkbdnJf). All other 4.3 h5 subsections are out of scope for this feature.

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Bid department staff adds complete personnel archive via 3-tab form (Priority: P1)

As a 投标专员 (or 投标管理员/组长), I can click "+ 新增人员", fill a 3-tab modal (基础信息 + 教育经历 as repeatable rows + 证书与职称 optional), submit with valid data, and have the new person saved with education history persisted, appear in the list (highlighted briefly), with a precise success toast, an operation log entry, and the certificates automatically enrolled for expiry monitoring.

**Why this priority**: This is the atomic entry point for the entire 4.3 "人员证书" module per the product blueprint. Without a working "新增" flow that matches the 3-tab + multi-education design, no other subsection (edit, search, reminders, etc.) can be validated against the spec.

**Independent Test**: Can be fully tested by a bid_specialist logging in, opening the add dialog, filling all 3 tabs with at least one education record + one certificate, submitting, and verifying (a) the person appears in the list, (b) education records are queryable via API/DB, (c) exact error messages appear for bad data, (d) only the three allowed roles can access the endpoint.

**Acceptance Scenarios**:

1. **Given** I am logged in as bid_specialist and on the 人员证书列表 page, **When** I click "+ 新增人员", fill Tab1 (valid unique 工号, name, gender, 入职时间=today-1y, 11-digit phone, valid birth date), Tab2 (two education rows with proper date order and required fields), Tab3 (one certificate with PDF attachment ≤10MB), and click 保存, **Then** the dialog closes, list refreshes, the new row is highlighted for 3 seconds, "新增成功" toast appears, an operation log "新增人员档案 - {工号} {姓名}" is recorded, and the certificate is visible in the expiry monitoring scope.
2. **Given** I submit with duplicate 工号, **When** the request reaches the backend, **Then** I receive the exact message "该工号已存在" and no data is persisted.
3. **Given** Tab2 has zero education rows or a row where 毕业时间 < 入学时间, **When** I submit, **Then** I see the exact message "添加所有教育经历" and the form does not submit.

---

### User Story 2 - Strict role-based access for add operation (Priority: P1)

As the system, only users with RoleProfile bid_admin, bid_lead, or bid_specialist may successfully call the add personnel API. All other roles (including project负责人, 行政人员, cross-department staff, and legacy ADMIN/MANAGER without the new bidding roles) must be rejected with 403.

**Why this priority**: The blueprint explicitly limits this module to the bidding department only. This is a hard security/authorization requirement that must be in place before any data is created.

**Independent Test**: Can be tested by attempting POST /api/knowledge/personnel with tokens for each of the 6+ roles listed in the 4.3 permission matrix and verifying only the three allowed roles succeed while others are forbidden.

**Acceptance Scenarios**:

1. **Given** a user with RoleProfile `bid_specialist`, **When** they POST valid add payload, **Then** 201 Created and person is saved.
2. **Given** a user with RoleProfile `admin_staff` or legacy `MANAGER`, **When** they POST, **Then** 403 Forbidden and no data is written.

---

### User Story 3 - Precise field validation with blueprint-specified Chinese messages (Priority: P2)

As a user filling the 3-tab form, I receive the exact Chinese error messages defined in the blueprint for every validation rule (工号唯一、出生日期不合理、手机格式、教育经历至少1条、附件大小/类型等) so that the UI behavior matches the product spec 1:1.

**Why this priority**: The blueprint is very prescriptive about the exact wording of error messages. This is part of the "一图一" (one blueprint = one implementation) principle for this module.

**Independent Test**: Each validation rule can be triggered in isolation via the form or API and the returned message asserted to be the literal string from the blueprint table.

**Acceptance Scenarios**:
1. Birth date more than 16 years before 入职 time → "出生日期不合理"
2. Certificate attachment >10MB or wrong extension → "仅支持 PDF/JPG/PNG，≤ 10MB"
3. etc. for all rows in the blueprint validation table.

---

### Edge Cases

- Submitting with education rows that have future dates or malformed dates.
- Concurrent creation of two people with the same 工号 (race condition on uniqueness).
- Uploading a certificate attachment that is exactly 10MB vs 10MB+1 byte.
- Adding a person with no certificates at all (Tab 3 empty is allowed).
- Education records where start_date == end_date (is this allowed per blueprint? needs clarification in later sub).

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- What happens when [boundary condition]?
- How does system handle [error scenario]?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: The "新增人员" entry point MUST be a modal with exactly three tabs in this order: 基础信息, 教育经历, 证书与职称 (as defined in the 4.3 blueprint for the "新增证书" h5).
- **FR-002**: Tab 2 (教育经历) MUST support adding, editing, and removing multiple education records (at least one required). Each record MUST contain: 毕业学校名称 (required), 入学时间 (year-month, required), 毕业时间 (year-month, required), 最高学历 (enum, required), 学习形式 (enum, required), 专业 (optional).
- **FR-003**: On submit, the system MUST enforce all validation rules from the blueprint table with the **exact Chinese error messages** specified (e.g. "该工号已存在", "出生日期不合理", "添加所有教育经历", "仅支持 PDF/JPG/PNG，≤ 10MB").
- **FR-004**: Only users whose active RoleProfile is one of `bid_admin`, `bid_lead`, or `bid_specialist` MAY successfully call the add personnel endpoint. All other roles MUST receive 403.
- **FR-005**: After successful creation, the UI MUST: auto-refresh the list, highlight the new row for 3 seconds, show toast "新增成功", and record an operation log entry with the exact format "新增人员档案 - {工号} {姓名}".
- **FR-006**: Any certificates provided during creation MUST be automatically included in the certificate expiry monitoring scope.
- **FR-007**: The backend MUST persist education records in a new dedicated table (`personnel_education`) with proper foreign key to `personnel`, supporting multiple rows per person.
- **FR-008**: The `Personnel` aggregate root (domain model) MUST support a list of `Education` value objects (in addition to the existing single-string `education` field for backward compatibility during this iteration).

*Clarifications needed (to be resolved in clarify step or next sub-iteration):*
- **FR-009**: Exact behavior when education start_date == end_date (allowed or not per blueprint?) — [NEEDS CLARIFICATION]
- **FR-010**: Whether the legacy single `education` string field on `personnel` table should be deprecated in this iteration or left for a later cleanup sub-section.

### Key Entities *(include if feature involves data)*

- **[Entity 1]**: [What it represents, key attributes without implementation]
- **[Entity 2]**: [What it represents, relationships to other entities]

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: [Measurable metric, e.g., "Users can complete account creation in under 2 minutes"]
- **SC-002**: [Measurable metric, e.g., "System handles 1000 concurrent users without degradation"]
- **SC-003**: [User satisfaction metric, e.g., "90% of users successfully complete primary task on first attempt"]
- **SC-004**: [Business metric, e.g., "Reduce support tickets related to [X] by 50%"]

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- [Assumption about target users, e.g., "Users have stable internet connectivity"]
- [Assumption about scope boundaries, e.g., "Mobile support is out of scope for v1"]
- [Assumption about data/environment, e.g., "Existing authentication system will be reused"]
- [Dependency on existing system/service, e.g., "Requires access to the existing user profile API"]
