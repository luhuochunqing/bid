#!/usr/bin/env bash
# Input: staged Flyway migration files from git index
# Output: validates header quality — version line, description, blueprint traceability
# Pos: scripts/ — Flyway migration header quality guardrail
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rules (hard block):
#   1. File must start with a SQL comment (`-- ...`) before any SQL statement.
#   2. The header must contain the version number (V{version}).
#   3. The header must have a meaningful description (> 15 chars of comment text).
#
# Rules (soft warning, non-blocking):
#   - Blueprint traceability (§, blueprint, 蓝图, #N/M) is encouraged.
#
# These checks prevent migration files with zero context (bare ALTER TABLE with
# no header comment), which makes audit and rollback difficult.

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

extract_version() {
  echo "$1" | sed -n -E 's/^V([0-9]+).*/\1/p'
}

STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACMR | grep "^${MIGRATION_DIR}/V" || true)

if [ -z "$STAGED_MIGRATIONS" ]; then
  echo "${script_name}: no staged Flyway migrations, skipping."
  exit 0
fi

errors=0
warnings=0
checked=0

while IFS= read -r file; do
  basename=$(basename "$file")
  version=$(extract_version "$basename")

  if [ -z "$version" ]; then
    red "${script_name}: SKIP — cannot extract version from ${basename}"
    continue
  fi

  checked=$((checked + 1))
  local_errors=0

  # 1) File must not be empty
  first_line=$(head -1 "$file" 2>/dev/null || true)
  if [ -z "$first_line" ]; then
    red "${script_name}: FAIL — ${basename} is empty"
    errors=$((errors + 1))
    continue
  fi

  # 2) Must start with a SQL comment
  if ! echo "$first_line" | grep -qE '^[[:space:]]*--'; then
    red "${script_name}: FAIL — ${basename} does not start with a SQL comment (-- ...)"
    red "  First line: ${first_line}"
    errors=$((errors + 1))
    local_errors=1
  fi

  # 3) Collect header lines (consecutive -- lines before first SQL)
  header_lines=""
  while IFS= read -r line; do
    if echo "$line" | grep -qE '^[[:space:]]*--'; then
      header_lines="${header_lines}${line}"$'\n'
    elif echo "$line" | grep -qE '^[[:space:]]*$'; then
      continue
    else
      break
    fi
  done < "$file"

  # 4) Header must mention version
  if ! echo "$header_lines" | grep -q "V${version}"; then
    yellow "${script_name}: WARN — ${basename} header does not mention version V${version}"
    warnings=$((warnings + 1))
  fi

  # 5) Header must have meaningful description (> 15 chars comment text)
  header_text=$(echo "$header_lines" | sed 's/^[[:space:]]*--[[:space:]]*//g' | tr -d '\n' | tr -d ' ')
  if [ "${#header_text}" -lt 15 ]; then
    red "${script_name}: FAIL — ${basename} header too short (${#header_text} chars). Add a descriptive comment."
    red "  Current header: $(echo "$header_lines" | head -1)"
    errors=$((errors + 1))
    local_errors=1
  fi

  # 6) Soft: blueprint traceability
  if ! echo "$header_lines" | grep -qiE '§|blueprint|蓝图|#[0-9]+/[0-9]+'; then
    yellow "${script_name}: WARN — ${basename} lacks blueprint traceability (e.g. §4.2.1, 蓝图, #7/13)"
    warnings=$((warnings + 1))
  fi

  if [ "$local_errors" -eq 0 ]; then
    green "${script_name}: PASS — ${basename}"
  fi

done <<< "$STAGED_MIGRATIONS"

echo ""
echo "${script_name}: checked=${checked} errors=${errors} warnings=${warnings}"

if [ "$errors" -gt 0 ]; then
  echo ""
  red "${script_name}: ${errors} migration header error(s) found."
  echo ""
  echo "  Required header format:"
  echo "    -- V{version}: {short description}"
  echo "    -- §{blueprint-section} #{task-number}/{total-tasks} {context}"
  echo ""
  echo "  Example:"
  echo "    -- V1019: Add supplier qualification expiry tracking"
  echo "    -- §4.3 #5/13 供应商资质 — tracks expiry and renewal workflow"
  exit 1
fi

if [ "$warnings" -gt 0 ]; then
  yellow "${script_name}: ${warnings} warning(s) — see above. Consider adding blueprint traceability."
fi

exit 0
