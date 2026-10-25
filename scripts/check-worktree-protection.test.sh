#!/usr/bin/env bash
# Input: temporary Git repositories created by this test
# Output: regression result for shared common-gitdir topology enforcement
# Pos: scripts/ - check-worktree-protection.sh executable regression
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

# Git hooks may export repository-scoped variables; fixtures must resolve their own repositories.
unset GIT_DIR GIT_WORK_TREE GIT_COMMON_DIR

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHECK_SCRIPT="$ROOT_DIR/scripts/check-worktree-protection.sh"
FIXTURE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/xiyu-gitdir-test.XXXXXX")"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

git_quiet() {
  git -c user.name='Gitdir Test' -c user.email='gitdir-test@example.invalid' "$@" >/dev/null
}

MAIN_REPO="$FIXTURE_ROOT/main repo"
MANAGED_BASE="$FIXTURE_ROOT/managed worktrees"
LINKED_WORKTREE="$MANAGED_BASE/claude"
ROGUE_CLONE="$MANAGED_BASE/codex"
mkdir -p "$MAIN_REPO" "$MANAGED_BASE"

git_quiet -C "$MAIN_REPO" init
git_quiet -C "$MAIN_REPO" commit --allow-empty -m initial
git_quiet -C "$MAIN_REPO" worktree add -b agent/claude-init "$LINKED_WORKTREE"

EXPECTED_COMMON_DIR="$(git -C "$MAIN_REPO" rev-parse --path-format=absolute --git-common-dir)"

bash "$CHECK_SCRIPT" \
  --worktree-base "$MANAGED_BASE" \
  --expected-common-dir "$EXPECTED_COMMON_DIR" >/dev/null

git_quiet clone "$MAIN_REPO" "$ROGUE_CLONE"
ROGUE_CLONE="$(cd "$ROGUE_CLONE" && pwd -P)"

if bash "$CHECK_SCRIPT" \
  --worktree-base "$MANAGED_BASE" \
  --expected-common-dir "$EXPECTED_COMMON_DIR" >"$FIXTURE_ROOT/failure.out" 2>&1; then
  echo "FAIL: independent clone inside managed base was accepted"
  exit 1
fi

if ! grep -F "$ROGUE_CLONE" "$FIXTURE_ROOT/failure.out" >/dev/null; then
  echo "FAIL: rejection did not identify rogue path"
  sed -n '1,120p' "$FIXTURE_ROOT/failure.out"
  exit 1
fi

echo "PASS: shared gitdir topology accepts linked worktrees and rejects independent clones"
