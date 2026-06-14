---
name: push
description: |
  Push the Symphony agent branch to origin and run the project's
  pre-push gate (14 checks via `pre-push-gate.sh`). Never bypasses
  `--no-verify`.
---

# Push

## Goals

- Push only branches prefixed with `agent/symphony/`.
- Let `pre-push` hook run all 14 gates (agent-locks, line-budget,
  architecture tests, Flyway checks, etc.).
- Surface any failure clearly so the agent can fix and retry.

## Preconditions

- On a branch matching `agent/symphony/*` (the WORKFLOW.md
  `agent.branch_prefix` configures Symphony to create such branches).
- Latest commit has been verified locally via the commit skill.
- Local validation gates passed (see WORKFLOW.md rule 6).

## Steps

1. `git branch --show-current` — verify prefix.
2. `git status -s` — must be clean.
3. `git log origin/main..HEAD --oneline` — preview commits to be pushed.
4. `git push origin HEAD` — let `pre-push` hook run.
5. If hook fails:
   - Read the failing gate's error message.
   - Fix the issue locally (do NOT pass `--no-verify`).
   - Amend or new commit + retry push.
6. On success, the URL in the push output is your branch's head.

## If you accidentally try to push a non-prefixed branch

```bash
# Renaming the local branch is allowed:
git branch -m <old-name> agent/symphony/<old-name>
# Then push:
git push origin HEAD
```

## Forbidden

- `git push --no-verify` (the `scripts/git` wrapper rejects it; even with
  the wrapper bypassed, the `.githooks/git-push-wrapper.sh` will catch it).
- Pushing to `main` directly.
- Force-push to a branch that has been PR'd (use `git push --force-with-lease`
  only on a branch you own and that has not been reviewed yet).
- Pushing to any remote other than `origin`.
