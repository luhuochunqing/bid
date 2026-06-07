#!/bin/bash
# Input: 脚本参数、当前 worktree DB 信息、JPA 实体
# Output: 验证结果（成功或 Hibernate 映射不匹配错误）
# Pos: 被 dev-services / package.json / CI 调用，作为 schema 门禁
# 维护声明: 维护者按项目SOP；变更需同步 notes + dev-flyway-repair + CI

# 本地 JPA Schema 验证：检查实体映射是否与数据库一致
# 用法: ./scripts/validate-schema.sh
# 需要正在运行的后端数据库

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== JPA Schema Validation ==="
echo ""

cd "$PROJECT_DIR/backend"

# 使用 dev,mysql profile + ddl-auto=validate 启动 Spring Boot
# 如果实体与数据库不匹配，应用会在启动阶段失败并报告缺失/类型错误的列
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev,mysql \
  -Dspring-boot.run.arguments="--spring.jpa.hibernate.ddl-auto=validate --server.port=0" \
  -DskipTests \
  2>&1 | grep -E "Started XiyuBid|SchemaManagementException|Schema-validation|Error creating bean.*entityManagerFactory" | head -10

# 检查是否成功启动
if mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev,mysql \
  -Dspring-boot.run.arguments="--spring.jpa.hibernate.ddl-auto=validate --server.port=0" \
  -DskipTests \
  2>&1 | grep -q "Started XiyuBidApplication"; then
  echo ""
  echo "✅ Schema validation PASSED — all entity mappings match the database."
else
  echo ""
  echo "❌ Schema validation FAILED — see above for mismatched columns."
  echo "   Fix: add a Flyway migration to align the DB schema with entity changes."
  exit 1
fi
