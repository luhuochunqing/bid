#!/usr/bin/env bash
# Input: current shell PATH and project scripts/git presence
# Output: pass/fail report on whether the system-level --no-verify prohibition wrapper is active
# Pos: scripts/ - verification helper for git safety (part of CI unblock and gate enforcement)
# 维护声明: 若 wrapper logic or health check criteria change, sync with CLAUDE.md §5/§6 and pre-push-gate.
# Quick health check: is the project git wrapper active and blocking --no-verify?
# Used by agent:health-check and manual verification.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== XiYu git wrapper safety check ==="

GIT_CMD="$(command -v git 2>/dev/null || echo 'NOT FOUND')"
echo "git in PATH: $GIT_CMD"

if [[ "$GIT_CMD" == *"/scripts/git" ]]; then
  echo "✓ Wrapper is first in PATH"
else
  echo "✗ Wrapper NOT active (raw git may allow --no-verify)"
  exit 1
fi

# Test that --no-verify is rejected (we use a no-op command that would otherwise succeed)
if "$GIT_CMD" --no-verify --version >/dev/null 2>&1; then
  echo "✗ Wrapper failed to reject --no-verify"
  exit 1
else
  echo "✓ --no-verify correctly rejected (non-zero exit)"
fi

echo "✓ Git safety wrapper is functioning."
