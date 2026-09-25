# Implementation Plan: 评分解析按方案判定候选资格与总分闭环

**Branch**: `071-scoreparse-scheme-eligibility` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/071-scoreparse-scheme-eligibility/spec.md`

## Summary

把评分方案身份和事实资格作为解析数据流的一部分：先隔离可审计的方案外事实，再按逻辑方案切分和合并文本/表格证据，保存可回指的方案总分与资格快照；复核 API、默认采纳和 UI 使用相同的资格结果。明确区分互斥档位的最高分与累计父子项合计。实现步骤按用户指定的 1 → 2 → 3 → 4 顺序推进。

## Technical Context

**Language/Version**: Java 21 (scoreparse backend), Vue 3 / JavaScript (review UI)
**Primary Dependencies**: Spring Boot 3.2, Jackson/JPA, Vue 3, Vitest
**Storage**: Existing `score_parse_fact` and per-fact `score_parse_evidence`; `run_summary` remains unchanged; no schema migration planned.
**Testing**: Maven JUnit/GoldenCasesReplayTest; Vitest; build and local repository gates
**Target Platform**: XiYu test/development application
**Project Type**: Existing scoreparse backend API plus project review UI
**Performance Goals**: Preserve extraction-call and source-size budgets; reduce irrelevant scoring prose per scheme.
**Constraints**: Preserve original text/table locators, existing `AUTO_APPROVED` publication guard and legacy review compatibility; no test-project writes or deployment.
**Scale/Scope**: Scheme resolution, source-backed total evidence, candidate/review persistence, default adoption and the score review card for projects 1382/1384.

## Constitution Check

- FP-Java: scope and scoring rules remain pure domain functions; JPA/API/UI stay in their existing layers.
- Real API Only: add no mock runtime path. Regression inputs are golden fixtures consumed by production extraction/policy code.
- TDD: each requested behavior starts with a failing domain, orchestration, API or UI assertion.
- Simplicity and line budgets: reuse existing scoreparse policies, chunk merger, immutable parse facts and evidence rows; add no new service/module.
- Database: use existing fact/evidence tables; do not duplicate scheme qualification in `run_summary`. If tests prove typed eligibility cannot be persisted in current evidence rows, stop before schema edits, amend this plan/Entry Gate, acquire a migration lock, and add a reversible Flyway pair.
- Release boundary: local working tree only; no deployment, project reparse, formal adoption or production-data mutation.

## Design Decisions

1. **Eligibility is data, not a warning.** Keep every observed score-like fact and original evidence. Persist per-fact scheme membership as typed evidence; `OUT_OF_SCHEME` and `SOURCE_UNVERIFIED` are review-only. Only locally valid `IN_SCHEME` facts participate in candidate totals or default adoption. Backend rechecks before adoption.
2. **Resolve schemes before merging chunks.** Identify scoring-section bounds, scheme identity, member tables and explicit total evidence from the artifact/original text. Slice model input by scheme. Score-like material outside the selected scheme stays in a separately marked audit group. Merge only within a scheme; resolve parent codes after assembling all scheme facts.
3. **Keep topology and total evidence orthogonal.** A scheme can have one or many physical tables. Its total independently has `VERIFIED`, `UNKNOWN` or `CONFLICT` state; the response-level adoption state is `CONFLICTING_TOTAL`. Multiple schemes require selection; totals are never summed merely because there are multiple table splits.
4. **Bind totals to original evidence.** Explicit totals carry a quote and source locator (`text:line[n]` or table/cell). Local row maxima and category subtotals cannot supply scheme total.
5. **Apply aggregation by relation.** `MUTUALLY_EXCLUSIVE` grade alternatives use their highest score. `SUM` children close against their parent; parents and leaves are not double-counted.
6. **Reuse existing snapshots.** `score_parse_evidence.kind/explanation` records per-fact qualification, including scheme/table membership and the source-backed total; the review DTO groups these rows into schemes. `run_summary` remains pipeline telemetry/table-split metadata and receives no duplicate scheme manifest. Old snapshots without scope evidence are `SOURCE_UNVERIFIED` and excluded by default.
7. **Adoption is single-scheme.** One eligible scheme may be selected by default. Multiple schemes require selection and submit only that scheme's fact IDs. Backend rejects missing selection, cross-scheme fact sets and ineligible IDs.

## Implementation Phases

1. Add failing eligibility, audit preservation, candidate total and backend default-adoption tests; propagate typed eligibility through saved evidence, review DTO, frontend defaults and rendering.
2. Add scheme-scoped extraction/merge tests, including 1382's active chapter and later contract section; bind 1384's 100-point total to original evidence; resolve cross-table parents and total/scheme states.
3. Add mutually exclusive tier and cumulative-child tests, then correct weight verification without changing parent-child aggregation semantics.
4. Add 1382/1384 golden and cross-layer replay checks. Report test-environment reparse separately; it is outside this working-tree route.

## Validation Plan

- Java targets: `OriginalScoringContextThreeDefectsTest`, `OriginalScoreEvidencePolicyTest`, `MultimodalScoreFactValidatorTest`, `MultimodalFactParentCodeNormalizerTest`, `ScoreUnitPolicyTest`, `ScoreFactChunkMergerTest`, `OpenAiMultimodalScoreFactExtractorTest`, `MultimodalStructuredExtractionHandlerTest`, `ScoreParseReviewAppServiceTest` and `GoldenCasesReplayTest`.
- UI targets: `src/composables/projectDetail/useScoreCandidateAdoption.spec.js` and `src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js`.
- Acceptance: 1382 eligible leaves total 100, with no p98+ contract item in default candidates; 1384 total 100 has exact original locator and same-scheme multi-table parent linkage; multi-scheme total is unset until selection; 5/4/3/2 grades max to 5 while cumulative 5+5+10 remains 20.
- After Mode B cleanup, rerun focused Maven/Vitest, Golden replay, `npm run build`, `mvn -f backend/pom.xml test`, `npm run agent:health-check` and applicable static/repository gates. Record all exits, failures and skips.
- Real project E2E requires a test deployment and reparse. Keep that state pending; a golden replay is not deployment acceptance.

## Project Structure

### Documentation

```text
specs/071-scoreparse-scheme-eligibility/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/score-parse-review-schemes.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code

```text
backend/src/main/java/com/xiyu/bid/scoreparse/
├── domain/                         # scheme, evidence, weight and parent policies
├── application/                    # extraction, persistence and review/adoption
├── infrastructure/openai/          # scheme-scoped chunks and fact merge
└── dto/                             # review scheme/fact response
src/composables/projectDetail/      # default candidate selection and sum
src/views/Project/stages/components/ # candidate and audit-only display
backend/src/test/java/com/xiyu/bid/scoreparse/ # domain, API and golden replay
```

**Structure Decision**: Extend the current scoreparse layers and existing review UI. Add no service module or schema unless RED tests prove the current immutable snapshot cannot store the required classification.

## Risks and Mitigations

- Irregular headings/table adjacency: keep ambiguous facts visible as unverified; do not widen eligibility from semantic similarity.
- Parent codes collide across schemes: include scheme identity in parent-resolution keys and test same-code cases.
- Legacy snapshots lack typed scheme evidence: expose them as unverified rather than inferring scope from free-text errors.
- Qualification and `run_summary` use existing MySQL `TEXT` columns. Fixture samples were measured at 546 bytes per qualification and 657 bytes per existing run summary, leaving over 64 KB per value; source quote/table-ID sizes remain subject to the column limit.
- Golden fixtures cannot prove deployment behavior: report test-system reparse as pending.

## Complexity Tracking

No Constitution violations. No new production services, modules or tables are planned.
