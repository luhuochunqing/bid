---
name: xiyu-server-deploy
description: XiYu production server deployment workflow for the 西域数智化投标管理平台. Use when preparing, executing, verifying, documenting, or troubleshooting server deployments, production release pipelines, GitHub production environment variables/secrets, MySQL 8.0 prod profile rollout, API-only smoke checks, rollback planning, or post-deploy release reports for this project.
---

# XiYu Server Deploy

## Operating Contract

Treat the 西域数智化投标管理平台 deployment path as real API only:

- Use `VITE_API_MODE=api` and real backend integration for build, smoke, UAT, screenshots, and acceptance.
- Use MySQL 8.0 for production with `SPRING_PROFILES_ACTIVE=prod,mysql` and `DB_ENGINE=mysql`.
- Do not add or depend on mock/demo/local-memory deployment paths.
- Treat deployment as a closed loop: preflight, backup, artifact activation, health check, smoke verification, report, and rollback readiness.

For detailed variables, scripts, and checklists, read `references/release-runbook.md` when the user asks for exact deployment steps, server setup, GitHub Actions configuration, production verification, or rollback commands.

## Workflow

1. Establish the target environment.
   - Confirm repository/worktree path, branch, commit, target host, public web URL, API URL, backend service name, app root, frontend public dir, and database name.
   - Say clearly whether you are preparing a plan, editing release automation, executing a deployment, or diagnosing a failed deployment.

2. Inspect the current release surface.
   - Read project docs and scripts before acting: `docs/release/PRODUCTION_RELEASE_PIPELINE.md`, `docs/release/GO_LIVE_CHECKLIST.md`, `docs/release/ROLLBACK.md`, `.github/workflows/main-release.yml`, and `scripts/release/*`.
   - Check `git status --short` before edits. Do not overwrite unrelated user changes.

3. Verify release readiness.
   - Prefer the project release gates already present in the repo.
   - For code changes, keep the normal project bar: frontend build/tests as relevant, backend tests as relevant, architecture gates for backend changes, and E2E/API smoke for user-facing deployment behavior.
   - For any local manual package, clean stale backend build output before packaging and inspect the jar for duplicate Flyway versions. A dirty `backend/target` once shipped a deleted `V1001` migration into `app.jar` and caused production startup failure.
   - Before production execution, require backup and rollback anchors: release tag or commit, previous frontend artifact, previous backend artifact, database backup, environment snapshot, and rollback owner.

4. Execute deployment safely.
   - Prefer existing automation over ad hoc SSH commands.
   - Package with `scripts/release/package-release.sh`.
   - If packaging locally, run from a clean build state such as `rm -rf backend/target` or use the CI artifact. After packaging, verify `BOOT-INF/classes/db/migration-mysql` inside `backend/app.jar` contains no duplicate migration versions.
   - Deploy through GitHub production workflow or `scripts/release/remote-deploy.sh` with explicit environment variables.
   - Keep production secrets out of chat, logs, docs, and committed files.

5. Prove the deployment.
   - Check backend health first, then API read endpoints, then frontend page availability.
   - Run the project's production smoke script when available: `node scripts/release/run-prod-smoke.mjs`.
   - Capture exact commit, release id, URLs checked, smoke result, and any skipped checks with reasons.

6. Handle failure decisively.
   - If health or P0 smoke fails, stop forward rollout.
   - Prefer rollback to the previous known-good artifact when the issue blocks core login, API health, project/tender access, or cannot be fixed within the maintenance window.
   - Never run destructive database recovery commands unless the user explicitly confirms the target database and backup.

## Response Shape

When reporting results, include:

- Environment and release id/commit.
- Commands or workflows run.
- Verification result, including health and smoke.
- Rollback posture: ready, not needed, executed, or blocked.
- Files changed, if any.
