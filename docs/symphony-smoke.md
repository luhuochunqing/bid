# Symphony Smoke Test

<!-- tested by Claude, reviewed by Codex -->

> Linear: [CO-205](https://linear.app/ericforai/issue/CO-205/claudecodex-routing-workflow-test)
> Branch: `agent/symphony/CO-204-routing-test`

## Purpose

End-to-end smoke artifact for the Claude-exec / Codex-review routing workflow.
Presence of the marker line on this branch proves Symphony claimed CO-205,
routed it to Claude (exec), and a workspace edit landed here — all on a
doc-only change with no rule-1 hot-path overlap. Review verdict lives on the
Integrator review state of the PR, not in this file.

## Verification (reviewer-runnable)

From the repo root after checking out `agent/symphony/CO-204-routing-test`:

```bash
set -e

# 1. Marker line present, verbatim, exactly once (whole-line fixed-string).
test "$(grep -Fxc '<!-- tested by Claude, reviewed by Codex -->' docs/symphony-smoke.md)" -eq 1

# 2. Diff footprint is doc-only (rule 1). Triple-dot range scopes to the
#    branch's own delta even if origin/main has advanced since fork.
test "$(git diff --name-only origin/main...HEAD)" = "docs/symphony-smoke.md"

# 3. Hot-path blacklist (rule 1) — must match nothing.
changed="$(git diff --name-only origin/main...HEAD)"
! printf '%s\n' "$changed" \
  | grep -E '^(backend/src/main/resources/db/migration-mysql/|backend/src/main/resources/db/rollback/migration-mysql/|backend/src/main/java/com/xiyu/bid/entity/|backend/src/main/resources/application.*\.yml|src/router/index\.js|src/views/Login\.vue|\.github/workflows/|\.githooks/)'

# 4. Branch naming (rule 2) — must start with agent/symphony/.
git rev-parse --abbrev-ref HEAD | grep -q '^agent/symphony/'

# 5. Branch tip is published — local HEAD matches the published remote ref.
test "$(git rev-parse HEAD)" = "$(git rev-parse origin/agent/symphony/CO-204-routing-test)"
```

## Acceptance criteria

- [x] The literal line `<!-- tested by Claude, reviewed by Codex -->` exists once.
- [x] Diff footprint is doc-only (`docs/symphony-smoke.md`).
- [x] No rule-1 hot path is touched
      (authoritative list: [`WORKFLOW.md` §1](../WORKFLOW.md#1-hot-paths-blacklist--do-not-modify)).
- [x] Commit lands on `agent/symphony/CO-204-routing-test` and is published.
- [x] Verification block reproduces locally without external services.
