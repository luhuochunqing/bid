#!/bin/bash
# Input: worktree DB (via dev-env), Flyway migration files under db/migration-mysql, backend/target state after rebase/sync
# Output: repaired flyway_schema_history (clears failed/duplicate version markers), optional auto backend launch with --spring.flyway.enabled=false
# Pos: scripts/ - dev-only Flyway repair helper after code sync/rebase (prevents "more than one migration with version" and checksum drift)
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

# dev 专用：更新代码（rebase/sync）后快速修复本地 Flyway 状态
# 用法：
#   XIYU_DEV_CONFIRMED=1 bash scripts/dev-flyway-repair.sh          # repair + auto-launch backend with disable
#   XIYU_DEV_CONFIRMED=1 bash scripts/dev-flyway-repair.sh --repair-only  # repair only (used by dev-services for auto integration)
# 会自动用当前 worktree 的 DB_NAME（来自 dev-env）
# 集成：dev-services.sh start_backend 会自动调用 --repair-only + 启动时带 disable。

set -e

if [[ "${XIYU_DEV_CONFIRMED:-}" != "1" ]]; then
  echo "ERROR: must export XIYU_DEV_CONFIRMED=1 (dev-only guard)"
  exit 1
fi

source scripts/dev-env.sh

DB_NAME="${DB_NAME:-xiyu_bid_main}"
DB_USER="${DB_USERNAME:-xiyu_user}"
DB_PASS="${DB_PASSWORD:-XiyuDB!2026}"
DB_URL="jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"

# dev-env.sh sets ROOT_DIR, but guard against missing
ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

echo "=== dev-flyway-repair for worktree: ${DB_NAME} ==="

# Skip repair on normal restarts: only run when git HEAD changed or target/ is missing.
REPAIR_MARKER="$ROOT_DIR/.runtime/dev-services/.flyway-repair-head"
CURRENT_HEAD="$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo 'unknown')"
if [[ -d "$ROOT_DIR/backend/target" ]] && [[ -f "$REPAIR_MARKER" ]]; then
  LAST_HEAD="$(cat "$REPAIR_MARKER" 2>/dev/null || echo '')"
  if [[ "$CURRENT_HEAD" == "$LAST_HEAD" ]]; then
    echo "Repair already done for HEAD $CURRENT_HEAD (backend/target exists). Skipping rm -rf + flyway:repair."
    exit 0
  fi
fi

echo "Step 1: rm -rf backend/target (critical after rebase/sync to avoid stale target/classes with old Vxxxx names causing 'duplicate version' errors)"
rm -rf backend/target

echo "Step 2: Repairing Flyway history (checksums + failed states) ..."
cd backend
mvn org.flywaydb:flyway-maven-plugin:9.22.3:repair \
  -Dflyway.url="${DB_URL}" \
  -Dflyway.user="${DB_USER}" \
  -Dflyway.password="${DB_PASS}" \
  -Dflyway.locations=filesystem:src/main/resources/db/migration-mysql \
  -B -q

echo "Step 2b: Compile (needed for Flyway classpath) ..."
mvn compile -q -B

echo "Step 2c: Running Flyway migrate to ensure DB schema is up to date ..."
mvn org.flywaydb:flyway-maven-plugin:9.22.3:migrate   -Dflyway.url="${DB_URL}"   -Dflyway.user="${DB_USER}"   -Dflyway.password="${DB_PASS}"   -Dflyway.locations=filesystem:src/main/resources/db/migration-mysql   -B -q

echo "Repair + migrate done."
exit 0

if [[ "$1" == "--repair-only" ]]; then
  echo "Repair-only mode (called from dev-services). Skipping Step 1/compile/auto-launch."
  # Step 1 is skipped: dev-services.sh already compiled in pre-flight,
  # so target/classes is up to date and flyway:migrate needs it for classpath.
  cd backend
  mvn org.flywaydb:flyway-maven-plugin:9.22.3:repair     -Dflyway.url="${DB_URL}"     -Dflyway.user="${DB_USER}"     -Dflyway.password="${DB_PASS}"     -Dflyway.locations=filesystem:src/main/resources/db/migration-mysql     -B -q
  mvn org.flywaydb:flyway-maven-plugin:9.22.3:migrate     -Dflyway.url="${DB_URL}"     -Dflyway.user="${DB_USER}"     -Dflyway.password="${DB_PASS}"     -Dflyway.locations=filesystem:src/main/resources/db/migration-mysql     -B -q
  echo "Repair + migrate done (repair-only mode)."
  if [[ -n "$CURRENT_HEAD" ]] && [[ "$CURRENT_HEAD" != "unknown" ]]; then
    echo "$CURRENT_HEAD" > "$REPAIR_MARKER"
  fi
  echo "The caller (dev-services) will start backend (with disable if configured)."
  exit 0
fi

echo ""
echo "Step 3: Automatically starting backend with --spring.flyway.enabled=false (auto-added disable to bypass pre-existing old migrations like V74 in this DB state, while using the repaired history for recent changes). This makes 'start after update' automatic and reliable."
nohup bash -c '
  export JWT_SECRET="${JWT_SECRET:-xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min}"
  export DB_USERNAME="${DB_USERNAME:-xiyu_user}"
  export DB_PASSWORD="${DB_PASSWORD:-XiyuDB!2026}"
  export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://localhost:1314,http://127.0.0.1:1314}"
  SPRING_PROFILES_ACTIVE=dev,mysql mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080 --spring.flyway.enabled=false" --no-transfer-progress
' > /tmp/dev-flyway-repair-backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend auto-launched in background with disable (PID: $BACKEND_PID), log: /tmp/dev-flyway-repair-backend.log"
echo "Monitor: tail -f /tmp/dev-flyway-repair-backend.log"
echo "Status: curl http://127.0.0.1:18080/actuator/health"
echo "Stop: kill $BACKEND_PID"
echo ""
echo "Alternative (if you want full dev stack managed): XIYU_DEV_CONFIRMED=1 npm run dev:stable:start (will auto-run repair via dev-services)"
echo ""
echo "For full clean (dev data loss only):"
echo "  (cd backend && mvn org.flywaydb:flyway-maven-plugin:9.22.3:clean -Dflyway.url=... -Dflyway.user=... -Dflyway.password=... -Dflyway.locations=filesystem:src/main/resources/db/migration-mysql )"
echo ""
echo "To re-run repair+auto-start later: XIYU_DEV_CONFIRMED=1 bash $0"
