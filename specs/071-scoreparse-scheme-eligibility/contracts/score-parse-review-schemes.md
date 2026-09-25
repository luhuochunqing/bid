# Score Parse Review Scheme Contract

The review response adds typed scheme data while retaining the existing `facts`, `declaredTotal` and review fields for older clients.

```json
{
  "schemeSelectionRequired": false,
  "selectedSchemeId": "scheme-1",
  "schemes": [
    {
      "schemeId": "scheme-1",
      "title": "综合评分办法",
      "tableIds": ["table-a", "table-b"],
      "layoutStatus": "MULTI_TABLE_SAME_SCHEME",
      "totalEvidenceStatus": "VERIFIED",
      "declaredTotal": 100,
      "totalQuote": "本项目总分为100分",
      "totalLocator": "text:line[18]",
      "selectionRequired": false
    }
  ],
  "facts": [
    {
      "factId": "fact-1",
      "schemeId": "scheme-1",
      "scopeStatus": "IN_SCHEME",
      "candidateEligible": true,
      "exclusionReason": null,
      "parentFactId": "fact-parent",
      "childAggregation": "SUM"
    },
    {
      "factId": "fact-2",
      "schemeId": "audit-contract-section",
      "scopeStatus": "OUT_OF_SCHEME",
      "candidateEligible": false,
      "exclusionReason": "ITEM_SCHEME_SCOPE_MISMATCH"
    }
  ]
}
```

Per-scheme `layoutStatus` values:

- `SINGLE_TABLE`: one logical scheme is backed by one physical table.
- `MULTI_TABLE_SAME_SCHEME`: one logical scheme spans multiple physical tables.
- `TEXT_ONLY`: the scheme has source text evidence and no owned physical scoring table.
- `UNKNOWN`: legacy or incomplete qualification does not prove the layout.

Independent schemes are represented as separate entries in `schemes`; the response-level `schemeSelectionRequired` is true until the user selects one. Scheme multiplicity is not a physical-table layout status.

`totalEvidenceStatus` values:

- `VERIFIED`: an exact source quote and locator support one total.
- `UNKNOWN`: no scheme-level total was verified.
- `CONFLICT`: two or more explicit source-backed totals for the same logical scheme disagree. The response-level `adoptionStatus` projects this as `CONFLICTING_TOTAL`.

Default candidate totals are calculated only after a scheme is selected and include candidate-eligible effective leaves. The existing adoption request shape remains valid. When no fact IDs are supplied, the server may default to all eligible leaves only if exactly one selectable scheme exists. For multiple schemes, clients submit IDs from one selected scheme; the server validates scheme membership and eligibility for every submitted ID and rejects cross-scheme or excluded facts.
