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
# OBS 大文件直传：默认 true 启用浏览器直传 OBS（后端须同时配好 XIYU_OBS_*）
# 第 8 次生产部署漏传 VITE_OBS_ENABLED=true 导致 OBS 直传失效事故的根治修复：
# 默认值从 false 改为 true，不传也启用；如需关闭须显式传 VITE_OBS_ENABLED=false
VITE_API_MODE=api \
VITE_API_BASE_URL="$API_BASE_URL" \
VITE_OBS_ENABLED="${VITE_OBS_ENABLED:-true}" \
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

# OBS 直传启用校验（第 84 次部署漏传 VITE_OBS_ENABLED=true 事故的硬门禁）
# 当 VITE_OBS_ENABLED=true 时，Detail chunk 中 .upload( 调用数应 >=2；
# 若为 0 说明 OBS 直传逻辑被 tree-shake，打包参数与产物不一致，立即中止。
# 第 8 次生产部署事故根治：默认值已改为 true，此处默认值同步改为 true。
if [[ "${VITE_OBS_ENABLED:-true}" == "true" ]]; then
  printf '\n==> 验证 OBS 直传已启用（Detail chunk .upload( 调用数）\n'
  DETAIL_FILES=( "$OUTPUT_DIR/frontend/assets/Detail-"*.js )
  if [[ ! -e "${DETAIL_FILES[0]}" ]]; then
    printf '❌ 未找到 Detail-*.js chunk，无法校验 OBS 启用状态\n' >&2
    exit 1
  fi
  UPLOAD_COUNT=0
  for _f in "${DETAIL_FILES[@]}"; do
    _n=$(grep -o "\.upload(" "$_f" 2>/dev/null | wc -l | tr -d ' ')
    UPLOAD_COUNT=$((UPLOAD_COUNT + _n))
  done
  if [[ "$UPLOAD_COUNT" -lt 2 ]]; then
    printf '❌ OBS 直传未启用：Detail chunk .upload( 调用数=%d（期望 >=2）\n' "$UPLOAD_COUNT" >&2
    printf '   根因：VITE_OBS_ENABLED=true 已传入，但构建产物中 OBS 逻辑被 tree-shake\n' >&2
    printf '   排查：检查 src/composables/useObsUploadFallback.js 是否被正确引用\n' >&2
    printf '   修复：确认 useObsProjectDocumentUpload / useObsUploadFallback 未被误删\n' >&2
    exit 1
  fi
  printf '✅ OBS 直传已启用（Detail chunk .upload( 调用数=%d）\n' "$UPLOAD_COUNT"
else
  printf '\n⚠️  VITE_OBS_ENABLED=false（OBS 直传已显式关闭）\n' >&2
  printf '   生产环境不应关闭 OBS 直传，请确认这是有意为之\n' >&2
fi

cat > "$OUTPUT_DIR/release-metadata.json" <<EOF
{
  "releaseId": "$RELEASE_ID",
  "apiBaseUrl": "$API_BASE_URL",
  "jarName": "$(basename "$JAR_PATH")",
  "builtAt": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "sentryEnabled": $([ -n "${VITE_SENTRY_DSN:-}" ] && echo 'true' || echo 'false'),
  "obsEnabled": $([ "${VITE_OBS_ENABLED:-true}" == "true" ] && echo 'true' || echo 'false')
}
EOF

tar -C "$OUTPUT_DIR" -czf "$ARCHIVE_PATH" .

printf '\nRelease directory: %s\n' "$OUTPUT_DIR"
printf 'Release archive: %s\n' "$ARCHIVE_PATH"
