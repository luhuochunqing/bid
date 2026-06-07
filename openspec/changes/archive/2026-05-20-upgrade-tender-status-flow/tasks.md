# Tasks: Tender Status Upgrade

## Phase 1: Core Logic (Expert: Domain Agent)
- [ ] Update `com.xiyu.bid.entity.Tender.Status` enum values. <!-- id: core-enum -->
- [ ] Update `com.xiyu.bid.batch.core.TenderStatusTransitionPolicy` with new transition matrix. <!-- id: core-policy -->
- [ ] Add unit tests for `TenderStatusTransitionPolicy`. <!-- id: core-test -->
- [ ] Update `com.xiyu.bid.scoreanalysis.entity.ScoreAnalysis` to include `tender_id`. <!-- id: core-entity-analysis -->

## Phase 2: Persistence & Migration (Expert: Infrastructure Agent)
- [ ] Create Flyway migration `V116__tender_status_expansion.sql`. <!-- id: db-migration -->
    - Update `tenders` table data: `PENDING` -> `PENDING_ASSIGNMENT`, `BIDDED` -> `BIDDING`.
    - Add `tender_id` to `score_analyses`.
- [ ] Update JPA Repositories to support `tender_id` lookups for ScoreAnalysis. <!-- id: db-repo -->

## Phase 3: Application Services (Expert: Application Agent)
- [ ] Update `TenderCommandService` to handle new status transitions. <!-- id: app-tender-service -->
- [ ] Update `ScoreAnalysisService` to trigger status change to `EVALUATED` on save. <!-- id: app-analysis-trigger -->
- [ ] Implement "Abandon" permission check logic. <!-- id: app-permission -->

## Phase 4: Frontend Implementation (Expert: Frontend Agent)
- [ ] Update `src/views/Bidding/bidding-utils-status.js` with new status mappings. <!-- id: fe-utils -->
- [ ] Update `TenderTable.vue` and `TenderActionMenu.vue` for new lifecycle actions. <!-- id: fe-components -->
- [ ] Add "Evaluation" and "Result Registration" dialogs/flows. <!-- id: fe-flows -->

## Phase 5: Verification & Cleanup
- [ ] Run full backend test suite (`mvn test`). <!-- id: qa-backend -->
- [ ] Verify UI status transitions manually. <!-- id: qa-ui -->
- [ ] Perform "Refactor-Clean" phase to remove old `BIDDED` references. <!-- id: qa-cleanup -->
