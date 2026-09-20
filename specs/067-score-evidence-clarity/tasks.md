# Tasks: 评分依据可信度与中文可读性

**Input**: Design documents from `specs/067-score-evidence-clarity/`

## Phase 1: Baseline and RED Evidence

- [x] T001 Record current targeted backend and frontend baseline commands in `specs/067-score-evidence-clarity/quickstart.md`
- [x] T002 [P] Add failing amount extraction cases to `backend/src/test/java/com/xiyu/bid/scoreparse/domain/KnowledgeCategoryPolicyTest.java`
- [x] T003 [P] Add failing manual-evidence classification cases to `backend/src/test/java/com/xiyu/bid/scoreparse/domain/ProjectEvidenceRequirementPolicyTest.java`
- [x] T004 [P] Add failing project-1117-style regression to `backend/src/test/java/com/xiyu/bid/scoreparse/application/EstimatedScoreServiceTest.java`
- [x] T005 [P] Add failing Chinese-only display cases to `src/views/Project/stages/components/ScoreItemDetailModal.spec.js`

## Phase 2: User Story 1 - 不完整证据不得自动给满分 (Priority: P1)

**Goal**: 不能证明全部强制条件时保持待确认。

**Independent Test**: 项目 1117 同类条款即使返回 131 条候选，仍为 null/PENDING，依据列出人工核验项。

- [x] T006 [US1] Implement contract amount extraction in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/KnowledgeCategoryPolicy.java`
- [x] T007 [US1] Implement immutable evidence assessment in `backend/src/main/java/com/xiyu/bid/scoreparse/domain/ProjectEvidenceRequirementPolicy.java`
- [x] T008 [US1] Pass extracted amount into project matching and gate incomplete evidence in `backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java`
- [x] T009 [US1] Run targeted backend tests and confirm both conservative and existing simple-score paths

## Phase 3: User Story 2 - 清楚说明来源和证据缺口 (Priority: P1)

**Goal**: 依据明确区分业绩候选与有效业绩。

**Independent Test**: 证据不足依据包含业绩库、候选数量、已核验条件和待核验材料，不含内部代码。

- [x] T010 [US2] Generate evidence-aware Chinese basis text in `backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java`
- [x] T010A [US2] Keep basis formatting outside the orchestration service in `backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreBasisFormatter.java`
- [x] T011 [US2] Convert normal match tier and proof wording to business Chinese in `backend/src/main/java/com/xiyu/bid/scoreparse/application/EstimatedScoreService.java`
- [x] T012 [US2] Extend assertions for zero candidates and complete candidates in `backend/src/test/java/com/xiyu/bid/scoreparse/application/EstimatedScoreServiceTest.java`

## Phase 4: User Story 3 - 详情信息全部业务中文化 (Priority: P2)

**Goal**: 详情不显示内部英文枚举，并准确标注数据来源。

**Independent Test**: 详情文本不含已知内部枚举，业绩类标签为“业绩库候选资料”。

- [x] T013 [US3] Add source-name and evidence-source presentation helpers in `src/views/Project/stages/components/ScoreItemDetailModal.vue`
- [x] T014 [US3] Replace generic knowledge-hit badge and raw location output in `src/views/Project/stages/components/ScoreItemDetailModal.vue`
- [x] T015 [US3] Run `ScoreItemDetailModal.spec.js` and verify existing actual-score behavior remains unchanged

## Phase 5: Cleanup, Verification, Review

- [x] T016 Run ai-slop-cleaner Mode B on changed source files and remove avoidable duplication
- [x] T017 Re-run targeted backend and frontend tests after cleanup
- [x] T018 Run backend compile and frontend build commands from `specs/067-score-evidence-clarity/quickstart.md`
- [x] T019 Run Spec Kit consistency analysis for `specs/067-score-evidence-clarity/`
- [x] T020 Record S5 evidence in `.omc/verify-01a0bf3a-9400-7e33-8f6d-b9e8409440ec.md`
- [x] T021 Execute read-only `xiyu-code-review` and record verdict in `.omc/review-01a0bf3a-9400-7e33-8f6d-b9e8409440ec.md`

## Dependencies & Execution Order

- T002-T005 are independent RED tests after T001.
- T006 and T007 must precede T008; T008-T012 complete the backend stories.
- T013-T015 are independent of backend implementation after the display contract is fixed.
- T016 invalidates earlier verification; T017-T021 must use post-cleanup code.

## Implementation Strategy

MVP is US1 + US2: prevent unsupported full scores and explain evidence gaps. US3 is a separate display slice. No task authorizes commit, push, PR, deployment, production rewrite, or production reparse.
