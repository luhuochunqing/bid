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

## Verification (reviewer-runnable)

Run from the repo root after checking out `agent/symphony/CO-204-routing-test`:

```bash
# Every assertion uses `set -e` semantics: a non-zero exit fails the block.

# 1. Marker line present, verbatim.
grep -F '<!-- tested by Claude, reviewed by Codex -->' docs/symphony-smoke.md

# 2. Diff footprint is doc-only (rule 1 hot-path gate).
test "$(git diff --name-only origin/main..HEAD)" = "docs/symphony-smoke.md"

# 3. Hot-path blacklist (rule 1) — must match nothing.
! git diff --name-only origin/main..HEAD \
  | grep -E '^(backend/src/main/resources/db/migration-mysql/|backend/src/main/resources/db/rollback/migration-mysql/|backend/src/main/java/com/xiyu/bid/entity/|backend/src/main/resources/application.*\.yml|src/router/index\.js|src/views/Login\.vue|\.github/workflows/|\.githooks/)'

# 4. Branch naming (rule 2) — must start with agent/symphony/.
git rev-parse --abbrev-ref HEAD | grep -q '^agent/symphony/'
```

## Iteration log

| Pass | Result |
|---|---|
| Pass 1 (c17c9d7e) | Created file with marker line. |
| Pass 2 (4ef28aa4) | Expanded Purpose + Acceptance criteria per Codex feedback. |
| Pass 3 (7a8b624c) | Added reviewer-runnable Verification block + iteration log so the artifact is self-auditable; re-verified rule-1 footprint is clean. |
| Pass 4 (this commit) | Hardened Verification commands to assert (non-zero exit on failure) instead of merely printing; replaced stale "this commit" self-reference in Pass 3 row with its actual hash `7a8b624c`. |

## Acceptance criteria

- [x] The literal line `<!-- tested by Claude, reviewed by Codex -->` exists
      in this file.
- [x] No hot-path from `WORKFLOW.md` rule 1 is touched (verified above).
- [x] Commit lands on `agent/symphony/CO-204-routing-test` (per issue body).
- [x] Diff footprint is doc-only (`docs/symphony-smoke.md`).
- [x] Verification commands reproduce locally without external services.
