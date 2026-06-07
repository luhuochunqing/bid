#!/usr/bin/env bash
# Input: staged Flyway migration files from git index
# Output: detects V*.sql files that lack a corresponding U*.sql rollback script
# Pos: scripts/ — Flyway migration rollback completeness guardrail
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   Every staged V{version}.sql migration MUST have a matching
#   U{version}.sql rollback script in the same directory.
#
# This prevents the recurring issue (2026-06-04: PR #69) where V1036/V1037/V1038
# were merged without rollback scripts, requiring a separate fix PR.
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }

STAGED_VERSIONS=$(git diff --cached --name-only --diff-filter=ACMR | grep "^${MIGRATION_DIR}/V" || true)

if [ -z "$STAGED_VERSIONS" ]; then
  echo "${script_name}: no staged Flyway V migrations, skipping."
  exit 0
fi

errors=0
checked=0

while IFS= read -r vfile; do
  basename=$(basename "$vfile")
  version=$(echo "$basename" | sed -n 's/^V\([0-9]\+\).*/\1/p')

  if [ -z "$version" ]; then
    echo "${script_name}: SKIP — cannot extract version from ${basename}"
    continue
  fi

  checked=$((checked + 1))

  vdir=$(dirname "$vfile")
  ufile="${vdir}/U${version}.sql"

  rollback_staged=$(git diff --cached --name-only --diff-filter=ACMR | grep "^${ufile}$" || true)
  rollback_tracked=$(git ls-files -- "${ufile}" 2>/dev/null || true)

  if [ -z "$rollback_staged" ] && [ -z "$rollback_tracked" ]; then
    red "${script_name}: FAIL — ${basename} has no matching rollback script"
    red "  Expected: ${ufile}"
    red "  Create it before committing. Example:"
    red "    -- U${version}: Rollback {description}"
    red "    -- §{blueprint-section}"
    red "    DROP ...;"
    errors=$((errors + 1))
    continue
  fi

  if [ -z "$rollback_staged" ] && [ -n "$rollback_tracked" ]; then
    if [ -f "${ufile}" ]; then
      echo "${script_name}: WARN — ${basename} staged but ${ufile} exists unstaged. Add it: git add ${ufile}"
    fi
  fi

  green "${script_name}: PASS — ${basename} has matching U${version}.sql"

done <<< "$STAGED_VERSIONS"

echo ""
echo "${script_name}: checked=${checked} errors=${errors}"

if [ "$errors" -gt 0 ]; then
  echo ""
  red "${script_name}: ${errors} migration(s) missing rollback script(s)."
  echo ""
  echo "  Every V{version}.sql migration must have a matching U{version}.sql rollback script."
  echo "  Rollback files live in the same directory as their V counterpart."
  echo ""
  echo "  Quick reference:"
  echo "    touch ${MIGRATION_DIR}/U<version>.sql"
  echo "    git add ${MIGRATION_DIR}/U<version>.sql"
  exit 1
fi

exit 0
