#!/usr/bin/env bash
# CI/Pre-commit gate: detect changes to dev-services scripts and run contract tests
# Input: list of changed files (from git diff)
# Output: exit code 0 if all tests pass or no relevant files changed
# Pos: scripts/test/ - dev services change detection gate
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CHANGED_FILES="${1:-}"

if [[ -z "$CHANGED_FILES" ]]; then
  # Detect changes from git
  CHANGED_FILES=$(cd "$PROJECT_DIR" && git diff --cached --name-only -- 'scripts/dev-*.sh' 'scripts/dev-*.mjs' 2>/dev/null || true)
fi

if [[ -z "$CHANGED_FILES" ]]; then
  echo "dev-services-gate: no dev-* script changes detected, skipping"
  exit 0
fi

echo "dev-services-gate: detected changes in:"
echo "$CHANGED_FILES" | sed 's/^/  /'

echo "dev-services-gate: running contract tests..."
cd "$PROJECT_DIR" && npm run test:dev-services
echo "dev-services-gate: all tests passed ✅"
