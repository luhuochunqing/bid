# Tasks: Implement Bidding Center

## Phase 1: Backend Data Model (DB + Entity)

- [ ] 1.1 Create Flyway migration V1.2 adding 18 new columns to `tender` table
- [ ] 1.2 Update `Tender.java` entity with all new fields + `ProjectType` enum
- [ ] 1.3 Update `TenderDtoMapper` to map new fields in API responses
- [ ] 1.4 Update `TenderCommandService.create()` to accept and persist new fields
- [ ] 1.5 Update `TenderCommandService.update()` to handle new field updates
- [ ] 1.6 Run `mvn test -pl backend` to verify no regressions

## Phase 2: Backend Core Logic

- [ ] 2.1 Create `TenderDeduplicationPolicy` (Pure Core) in `tender/domain/`
- [ ] 2.2 Wire dedup check in `TenderController.create()` with override/new logic
- [ ] 2.3 Wire dedup in `TenderSourceController` sync flow (auto-skip)
- [ ] 2.4 Create `TenderAuditService` logging all 9 operation types
- [ ] 2.5 Add audit log endpoint `GET /api/tenders/{id}/audit-logs`
- [ ] 2.6 Extend export to include all 17 blueprint columns
- [ ] 2.7 Update `TenderSearchCard` backend query to support personnel filters
- [ ] 2.8 Add evaluation notification (todo + 企微) on submit
- [ ] 2.9 Run `FPJavaArchitectureTest` to verify pure core constraints

## Phase 3: Frontend List Page

- [ ] 3.1 Rewrite `TenderTable.vue` columns: 5 → 18 columns
- [ ] 3.2 Add row index column with pagination-aware numbering
- [ ] 3.3 Add personnel columns (项目负责人/投标负责人/部门/创建人)
- [ ] 3.4 Add missing filter fields to `TenderSearchCard.vue`
- [ ] 3.5 Update API calls in `useTenderList` composable
- [ ] 3.6 Verify mobile responsive behavior

## Phase 4: Frontend Detail Page

- [ ] 4.1 Refactor `DetailPage.vue` from el-descriptions to el-tabs (3 tabs)
- [ ] 4.2 Build Tab1: BasicInfo with all 27 fields
- [ ] 4.3 Integrate Tab2: existing TenderEvaluationForm
- [ ] 4.4 Build Tab3: `OperationLogTimeline.vue` component
- [ ] 4.5 Implement sticky bottom action bar
- [ ] 4.6 Update edit mode to respect blueprint status-role matrix

## Phase 5: Frontend Manual Entry

- [ ] 5.1 Build `ManualTenderCreate.vue` with step flow (基本信息 → 项目评估表)
- [ ] 5.2 Wire AI parsing into step flow
- [ ] 5.3 Add dedup prompt dialog on save
- [ ] 5.4 Keep `ManualTenderDialog.vue` as legacy entry redirect

## Phase 6: Source Config & Bulk Import

- [ ] 6.1 Add 业务单位 / 自动匹配 / 自动去重 fields to SourceConfigDialog
- [ ] 6.2 Update BulkImportDialog template with new fields
- [ ] 6.3 Verify import template download includes new columns

## Phase 7: Validation

- [ ] 7.1 Run `npm run build` (frontend)
- [ ] 7.2 Run `mvn test` (backend)
- [ ] 7.3 Run `FPJavaArchitectureTest` + `MaintainabilityArchitectureTest`
- [ ] 7.4 Add E2E test for tender creation with new fields
- [ ] 7.5 Add E2E test for dedup prompt flow
- [ ] 7.6 Add E2E test for 3-tab detail page navigation
- [ ] 7.7 Run `openspec validate implement-bidding-center --strict`
