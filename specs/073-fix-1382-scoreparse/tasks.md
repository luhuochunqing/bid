# Tasks: approved extraction and activation contract
- [x] T001 [US1] Source spans, logical continuity, anchored extraction, raw text restoration, independent real fixtures.
- [x] T002 [US2] Versioned full snapshots, nullable weights, semantics, compatibility migrations, activation CAS and lifecycle tests.
- [x] T003 [US2] Version-bound scoring and stale completion rejection.
- [x] T004 [US3] Simultaneous extraction/active frontend views, version differences and lifecycle tests.
- [x] T005 Cross-format regression, independent review, knowledge/pre-push prevention and delivery evidence.
Dependencies: T001 -> T002 -> T003 -> T004 -> T005. Each task reviewed before next implementation. Live acceptance remains explicitly outstanding until executed.
