#!/usr/bin/env bash
# Input: --env=dev|prod（默认 prod），--fail 模式，--jar-path=<path>
# Output: 检测源码/JAR 迁移文件与生产 DB flyway_schema_history 的一致性
# Pos: scripts/ — Flyway DB vs Source 同步性治理
#
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 用途：
#   对比生产 DB 的 flyway_schema_history 与 JAR/源码中的迁移文件，
#   检测两类不一致：
#     (1) DB 已 applied 但 JAR 缺失 → 危险！部署会触发 Flyway validate 失败
#     (2) JAR 存在但 DB 未 applied → 正常（待下次部署执行）
#
# 两种运行模式：
#   本地开发：对比 DB vs 本地文件系统（git repo 内）
#   远程部署：对比 DB vs 服务器上的 JAR 文件（/opt/xiyu-bid/shared/backend/app.jar）
#
# 触发场景：
#   - pre-push gate 中作为部署前检查的一部分
#   - CI/CD 部署前检查
#   - 手动部署前的预防性检查
#
# 用法:
#   bash scripts/check-flyway-db-source-sync.sh                        # 本地文件系统 vs DB
#   FLYWAY_CHECK_ENV=prod bash scripts/check-flyway-db-source-sync.sh # prod DB vs 本地文件系统
#   FLYWAY_CHECK_FAIL=1 ...                                           # fail 模式（阻断）
#   --jar-path=/opt/xiyu-bid/shared/backend/app.jar                   # 指定 JAR 路径（远程模式）

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo "")"
if [[ -n "$ROOT_DIR" ]]; then
  cd "$ROOT_DIR"
fi

ENV="${FLYWAY_CHECK_ENV:-local}"
FAIL_MODE="${FLYWAY_CHECK_FAIL:-0}"
JAR_PATH="${FLYWAY_CHECK_JAR:-}"
MIGRATION_DIR="${ROOT_DIR:-.}/backend/src/main/resources/db/migration-mysql"

# ── 参数解析 ────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --env=dev) ENV="dev" ;;
    --env=prod) ENV="prod" ;;
    --env=local) ENV="local" ;;
    --fail) FAIL_MODE=1 ;;
    --jar-path=*) JAR_PATH="${arg#--jar-path=}" ;;
  esac
done

echo "=== Flyway DB vs JAR/Source 一致性检查 ==="
echo ""

# ── 加载 DB 环境变量 ────────────────────────────────────
load_db_env() {
  if [[ "$ENV" == "prod" ]]; then
    ENV_FILE="/etc/xiyu-bid/backend.env"
  elif [[ "$ENV" == "dev" ]]; then
    ENV_FILE="${ROOT_DIR:-.}/.env.mysql"
  else
    # local 模式：尝试从 .env 或 backend/.env.local 读取
    ENV_FILE="${ROOT_DIR:-.}/.env.mysql"
    [[ ! -f "$ENV_FILE" ]] && ENV_FILE="${ROOT_DIR:-.}/backend/.env.local"
  fi

  if [[ -f "$ENV_FILE" ]]; then
    set -a
    source "$ENV_FILE"
    set +a
    echo "加载环境: $ENV_FILE"
    return 0
  else
    echo "无法找到环境文件: $ENV_FILE"
    return 1
  fi
}

# ── 获取 DB 中的 flyway 版本列表 ─────────────────────────
get_db_versions() {
  local sql
  sql="SELECT version FROM flyway_schema_history WHERE type='SQL' AND success=1 ORDER BY installed_rank"

  MYSQL_PWD="${DB_PASSWORD}" mysql -h "${DB_HOST}" \
    -P "${DB_PORT:-3306}" \
    -u "${DB_USERNAME:-${DB_USER}}" \
    "${DB_NAME}" \
    -N -e "$sql" 2>/dev/null
}

# ── 获取 JAR 中的迁移版本列表 ────────────────────────────
get_jar_versions() {
  local jar="$1"
  if [[ -f "$jar" ]]; then
    unzip -l "$jar" 2>/dev/null | \
      grep "BOOT-INF/classes/db/migration-mysql/V" | \
      awk '{print $4}' | \
      xargs -I{} basename {} | \
      grep -oE '^V[0-9]+' | \
      sed 's/^V//' | \
      sort -n | uniq
  else
    echo ""
  fi
}

# ── 获取文件系统迁移版本列表 ─────────────────────────────
get_filesystem_versions() {
  if [[ -d "$MIGRATION_DIR" ]]; then
    find "$MIGRATION_DIR" -name 'V*.sql' -type f 2>/dev/null | \
      sed -n 's/.*\/V\([0-9]\+\).*/\1/p' | \
      sort -n | uniq
  else
    echo ""
  fi
}

# ── 查找 JAR 路径 ────────────────────────────────────────
find_jar() {
  if [[ -n "$JAR_PATH" ]] && [[ -f "$JAR_PATH" ]]; then
    echo "$JAR_PATH"
    return
  fi

  local jar=""
  if [[ "$ENV" == "prod" ]]; then
    jar="/opt/xiyu-bid/shared/backend/app.jar"
  else
    jar="${ROOT_DIR:-.}/backend/target/bid-platform-1.0.3.jar"
  fi

  if [[ -f "$jar" ]]; then
    echo "$jar"
  else
    echo ""
  fi
}

# ── 主检查逻辑 ──────────────────────────────────────────
run_check() {
  local db_versions
  local source_versions
  local mode

  # 获取源版本列表
  jar_path=$(find_jar)
  if [[ -n "$jar_path" ]]; then
    echo "使用 JAR 迁移列表: $jar_path"
    source_versions=$(get_jar_versions "$jar_path")
    mode="jar"
  elif [[ -d "$MIGRATION_DIR" ]]; then
    echo "使用文件系统迁移列表: $MIGRATION_DIR"
    source_versions=$(get_filesystem_versions)
    mode="filesystem"
  else
    echo "无法找到迁移文件（JAR 或文件系统目录都不存在），跳过检查。"
    exit 0
  fi

  source_version_count=$(echo "$source_versions" | grep -cE '^[0-9]+' || echo "0")
  echo "源码/JAR 迁移文件: ${source_version_count} 个"
  echo ""

  # 尝试加载 DB 环境（可选，跳过不影响源文件分析）
  db_versions=""
  db_version_count=0
  if load_db_env 2>/dev/null; then
    echo "DB: ${DB_HOST}:${DB_PORT:-3306}/${DB_NAME}"
    db_versions=$(get_db_versions)
    if [[ -n "$db_versions" ]]; then
      db_version_count=$(echo "$db_versions" | grep -cE '^[0-9]+' || echo "0")
      echo "DB flyway_schema_history: ${db_version_count} 个已执行迁移"
    else
      echo "DB: 无法读取 flyway_schema_history，跳过 DB 对比。"
    fi
  else
    echo "DB: 环境文件不可用，跳过 DB 对比。"
  fi
  echo ""

  # ── DB 对比（仅当能连接 DB 时执行）───
  if [[ -n "$db_versions" ]] && [[ -n "$source_versions" ]]; then

    # ── 检查 1: DB 已 applied 但源码缺失（危险！）───
    echo "── DB 已执行但 JAR/源码缺失的迁移 ──"
    local db_missing_from_source=0
    while IFS= read -r db_ver; do
      [[ -z "$db_ver" ]] && continue
      if ! echo "$source_versions" | grep -qx "$db_ver"; then
        echo -e "  ${RED}✗ V${db_ver} — DB 已执行但 JAR/源码中缺失${NC}"
        desc=$(MYSQL_PWD="${DB_PASSWORD}" mysql -h "${DB_HOST}" \
          -P "${DB_PORT:-3306}" \
          -u "${DB_USERNAME:-${DB_USER}}" \
          "${DB_NAME}" \
          -N -e "SELECT description FROM flyway_schema_history WHERE version='${db_ver}' LIMIT 1" 2>/dev/null)
        echo "    description: $desc"
        db_missing_from_source=$((db_missing_from_source + 1))
      fi
    done <<< "$db_versions"

    if [[ "$db_missing_from_source" -eq 0 ]]; then
      echo -e "  ${GREEN}✓ 无不一致${NC}"
    else
      echo ""
      echo -e "${RED}⚠ 发现 ${db_missing_from_source} 个 DB 已执行但 JAR/源码缺失的迁移！${NC}"
      echo "  这会导致部署时 Flyway validate 失败："
      echo "  'Detected applied migration not resolved locally: <version>'"
      echo ""
      echo "  修复方法：在源码中补全对应的迁移文件（内容与 DB 中执行的一致）。"
      echo "  参考: docs/release/deploy-report-2026-06-26-5th.md（V1100 案例）"
    fi
    echo ""

    # ── 检查 2: 源码存在但 DB 未 applied（正常/警告）───
    echo "── JAR/源码存在但 DB 未执行的迁移 ──"
    local source_not_in_db=0
    while IFS= read -r src_ver; do
      [[ -z "$src_ver" ]] && continue
      if ! echo "$db_versions" | grep -qx "$src_ver"; then
        if [[ "$mode" == "jar" ]]; then
          echo -e "  ${YELLOW}⚠ V${src_ver} — JAR 存在但 DB 未执行${NC}（正常，待下次部署）"
        else
          echo -e "  ${YELLOW}⚠ V${src_ver} — 源码存在但 DB 未执行${NC}（正常，待下次部署）"
        fi
        source_not_in_db=$((source_not_in_db + 1))
      fi
    done <<< "$source_versions"

    if [[ "$source_not_in_db" -eq 0 ]]; then
      echo -e "  ${GREEN}✓ 无待执行迁移${NC}"
    else
      echo ""
      echo -e "${YELLOW}⚠ 发现 ${source_not_in_db} 个待执行迁移（正常）${NC}"
    fi
    echo ""

    # ── 结果汇总 ──────────────────────────────────────────
    echo "── 汇总 ──"
    echo "  DB 迁移总数:   $db_version_count"
    echo "  源码/JAR 总数: $source_version_count"
    echo "  DB→源码 缺口: $db_missing_from_source"
    echo "  源码→DB 缺口: $source_not_in_db"
    echo ""

    if [[ "$db_missing_from_source" -gt 0 ]]; then
      echo -e "${RED}✗ 检查失败: $db_missing_from_source 个迁移在 DB 已执行但 JAR/源码缺失${NC}"
      if [[ "$FAIL_MODE" == "1" ]]; then
        exit 1
      fi
    else
      echo -e "${GREEN}✓ 检查通过: JAR/源码与 DB 完全一致${NC}"
    fi

  elif [[ -n "$source_versions" ]]; then
    # 无法连接 DB，仅报告源文件版本
    echo "── 仅源文件版本（无 DB 连接）──"
    echo "  源码/JAR 迁移文件: ${source_version_count} 个"
    latest_src=$(echo "$source_versions" | tail -1)
    echo "  最新版本: V${latest_src}"
    echo ""
    echo -e "${YELLOW}⚠ 无法连接 DB，无法执行一致性检查${NC}"
    echo "  （确保 DB 环境变量已配置，或使用 --env=prod 指定环境）"
  fi
}

run_check
