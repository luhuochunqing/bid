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
# `set -e` makes every assertion fail the block on the first non-zero exit;
# without it only the final command's status would propagate.
set -e

# 1. Marker line present, verbatim (whole-line match so the inline
#    references in this very doc — code samples, acceptance criteria —
#    don't inflate the count).
grep -Fx '<!-- tested by Claude, reviewed by Codex -->' docs/symphony-smoke.md

# 1b. Marker line appears exactly once (no accidental duplication).
#     `-x` matches the whole line; without it `grep -Fc` counts every line
#     that merely *contains* the marker substring (e.g. the two grep
#     invocations below and the acceptance-criteria bullet), which on this
#     file yields 4 and would make the assertion always fail.
test "$(grep -Fxc '<!-- tested by Claude, reviewed by Codex -->' docs/symphony-smoke.md)" -eq 1

# 2. Diff footprint is doc-only (rule 1 hot-path gate).
test "$(git diff --name-only origin/main..HEAD)" = "docs/symphony-smoke.md"

# 3. Hot-path blacklist (rule 1) — must match nothing.
! git diff --name-only origin/main..HEAD \
  | grep -E '^(backend/src/main/resources/db/migration-mysql/|backend/src/main/resources/db/rollback/migration-mysql/|backend/src/main/java/com/xiyu/bid/entity/|backend/src/main/resources/application.*\.yml|src/router/index\.js|src/views/Login\.vue|\.github/workflows/|\.githooks/)'

# 4. Branch naming (rule 2) — must start with agent/symphony/.
git rev-parse --abbrev-ref HEAD | grep -q '^agent/symphony/'
```

## Iteration log

Commit hashes are intentionally omitted from this table; they are
recoverable via `git log -- docs/symphony-smoke.md`. Recording them
inline forced every pass to re-pin the previous pass's `(this commit)`
placeholder, creating an unbounded review loop.

| Pass | Result |
|---|---|
| Pass 1 | Created file with marker line. |
| Pass 2 | Expanded Purpose + Acceptance criteria per Codex feedback. |
| Pass 3 | Added reviewer-runnable Verification block + iteration log so the artifact is self-auditable; re-verified rule-1 footprint is clean. |
| Pass 4 | Hardened Verification commands to assert (non-zero exit on failure) instead of merely printing. |
| Pass 5 | Closed the remaining self-reference loop in the log. |
| Pass 6 | Removed all inline commit hashes from the iteration log to break the `(this commit)` re-pin loop and stop introducing new review noise. No artifact-content change beyond the iteration log. |
| Pass 7 | Added step 1b to the Verification block asserting the marker line appears exactly once (closes a gap where a duplicate marker would still pass `grep -F`). Doc-only. |
| Pass 8 | Fixed self-inflicted break in step 1b: `grep -Fc` matched the marker *substring* inside the verification block's own sample commands and the acceptance-criteria bullet, so the count was 4 and the assertion could never pass on this file. Switched steps 1 and 1b to `grep -Fx`/`grep -Fxc` (whole-line match), which yields 1 as intended. Doc-only. |
| Pass 9 | Codex returned `changes_needed` with no inline detail; made the Verification block actually runnable end-to-end by emitting `set -e` as the first command (previously the block only *claimed* `set -e` semantics in a comment, so a paste-as-is would only surface the final command's exit). Doc-only. |

## Acceptance criteria

- [x] The literal line `<!-- tested by Claude, reviewed by Codex -->` exists
      in this file.
- [x] No hot-path from `WORKFLOW.md` rule 1 is touched (verified above).
- [x] Commit lands on `agent/symphony/CO-204-routing-test` (per issue body).
- [x] Diff footprint is doc-only (`docs/symphony-smoke.md`).
- [x] Verification commands reproduce locally without external services.
