---
name: land
description: |
  Land a Symphony-generated PR by monitoring Gitee review comments,
  resolving them, waiting for CI, and letting
  `auto-enable-merge-on-approved.yml` squash-merge when green. Use
  when the Linear issue is in `Merging` state.
---

# Land

## Goals

- Keep PR conflict-free with `origin/main`.
- Keep Gitee CI green and fix failures.
- Let `auto-enable-merge-on-approved.yml` squash-merge once human
  approval + 1 review + all required checks are green.
- Do not call `gitee-pr-helper.sh merge` directly — that workflow
  is triggered by GitHub Actions mirror.

## Preconditions

- `GITEE_TOKEN` is set (host's env).
- `gh` is NOT used; do not assume GitHub CLI.
- You are on the PR branch with a clean working tree.

## Steps

1. Locate the PR number:
   ```bash
   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh list
   ```
2. Confirm local gauntlet is green before any push:
   ```bash
   npm run check:front-data-boundaries
   npm run check:doc-governance
   npm run check:line-budgets
   npm run build
   cd backend && mvn -Dtest=ArchitectureTest test
   ```
3. Check mergeability:
   ```bash
   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh check <num>
   ```
4. If `state == conflict`, run `pull` skill to rebase onto `origin/main`,
   then `push` skill.
5. Watch CI (Gitee Go pipeline):
   ```bash
   # Gitee Go runs on gitee.com; poll via API
   curl -H "Authorization: token $GITEE_TOKEN" \
     "https://gitee.com/api/v5/repos/allinai888/bid/pulls/<num>" \
     | jq '.merge_status, .check_runs[]? | {name, status}'
   ```
6. If any check fails, fetch logs, fix locally, commit, push, restart.
7. Wait for review feedback:
   ```bash
   GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh approve <num>
   ```
   Look for `approved` count >= 1 and no `changerequested` / `dismissed`.
8. When all checks are green AND review is approved, the
   `auto-enable-merge-on-approved.yml` workflow (in `.github/workflows/`)
   will enable auto-merge on the Gitee mirror. No action required from
   Symphony agent.
9. After merge completes (poll via `gitee-pr-helper.sh check <num>`
   until `state == merged`), move the Linear issue to `Done` via
   `linear_graphql` `issueUpdate` with the `Done` stateId.

## Review handling

- Review feedback arrives as Gitee PR comments (top-level + inline).
- Treat every actionable comment as blocking until one of:
  - code/test/docs updated to address it, OR
  - explicit, justified pushback reply posted on that thread.
- Reply prefix: `[symphony]` (matches linear skill comment convention).
- For inline review comments, reply with `[symphony] <rationale>` and
  link to the addressing commit.

## Forbidden

- `gh pr merge` (this project does not use GitHub for merge).
- `./scripts/gitee-pr-helper.sh merge` (auto-merge workflow owns this).
- Enabling auto-merge by hand — the workflow handles it.

## Branch cleanup after merge

After the Linear issue is `Done`, the project auto-deletes head branches
via `agent-branch-cleanup.yml` (on the GitHub Actions mirror) and
`scripts/sweep-merged-branches.sh` (on the next early-morning sync).
Symphony agents do not need to delete branches manually.
