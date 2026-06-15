# Claude Adapter for Symphony

This directory contains a thin adapter that lets Symphony's Elixir orchestrator
drive **Claude Code** (Anthropic's CLI) instead of OpenAI Codex. Symphony's
elixir implementation speaks a Codex-specific JSON-RPC protocol on stdio;
this adapter bridges that protocol to `claude -p` (Claude Code's one-shot
non-interactive mode).

## Why

Symphony (openai/symphony) is hardcoded to `codex app-server`. We want a
single orchestration layer that can dispatch issues to either Codex **or**
Claude, depending on the task and team preference. Forcing a fork of
Symphony is heavy (~1000+ LOC of Elixir); a stdio adapter is ~250 LOC of
Python.

## Files

| File | Purpose |
|---|---|
| `claude-app-server.py` | The adapter. Symlink or copy to `~/symphony/bin-shim/claude-app-server.py` and reference from WORKFLOW.md's `codex.command`. |
| `WORKFLOW.md.example` | Minimal Symphony WORKFLOW.md that points the orchestrator at the adapter instead of `codex app-server`. |
| `test-e2e.py` | Standalone Python script that exercises the adapter end-to-end without Symphony in the loop. |

## Architecture

```
Symphony (Elixir)                    claude-app-server.py                Claude Code CLI
       │                                      │                                   │
       │  ── thread/start (cwd) ──────────▶   │                                   │
       │  ◀── {threadId} ─────────────────    │                                   │
       │  ── turn/start (prompt) ─────────▶   │  ── claude -p <prompt> --bare ──▶│
       │                                      │  ◀── stdout (full answer) ────────│
       │  ◀── turn/started notification ──    │                                   │
       │  ◀── item/started notification ──    │                                   │
       │  ◀── item/completed (text) ───────    │                                   │
       │  ◀── turn/completed {status} ────    │                                   │
```

State is **stateless between turns** — each `turn/start` is a fresh `claude -p`
invocation. Symphony already uses the workspace state (`git log`, files,
workpad comments) as the source of truth across turns; we don't need the LLM
to remember prior conversation turns.

## Policy mapping

Symphony passes Codex's `approvalPolicy` and `sandbox` params to the adapter.
We map them onto Claude Code's `--allowedTools` / `--allow-dangerously-skip-permissions`:

| Codex `sandbox` / `approvalPolicy` | Claude Code flag |
|---|---|
| `read-only` / `untrusted` | `--allowedTools "Read,Grep,Glob"` |
| `workspace-write` / `on-failure` / `on-request` | `--allowedTools "Read,Edit,Write,Bash(git *),Bash(npm *),Bash(mvn *)"` |
| `danger-full-access` / `never` | `--allow-dangerously-skip-permissions` |

To customize the allowed tool list, edit `claude_flags_for()` in
`claude-app-server.py`.

## Quick start

### 1. Install (already done in this repo's smoke test)

```bash
chmod +x claude/claude-app-server.py
cp claude/claude-app-server.py ~/symphony/bin-shim/claude-app-server.py
```

### 2. Sanity check (no Symphony)

```bash
python3 claude/test-e2e.py
```

Should print: `turn/result: {'id': 3, 'result': {'status': 'completed'}}`
and a `[claude output]` block with the model's reply.

### 3. Wire into a Symphony WORKFLOW.md

Replace this block in your project's `WORKFLOW.md`:

```yaml
codex:
  command: codex app-server
  approval_policy: on-failure
  thread_sandbox: workspace-write
  read_timeout_ms: 30000
```

With:

```yaml
codex:
  command: /Users/user/symphony/bin-shim/claude-app-server.py
  approval_policy: on-failure
  thread_sandbox: workspace-write
  read_timeout_ms: 30000
```

### 4. Test with a real issue

Restart Symphony (`launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.xiyu.bid.symphony.plist`)
and create a Linear issue with the `symphony-eligible` label. Watch
`~/symphony/log/prod/log/symphony.log.1` for `[claude-adapter]` log lines.

## Known limitations

- **No streaming output yet** — we emit a single `item/completed` with the
  full text. To stream, swap `claude -p` for `claude -p --output-format
  stream-json` and parse JSON lines into incremental notifications.
- **No conversation memory between turns** — intentional; Symphony's model
  relies on workspace state, not chat history.
- **No MCP server support** — Codex's app-server exposes a `linear_graphql`
  tool to repo skills; we don't proxy it. If Symphony needs that, extend
  `handle_turn_start` to forward a Linear client to `claude -p` via env
  vars or a sidecar.
- **Bash allowlist is coarse** — `Bash(git *)` matches `git status`, `git
  push`, `git reset --hard`. If you want finer control, add specific
  patterns or switch to `--allow-dangerously-skip-permissions` for
  trusted workspaces.

## License

Inherits the parent repo's Apache-2.0.
