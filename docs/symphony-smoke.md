# Symphony Smoke Test

<!-- tested by Claude, reviewed by Codex -->

> Linear: [CO-205](https://linear.app/ericforai/issue/CO-205/claudecodex-routing-workflow-test)
> Branch: `agent/symphony/CO-204-routing-test`

## Purpose

This file is the smoke-test artifact for the Claude-exec / Codex-review
routing workflow. Its presence on the task branch proves that:

1. Symphony claimed Linear issue **CO-205** and routed it to Claude (exec).
2. Claude landed a workspace edit on `docs/symphony-smoke.md`.
3. The marker comment above is the exact string requested by the issue
   description, preserved verbatim.
4. Codex (review) was given the diff and returned actionable feedback, which
   Claude applied on the same branch.

## Acceptance criteria

- [x] The literal line `<!-- tested by Claude, reviewed by Codex -->` exists
      in this file.
- [x] No hot-path from `WORKFLOW.md` rule 1 is touched.
- [x] Commit lands on `agent/symphony/CO-204-routing-test` (per issue body).
- [x] Diff footprint is doc-only (`docs/symphony-smoke.md`).
