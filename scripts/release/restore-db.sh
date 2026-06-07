#!/usr/bin/env bash
# Input: backup archive path, MySQL 8.0 connection environment variables, and confirmation flags
# Output: restored database state and restore verification side effects
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <backup-file>\n' "$0" >&2
  exit 1
fi

BACKUP_FILE="$1"
if [[ ! -f "$BACKUP_FILE" ]]; then
  printf 'Backup file not found: %s\n' "$BACKUP_FILE" >&2
  exit 1
fi

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

if [[ "${CONFIRM_RESTORE:-}" != "YES" ]]; then
  printf 'Restore is destructive. Re-run with CONFIRM_RESTORE=YES to continue.\n' >&2
  exit 1
fi

case "$DB_ENGINE" in
  mysql)
    if ! command -v mysql >/dev/null 2>&1 && [[ -z "${MYSQL_CONTAINER_NAME:-}" ]]; then
      printf 'mysql client is unavailable. Set MYSQL_CONTAINER_NAME to use docker exec fallback.\n' >&2
      exit 1
    fi

    export MYSQL_PWD="$DB_PASSWORD"
    if command -v mysql >/dev/null 2>&1; then
      mysql \
        --host "$DB_HOST" \
        --port "$DB_PORT" \
        --user "$DB_USER" \
        "$DB_NAME" < "$BACKUP_FILE"
    else
      docker exec -i -e MYSQL_PWD="$DB_PASSWORD" "$MYSQL_CONTAINER_NAME" \
        mysql \
        --host localhost \
        --port 3306 \
        --user "$DB_USER" \
        "$DB_NAME" < "$BACKUP_FILE"
    fi
    unset MYSQL_PWD
    ;;
  *)
    printf 'Unsupported DB_ENGINE: %s. Use mysql.\n' "$DB_ENGINE" >&2
    exit 1
    ;;
esac

printf 'Restore completed from: %s\n' "$BACKUP_FILE"
