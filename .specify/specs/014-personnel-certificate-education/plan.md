# Implementation Plan: 014 - personnel-certificate-education (新增证书 - Education History Support)

**Feature**: 014-personnel-certificate-education
**Branch**: claude/personnel-certificate-enhancement (task worktree)
**Date**: 2026-05-30
**Scope**: Strictly the first atomic h5 "新增证书" of blueprint 4.3 only. All other 4.3 subsections are explicitly out of scope.

## 1. Summary of the 6-Step Execution Order (from prior detailed analysis)

The user has explicitly defined the recommended startup order for this feature:

1. Create V1013__personnel_education.sql + corresponding rollback script (zero-risk new table)
2. Domain model extension (new Education value object + update Personnel record to include educations list)
3. Command / DTO / Mapper / Service layer adjustments (support multi-education in create path)
4. Controller permission convergence (only bid_admin, bid_lead, bid_specialist)
5. Frontend 3-tab form transformation (基础信息 / 教育经历 / 证书与职称) + dynamic education rows + exact blueprint validation messages
6. Basic verification (manual flows + relevant unit tests)

This plan is derived directly from the authoritative blueprint content (lark-cli 1.0.44 fetch) and the gap analysis in `.omc/research/4.3-新增证书-详细实现方案.md`.

## 2. Technical Context & Constraints

- **Project FP-Java Profile**: Pure core (domain/policy) vs Imperative shell (app service, controller, persistence adapter). Business rules and validation go in pure core.
- **Database**: MySQL 8.0, Flyway. Next version after V1012 is V1013.
- **Frontend**: Vue 3 + Element Plus. Existing Personnel.vue uses flat form + dynamic cert list.
- **Authorization**: Must align with new RoleProfile system (bid_* roles). Legacy ADMIN/MANAGER/STAFF are not sufficient.
- **One small subsection rule**: Only deliver what is required for "新增证书". Do not implement edit, delete, search, reminders, or logs in this iteration.

## 3. Data Model Changes

### New Table (V1013)

See detailed schema in `.omc/research/4.3-新增证书-详细实现方案.md` (personnel_education with proper FK, indexes, columns for school, dates, highest_education, study_form, major).

Rollback: simple DROP TABLE.

### Domain Model

- New value object: `Education` (immutable record or class with the 6 fields + basic invariants).
- `Personnel` record gains `List<Education> educations` (defensive copy, withEducations mutator).
- `Personnel.create(...)` gains educations parameter + validation (at least 1, graduation after enrollment).
- Existing single `education` String field kept for now (backward compat during this sub-iteration only).

## 4. Implementation Phases (mapped to user's 6 steps)

**Phase 1: Database (Step 1 of user plan)**
- Create `backend/src/main/resources/db/migration-mysql/V1013__personnel_education.sql`
- Create corresponding `backend/src/main/resources/db/rollback/migration-mysql/U1013__personnel_education.sql`
- Add JPA entity `PersonnelEducationEntity`
- Add `PersonnelEducationJpaRepository`

**Phase 2: Domain & Persistence (Step 2)**
- `Education.java` value object in domain/valueobject
- Update `Personnel.java` domain model
- Update `PersonnelRepository` port if needed
- Update adapter to load/save educations (new methods or in existing save path)

**Phase 3: Application Layer (Step 3)**
- Update `PersonnelUpsertCommand` (add `List<EducationEntry>`)
- Update `PersonnelDTO` and `CertificateDTO` (add educations)
- Update `PersonnelMapper`
- Update `CreatePersonnelAppService` (handle educations, call domain validation, persist via adapter)

**Phase 4: API & Security (Step 4)**
- Update `PersonnelController.create` method `@PreAuthorize` to the three bid_* roles only (use hasAnyAuthority or project-standard expression).
- Ensure other methods in controller are reviewed but only create is strictly required for this sub.

**Phase 5: Frontend (Step 5)**
- In `src/views/Knowledge/Personnel.vue`:
  - Replace flat form with `<el-tabs>` containing 3 panes matching blueprint labels.
  - Implement repeatable education rows (add/remove, fields per blueprint).
  - Wire exact validation messages on submit (prefer backend for business rules, frontend for instant UX).
  - Post-submit: highlight logic (3s), correct toast text.
- Update `src/api/modules/personnel.js` if payload shape changes.

**Phase 6: Verification (Step 6)**
- Manual flow with bid_specialist account (real backend + frontend).
- Unit tests for domain validation and CreatePersonnelAppService.
- Quick architecture test run.
- Confirm no regression on existing personnel list/create for other roles (document any temporary widening if needed).

## 5. Out of Scope (Explicit)

- Any change to edit, delete, filter, search, expiry reminder rules, operation logs UI, batch import/export.
- Full E2E Playwright tests (basic manual + unit only for this sub).
- Deprecation of legacy single education string (future cleanup).
- Changes to 4.1 资质证书 or any other module.

## 6. Risks & Mitigations

- Education multi-row is a new concept — risk of inconsistency with future "edit" sub-section → mitigated by clear documentation in this plan and implementation-notes.
- Permission change may affect existing tests/dev accounts → use the known e2e/dev accounts and update only as needed for this sub.
- Attachment handling for certificates in Tab 3 already partially exists; we only need to ensure the validation messages match exactly.

## 7. Success Criteria

When this plan is implemented following the 6 steps in order:
- A bid_specialist can successfully add a person using the exact 3-tab flow from the blueprint.
- All exact Chinese validation messages from the blueprint table are returned.
- Only the three designated roles can create.
- Education history is stored in the new table and visible in the domain/DTO.
- No other 4.3 functionality is altered.

---

**Reference Documents**:
- Detailed scheme: `.omc/research/4.3-新增证书-详细实现方案.md`
- Blueprint source: fetched via lark-cli 1.0.44 (section block-id UEl8d4EN9owkpExa6JDcLkbdnJf)

This plan is intentionally narrow so the team can deliver one small, verifiable slice and move to the next h5 subsection only after verification.
