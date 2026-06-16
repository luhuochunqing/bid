#!/usr/bin/env bash
# Input: git ref (default HEAD), migration directory
# Output: schema table conflicts between current branch and other open branches on origin
# Pos: scripts/ — Schema 语义冲突检测：跨分支检查迁移文件是否改同一张表
# 维护声明: 本脚本被 pre-push-gate.sh 调用；新增迁移目录时请同步 MIGRATION_DIR
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -z "$ROOT_DIR" ]]; then exit 0; fi
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"

# ── 从迁移文件名中提取表名 ──
#   V1063__project_add_review_status.sql  → project
#   V146__brand_authorization_library.sql → brand
#   V1008__ca_certificate_management.sql  → ca
extract_table() {
  local filename="$1"
  local rest="${filename#V[0-9]*__}"
  local table_part="${rest%%_*}"
  echo "$table_part"
}

# ── 获取某个 ref 相对于 origin/main「新增」的迁移文件表名 ──
#   只关心这个分支新加的迁移，不关心 origin/main 已有的
get_new_tables() {
  local ref="$1"
  # 相对于 origin/main 新增的文件
  git diff --name-only --diff-filter=A origin/main..."$ref" -- "$MIGRATION_DIR/" 2>/dev/null | \
    grep -E "/V[0-9]+_" | \
    while read -r filepath; do
      extract_table "$(basename "$filepath")"
    done | sort -u
}

# ── 主逻辑 ──
SCHEMA_CONFLICT=false

# 确保有最新 origin/main（全量 fetch，不用 --depth，避免维持 shallow 边界）
git fetch origin main 2>/dev/null || true

# 收集本地分支新加的迁移文件表名
echo "check-schema-conflicts: 分析本地分支新增迁移文件（vs origin/main）..."
LOCAL_NEW_TABLES=$(get_new_tables "HEAD")

if [[ -z "$LOCAL_NEW_TABLES" ]]; then
  echo "check-schema-conflicts: 本地分支无新增迁移文件，跳过"
  exit 0
fi

echo "  新增表: $(echo "$LOCAL_NEW_TABLES" | tr '\n' ' ')"

# 获取当前分支名，跳过自身
CURRENT_BRANCH=$(git symbolic-ref --short HEAD 2>/dev/null || echo "")

# 获取 origin 上所有未合入 main 的分支
echo ""
echo "check-schema-conflicts: 扫描其他分支是否也新增了同一张表的迁移..."
OTHER_BRANCHES=$(git branch -r --no-merged origin/main 2>/dev/null | \
  grep -v 'origin/main' | \
  grep -v 'origin/HEAD' | \
  grep -v 'origin/agent/.*-init' | \
  sed 's/^[[:space:]]*//' || true)

if [[ -z "$OTHER_BRANCHES" ]]; then
  echo "check-schema-conflicts: 没有未合入的 origin 分支，跳过"
  exit 0
fi

for ref in $OTHER_BRANCHES; do
  # 跳过自身
  [[ "origin/$CURRENT_BRANCH" == "$ref" ]] && continue

  # 获取这个分支新增的迁移文件表名
  REMOTE_NEW_TABLES=$(get_new_tables "$ref")

  if [[ -z "$REMOTE_NEW_TABLES" ]]; then
    continue
  fi

  # 逐表对比
  while IFS= read -r remote_table; do
    [[ -z "$remote_table" ]] && continue
    while IFS= read -r local_table; do
      [[ -z "$local_table" ]] && continue
      if [[ "$remote_table" == "$local_table" ]]; then
        local remote_file
        remote_file=$(git diff --name-only --diff-filter=A origin/main..."$ref" -- "$MIGRATION_DIR/" 2>/dev/null | \
          grep "/V[0-9]*_${remote_table}_" | head -1 || echo "?")
        local local_file
        local_file=$(git diff --name-only --diff-filter=A origin/main...HEAD -- "$MIGRATION_DIR/" 2>/dev/null | \
          grep "/V[0-9]*_${local_table}_" | head -1 || echo "?")

        echo ""
        echo "⚠  Schema 冲突检测: 表 '${remote_table}' 被多个分支同时新增迁移！"
        echo "   本地 ($CURRENT_BRANCH):  $(basename "$local_file" 2>/dev/null)"
        echo "   远端 (${ref}):           $(basename "$remote_file" 2>/dev/null)"
        SCHEMA_CONFLICT=true
      fi
    done <<< "$LOCAL_NEW_TABLES"
  done <<< "$REMOTE_NEW_TABLES"
done

if [[ "$SCHEMA_CONFLICT" == "true" ]]; then
  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  ⚠  Schema 语义冲突风险！                                  ║"
  echo "║                                                            ║"
  echo "║  以下表的迁移文件在多个分支中同时新增。                     ║"
  echo "║  合并时可能产生冲突，请：                                  ║"
  echo "║  1. 在 PR 描述中注明冲突的表                               ║"
  echo "║  2. 通知其他分支作者协调合并顺序                           ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""
  exit 0
fi

echo "check-schema-conflicts: ✅ 未发现跨分支 Schema 冲突"
exit 0
