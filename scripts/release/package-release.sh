#!/usr/bin/env bash
# Input: repository source tree, installed toolchains, and release build environment variables
# Output: versioned release archive containing frontend assets, backend jar, and metadata;
#         then prunes .release/ to keep only the newest $RELEASE_KEEP_COUNT packages
#         (2026-09-26: auto-cleanup added — old release tarballs had piled up to 17G)
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
# 容器测试门禁（2026-08-12 §111 事故）：新增被注入类漏加 @Service 会导致 Spring crash-loop，
# 纯 Mockito 单测 + 本脚本的 -DskipTests 打包都测不出来。打包前强制跑一次能孵化 Spring
# 全量上下文 + Testcontainers 的 FlywayMysqlContainerTest，验证 Bean 装配无误再打包。
# 逃生阀：XIYU_SKIP_CONTAINER_TEST=true 可显式跳过（打印警告，仅限明确知道无需验证的场景）。
if [[ "${XIYU_SKIP_CONTAINER_TEST:-false}" == "true" ]]; then
  printf '⚠️  已显式跳过容器测试（XIYU_SKIP_CONTAINER_TEST=true）——未验证 Spring 装配，请确认这是有意为之\n' >&2
else
  printf '\n==> 容器测试门禁：验证 Spring 全量上下文 + Flyway（防止漏加 @Service 导致 crash-loop）\n'
  if ! CONTAINER_TEST_LOG=$(mvn -Dtest=FlywayMysqlContainerTest -DfailIfNoTests=true test 2>&1); then
    printf '❌ 容器测试失败（FlywayMysqlContainerTest）——Spring 上下文未能完整装配\n' >&2
    printf '   常见根因：新增被注入类漏加 @Service/@Component 注解\n' >&2
    printf '%s\n' "$CONTAINER_TEST_LOG" | tail -40 >&2
    exit 1
  fi
  if grep -qE "Tests run:.*Skipped: [1-9]" <<<"$CONTAINER_TEST_LOG"; then
    printf '❌ 容器测试有 Skipped——Docker 不可用或测试配置错误，测试跳过 ≠ 测试通过\n' >&2
    grep -E "Tests run:" <<<"$CONTAINER_TEST_LOG" >&2 || true
    exit 1
  fi
  printf '✅ 容器测试通过（FlywayMysqlContainerTest，Spring 全量上下文装配正常）\n'
fi
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

rm -rf "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend" "$OUTPUT_DIR/sidecar"
mkdir -p "$OUTPUT_DIR/frontend" "$OUTPUT_DIR/backend" "$OUTPUT_DIR/sidecar"
cp -R "$ROOT_DIR/dist/." "$OUTPUT_DIR/frontend/"
cp "$JAR_PATH" "$OUTPUT_DIR/backend/app.jar"

printf '\n==> Packaging document-converter-sidecar\n'
# 必需文件门禁：缺任一文件说明 sidecar 不完整，打包即失败（防 /extract-tables 缺端点静默上线）
for _f in app.py table_extractor.py converter.py image_extractor.py requirements.txt Dockerfile.cn; do
  if [[ ! -f "$ROOT_DIR/document-converter-sidecar/$_f" ]]; then
    printf '❌ sidecar 缺少必需文件：document-converter-sidecar/%s\n' "$_f" >&2
    exit 1
  fi
done
# 表格契约版本指纹：与 Java SidecarTableExtractor.EXPECTED_SCHEMA_VERSION=1.0
# / DocumentArtifact.SUPPORTED_SCHEMA_VERSION=2.0 对齐，写入 release-metadata 供部署比对
SIDECAR_SCHEMA_VERSION="$(grep -E '^SCHEMA_VERSION' "$ROOT_DIR/document-converter-sidecar/table_extractor.py" | cut -d'"' -f2 | tr -d "'" || true)"
if [[ ! "$SIDECAR_SCHEMA_VERSION" =~ ^[0-9]+\.[0-9]+$ ]]; then
  printf '❌ 无法从 table_extractor.py 解析 SCHEMA_VERSION（得到 "%s"）\n' "$SIDECAR_SCHEMA_VERSION" >&2
  exit 1
fi
COPYFILE_DISABLE=1 tar -C "$ROOT_DIR/document-converter-sidecar" \
  --exclude='__pycache__' \
  --exclude='.pytest_cache' \
  --exclude='.venv' \
  --exclude='.runtime' \
  --exclude='.DS_Store' \
  --exclude='tests' \
  --exclude='._*' \
  --exclude='*.pyc' \
  --exclude='.backup' \
  -czf "$OUTPUT_DIR/sidecar/sidecar.tar.gz" .
SIDECAR_SHA256="$(shasum -a 256 "$OUTPUT_DIR/sidecar/sidecar.tar.gz" 2>/dev/null | cut -d' ' -f1 || sha256sum "$OUTPUT_DIR/sidecar/sidecar.tar.gz" | cut -d' ' -f1)"
printf '✅ sidecar 打包完成（SHA-256: %s，schemaVersion=%s）\n' "$SIDECAR_SHA256" "$SIDECAR_SCHEMA_VERSION"

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
    _n=$(grep -o "\.upload(" "$_f" 2>/dev/null | wc -l | tr -d ' ' || true)
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
  "sidecarSha256": "$SIDECAR_SHA256",
  "sidecarSchemaVersion": "$SIDECAR_SCHEMA_VERSION",
  "sentryEnabled": $([ -n "${VITE_SENTRY_DSN:-}" ] && echo 'true' || echo 'false'),
  "obsEnabled": $([ "${VITE_OBS_ENABLED:-true}" == "true" ] && echo 'true' || echo 'false')
}
EOF

tar -C "$OUTPUT_DIR" -czf "$ARCHIVE_PATH" .

printf '\nRelease directory: %s\n' "$OUTPUT_DIR"
printf 'Release archive: %s\n' "$ARCHIVE_PATH"

# 发布包保留策略（2026-09-26）：历史发布包曾在 .release/ 下堆积 48 个、占满约 17G 磁盘。
# 每次打包完成后自动清理旧发布包，只保留最近 $RELEASE_KEEP_COUNT 个
# （tar.gz 及其同名解包目录一起删）。保留数量可用环境变量覆盖：
#   RELEASE_KEEP_COUNT=5 bash scripts/release/package-release.sh
RELEASE_KEEP_COUNT="${RELEASE_KEEP_COUNT:-3}"
printf '\n==> 清理旧发布包（仅保留最近 %d 个）\n' "$RELEASE_KEEP_COUNT"
_pruned_count=0
while IFS= read -r _old_archive; do
  _old_id="${_old_archive#xiyu-bid-release-}"
  _old_id="${_old_id%.tar.gz}"
  rm -f "$ROOT_DIR/.release/$_old_archive"
  # 只删与该发布包同名的解包目录，避免误删 .release/ 下其他内容
  if [[ -d "$ROOT_DIR/.release/$_old_id" ]]; then
    rm -rf "$ROOT_DIR/.release/$_old_id"
  fi
  _pruned_count=$((_pruned_count + 1))
  printf '  已清理旧发布包：%s\n' "$_old_archive"
done < <(cd "$ROOT_DIR/.release" && ls -t xiyu-bid-release-*.tar.gz 2>/dev/null | tail -n +"$((RELEASE_KEEP_COUNT + 1))")
printf '旧发布包清理完成，共清理 %d 个\n' "$_pruned_count"
