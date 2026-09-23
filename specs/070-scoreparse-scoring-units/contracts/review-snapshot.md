# Review Snapshot Contract Addition

Existing review endpoint and envelope remain unchanged. Each fact in `facts[]` adds an optional field:

```json
{
  "factId": "fact-1",
  "parentFactId": null,
  "childAggregation": "SHARED_CAP",
  "weight": 5,
  "sourceCellIds": ["cell-page-2-r4-c3"],
  "weightEvidence": "最高得5分，满足下列条件之一"
}
```

Allowed values: `SUM`, `SHARED_CAP`, `BEST_OF`, `MUTUALLY_EXCLUSIVE`, `UNKNOWN`.

- Field is optional for backward-compatible clients and legacy rows.
- A missing/null value is interpreted as `SUM` by the backend.
- The field describes how this fact's direct children contribute to the parent's scoring rule.
- Every child fact and existing evidence item remains in the response.
- This contract does not authorize review submission to auto-change formal score items; existing adoption rules apply.
