# Data Model: 评分方案资格与复核快照

## LogicalScheme

One source-backed scoring rule set. One scheme may cover several physical tables.

| Field | Meaning |
|---|---|
| `schemeId` | Stable identity within one parse task; included in fact and parent-resolution keys |
| `title` | Original scoring-section heading or equivalent source label |
| `sourceRange` | Original text/table scope that identifies the scheme |
| `tableIds` | Physical source tables owned by this scheme |
| `layoutStatus` | `SINGLE_TABLE`, `MULTI_TABLE_SAME_SCHEME`, `TEXT_ONLY`, or `UNKNOWN` |
| `totalEvidenceStatus` | `VERIFIED`, `UNKNOWN`, or `CONFLICT` |
| `declaredTotal` | Number only when source evidence verifies one scheme total |
| `totalQuote` / `totalLocator` | Exact original paragraph/cell text and locator for verified totals |
| `selectionRequired` | True when more than one independent scheme is present |
| `factIds` | Fact IDs assigned to this scheme in the immutable task summary |

`layoutStatus` and `totalEvidenceStatus` are independent. Multiple physical tables alone do not make total evidence conflicting.

## ScoringFact

Persisted in the existing `score_parse_fact` row with original cells/evidence in `score_parse_evidence`.

| Field | Meaning |
|---|---|
| `factId` | Immutable identity in one parse task |
| `schemeId` | Logical scheme assignment; an audit-only scope can have its own non-selectable identity |
| `scopeStatus` | `IN_SCHEME`, `OUT_OF_SCHEME`, or `SOURCE_UNVERIFIED` |
| `candidateEligible` | True only when its original locator and quote verify membership in a logical scoring scheme; local weight and parent validation remain separately visible in `validationStatus` |
| `exclusionReason` | Stable reason code used by the reviewer and audit log |
| `sourceTableId`, `sourceCellIds` | First physical table and ordered exact source cells; a multi-table source is valid only when the cells share one converter-proven `logicalRowId`. Text facts use their text locator. |
| `parentFactId` | Parent identity scoped to the same logical scheme |
| `childAggregation` | Existing aggregation rule such as `SUM` or `MUTUALLY_EXCLUSIVE` |
| `weight`, `weightEvidence` | Effective cap and original score evidence; mutually exclusive alternatives resolve to their maximum |

Facts with `OUT_OF_SCHEME` or `SOURCE_UNVERIFIED` remain visible in the audit group and cannot be included in default selection, candidate totals or server-side default adoption. In-scheme facts with invalid local evidence remain visible but do not contribute to effective-leaf totals.

## CandidateSet

Derived from one explicitly selected `LogicalScheme`.

- `facts`: only `candidateEligible=true` facts in the selected scheme; parents remain structural facts and contribute according to `childAggregation`.
- `candidateTotal`: relation-aware effective total; `SUM` parents contribute their child leaves, non-sum parents contribute once, and unknown parent relations do not produce an adoptable total.
- `selectionRequired`: true when multiple independent schemes exist and no selection was made.
- `isClosed`: true when a verified declared total equals the effective leaf total and all required evidence/parent relations are valid.

## Persistence mapping

- `score_parse_fact` retains source, parent, weight and validation facts.
- `score_parse_evidence` stores a `SCHEME_QUALIFICATION` row for each fact. Its explanation JSON carries scheme ID/title, scope status, eligibility/reason, declared total and its evidence, layout, and member table IDs; `quoted_text` stores the locator. Original fact/source and weight evidence remain in their existing rows.
- Sidecar v2.1 emits a stable cell `logicalRowId` for every row; IDs are shared across physical tables only for proven continuation fragments. The existing `sourceCellIds` JSON list stores every ordered fragment ID; Java resolves every ID back to its physical table/cell before qualification. No DB migration is required.
- `score_parse_task.run_summary` remains unchanged and continues to store pipeline diagnostics/table splits. The review layer groups per-fact qualifications by scheme ID, avoiding a second scheme manifest that could disagree with the evidence rows.
- `ScoreParseReviewDTO` exposes the grouped scheme list and each fact's `schemeId`, `scopeStatus`, `candidateEligible` and `exclusionReason`. Old snapshots lacking qualification evidence map to `SOURCE_UNVERIFIED`.

### Existing storage capacity check

- `SCHEME_QUALIFICATION` is 20 characters in the existing `VARCHAR(64)` kind field (44 characters remain).
- A serialized qualification for the four-table 1384 fixture is 546 UTF-8 bytes; its three facts use 1,638 bytes across three independent evidence rows.
- The existing two-table `run_summary` fixture is 657 UTF-8 bytes. Both explanation and run summary are MySQL `TEXT` (65,535-byte maximum), so the fixture samples leave over 64 KB per value. This measures the current fixtures, not an enforced upper bound for unusually long source quotes or table lists.

Old snapshots lacking scheme membership are interpreted as `SOURCE_UNVERIFIED`, with no inferred declared total and no default adoption.
