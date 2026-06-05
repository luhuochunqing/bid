#!/usr/bin/env bash
# Input: staged Flyway migration files from git index
# Output: detects version number conflicts against origin/main
# Pos: scripts/ — Flyway migration version conflict guardrail
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"

# 1. 检查是否有 staged 迁移文件
STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACMR | grep "^${MIGRATION_DIR}/V" || true)
if [ -z "$STAGED_MIGRATIONS" ]; then
  echo "flyway-versions: no staged Flyway migrations, skipping."
  exit 0
fi

# 2. 提取 staged 迁移的版本号
staged_versions=()
while IFS= read -r file; do
  basename=$(basename "$file")
  version=$(echo "$basename" | sed -n 's/^V\([0-9]\+\).*/\1/p')
  if [ -n "$version" ]; then
    staged_versions+=("$version")
    echo "flyway-versions: staged V${version} -> ${file}"
  fi
done <<< "$STAGED_MIGRATIONS"

if [ "${#staged_versions[@]}" -eq 0 ]; then
  echo "flyway-versions: no version numbers extracted, skipping."
  exit 0
fi

# 3. Fetch origin/main (shallow clone safe)
REMOTE=$(git remote get-url origin 2>/dev/null || echo "")
if [ -z "$REMOTE" ]; then
  echo "flyway-versions: no origin remote, skipping conflict check."
  exit 0
fi

# Try to get main's migration versions
main_versions=""
if git fetch origin main --depth=1 2>/dev/null; then
  main_versions=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)
fi

if [ -z "$main_versions" ]; then
  echo "flyway-versions: could not determine origin/main migration versions, skipping conflict check."
  exit 0
fi

# 4. 检查冲突
conflicts_found=false
while IFS= read -r main_version; do
  for sv in "${staged_versions[@]}"; do
    if [ "$sv" = "$main_version" ]; then
      echo "flyway-versions: CONFLICT — V${sv} exists in both staged and origin/main"
      conflicts_found=true
    fi
  done
done <<< "$main_versions"

if [ "$conflicts_found" = true ]; then
  echo ""
  echo "flyway-versions: Flyway 版本冲突！请重新编号你的迁移文件以避免与 main 冲突。"
  echo "  已占用版本号（origin/main 最新）："
  echo "$main_versions" | tail -5 | while read v; do echo "    V${v}"; done
  echo ""
  echo "  建议：使用 V$(($(echo "$main_versions" | tail -1) + 1)) 作为你的起始版本号。"
  exit 1
fi

echo "flyway-versions: passed. checked_migrations=${#staged_versions[@]}"
exit 0
