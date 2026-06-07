#!/usr/bin/env bash
# Input: rehearsal environment variables and backend runtime launch settings
# Output: backend process started in background with pid/log files under STATE_DIR
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/release/rehearsal-env.sh"

cd "$BACKEND_DIR"
nohup env \
SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
DB_URL="$DB_URL" \
DB_PASSWORD="$DB_PASSWORD" \
DB_USERNAME="$DB_USERNAME" \
JWT_SECRET="$JWT_SECRET" \
REDIS_HOST="$REDIS_HOST" \
REDIS_PORT="$REDIS_PORT" \
CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS" \
PLATFORM_ENCRYPTION_KEY="$PLATFORM_ENCRYPTION_KEY" \
ADMIN_PASSWORD="$ADMIN_PASSWORD" \
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=${BACKEND_PORT} --spring.datasource.url=${DB_URL} --spring.datasource.username=${DB_USER} --spring.datasource.password=${DB_PASSWORD} --spring.data.redis.host=${REDIS_HOST} --spring.data.redis.port=${REDIS_PORT}" \
  > "$STATE_DIR/backend.log" 2>&1 < /dev/null &
echo $! > "$STATE_DIR/backend.pid"
