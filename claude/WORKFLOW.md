---
# Example Symphony WORKFLOW.md for the Claude Code adapter.
# Copy this to your project root as WORKFLOW.md (overwriting the Codex version)
# and restart Symphony to dispatch issues to Claude instead of Codex.
#
# The `codex.command` field is misleadingly named — it's just the command
# Symphony spawns. The adapter speaks the same JSON-RPC protocol as Codex
# on stdio, so Symphony can't tell the difference.

tracker:
  kind: linear
  project_slug: "FILL_IN_LINEAR_SLUG"
  required_labels:
    - symphony-eligible
  active_states:
    - Todo
    - 开发中
    - 评审中
    - Merging
    - Rework
  terminal_states:
    - 已完成
    - 取消
    - Duplicate

polling:
  interval_ms: 5000

workspace:
  root: ~/code/workspaces
  hooks:
    after_create: |
      set -euo pipefail
      git clone --depth 1 https://gitee.com/allinai888/bid.git .
      git config user.email "symphony-bot@xiyu.local"
      git config user.name "Symphony Bot (Claude)"
    before_remove: ""

agent:
  max_concurrent_agents: 2
  max_turns: 20

codex:
  # === Switched from `codex app-server` to the Claude adapter ===
  command: /Users/user/symphony/bin-shim/claude-app-server.py
  approval_policy: on-failure
  thread_sandbox: workspace-write
  read_timeout_ms: 30000
---

# (markdown body) — see WORKFLOW.md in the repo root for the full template
with the project rules, hot-paths blacklist, status map, and workpad format.
This file is meant as a focused example showing ONLY the swap from Codex to
Claude. The full project body should be copied from the original WORKFLOW.md.
