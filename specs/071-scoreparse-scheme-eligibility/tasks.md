# Tasks: 评分解析按方案判定候选资格与总分闭环

**Input**: Design documents in `specs/071-scoreparse-scheme-eligibility/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/score-parse-review-schemes.md`, `quickstart.md`

## Phase 1: Baseline and shared setup

- [x] T001 Record baseline test counts and exits for the Java/Vitest commands in `specs/071-scoreparse-scheme-eligibility/quickstart.md`.
- [x] T002 [P] Measure the existing `run_summary` fixture and four-table per-fact qualification serialization; record TEXT/VARCHAR headroom in `research.md` and `data-model.md`.

## Phase 2: User Story 1 - 方案外事实保留审计但退出候选 (Priority: P1)

**Goal**: turn original-scheme qualification into a persisted candidate-eligibility decision shared by the review API, total calculation and adoption path.
**Independent Test**: out-of-scheme facts remain in the snapshot with source/reason but are absent from default candidates, sums and default/explicit adoption; in-scheme valid facts remain.

### Tests first

- [x] T003 [P] [US1] Add out-of-scheme versus in-scheme eligibility assertions in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicyTest.java`.
- [x] T004 [P] [US1] Add stored audit-fact and scheme-evidence assertions in `backend/src/test/java/com/xiyu/bid/scoreparse/application/MultimodalStructuredExtractionHandlerTest.java`.
- [x] T005 [P] [US1] Add legacy-unverified, default-adoption and explicit ineligible-fact rejection cases in `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java`.
- [x] T006 [P] [US1] Add eligibility filtering, leaf sum and explicit fact-ID payload cases in `src/composables/projectDetail/useScoreCandidateAdoption.spec.js`.
- [x] T007 [P] [US1] Add separate in-scheme and audit-only rendering cases in `src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js`.

### Implementation

- [x] T008 [US1] Return an explicit per-claim scheme and source eligibility result from `backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicy.java`.
- [x] T009 [US1] Persist typed per-fact scheme qualification evidence, including member table IDs, through `ScoreParseFactEvidenceWriter`; the review mapper reconstructs schemes from those immutable evidence rows.
- [x] T010 [US1] Map scheme list, fact eligibility and exclusions into the review contract in `backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreParseReviewDTO.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java`.
- [x] T011 [US1] Recheck scheme qualification when resolving default and explicit adoption IDs in `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreItemPersistenceService.java`.
- [x] T012 [US1] Calculate totals and one-click payloads only from selected-scheme eligible facts in `src/composables/projectDetail/useScoreCandidateAdoption.js`.
- [x] T013 [US1] Render review-only facts in a distinct auditable section in `src/views/Project/stages/components/ScoreCandidateReviewCard.vue`.

## Phase 3: User Story 2 - 按一个逻辑评分方案组织多段、多表事实 (Priority: P1)

**Goal**: scope extraction and merging to logical scoring schemes, bind totals to original evidence, and distinguish unknown, conflicting and same-scheme multi-table states.
**Independent Test**: score-like facts from later chapters or independent schemes cannot merge into the selected scheme; one scheme spanning physical tables retains its facts, total and parent relationships.

### Tests first

- [x] T014 [P] [US2] Add exact paragraph/table-cell total locators and local-subtotal rejection cases in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContextThreeDefectsTest.java`.
- [x] T015 [P] [US2] Add scheme-scoped chunk inputs and isolated audit-group assertions in `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractorTest.java`.
- [x] T016 [P] [US2] Add same-scheme cross-table merge and independent-scheme isolation cases in `backend/src/test/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreFactChunkMergerTest.java`.
- [x] T017 [P] [US2] Add total/layout state matrix for verified, unknown, conflicting, multi-table and multiple-scheme inputs in `NEW: backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewTotalPolicyTest.java`.
- [x] T018 [P] [US2] Add same-code cross-scheme and same-scheme cross-table parent resolution cases in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizerTest.java`.

### Implementation

- [x] T019 [US2] Resolve scoring-section ranges, logical scheme identity, table ownership and exact total evidence from source in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoringContext.java`.
- [x] T020 [US2] Slice model input by resolved scheme while preserving separately tagged audit-only score material in `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/OpenAiMultimodalScoreFactExtractor.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/application/MultimodalScoreFactExtractor.java`.
- [x] T021 [US2] Merge overlapping chunks only within a scheme and resolve parent relationships after the same-scheme fact set is complete in `backend/src/main/java/com/xiyu/bid/scoreparse/infrastructure/openai/ScoreFactChunkMerger.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalFactParentCodeNormalizer.java`.
- [x] T022 [US2] Calculate total evidence independently from physical table count and prevent cross-scheme total addition in `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewTotalPolicy.java`.
- [x] T023 [US2] Expose scheme layout, selection requirement, total state and original total evidence in `backend/src/main/java/com/xiyu/bid/scoreparse/dto/ScoreParseReviewDTO.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java`.

## Phase 4: User Story 3 - 按评分关系解释权重与父子项 (Priority: P1)

**Goal**: correctly derive exclusive tier caps without weakening cumulative parent-child validation.
**Independent Test**: alternatives 5/4/3/2 yield a 5-point maximum, and cumulative 5+5+10 remains 20 under its parent.

### Tests first

- [x] T024 [P] [US3] Add mutually-exclusive tier maximum and cumulative-child counterexamples in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicyTest.java`.
- [x] T025 [P] [US3] Add effective-unit and parent total matrix for `MUTUALLY_EXCLUSIVE` and `SUM` in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicyTest.java`.
- [x] T026 [P] [US3] Add validator assertions that tier alternatives do not change parent aggregation in `backend/src/test/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidatorTest.java`.

### Implementation

- [x] T027 [US3] Treat alternative rubric scores as one item cap equal to their maximum in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/OriginalScoreEvidencePolicy.java`.
- [x] T028 [US3] Keep effective-leaf and cumulative-parent checks relation-aware in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreUnitPolicy.java` and `backend/src/main/java/com/xiyu/bid/scoreparse/domain/MultimodalScoreFactValidator.java`.

## Phase 5: User Story 4 - 真实项目回归不重现 1382/1384 分数污染 (Priority: P1)

**Goal**: add cross-layer golden replay cases for the currently observed 1382/1384 failure shapes and report actual test-system acceptance separately.
**Independent Test**: one replay asserts 1382's valid scoped leaves close 100 with later contract facts excluded; the other asserts 1384's source-bound 100, cross-table parent integrity and no unselected 250 total.

### Tests first

- [x] T029 [P] [US4] Add a redacted 1382 source fixture for an active scoring section followed by contract-performance scoring in `NEW: backend/src/test/resources/scoreparse-golden/case-project-1382-scheme-eligibility.md` and `NEW: backend/src/test/resources/scoreparse-golden/case-project-1382-scheme-eligibility.expected.json`.
- [x] T030 [P] [US4] Add a redacted 1384 multi-table scheme fixture with an explicit source-backed 100 total in `NEW: backend/src/test/resources/scoreparse-golden/case-project-1384-logical-scheme.md` and `NEW: backend/src/test/resources/scoreparse-golden/case-project-1384-logical-scheme.expected.json`.
- [x] T031 [US4] Register replay assertions for scheme selection, eligibility, exact total locators, parent links and effective leaf closure in `backend/src/test/java/com/xiyu/bid/scoreparse/golden/GoldenCasesReplayTest.java`.
- [x] T032 [US4] Add API/UI agreement assertions for selected scheme, audit-only facts and candidate total in `backend/src/test/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppServiceTest.java` and `src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js`.

### External acceptance boundary

- [ ] T033 [US4] Record test deployment commit, parse task IDs, file hashes and `/review` plus `/items` evidence for projects 1382 and 1384 in `specs/071-scoreparse-scheme-eligibility/quickstart.md` after separate test-release and reparse authorization; local golden replay does not complete this task.

## Phase 6: Polish and verification

- [x] T034 Run Spec Kit cross-artifact analysis and resolve requirement/task coverage gaps in `specs/071-scoreparse-scheme-eligibility/spec.md`, `plan.md` and `tasks.md`.
- [x] T035 Run deletion-first Mode B cleanup on the changed source/test files; record findings and deletions in `.omc/verify-01a0d212-e635-7db0-99c9-d0c7b235a0c3.md`.
- [x] T036 After cleanup, run the focused Java/Vitest targets, `GoldenCasesReplayTest`, build, backend suite, health check and applicable local gates; record exits and pass/fail/skip counts in `.omc/verify-01a0d212-e635-7db0-99c9-d0c7b235a0c3.md`. The broad backend run was explicitly interrupted after external test-infrastructure failures; it is not recorded as a pass.
- [x] T037 Perform read-only XiYu three-lens/adversarial review and record a fresh verdict in `.omc/review-01a0d212-e635-7db0-99c9-d0c7b235a0c3.md`.

## Dependencies

- User Story order follows the requested sequence: US1 eligibility gate → US2 logical scheme extraction/total/parent grouping → US3 weight semantics → US4 two-project replay.
- Within each story, tests are written and run before implementation; all post-cleanup verification follows T035.
- T033 depends on an authorized test deployment/reparse and is not part of the local working-tree delivery ceiling.

## Parallel Opportunities

- T003–T007 cover independent domain, orchestration, API and UI tests and can be authored separately before implementation.
- T014–T018 cover independent scheme-context, extractor, merger, total-state and parent-normalization tests.
- T024–T026 cover independent tier, unit-aggregation and validator regressions.
- T029–T030 fixtures can be prepared in parallel; replay registration waits for both fixtures.

## Implementation Strategy

Implement one requested behavior at a time. First make eligibility affect server and client defaults while preserving excluded facts. Then fix the scheme boundary and cross-table grouping that supply eligibility. Next fix mutually exclusive grade scoring independently of cumulative child aggregation. Finish with both redacted project-shaped replays and explicit reporting of the external test-system acceptance state.
