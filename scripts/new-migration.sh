#!/usr/bin/env bash
# Input: description argument for the new migration
# Output: V{next}__{name}.sql + U{next}__{name}.sql in migration-mysql dirs
# Pos: scripts/ - Flyway migration scaffold
# 维护声明: 若 Flyway 版本规则或迁移目录结构变化，同步更新本脚本。

# new-migration.sh — 新建 Flyway 迁移 + 回滚脚本脚手架
# 用法: bash scripts/new-migration.sh "add_ca_borrow_approval_workflow"
# 产出: V{next}__<name>.sql + db/rollback/migration-mysql/U{next}__<name>.sql

set -euo pipefail

DESCRIPTION="${1:-}"
if [ -z "$DESCRIPTION" ]; then
  echo "用法: bash scripts/new-migration.sh <description>"
  echo "示例: bash scripts/new-migration.sh add_ca_borrow_approval_workflow"
  exit 1
fi

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"
ROLLBACK_DIR="backend/src/main/resources/db/rollback/migration-mysql"

if [ ! -d "$MIGRATION_DIR" ]; then
  echo "错误: 迁移目录 $MIGRATION_DIR 不存在，请在项目根目录执行"
  exit 1
fi

# 找到下一个版本号
LAST=$(ls "$MIGRATION_DIR"/*.sql 2>/dev/null | sort -V | tail -1 | xargs -I{} basename {} | sed 's/^[A-Za-z]*//' | sed 's/__.*//')
if [ -z "$LAST" ]; then
  echo "错误: 无法从 $MIGRATION_DIR 找到已有迁移"
  exit 1
fi

NEXT=$((LAST + 1))
MIGRATION_FILE="$MIGRATION_DIR/V${NEXT}__${DESCRIPTION}.sql"
ROLLBACK_FILE="$ROLLBACK_DIR/U${NEXT}__${DESCRIPTION}.sql"

# 创建目录
mkdir -p "$ROLLBACK_DIR"

# 迁移脚本
cat > "$MIGRATION_FILE" << EOF
-- V${NEXT}: ${DESCRIPTION}
-- 说明: TODO

-- TODO: 在此写下迁移 SQL
EOF

# 回滚脚本 (含 source header)
cat > "$ROLLBACK_FILE" << EOF
-- Input: migration-mysql/V${NEXT}__${DESCRIPTION}.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.

-- U${NEXT}: 回滚 ${DESCRIPTION}
-- TODO: 在此写下回滚 SQL（与外键依赖逆序）
EOF

echo "✓ 已创建:"
echo "  迁移: $MIGRATION_FILE"
echo "  回滚: $ROLLBACK_FILE"
echo ""
echo "下一步: 1. 填写 SQL  2. mvn test -Dtest=FlywayRollbackScriptCoverageTest"
