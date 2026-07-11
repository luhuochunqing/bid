#!/bin/bash
# ============================================================
# 业绩数据部署脚本（支持测试 / 生产环境）
#
# 用法：
#   ./deploy.sh test    # 部署到测试环境
#   ./deploy.sh prod    # 部署到生产环境
#
# 前置条件：
#   1. 三个文件已上传到 /tmp/：
#      - /tmp/performance_data.sql
#      - /tmp/performance_attachments.tar.gz
#   2. 当前用户有 sudo 权限（重启 xiyu-bid-backend 服务）
#   3. /etc/xiyu-bid/backend.env 存在且包含 DB 连接信息
#
# 关键设计：
#   - DB 配置从 /etc/xiyu-bid/backend.env 读取，不硬编码
#   - 附件根目录自动检测（backend.env → 代码默认值）
#   - tar 包内顶层是 performance/，解压到 ATTACHMENT_ROOT 的父目录
#     解压后路径 = $ATTACHMENT_ROOT_PARENT/performance/{id}/{file}
#              = $ATTACHMENT_ROOT/{id}/{file}  ✓
#   - 测试环境：先清理旧数据再导入
#   - 生产环境：空表全新导入（如有数据会报主键冲突，脚本会检测）
# ============================================================
set -euo pipefail

# ── 颜色输出 ──────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
ok()   { echo -e "${GREEN}[✓]${NC} $1"; }
fail() { echo -e "${RED}[✗]${NC} $1"; exit 1; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
info() { echo -e "    $1"; }

# ── 环境选择 ──────────────────────────────────────────────────
ENV="${1:-}"
if [ "$ENV" != "test" ] && [ "$ENV" != "prod" ]; then
    echo "用法: $0 <test|prod>"
    echo "  test  — 测试环境（172.16.38.78）"
    echo "  prod  — 生产环境（172.16.10.149）"
    exit 1
fi

echo "============================================================"
echo "  业绩数据部署 — 环境: $ENV"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
echo ""

# ── 安全确认（生产环境二次确认）──────────────────────────────
if [ "$ENV" = "prod" ]; then
    echo -e "${RED}⚠️  即将部署到生产环境！${NC}"
    echo "  数据量: 766 条业绩记录, 3870 个附件, 附件包约 1GB"
    echo "  操作: 备份 → 导入 SQL → 解压附件 → 重启服务"
    echo ""
    read -p "确认部署到生产环境？输入 YES 继续: " CONFIRM
    [ "$CONFIRM" = "YES" ] || { echo "已取消"; exit 0; }
fi

# ── 文件检查 ──────────────────────────────────────────────────
SQL_FILE="/tmp/performance_data.sql"
TAR_FILE="/tmp/performance_attachments.tar.gz"

echo "── Step 1: 文件检查 ──"
[ -f "$SQL_FILE" ] || fail "SQL 文件不存在: $SQL_FILE"
[ -f "$TAR_FILE" ] || fail "附件包不存在: $TAR_FILE"
SQL_SIZE=$(du -h "$SQL_FILE" | cut -f1)
TAR_SIZE=$(du -h "$TAR_FILE" | cut -f1)
ok "SQL 文件: $SQL_FILE ($SQL_SIZE)"
ok "附件包: $TAR_FILE ($TAR_SIZE)"
echo ""

# ── 读取数据库配置 ────────────────────────────────────────────
echo "── Step 2: 读取数据库配置 ──"
ENV_FILE="/etc/xiyu-bid/backend.env"
[ -f "$ENV_FILE" ] || fail "找不到 $ENV_FILE，无法获取 DB 配置"

# source 方式读取（正确处理单引号密码，见 LIVE_SERVER_DEPLOYMENT_RUNBOOK.md:350）
set -a; . "$ENV_FILE"; set +a

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-xiyu_bid_main}"
DB_USER="${DB_USERNAME:-${DB_USER:-root}}"
[ -n "${DB_PASSWORD:-}" ] || fail "DB_PASSWORD 未设置 in $ENV_FILE"

ok "DB_HOST=$DB_HOST"
ok "DB_PORT=$DB_PORT"
ok "DB_NAME=$DB_NAME"
ok "DB_USER=$DB_USER"
echo ""

# ── 检测附件根目录 ────────────────────────────────────────────
echo "── Step 3: 检测附件根目录 ──"

# 优先从 backend.env 读取（如果配置了）
ATTACHMENT_ROOT="${PERFORMANCE_ATTACHMENT_ROOT:-}"

if [ -z "$ATTACHMENT_ROOT" ]; then
    # 检查 application.yml 是否配置了 performance.attachment.root
    APP_YML=""
    for f in /opt/xiyu-bid/shared/backend/application.yml \
             /opt/xiyu-bid/shared/backend/config/application.yml; do
        [ -f "$f" ] && APP_YML="$f" && break
    done

    if [ -n "$APP_YML" ]; then
        DETECTED=$(grep -A1 'performance:' "$APP_YML" 2>/dev/null \
                   | grep 'attachment-root\|attachment.root' \
                   | sed 's/.*: *//' | tr -d ' "' || true)
        [ -n "$DETECTED" ] && ATTACHMENT_ROOT="$DETECTED"
    fi
fi

# 回退到代码默认值（见 PerformanceAttachmentStorageAppService.java:43）
if [ -z "$ATTACHMENT_ROOT" ]; then
    ATTACHMENT_ROOT="/data/attachments/performance"
    warn "未检测到显式配置，使用代码默认值: $ATTACHMENT_ROOT"
    warn "如有不符请手动修改脚本后重新执行"
fi

ok "附件根目录: $ATTACHMENT_ROOT"
ATTACHMENT_PARENT=$(dirname "$ATTACHMENT_ROOT")
info "tar 将解压到父目录: $ATTACHMENT_PARENT"
echo ""

# ── 备份数据库 ────────────────────────────────────────────────
echo "── Step 4: 备份数据库 ──"
BACKUP_DIR="/opt/xiyu-bid/db-backups"
mkdir -p "$BACKUP_DIR"
BACKUP_FILE="$BACKUP_DIR/performance-${ENV}-$(date '+%Y%m%d%H%M%S').sql.gz"

MYSQL_PWD="$DB_PASSWORD" mysqldump \
    -h "$DB_HOST" -P "$DB_PORT" \
    -u "$DB_USER" \
    --single-transaction \
    "$DB_NAME" performance_record performance_attachment 2>/tmp/mysqldump.err \
    | gzip > "$BACKUP_FILE" || true

# 检查备份文件是否有效（至少 1KB）
BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
BACKUP_BYTES=$(stat -c%s "$BACKUP_FILE" 2>/dev/null || stat -f%z "$BACKUP_FILE" 2>/dev/null || echo 0)
if [ "$BACKUP_BYTES" -gt 1000 ]; then
    ok "备份完成: $BACKUP_FILE ($BACKUP_SIZE)"
else
    echo "    mysqldump stderr:"
    cat /tmp/mysqldump.err 2>/dev/null | head -5
    fail "备份失败，文件过小: $BACKUP_FILE ($BACKUP_SIZE)"
fi
echo ""

# ── 检查现有数据 ──────────────────────────────────────────────
echo "── Step 5: 检查现有数据 ──"
EXISTING_RECORDS=$(MYSQL_PWD="$DB_PASSWORD" mysql \
    -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 \
    "$DB_NAME" -sN \
    -e "SELECT COUNT(*) FROM performance_record;" 2>/dev/null || echo "TABLE_NOT_EXIST")

if [ "$EXISTING_RECORDS" = "TABLE_NOT_EXIST" ]; then
    fail "performance_record 表不存在，请确认 Flyway 迁移已执行"
fi

EXISTING_ATTACHMENTS=$(MYSQL_PWD="$DB_PASSWORD" mysql \
    -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 \
    "$DB_NAME" -sN \
    -e "SELECT COUNT(*) FROM performance_attachment;" 2>/dev/null)

info "现有业绩记录: $EXISTING_RECORDS 条"
info "现有附件记录: $EXISTING_ATTACHMENTS 条"

if [ "$EXISTING_RECORDS" -gt 0 ]; then
    if [ "$ENV" = "test" ]; then
        echo ""
        warn "测试环境有 $EXISTING_RECORDS 条旧数据，将执行清理"
        read -p "确认清理旧数据？输入 YES 继续: " CONFIRM_CLEAN
        [ "$CONFIRM_CLEAN" = "YES" ] || { echo "已取消"; exit 0; }

        MYSQL_PWD="$DB_PASSWORD" mysql \
            -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 \
            "$DB_NAME" \
            -e "SET FOREIGN_KEY_CHECKS=0;
                DELETE FROM performance_attachment;
                DELETE FROM performance_record;
                SET FOREIGN_KEY_CHECKS=1;"
        ok "旧数据已清理"
    else
        fail "生产环境 performance_record 非空 ($EXISTING_RECORDS 条)，预期空表。请先清理或确认数据状态"
    fi
else
    ok "表为空，可直接导入"
fi
echo ""

# ── 导入 SQL ──────────────────────────────────────────────────
echo "── Step 6: 导入 SQL 数据 ──"
echo "    导入中... (766 条业绩 + 3870 个附件，预计 10-30 秒)"

# 关键：必须指定 --default-character-set=utf8mb4，否则 MySQL 客户端默认用 latin1
# 连接，UTF-8 中文会被 latin1 解释后存入数据库，导致乱码
MYSQL_PWD="$DB_PASSWORD" mysql \
    -h "$DB_HOST" -P "$DB_PORT" \
    -u "$DB_USER" \
    --default-character-set=utf8mb4 \
    "$DB_NAME" < "$SQL_FILE"

ok "SQL 导入完成"
echo ""

# ── 解压附件 ──────────────────────────────────────────────────
echo "── Step 7: 解压附件文件 ──"
echo "    附件包约 1GB，解压需要 1-3 分钟..."

# 关键：tar 包内顶层是 performance/，解压到 ATTACHMENT_ROOT 的父目录
# 解压后: $ATTACHMENT_PARENT/performance/{id}/{file} = $ATTACHMENT_ROOT/{id}/{file}
mkdir -p "$ATTACHMENT_PARENT"
tar -xzf "$TAR_FILE" -C "$ATTACHMENT_PARENT"
ok "附件解压完成"
echo ""

# ── 验证数据 ──────────────────────────────────────────────────
echo "── Step 8: 验证数据 ──"

RECORD_COUNT=$(MYSQL_PWD="$DB_PASSWORD" mysql \
    -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 \
    "$DB_NAME" -sN \
    -e "SELECT COUNT(*) FROM performance_record WHERE is_deleted=0;")
ATT_COUNT=$(MYSQL_PWD="$DB_PASSWORD" mysql \
    -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 \
    "$DB_NAME" -sN \
    -e "SELECT COUNT(*) FROM performance_attachment WHERE is_deleted=0;")
ATT_DIRS=$(find "$ATTACHMENT_ROOT" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | wc -l | tr -d ' ')

info "业绩记录数: $RECORD_COUNT (预期: 766)"
info "附件记录数: $ATT_COUNT (预期: 3870)"
info "附件目录数: $ATT_DIRS"

if [ "$RECORD_COUNT" -eq 766 ] && [ "$ATT_COUNT" -eq 3870 ]; then
    ok "数据量校验通过"
else
    warn "数据量不匹配！请检查"
fi

# 抽查附件文件是否存在
SAMPLE_ID=1
SAMPLE_FILE=$(MYSQL_PWD="$DB_PASSWORD" mysql \
    -h "$DB_HOST" -u "$DB_USER" --default-character-set=utf8mb4 \
    "$DB_NAME" -sN \
    -e "SELECT file_url FROM performance_attachment WHERE performance_id=1 LIMIT 1;" 2>/dev/null)

if [ -n "$SAMPLE_FILE" ]; then
    SAMPLE_PATH="${ATTACHMENT_ROOT}${SAMPLE_FILE}"
    if [ -f "$SAMPLE_PATH" ]; then
        ok "抽查附件存在: $SAMPLE_PATH"
    else
        fail "抽查附件不存在: $SAMPLE_PATH"
    fi
else
    warn "无 performance_id=1 的附件，跳过抽查"
fi
echo ""

# ── 重启后端服务 ──────────────────────────────────────────────
echo "── Step 9: 重启后端服务 ──"
SERVICE_NAME="xiyu-bid-backend"

if systemctl is-active "$SERVICE_NAME" >/dev/null 2>&1; then
    sudo systemctl restart "$SERVICE_NAME"
    ok "服务已重启: $SERVICE_NAME"
else
    warn "服务 $SERVICE_NAME 未运行，尝试启动..."
    sudo systemctl start "$SERVICE_NAME"
    ok "服务已启动: $SERVICE_NAME"
fi

# 等待健康检查
echo "    等待服务启动..."
HEALTH_OK=false
for i in $(seq 1 30); do
    sleep 2
    HEALTH=$(curl -sf --noproxy '*' \
        http://127.0.0.1:18080/actuator/health 2>/dev/null || echo "")
    if echo "$HEALTH" | grep -q '"status":"UP"'; then
        HEALTH_OK=true
        ok "健康检查通过 (${i}次尝试, $(( i * 2 ))秒)"
        break
    fi
    printf "."
done
echo ""

if [ "$HEALTH_OK" = "false" ]; then
    fail "健康检查失败，请检查服务状态: sudo systemctl status $SERVICE_NAME"
fi
echo ""

# ── 完成 ──────────────────────────────────────────────────────
echo "============================================================"
echo -e "${GREEN}  ✓ 业绩数据部署完成${NC}"
echo "============================================================"
echo ""
echo "部署摘要:"
info "环境: $ENV"
info "业绩记录: $RECORD_COUNT 条"
info "附件记录: $ATT_COUNT 条"
info "附件目录: $ATT_DIRS 个"
info "附件根目录: $ATTACHMENT_ROOT"
info "数据库备份: $BACKUP_FILE"
echo ""
echo "后续验证建议:"
echo "  1. 登录前端，访问 业绩库 页面，确认列表有 766 条记录"
echo "  2. 打开任意一条业绩详情，下载附件确认能正常预览/下载"
echo "  3. 检查后端日志有无 NoSuchFileException: sudo journalctl -u $SERVICE_NAME --since '5 min ago' | grep -i error"
echo ""
