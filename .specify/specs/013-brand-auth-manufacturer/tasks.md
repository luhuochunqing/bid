# Tasks: 品牌授权 — 原厂授权核心 §4.6a

**Input**: Design documents from `specs/013-brand-auth-manufacturer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1-US5)

---

## Phase 1: Setup (Database + Enums + Permissions)

**Purpose**: Database migration, enums, and permission registration — prerequisites for all user stories.

- [x] T001 [P] Create ProductLine enum (39 items) in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/domain/valueobject/ProductLine.java
- [x] T002 [P] Create AuthStatus enum (5 states) in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/domain/valueobject/AuthStatus.java
- [x] T003 [P] Create AttachmentType enum in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/domain/valueobject/AttachmentType.java
- [ ] T004 Create Flyway migration V147 in backend/src/main/resources/db/migration-mysql/V147__manufacturer_authorization.sql — rename old table, create manufacturer_authorization + brand_auth_attachment tables
- [ ] T005 Create rollback migration U147 in backend/src/main/resources/db/rollback/migration-mysql/U147__manufacturer_authorization.sql
- [x] T006 Add knowledge-brand-auth.* permission keys to RoleProfileCatalog.java and create Flyway migration V148 to seed existing roles

---

## Phase 2: Foundational (Domain + Repository + Shared UI)

**Purpose**: Core domain model, persistence layer, and shared frontend infrastructure that all user stories depend on.

- [ ] T007 [P] Create ManufacturerAuthorization domain record in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/domain/model/ManufacturerAuthorization.java
- [ ] T008 [P] Create ManufacturerAuthorizationRepository port interface in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/domain/port/ManufacturerAuthorizationRepository.java
- [ ] T009 [P] Create AuthorizationExpiryPolicy domain service in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/domain/service/AuthorizationExpiryPolicy.java
- [ ] T010 Create ManufacturerAuthorizationEntity JPA entity in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/entity/ManufacturerAuthorizationEntity.java
- [ ] T011 [P] Create BrandAuthAttachmentEntity JPA entity in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/entity/BrandAuthAttachmentEntity.java
- [ ] T012 [P] Create ManufacturerAuthorizationJpaRepository in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/repository/ManufacturerAuthorizationJpaRepository.java
- [ ] T013 [P] Create BrandAuthAttachmentJpaRepository in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/repository/BrandAuthAttachmentJpaRepository.java
- [ ] T014 Create ManufacturerAuthorizationRepositoryAdapter in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/persistence/ManufacturerAuthorizationRepositoryAdapter.java
- [ ] T015 Create ManufacturerAuthorizationDTO in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/dto/ManufacturerAuthorizationDTO.java
- [ ] T016 Create ManufacturerAuthMapper in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/mapper/ManufacturerAuthMapper.java
- [ ] T017 [P] Update BrandAuth.vue — add el-tabs (原厂授权 | 代理商授权 placeholder), extract table into BrandAuthManufacturerTab.vue component
- [ ] T018 [P] Update src/api/modules/brandAuth.js — add new API functions (manufacturer CRUD, upload, revoke, logs)

---

## Phase 3: User Story 1 — 新增原厂授权 (P1) 🎯 MVP

**Goal**: bid_admin/bid_lead/bid_specialist can create a new 原厂授权 with 3-section drawer form and file upload.

**Independent Test**: Login as bid_admin, navigate to /knowledge/brand-auth, click "+ 新增原厂授权", fill form with attachments, save, see new record in list.

- [ ] T019 [US1] Create CreateManufacturerAuthCommand in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/command/CreateManufacturerAuthCommand.java
- [ ] T020 [US1] Create CreateManufacturerAuthAppService in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/CreateManufacturerAuthAppService.java — validate required fields, date logic, duplicate detection
- [ ] T021 [US1] Create AttachmentUploadAppService in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/AttachmentUploadAppService.java — file validation (PDF/JPG/PNG, ≤20MB), storage
- [ ] T022 [US1] Create ManufacturerAuthorizationController.java with POST /api/knowledge/brand-auth and POST /api/knowledge/brand-auth/attachments/upload in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/infrastructure/ManufacturerAuthorizationController.java
- [ ] T023 [US1] Build 3-section drawer form in BrandAuthManufacturerTab.vue — 基础信息区 (productLine + brandId + brandName + importDomestic + manufacturerName), 授权信息区 (authDates + authDoc upload), 补充信息区 (remarks + supplementary upload)
- [ ] T024 [US1] Implement form validation: required field red highlight + auto-scroll, end-date check, duplicate yellow warning
- [ ] T025 [US1] Write unit test for CreateManufacturerAuthAppService validation rules
- [ ] T026 [US1] Write unit test for ManufacturerAuthorizationController create endpoint

---

## Phase 4: User Story 2 — 查看列表与详情 (P1)

**Goal**: Users see 12-column list with pagination, click to open detail drawer with 4 sections and attachment preview.

**Independent Test**: Login as any authorized role, see list, click row, open detail with attachment preview.

- [ ] T027 [US2] Create ListManufacturerAuthAppService in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/ListManufacturerAuthAppService.java — pagination, default sort by end_date ASC
- [ ] T028 [US2] Add GET /api/knowledge/brand-auth (list) and GET /api/knowledge/brand-auth/{id} (detail) to ManufacturerAuthorizationController.java
- [ ] T029 [US2] Add GET /api/knowledge/brand-auth/{id}/logs to ManufacturerAuthorizationController.java — query audit_logs WHERE entity_type='BRAND_AUTH'
- [ ] T030 [US2] Build 12-column table in BrandAuthManufacturerTab.vue — columns per spec, pagination 20/50/100
- [ ] T031 [US2] Build 4-section detail drawer — 基础信息 / 授权信息 (with attachment preview) / 备注与附件 / 操作日志 (last 5, expandable)

---

## Phase 5: User Story 3 — 修改原厂授权 (P2)

**Goal**: Edit authorization with status-gated field availability.

**Independent Test**: Edit an ACTIVE record, change brand name, save, see change in detail and audit log.

- [ ] T032 [US3] Create UpdateManufacturerAuthCommand in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/command/UpdateManufacturerAuthCommand.java
- [ ] T033 [US3] Create UpdateManufacturerAuthAppService in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/UpdateManufacturerAuthAppService.java — status-gated field editing, diff generation for audit log
- [ ] T034 [US3] Add PUT /api/knowledge/brand-auth/{id} to ManufacturerAuthorizationController.java
- [ ] T035 [US3] Implement edit mode in BrandAuthManufacturerTab.vue — reuse drawer form, status-gated field readonly states, save updates

---

## Phase 6: User Story 4 — 作废授权 (P2)

**Goal**: bid_admin/bid_lead can soft-delete with required reason ≥10 chars.

**Independent Test**: As bid_admin, click 作废, enter reason, confirm, see status change to REVOKED with grey strikethrough.

- [ ] T036 [US4] Create RevokeManufacturerAuthCommand in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/command/RevokeManufacturerAuthCommand.java
- [ ] T037 [US4] Create RevokeManufacturerAuthAppService in backend/src/main/java/com/xiyu/bid/brandauth/manufacturer/application/service/RevokeManufacturerAuthAppService.java — validate reason ≥10 chars, update status, stop reminders
- [ ] T038 [US4] Add POST /api/knowledge/brand-auth/{id}/revoke to ManufacturerAuthorizationController.java — @PreAuthorize for bid_admin/bid_lead only
- [ ] T039 [US4] Implement 作废 confirmation dialog in BrandAuthManufacturerTab.vue — warning text, reason textarea with char count, confirm/cancel
- [ ] T040 [US4] Remove DELETE endpoint from old BrandAuthorizationController, redirect or deprecate

---

## Phase 7: User Story 5 — 筛选与导出 (P3)

**Goal**: 11-dimension filter + Excel export ≤500 records.

**Independent Test**: Set filters (status=ACTIVE), click query, list updates. Click export, get Excel file.

- [ ] T041 [US5] Add server-side filtering to ListManufacturerAuthAppService — 11 dimensions per API contract
- [ ] T042 [US5] Build filter bar in BrandAuthManufacturerTab.vue — productLine multi-select, brandId fuzzy, status multi-select (default exclude REVOKED), date ranges, etc.
- [ ] T043 [US5] Implement export endpoint GET /api/knowledge/brand-auth/export — max 500 rows, Excel generation, audit log
- [ ] T044 [US5] Add export button with confirmation dialog in BrandAuthManufacturerTab.vue — filter summary, record count, filename preview

---

## Phase 8: E2E Tests + Polish

**Purpose**: End-to-end verification and cross-cutting cleanup.

- [ ] T045 [P] Create Playwright E2E test e2e/brand-auth-manufacturer-flow.spec.js — cover US1-US4 across 4 roles
- [ ] T046 Run mvn compile -q + npm run build to verify compilation
- [ ] T047 Run ArchitectureTest to verify no layer violations
- [ ] T048 Mark old BrandAuthorization domain classes with @Deprecated annotations
- [ ] T049 Update implementation-notes.md with decisions and pitfalls

---

## Dependencies

```
Phase 1 (Setup) ─────────────────────────────────────────────────────
     │
Phase 2 (Foundational) ──────────────────────────────────────────────
     │
     ├── Phase 3 (US1: 新增) ──┐
     │                          ├── Phase 5 (US3: 修改)
     ├── Phase 4 (US2: 列表) ──┘
     │                          │
     │                          ├── Phase 6 (US4: 作废)
     │                          │
     └──────────────────────────┴── Phase 7 (US5: 筛选导出) ── Phase 8 (E2E)
```

US1 + US2 together form the foundation for US3 and US4.
US5 depends on US2 (list) being complete.
E2E tests require all user stories complete.

## Parallel Opportunities

- T001, T002, T003 (all enums) — parallel
- T007, T008, T009 (domain model) — parallel with each other after enums
- T010, T011, T012, T013 (entities + repos) — parallel after domain model
- T017, T018 (frontend base) — parallel
- T019, T020 within US1 — parallel (command + app service)
- T045 (E2E) can start early in parallel with US5

## Implementation Strategy

**MVP (Phase 1-4)**: Ship US1 + US2 first — users can create and view 原厂授权.
This delivers the core value: data entry + visibility.

**V1.1 (Phase 5-6)**: Add edit + revoke — complete CRUD lifecycle.

**V1.2 (Phase 7-8)**: Filter/export + E2E validation — production readiness.
