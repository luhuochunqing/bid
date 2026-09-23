# Tasks: 评分解析页面证据与计分单元修复

**Input**: Design documents in `specs/070-scoreparse-scoring-units/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/review-snapshot.md`, `quickstart.md`

## Phase 1: Baseline and shared setup

- [ ] T001 Record baseline pass/fail/skip counts for the Sidecar and backend commands in `specs/070-scoreparse-scoring-units/quickstart.md`
- [x] T002 [P] Confirm current migration naming and rollback conventions in `backend/src/main/resources/db/migration-mysql/`

## Phase 2: User Story 1 - 跨页评分表证据完整可复核 (Priority: P1)

**Goal**: carry physical page provenance from Sidecar cell through image selection and prevent incomplete page images from passing evidence checks.
**Independent Test**: Sidecar PDF continuation fixtures expose each cell page; backend image selection contains cited page images; missing/truncated coverage fails closed.

### Tests first

- [x] T003 [P] [US1] Add PDF continuation cell physical-page assertions in `document-converter-sidecar/tests/test_extract_tables_v2.py`
- [x] T004 [P] [US1] Add image extraction truncation/selected-page assertions in `document-converter-sidecar/tests/test_convert_images.py`
- [x] T005 [P] [US1] Add legacy-artifact and per-cell page deserialization tests in `backend/src/test/java/com/xiyu/bid/scoreparse/application/SidecarImageExtractorTest.java`
- [x] T006 [P] [US1] Add page-union and missing/truncated cited-page rejection tests in `backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java`

### Implementation

- [x] T007 [US1] Carry source physical page in PDF continuation cell metadata in `document-converter-sidecar/converter.py`
- [x] T008 [US1] Confirm the existing page selection and truncation API contract remains covered by `document-converter-sidecar/tests/test_convert_images.py`
- [x] T009 [US1] Add optional per-cell `sourcePage` to `backend/src/main/java/com/xiyu/bid/scoreparse/domain/grid/DocumentArtifact.java`
- [x] T010 [US1] Confirm the existing client preserves `truncated` and per-image `pageHint` in `backend/src/test/java/com/xiyu/bid/scoreparse/application/SidecarImageExtractorTest.java`
- [x] T011 [US1] Select image pages from referenced cells and fail evidence coverage when cited page is missing in `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java`
- [x] T012 [US1] Preserve and test existing DOCX gridSpan/vMerge mapping in `document-converter-sidecar/tests/test_extract_tables_v2.py`

## Phase 3: User Story 2 - 复核只在证据更强时替换初次结果 (Priority: P1)

**Goal**: use identical visual/source validation for initial extraction and recheck; only a strictly stronger verified result can replace.
**Independent Test**: deterministic result-selection matrix covers strong/weak, equal, and both-invalid cases.

### Tests first

- [x] T013 [P] [US2] Add initial/recheck evidence-strength selection cases in `backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java`
- [x] T014 [P] [US2] Assert initial and recheck model requests receive the same physical-page image set in `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractorTest.java`

### Implementation

- [x] T015 [US2] Add image-aware recheck contract in `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalScoreFactExtractor.java`
- [x] T016 [US2] Pass identical selected page images to recheck and validate both attempts through the same policies in `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java`
- [x] T017 [US2] Replace nonempty-result override with deterministic validation-strength comparison in `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java`
- [x] T018 [US2] Verify unsupported response mode/error fallback cannot silently claim complete visual evidence in `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java`

## Phase 4: User Story 3 - 按父子关系计算真实计分单元 (Priority: P1)

**Goal**: make child relationship explicit, use one pure scoring-unit rule across totals/candidates/publication, and return it in persistent review snapshots.
**Independent Test**: SUM / SHARED_CAP / BEST_OF / MUTUALLY_EXCLUSIVE produce exact effective units; UNKNOWN stays review-only; old null reads as SUM.

### Tests first

- [x] T019 [P] [US3] Add pure effective-unit and total matrix tests in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicyTest.java`
- [x] T020 [P] [US3] Add parent validation, multi-cell citations and relation mismatch tests in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidatorTest.java`
- [x] T021 [P] [US3] Add relation facts, candidate selection and review-required preservation tests in `backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java`
- [x] T022 [P] [US3] Add API model relation parsing and omission-to-UNKNOWN tests in `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractorTest.java`
- [x] T023 [P] [US3] Add review DTO legacy-null and relationship roundtrip tests in `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java`

### Implementation

- [x] T024 [US3] Define direct-child `ChildAggregation` and pure effective-unit traversal in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicy.java`
- [x] T025 [US3] Add relation to `backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFact.java` and JSON contract in `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java`
- [x] T026 [US3] Validate relation compatibility and all cited source cells in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidator.java`
- [x] T027 [US3] Use policy for source-backed total proof in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicy.java`
- [x] T028 [US3] Use policy for candidate and review gating in `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandler.java`
- [x] T029 [US3] Use effective-unit selection in existing publication guard `backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalSourcePublicationGuard.java`
- [x] T030 [US3] Persist relation from each domain fact into its immutable parse snapshot in `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemPersistenceService.java`
- [x] T031 [US3] Add nullable relation column and reversible rollback in `backend/src/main/resources/db/migration-mysql/V1217__add_score_child_aggregation.sql` and `backend/src/main/resources/db/rollback/migration-mysql/U1217__add_score_child_aggregation.sql`
- [x] T032 [US3] Map stored relation into legacy-compatible review snapshot DTOs in `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreParseReviewDTO.java`

## Phase 5: Polish and cross-cutting validation

- [x] T033 Run Sidecar focused tests and record exact counts in `specs/070-scoreparse-scoring-units/quickstart.md`
- [x] T034 Run backend focused scoreparse tests and GoldenCasesReplayTest; record failures/skips in `specs/070-scoreparse-scoring-units/quickstart.md`
- [x] T035 Validate migration/rollback, Spec Kit consistency, architecture gates, and final diff without running a production migration in `specs/070-scoreparse-scoring-units/quickstart.md`
- [ ] T036 Perform independent adversarial review of changed source, tests, persistence contract and formal publication guard in `specs/070-scoreparse-scoring-units/plan.md`

## Dependencies

- US1 completes before US2 because recheck must reuse the final page/image provenance contract.
- US2 completes before US3 so rule validation operates on chosen, evidence-complete facts.
- US3 persistence and review mapping depend on the shared domain relation type/policy.
- Execution order: US1 → US2 → US3 → Polish.

## Parallel Opportunities

- T003–T006 are separate regression test files; run independently after baseline.
- T013–T014 can be authored independently.
- T019–T023 can be authored independently before implementation, then T024 unblocks dependent policy integration.

## Implementation Strategy

Deliver first the page-provenance/recheck increments while preserving the existing scoring gate. Then introduce the relation contract and replace every independent leaf-sum derivation with the same policy. Do not enable auto-publication for UNKNOWN or incomplete evidence. Finish only after tests demonstrate both new semantics and legacy null compatibility.
