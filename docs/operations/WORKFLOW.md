---
# Symphony workflow contract for xiyu-bid-poc (西域数智化投标管理平台)
# Adapted from openai/symphony@main/elixir/WORKFLOW.md for the Gitee + Integrator
# review pipeline. Do not change field names — Symphony's lib parses them.
#
# 角色定位:本 workflow 只让 Symphony 接手打了 `symphony-eligible` label 的
# 低风险任务(文档/注释/单测/翻译/小重构)。所有 hot-paths 黑名单任务必须由
# 人类 + 现有 Claude/Codex/Cursor agent 手动完成。
tracker:
  kind: linear
  project_slug: "346a62d23636"
  required_labels:
    - symphony-eligible
  active_states:
    - Todo
    - In Progress
    - Merging
    - Rework
  terminal_states:
    - Closed
    - Cancelled
    - Canceled
    - Duplicate
    - Done

polling:
  interval_ms: 5000

workspace:
  root: ~/code/workspaces
  hooks:
    after_create: |
      set -euo pipefail
      if [ -d ".git" ]; then
        echo "workspace already initialized, fetching updates"
        git -C . fetch origin main
        git -C . rebase origin/main || true
      else
        echo "cloning bid repo into workspace"
        git clone --depth 1 https://gitee.com/allinai888/bid.git . || {
          echo "clone failed, trying without depth"
          git clone https://gitee.com/allinai888/bid.git .
        }
      fi
      git config user.email "symphony-bot@xiyu.local"
      git config user.name "Symphony Bot"
      python3 -c '
      lines = [
        "SPRING_PROFILES_ACTIVE=dev,mysql",
        "SERVER_PORT=18082",
        "DB_NAME=xiyu_bid_codex",
        "VITE_API_BASE_URL=http://127.0.0.1:18082",
        "FRONTEND_PORT=1316",
        "LINEAR_API_KEY_OVERRIDE=use-host",
      ]
      open(".env.runtime", "w").write("\n".join(lines) + "\n")
      '
      # Branch setup: ensure workspace is on a dedicated agent/symphony/<ID> branch
      SAFE_ID=$(basename "$(pwd)")
      BRANCH="agent/symphony/${SAFE_ID}"
      if git rev-parse --verify "$BRANCH" >/dev/null 2>&1; then
        echo "Branch $BRANCH exists locally, switching and rebasing"
        git checkout "$BRANCH" || git checkout -b "$BRANCH" origin/main
        git -C . rebase origin/main || true
      elif git ls-remote --heads origin "$BRANCH" 2>/dev/null | grep -q "$BRANCH"; then
        echo "Branch $BRANCH exists on origin, checking out"
        git checkout -b "$BRANCH" "origin/$BRANCH"
        git -C . rebase origin/main || true
      else
        echo "Creating new branch $BRANCH from origin/main"
        git checkout -b "$BRANCH" origin/main
      fi
    before_remove: |
      # 留空,workspace 删除前不需要 hook(workspace 是 Symphony 自己的目录)

agent:
  max_concurrent_agents: 2
  max_turns: 20
  # 任务分支必须以 agent/symphony/ 开头,跟 who-touches.sh 兼容
  branch_prefix: "agent/symphony/"

codex:
  command: /Users/user/symphony/bin-shim/claude-app-server.py
  approval_policy: '{"reject":{"sandbox_approval":true,"rules":true,"mcp_elicitations":true}}'
  thread_sandbox: workspace-write
  turn_sandbox_policy:
    type: workspaceWrite
    networkAccess: true
---

# 西域数智化投标管理平台 Symphony Workflow

You are working on a Linear ticket `{{ issue.identifier }}` for the
"西域数智化投标管理平台" (xiyu-bid-poc) project.

> **本 workflow 是被 OpenAI `symphony` elixir 实现的参考实现驱动的**,但所有
> GitHub-specific 的钩子(`gh pr merge`、Codex Review 评论识别)都已改写为
> 本仓 Gitee + Integrator review 流程,**不要执行 `gh ...` 命令**。

{% if attempt %}
## Continuation context
- This is retry attempt #{{ attempt }}.
- Resume from current workspace state; do not restart from scratch.
- Do not repeat completed investigation/validation unless code changes need it.
- Do not end the turn while the issue is still in an active state unless blocked.
{% endif %}

## Issue context
- Identifier: {{ issue.identifier }}
- Title: {{ issue.title }}
- Current status: {{ issue.state }}
- Labels: {{ issue.labels }}
- URL: {{ issue.url }}

## Description
{% if issue.description %}
{{ issue.description }}
{% else %}
No description provided.
{% endif %}

---

## Project rules (MUST follow)

### 1. Hot-paths blacklist — DO NOT MODIFY

These paths require coordination with the existing Claude/Codex/Cursor agent
worktrees via `.agent-locks/<task-slug>.yml`. Symphony agents are **forbidden**
from modifying them in any PR.

```
backend/src/main/resources/db/migration-mysql/**
backend/src/main/resources/db/rollback/migration-mysql/**
backend/src/main/java/com/xiyu/bid/entity/**
backend/src/main/resources/application*.yml
src/router/index.js
src/views/Login.vue
.github/workflows/**
.githooks/**
```

**If the issue requires changing any path in this list, STOP.** Move the
Linear issue to `Human Review` and write a `## Codex Workpad` comment explaining
that the task needs a human/agent to handle hot-path coordination. Do not
silently work around the blacklist.

### 2. Branch naming — MANDATORY

Every PR branch MUST be prefixed with `agent/symphony/`. The base must be
  `origin/main` rebased to current. Do not push to any other branch.

  **Use the workspace's pre-created branch**: the `after_create` hook has already
  created `agent/symphony/{{ issue.identifier }}` (exact match, no slug). Do
  NOT add a kebab-case slug suffix. The current branch (`git branch --show-current`)
  should already be `agent/symphony/{{ issue.identifier }}` — if not, check it out
  before making any changes. Any commit you make on the wrong branch (e.g. `main`)
  will be lost on push (main is protected) and will block the auto-PR/Linear loop.

  ```bash
  # Correct (use the existing branch created by after_create):
  agent/symphony/CO-219

  # Wrong (will be rejected and break the post_agent_cleanup branch lookup):
  agent/symphony/CO-219-source-type-chinese
  agent/symphony/co-219
  CO-219
  ```

### 3. PR target — Gitee ONLY

This project is mirrored on Gitee (`https://gitee.com/allinai888/bid`). All
PRs MUST be created via `scripts/gitee-pr-helper.sh`, never `gh`.

```bash
# Create PR
GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh create

# Check status
GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh check <num>

# View approvers
GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh approve <num>
```

### 4. Review pipeline — Integrator agent

`CODEOWNERS` lists `@user` for code paths, and the
`auto-enable-merge-on-approved.yml` workflow turns on auto-merge once 1
human/integrator review approves. **Symphony agents must NEVER approve their
own PRs** (token permission `GITEE_TOKEN` does not include approval scope; if
it does, abort the run and alert via the workpad comment).

Review feedback arrives as Gitee PR comments. Detect feedback by:
```bash
GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh check <num>
# → review comments come back as JSON; address each blocking comment inline
```

Do not move to `Human Review` until:
- All CI checks are green (`backend-architecture-tests`, `governance-agent-locks`,
  `frontend-build`, etc.)
- All review feedback has been addressed or explicitly pushed back with rationale
- The PR description references the Linear issue ID via `ENG-XXX` notation

### 5. File-lock coordination

Before touching any path that might overlap with another agent's work:

```bash
# Check if anyone else has a lock on this path
npm run agent:lock-check:changed
```

If a hot-path lock is needed but the path is in the blacklist (rule 1),
abort the run instead.

### 6. Local validation gates

Run these locally before pushing:

```bash
# Frontend
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run build

# Backend
cd backend && mvn -Pjava-quality checkstyle:check
cd backend && mvn -Dtest=ArchitectureTest test

# E2E (only if UI changed)
npm run test:e2e
```

If any check fails, fix it locally before pushing. Do not push and rely on CI.

### 7. Commit / push protocol

Use `.codex/skills/commit/SKILL.md` and `.codex/skills/push/SKILL.md`. Both
respect the project's `pre-commit` and `pre-push` hooks (15+ / 14 gates
respectively). Never use `--no-verify` — the wrapper scripts reject it.

---

## Status map

| Linear state | Action |
|---|---|
| `Backlog` | Out of scope. Do not modify; wait for human to move to `Todo`. |
| `Todo` | Move to `In Progress`, create workpad, start work. |
| `In Progress` | Continue execution; keep workpad current. |
| `Human Review` | PR attached, awaiting review. Poll for feedback. |
| `Merging` | Approved. Run `.codex/skills/land/SKILL.md` loop. |
| `Rework` | Reviewer requested changes. Replan, do not incrementally patch. |
| `Done` | Terminal. No action. |

## Step 0: Determine current ticket state and route

1. Fetch the issue by ID via `linear_graphql` tool.
2. Read the current state.
3. Route to the matching flow above.
4. **Verify the issue has the `symphony-eligible` label.** If not, move it to
   `Human Review` with a brief comment "Symphony is not authorized to handle
   this issue (missing required label)". Stop.
5. If a PR is already attached and the branch PR is `CLOSED` or `MERGED`,
   treat prior branch work as non-reusable. Create a fresh branch from
   `origin/main` and restart.

## Step 1: Start/continue execution (Todo or In Progress)

1. Find or create a single persistent `## Symphony Workpad` comment on the
   issue (similar to Symphony's `## Codex Workpad` template).
2. Run the `pull` skill to sync with `origin/main` and record the result in
   the workpad `### Notes` section.
3. Write a hierarchical plan in the workpad `### Plan` section with checkboxes.
4. Add explicit acceptance criteria in `### Acceptance Criteria`.
5. Capture reproduction signal (if bug fix) in `### Notes`.
6. Compact context, then begin implementation.

## Step 2: Execution phase

1. Implement against the plan. Update the workpad after every milestone.
2. Run local validation gates (rule 6) before every push.
3. If a temporary proof edit is needed (e.g. hardcoded test data), revert it
   before commit and document it in the workpad.
4. Commit using `.codex/skills/commit/SKILL.md`.
5. Push using `.codex/skills/push/SKILL.md`.
6. Create PR via `GITEE_TOKEN=xxx ./scripts/gitee-pr-helper.sh create`.
7. Address any review feedback that comes in.
8. Repeat the push → review loop until all checks are green and review
   feedback is resolved.
9. Move the issue to `Human Review`.

## Step 3: Human Review and merge handling

1. When the issue is in `Human Review`, do not code or change ticket content.
2. Poll `gitee-pr-helper.sh check <num>` for review feedback.
3. If feedback requires changes, move to `Rework`.
4. If approved and merged, move to `Done`.
5. The merge itself is handled by `auto-enable-merge-on-approved.yml`; do not
   call `gitee-pr-helper.sh merge` directly.

## Step 4: Rework handling

1. Treat `Rework` as a full approach reset, not incremental patching.
2. Re-read the full issue body and all comments.
3. Identify what will be done differently.
4. Close the existing PR tied to the issue.
5. Remove the existing `## Symphony Workpad` comment.
6. Create a fresh branch from `origin/main` and restart from Step 1.

## Workpad template

Use this exact structure for the persistent workpad comment:

````md
## Symphony Workpad

```text
<hostname>:<abs-path>@<short-sha>
```

### Plan

- [ ] 1. Parent task
  - [ ] 1.1 Child task
  - [ ] 1.2 Child task
- [ ] 2. Parent task

### Acceptance Criteria

- [ ] Criterion 1
- [ ] Criterion 2

### Validation

- [ ] `npm run check:front-data-boundaries` (if UI)
- [ ] `cd backend && mvn -Pjava-quality checkstyle:check` (if backend)
- [ ] `cd backend && mvn -Dtest=ArchitectureTest test` (if backend)
- [ ] `npm run test:e2e` (if UI behavior)

### Notes

- <progress note with timestamp>

### Confusions

- <only include when something was confusing>
````

## Guardrails

- Never touch hot-paths (rule 1).
- Never push to a branch not prefixed with `agent/symphony/`.
- Never call `gh ...` — use `scripts/gitee-pr-helper.sh` only.
- Never approve your own PR.
- Never use `--no-verify` on `git commit` or `git push`.
- If blocked by missing tool/auth/secret, write a brief blocker in the
  workpad and move the issue to `Human Review`.
- Keep issue text concise and reviewer-oriented.
