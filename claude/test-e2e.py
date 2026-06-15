#!/usr/bin/env python3
"""End-to-end test for claude-app-server.py.

Spawns the adapter, drives it through initialize -> thread/start -> turn/start,
streams notifications, prints the result, and exits. No Symphony in the loop.

Usage:
    python3 claude/test-e2e.py
    python3 claude/test-e2e.py /Users/user/symphony/bin-shim/claude-app-server.py
"""

import json
import subprocess
import sys
import time


def main():
    adapter = sys.argv[1] if len(sys.argv) > 1 else "/Users/user/symphony/bin-shim/claude-app-server.py"
    proc = subprocess.Popen(
        [adapter],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, bufsize=1,
    )

    def send(msg):
        proc.stdin.write(json.dumps(msg) + "\n")
        proc.stdin.flush()

    def read(timeout=2.0):
        end = time.time() + timeout
        while time.time() < end:
            line = proc.stdout.readline().strip()
            if line:
                try:
                    return json.loads(line)
                except json.JSONDecodeError:
                    continue
        return None

    # 1. initialize
    send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
          "params": {"clientInfo": {"name": "test-e2e", "version": "0.1.0"}}})
    r = read()
    assert r and r.get("id") == 1 and "result" in r, f"initialize failed: {r}"
    print(f"[ok] initialize: {r['result']['userAgent'][:60]}...")

    # 2. initialized notification
    send({"jsonrpc": "2.0", "method": "initialized", "params": {}})

    # 3. thread/start
    send({"jsonrpc": "2.0", "id": 2, "method": "thread/start",
          "params": {"cwd": "/tmp", "approvalPolicy": "on-failure", "sandbox": "workspace-write"}})
    r = read()
    assert r and r.get("id") == 2 and "threadId" in r["result"], f"thread/start failed: {r}"
    thread_id = r["result"]["threadId"]
    print(f"[ok] thread: {thread_id}")

    # 4. turn/start
    send({"jsonrpc": "2.0", "id": 3, "method": "turn/start",
          "params": {"threadId": thread_id,
                     "input": [{"type": "text", "text": "Run `pwd && ls -1 | head -3`. Be terse, one line each."}]}})

    seen = []
    deadline = time.time() + 90
    final = None
    while time.time() < deadline:
        obj = read(timeout=0.5)
        if not obj:
            continue
        if obj.get("id") == 3:
            final = obj
            break
        method = obj.get("method")
        if method:
            seen.append(method)
            if method == "item/completed":
                text = obj.get("params", {}).get("item", {}).get("text", "")
                print(f"[claude output]:\n{text[:400]}{'...' if len(text) > 400 else ''}")

    assert final, "no turn response received"
    print(f"\n[ok] notifications: {seen}")
    print(f"[ok] turn result: {final.get('result') or final.get('error')}")

    proc.stdin.close()
    proc.wait(timeout=5)
    return 0 if final.get("result", {}).get("status") == "completed" else 1


if __name__ == "__main__":
    sys.exit(main())
