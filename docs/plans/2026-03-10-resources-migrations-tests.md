# Resources Migrations And Integration Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the new BAR certificate and expense approval/return contracts deployable by adding incremental database migrations and minimal integration tests.

**Architecture:** Add Flyway as the incremental schema mechanism for existing environments, using a single migration that introduces the new tables/columns and safe backfills for the resource contracts. Keep integration tests on the current H2 + MockMvc pattern and validate the new HTTP state transitions directly, without trying to retrofit full-schema migrations for the whole application in this round.

**Tech Stack:** Spring Boot 3.2, Spring Data JPA, Flyway, H2, MockMvc, JUnit 5, Spring Security Test.

---

### Task 1: Add incremental schema migration support

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/resources/application-test.yml`

**Step 1: Add Flyway dependency**
- Add `org.flywaydb:flyway-core` to `backend/pom.xml`.
- Do not add a second migration tool.

**Step 2: Configure incremental migration behavior**
- In `backend/src/main/resources/application.yml`, add Flyway config with:
  - `enabled: true`
  - `baseline-on-migrate: true`
  - `baseline-version: 0`
- Keep `spring.jpa.hibernate.ddl-auto: validate` unchanged.

**Step 3: Keep current integration-test boot behavior stable**
- In `backend/src/test/resources/application-test.yml`, explicitly disable Flyway and keep `ddl-auto: create-drop`.
- This prevents the new incremental migration from breaking the existing H2-based test suite, which has no full historical schema baseline.

**Step 4: Verify config compiles**
- Run: `mvn -DskipTests compile`
- Expected: PASS.

### Task 2: Create SQL migration for resource contracts

**Files:**
- Create: `backend/src/main/resources/db/migration-mysql/V1__resources_contracts.sql`
- Read for alignment: `backend/src/main/java/com/xiyu/bid/resources/entity/Expense.java`
- Read for alignment: `backend/src/main/java/com/xiyu/bid/resources/entity/ExpenseApprovalRecord.java`
- Read for alignment: `backend/src/main/java/com/xiyu/bid/resources/entity/BarCertificate.java`
- Read for alignment: `backend/src/main/java/com/xiyu/bid/resources/entity/BarCertificateBorrowRecord.java`

**Step 1: Alter the existing `expenses` table**
- Add columns if absent:
  - `expense_type`
  - `status`
  - `approval_comment`
  - `approved_by`
  - `approved_at`
  - `return_requested_at`
  - `return_confirmed_at`
  - `return_comment`
- Backfill `status` to `PAID` for existing rows where it is null.
- Backfill `expense_type` to `其他` for existing rows where it is null.
- Make `status` non-null after backfill.

**Step 2: Create the approval history table**
- Create `expense_approval_records` with columns matching the entity.
- Add index on `expense_id` and `acted_at`.

**Step 3: Create BAR certificate tables**
- Create `bar_certificates` with columns matching the entity and index on `bar_asset_id`.
- Create `bar_certificate_borrow_records` with columns matching the entity and indexes on `certificate_id` and `status`.

**Step 4: Keep SQL MySQL-safe and idempotent enough for rolling upgrades**
- Use `IF NOT EXISTS` for additive operations where possible.
- Avoid dropping or rewriting existing tables.
- Do not attempt to create legacy tables unrelated to this feature.

**Step 5: Verify schema alignment**
- Run: `mvn -DskipTests compile`
- Expected: PASS.

### Task 3: Add minimal expense integration tests

**Files:**
- Create: `backend/src/test/java/com/xiyu/bid/resources/integration/ExpenseControllerIntegrationTest.java`
- Read for pattern: `backend/src/test/java/com/xiyu/bid/competitionintel/integration/CompetitionIntelControllerIntegrationTest.java`
- Read for target API: `backend/src/main/java/com/xiyu/bid/resources/controller/ExpenseController.java`

**Step 1: Write setup helpers**
- Use `@SpringBootTest`, `@AutoConfigureMockMvc`, `@DirtiesContext(AFTER_EACH_TEST_METHOD)`.
- Seed one guarantee expense and one normal expense through `ExpenseRepository`.

**Step 2: Cover approval flow**
- Test `POST /api/resources/expenses/{id}/approve` with approval request.
- Assert response success, updated `status`, `approvedBy`, and approval history row.

**Step 3: Cover guarantee return flow**
- Test `POST /return-request` then `POST /confirm-return` for a guarantee expense.
- Assert status transitions `APPROVED -> RETURN_REQUESTED -> RETURNED` and timestamp fields are set.

**Step 4: Cover invalid return scenario**
- Test return request on a non-guarantee expense.
- Assert the request fails with server-side validation rather than silently succeeding.

**Step 5: Cover approval-records query**
- Test `GET /api/resources/expenses/approval-records?projectId=...` returns the created history rows.

**Step 6: Run focused tests**
- Run: `mvn -Dtest=ExpenseControllerIntegrationTest test`
- Expected: PASS.

### Task 4: Add minimal BAR certificate integration tests

**Files:**
- Create: `backend/src/test/java/com/xiyu/bid/resources/integration/BarCertificateControllerIntegrationTest.java`
- Read for target API: `backend/src/main/java/com/xiyu/bid/resources/controller/BarCertificateController.java`
- Read for supporting entity: `backend/src/main/java/com/xiyu/bid/resources/entity/BarAsset.java`

**Step 1: Write setup helpers**
- Seed one `BarAsset` through `BarAssetRepository`.
- Create one certificate either via repository or create endpoint.

**Step 2: Cover certificate CRUD entry point**
- Test `POST /api/resources/bar-assets/{assetId}/certificates` creates a certificate.
- Test `GET /api/resources/bar-assets/{assetId}/certificates` returns it.

**Step 3: Cover borrow/return flow**
- Test `POST /{certificateId}/borrow` updates certificate status to `BORROWED` and creates one borrow record.
- Test `POST /{certificateId}/return` updates status back to `AVAILABLE` and marks the record `RETURNED`.

**Step 4: Cover borrow-records query**
- Test `GET /{certificateId}/borrow-records` returns the expected record list.

**Step 5: Run focused tests**
- Run: `mvn -Dtest=BarCertificateControllerIntegrationTest test`
- Expected: PASS.

### Task 5: Run final verification

**Files:**
- No code changes.

**Step 1: Backend verification**
- Run: `mvn -Dtest=ExpenseControllerIntegrationTest,BarCertificateControllerIntegrationTest test`
- Expected: PASS.

**Step 2: Compile verification**
- Run: `mvn -DskipTests compile`
- Expected: PASS.

**Step 3: Frontend regression check**
- Run: `npm run build`
- Expected: PASS.

**Step 4: API-mode frontend regression check**
- Run: `VITE_API_MODE=api npm run build`
- Expected: PASS.

**Step 5: Record known boundary**
- Note that Flyway now covers only this incremental resource contract, not the entire historical schema for a fresh empty production database.
- Fresh-environment bootstrap remains a separate follow-up project.
