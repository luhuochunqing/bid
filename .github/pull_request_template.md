## Summary
- Describe the user-visible change.
- Link the ticket or context if there is one.

## UI/路由变更检查（必填）
- [ ] 未修改 `src/views/` 或 `src/router/`（本 PR 无 UI 改动）
- [ ] 修改了 UI/路由，且已同步更新 `e2e/` 测试
- [ ] 修改了 UI/路由，且在 commit message 中注明了 `[skip e2e-scope]` 及原因

## Scope Check
- [ ] I changed only the files required for this task.
- [ ] I reviewed `git diff` and did not include unrelated local changes.
- [ ] I ran `npm run agent:lock-check`, and any locked files touched by this PR are owned by this branch/task.
- [ ] If I needed a lock, I used `npm run agent:lock-acquire` and pushed the branch so other Agents can see it.
- [ ] I pushed the branch before asking for review.

## Architecture Self-Check
- [ ] New business rules introduced by this PR live in a `core/` or `domain/` package; if they remain in a `Service`, I stated the reason.
- [ ] Touched `Service` files moved toward the 300-line budget (or at least did not grow further).
- [ ] No new business exceptions; expected business failures are returned as `Result` / `Optional` values.

## Verification
- [ ] `npm run build`
- [ ] `VITE_API_MODE=api npm run build`
- [ ] If backend Java files changed, I ran `cd backend && mvn -Pjava-format spotless:apply` before committing (or my IDE auto-formats on save with google-java-format).
- [ ] If backend changed, the relevant Maven compile/test commands passed.
- [ ] If backend quality-gate config or protected modules changed, I ran the documented quality audit/strict commands and included the result in the PR description.

## UI Evidence
- [ ] Not applicable
- [ ] Screenshots attached
- [ ] Screen recording attached

## Risk Check
- [ ] No schema or contract changes
- [ ] Contains API or schema changes
- [ ] Contains mock-data-only changes
- [ ] Contains quality-gate scope or governance changes

## Rollback
- Describe the fastest rollback path if this PR needs to be reverted.

## Auto-merge
- After the required 1 review approves and all gates (agent-locks, line-budget, frontend, backend, e2e + strict) are green, a workflow will automatically enable GitHub auto-merge.
- You can also manually click "Enable auto-merge" (squash recommended) any time after review is requested.
- This removes the final manual merge click. The actual merge only happens when **all** protection rules pass (no gate is bypassed).
- After merge lands on main, run in your worktree: `source scripts/dev-env.sh && ./scripts/sync-env.sh .` (or `npm run agent:up`) to rebase and let the "next line" of work continue.
