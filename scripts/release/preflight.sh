#!/usr/bin/env bash
# Input: release environment variables, MySQL 8.0 database selection, and local tool availability
# Output: preflight checks for MySQL 8.0 release workflows
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"

required_commands=(node npm java mvn)
optional_commands=(docker mysqldump mysql)
DB_USER="${DB_USER:-${DB_USERNAME:-}}"
DB_USERNAME="${DB_USERNAME:-$DB_USER}"
required_env=(SPRING_PROFILES_ACTIVE DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD JWT_SECRET REDIS_HOST CORS_ALLOWED_ORIGINS)
DB_ENGINE="${DB_ENGINE:-mysql}"

printf '==> Preflight checks\n'
printf 'Root: %s\n' "$ROOT_DIR"

for cmd in "${required_commands[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$cmd" >&2
    exit 1
  fi
done

for cmd in "${optional_commands[@]}"; do
  if command -v "$cmd" >/dev/null 2>&1; then
    printf 'Optional command available: %s\n' "$cmd"
  else
    printf 'Optional command missing: %s\n' "$cmd"
  fi
done

missing_env=0
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required env: %s\n' "$name" >&2
    missing_env=1
  fi
done

if [[ "$missing_env" -ne 0 ]]; then
  exit 1
fi

case "$DB_ENGINE" in
  mysql)
    if [[ ",$SPRING_PROFILES_ACTIVE," != *",mysql,"* ]]; then
      printf 'MySQL deployments must include mysql in SPRING_PROFILES_ACTIVE, for example: prod,mysql\n' >&2
      exit 1
    fi
    ;;
  *)
    printf 'Unsupported DB_ENGINE: %s. Use mysql.\n' "$DB_ENGINE" >&2
    exit 1
    ;;
esac

printf 'Node: %s\n' "$(node -v)"
printf 'NPM: %s\n' "$(npm -v)"
printf 'Java: %s\n' "$(java -version 2>&1 | head -n 1)"
printf 'Maven: %s\n' "$(mvn -v | head -n 1)"
printf 'Version: %s\n' "$(cat "$ROOT_DIR/VERSION")"

node "$ROOT_DIR/scripts/check-version-sync.mjs"

printf 'Database engine: %s\n' "$DB_ENGINE"
printf 'Database target: %s:%s/%s (user=%s)\n' "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER"
if [[ -n "${DB_URL:-}" ]]; then
  printf 'Database URL override: configured\n'
fi
printf 'Redis target: %s:%s\n' "$REDIS_HOST" "${REDIS_PORT:-6379}"
printf 'CORS origins: %s\n' "$CORS_ALLOWED_ORIGINS"

printf 'Preflight checks passed.\n'
