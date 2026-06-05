---

description: "标讯中心 P0 阻塞项落地任务列表"
---

# Tasks: 标讯中心 P0 阻塞项

**Input**: Design documents from `specs/003-tender-center-p0/`

**Organization**: Tasks grouped by user story. Each story is independently implementable and testable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1-US5)
- Include exact file paths in descriptions

---

## Phase 1: Foundational (Shared Prerequisites)

**Purpose**: Schema changes and code cleanup that block or benefit all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Foundation: V129 — SourceType enum expansion

- [X] T001 Create V129 migration: `backend/src/main/resources/db/migration-mysql/V129__tender_source_type_expand.sql` — ALTER ENUM to 4 values + data backfill script with `source` prefix matching (CRM→CRM_OPPORTUNITY, EXTERNAL→EXTERNAL_PLATFORM, MANUAL→MANUAL_SINGLE)
- [X] T002 [P] Create U129 rollback: `backend/src/main/resources/db/rollback/migration-mysql/U129__tender_source_type_expand.sql`
- [X] T003 Update `Tender.SourceType` inner enum in `backend/src/main/java/com/xiyu/bid/entity/Tender.java` — expand from `MANUAL/EXTERNAL` to `EXTERNAL_PLATFORM/MANUAL_SINGLE/CRM_OPPORTUNITY/BULK_IMPORT`
- [X] T004 [P] Delete duplicate enum files: `backend/src/main/java/com/xiyu/bid/entity/TenderSourceType.java`
- [X] T005 Update `TenderCommandService.java` — set correct `SourceType` per creation channel (manual→MANUAL_SINGLE, batch→BULK_IMPORT, etc.)

**Checkpoint**: Foundation ready — 4 source types exist in DB and Java enum; user story implementation can begin

---

## Phase 2: User Story 1 — 三段式评估表 (Priority: P1) 🎯 MVP

**Goal**: 项目负责人可在跟踪中标讯详情页填写三段评估表（基础信息8字段 + 客户信息13×14 + 投标负责人建议）并提交，决策人可审核决策

**Independent Test**: 项目负责人登录 → 进入跟踪中标讯详情页 → 切换到「项目评估表」Tab → 看到3段 → 填写提交 → 状态变为「已评估」 → 决策人审核 → 立即投标/放弃投标

### V130: Evaluation 3-section tables

- [X] T006 [US1] Create V130 migration: `backend/src/main/resources/db/migration-mysql/V130__tender_evaluation_three_sections.sql` — create `tender_evaluation_basics`, `tender_evaluation_customer_info`, `tender_evaluation_recommendation` tables + add `requires_review`/`last_reviewed_by_id`/`last_reviewed_at`/`evaluation_round` columns to `tender_evaluations`
- [X] T007 [P] [US1] Create U130 rollback: `backend/src/main/resources/db/rollback/migration-mysql/U130__tender_evaluation_three_sections.sql`

### Backend: Entities & Repositories

- [X] [US1] T008 [P] Create `TenderEvaluationBasic.java` entity in `backend/src/main/java/com/xiyu/bid/tender/entity/TenderEvaluationBasic.java` — 8 fields, one-to-one with TenderEvaluation
- [X] [US1] T009 [P] Create `TenderEvaluationCustomerInfo.java` entity in `backend/src/main/java/com/xiyu/bid/tender/entity/TenderEvaluationCustomerInfo.java` — EAV pattern: (evaluation_id, role_key, info_key, value, value_type) UNIQUE constraint
- [X] [US1] T010 [P] Create `TenderEvaluationRecommendation.java` entity in `backend/src/main/java/com/xiyu/bid/tender/entity/TenderEvaluationRecommendation.java` — should_bid, reason
- [X] [US1] T011 [P] Create `TenderEvaluationCustomerInfoRepository.java` in `backend/src/main/java/com/xiyu/bid/tender/repository/TenderEvaluationCustomerInfoRepository.java`
- [X] [US1] T012 Modify `TenderEvaluation.java` entity — add `requiresReview`, `lastReviewedBy`, `lastReviewedAt`, `evaluationRound` fields; add one-to-one relationships to Basic, CustomerInfo list, Recommendation

### Backend: Core Business Policy (Pure Core)

- [X] [US1] T013 Create `TenderEvaluationCustomerInfoPolicy.java` in `backend/src/main/java/com/xiyu/bid/tender/core/TenderEvaluationCustomerInfoPolicy.java` — validate fixed 13 roles × 14 info columns, no row mutations allowed, pure core (no framework imports)

### Backend: Service Layer

- [X] [US1] T014 Modify `TenderEvaluationService.java` — restructure load/save to handle 3-section aggregate; on save in EVALUATED state, set `requires_review=true` and increment `evaluation_round`
- [X] [US1] T015 Add `reviewEvaluation(evaluationId)` method to `TenderEvaluationService.java` — set `requires_review=false`, update `last_reviewed_by_id`/`last_reviewed_at`
- [X] [US1] T016 Modify `TenderEvaluationSubmissionService.java` — on submit, validate all 3 sections complete; send notification to admin/lead/owner

### Backend: Controller

- [X] [US1] T017 Add `POST /api/tenders/{evaluationId}/evaluation/review` endpoint in `TenderEvaluationController.java` — bid_admin/bid_lead only, requires requires_review=true

### Frontend: Evaluation Form Restructure

- [X] [US1] T018 Create `CustomerInfoMatrix.vue` in `src/views/Bidding/detail/components/CustomerInfoMatrix.vue` — fixed 13-row × 14-column table, role_key rows pre-filled and non-editable, dropdown cells for boolean/enum fields, text cells for name fields
- [X] [US1] T019 Modify `TenderEvaluationForm.vue` in `src/views/Bidding/detail/TenderEvaluationForm.vue` — restructure into 3 panels (基础信息 / 客户信息矩阵 / 投标负责人建议), integrate CustomerInfoMatrix, add section navigation
- [X] [US1] T020 Modify `src/api/modules/tenders.js` — update evaluation load/save/submit payload to support 3-section data structure
- [X] [US1] T021 Add review button + requires_review badge to `DetailPage.vue` — show "需审核" badge when requires_review=true, show "确认审核" button for admin/lead role

**Checkpoint**: US1 complete — evaluation 3-section form functional, submit→review→bid/abandon cycle works end-to-end

---

## Phase 3: User Story 5 — 角色术语统一 (Priority: P5) 🔄 Parallel

**Goal**: `sales` RoleProfile 在系统各处显示为「项目负责人」

**Independent Test**: sales 用户登录 → 所有界面显示「项目负责人」而非「销售」

- [X] T022 [P] [US5] Update `RoleProfileCatalog.java` in `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` — change `sales` display name from "销售/业务负责人" to "项目负责人"
- [X] T023 [P] [US5] Update frontend role display in `src/utils/permission.js` (or i18n config) — replace "销售" with "项目负责人" in all display mappings (Header.vue, interface-permission-matrix-core.js, InterfacePermissionMatrixPanel.vue)
- [X] T024 [P] [US5] Update `e2e/bidding-list-detail-ai-flow.spec.js` — no "销售" references found; no changes needed
- [X] T025 [P] [US5] Update `CLAUDE.md` credentials section — add note about `sales` display name being "项目负责人"

**Checkpoint**: US5 complete — terminology unified across backend enum, frontend UI, e2e assertions

---

## Phase 4: User Story 2 — 转派流程独立化 (Priority: P2)

**Goal**: 投标管理员/组长可在跟踪中/已评估标讯操作列发起转派，原负责人立即失去访问权限

**Independent Test**: 投标管理员 → 跟踪中标讯 → 点击转派按钮 → 选择新负责人 → 确认 → 原负责人看不到该标讯 → 新负责人看到 → 状态不变

### V132: Assignment record type column

- [X] T026 Create V132 migration: `backend/src/main/resources/db/migration-mysql/V132__tender_assignment_record_type.sql` — ADD COLUMN `type ENUM('DISPATCH','TRANSFER')` to `tender_assignment_records`, DEFAULT 'DISPATCH'
- [X] T027 [P] Create U132 rollback: `backend/src/main/resources/db/rollback/migration-mysql/U132__tender_assignment_record_type.sql`

### Backend

- [X] T028 Create `TenderTransferService.java` in `backend/src/main/java/com/xiyu/bid/tender/service/TenderTransferService.java` — transfer(tenderId, newOwnerId, operatorId): validate status TRACKING/EVALUATED, validate operator role, write TRANSFER record, update project_manager_id/department
- [X] T029 Add `POST /api/tenders/{id}/transfer` endpoint in `TenderController.java` — @PreAuthorize bid_admin/bid_lead, delegate to TenderTransferService
- [X] T030 Update `TenderAuditService.java` — add `TRANSFER` audit action constant

### Frontend

- [X] T031 Create `TransferDialog.vue` in `src/views/Bidding/list/components/TransferDialog.vue` — single field (project owner select), no bidding person field; validate target != current owner
- [X] T032 Update `TenderActionMenu.vue` in `src/views/Bidding/list/components/TenderActionMenu.vue` — show "转派" button separate from "分配" for TRACKING/EVALUATED status, only for bid_admin/bid_lead
- [X] T033 Add `transferTender(id, payload)` to `src/api/modules/tenders.js`

**Checkpoint**: US2 complete — transfer flow works end-to-end with permission enforcement and real-time data scope

---

## Phase 5: User Story 3 — 标讯源后端持久化 (Priority: P3)

**Goal**: 投标管理员统一管理标讯源配置，团队共享一份生效配置

**Independent Test**: 管理员在浏览器A保存配置 → 浏览器B登录 → 看到相同配置；非管理员403

### V131: Tender source config table

- [X] [US3] T034 Create V131 migration: `backend/src/main/resources/db/migration-mysql/V131__tender_source_config.sql` — CREATE TABLE `tender_source_configs` single-row singleton
- [X] [US3] T035 [P] Create U131 rollback: `backend/src/main/resources/db/rollback/migration-mysql/U131__tender_source_config.sql`

### Backend

- [X] [US3] T036 Create `TenderSourceConfig.java` entity in `backend/src/main/java/com/xiyu/bid/tendersource/entity/TenderSourceConfig.java`
- [X] [US3] T037 [P] Create `TenderSourceConfigRepository.java` in `backend/src/main/java/com/xiyu/bid/tendersource/repository/TenderSourceConfigRepository.java`
- [X] [US3] T038 Create `TenderSourceConfigService.java` in `backend/src/main/java/com/xiyu/bid/tendersource/service/TenderSourceConfigService.java` — get()/save(adminId) with API key AES encryption/decryption; reuse existing encryption utility
- [X] [US3] T039 Create `TenderSourceConfigController.java` in `backend/src/main/java/com/xiyu/bid/tendersource/controller/TenderSourceConfigController.java` — GET/PUT /api/tender-sources/config, @PreAuthorize bid_admin

### Frontend

- [X] [US3] T040 Update `src/api/modules/tenderSources.js` — replace test-only connection endpoint with getConfig()/saveConfig(payload); keep testConnection() for existing USB
- [X] [US3] T041 Update `SourceConfigDialog.vue` in `src/views/Bidding/list/components/SourceConfigDialog.vue` — onMounted fetch config from backend; save → POST to backend; remove localStorage logic from `useTenderSourceConfig.js`

**Checkpoint**: US3 complete — source config persisted server-side, shared across sessions, admin-only access

---

## Phase 6: User Story 4 — 来源类型扩展 (Priority: P4)

**Goal**: 标讯列表筛选和统计接口按4个独立来源分类

**Independent Test**: 创建4种来源的标讯 → 筛选器显示4个选项 → 各自出现在正确类目 → 统计接口按4来源分组

- [X] T042 [US4] Update frontend source filter options in `src/views/Bidding/list/components/TenderSearchCard.vue` — show 4 source options matching new enum values
- [X] T043 [US4] Update `TenderQueryService.java` — verify `findBySourceType()` works with all 4 enum values
- [X] T044 [US4] Verify statistics endpoint in `TenderController.java` groups by source_type correctly (4 values)
- [X] T045 [US4] Run dry-run migration preview: `SELECT source, source_type, COUNT(*) FROM tenders GROUP BY source, source_type` — verify data backfill accuracy before applying V129

**Checkpoint**: US4 complete — 4 source types functional in filtering and statistics

---

## Phase 7: Polish & Cross-Cutting

**Purpose**: E2E tests, regression, documentation

- [X] T046 Create `e2e/tender-transfer-flow.spec.js` — new E2E covering: admin transfers tracked tender → original owner disappears → new owner sees it → status unchanged → cannot approve invalid targets
- [X] T047 Run full regression: `npm run build && mvn -f backend/pom.xml test && npm run test:e2e`
- [X] T048 Update `docs/prd-简报-标讯中心-2026-05-18.md` — add clarifications section from spec.md
- [X] T049 Verify agent context update: ensure `CLAUDE.md` references this plan path correctly

---

## Dependencies & Execution Order

### Phase Dependencies

- **Foundational (Phase 1)**: No code dependencies — can start immediately
- **US1 (Phase 2)**: Depends on V129 + T005 (SourceType for evaluation source display)
- **US5 (Phase 3)**: Can start in **parallel** with Phase 2 (different files, no overlap)
- **US2 (Phase 4)**: Depends on V129 (uses Tender entity with updated SourceType). Can start after Foundational (Phase 1) — independent from US1
- **US3 (Phase 5)**: Depends on V129. Can start after Phase 1 — independent from US1/US2
- **US4 (Phase 6)**: Depends on V129 data migration. Can start after Phase 1 — independent from other stories
- **Polish (Phase 7)**: Depends on all user stories complete

### Parallel Opportunities

```
Phase 1 (Foundational / V129)
    │
    ├──→ Phase 2 (US1: 评估表三段式) ──┐
    │                                  │
    ├──→ Phase 3 (US5: 角色重命名 [P]) ─┼──→ Phase 7 (Polish)
    │                                  │
    ├──→ Phase 4 (US2: 转派) ──────────┘
    │         AND
    ├──→ Phase 5 (US3: 源持久化)
    │         AND
    └──→ Phase 6 (US4: 来源扩展)
```

- Phase 2 and Phase 3 can run fully in parallel (US5 has zero overlap with US1)
- Phase 4, 5, 6 can run in parallel with each other after Foundational is done
- Only Phase 1 must be sequential (shared schema change)

---

## Implementation Strategy

### MVP First (US1 + US5 = evaluation + rename)

1. Complete Phase 1: Foundational (V129)
2. Complete Phase 2: US1 (评估表三段式)
3. Complete Phase 3: US5 (角色重命名, parallel with Phase 2)
4. **STOP and VALIDATE**: Test evaluation form end-to-end
5. Proceed to US2/US3/US4 in any order as staffing allows

### Incremental Delivery

| Phase | Value Delivered | Independent Test |
|-------|----------------|------------------|
| Phase 2 (US1) | 评估表可直接用了 | See US1 Independent Test |
| Phase 3 (US5) | 术语统一 | See US5 Independent Test |
| Phase 4 (US2) | 转派独立流程 | See US2 Independent Test |
| Phase 5 (US3) | 配置团队共享 | See US3 Independent Test |
| Phase 6 (US4) | 来源精细分类 | See US4 Independent Test |
