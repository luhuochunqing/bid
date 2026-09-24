# Research: 评分方案资格与分组

## Decision 1: 方案资格必须进入默认候选数据流

**Decision**: Keep all score-like facts that the extraction pipeline observes. Attach a typed scheme-scope result to each fact and use the same result to build the review snapshot, candidate total, default adoption payload and backend adoption set. Treat missing scope proof as `SOURCE_UNVERIFIED`.

**Evidence**: The current source evidence policy appends `ITEM_SCHEME_SCOPE_MISMATCH` to diagnostics without dropping the fact. The structured-table persistence path stores all review facts, the review API reads all stored facts, and the UI composable exposes all of them as candidate facts and sums every leaf. This allows a diagnostic to be correct while the visible candidate set remains wrong.

**Alternatives considered**:

- Hide facts in the Vue card only — rejected because the backend default-adoption path still accepts every stored fact.
- Drop out-of-scope facts during extraction — rejected because the user needs an auditable exclusion and its source locator.
- Treat all `REVIEW_REQUIRED` facts as excluded — rejected because a task may require review for a scheme-level total while individual in-scope facts remain valid.

## Decision 2: Scope model input and merge by a logical scheme

**Decision**: Resolve scheme boundaries and table membership from the original document/context before chunk extraction. Tag each extracted chunk with its scheme identity. Keep score-like facts found outside the selected scheme in isolated audit groups. Merge and resolve parent references only within one logical scheme, which may contain multiple physical tables.

**Evidence**: The multimodal extractor currently splits whole tender text into overlapping 24k chunks. The chunk merger deduplicates facts globally and preserves conflicting/missing total symptoms for later validation; it does not know the logical scoring-scheme boundary. The original scoring context already has heading and table ownership helpers that can be extended as the source of truth.

**Alternatives considered**:

- Keep whole-file extraction and add more final validators — rejected because validation cannot recover lost cross-table parents or reliably distinguish identical codes in different schemes.
- Split only on fixed character/page count — rejected because those boundaries do not represent scoring-section ownership.
- Group by table ID — rejected because one logical scheme can span several physical tables.

## Decision 3: Separate scheme layout from total-evidence state

**Decision**: Report logical-scheme count/table layout independently from total evidence. A scheme's total status is `VERIFIED`, `UNKNOWN` or `CONFLICT`; the response-level adoption state projects a source conflict as `CONFLICTING_TOTAL`. A scheme layout is `SINGLE_TABLE`, `MULTI_TABLE_SAME_SCHEME`, `TEXT_ONLY` or `UNKNOWN`; multiple-scheme selection is a separate response field. Never infer conflict from the number of physical tables or add table totals without a source-backed same-scheme relationship.

**Evidence**: The prior review total policy returned `CONFLICTING_TOTAL` when table splits were multiple and the total was null. The revised policy leaves totals `UNKNOWN` unless the source context proves an explicit conflict; multiple schemes require separate selection. The DTO no longer sums numeric table splits without a scheme key.

**Alternatives considered**:

- Keep one `adoptionStatus` for all states — rejected because unknown, conflict and multi-table same-scheme are independent conditions.
- Sum all physical-table totals — rejected because several tables may be components of one scheme or may belong to separate schemes.

## Decision 4: Persist scheme qualification with each immutable fact

**Decision**: Store typed scheme qualification in `score_parse_evidence.kind=SCHEME_QUALIFICATION`; its JSON explanation carries scheme ID/title, eligibility, exclusion reason, total evidence and table membership. Build the review scheme list by grouping those persisted fact qualifications. Keep `score_parse_task.run_summary` unchanged; it continues to hold pipeline diagnostics and physical table splits, not a duplicate scheme manifest. Add no migration.

**Evidence**: `ScoreParseFactEvidenceWriter` writes one qualification evidence row for each persisted fact, and `ScoreParseReviewFactMapper.schemes` groups the typed fact DTOs by scheme ID. Existing `score_parse_evidence.kind` is `VARCHAR(64)` and both `explanation` and `score_parse_task.run_summary` are `TEXT`; no schema change is needed. With the four-table 1384 fixture, serialized qualification is 546 UTF-8 bytes per fact (1,638 bytes for its three facts); the existing two-table run-summary fixture is 657 bytes. Each sample TEXT value has over 64 KB of column headroom. These are fixture measurements, not worst-case limits; source quote and table-ID sizes still need to fit the existing TEXT columns.

**Alternatives considered**:

- Duplicate a scheme manifest in `run_summary` — rejected because review reconstructs the same scheme fields from immutable per-fact evidence, creating two sources that could drift.
- Add fact columns and a migration — rejected because typed evidence already round-trips the complete qualification and table membership.

## Decision 5: Score alternative rubric tiers by maximum, retain cumulative relationships

**Decision**: For mutually exclusive rubric levels, the item cap is the maximum score stated by its alternatives. For cumulative children, preserve parent-to-child aggregation and sum only effective leaves. Test both in the same extraction/validation contract.

**Evidence**: Current weight evidence validation expects one distinct numeric score and reports 5/4/3/2 alternatives as unknown. Existing `ScoreUnitPolicy` already distinguishes child aggregation relations; the correction belongs in score-range verification, not by changing all parents to a max rule.
