# Implementation Plan: 标讯中心 P0 阻塞项

**Branch**: `003-tender-center-p0` | **Date**: 2026-05-18 | **Spec**: specs/003-tender-center-p0/spec.md

## Summary

标讯中心 P0 阻塞项：评估表三段重构、SourceType 扩 4 值、TenderSource 后端持久化、转派流程独立、销售→项目负责人重命名。现有代码约 60-70% 已实现，本 plan 覆盖 5 项 P0 改造的完整技术设计。

## Technical Context

**Language/Version**: Java 21 (backend), JavaScript/Vue 3.4 (frontend)

**Primary Dependencies**:
- Backend: Spring Boot 3.2, Spring Security, JPA/Hibernate, Flyway, MySQL 8.0
- Frontend: Vue 3.4, Vite 5, Pinia, Element Plus, ECharts
- Testing: JUnit 5 + ArchUnit (backend), Vitest (unit), Playwright (E2E)

**Storage**: MySQL 8.0 via Flyway migrations (backend/src/main/resources/db/migration-mysql/)

**Testing**:
- Backend: `mvn test -Dtest=ArchitectureTest` (gate), `mvn test` (full)
- Frontend: `npm run test:unit`
- E2E: `npx playwright test e2e/bidding-list-detail-ai-flow.spec.js`

**Target Platform**: Web (Spring Boot 18081 + Vite 1315)

**Project Type**: Full-stack web application (Vue SPA + REST API)

**Performance Goals**: Standard web app latency (<500ms p95)

**Constraints**: Follow existing patterns (FP-Java architecture, Split-First, @DataScope for data permissions)

**Scale/Scope**: Single-feature enhancement within existing tender center module

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. FP-Java Architecture | ✅ PASS | Pure Core (TenderEvaluationCustomerInfoPolicy, TenderTransferPolicy) extracted from Shell (Service) |
| II. Real-API Only | ✅ PASS | No mock involved; all APIs backed by real persistence |
| III. TDD | ✅ PASS | New specs (tender-transfer-flow.e2e) + existing tests stay green |
| IV. Split-First & Simplicity | ✅ PASS | Each new file <200 lines; TenderSourceConfig = single-row singleton, no over-engineering |
| V. Boring Proven Patterns | ✅ PASS | All patterns reuse existing: JPA entities, @Service/@RestController, Flyway migrations |
| Security & Access Control | ✅ PASS | @DataScope reused for transfer permission; @PreAuthorize for TenderSource; API key encrypted |
| Dev Workflow & Multi-Agent SOP | ✅ PASS | Can work in parallel with other agents via who-touches.sh |

## Project Structure

```
specs/003-tender-center-p0/
├── spec.md              # Feature specification (with clarifications)
├── plan.md              # This file (will be copied after plan mode exit)
├── research.md          # Design decisions (will be created after plan mode exit)
├── data-model.md        # Entity definitions (will be created after plan mode exit)
├── quickstart.md        # Implementation guide (will be created after plan mode exit)
├── contracts/           # API contracts (will be created after plan mode exit)
└── tasks.md             # Task breakdown (will be created by /speckit-tasks)
```

### Source Code (modified files)

```
# Backend
backend/src/main/java/com/xiyu/bid/
├── entity/
│   ├── Tender.java                              # [MODIFY] SourceType enum: 2→4 values
│   └── RoleProfileCatalog.java                  # [MODIFY] sales→项目负责人 display
├── tender/entity/
│   ├── TenderEvaluation.java                    # [MODIFY] Restructure into 3-section aggregate
│   └── TenderEvaluationCustomerInfo.java        # [NEW] EAV entity for 13×14 customer matrix
├── tender/repository/
│   └── TenderEvaluationCustomerInfoRepository.java # [NEW] Spring Data JPA
├── tender/core/
│   ├── TenderEvaluationCustomerInfoPolicy.java  # [NEW] Pure Core: validate fixed 13×14, no row mutations
│   └── TenderStatusTransitionPolicy.java        # [MODIFY] Add EVALUATED_PENDING_REVIEW → EVALUATED transition
├── tender/service/
│   ├── TenderCommandService.java                # [MODIFY] SourceType assignment per channel
│   ├── TenderEvaluationService.java             # [MODIFY] 3-section load/save/submit/review
│   ├── TenderImportService.java                 # [MODIFY] SourceType=BULK_IMPORT
│   └── TenderTransferService.java               # [NEW] Dedicated transfer logic (distinct from dispatch)
├── tender/controller/
│   ├── TenderController.java                    # [MODIFY] Add POST /api/tenders/{id}/transfer
│   └── TenderEvaluationController.java          # [MODIFY] Add POST /api/tenders/{id}/evaluation/review
├── tendersource/
│   ├── entity/TenderSourceConfig.java           # [NEW] JPA entity, single-row singleton
│   ├── repository/TenderSourceConfigRepository.java # [NEW]
│   ├── service/TenderSourceConfigService.java   # [NEW] CRUD with API key encryption
│   └── controller/TenderSourceConfigController.java # [NEW] GET/PUT /api/tender-sources/config

# Migrations
backend/src/main/resources/db/migration-mysql/
├── V129__tender_source_type_expand.sql          # [NEW] ALTER ENUM, data backfill
├── V130__tender_evaluation_three_sections.sql   # [NEW] 3-section tables
├── V131__tender_source_config.sql               # [NEW] tender_source_configs table
└── V132__tender_assignment_record_type.sql      # [NEW] ADD COLUMN type ENUM('DISPATCH','TRANSFER')

backend/src/main/resources/db/rollback/migration-mysql/
├── U129__tender_source_type_expand.sql
├── U130__tender_evaluation_three_sections.sql
├── U131__tender_source_config.sql
└── U132__tender_assignment_record_type.sql

# Frontend
src/
├── api/modules/
│   ├── tenders.js                                # [MODIFY] Add transferTender, evaluation review endpoint
│   └── tenderSources.js                          # [MODIFY] Replace test-only with getConfig/saveConfig
├── views/Bidding/
│   ├── List.vue                                  # [MODIFY] Add transfer button context
│   ├── list/components/
│   │   ├── TransferDialog.vue                    # [NEW] Transfer dialog (project owner only)
│   │   ├── SourceConfigDialog.vue                # [MODIFY] Fetch/save from backend, not localStorage
│   │   └── TenderActionMenu.vue                  # [MODIFY] Show 转派 for TRACKING/EVALUATED
│   └── detail/
│       ├── DetailPage.vue                        # [MODIFY] Evaluation review button
│       └── TenderEvaluationForm.vue              # [MODIFY] Restructure to 3-section layout
│       └── components/
│           └── CustomerInfoMatrix.vue             # [NEW] 13×14 fixed table component
├── utils/
│   └── permission.js                             # [MODIFY] sales→项目负责人 display
└── router/index.js                               # no changes needed (routes exist)

# E2E
e2e/
├── bidding-list-detail-ai-flow.spec.js            # [MODIFY] Update role display name assertions
└── tender-transfer-flow.spec.js                   # [NEW] Transfer flow E2E
```

## Phase 0: Research & Design Decisions

### Decision 1: Evaluation customer info storage — EAV pattern

**Decision**: Use EAV (Entity-Attribute-Value) table for 13×14 matrix
**Rationale**: 13 roles × 14 info columns = 182 cells, not pre-definable as separate columns; EAV matches JPA entity-per-storage pattern already used in this project. Each cell = 1 row in `tender_evaluation_customer_info` with `(evaluation_id, role_key, info_key, value, value_type)`.
**Alternatives**: JSON column (loss of type safety, hard to query), 14 columns × 13 rows (182 columns total, anti-pattern).

### Decision 2: TenderSource API key encryption

**Decision**: Use project's existing AES encryption (same infrastructure as JWT secret)
**Rationale**: Avoid introducing new KMS infrastructure; symmetric AES with key derived from JWT_SECRET. Encrypt at Service layer, store as VARCHAR(512) in DB.
**Alternatives**: Vault/HashiCorp (overkill for single tenant config).

### Decision 3: EVALUATED_PENDING_REVIEW as virtual state

**Decision**: Not a new DB column — `tender_evaluations.last_reviewed_at IS NULL AND status='EVALUATED'` plus `requires_review BOOLEAN DEFAULT FALSE` flag.
**Rationale**: Avoids expanding Tender status enum (which would require updating all 7-state checks) and keeps the flag scoped to the evaluation table.

### Decision 4: Transfer vs Dispatch distinction

**Decision**: `TenderAssignmentRecord.type` column with `DISPATCH` or `TRANSFER`. Transfer endpoint is `POST /api/tenders/{id}/transfer`, requires `bid_admin` or `bid_lead` role. Does NOT change tender status. Old `project_manager_id` is overwritten.
**Rationale**: Adding a type column is minimal schema change. The data scope guard (`@DataScope`) already reads `project_manager_id` from the tender row, so updating it on transfer automatically removes old owner's access without additional cache invalidation.

## Phase 1: Data Model & Contracts

### V129: Source type enum expansion

```sql
ALTER TABLE tenders MODIFY COLUMN source_type ENUM(
  'EXTERNAL_PLATFORM', 'CRM_OPPORTUNITY', 'MANUAL_SINGLE', 'BULK_IMPORT'
) NOT NULL DEFAULT 'MANUAL_SINGLE';

-- Data migration
UPDATE tenders SET source_type = 'CRM_OPPORTUNITY'
  WHERE source_type = 'EXTERNAL' AND (source LIKE 'CRM%' OR source LIKE '商机%');
UPDATE tenders SET source_type = 'EXTERNAL_PLATFORM'
  WHERE source_type = 'EXTERNAL' AND source_type != 'CRM_OPPORTUNITY';
UPDATE tenders SET source_type = 'BULK_IMPORT'
  WHERE source_type = 'MANUAL' AND (source LIKE '%导入%' OR source LIKE '%批量%');
UPDATE tenders SET source_type = 'MANUAL_SINGLE'
  WHERE source_type IN ('EXTERNAL', 'MANUAL') AND source_type NOT IN ('CRM_OPPORTUNITY', 'BULK_IMPORT', 'EXTERNAL_PLATFORM');
```

### V130: Evaluation 3-section tables

- `tender_evaluations` — keep existing table, add columns: `requires_review BOOLEAN DEFAULT FALSE`, `last_reviewed_by_id VARCHAR(32)`, `last_reviewed_at DATETIME`, `evaluation_round INT DEFAULT 1`
- `tender_evaluation_basics` — extracted from existing `tender_evaluations` columns: `shortlisted_count`, `annual_procurement_amount`, `unfavorable_items`, `risk_assessment`, `risk_mitigation_plan`, `project_manager_process_knowledge`, `support_notes` (text, max 5000 chars each), `project_plan_gap` (text)
- `tender_evaluation_customer_info` — EAV table: `id BIGINT AUTO_INCREMENT`, `evaluation_id BIGINT NOT NULL`, `role_key VARCHAR(32) NOT NULL`, `info_key VARCHAR(32) NOT NULL`, `value VARCHAR(500)`, `value_type ENUM('TEXT', 'DROPDOWN') NOT NULL`
- `tender_evaluation_recommendation` — `evaluation_id BIGINT PK`, `should_bid BOOLEAN`, `reason TEXT`

### V131: Tender source config

```sql
CREATE TABLE tender_source_configs (
  id        BIGINT PRIMARY KEY,
  config    JSON NOT NULL,  -- platforms, apiEndpoint, keywords, regions, budgets, sync, dedupe
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  updated_by    VARCHAR(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Single row: id always 1
```

### V132: Assignment record type

```sql
ALTER TABLE tender_assignment_records
  ADD COLUMN type ENUM('DISPATCH', 'TRANSFER') NOT NULL DEFAULT 'DISPATCH' AFTER id;
UPDATE tender_assignment_records SET type = 'DISPATCH' WHERE type IS NULL;
```

### API Contracts

See specs/003-tender-center-p0/contracts/ after plan mode exit.

### Entity relationship

```
Tender 1─1 TenderEvaluation
                        ├──1:N TenderEvaluationBasic (8 fields)
                        ├──1:N TenderEvaluationCustomerInfo (EAV, 13×14 cells)
                        └──1:1 TenderEvaluationRecommendation

TenderAssignmentRecord ── (new column: type DISPATCH/TRANSFER)

TenderSourceConfig (single-row singleton, id=1)
```

## Verification

### During development
```bash
# Backend
mvn test -Dtest=ArchitectureTest -q

# Frontend
npm run check:front-data-boundaries && npm run build

# Database migration dry-run
mvn flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/xiyu_bid_claude
```

### Pre-completion gate
```bash
# 1. Migrations
mvn flyway:migrate

# 2. Backend tests
mvn test -Dtest=ArchitectureTest,TenderCommandServiceTest,TenderEvaluationServiceTest,TenderTransferServiceTest

# 3. Frontend build + unit
npm run build && npm run test:unit

# 4. E2E (affected specs)
npx playwright test e2e/bidding-list-detail-ai-flow.spec.js e2e/tender-transfer-flow.spec.js
```

## Task ordering

1. **V129 + SourceType expansion** (parallel: role rename)
2. **V130 + Evaluation 3-section** + CustomerInfoMatrix.vue (largest, 2-3 days)
3. **V131 + TenderSource persistence** (parallel: V132)
4. **V132 + Transfer flow** + TransferDialog.vue
5. **Role rename** (trivial, done inline)
6. **E2E: tender-transfer-flow** (new spec)
7. **Full regression**
