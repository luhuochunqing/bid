#!/usr/bin/env bash
# Input: none
# Output: validates the dry-run output contract for scripts/agent-start-task.sh
# Pos: scripts/test/agent-start-task-dryrun-contract.sh
# 维护声明: 若 agent-start-task.sh 的 dry-run 协作提示文案或关键区块变更，请同步更新此契约测试。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TARGET_SCRIPT="$REPO_ROOT/scripts/agent-start-task.sh"

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local description="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "[FAIL] missing: $description" >&2
    echo "        expected snippet: $needle" >&2
    exit 1
  fi
  echo "[PASS] $description"
}

run_contract_case() {
  local case_name="$1"
  shift
  echo "== $case_name =="
  local output
  output="$(bash "$TARGET_SCRIPT" "$@")"
  printf '%s\n' "$output"
  echo
  case "$case_name" in
    rich-dry-run)
      assert_contains "$output" "touch paths:" "prints declared touch paths"
      assert_contains "$output" "Expected status summary:" "prints expected status summary header"
      assert_contains "$output" ".agent-task-context will record declared touch paths" "mentions touch path context persistence"
      assert_contains "$output" "conflict override is enabled; coordination evidence should be recorded if conflicts appear" "mentions conflict override coordination reminder"
      assert_contains "$output" "initial locks will be registered in the new worktree" "mentions lock registration expectation"
      assert_contains "$output" "branch will be pushed automatically and become visible to other agents" "mentions auto push visibility"
      assert_contains "$output" "post-create push:" "prints post-create push command"
      ;;
    minimal-dry-run)
      assert_contains "$output" "Expected status summary:" "prints expected status summary header"
      assert_contains "$output" "no touch paths declared; consider using --touch for collaborative visibility" "warns about missing touch paths"
      assert_contains "$output" "no initial locks will be registered automatically" "warns about missing initial locks"
      assert_contains "$output" "branch will remain local until you push it manually" "warns about local-only branch"
      ;;
    *)
      echo "Unknown contract case: $case_name" >&2
      exit 1
      ;;
  esac
}

run_contract_case \
  rich-dry-run \
  cursor demo-task origin/main --dry-run \
  --touch scripts/agent-start-task.sh \
  --lock scripts/agent-start-task.sh \
  --force-touch-conflict \
  --push

run_contract_case \
  minimal-dry-run \
  cursor demo-task origin/main --dry-run

echo "All agent-start-task dry-run contract checks passed."
