#!/usr/bin/env python3
"""
claude-app-server.py — Codex app-server protocol adapter for Claude Code CLI,
with optional Claude-executes / Codex-reviews routing.

Symphony's Elixir code is hardcoded to talk to `codex app-server` via JSON-RPC.
This adapter speaks the minimal subset of that protocol on stdio. For each
turn, it can either:

  (A) Default mode: spawn `claude -p <prompt>` (one-shot non-interactive Claude)
  (B) Routing mode (set REVIEW_ENABLED=1):
      - turn 1:  Claude writes the code
      - turn 2:  Codex reviews the changes
      - turn 3+: Claude reworks (if Codex requested changes), or finalizes

Routing mode is opt-in via the REVIEW_ENABLED environment variable so the
adapter stays backward-compatible with projects that want pure Claude.

Protocol subset implemented (toward Symphony):
  - initialize / initialized
  - thread/start     (response shape: {"thread": {"id": "..."}})
  - turn/start       (returns synthetic {turn: {id, status}} immediately,
                      then background worker emits lifecycle notifications)

Notifications emitted (Symphony consumes these as "messages"):
  - turn/started
  - item/started
  - item/completed   (with full output text)
  - turn/completed

State model:
  - Each `thread/start` returns a fresh threadId and remembers cwd/policy.
  - `thread_state[thread_id]` tracks turn count, original prompt, last diff,
    and Codex verdict across turns.
  - In routing mode, turn 1 spawns `claude -p`, then we `git diff` to capture
    the work; turn 2 spawns `codex app-server` with a review prompt that
    includes the diff; subsequent turns either re-prompt Claude with Codex
    feedback or finalize.
"""

import json
import os
import re
import subprocess
import sys
import threading
import time
import uuid


def send(obj):
    """Send a JSON-RPC message to Symphony on stdout."""
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


def log(msg):
    """Diagnostic log on stderr (Symphony captures as debug)."""
    sys.stderr.write(f"[claude-adapter] {msg}\n")
    sys.stderr.flush()


# Per-thread state
threads = {}            # thread_id -> {cwd, approval, sandbox}
thread_state = {}       # thread_id -> {turn_count, original_prompt, last_diff, last_verdict}

# Routing toggle: REVIEW_ENABLED=1 enables Claude-exec / Codex-review routing.
# When unset, every turn is just `claude -p` (backward-compatible).
REVIEW_ENABLED = os.environ.get("REVIEW_ENABLED", "0") == "1"
CODEX_BIN = os.environ.get("CODEX_BIN", "codex")


def handle_initialize(params):
    client = (params.get("clientInfo") or {}).get("name", "unknown")
    log(f"initialize: client={client} review_enabled={REVIEW_ENABLED}")
    return {
        "userAgent": f"claude-adapter/0.2.0 (Codex-protocol shim for Claude{'+Codex review' if REVIEW_ENABLED else ''})",
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
    thread_state[thread_id] = {
        "turn_count": 0,
        "original_prompt": None,
        "last_diff": None,
        "last_verdict": None,
    }
    log(f"thread/start: {thread_id} cwd={cwd} approval={approval} sandbox={sandbox}")
    # Symphony reads result.thread.id (see elixir/lib/symphony_elixir/codex/app_server.ex)
    return {"thread": {"id": thread_id}}


def claude_flags_for(approval, sandbox):
    """Map Codex policy fields to Claude Code CLI flags."""
    if sandbox == "read-only" or approval == "untrusted":
        return ["--allowedTools", "Read,Grep,Glob"]
    if sandbox == "danger-full-access" or approval == "never":
        return ["--allow-dangerously-skip-permissions"]
    return ["--allowedTools", "Read,Edit,Write,Bash(git *),Bash(npm *),Bash(mvn *)"]


def extract_prompt(items):
    parts = []
    for item in items or []:
        if item.get("type") == "text" and item.get("text"):
            parts.append(item["text"])
    return "\n\n".join(parts)


# ----------------------------------------------------------------------
# Backends
# ----------------------------------------------------------------------

def run_claude_p(cwd, prompt, approval, sandbox):
    """Spawn `claude -p <prompt>` and return (stdout, exit_code)."""
    flags = claude_flags_for(approval, sandbox)
    log(f"claude: prompt_chars={len(prompt)} flags={flags}")
    try:
        proc = subprocess.run(
            ["claude", "-p", prompt, "--bare"] + flags,
            cwd=cwd,
            capture_output=True,
            text=True,
            timeout=600,
        )
        return proc.stdout or "", proc.returncode, proc.stderr or ""
    except FileNotFoundError:
        return "", 127, "claude CLI not found in PATH"
    except subprocess.TimeoutExpired:
        return "", 124, "claude -p timed out after 600s"


def run_codex_app_server(cwd, prompt, approval, sandbox):
    """Spawn codex app-server, run a review turn, return (output_text, exit_status).

    Talks codex's JSON-RPC protocol on stdio:
      initialize -> initialized -> thread/start -> turn/start -> [notifications...] -> turn/completed
    """
    cmd = [CODEX_BIN, "app-server"]
    log(f"codex: spawning {cmd} cwd={cwd}")
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1,
        )
    except FileNotFoundError:
        return "", "codex CLI not found in PATH"

    output_text = ""
    turn_status = "unknown"
    error_msg = None

    def codex_send(msg):
        proc.stdin.write(json.dumps(msg) + "\n")
        proc.stdin.flush()

    def codex_readline():
        return proc.stdout.readline()

    try:
        # 1. initialize
        codex_send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
                    "params": {"clientInfo": {"name": "claude-adapter-reviewer", "version": "0.2.0"}}})
        deadline = time.time() + 30
        init_resp = None
        while time.time() < deadline:
            line = codex_readline()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("id") == 1:
                init_resp = obj
                break
        if not init_resp or "error" in init_resp:
            return "", f"codex initialize failed: {init_resp}"

        # 2. initialized (notification, no response)
        codex_send({"jsonrpc": "2.0", "method": "initialized", "params": {}})

        # 3. thread/start
        codex_send({"jsonrpc": "2.0", "id": 2, "method": "thread/start",
                    "params": {"cwd": cwd, "approvalPolicy": approval, "sandbox": sandbox}})
        deadline = time.time() + 30
        thread_resp = None
        while time.time() < deadline:
            line = codex_readline()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("id") == 2:
                thread_resp = obj
                break
        if not thread_resp or "error" in thread_resp:
            return "", f"codex thread/start failed: {thread_resp}"
        codex_thread_id = thread_resp["result"]["thread"]["id"]

        # 4. turn/start
        codex_send({"jsonrpc": "2.0", "id": 3, "method": "turn/start",
                    "params": {"threadId": codex_thread_id,
                               "input": [{"type": "text", "text": prompt}]}})
        deadline = time.time() + 30
        turn_resp = None
        while time.time() < deadline:
            line = codex_readline()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("id") == 3:
                turn_resp = obj
                break
        if not turn_resp or "error" in turn_resp:
            return "", f"codex turn/start failed: {turn_resp}"

        # 5. Stream notifications until turn/completed
        deadline = time.time() + 600
        while time.time() < deadline:
            line = codex_readline()
            if not line:
                time.sleep(0.05)
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            method = obj.get("method")
            if method == "item/completed":
                item = obj.get("params", {}).get("item", {})
                if item.get("type") == "agentMessage":
                    output_text += item.get("text", "")
            elif method == "turn/completed":
                params = obj.get("params", {})
                turn_status = params.get("status", "unknown")
                err = params.get("error")
                if err:
                    error_msg = err.get("message", str(err))
                break

        if error_msg:
            return output_text, f"codex turn failed: {error_msg}"
        return output_text, turn_status

    finally:
        try:
            proc.terminate()
            proc.wait(timeout=5)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass


# ----------------------------------------------------------------------
# Review-mode helpers
# ----------------------------------------------------------------------

def get_git_diff(cwd):
    """Capture the diff of the most recent commit on the current branch."""
    try:
        out = subprocess.run(
            ["git", "diff", "HEAD~1", "HEAD"],
            cwd=cwd, capture_output=True, text=True, timeout=10,
        )
        if out.returncode == 0 and out.stdout.strip():
            return out.stdout
        # If HEAD~1 doesn't exist (first commit), diff against empty tree
        out2 = subprocess.run(
            ["git", "show", "HEAD"],
            cwd=cwd, capture_output=True, text=True, timeout=10,
        )
        return out2.stdout if out2.returncode == 0 else "(no diff available)"
    except Exception as e:
        return f"(failed to capture diff: {e})"


def build_review_prompt(original_task, diff):
    """Construct a review prompt for Codex."""
    diff_truncated = diff[:8000] if diff else "(no changes)"
    return f"""You are reviewing code changes made by another AI agent (Claude).

ORIGINAL TASK:
{original_task[:2000]}

CHANGES (git diff of last commit):
```diff
{diff_truncated}
```

YOUR JOB:
1. Check if the changes correctly and completely accomplish the original task.
2. Be terse. End your reply with EXACTLY one of:
   - `VERDICT: APPROVED` if the changes are correct
   - `VERDICT: CHANGES_NEEDED` followed by a bulleted list of specific issues

Begin your review now."""


def build_rework_prompt(verdict_feedback, original_task):
    """Construct a rework prompt for Claude after Codex requests changes."""
    return f"""Codex reviewed your changes and requested changes. Apply them now.

ORIGINAL TASK:
{original_task[:2000]}

CODEX FEEDBACK:
{verdict_feedback}

Make the fixes, commit on the same branch, and push. Then report what you did."""


def parse_codex_verdict(output):
    """Return 'approved' or 'changes_needed' from Codex's review output."""
    last = output.strip().splitlines()[-3:] if output.strip() else []
    blob = " ".join(last).upper()
    if "VERDICT: APPROVED" in blob or "VERDICT:APPROVED" in blob:
        return "approved"
    if "VERDICT: CHANGES_NEEDED" in blob or "VERDICT:CHANGES_NEEDED" in blob:
        return "changes_needed"
    return "unclear"


# ----------------------------------------------------------------------
# turn/start dispatch
# ----------------------------------------------------------------------

def handle_turn_start(params):
    thread_id = params.get("threadId")
    if not isinstance(thread_id, str):
        return {"status": "failed", "error": f"threadId is not a string: {type(thread_id).__name__}"}
    if thread_id not in threads:
        return {"status": "failed", "error": f"unknown thread: {thread_id}"}
    thread = threads[thread_id]
    prompt = extract_prompt(params.get("input", []))
    if not prompt.strip():
        return {"status": "failed", "error": "empty prompt"}

    state = thread_state[thread_id]
    state["turn_count"] += 1
    turn_n = state["turn_count"]

    if turn_n == 1:
        state["original_prompt"] = prompt
    flags = claude_flags_for(thread["approval"], thread["sandbox"])

    # Decide backend
    if not REVIEW_ENABLED:
        backend = "claude"
    else:
        if turn_n == 1:
            backend = "claude"
        elif turn_n == 2:
            backend = "codex-review"
        elif turn_n >= 3:
            if state.get("last_verdict") == "approved":
                backend = "claude-finalize"
            else:
                backend = "claude-rework"
        else:
            backend = "claude"

    log(f"turn {turn_n} -> backend={backend} (review_enabled={REVIEW_ENABLED})")

    # IMPORTANT: Symphony's await_response blocks for read_timeout_ms. The
    # turn/start RESPONSE must come back immediately. We return a synthetic
    # turn id and let the background worker emit lifecycle notifications.
    turn_id = f"turn-{uuid.uuid4()}"

    def run_in_background():
        send({"method": "turn/started", "params": {"threadId": thread_id}})
        send({"method": "item/started", "params": {"item": {"type": "agentMessage", "text": ""}}})

        output, status, err = "", "completed", ""

        try:
            if backend == "claude":
                output, status, err = run_claude_p(thread["cwd"], prompt,
                                                    thread["approval"], thread["sandbox"])
                if status == 0:
                    state["last_diff"] = get_git_diff(thread["cwd"])

            elif backend == "codex-review":
                review_prompt = build_review_prompt(state["original_prompt"], state.get("last_diff"))
                output, status = run_codex_app_server(thread["cwd"], review_prompt,
                                                      thread["approval"], thread["sandbox"])
                state["last_verdict"] = parse_codex_verdict(output)
                log(f"codex verdict: {state['last_verdict']}")

            elif backend == "claude-rework":
                rework = build_rework_prompt(output or state.get("last_verdict", ""),
                                              state["original_prompt"])
                output, status, err = run_claude_p(thread["cwd"], rework,
                                                    thread["approval"], thread["sandbox"])
                if status == 0:
                    state["last_diff"] = get_git_diff(thread["cwd"])

            elif backend == "claude-finalize":
                finalize = "Codex approved your work. Write a one-paragraph summary of what you did and what files changed."
                output, status, err = run_claude_p(thread["cwd"], finalize,
                                                    thread["approval"], thread["sandbox"])

        except Exception as e:
            log(f"backend {backend} crashed: {type(e).__name__}: {e}")
            status = "failed"
            err = f"{type(e).__name__}: {e}"

        # Emit item/completed (full output as a single agent message)
        send({"method": "item/completed",
              "params": {"item": {"type": "agentMessage",
                                  "text": f"[backend={backend} exit={status}]\n\n{output}" if output else f"[backend={backend} exit={status} err={err}]"}}})

        # Emit turn/completed
        if status in (0, "completed", "approved"):
            send({"method": "turn/completed",
                  "params": {"threadId": thread_id, "status": "completed", "error": None}})
        else:
            send({"method": "turn/completed",
                  "params": {"threadId": thread_id, "status": "failed",
                             "error": {"message": f"backend={backend} {status}: {err[:200]}"}}})

    threading.Thread(target=run_in_background, daemon=True).start()
    return {"turn": {"id": turn_id, "status": "inProgress"}}


# ----------------------------------------------------------------------
# Main loop
# ----------------------------------------------------------------------

def main():
    log(f"claude-adapter starting; review_enabled={REVIEW_ENABLED} covex_bin={CODEX_BIN}")
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
                pass
            elif method == "thread/start":
                send({"id": msg_id, "result": handle_thread_start(params)})
            elif method == "turn/start":
                send({"id": msg_id, "result": handle_turn_start(params)})
            else:
                log(f"unknown method: {method}")
                if msg_id is not None:
                    send({"id": msg_id,
                          "error": {"code": -32601, "message": f"Method not found: {method}"}})
        except Exception as e:
            log(f"handler error for {method}: {type(e).__name__}: {e}")
            if msg_id is not None:
                send({"id": msg_id,
                      "error": {"code": -32603, "message": f"Internal error: {e}"}})

    log("stdin closed; shutting down")


if __name__ == "__main__":
    main()
