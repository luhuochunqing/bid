---
name: commit
description: |
  Produce clean, logical commits during Symphony implementation runs.
  Respects the project's pre-commit hook (15+ checks). Never bypasses
  `--no-verify`.
---

# Commit

## Goals

- One logical change per commit.
- Conventional Commits subject: `feat(scope): summary` or
  `fix(scope): summary` etc.
- Body explains why, not what.
- Authored as `Symphony Bot <symphony-bot@xiyu.local>` (set in
  `hooks.after_create` of WORKFLOW.md).

## Preconditions

- Working tree has staged changes that pass `npm run check:front-data-boundaries`
  and `cd backend && mvn -Pjava-quality checkstyle:check` (where applicable).
- `git status` is clean except for staged files.

## Steps

1. `git status -s` — review unstaged + staged files.
2. `git diff --staged --stat` — sanity check the change footprint.
3. `git add <files>` — never `git add .` or `git add -A` (might pull in
   `.runtime/`, `erl_crash.dump`, or other local-only artifacts).
4. Write the commit subject. Format:
   ```
   <type>(<scope>): <subject ≤ 72 chars>
   ```
   Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `style`.
   Scopes commonly used in xiyu-bid-poc: `bidding`, `tender`, `auth`,
   `frontend`, `backend`, `symphony`.
5. Write the body (optional, but preferred for non-trivial changes):
   ```
   - <what changed>
   - <why>
   - <test evidence, e.g. "mvn -Dtest=ArchitectureTest passes">
   - <related Linear issue, e.g. "ENG-123">
   ```
6. `git commit -m "<subject>" -m "<body>"` — let `pre-commit` hook run.
7. If `pre-commit` fails, fix the issue (do NOT pass `--no-verify`),
   re-stage, and retry.

## Forbidden

- `git commit --no-verify` (project's `git` wrapper rejects it).
- `git add .` / `git add -A` (pulls in unwanted artifacts).
- Mixing unrelated changes in one commit.
- Skipping `mvn` / `npm` validation steps.
