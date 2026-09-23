# Data Model: Score Parsing Scoring Units

## ScoreParseFact

Persisted, source-grounded fact extracted from one scoring clause.

| Field | Meaning | Compatibility |
|---|---|---|
| `factId` | Stable fact identity in parse task | Existing |
| `parentFactId` | Direct parent fact, null for root | Existing |
| `childAggregation` | Scoring relationship between this fact and direct children | Optional; null reads as `SUM` for legacy data |
| `weight` | Declared parent/leaf scoring value | Existing |
| `sourceCellIds` | All canonical source cells for the fact | Existing serialized representation, may include multiple IDs |
| `weightEvidence` / evidence rows | Verbatim text and cell-level provenance | Existing; preserve exactly |
| `validationStatus` | Validation outcome | Existing; UNKNOWN relationship requires review |

## ChildAggregation

- `SUM`: direct children are independently scored and summed. Recurse to their effective units; terminal facts count their own weight.
- `SHARED_CAP`: conditions share the parent cap. Count the parent once; descendants remain descriptive/review facts.
- `BEST_OF`: alternatives compete for the parent score. Count the parent once; no candidate child sum.
- `MUTUALLY_EXCLUSIVE`: only one branch can apply. Count the parent once; do not sum branches.
- `UNKNOWN`: relation unresolved. Facts remain reviewable but cannot pass automatic publication.

## Effective Score Unit

Pure policy projection over facts grouped by `parentFactId`. A non-additive parent is the sole effective scoring unit for its subtree. A SUM parent with children delegates score units to those children. This is a derived view and is not stored separately.

## ScoreParseReviewSnapshot

Existing immutable parse task snapshot plus `childAggregation` in each fact projection. All child descriptions and evidence remain individually addressable; the relationship does not replace evidence or rewrite published score items.

## Persistence Change

`score_parse_fact.child_aggregation VARCHAR(32) NULL`, added with V1217. No backfill. U1217 drops only this column. Entity mapping interprets SQL null as legacy SUM at read/use boundary.
