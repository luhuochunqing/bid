# Tasks: 014 - personnel-certificate-education (新增证书)

**Feature**: 014-personnel-certificate-education
**Input**: The 6-step execution order defined by the user + detailed plan in plan.md + blueprint requirements for the "新增证书" h5 only.

This task list is intentionally narrow. Every task below is required for the first atomic subsection only. No work on edit, delete, search, reminders, or logs.

## Phase 1: Database Foundation (User Step 1)

- [ ] 1.1 Create Flyway migration V1013__personnel_education.sql with the full table definition (personnel_id FK, school_name, start_date, end_date, highest_education, study_form, major, timestamps, indexes) in `backend/src/main/resources/db/migration-mysql/`
- [ ] 1.2 Create matching rollback script U1013__personnel_education.sql (DROP TABLE) in the rollback directory
- [ ] 1.3 Add `PersonnelEducationEntity.java` (JPA entity mirroring the table, with proper relationships/annotations)
- [ ] 1.4 Add `PersonnelEducationJpaRepository.java` (basic findByPersonnelId, etc.)
- [ ] 1.5 Run `./mvnw flyway:info` or equivalent locally to verify the migration is recognized (no execution yet in this task)

## Phase 2: Domain Model Extension (User Step 2)

- [ ] 2.1 Create `Education.java` value object in `domain/valueobject/` (immutable record/class with the 6 fields + basic validation helpers)
- [ ] 2.2 Update `Personnel.java` domain record: add `List<Education> educations`, update constructor/factory, add `withEducations(...)` method, add invariant check for "at least one education record with graduation after enrollment"
- [ ] 2.3 Update `PersonnelRepository` port interface if new methods are required for loading/saving educations
- [ ] 2.4 Update the persistence adapter (`PersonnelRepositoryAdapter`) to handle the new educations collection (insert/update on save path)

## Phase 3: Application & Mapping Layer (User Step 3)

- [ ] 3.1 Extend `PersonnelUpsertCommand` with `List<EducationEntry>` (nested record mirroring the fields)
- [ ] 3.2 Extend `PersonnelDTO` (and any related response DTOs) to carry educations list
- [ ] 3.3 Update `PersonnelMapper` to convert between Command ↔ Domain ↔ DTO for the education collection
- [ ] 3.4 Modify `CreatePersonnelAppService.create(...)`:
  - Map incoming education entries
  - Call domain factory/validation
  - Persist via updated repository/adapter
  - Ensure certificates (existing) + new educations are handled in the same transaction

## Phase 4: API Security Hardening (User Step 4)

- [ ] 4.1 Update `PersonnelController.create(...)` `@PreAuthorize` annotation to allow only the three bidding roles (`bid_admin`, `bid_lead`, `bid_specialist`) using the project's standard role expression
- [ ] 4.2 Verify (via test or manual) that other roles (project负责人, 行政人员, legacy MANAGER without bid_* , etc.) receive 403 on POST /api/knowledge/personnel
- [ ] 4.3 (Optional but recommended) Add a brief comment in the controller referencing the 4.3 blueprint permission matrix

## Phase 5: Frontend 3-Tab Form + Exact Validations (User Step 5)

- [ ] 5.1 In `src/views/Knowledge/Personnel.vue`, convert the existing flat `<el-dialog>` form into `<el-tabs>` with three panes exactly matching blueprint labels: 基础信息, 教育经历, 证书与职称
- [ ] 5.2 Implement repeatable education rows in Tab 2 (add/remove buttons, all fields from blueprint: school, start/end dates as year-month pickers or inputs, highest_education select, study_form select, major)
- [ ] 5.3 Wire frontend basic required/ format checks; ensure backend returns the exact Chinese strings from the blueprint validation table on error (display them directly)
- [ ] 5.4 Implement post-submit UX: list refresh + temporary row highlight (3 seconds), correct "新增成功" toast
- [ ] 5.5 (Stretch for this sub) Ensure certificate attachments in Tab 3 still work and respect the 10MB / PDF-JPG-PNG rule with the exact error message

## Phase 6: Verification (User Step 6)

- [ ] 6.1 Manual happy path: using a real `bid_specialist` (or equivalent dev account), create a person via the new 3-tab UI with 2 education rows + 1 certificate. Verify list appearance, highlight, toast, DB state (personnel + personnel_education + personnel_certificate rows), and that the certificate appears in expiry-related queries.
- [ ] 6.2 Negative validation tests (unit or manual): duplicate 工号, missing education rows, bad birth date, oversized/wrong-type attachment — assert exact messages.
- [ ] 6.3 Permission test: confirm non-bidding roles are blocked at the API level.
- [ ] 6.4 Run relevant backend unit tests + `mvn test -Dtest=ArchitectureTest` (or the personnel-related slice) with no new violations.
- [ ] 6.5 Frontend build: `npm run build` succeeds with no new errors.
- [ ] 6.6 Document any deviations or open questions in `implementation-notes.md` (or the existing research doc) for this sub-section only.

## Cross-Cutting / Polish (only what is required for this sub)

- [ ] 7.1 Update any relevant Javadoc or inline comments in the changed domain/application classes referencing the 4.3 blueprint subsection.
- [ ] 7.2 If new files were added, ensure they are covered by existing package structure and do not violate line budgets or ArchUnit rules.
- [ ] 7.3 (If time) Add one or two focused unit tests for the new Education value object invariants.

---

**Notes for implementer**:
- Every task above is derived 1:1 from the user's explicit 6-step order.
- Do not touch files or logic belonging to the other 7 h5 subsections of 4.3.
- After all checkboxes in a phase are complete, the previous phase's changes must still compile and the basic happy path for "新增" must still work.
- When this feature is done, the next 4.3 h5 (编辑证书 or whichever the user chooses) will be a separate speckit feature.

**Total tasks**: 22 (7 phases)
