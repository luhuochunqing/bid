#!/usr/bin/env bash
# Input: production backend jar (with bundled Flyway), backend.env (DB credentials), migration-mysql source
# Output: runs Flyway validate / repair / info against the production DB without restarting the backend
# Pos: scripts/release/ — Flyway checksum 诊断与修复工具（生产侧）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 用法（在目标服务器上执行）：
#   bash scripts/release/flyway-repair-runner.sh validate   # 只读校验，看哪些版本 checksum mismatch / pending
#   bash scripts/release/flyway-repair-runner.sh info       # 只读查看 Flyway 状态（已执行/待执行）
#   bash scripts/release/flyway-repair-runner.sh repair     # 对齐 checksum（自动备份 flyway_schema_history 前置）
#
# 设计要点：
#   - 用服务器上当前运行的 jar（/opt/xiyu-bid/shared/backend/app.jar）自带的 Flyway，
#     保证算出的 checksum 与 jar 启动时的 Flyway 版本完全一致（跨 Flyway 版本算法可能不同）。
#   - 从 BOOT-INF/lib/ 提取 flyway-core / flyway-mysql / mysql-connector-j / gson / slf4j / logback。
#   - repair 前自动 mysqldump 备份 flyway_schema_history，失败可整表恢复。
#   - repair 后自动跑 validate 确认。
#
# 工程背景（请勿删除）：
# 2026-06-26 部署 53bbf8a34 时事故（详见 docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md §13.5）
# 问题根因：commit 407587394 重命名+改了已发布的 V1096，生产 flyway_schema_history 仍记录旧
#   checksum，新 jar 启动时报 9 个版本 checksum mismatch 拒绝启动。
#   当时靠手动从 jar 提取 Flyway + 写一次性 Runner 才完成 repair，耗时且易错。
# 本脚本把那次手动操作沉淀为可重复工具，下次遇到 checksum mismatch 可一键处置。
#
# 安全约束：
#   - validate / info 是只读，对生产无风险。
#   - repair 会改 flyway_schema_history（只改元数据 checksum/description，不动业务表），
#     已内置前置备份；repair 失败可用备份的 .sql 整表恢复。
#   - 绝不执行 flyway clean（生产禁止），本脚本不提供该动作。
set -euo pipefail

# ── 配置（可在环境变量覆盖）──
BACKEND_JAR="${BACKEND_JAR:-/opt/xiyu-bid/shared/backend/app.jar}"
BACKEND_ENV="${BACKEND_ENV:-/etc/xiyu-bid/backend.env}"
MIGRATION_SOURCE="${MIGRATION_SOURCE:-/tmp/migration-mysql}"   # 仓库的 migration-mysql 目录（需事先上传或 rsync）
APP_ROOT="${APP_ROOT:-/opt/xiyu-bid}"
# 优先用后端运行时的 JDK（与 jar 编译版本一致，避免 UnsupportedClassVersionError），
# 回退到 PATH 上的 java
if [[ -z "${JAVA_BIN:-}" ]]; then
  for candidate in /opt/xiyu-tools/jdk-21/bin/java /opt/xiyu-tools/jdk-17/bin/java java; do
    if command -v "$candidate" >/dev/null 2>&1 || [[ -x "$candidate" ]]; then
      JAVA_BIN="$candidate"
      break
    fi
  done
fi
# 同步 javac（从 java 同目录推导）
JAVAC_BIN="${JAVAC_BIN:-$(dirname "$JAVA_BIN")/javac}"
EXTRACT_DIR="${EXTRACT_DIR:-/tmp/flyway-repair-extract}"
RUNNER_SRC="${RUNNER_SRC:-/tmp/FlywayRepairRunner.java}"

ACTION="${1:-}"
if [[ "$ACTION" != "validate" && "$ACTION" != "repair" && "$ACTION" != "info" ]]; then
  echo "Usage: $0 <validate|repair|info>" >&2
  echo "  validate — 只读校验 checksum 一致性 + 列出 pending 迁移" >&2
  echo "  info     — 只读查看 Flyway 状态" >&2
  echo "  repair   — 对齐 checksum（自动备份 flyway_schema_history）" >&2
  exit 2
fi

# ── Step 1: 校验前置条件 ──
if [[ ! -f "$BACKEND_JAR" ]]; then
  echo "❌ 后端 jar 不存在: $BACKEND_JAR" >&2
  exit 1
fi
if [[ ! -f "$BACKEND_ENV" ]]; then
  echo "❌ backend.env 不存在: $BACKEND_ENV（需要 sudo 读取）" >&2
  exit 1
fi
if [[ ! -d "$MIGRATION_SOURCE" ]]; then
  echo "❌ 迁移源目录不存在: $MIGRATION_SOURCE" >&2
  echo "   请先从仓库上传：rsync -av backend/src/main/resources/db/migration-mysql/ <server>:$MIGRATION_SOURCE/" >&2
  exit 1
fi

# ── Step 2: 加载 DB 凭据（从 backend.env）──
echo "==> 加载 DB 凭据 from $BACKEND_ENV"
if [[ "$(id -u)" -eq 0 ]]; then
  # root：直接 source
  set -a; . "$BACKEND_ENV"; set +a
else
  # 非 root：用 sudo bash 子进程 source 并输出（凭据只在子进程内，不进历史）
  eval "$(sudo bash -c "set -a; . $BACKEND_ENV; set +a; \
    echo DB_HOST=\\\"\$DB_HOST\\\"; \
    echo DB_PORT=\\\"\${DB_PORT:-3306}\\\"; \
    echo DB_NAME=\\\"\$DB_NAME\\\"; \
    echo DB_USER=\\\"\${DB_USER:-\$DB_USERNAME}\\\"; \
    echo DB_PASSWORD=\\\"\$DB_PASSWORD\\\"")"
fi

if [[ -z "${DB_HOST:-}" || -z "${DB_NAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
  echo "❌ 无法从 $BACKEND_ENV 读取 DB 凭据" >&2
  exit 1
fi
DB_USER="${DB_USER:-${DB_USERNAME:-}}"
echo "   DB_HOST=$DB_HOST DB_NAME=$DB_NAME DB_USER=$DB_USER (password hidden)"

JDBC_URL="jdbc:mysql://${DB_HOST}:${DB_PORT:-3306}/${DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"

# ── Step 3: 从 jar 提取 Flyway + 依赖 ──
echo "==> 从 $BACKEND_JAR 提取 Flyway 依赖到 $EXTRACT_DIR"
rm -rf "$EXTRACT_DIR"
mkdir -p "$EXTRACT_DIR/BOOT-INF/lib"
# 找出需要的 jar（版本号随时间变，用 glob）
for pattern in flyway-core-*.jar flyway-mysql-*.jar mysql-connector-j-*.jar \
               gson-*.jar slf4j-api-*.jar logback-classic-*.jar logback-core-*.jar; do
  matched=$(unzip -l "$BACKEND_JAR" "BOOT-INF/lib/$pattern" 2>/dev/null | awk '/BOOT-INF\/lib\// {print $4}' | head -1)
  if [[ -n "$matched" ]]; then
    unzip -o -q "$BACKEND_JAR" "$matched" -d "$EXTRACT_DIR"
    echo "   + $(basename "$matched")"
  fi
done

# 确认 flyway-core 真的提取成功
# 注意：[[ -f path/*.jar ]] 对 glob 不可靠（glob 在 [[]] 内不展开），用 ls 复核
if ! ls "$EXTRACT_DIR/BOOT-INF/lib/flyway-core-"*.jar >/dev/null 2>&1; then
  echo "❌ flyway-core 未提取成功（jar 可能损坏或结构变化）" >&2
  exit 1
fi

# ── Step 4: 内联编译 FlywayRepairRunner ──
echo "==> 编译 FlywayRepairRunner"
FLYWAY_CP="$EXTRACT_DIR/BOOT-INF/lib/*"
cat > "$RUNNER_SRC" <<'JAVA'
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.RepairResult;

public class FlywayRepairRunner {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: FlywayRepairRunner <jdbcUrl> <user> <password> <sqlLocation> <action>");
            System.exit(2);
        }
        String url = args[0], user = args[1], pass = args[2], loc = args[3], action = args[4];
        System.out.println("URL: " + url);
        System.out.println("User: " + user);
        System.out.println("Location: " + loc);
        System.out.println("Action: " + action);
        System.out.println("---");

        Flyway flyway = Flyway.configure()
            .dataSource(url, user, pass)
            .locations("filesystem:" + loc)
            .baselineOnMigrate(false)
            .validateOnMigrate(true)
            .load();

        switch (action) {
            case "validate":
                try {
                    flyway.validate();
                    System.out.println("VALIDATE OK - all checksums match");
                } catch (FlywayException e) {
                    System.out.println("VALIDATE FAILED:");
                    System.out.println(e.getMessage());
                    System.exit(1);
                }
                break;
            case "info":
                org.flywaydb.core.api.output.InfoResult ir = flyway.info().getInfoResult();
                System.out.println("Database: " + ir.database + " | schema: " + ir.schemaName + " | schemaVersion: " + ir.schemaVersion);
                System.out.println("Migrations: " + (ir.migrations == null ? 0 : ir.migrations.size()));
                if (ir.migrations != null && !ir.migrations.isEmpty()) {
                    System.out.println("---");
                    System.out.printf("%-10s %-20s %-10s %-40s%n", "Version", "State", "Type", "Description");
                    boolean hasNonSuccess = false;
                    for (org.flywaydb.core.api.output.InfoOutput m : ir.migrations) {
                        String stateStr = String.valueOf(m.state);
                        // 只打印非 SUCCESS 的迁移（pending/failed/future/ignored）
                        // state 字符串可能是 "Success"（首字母大写），用 equalsIgnoreCase
                        if (!stateStr.equalsIgnoreCase("Success") && !stateStr.equals("null")) {
                            System.out.printf("%-10s %-20s %-10s %-40s%n",
                                m.version == null ? "(no-ver)" : m.version,
                                stateStr, m.type, m.description);
                            hasNonSuccess = true;
                        }
                    }
                    if (!hasNonSuccess) {
                        System.out.println("(所有迁移均为 SUCCESS，无 pending/failed/future)");
                    }
                }
                break;
            case "repair":
                RepairResult result = flyway.repair();
                System.out.println("Repair completed.");
                System.out.println("repairActions: " + result.repairActions);
                System.out.println("migrationsAligned: " + result.migrationsAligned);
                System.out.println("migrationsRemoved: " + result.migrationsRemoved);
                System.out.println("migrationsDeleted: " + result.migrationsDeleted);
                break;
            default:
                System.err.println("Unknown action: " + action);
                System.exit(2);
        }
    }
}
JAVA

RUNNER_CLASS_DIR="${RUNNER_CLASS_DIR:-/tmp}"
"$JAVAC_BIN" -encoding UTF-8 -cp "$FLYWAY_CP" -d "$RUNNER_CLASS_DIR" "$RUNNER_SRC"
RUNNER_CP="$RUNNER_CLASS_DIR:$FLYWAY_CP"

run_action() {
  "$JAVA_BIN" -cp "$RUNNER_CP" FlywayRepairRunner "$JDBC_URL" "$DB_USER" "$DB_PASSWORD" "$MIGRATION_SOURCE" "$1"
}

# ── Step 5: 执行动作 ──
if [[ "$ACTION" == "repair" ]]; then
  # repair 前置：备份 flyway_schema_history
  STAMP=$(date +%Y%m%d-%H%M%S)
  BACKUP_DIR="$APP_ROOT/backups/flyway-history"
  BACKUP_FILE="$BACKUP_DIR/flyway_schema_history-$STAMP.sql"
  echo "==> 备份 flyway_schema_history → $BACKUP_FILE"
  mkdir -p "$BACKUP_DIR"
  if [[ "$(id -u)" -eq 0 ]]; then
    mysqldump -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" flyway_schema_history > "$BACKUP_FILE" 2>/dev/null
  else
    sudo mysqldump -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" flyway_schema_history > "$BACKUP_FILE" 2>/dev/null
  fi
  ls -la "$BACKUP_FILE"
  echo ""
fi

echo "==> 执行 $ACTION"
run_action "$ACTION"
ACTION_EXIT=$?
echo ""

# ── Step 6: repair 后置 validate 确认 ──
if [[ "$ACTION" == "repair" && "$ACTION_EXIT" -eq 0 ]]; then
  echo "==> repair 后跑 validate 确认"
  if run_action validate; then
    echo ""
    echo "✅ repair + validate 成功。现在可以安全部署新 jar。"
  else
    echo ""
    echo "⚠️  repair 后 validate 仍失败（可能仍有 pending 新迁移，这是正常的——新迁移会由新 jar 启动时执行）。"
    echo "   只剩 'Detected resolved migration not applied to database' 是预期状态。"
  fi
  echo ""
  echo "如需回滚 repair，恢复 flyway_schema_history："
  echo "  mysql -h $DB_HOST -u $DB_USER -p \$DB_NAME < $BACKUP_FILE"
fi

exit "$ACTION_EXIT"
