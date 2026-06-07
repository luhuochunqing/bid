#!/usr/bin/env bash
# Input: Git branch refs and a pathspec supplied by the caller
# Output: agent branches with unmerged commits touching that pathspec
# Pos: scripts/ - Repository maintenance guardrail
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# who-touches.sh — list agent/* branches with unmerged changes touching <pathspec>.
#
# Used as the file-level half of the multi-agent lease protocol (see CLAUDE.md
# §"任务启动协议"). Pairs with grep over plan.md scope declarations.
#
# Usage:
#   scripts/who-touches.sh <pathspec>      # report touching agent branches
#   scripts/who-touches.sh --self-test     # run sanity test on a fake repo
#   scripts/who-touches.sh --help          # this help
#
# Env:
#   WHO_TOUCHES_BASE  comparison baseline (default: origin/main, falls back to main)
#
# Output (one line per touching branch, tab-separated):
#   <branch>\t<n> commits\t<last-commit-age>\t<last-commit-subject>
#
# Exit codes:
#   0  no conflicts found OR self-test passed
#   1  conflicts found (pre-push hook can use this) OR usage / runtime error
#
# Notes:
#   * scans refs/heads/agent/* + refs/remotes/origin/agent/*, deduped, origin preferred
#   * skips current branch (you're not in conflict with yourself)
#   * relies on agents pushing WIP branches at least daily (see CLAUDE.md §纪律)

# Activate git safety wrapper (blocks --no-verify at system level) so that
# lease coordination commands themselves cannot accidentally bypass gates.
if [[ -f "$(dirname "${BASH_SOURCE[0]}")/dev-env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$(dirname "${BASH_SOURCE[0]}")/dev-env.sh" || true
fi

set -euo pipefail

resolve_base() {
    local base="${WHO_TOUCHES_BASE:-origin/main}"
    if git rev-parse --verify --quiet "$base" >/dev/null 2>&1; then
        printf '%s' "$base"
        return 0
    fi
    if git rev-parse --verify --quiet main >/dev/null 2>&1; then
        printf 'main'
        return 0
    fi
    return 1
}

list_agent_branches() {
    {
        git for-each-ref --format='%(refname:short)' refs/heads/agent/ 2>/dev/null || true
        git for-each-ref --format='%(refname:short)' refs/remotes/origin/agent/ 2>/dev/null \
            | sed 's|^origin/||' || true
    } | sort -u
}

run_check() {
    local pathspec="$1"
    local base
    if ! base=$(resolve_base); then
        printf 'who-touches: no origin/main or main reference found\n' >&2
        return 1
    fi

    local current
    current=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || printf '')

    local branches
    branches=$(list_agent_branches)
    if [ -z "$branches" ]; then
        printf '(no agent/* branches found)\n' >&2
        return 0
    fi

    local found=0
    while IFS= read -r branch; do
        [ -z "$branch" ] && continue
        [ "$branch" = "$current" ] && continue

        local ref
        if git rev-parse --verify --quiet "refs/remotes/origin/$branch" >/dev/null 2>&1; then
            ref="refs/remotes/origin/$branch"
        elif git rev-parse --verify --quiet "refs/heads/$branch" >/dev/null 2>&1; then
            ref="refs/heads/$branch"
        else
            continue
        fi

        local count
        count=$(git rev-list --count "$base..$ref" -- "$pathspec" 2>/dev/null || printf '0')
        [ "$count" -eq 0 ] && continue

        local last
        last=$(git log -1 --format='%ar%x09%s' "$ref" -- "$pathspec" 2>/dev/null || printf '')
        [ -z "$last" ] && continue

        printf '%s\t%s commits\t%s\n' "$branch" "$count" "$last"
        found=1
    done <<<"$branches"

    if [ "$found" = "0" ]; then
        printf "(no active agent/* branches touching '%s')\n" "$pathspec" >&2
        return 0
    fi
    return 1
}

self_test() {
    local script_path
    script_path="$(cd "$(dirname "$0")" && pwd -P)/$(basename "$0")"

    local tmpdir
    tmpdir=$(mktemp -d)
    trap 'rm -rf "$tmpdir"' RETURN

    (
        set -e
        cd "$tmpdir"
        git init -q -b main
        git -c user.email=t@t.t -c user.name=t commit -q --allow-empty -m initial
        printf 'alpha\n' >target.txt
        git add target.txt
        git -c user.email=t@t.t -c user.name=t commit -q -m baseline

        git checkout -q -b agent/foo
        printf 'modified\n' >target.txt
        git add target.txt
        git -c user.email=t@t.t -c user.name=t commit -q -m "foo edits target"

        git checkout -q main
        git checkout -q -b agent/bar
        printf 'unrelated\n' >other.txt
        git add other.txt
        git -c user.email=t@t.t -c user.name=t commit -q -m "bar edits other"

        git checkout -q main

        # Positive: target.txt should surface agent/foo
        local out
        out=$(WHO_TOUCHES_BASE=main "$script_path" target.txt 2>/dev/null) || true
        printf '%s\n' "$out" | grep -q '^agent/foo' \
            || { printf '[FAIL] agent/foo NOT detected on target.txt:\n%s\n' "$out" >&2; exit 1; }
        printf '[PASS] agent/foo detected on target.txt\n'

        # Negative: agent/bar must NOT surface for target.txt
        if printf '%s\n' "$out" | grep -q '^agent/bar'; then
            printf '[FAIL] agent/bar wrongly detected on target.txt:\n%s\n' "$out" >&2
            exit 1
        fi
        printf '[PASS] agent/bar correctly excluded from target.txt\n'

        # Reverse: other.txt should surface agent/bar (proves it's not always silent)
        out=$(WHO_TOUCHES_BASE=main "$script_path" other.txt 2>/dev/null) || true
        printf '%s\n' "$out" | grep -q '^agent/bar' \
            || { printf '[FAIL] agent/bar NOT detected on other.txt:\n%s\n' "$out" >&2; exit 1; }
        printf '[PASS] agent/bar detected on other.txt\n'
    )
    printf '[PASS] who-touches.sh self-test\n'
}

print_help() {
    sed -n '2,20p' "$0" | sed 's/^# \?//'
}

case "${1:-}" in
    ""|"-h"|"--help")
        print_help
        ;;
    --self-test)
        self_test
        ;;
    *)
        run_check "$1"
        ;;
esac
