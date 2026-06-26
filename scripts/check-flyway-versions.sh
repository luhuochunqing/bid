#!/usr/bin/env bash
# Input: staged Flyway migration files (default) OR named source (--source=push)
# Output: detects version number conflicts + optionally auto-fixes (--fix)
# Pos: scripts/ — Flyway migration version conflict guardrail + auto-number
# 维护声明: 本脚本分两种模式运行：(1) pre-commit: 检查 + 自动编号；(2) pre-push: 强验证。
#           新增迁移目录时请同步修改 MIGRATION_DIR 和 ROLLBACK_DIR。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -z "$ROOT_DIR" ]]; then
  echo "flyway-versions: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
ROLLBACK_DIR="backend/src/main/resources/db/rollback/migration-mysql"

# ── 参数解析 ──
MODE="pre-commit"   # pre-commit | pre-push

for arg in "$@"; do
  case "$arg" in
    --source=push) MODE="pre-push" ;;
    --source=pre-commit) MODE="pre-commit" ;;
    --fix|--staged) ;;  # --fix 已在 pre-push 模式强制执行，忽略
  esac
done

# ── pre-commit 模式：先跑自动编号，再检查冲突 ──
if [[ "$MODE" == "pre-commit" ]]; then
  bash "$ROOT_DIR/scripts/assign-flyway-version.sh"

  STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACMR | \
    grep "^${MIGRATION_DIR}/V" || true)

  if [[ -z "$STAGED_MIGRATIONS" ]]; then
    echo "flyway-versions: no staged Flyway migrations, skipping."
    exit 0
  fi

  staged_versions=()
  while IFS= read -r file; do
    version=$(echo "$(basename "$file")" | sed -n 's/^V\([0-9]\+\).*/\1/p')
    if [[ -n "$version" ]]; then
      staged_versions+=("$version")
      echo "flyway-versions: staged V${version} -> ${file}"
    fi
  done <<< "$STAGED_MIGRATIONS"

  if [[ "${#staged_versions[@]}" -eq 0 ]]; then
    echo "flyway-versions: no version numbers extracted, skipping."
    exit 0
  fi

  echo "flyway-versions: checking ${#staged_versions[@]} staged migration(s)..."

  git fetch origin main --prune 2>/dev/null || true
  main_versions=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
    sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

  if [[ -z "$main_versions" ]]; then
    echo "flyway-versions: could not determine origin/main versions, skipping."
    exit 0
  fi

  conflicts_found=false
  while IFS= read -r main_version; do
    for sv in "${staged_versions[@]}"; do
      if [[ "$sv" == "$main_version" ]]; then
        echo "flyway-versions: CONFLICT — V${sv} 与 origin/main 冲突"
        conflicts_found=true
      fi
    done
  done <<< "$main_versions"

  if [[ "$conflicts_found" == "true" ]]; then
    MAIN_LATEST=$(echo "$main_versions" | tail -1)
    echo ""
    echo "flyway-versions: ⚠ Flyway 版本冲突！"
    echo "  origin/main 最新版本号：V${MAIN_LATEST}"
    echo "  建议 git rebase origin/main，rebase 后 pre-commit 会自动重新编号"
    exit 1
  fi

  echo "flyway-versions: passed. checked_migrations=${#staged_versions[@]}"
  exit 0
fi

# ── pre-push 模式：检查 WORKING TREE 中所有 V* 文件 vs origin/main ──
# 关键改进：检查 WORKING TREE 中所有 V* 文件（不只是 git diff 显示的"新"文件），
# 这样可以捕获"多人并行创建了相同版本号"的场景（git diff 只显示与 main 的差异，
# 不显示在分支内部已存在但与 main HEAD 对比时没有差异的冲突文件）。
echo "flyway-versions: pre-push — fetching origin/main..."
git fetch origin main --prune 2>/dev/null || true

MAIN_VERSIONS=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
  sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

if [[ -z "$MAIN_VERSIONS" ]]; then
  echo "flyway-versions: could not fetch origin/main versions, skipping check."
  exit 0
fi

# 检查 WORKING TREE 中所有 V* 文件
WORKTREE_VERSIONS=$(find "${MIGRATION_DIR}" -name 'V*.sql' -type f 2>/dev/null | \
  sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

if [[ -z "$WORKTREE_VERSIONS" ]]; then
  echo "flyway-versions: no local V* migrations in worktree, skipping."
  exit 0
fi

echo "flyway-versions: checking worktree V* migrations against origin/main..."

CONFLICT_VERSIONS=""
while IFS= read -r wv; do
  while IFS= read -r mv; do
    if [[ "$wv" == "$mv" ]]; then
      echo "flyway-versions: CONFLICT — V${wv} 已被 origin/main 占用"
      CONFLICT_VERSIONS="${CONFLICT_VERSIONS}${wv}"$'\n'
    fi
  done <<< "$MAIN_VERSIONS"
done <<< "$WORKTREE_VERSIONS"

if [[ -n "$CONFLICT_VERSIONS" ]]; then
  MAIN_LATEST=$(echo "$MAIN_VERSIONS" | tail -1)
  echo ""
  echo "flyway-versions: pre-push 检测到 Flyway 版本冲突 — 强制 auto-fix"

  NEW_START=$((MAIN_LATEST + 1))
  while IFS= read -r cv; do
    [[ -z "$cv" ]] && continue
    old_file=$(find "${MIGRATION_DIR}" -name "V${cv}_*.sql" -type f 2>/dev/null | head -1)
    if [[ -n "$old_file" ]]; then
      new_name=$(basename "$old_file" | sed "s/^V${cv}_/V${NEW_START}_/")
      new_path="${MIGRATION_DIR}/${new_name}"
      echo "  mv ${old_file} → ${new_path}"
      git mv "$old_file" "$new_path"

      u_file="${ROLLBACK_DIR}/U${cv}_"* 2>/dev/null
      u_actual=$(ls $u_file 2>/dev/null | head -1 || echo "")
      if [[ -n "$u_actual" ]]; then
        u_new=$(basename "$u_actual" | sed "s/^U${cv}_/U${NEW_START}_/")
        u_new_path="${ROLLBACK_DIR}/${u_new}"
        echo "  (rollback) mv ${u_actual} → ${u_new_path}"
        git mv "$u_actual" "$u_new_path"
      fi
      NEW_START=$((NEW_START + 1))
    fi
  done <<< "$CONFLICT_VERSIONS"

  echo ""
  echo "flyway-versions: auto-fix 完成。请执行："
  echo "  git add ${MIGRATION_DIR}/ ${ROLLBACK_DIR}/"
  echo "  git commit --amend --no-edit"
  echo "  git push ..."
  exit 1  # 阻止 push，让用户 amend 后重试
fi

echo "flyway-versions: (pre-push) passed. worktree_versions=$(echo "$WORKTREE_VERSIONS" | wc -l | tr -d ' ')"
exit 0
