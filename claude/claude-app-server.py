#!/usr/bin/env python3
"""
claude-app-server.py — Codex app-server protocol adapter for Claude Code CLI.

Symphony's Elixir code is hardcoded to talk to `codex app-server` via JSON-RPC.
This adapter speaks the minimal subset of that protocol on stdio, but backs
each turn with a fresh `claude -p` invocation instead of a Codex model call.

Protocol subset implemented:
  - initialize / initialized
  - thread/start
  - turn/start
  - (Symphony never sends exit; we shut down when stdin closes)

Notifications emitted (Symphony consumes these as "messages"):
  - turn/started     (just before claude spawn)
  - item/started     (just before streaming output begins)
  - item/completed   (with full output text, simple aggregation)
  - turn/completed   (final; status=completed | failed)

Mapping of Codex params to Claude Code CLI flags:
  - approval_policy "untrusted"     -> --allowedTools "Read,Grep,Glob"  (read-only)
  - approval_policy "on-failure"    -> --allowedTools "Read,Edit,Write,Bash(git *)"
  - approval_policy "on-request"    -> same as on-failure but interactive; here we use bare
  - approval_policy "never"         -> --allow-dangerously-skip-permissions
  - thread_sandbox "read-only"      -> --allowedTools "Read,Grep,Glob"
  - thread_sandbox "workspace-write"-> same as on-failure tools
  - thread_sandbox "danger-full-access" -> --allow-dangerously-skip-permissions

State model:
  - Each `thread/start` returns a fresh threadId and remembers the cwd.
  - Each `turn/start` is a one-shot `claude -p <prompt>` call in that cwd.
  - There is no conversation continuity between turns — Symphony already
    uses the workspace (git log, files) as the source of truth.

This is intentionally a thin glue layer. For richer streaming, swap
`--print` for `--output-format stream-json` and parse JSON lines into
incremental `item/*` notifications.
"""

import json
import os
import subprocess
import sys
import uuid


def send(obj):
    """Send a JSON-RPC message to Symphony on stdout."""
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


def log(msg):
    """Diagnostic log on stderr (Symphony captures as debug)."""
    sys.stderr.write(f"[claude-adapter] {msg}\n")
    sys.stderr.flush()


# In-memory state: thread_id -> {cwd, approval, sandbox}
threads = {}


def handle_initialize(params):
    client = (params.get("clientInfo") or {}).get("name", "unknown")
    log(f"initialize: client={client}")
    return {
        "userAgent": "claude-adapter/0.1.0 (Codex-protocol shim for Claude Code)",
        "codexHome": os.path.expanduser("~/.claude"),
        "platformFamily": "unix",
        "platformOs": "macos",
    }


def handle_thread_start(params):
    thread_id = f"thr-{uuid.uuid4()}"
    cwd = params.get("cwd") or os.getcwd()
    approval = params.get("approvalPolicy") or "on-failure"
    sandbox = params.get("sandbox") or "workspace-write"
    threads[thread_id] = {"cwd": cwd, "approval": approval, "sandbox": sandbox}
    log(f"thread/start: {thread_id} cwd={cwd} approval={approval} sandbox={sandbox}")
    return {"threadId": thread_id}


def claude_flags_for(approval, sandbox):
    """Map Codex policy fields to Claude Code CLI flags."""
    if sandbox == "read-only" or approval == "untrusted":
        return ["--allowedTools", "Read,Grep,Glob"]
    if sandbox == "danger-full-access" or approval == "never":
        return ["--allow-dangerously-skip-permissions"]
    # default: workspace-write + on-failure/on-request
    return ["--allowedTools", "Read,Edit,Write,Bash(git *),Bash(npm *),Bash(mvn *)"]


def extract_prompt(items):
    """Concatenate text items from a Codex `turn/start` input array."""
    parts = []
    for item in items or []:
        if item.get("type") == "text" and item.get("text"):
            parts.append(item["text"])
    return "\n\n".join(parts)


def handle_turn_start(params):
    thread_id = params.get("threadId")
    if thread_id not in threads:
        return {"status": "failed", "error": f"unknown thread: {thread_id}"}
    thread = threads[thread_id]
    prompt = extract_prompt(params.get("input", []))

    if not prompt.strip():
        return {"status": "failed", "error": "empty prompt"}

    flags = claude_flags_for(thread["approval"], thread["sandbox"])
    log(f"turn/start: thread={thread_id} prompt_chars={len(prompt)} flags={flags}")

    # Emit turn/started
    send({
        "method": "turn/started",
        "params": {"threadId": thread_id},
    })

    # Emit item/started (a single agent message)
    send({
        "method": "item/started",
        "params": {"item": {"type": "agentMessage", "text": ""}},
    })

    # Spawn claude -p and capture output
    try:
        proc = subprocess.run(
            ["claude", "-p", prompt, "--bare"] + flags,
            cwd=thread["cwd"],
            capture_output=True,
            text=True,
            timeout=600,
        )
        output = proc.stdout or ""
        stderr = proc.stderr or ""
        log(f"claude exit={proc.returncode} stdout_chars={len(output)} stderr_chars={len(stderr)}")

        # Emit item/completed with the full agent output
        send({
            "method": "item/completed",
            "params": {"item": {"type": "agentMessage", "text": output}},
        })

        # Emit turn/completed
        if proc.returncode == 0:
            send({
                "method": "turn/completed",
                "params": {"threadId": thread_id, "status": "completed", "error": None},
            })
            return {"status": "completed"}
        else:
            err_msg = (stderr.strip().splitlines() or ["claude exit non-zero"])[-1][:300]
            send({
                "method": "turn/completed",
                "params": {
                    "threadId": thread_id,
                    "status": "failed",
                    "error": {"message": f"claude exit {proc.returncode}: {err_msg}"},
                },
            })
            return {"status": "failed", "error": err_msg}

    except subprocess.TimeoutExpired:
        log("claude timed out after 600s")
        send({
            "method": "item/completed",
            "params": {"item": {"type": "agentMessage", "text": "[claude-adapter] timed out after 600s"}},
        })
        send({
            "method": "turn/completed",
            "params": {
                "threadId": thread_id,
                "status": "failed",
                "error": {"message": "claude -p timed out after 600s"},
            },
        })
        return {"status": "failed", "error": "timeout"}

    except FileNotFoundError:
        msg = "claude CLI not found in PATH (expected /opt/homebrew/bin/claude)"
        log(msg)
        send({
            "method": "turn/completed",
            "params": {
                "threadId": thread_id,
                "status": "failed",
                "error": {"message": msg},
            },
        })
        return {"status": "failed", "error": msg}

    except Exception as e:
        log(f"turn error: {type(e).__name__}: {e}")
        send({
            "method": "turn/completed",
            "params": {
                "threadId": thread_id,
                "status": "failed",
                "error": {"message": f"{type(e).__name__}: {e}"},
            },
        })
        return {"status": "failed", "error": str(e)}


def main():
    log("claude-adapter starting; awaiting JSON-RPC on stdin")
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except json.JSONDecodeError as e:
            log(f"invalid JSON: {e}")
            continue

        method = msg.get("method")
        msg_id = msg.get("id")
        params = msg.get("params") or {}

        try:
            if method == "initialize":
                send({"id": msg_id, "result": handle_initialize(params)})
            elif method == "initialized":
                pass  # notification; no response
            elif method == "thread/start":
                send({"id": msg_id, "result": handle_thread_start(params)})
            elif method == "turn/start":
                send({"id": msg_id, "result": handle_turn_start(params)})
            else:
                log(f"unknown method: {method}")
                if msg_id is not None:
                    send({
                        "id": msg_id,
                        "error": {"code": -32601, "message": f"Method not found: {method}"},
                    })
        except Exception as e:
            log(f"handler error for {method}: {type(e).__name__}: {e}")
            if msg_id is not None:
                send({
                    "id": msg_id,
                    "error": {"code": -32603, "message": f"Internal error: {e}"},
                })

    log("stdin closed; shutting down")


if __name__ == "__main__":
    main()
