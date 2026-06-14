---
name: pull
description: |
  Sync the Symphony branch with latest `origin/main` before any
  implementation or commit. Resolves trivial conflicts automatically
  and surfaces non-trivial ones for agent handling.
---

# Pull

## Goals

- Keep the agent's working branch current with `origin/main`.
- Detect conflicts early (before committing, not at PR time).
- Record pull/sync result in the workpad `### Notes` section.

## Preconditions

- On a branch matching `agent/symphony/*`.
- Working tree is clean (or only contains intended WIP).

## Steps

1. `git fetch origin main --prune`
2. `git rebase origin/main`
3. If rebase conflicts:
   - For text/code conflicts: resolve manually, `git add <files>`,
     `git rebase --continue`.
   - For lockfile / generated conflicts: re-run
     `npm install` or `mvn -DskipTests dependency:resolve` as needed.
4. Verify the rebase result:
   ```bash
   git log --oneline -3
   git status -s
   ```
5. Record in workpad `### Notes`:
   ```
   pull skill evidence: rebased onto origin/main @<short-sha>, result: clean
   ```

## If the branch has been force-pushed by an external process

```bash
git fetch origin
git reset --hard origin/agent/symphony/<branch-name>
```

Only do this if you are the sole owner of the branch (no other agent
or human has commits on it).

## Forbidden

- `git pull` without `--rebase` (creates merge commits that pollute
  the linear history).
- Pushing the rebase result without running pre-push gates.
