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
AUTO_FIX=0

for arg in "$@"; do
  case "$arg" in
    --source=push) MODE="pre-push" ;;
    --source=pre-commit) MODE="pre-commit" ;;
    --fix) AUTO_FIX=1 ;;
    --staged) ;;  # 兼容旧调用方式
  esac
done

# ── Step 1：pre-commit 模式 — 先跑自动编号 ──
if [[ "$MODE" == "pre-commit" ]]; then
  # 先跑自动编号（处理 V_next_ 占位符）
  bash "$ROOT_DIR/scripts/assign-flyway-version.sh"

  # 然后检查冲突
  STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACMR | \
    grep "^${MIGRATION_DIR}/V" || true)

  if [[ -z "$STAGED_MIGRATIONS" ]]; then
    echo "flyway-versions: no staged Flyway migrations, skipping."
    exit 0
  fi
fi

# ── pre-push 模式：检查本地未推送的 V* 文件 vs origin/main ──
if [[ "$MODE" == "pre-push" ]]; then
  # 获取本地分支上未推送的 V* 文件（相对于 origin/main）
  LOCAL_VERSIONS=$(git diff --name-only origin/main..HEAD -- "${MIGRATION_DIR}/V" 2>/dev/null | \
    sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

  if [[ -z "$LOCAL_VERSIONS" ]]; then
    echo "flyway-versions: no new local migrations vs origin/main, skipping."
    exit 0
  fi

  echo "flyway-versions: checking ${LOCAL_VERSIONS} local migration(s) against origin/main..."

  # 获取 origin/main 的最新版本号（全量 fetch，不用 --depth，避免维持 shallow 边界）
  git fetch origin main 2>/dev/null || true
  MAIN_VERSIONS=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
    sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

  if [[ -z "$MAIN_VERSIONS" ]]; then
    echo "flyway-versions: could not fetch origin/main versions, skipping check."
    exit 0
  fi

  MAIN_LATEST=$(echo "$MAIN_VERSIONS" | tail -1)
  LOCAL_LATEST=$(echo "$LOCAL_VERSIONS" | tail -1)

  # 检查版本号冲突
  CONFLICTS=false
  while IFS= read -r mv; do
    while IFS= read -r lv; do
      if [[ "$mv" == "$lv" ]]; then
        echo "flyway-versions: CONFLICT — V${lv} 已被 origin/main 占用"
        CONFLICTS=true
      fi
    done <<< "$LOCAL_VERSIONS"
  done <<< "$MAIN_VERSIONS"

  if [[ "$CONFLICTS" == "true" ]]; then
    echo ""
    echo "flyway-versions: ⚠ 版本冲突，当前方案选择："

    if [[ "$AUTO_FIX" == "1" ]]; then
      echo "  --fix 模式：自动重编号本地迁移文件"
      NEW_START=$((MAIN_LATEST + 1))

      while IFS= read -r lv; do
        old_file=$(git diff --name-only origin/main..HEAD -- "${MIGRATION_DIR}/V${lv}_*" | head -1)
        if [[ -z "$old_file" ]]; then
          old_file=$(find "${MIGRATION_DIR}" -name "V${lv}_*" -type f | head -1)
        fi
        if [[ -n "$old_file" ]]; then
          new_name=$(echo "$(basename "$old_file")" | sed "s/^V${lv}_/V${NEW_START}_/")
          new_path="${MIGRATION_DIR}/${new_name}"
          echo "  mv ${old_file} → ${new_path}"
          git mv "$old_file" "$new_path"

          # 同步回滚文件
          u_file="${ROLLBACK_DIR}/U${lv}_*"
          u_actual=$(ls $u_file 2>/dev/null | head -1 || echo "")
          if [[ -n "$u_actual" ]]; then
            u_new=$(echo "$(basename "$u_actual")" | sed "s/^U${lv}_/U${NEW_START}_/")
            u_new_path="${ROLLBACK_DIR}/${u_new}"
            echo "  (rollback) mv ${u_actual} → ${u_new_path}"
            git mv "$u_actual" "$u_new_path"
          fi

          NEW_START=$((NEW_START + 1))
        fi
      done <<< "$LOCAL_VERSIONS"

      echo ""
      echo "flyway-versions: 自动重编号完成，请重新 add 后推送"
      echo "  git add ${MIGRATION_DIR}/ ${ROLLBACK_DIR}/"
      echo "  git commit --amend --no-edit"
      echo "  git push ..."
      exit 1  # 阻止 push，让用户重新 commit
    else
      echo "  方案 1：git rebase origin/main（推荐，rebase 后重新触发自动编号）"
      echo "  方案 2：bash scripts/check-flyway-versions.sh --source=push --fix（自动重编号）"
      echo ""
      echo "  origin/main 最新版本号：V${MAIN_LATEST}"
      echo "  本地使用的版本号："
      echo "$LOCAL_VERSIONS" | while read v; do echo "    V${v}"; done
      echo ""
      echo "  建议起始版本号：V$((MAIN_LATEST + 1))"
      exit 1
    fi
  fi

  echo "flyway-versions: (pre-push) passed. local_versions=$(echo "$LOCAL_VERSIONS" | wc -l | tr -d ' ')"
  exit 0
fi

# ── pre-commit 冲突检查（自动编号之后的兜底检查） ──
STAGED_MIGRATIONS=$(git diff --cached --name-only --diff-filter=ACMR | \
  grep "^${MIGRATION_DIR}/V" || true)
if [[ -z "$STAGED_MIGRATIONS" ]]; then
  echo "flyway-versions: no staged Flyway migrations, skipping."
  exit 0
fi

staged_versions=()
while IFS= read -r file; do
  basename=$(basename "$file")
  version=$(echo "$basename" | sed -n 's/^V\([0-9]\+\).*/\1/p')
  if [[ -n "$version" ]]; then
    staged_versions+=("$version")
    echo "flyway-versions: staged V${version} -> ${file}"
  fi
done <<< "$STAGED_MIGRATIONS"

if [[ "${#staged_versions[@]}" -eq 0 ]]; then
  echo "flyway-versions: no version numbers extracted, skipping."
  exit 0
fi

main_versions=""
if git fetch origin main 2>/dev/null; then
  main_versions=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
    sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)
fi

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
  echo "  建议运行 rebase 后重新提交"
  echo "    git rebase origin/main"
  echo "    # rebase 后 pre-commit 会自动重新编号"
  exit 1
fi

echo "flyway-versions: passed. checked_migrations=${#staged_versions[@]}"
exit 0
