#!/usr/bin/env bash
# Input: release id label; optional BACKEND_ENV_FILE (default /etc/xiyu-bid/backend.env), BACKUP_DIR, DB_BACKUP_MIN_BYTES, MYSQLDUMP_BIN
# Output: complete gzip-compressed MySQL dump file; nonzero exit with partial file removed when the dump is unusable
# Pos: scripts/release/ - Shared pre-deploy database backup（deploy-prod.sh / deploy-test.sh 共用）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 第 39 次生产部署（2026-11-07）事故重放防线（测试环境同款命令同样存在该缺陷）：
# 生产账号 ea_bid_pro 无 LOCK TABLES 权限（1044），旧内联命令 `mysqldump | gzip > file`
# 让 gzip 的成功状态吞掉 mysqldump 退出码，产出 371 字节"假备份"且部署继续。
# 本脚本契约：任一环节失败必须返回非零并删除半成品，由 remote-deploy.sh（set -e）中止部署。
set -uo pipefail

RELEASE_ID="${1:-}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/xiyu-bid/backend.env}"
BACKUP_DIR="${BACKUP_DIR:-/opt/xiyu-bid/db-backups}"
MIN_BYTES="${DB_BACKUP_MIN_BYTES:-1024}"
MYSQLDUMP_BIN="${MYSQLDUMP_BIN:-mysqldump}"
GZIP_BIN="${GZIP_BIN:-gzip}"

fail() {
  printf 'DB backup failed: %s\n' "$1" >&2
  exit 1
}

[[ -n "$RELEASE_ID" ]] || fail "usage: backup-db-pre-deploy.sh <release-id>"
[[ -r "$BACKEND_ENV_FILE" ]] || fail "backend env not readable: $BACKEND_ENV_FILE"

# 复用生产后端同一份 DB 凭据，不在本脚本硬编码账号密码。
# env 文件可能包含未导出变量或 $ 引用，source 阶段临时关闭 nounset。
set +u
# shellcheck disable=SC1090
source "$BACKEND_ENV_FILE" || fail "source $BACKEND_ENV_FILE"
set -u

DB_NAME="${DB_NAME:-xiyu_bid_main}"
[[ -n "${DB_PASSWORD:-}" ]] || fail "DB_PASSWORD missing after sourcing $BACKEND_ENV_FILE"

mkdir -p "$BACKUP_DIR" || fail "mkdir -p $BACKUP_DIR"
OUT="$BACKUP_DIR/winbid-${RELEASE_ID}-$(date +%Y%m%d%H%M%S).sql.gz"

# 失败即删除半成品：禁止假备份落盘（第 39 次部署教训）。
cleanup_partial() {
  local rc=$?
  if ((rc != 0)); then
    rm -f "$OUT"
    printf 'DB backup aborted (exit %d), partial file removed: %s\n' "$rc" "$OUT" >&2
  fi
}
trap cleanup_partial EXIT

# --single-transaction：生产账号无 LOCK TABLES 权限，且 InnoDB 快照一致性不弱于锁表；
# 该参数已在生产机 mysqldump 5.5.68-MariaDB 实测生成完整备份（6.7M，含 Dump completed）。
"$MYSQLDUMP_BIN" \
  --single-transaction \
  -h"${DB_HOST:-127.0.0.1}" \
  -P"${DB_PORT:-3306}" \
  -u"${DB_USER:-root}" \
  -p"${DB_PASSWORD}" \
  "$DB_NAME" |
  "$GZIP_BIN" >"$OUT" || fail "mysqldump pipeline（检查 LOCK TABLES 权限或 DB 连接）"

# gzip -t 只能证明压缩流完整：mysqldump 中途失败产生的空/截断 dump 同样能通过，
# 所以必须再校验 mysqldump 的完成标记。
"$GZIP_BIN" -t "$OUT" || fail "gzip integrity check"
LAST_LINE="$("$GZIP_BIN" -dc "$OUT" | tail -n 1)"
[[ "$LAST_LINE" == *"Dump completed"* ]] || fail "dump incomplete（缺少 'Dump completed' 尾部标记）"

BYTES="$(wc -c <"$OUT" | tr -d ' ')"
((BYTES >= MIN_BYTES)) || fail "dump too small：${BYTES} bytes < ${MIN_BYTES}"

printf 'DB backup completed: %s (%s bytes)\n' "$OUT" "$BYTES"
