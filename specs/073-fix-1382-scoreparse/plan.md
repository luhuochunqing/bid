# Implementation Plan: extraction snapshots and active versions
Approved 2026-09-27. Branch agent/codex/scoreparse-extraction-v2; base 1cd7d21c4.
## Architecture
Source evidence -> all extracted items snapshot -> independent diagnostics/computability -> transactional active version -> version-bound scoring -> frontend views.
Java21/Spring/JPA/MySQL, Python Sidecar, Vue/Vitest. Domain policies remain pure. Existing APIs and legacy records stay readable. Feature switch defaults off.
## Constitution check
Spec Kit existing073 updated before code; native agent-start-task satisfies existing feature branch hook (extension hook script absent). Real fixtures independently labelled. Incremental migration and rollback now expressly approved. No environment mutation or deployment.
## Authoritative task plan
See docs/exec-plans/active/scoreparse-1382-post2767-plan.md Tasks1-5, which supersede the earlier no-migration plan following user decisions.
## Acceptance boundary
Deterministic replay and integration tests prove implementation behavior; live model and deployed API/DB/browser acceptance remain separate evidence.
