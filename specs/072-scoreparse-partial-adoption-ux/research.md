# Research

## Current implementation findings

- `ScoreCandidateReviewCard.vue` shows a warning and disables adoption whenever candidate total differs from declared total, including the valid partial case 91 < 100.
- `ScoreParseReviewAppService.handleAdoptCandidates` recomputes effective candidate weight and rejects any result unequal to declared total before persistence. Frontend-only changes cannot succeed.
- The service writes the actual adopted sum to `ScoreParseTask.originalTotalWeight`, which must remain the actual sum; declaration evidence lives in scheme qualification facts.
- Candidate eligibility is derived from `SCHEME_QUALIFICATION` evidence and checked again by the backend. This work must not bypass it.
- Selected action requests include adjusted weights; `ScoreReviewAdoptionPolicy` previously preserved an explicitly selected negative value. Once partial totals are accepted, a negative adjustment could offset a larger item. The pure domain policy must reject any negative item before producing candidates.
- The component directly prints `validationStatus`, `validationMessage`, or `fallbackReason`; those fields may contain opaque validator names or machine diagnostics.

## Decisions

- Partial adoption is a human-confirmed acceptance of the extracted subset. The missing weight remains a gap; do not infer or synthesize missing scoring items.
- Preserve upper-bound and provenance protections. Unknown/conflicting totals and multi-table unknown totals retain their existing behavior.
- Translate only presentation. Keep raw diagnostics unchanged in backend response/audit.
