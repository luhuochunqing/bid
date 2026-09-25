# Implementation Plan: 评分解析按方案判定候选资格与总分闭环

**Branch**: `071-scoreparse-scheme-eligibility` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/071-scoreparse-scheme-eligibility/spec.md`

## Summary

把评分方案身份和事实资格作为解析数据流的一部分：先隔离可审计的方案外事实，再按逻辑方案切分和合并文本/表格证据，保存可回指的方案总分与资格快照；复核 API、默认采纳和 UI 使用相同的资格结果。明确区分互斥档位的最高分与累计父子项合计。本轮新增根因修复顺序：先统一 DOCX heading/sourceSection 转换契约，再让资格规则只接受该契约可证明的章节归属，最后以测试站项目 1384 的真实 DOCX 重解析验收。

## Technical Context

**Language/Version**: Java 21 (scoreparse backend), Vue 3 / JavaScript (review UI)
**Primary Dependencies**: Spring Boot 3.2, Jackson/JPA, Vue 3, Vitest
**Storage**: Existing `score_parse_fact` and per-fact `score_parse_evidence`; `run_summary` remains unchanged; no schema migration planned.
**Testing**: Maven JUnit/GoldenCasesReplayTest; Vitest; build and local repository gates
**Target Platform**: XiYu test/development application
**Project Type**: Existing scoreparse backend API plus project review UI
**Performance Goals**: Preserve extraction-call and source-size budgets; reduce irrelevant scoring prose per scheme.
**Constraints**: Preserve original text/table locators, existing `AUTO_APPROVED` publication guard and legacy review compatibility; no schema change; test deployment/reparse is authorized only for project 1384, never production.
**Scale/Scope**: Scheme resolution, source-backed total evidence, candidate/review persistence, default adoption and the score review card for projects 1382/1384.

## Constitution Check

- FP-Java: scope and scoring rules remain pure domain functions; JPA/API/UI stay in their existing layers.
- Real API Only: add no mock runtime path. Regression inputs are golden fixtures consumed by production extraction/policy code.
- TDD: each requested behavior starts with a failing domain, orchestration, API or UI assertion.
- Simplicity and line budgets: reuse existing scoreparse policies, chunk merger, immutable parse facts and evidence rows; add no new service/module.
- Database: use existing fact/evidence tables; do not duplicate scheme qualification in `run_summary`. If tests prove typed eligibility cannot be persisted in current evidence rows, stop before schema edits, amend this plan/Entry Gate, acquire a migration lock, and add a reversible Flyway pair.
- Release boundary: deploy the reviewed artifact only to the test environment, then reparse project 1384. Do not deploy to production, formally adopt candidates, or mutate production data.

## Design Decisions

1. **Eligibility is data, not a warning.** Keep every observed score-like fact and original evidence. Persist per-fact scheme membership as typed evidence; `OUT_OF_SCHEME` and `SOURCE_UNVERIFIED` are review-only. Only locally valid `IN_SCHEME` facts participate in candidate totals or default adoption. Backend rechecks before adoption.
2. **Resolve schemes before merging chunks.** Identify scoring-section bounds, scheme identity, member tables and explicit total evidence from the artifact/original text. Slice model input by scheme. Score-like material outside the selected scheme stays in a separately marked audit group. Merge only within a scheme; resolve parent codes after assembling all scheme facts.
3. **Keep topology and total evidence orthogonal.** A scheme can have one or many physical tables. Its total independently has `VERIFIED`, `UNKNOWN` or `CONFLICT` state; the response-level adoption state is `CONFLICTING_TOTAL`. Multiple schemes require selection; totals are never summed merely because there are multiple table splits.
4. **Bind totals to original evidence.** Explicit totals carry a quote and source locator (`text:line[n]` or table/cell). Local row maxima and category subtotals cannot supply scheme total.
5. **Apply aggregation by relation.** `MUTUALLY_EXCLUSIVE` grade alternatives use their highest score. `SUM` children close against their parent; parents and leaves are not double-counted.
6. **Reuse existing snapshots.** `score_parse_evidence.kind/explanation` records per-fact qualification, including scheme/table membership and the source-backed total; the review DTO groups these rows into schemes. `run_summary` remains pipeline telemetry/table-split metadata and receives no duplicate scheme manifest. Old snapshots without scope evidence are `SOURCE_UNVERIFIED` and excluded by default.
7. **Adoption is single-scheme.** One eligible scheme may be selected by default. Multiple schemes require selection and submit only that scheme's fact IDs. Backend rejects missing selection, cross-scheme fact sets and ineligible IDs.

## DOCX Input Contract (new first slice)

- The existing `/convert` response exposes heading metadata from `extract_headings`; it must recognize Markdown bold wrapped headings while keeping `charStart`/`charEnd` in the original Markdown coordinate space. Heading text is returned without Markdown emphasis.
- The existing `/extract-tables` table-v2 response must derive `sourceSection` from paragraphs and tables in DOCX body order, not `doc.tables` alone. Its value is a stable heading breadcrumb using ` > `; table keywords remain only a content classifier and cannot establish section membership. Unknown section remains `null`/empty according to the existing Java DTO contract.
- Use the existing `python-docx` dependency already installed by the Sidecar for body iteration. Walk block elements once; keep heading stack and table index in that pass. Avoid a second DOCX parser or a post-hoc fuzzy text binder. This preserves O(paragraphs + tables + cells) traversal.
- Java domain parsing must tolerate inline Markdown emphasis around total values and headings without changing line numbers, so the existing `text:line[n]` locator remains valid. A synthetic document scheme inferred only from `declaredTotal != null && any isScoreTable` is not evidence and must be removed; explicit heading/table section evidence remains valid.

## 2026-09-25 follow-up: cross-table DOCX evidence

The real task `e24bdfdd-71c5-4ab7-b04a-c496744bb3e8` verified the 100-point total but qualified only 56 points. The other 44 points were extracted but could not be bound: price 30 was affected by MarkItDown emphasis (`**30**`), overall plan 10 had a non-verbatim `and/和` quote, and project team 4 spans physical tables `t-0008`/`t-0009`.

- Sidecar emits an additive `logicalRowId` for every canonical cell. Only a unique continuation candidate may share an ID across adjacent same-section tables: column counts must match, the predecessor must have a score-value header, the continuation row must carry detail text while scoring/identity columns are blank, and a later row in the current table must contain a numeric score plus criterion text outside sequence/number columns. The broad keyword classifier alone is not continuation evidence; an ordinary table containing “评审” cannot link. Otherwise the rows remain separate. Keep physical table IDs, cell IDs, row/column coordinates and XML paths unchanged.
- Java carries the ordered original cell IDs in the existing `sourceCellIds[]` field. The binder may join cells only when all IDs resolve to one `logicalRowId`; qualification also verifies same scheme and exact normalized source quote. This avoids schema migration and retains the existing review DTO/persistence shape.
- If a model quote fails exact matching, rebuild it from original cells only when exact dimension text and original score evidence identify exactly one logical row. A missing or ambiguous match remains unverified. Never approve through fuzzy similarity or weight-only lookup.
- Keep JSON additive and backward-compatible: v2.1 emits a `logicalRowId` on every cell; rows not linked across tables keep their physical-table row ID. Java accepts prior v2.0 artifacts with no such field and treats each cell as lacking cross-table proof. Python-docx processing stays in Sidecar; qualification remains in pure Java domain policy.

## Implementation Phases

1. Establish Sidecar RED tests for bold heading metadata, interleaved section paths, a real-shaped continuation row, and an adjacent-table false-positive countercase; implement the additive v2.1 logical-row contract.
2. Add Java RED tests for ordered cell sources across a proven logical row, missing/mixed-source rejection, strict cross-scheme rejection, and unique dimension-plus-score quote reconstruction; then update binding and qualification without weakening exact-source checks.
3. Register a synthetic/redacted 1384 continuation Golden and retain the existing 1382/1384, parent-child, mutually-exclusive/cumulative-weight and UI regressions.
4. After Mode B cleanup, fresh verification and read-only review, deploy the exact commit to ENV=test only when its readiness and migration preconditions pass; reparse only project 1384 and compare `/review`, `/items`, original formal items and rendered review card. Do not treat Golden replay as acceptance.

## Validation Plan

- Java targets: `OriginalScoringContextThreeDefectsTest`, `OriginalScoreEvidencePolicyTest`, `MultimodalScoreFactValidatorTest`, `MultimodalFactParentCodeNormalizerTest`, `ScoreUnitPolicyTest`, `ScoreFactChunkMergerTest`, `OpenAiMultimodalScoreFactExtractorTest`, `MultimodalStructuredExtractionHandlerTest`, `ScoreParseReviewAppServiceTest` and `GoldenCasesReplayTest`.
- UI targets: `src/composables/projectDetail/useScoreCandidateAdoption.spec.js` and `src/views/Project/stages/components/ScoreCandidateReviewCard.spec.js`.
- Acceptance: 1382 eligible leaves total 100, with no p98+ contract item in default candidates; 1384 total 100 has exact original locator and same-scheme multi-table parent linkage; multi-scheme total is unset until selection; 5/4/3/2 grades max to 5 while cumulative 5+5+10 remains 20.
- After Mode B cleanup, rerun focused Maven/Vitest, Golden replay, `npm run build`, `mvn -f backend/pom.xml test`, `npm run agent:health-check` and applicable static/repository gates. Record all exits, failures and skips.
- Project 1384 test-site deployment and reparse are within the authorized delivery target; a Golden replay is not deployment acceptance. Project 1382 remains a local redacted replay only.

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
- Bold text may be emphasis rather than a heading: only accept lines that satisfy the existing structural heading patterns or explicit Word outline styles. Golden fixtures cannot prove deployment behavior: finish with the authorized test-site reparse.

## Complexity Tracking

No Constitution violations. No new production services, modules or tables are planned.
