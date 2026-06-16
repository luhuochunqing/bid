#!/usr/bin/env bash
# Input: staged Flyway migration files (.sql) containing V_next__ in name
# Output: renames V_next__*.sql to V${NEXT}__*.sql, auto-assigns version numbers
# Pos: scripts/ — Flyway migration version auto-assignment (pre-commit entry)
# 维护声明: 本脚本运行在 pre-commit 且 set -e 下，必须幂等（第二次运行不重复编号）。
#           不与 check-flyway-versions.sh 冲突，后者做纯检查，本脚本做自动修复。
#           支持回滚文件（U_next__*.sql）同步编号。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -z "$ROOT_DIR" ]]; then
  echo "assign-flyway-version: not in a git repo, skipping."
  exit 0
fi

cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
ROLLBACK_DIR="backend/src/main/resources/db/rollback/migration-mysql"

# ── 1. 查找所有带 _next_ 占位符的迁移文件（staged 和 unstaged 都查）
#     staged 的文件是即将提交的，unstaged 的是刚创建还没 add 的
#     pre-commit 时 staged 文件已经 add 了，所以只用查 staged
#     但为了开发体验，也查 unstaged 的（提醒用户先 git add）

next_files_staged=$(git diff --cached --name-only --diff-filter=ACR | \
  grep -E "^${MIGRATION_DIR}/V_next_" || true)

next_files_unstaged=$(git ls-files --others --exclude-standard | \
  grep -E "^${MIGRATION_DIR}/V_next_" || true)

if [[ -z "$next_files_staged" && -z "$next_files_unstaged" ]]; then
  echo "assign-flyway-version: no V_next_ files found, skipping."
  exit 0
fi

# ── 2. 获取 origin/main 的最新版本号 ──（全量 fetch，不用 --depth，避免维持 shallow 边界）
git fetch origin main 2>/dev/null || true

main_versions=$(git ls-tree -r --name-only FETCH_HEAD -- "${MIGRATION_DIR}/" 2>/dev/null | \
  sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

# 也查本地分支自己已有的 V* 文件（已有版本号且不在 main 上的）
local_versions=$(git ls-files "${MIGRATION_DIR}/" | \
  sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | sort -n || true)

# 合并取最大值
all_versions=$(echo -e "${main_versions}\n${local_versions}" | sort -n | uniq)

next_version=0
if [[ -n "$all_versions" ]]; then
  next_version=$(echo "$all_versions" | tail -1)
fi
next_version=$((next_version + 1))

echo "assign-flyway-version: latest origin/main version: V$((next_version - 1)), assigning from V${next_version}"

# ── 3. 自动编号 ──
# 先把所有文件收集起来排序，依次分配版本号
all_next_files=""
if [[ -n "$next_files_staged" ]]; then
  all_next_files+="
${next_files_staged}"
fi
if [[ -n "$next_files_unstaged" ]]; then
  all_next_files+="
${next_files_unstaged}"
fi

# 按文件名排序，确保分配顺序稳定
sorted_files=$(echo "$all_next_files" | grep -v '^$' | sort)

declare -a renamed_pairs=()
current=$next_version

while IFS= read -r file; do
  [[ -z "$file" ]] && continue

  dir=$(dirname "$file")
  basename=$(basename "$file")
  # V_next__描述.sql → V${current}__描述.sql
  new_name=$(echo "$basename" | sed "s/^V_next_/V${current}_/")
  new_path="${dir}/${new_name}"

  if [[ "$basename" == "$new_name" ]]; then
    echo "assign-flyway-version: WARNING: '$basename' doesn't match V_next_ pattern, skipping"
    continue
  fi

  if [[ -f "$new_path" ]]; then
    echo "assign-flyway-version: ERROR: target file already exists: $new_path"
    exit 1
  fi

  renamed_pairs+=("$file|$new_path")
  current=$((current + 1))
done <<< "$sorted_files"

# ── 4. 执行重命名 ──
for pair in "${renamed_pairs[@]}"; do
  old="${pair%%|*}"
  new="${pair#*|}"

  # git mv 处理 staged 的文件；普通 mv 处理 unstaged 的文件
  if git diff --cached --name-only -- "$old" | grep -q "$old" 2>/dev/null; then
    git mv "$old" "$new"
  else
    mv "$old" "$new"
  fi
  echo "assign-flyway-version: ${old} → ${new}"
done

# ── 5. 同步处理回滚文件（U_next_ → U{V}） ──
if [[ -d "$ROLLBACK_DIR" ]]; then
  for pair in "${renamed_pairs[@]}"; do
    old="${pair%%|*}"
    new="${pair#*|}"
    old_base=$(basename "$old")
    new_base=$(basename "$new")

    # V_next_xxx.sql → U_next_xxx.sql
    u_old_base=$(echo "$old_base" | sed 's/^V_/U_/' | sed 's/^V_next_/U_next_/')
    u_new_base=$(echo "$new_base" | sed "s/^V\([0-9]\+\)_/U\1_/")

    u_old="${ROLLBACK_DIR}/${u_old_base}"
    u_new="${ROLLBACK_DIR}/${u_new_base}"

    if [[ -f "$u_old" ]]; then
      if git diff --cached --name-only -- "$u_old" | grep -q "$u_old" 2>/dev/null; then
        git mv "$u_old" "$u_new"
      else
        mv "$u_old" "$u_new"
      fi
      echo "assign-flyway-version: (rollback) ${u_old} → ${u_new}"
    fi
  done
fi

echo "assign-flyway-version: assigned V${next_version} to V$((current - 1)) (${#renamed_pairs[@]} files)"
