#!/usr/bin/env bash
# Input: MySQL 8.0 connection environment variables and backup destination arguments
# Output: release backup archive files and backup metadata
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

DB_USER="${DB_USER:-${DB_USERNAME:-}}"
DB_USERNAME="${DB_USERNAME:-$DB_USER}"
required_env=(DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD)
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required env: %s\n' "$name" >&2
    exit 1
  fi
done

DB_ENGINE="${DB_ENGINE:-mysql}"

OUTPUT_DIR="${BACKUP_DIR:-$(pwd)/backups}"
mkdir -p "$OUTPUT_DIR"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

case "$DB_ENGINE" in
  mysql)
    if ! command -v mysqldump >/dev/null 2>&1 && [[ -z "${MYSQL_CONTAINER_NAME:-}" ]]; then
      printf 'mysqldump is unavailable. Set MYSQL_CONTAINER_NAME to use docker exec fallback.\n' >&2
      exit 1
    fi

    OUTPUT_FILE="$OUTPUT_DIR/${DB_NAME}-${TIMESTAMP}.sql"
    export MYSQL_PWD="$DB_PASSWORD"
    if command -v mysqldump >/dev/null 2>&1; then
      mysqldump \
        --host "$DB_HOST" \
        --port "$DB_PORT" \
        --user "$DB_USER" \
        --default-character-set=utf8mb4 \
        --no-tablespaces \
        --single-transaction \
        --hex-blob \
        --routines \
        --triggers \
        --set-gtid-purged=OFF \
        "$DB_NAME" > "$OUTPUT_FILE"
    else
      docker exec -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER_NAME" \
        mysqldump \
        --host localhost \
        --port 3306 \
        --user "$DB_USER" \
        --default-character-set=utf8mb4 \
        --no-tablespaces \
        --single-transaction \
        --hex-blob \
        --routines \
        --triggers \
        --set-gtid-purged=OFF \
        "$DB_NAME" > "$OUTPUT_FILE"
    fi
    unset MYSQL_PWD
    ;;
  *)
    printf 'Unsupported DB_ENGINE: %s. Use mysql.\n' "$DB_ENGINE" >&2
    exit 1
    ;;
esac

printf 'Backup created: %s\n' "$OUTPUT_FILE"
