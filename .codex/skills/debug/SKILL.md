---
name: debug
description: |
  Debug a Symphony agent run by inspecting workspace logs, codex
  app-server output, and Symphony orchestrator state. Use when an
  issue is stuck in a non-progressing state.
---

# Debug

## When to use

- A Linear issue is claimed but the Symphony workspace has been idle
  for > 15 minutes.
- The `bin/symphony` process appears to have stopped.
- Code execution is failing repeatedly with the same error.

## Steps

### 1. Check the orchestrator process

```bash
ps aux | grep -E "symphony|beam" | grep -v grep
```

If no process, restart Symphony (see smoke-test in `~/symphony/INSTALL.md`).

### 2. Tail Symphony logs

```bash
tail -f ~/symphony/log/<workflow-name>/<date>/symphony.log
```

Or for the active workflow:
```bash
ls -lt ~/symphony/log/ | head -5
```

Look for:
- `WARN` lines about workspace creation
- `ERROR` lines about codex app-server connection
- `INFO` lines marking issue claim / dispatch

### 3. Inspect a specific workspace

```bash
ls -lt ~/code/workspaces/
# pick the workspace whose name matches the Linear issue ID
cd ~/code/workspaces/<issue-id>
git log --oneline -5
git status -s
```

### 4. Check codex app-server output

The codex CLI's app-server mode writes to stderr. Find the most recent
log:
```bash
ls -lt ~/code/workspaces/<issue-id>/.codex/log/ 2>/dev/null || true
```

(If `.codex/log/` is empty, the workspace's `hooks.after_create` did
not run successfully — check `~/symphony/log/...` for hook errors.)

### 5. Common failure modes

| Symptom | Cause | Fix |
|---|---|---|
| Workspace dir not created | `hooks.after_create` failed | Check `chmod +x` and the inline shell |
| codex app-server connection refused | `codex` not in PATH inside workspace | Add `codex` path to `hooks.after_create` `PATH=` prefix |
| Issue stuck in `In Progress` | Codex turn budget exhausted | Increase `agent.max_turns` in WORKFLOW.md |
| `symphony-eligible` label not seen | Linear label has different casing | Check exact label name in Linear settings |

## Escalation

If you cannot resolve the issue within 5 minutes of debugging:

1. Move the Linear issue to `Human Review` via `linear_graphql`.
2. Add a `## Symphony Workpad` comment with the symptom + what you tried.
3. Do not delete the workspace — humans may want to inspect it.
