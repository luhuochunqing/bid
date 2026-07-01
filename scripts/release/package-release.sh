#!/usr/bin/env bash
# Input: repository source tree, installed toolchains, and release build environment variables
# Output: versioned release archive containing frontend assets, backend jar, and metadata
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
RELEASE_ID="${RELEASE_ID:-$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d-%H%M%S)}"
OUTPUT_DIR="${OUTPUT_DIR:-$ROOT_DIR/.release/$RELEASE_ID}"
ARCHIVE_PATH="${ARCHIVE_PATH:-$ROOT_DIR/.release/xiyu-bid-release-${RELEASE_ID}.tar.gz}"
# VITE_API_BASE_URL 解析：
#   - 显式设为空（VITE_API_BASE_URL=）→ 同源构建（API_BASE_URL=''，前端走相对路径，
#     与后端同 origin）。用于 172.16.x 内网直连 / Spring Boot 一体部署（前后端同源）。
#   - 完全未设 → fallback 到 PRODUCTION_API_BASE_URL / 默认 dev 地址（127.0.0.1:18089）。
#   - 显式设为 URL（含域名）→ 用该 URL（公网/WAF 入口，如 winbid-test.ehsy.com）。
# 关键：用 ${VITE_API_BASE_URL+x} 区分"未设"与"显式空"，否则 :- 会对显式空也 fallback，
# 导致同源部署永远拿不到空 baseURL（IP:8080 前端被迫调域名 API → 跨域 403）。
if [[ -z "${VITE_API_BASE_URL+x}" ]]; then
  API_BASE_URL="${PRODUCTION_API_BASE_URL:-http://127.0.0.1:18089}"
else
  API_BASE_URL="$VITE_API_BASE_URL"
fi

mkdir -p "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend" "$(dirname "$ARCHIVE_PATH")"

printf '==> Building frontend release assets\n'
cd "$ROOT_DIR"
# Sentry 前端错误追踪：通过环境变量注入 DSN（未配置时前端自动禁用）
# 部署时传入：VITE_SENTRY_DSN=https://xxx@sentry.io/xxx bash scripts/release/package-release.sh
VITE_API_MODE=api \
VITE_API_BASE_URL="$API_BASE_URL" \
VITE_SENTRY_DSN="${VITE_SENTRY_DSN:-}" \
VITE_SENTRY_ENVIRONMENT="${VITE_SENTRY_ENVIRONMENT:-production}" \
VITE_SENTRY_TRACES_SAMPLE_RATE="${VITE_SENTRY_TRACES_SAMPLE_RATE:-0.1}" \
npm run build:api

printf '\n==> 验证前端产物不含 dev API 地址（localhost/127.0.0.1:port）\n'
npm run --silent check:frontend-api-base

printf '\n==> Packaging backend jar\n'
cd "$BACKEND_DIR"
# 强制 clean：避免 target/ 残留旧迁移文件被打进 jar（2026-06-25 V1096 jar 内重复事故）
# 教训：mvn package 增量编译不会清理已删除的 V*.sql，导致 jar 内出现两个 V1096
mvn clean -DskipTests package

JAR_PATH="$(find "$BACKEND_DIR/target" -maxdepth 1 -type f -name '*.jar' ! -name '*original*.jar' | sort | head -n 1)"
if [[ -z "${JAR_PATH:-}" ]]; then
  printf 'No backend jar produced under %s/target\n' "$BACKEND_DIR" >&2
  exit 1
fi

# 校验 jar 内 Flyway 迁移版本无重复（2026-06-25 V1096 事故）
# target 残留或并行开发撞号都可能导致 jar 内出现两个 V1096
printf '\n==> 验证 jar 内 Flyway 迁移版本无重复\n'
DUPLICATE_VERSIONS=$(unzip -l "$JAR_PATH" | grep "db/migration-mysql/V" | \
  sed 's|.*\(V[0-9]*\)__.*/|\1|' | sort | uniq -d || true)
if [[ -n "$DUPLICATE_VERSIONS" ]]; then
  printf '❌ jar 内存在重复的 Flyway 迁移版本：\n' >&2
  printf '%s\n' "$DUPLICATE_VERSIONS" | sed 's/^/  /' >&2
  printf '\n修复方案：\n' >&2
  printf '  1. 确认 backend/src/main/resources/db/migration-mysql/ 无重复版本号\n' >&2
  printf '  2. 重新执行 rm -rf backend/target && mvn clean package\n' >&2
  printf '  3. 如仍重复，检查 .agent-locks/ 是否有并行开发撞号\n' >&2
  exit 1
fi
printf '✅ jar 内 Flyway 迁移版本无重复\n'

rm -rf "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend"
mkdir -p "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend"
cp -R "$ROOT_DIR/dist/." "$OUTPUT_DIR/frontend/"
cp "$JAR_PATH" "$OUTPUT_DIR/backend/app.jar"

cat > "$OUTPUT_DIR/release-metadata.json" <<EOF
{
  "releaseId": "$RELEASE_ID",
  "apiBaseUrl": "$API_BASE_URL",
  "jarName": "$(basename "$JAR_PATH")",
  "builtAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "sentryEnabled": $([ -n "${VITE_SENTRY_DSN:-}" ] && echo 'true' || echo 'false')
}
EOF

tar -C "$OUTPUT_DIR" -czf "$ARCHIVE_PATH" .

printf '\nRelease directory: %s\n' "$OUTPUT_DIR"
printf 'Release archive: %s\n' "$ARCHIVE_PATH"
