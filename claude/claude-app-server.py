#!/usr/bin/env python3
"""
claude-app-server.py — Codex app-server protocol adapter for Claude Code CLI,
with optional Claude-executes / Codex-reviews routing.

Stability fixes (v0.3.0):
  - Non-blocking subprocess with real timeout enforcement
  - Turn state persistence: crash-safe writes + orphaned turn recovery
  - Turn 1 auto-rebase to latest origin/main
  - Atomic state file for PR creation
  - read_timeout_ms passed from WORKFLOW.md
  - usage reporting in turn/completed notifications

Protocol subset implemented (toward Symphony):
  - initialize / initialized
  - thread/start
  - turn/start (returns synthetic {turn: {id, status}} immediately,
                 then background worker emits lifecycle notifications)

Notifications emitted:
  - turn/started / item/started / item/completed / turn/completed
"""

import json
import os
import re
import select
import subprocess
import sys
import threading
import time
import uuid


def send(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


def log(msg):
    sys.stderr.write(f"[claude-adapter] {msg}\n")
    sys.stderr.flush()


# ------------------------------------------------------------------
# Environment & configuration
# ------------------------------------------------------------------

REVIEW_ENABLED = os.environ.get("REVIEW_ENABLED", "0") == "1"
CODEX_BIN = os.environ.get("CODEX_BIN", "codex")
ADAPTER_STATE_DIR = os.environ.get("ADAPTER_STATE_DIR",
                                    "/tmp/symphony-adapter-state")

# Per-thread in-memory state
threads = {}
thread_state = {}

os.makedirs(ADAPTER_STATE_DIR, exist_ok=True)


# ------------------------------------------------------------------
# State persistence — crash-safe turn tracking
# ------------------------------------------------------------------

def _state_path(thread_id):
    return os.path.join(ADAPTER_STATE_DIR, f"{thread_id}.state.json")


def save_turn_start(thread_id, turn_n, backend, cwd):
    state = {
        "thread_id": thread_id,
        "turn_n": turn_n,
        "backend": backend,
        "cwd": cwd,
        "started_at": time.time(),
        "status": "in_progress",
    }
    tmp = _state_path(thread_id) + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f)
    os.rename(tmp, _state_path(thread_id))


def save_turn_done(thread_id, status, exit_code, err_msg, usage):
    try:
        state = {"thread_id": thread_id, "status": status,
                 "exit_code": exit_code, "err": err_msg,
                 "usage": usage, "done_at": time.time()}
        tmp = _state_path(thread_id) + ".tmp"
        with open(tmp, "w") as f:
            json.dump(state, f)
        os.rename(tmp, _state_path(thread_id))
    except Exception as e:
        log(f"save_turn_done failed: {e}")


def clear_turn_state(thread_id):
    try:
        p = _state_path(thread_id)
        if os.path.exists(p):
            os.unlink(p)
    except Exception as e:
        log(f"clear_turn_state failed: {e}")


def _recover_orphaned_turns():
    for fname in os.listdir(ADAPTER_STATE_DIR):
        if not fname.endswith(".state.json"):
            continue
        path = os.path.join(ADAPTER_STATE_DIR, fname)
        try:
            with open(path) as f:
                state = json.load(f)
        except Exception:
            continue
        if state.get("status") == "in_progress":
            thread_id = state["thread_id"]
            started = state.get("started_at", 0)
            age = time.time() - started
            if age > 60:
                log(f"orphan turn detected: {thread_id} age={age:.0f}s, sending failure")
                send({
                    "method": "turn/completed",
                    "params": {
                        "threadId": thread_id,
                        "status": "failed",
                        "error": {"message": f"orphaned turn recovered after crash (age={age:.0f}s)"},
                    }
                })
                os.unlink(path)
            else:
                log(f"recent in_progress turn {thread_id} age={age:.0f}s, leaving for owner")
        else:
            os.unlink(path)


# ------------------------------------------------------------------
# Protocol handlers
# ------------------------------------------------------------------

def handle_initialize(params):
    client = (params.get("clientInfo") or {}).get("name", "unknown")
    log(f"initialize: client={client} review_enabled={REVIEW_ENABLED}")
    _recover_orphaned_turns()
    return {
        "userAgent": f"claude-adapter/0.3.0 (Codex-protocol shim for Claude{'+Codex review' if REVIEW_ENABLED else ''})",
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
    log(f"thread/start: {thread_id} cwd={cwd}")
    return {"thread": {"id": thread_id}}


def claude_flags_for(approval, sandbox):
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


# ------------------------------------------------------------------
# Git helpers
# ------------------------------------------------------------------

def git_rebase_to_main(cwd, timeout=60):
    """Fetch + rebase current branch onto origin/main. Returns (success, msg)."""
    try:
        subprocess.run(["git", "fetch", "origin", "main"],
                       cwd=cwd, capture_output=True, timeout=timeout)
        r = subprocess.run(["git", "rebase", "origin/main"],
                           cwd=cwd, capture_output=True, text=True, timeout=timeout)
        if r.returncode == 0:
            return True, "rebased to origin/main"
        if "no rebase without being up to date" in r.stderr.lower():
            return True, "already up to date"
        return False, f"rebase failed: {r.stderr[:200]}"
    except subprocess.TimeoutExpired:
        return False, "rebase timed out"
    except Exception as e:
        return False, f"rebase error: {e}"


def get_git_diff(cwd):
    try:
        out = subprocess.run(
            ["git", "diff", "HEAD~1", "HEAD"],
            cwd=cwd, capture_output=True, text=True, timeout=10,
        )
        if out.returncode == 0 and out.stdout.strip():
            return out.stdout
        out2 = subprocess.run(
            ["git", "show", "HEAD"],
            cwd=cwd, capture_output=True, text=True, timeout=10,
        )
        return out2.stdout if out2.returncode == 0 else "(no diff available)"
    except Exception as e:
        return f"(failed to capture diff: {e})"


# ------------------------------------------------------------------
# Backends
# ------------------------------------------------------------------

def run_claude_p(cwd, prompt, approval, sandbox, timeout_s=300):
    """Spawn `claude -p` with non-blocking output + real timeout enforcement.

    Returns (stdout, returncode, stderr, usage_dict).
    usage_dict: {"input_tokens": int, "output_tokens": int, "total_tokens": int} or None.
    """
    flags = claude_flags_for(approval, sandbox)
    log(f"claude: prompt_chars={len(prompt)} timeout={timeout_s}s flags={flags}")
    usage = None
    try:
        proc = subprocess.Popen(
            ["claude", "-p", prompt, "--bare",
             "--output-format", "stream-json", "--verbose"] + flags,
            cwd=cwd,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        stdout_parts = []
        stderr_parts = []
        deadline = time.time() + timeout_s

        while time.time() < deadline:
            ready, _, _ = select.select([proc.stdout, proc.stderr], [], [], 0.5)
            if not ready and proc.poll() is not None:
                break
            for fd in ready:
                if fd == proc.stdout:
                    chunk = os.read(fd.fileno(), 32768)
                    if chunk:
                        stdout_parts.append(chunk.decode("utf-8", errors="replace"))
                elif fd == proc.stderr:
                    chunk = os.read(fd.fileno(), 8192)
                    if chunk:
                        stderr_parts.append(chunk.decode("utf-8", errors="replace"))

        stdout_text = "".join(stdout_parts)
        stderr_text = "".join(stderr_parts)

        if proc.poll() is None:
            log(f"claude: timeout after {timeout_s}s, killing")
            proc.kill()
            proc.wait()
            return "", 124, f"timed out after {timeout_s}s", None

        proc.wait()
        usage = _parse_claude_usage_stream(stdout_text)
        return stdout_text, proc.returncode, stderr_text, usage

    except FileNotFoundError:
        return "", 127, "claude CLI not found in PATH", None
    except Exception as e:
        log(f"run_claude_p exception: {type(e).__name__}: {e}")
        return "", 1, f"{type(e).__name__}: {e}", None


def _parse_claude_usage_stream(stream_text):
    """Extract usage from a Claude --output-format=stream-json --verbose stream.

    Looks for the 'result' event which contains a 'usage' object with
    input_tokens / output_tokens / total_tokens.
    """
    for line in reversed(stream_text.splitlines()):
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except (json.JSONDecodeError, ValueError):
            continue
        if isinstance(obj, dict) and obj.get("type") == "result":
            u = obj.get("usage")
            if isinstance(u, dict):
                it = u.get("input_tokens", 0) or 0
                ot = u.get("output_tokens", 0) or 0
                return {"input_tokens": it, "output_tokens": ot, "total_tokens": it + ot}
            return None
        break
    m = re.search(
        r"(\d+)\s+[Ii]nput\s+[Tt]okens?[,;\s]+(\d+)\s+[Oo]utput\s+[Tt]okens?",
        stream_text,
    )
    if m:
        it, ot = int(m.group(1)), int(m.group(2))
        return {"input_tokens": it, "output_tokens": ot, "total_tokens": it + ot}
    return None


def run_codex_app_server(cwd, prompt, approval, sandbox, timeout_s=30):
    """Spawn codex app-server, run a review turn, return (output_text, exit_status, err_msg, None).

    Returns 4-tuple (output_text, status, err, None) for compatibility.
    """
    cmd = [CODEX_BIN, "app-server"]
    log(f"codex: spawning {cmd} cwd={cwd} timeout={timeout_s}s")
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    except FileNotFoundError:
        return "", "codex CLI not found in PATH", "codex CLI not found in PATH", None

    output_text = ""
    turn_status = "unknown"
    error_msg = None

    def codex_send(msg):
        try:
            proc.stdin.write(json.dumps(msg) + "\n")
            proc.stdin.flush()
        except Exception:
            pass

    def codex_readline_deadline(deadline_ts):
        while time.time() < deadline_ts:
            ready, _, _ = select.select([proc.stdout], [], [], 0.5)
            if ready:
                line = proc.stdout.readline()
                if line:
                    return line
        return ""

    try:
        # 1. initialize
        codex_send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
                    "params": {"clientInfo": {"name": "claude-adapter-reviewer", "version": "0.3.0"}}})
        line = codex_readline_deadline(time.time() + timeout_s)
        init_resp = None
        if line:
            try:
                obj = json.loads(line)
                if obj.get("id") == 1:
                    init_resp = obj
            except Exception:
                pass
        if not init_resp or "error" in init_resp:
            return "", f"codex init failed: {init_resp}", "init failed", None

        # 2. initialized
        codex_send({"jsonrpc": "2.0", "method": "initialized", "params": {}})

        # 3. thread/start
        codex_send({"jsonrpc": "2.0", "id": 2, "method": "thread/start",
                    "params": {"cwd": cwd, "approvalPolicy": approval, "sandbox": sandbox}})
        line = codex_readline_deadline(time.time() + timeout_s)
        thread_resp = None
        if line:
            try:
                obj = json.loads(line)
                if obj.get("id") == 2:
                    thread_resp = obj
            except Exception:
                pass
        if not thread_resp or "error" in thread_resp:
            return "", f"codex thread/start failed: {thread_resp}", "thread/start failed", None

        # 4. turn/start
        codex_send({"jsonrpc": "2.0", "id": 3, "method": "turn/start",
                    "params": {"threadId": thread_resp["result"]["thread"]["id"],
                               "input": [{"type": "text", "text": prompt}]}})
        line = codex_readline_deadline(time.time() + timeout_s)
        turn_resp = None
        if line:
            try:
                obj = json.loads(line)
                if obj.get("id") == 3:
                    turn_resp = obj
            except Exception:
                pass
        if not turn_resp or "error" in turn_resp:
            return "", f"codex turn/start failed: {turn_resp}", "turn/start failed", None

        # 5. Stream notifications until turn/completed or timeout
        deadline = time.time() + 600
        while time.time() < deadline:
            line = proc.stdout.readline()
            if not line:
                if proc.poll() is not None:
                    break
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

        if time.time() >= deadline:
            turn_status = "timeout"
            error_msg = "codex review timed out after 600s"

        if error_msg:
            return output_text, f"codex turn failed: {error_msg}", error_msg, None
        return output_text, turn_status, None, None

    finally:
        try:
            proc.terminate()
            proc.wait(timeout=5)
        except Exception:
            try:
                proc.kill()
            except Exception:
                pass


# ------------------------------------------------------------------
# Review-mode helpers
# ------------------------------------------------------------------

def build_review_prompt(original_task, diff):
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
    return f"""Codex reviewed your changes and requested changes. Apply them now.

ORIGINAL TASK:
{original_task[:2000]}

CODEX FEEDBACK:
{verdict_feedback}

Make the fixes, commit on the same branch, and push. Then report what you did."""


def parse_codex_verdict(output):
    last = output.strip().splitlines()[-3:] if output.strip() else []
    blob = " ".join(last).upper()
    if "VERDICT: APPROVED" in blob or "VERDICT:APPROVED" in blob:
        return "approved"
    if "VERDICT: CHANGES_NEEDED" in blob or "VERDICT:CHANGES_NEEDED" in blob:
        return "changes_needed"
    return "unclear"


# ------------------------------------------------------------------
# turn/start dispatch
# ------------------------------------------------------------------

def handle_turn_start(params):
    thread_id = params.get("threadId")
    if not isinstance(thread_id, str):
        return {"status": "failed", "error": f"threadId is not a string: {type(thread_id).__name__}"}
    if thread_id not in threads:
        return {"status": "failed", "error": f"unknown thread: {thread_id}"}

    prompt = extract_prompt(params.get("input", []))
    if not prompt.strip():
        return {"status": "failed", "error": "empty prompt"}

    state = thread_state[thread_id]
    state["turn_count"] += 1
    turn_n = state["turn_count"]

    if turn_n == 1:
        state["original_prompt"] = prompt

    # Determine backend
    if not REVIEW_ENABLED:
        backend = "claude"
    else:
        if turn_n == 1:
            backend = "claude"
        elif turn_n == 2:
            backend = "codex-review"
        elif turn_n >= 3:
            backend = "claude-finalize" if state.get("last_verdict") == "approved" else "claude-rework"
        else:
            backend = "claude"

    log(f"turn {turn_n} -> backend={backend} (review_enabled={REVIEW_ENABLED})")
    turn_id = f"turn-{uuid.uuid4()}"

    # Pass read_timeout_ms from WORKFLOW.md if set (default 30s)
    timeout_s = int(os.environ.get("ADAPTER_TIMEOUT_S", "300"))

    def run_in_background():
        cwd = threads[thread_id]["cwd"]
        turn_usage = None

        # --- Turn 1: auto-rebase to latest main ---
        if turn_n == 1 and backend in ("claude",):
            ok, msg = git_rebase_to_main(cwd)
            log(f"Turn 1 rebase: {ok} ({msg})")

        # --- Persist turn start (crash protection) ---
        save_turn_start(thread_id, turn_n, backend, cwd)

        send({"method": "turn/started",
              "params": {"threadId": thread_id, "turnId": turn_id}})
        send({"method": "item/started",
              "params": {"item": {"type": "agentMessage", "text": ""}}})

        output, status, err = "", "completed", ""
        success = False

        try:
            if backend == "claude":
                output, status, err, turn_usage = run_claude_p(
                    cwd, prompt, threads[thread_id]["approval"],
                    threads[thread_id]["sandbox"], timeout_s=timeout_s)
                success = (status == 0)
                if success:
                    state["last_diff"] = get_git_diff(cwd)

            elif backend == "codex-review":
                review_prompt = build_review_prompt(
                    state["original_prompt"], state.get("last_diff"))
                output, status, err, _ = run_codex_app_server(
                    cwd, review_prompt, threads[thread_id]["approval"],
                    threads[thread_id]["sandbox"], timeout_s=timeout_s)
                state["last_verdict"] = parse_codex_verdict(output)
                log(f"codex verdict: {state['last_verdict']}")
                success = (status == "completed")

            elif backend == "claude-rework":
                rework = build_rework_prompt(
                    output or state.get("last_verdict", ""), state["original_prompt"])
                output, status, err, _ = run_claude_p(
                    cwd, rework, threads[thread_id]["approval"],
                    threads[thread_id]["sandbox"], timeout_s=timeout_s)
                success = (status == 0)
                if success:
                    state["last_diff"] = get_git_diff(cwd)

            elif backend == "claude-finalize":
                output, status, err, _ = run_claude_p(
                    cwd,
                    "Codex approved your work. Write a one-paragraph summary of what you did and what files changed.",
                    threads[thread_id]["approval"],
                    threads[thread_id]["sandbox"], timeout_s=timeout_s)
                success = (status == 0)

        except Exception as e:
            log(f"backend {backend} crashed: {type(e).__name__}: {e}")
            status = "failed"
            err = f"{type(e).__name__}: {e}"
            success = False

        # --- Emit item/completed ---
        output_snippet = output if output else f"[exit={status} err={err}]"
        send({"method": "item/completed",
              "params": {"item": {"type": "agentMessage",
                                   "text": f"[backend={backend}]\n\n{output_snippet}"}}})

        # --- Emit turn/completed ---
        params = {"threadId": thread_id, "turnId": turn_id}
        if success:
            params.update({"status": "completed", "error": None})
            if turn_usage:
                params["usage"] = turn_usage
        else:
            params.update({"status": "failed",
                          "error": {"message": f"backend={backend} exit={status}: {str(err)[:200]}"}})

        send({"method": "turn/completed", "params": params})
        save_turn_done(thread_id, status, status if not success else 0,
                       str(err) if err else None, turn_usage)

    threading.Thread(target=run_in_background, daemon=True).start()
    return {"turn": {"id": turn_id, "status": "inProgress"}}


# ------------------------------------------------------------------
# Main loop
# ------------------------------------------------------------------

def main():
    log(f"claude-adapter v0.3.0 starting; review_enabled={REVIEW_ENABLED} "
        f"covex_bin={CODEX_BIN} state_dir={ADAPTER_STATE_DIR}")
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
