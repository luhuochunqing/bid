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


## Phase 6: DOCX conversion契约与资格根因收敛（本轮新增）

**Goal**: 转换输出保留标题和表格章节证据，Java 只凭可验证的证据授予资格。执行顺序固定：T038–T041 Sidecar 契约；T042–T043 Java 资格；T044 本地交叉层回归；T045 测试站真实 DOCX 验收。

### DOCX 转换契约（先做）

- [x] T038 [P] [US5] 在 `document-converter-sidecar/tests/test_app.py` 为加粗结构标题添加 headings HTTP 契约失败测试，断言解强调后的标题文本及原始字符 offset。
- [x] T039 [US5] 修改 `document-converter-sidecar/converter.py` 的 `extract_headings`，识别 MarkItDown 加粗的结构性中文章节标题并保持 offset 指向原始 Markdown；RED→GREEN。
- [x] T040 [P] [US5] 在 `document-converter-sidecar/tests/test_extract_tables_v2.py` 创建临时 DOCX，断言章节段落与交错表格按 body 顺序生成 sourceSection，未知区段保持空值。
- [x] T041 [US5] 修改 `document-converter-sidecar/table_extractor.py`，按一次线性 DOCX body 遍历生成稳定标题路径；保持原 tableId 顺序、单元格/XML locator 和评分表 classifier；RED→GREEN。

### 资格规则（转换契约通过后）

- [x] T042 [P] [US5] 添加 converter-shaped Java 回归，使用粗体标题/总分和章节表格，不预填 schemeId，断言行定位、方案总分和章节归属。
- [x] T043 [US5] 修复 inline emphasis 与空章节资格后门；额外修复 validator 在资格核验后仍以模型空身份聚合跨表父子项的问题，无章节证据继续 SOURCE_SCHEME_NOT_VERIFIED。
- [x] T044 [US5] 更新项目 1384 脱敏回放为 Sidecar converter-shaped 输入且不预置 scheme/scope；验证 total locator、跨表父项和合格叶子 100 分，并运行 1382 回归。

## Phase 7: Polish, verification and test-site acceptance (previous revision)

- [x] T034 Run Spec Kit cross-artifact analysis and resolve requirement/task coverage gaps in `specs/071-scoreparse-scheme-eligibility/spec.md`, `plan.md` and `tasks.md`.
- [x] T035 Run deletion-first Mode B cleanup on the changed source/test files; record findings and deletions in `.omc/verify-01a0d212-e635-7db0-99c9-d0c7b235a0c3.md`.
- [x] T036 After cleanup, run the focused Java/Vitest targets, `GoldenCasesReplayTest`, build, backend suite, health check and applicable local gates; record exits and pass/fail/skip counts in `.omc/verify-01a0d212-e635-7db0-99c9-d0c7b235a0c3.md`. The broad backend run was explicitly interrupted after external test-infrastructure failures; it is not recorded as a pass.
- [x] T037 Perform read-only XiYu three-lens/adversarial review and record a fresh verdict in `.omc/review-01a0d212-e635-7db0-99c9-d0c7b235a0c3.md`.

### Post-change gates (must follow T038–T044)

- [x] T045 [US5] 对所有本轮修改的 Sidecar、Java、回归测试和规格文件执行 Mode B deletion-first 清理；在 `.omc/slop-cleanup-01a0d614-a262-7ff1-ada6-f7e3876e56d6.md` 记录检查范围、实际删除项或“无安全删除项”。
- [x] T046 [US5] 在清理后重跑 Sidecar 与指定 Java/Golden 测试、`mvn -f backend/pom.xml test -Dtest=FPJavaArchitectureTest`、`npm run build`、`cd backend && mvn test` 和适用的仓库门禁；在 `.omc/verify-01a0d614-a262-7ff1-ada6-f7e3876e56d6.md` 记录每条命令退出码及精确 pass/fail/skip。全后端测试中断且非通过，测试站部署健康门禁也失败。
- [x] T047 [US5] 运行 `npm run agent:health-check` 和只读 XiYu 三透镜/对抗审查，检查最新 diff 与 T046 证据；在 `.omc/review-01a0d614-a262-7ff1-ada6-f7e3876e56d6.md` 写 verdict、reviewer、轮次和发现。
- [ ] T048 [US5] 测试站发布尝试 `1384-docx-contract3-api8080` 在 `/actuator/health` 连续 240 次探测失败后退出；V1218 `backfill_table_column_comments` 在测试库失败。已恢复后端与前端到发布前 `8245c4da8` 并保留失败现场/备份。真实 DOCX 未重解析，`/review`、`/items` 与页面验收待测试库迁移恢复后执行。详见 `quickstart.md`。

## Phase 8: 1384 missing-evidence follow-up

**Goal**: Recover only source-verifiable 1384 facts. First extend the Sidecar contract for split DOCX scoring rows; then bind and qualify ordered source-cell fragments in Java. Uncertain links stay audit-only.

### Sidecar contract (RED/GREEN before Java changes)

- [x] T049 [P] [US5] Add generated DOCX regressions in `document-converter-sidecar/tests/test_extract_tables_v2.py` for a split scoring row, an adjacent complete independent row and a repeated header. Assert only proven continuation shares `logicalRowId`; physical table/cell IDs, coordinates and XML paths remain unchanged. RED observed before implementation.
- [x] T050 [US5] Emit stable `logicalRowId` for every v2.1 cell in `document-converter-sidecar/table_extractor.py`; link only adjacent same-section tables with compatible columns, no repeated header, blank score/identity cells and non-empty continuation rule text when the predecessor is unique. Update schema-version assertions. Java remains backward-compatible with v2.0 cells lacking this field.

### Exact Java source binding and qualification (after T050)

- [x] T051 [P] [US5] Add binder regressions for exact quote binding across ordered cells sharing one `logicalRowId`; reject duplicate matching rows and model quotes that lack an exact dimension-plus-score anchor.
- [x] T052 [US5] Implement deterministic binding: first require exact quote text; when it fails, rebuild the quote from original cells only if exact dimension text plus source score evidence selects one logical row. Preserve ordered source cell IDs and the first physical table ID. Ambiguous/no matches remain unbound.
- [x] T053 [US5] Add qualification and validator regressions for same-row multi-table sources, missing cell IDs, different logical rows and cross-scheme sources. Verify Java deserializes v2.1 `logicalRowId` and v2.0 artifacts without it; require every cited ID to resolve and cross-table IDs to share a logical row/scheme with an exact joined quote, while retaining legacy single-table and text locator behavior.
- [x] T054 [US5] Add synthetic/redacted `case-project-1384-docx-continuation.md` and `.expected.json` under `backend/src/test/resources/scoreparse-golden/`, register it in `GoldenCasesReplayTest#cases()`, and assert synthetic score extraction and 100-point closure. Keep exact emphasis/`and`/`和` reconstruction and continuation citations asserted by binder/validator tests. Do not commit the real DOCX.

### Fresh cleanup, verification, review and test acceptance

- [ ] T055 [US5] Run `ai-slop-cleaner` Mode B on the follow-up diff and record cleanup evidence. Then run the new Sidecar and Java/Golden cases, `mvn -q -Dtest=FPJavaArchitectureTest test`, `npm run build` and applicable PR gates after cleanup.
- [ ] T056 [US5] Run fresh health and read-only XiYu three-lens source review against the new diff and T055 evidence; classify code findings separately from the still-pending real acceptance, and do not push. Record this review round; it cannot reuse prior S6 evidence.
- [ ] T057 [US5] Only when the source review has no code blocker and test readiness is healthy, deploy this exact commit to ENV=test and reparse the existing 1384 DOCX; record release/input SHA, task ID/time, each missing fact's citations and qualification, `/review`, `/items`, UI and preserved formal items. The historical V1218 failure blocks this task until resolved; do not repair migration history or the DB as part of this fix.
- [ ] T058 [US5] Run final read-only review on fresh real acceptance evidence. Update existing PR #2760 only when MERGE_READY; if test DB readiness remains blocked, report code verification separately and leave live acceptance/PR delivery pending.

## Dependencies

- User Story order follows the requested sequence: US1 eligibility gate → US2 logical scheme extraction/total/parent grouping → US3 weight semantics → US4 two-project replay.
- Within each story, tests are written and run before implementation; new conversion-contract tasks T038–T041 precede qualification tasks T042–T044. T045–T047 were completed for the prior source revision; their evidence is stale after this follow-up change and must not be reused.
- T057 remains ENV=test only and requires fresh T055/T056 evidence plus healthy readiness. T058 and PR update require real reparse acceptance. No production deployment or database repair is authorized.

## Parallel Opportunities

- T003–T007 cover independent domain, orchestration, API and UI tests and can be authored separately before implementation.
- T014–T018 cover independent scheme-context, extractor, merger, total-state and parent-normalization tests.
- T024–T026 cover independent tier, unit-aggregation and validator regressions.
- T029–T030 fixtures can be prepared in parallel; replay registration waits for both fixtures.

## Implementation Strategy

Implement one requested behavior at a time. First make eligibility affect server and client defaults while preserving excluded facts. Then fix the scheme boundary and cross-table grouping that supply eligibility. Next fix mutually exclusive grade scoring independently of cumulative child aggregation. Finish with both redacted project-shaped replays and explicit reporting of the external test-system acceptance state.
