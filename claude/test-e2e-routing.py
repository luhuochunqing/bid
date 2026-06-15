#!/usr/bin/env python3
"""Multi-turn E2E test for claude-app-server.py with optional routing mode.

Drives the adapter through:
  - initialize
  - thread/start
  - turn 1 (claude writes)
  - turn 2 (in REVIEW_ENABLED=1 mode, this routes to codex-review; otherwise claude)
  - turn 3 (in REVIEW_ENABLED=1 mode, routes to claude-rework or claude-finalize)

Usage:
    python3 claude/test-e2e-routing.py
    REVIEW_ENABLED=1 python3 claude/test-e2e-routing.py
    python3 claude/test-e2e-routing.py /path/to/claude-app-server.py
"""

import json
import os
import subprocess
import sys
import time


def main():
    adapter = sys.argv[1] if len(sys.argv) > 1 else "/Users/user/symphony/bin-shim/claude-app-server.py"
    review = os.environ.get("REVIEW_ENABLED", "0") == "1"
    print(f"=== Multi-turn test (REVIEW_ENABLED={review}) ===\n")

    proc = subprocess.Popen(
        [adapter],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1,
    )

    def send(msg):
        proc.stdin.write(json.dumps(msg) + "\n")
        proc.stdin.flush()

    def read_response(target_id, timeout=60):
        deadline = time.time() + timeout
        while time.time() < deadline:
            line = proc.stdout.readline().strip()
            if not line:
                time.sleep(0.05)
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("id") == target_id:
                return obj
            # Notifications come on the same stream; surface them but keep reading
            if "method" in obj:
                method = obj["method"]
                if method == "item/completed":
                    text = obj.get("params", {}).get("item", {}).get("text", "")
                    print(f"\n  [{method}]:\n  {text[:400]}{'...' if len(text) > 400 else ''}")
                else:
                    print(f"  [notification: {method}]")
        return None

    # 1. initialize
    send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
          "params": {"clientInfo": {"name": "test-e2e-routing", "version": "0.1.0"}}})
    r = read_response(1)
    assert r and "result" in r, f"initialize failed: {r}"
    print(f"[ok] initialize: {r['result']['userAgent'][:80]}")

    # 2. thread/start
    send({"jsonrpc": "2.0", "id": 2, "method": "thread/start",
          "params": {"cwd": "/tmp", "approvalPolicy": "on-failure", "sandbox": "workspace-write"}})
    r = read_response(2)
    assert r and "thread" in r["result"], f"thread/start failed: {r}"
    thread_id = r["result"]["thread"]["id"]
    print(f"[ok] thread: {thread_id}")

    # 3. turn 1: claude writes
    print(f"\n--- turn 1 (claude) ---")
    send({"jsonrpc": "2.0", "id": 3, "method": "turn/start",
          "params": {"threadId": thread_id,
                     "input": [{"type": "text", "text": "Run `pwd` and report the absolute path. Be terse, one line."}]}})
    r = read_response(3, timeout=120)
    assert r and r.get("result"), f"turn 1 failed: {r}"
    print(f"[ok] turn 1: {r['result']}")

    # 4. turn 2: routing decision
    print(f"\n--- turn 2 (routing decision) ---")
    send({"jsonrpc": "2.0", "id": 4, "method": "turn/start",
          "params": {"threadId": thread_id,
                     "input": [{"type": "text", "text": "Continuation (Symphony's default)."}]}})
    r = read_response(4, timeout=600)  # codex review can be slow
    if r and r.get("result"):
        print(f"[ok] turn 2: {r['result']}")
    else:
        print(f"[fail] turn 2: {r}")

    # 5. turn 3 (only meaningful in routing mode)
    if review:
        print(f"\n--- turn 3 (rework/finalize) ---")
        send({"jsonrpc": "2.0", "id": 5, "method": "turn/start",
              "params": {"threadId": thread_id,
                         "input": [{"type": "text", "text": "Continuation."}]}})
        r = read_response(5, timeout=600)
        if r and r.get("result"):
            print(f"[ok] turn 3: {r['result']}")
        else:
            print(f"[fail] turn 3: {r}")

    proc.stdin.close()
    try:
        proc.wait(timeout=5)
    except subprocess.TimeoutExpired:
        proc.kill()
    print(f"\n=== adapter stderr (last 10 lines) ===")
    for line in proc.stderr.read().splitlines()[-10:]:
        print(f"  {line}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
