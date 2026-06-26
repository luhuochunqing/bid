#!/usr/bin/env bash
# Input: uploaded release archive path, remote host deployment paths, and service restart configuration
# Output: activated release files, restarted backend service, and remote health verification
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

required_env=(RELEASE_ARCHIVE APP_ROOT FRONTEND_PUBLIC_DIR BACKEND_SERVICE_NAME HEALTHCHECK_URL)
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Missing required env: %s\n' "$name" >&2
    exit 1
  fi
done

if [[ ! -f "$RELEASE_ARCHIVE" ]]; then
  printf 'Release archive not found: %s\n' "$RELEASE_ARCHIVE" >&2
  exit 1
fi

RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d-%H%M%S)}"
RELEASES_DIR="${RELEASES_DIR:-$APP_ROOT/releases}"
BACKEND_RUNTIME_DIR="${BACKEND_RUNTIME_DIR:-$APP_ROOT/shared/backend}"
BACKEND_JAR_PATH="${BACKEND_JAR_PATH:-$BACKEND_RUNTIME_DIR/app.jar}"
DEPLOYED_RELEASE_RECORD="${DEPLOYED_RELEASE_RECORD:-$APP_ROOT/deployed-release.json}"
SYSTEMCTL_BIN="${SYSTEMCTL_BIN:-systemctl}"
SYSTEMCTL_SUDO="${SYSTEMCTL_SUDO:-false}"
POST_DEPLOY_COMMAND="${POST_DEPLOY_COMMAND:-}"
DB_BACKUP_COMMAND="${DB_BACKUP_COMMAND:-}"
PENDING_FRONTEND_DIR="${FRONTEND_PUBLIC_DIR}.pending"
RELEASE_DIR="$RELEASES_DIR/$RELEASE_ID"

assert_not_nested_under() {
  local label="$1"
  local candidate="$2"
  local forbidden_root="$3"

  if [[ "$candidate" == "$forbidden_root" || "$candidate" == "$forbidden_root"/* ]]; then
    printf '%s must not live under %s: %s\n' "$label" "$forbidden_root" "$candidate" >&2
    exit 1
  fi
}

write_deployed_release_record() {
  local metadata_source="$RELEASE_DIR/release-metadata.json"
  local activated_at
  activated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

  if [[ -f "$metadata_source" ]]; then
    {
      printf '{\n'
      printf '  "releaseId": "%s",\n' "$RELEASE_ID"
      printf '  "activatedAt": "%s",\n' "$activated_at"
      printf '  "releaseDir": "%s",\n' "$RELEASE_DIR"
      printf '  "frontendPublicDir": "%s",\n' "$FRONTEND_PUBLIC_DIR"
      printf '  "backendJarPath": "%s",\n' "$BACKEND_JAR_PATH"
      printf '  "backendServiceName": "%s",\n' "$BACKEND_SERVICE_NAME"
      printf '  "healthcheckUrl": "%s",\n' "$HEALTHCHECK_URL"
      printf '  "packageMetadata": '
      sed 's/^/  /' "$metadata_source"
      printf '\n}\n'
    } > "$DEPLOYED_RELEASE_RECORD"
  else
    cat > "$DEPLOYED_RELEASE_RECORD" <<EOF
{
  "releaseId": "$RELEASE_ID",
  "activatedAt": "$activated_at",
  "releaseDir": "$RELEASE_DIR",
  "frontendPublicDir": "$FRONTEND_PUBLIC_DIR",
  "backendJarPath": "$BACKEND_JAR_PATH",
  "backendServiceName": "$BACKEND_SERVICE_NAME",
  "healthcheckUrl": "$HEALTHCHECK_URL"
}
EOF
  fi
}

run_systemctl() {
  if [[ "$SYSTEMCTL_SUDO" == "true" ]]; then
    sudo "$SYSTEMCTL_BIN" "$@"
  else
    "$SYSTEMCTL_BIN" "$@"
  fi
}

printf '==> Preparing remote release directories\n'
mkdir -p "$RELEASES_DIR" "$BACKEND_RUNTIME_DIR" "$FRONTEND_PUBLIC_DIR"
assert_not_nested_under "FRONTEND_PUBLIC_DIR" "$FRONTEND_PUBLIC_DIR" "$RELEASES_DIR"
assert_not_nested_under "BACKEND_RUNTIME_DIR" "$BACKEND_RUNTIME_DIR" "$RELEASES_DIR"
assert_not_nested_under "BACKEND_JAR_PATH" "$BACKEND_JAR_PATH" "$RELEASES_DIR"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

printf '==> Extracting %s\n' "$RELEASE_ARCHIVE"
tar -xzf "$RELEASE_ARCHIVE" -C "$RELEASE_DIR"

if [[ -n "$DB_BACKUP_COMMAND" ]]; then
  printf '==> Running backup command\n'
  bash -lc "$DB_BACKUP_COMMAND"
fi

printf '==> Activating frontend assets\n'
rm -rf "$PENDING_FRONTEND_DIR"
mkdir -p "$PENDING_FRONTEND_DIR"
cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"
rm -rf "$FRONTEND_PUBLIC_DIR"
mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"

# ── Flyway validate 预检（restart 前置，避免 checksum mismatch 导致启动失败）──
# 工程背景（2026-06-26）：新 jar 启动时若生产 flyway_schema_history 与代码迁移文件 checksum 不一致，
# Spring Boot validateOnMigrate 会让后端拒绝启动，remote-deploy 会卡满 4 分钟 health 超时才失败。
# 本预检在"覆盖 jar"之前用当前运行中的 jar 自带 Flyway 跑 validate，失败则停止 rollout，
# 此时旧 jar 仍在运行（服务不中断），操作者可安全用 flyway-repair-runner.sh 处置。
# 依赖：flyway-repair-runner.sh 需与 remote-deploy.sh 一同部署到服务器（如 /tmp/ 或 $APP_ROOT/bin/）。
# 跳过：SKIP_FLYWAY_VALIDATE=1（紧急/离线场景）
FLYWAY_REPAIR_RUNNER="${FLYWAY_REPAIR_RUNNER:-$(dirname "$0")/flyway-repair-runner.sh}"
if [[ "${SKIP_FLYWAY_VALIDATE:-0}" != "1" && -x "$FLYWAY_REPAIR_RUNNER" ]]; then
  printf '==> Flyway validate pre-check (before jar activation)\n'
  # validate 失败时阻止 rollout；此时旧 jar 未被覆盖，服务仍在线
  if ! bash "$FLYWAY_REPAIR_RUNNER" validate; then
    printf '\n❌ Flyway validate 失败 — 检测到 checksum mismatch 或配置问题。\n' >&2
    printf '   旧 jar 仍在运行，服务未中断。处置方案：\n' >&2
    printf '   1. 查看失败详情（上面输出），确认哪些版本 mismatch\n' >&2
    printf '   2. 确认 mismatch 是良性的（仅历史迁移被改），执行 repair：\n' >&2
    printf '        bash %s repair\n' "$FLYWAY_REPAIR_RUNNER" >&2
    printf '   3. repair 后重跑 remote-deploy.sh\n' >&2
    printf '   4. 紧急跳过本预检（不推荐，会让后端启动失败）：SKIP_FLYWAY_VALIDATE=1\n' >&2
    printf '   详见 docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md §13.5\n\n' >&2
    exit 1
  fi
  printf '✅ Flyway validate 通过（仅 pending 新迁移为预期状态）\n\n'
elif [[ "${SKIP_FLYWAY_VALIDATE:-0}" != "1" ]]; then
  printf '⚠️  Flyway validate 预检跳过：flyway-repair-runner.sh 不可用 (%s)\n' "$FLYWAY_REPAIR_RUNNER"
  printf '   建议将其与 remote-deploy.sh 一起上传，获得 checksum mismatch 前置拦截能力。\n\n'
fi

printf '==> Updating backend artifact\n'
cp "$RELEASE_DIR/backend/app.jar" "$BACKEND_JAR_PATH"

printf '==> Writing deployed release record %s\n' "$DEPLOYED_RELEASE_RECORD"
write_deployed_release_record

printf '==> Restarting backend service %s\n' "$BACKEND_SERVICE_NAME"
run_systemctl daemon-reload || true
run_systemctl restart "$BACKEND_SERVICE_NAME"
run_systemctl --no-pager --full status "$BACKEND_SERVICE_NAME" || true

printf '==> Waiting for health check %s\n' "$HEALTHCHECK_URL"
# 部分环境 ApplicationReadyEvent（如 OrganizationEvent SDK 注册/Kafka 启动）耗时超过 2 分钟，
# 将 health check 等待上限延长至 4 分钟，避免后端已正常启动但脚本提前失败。
for _ in {1..120}; do
  if curl -fsS "$HEALTHCHECK_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "$HEALTHCHECK_URL" >/dev/null

# ── 前端一致性校验：确保 nginx 根目录与发布包一致 ──
printf '==> Verifying frontend consistency\n'
RELEASE_INDEX_HTML="$RELEASE_DIR/frontend/index.html"
PUBLIC_INDEX_HTML="$FRONTEND_PUBLIC_DIR/index.html"
if [[ -f "$RELEASE_INDEX_HTML" && -f "$PUBLIC_INDEX_HTML" ]]; then
  RELEASE_ENTRY=$(grep -oP 'src="/assets/[^"]+' "$RELEASE_INDEX_HTML" | head -1)
  PUBLIC_ENTRY=$(grep -oP 'src="/assets/[^"]+' "$PUBLIC_INDEX_HTML" | head -1)
  if [[ "$RELEASE_ENTRY" != "$PUBLIC_ENTRY" ]]; then
    printf '⚠️  Frontend mismatch detected! Entry: %s (release) vs %s (public)\n' "$RELEASE_ENTRY" "$PUBLIC_ENTRY"
    printf '==> Auto-fixing frontend: re-syncing from release archive\n'
    rm -rf "$PENDING_FRONTEND_DIR"
    mkdir -p "$PENDING_FRONTEND_DIR"
    cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"
    rm -rf "$FRONTEND_PUBLIC_DIR"
    mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"
    printf '✅ Frontend re-synced successfully\n'
  else
    printf '✅ Frontend matches release (%s)\n' "$RELEASE_ENTRY"
  fi
else
  if [[ ! -f "$RELEASE_INDEX_HTML" ]]; then
    printf '⚠️  Release frontend index.html not found — skipping frontend verification\n'
  fi
  if [[ ! -f "$PUBLIC_INDEX_HTML" ]]; then
    printf '⚠️  Public frontend index.html not found at %s — re-syncing\n' "$PUBLIC_INDEX_HTML"
    rm -rf "$PENDING_FRONTEND_DIR"
    mkdir -p "$PENDING_FRONTEND_DIR"
    cp -R "$RELEASE_DIR/frontend/." "$PENDING_FRONTEND_DIR/"
    rm -rf "$FRONTEND_PUBLIC_DIR"
    mv "$PENDING_FRONTEND_DIR" "$FRONTEND_PUBLIC_DIR"
  fi
fi

if [[ -n "$POST_DEPLOY_COMMAND" ]]; then
  printf '==> Running post deploy command\n'
  bash -lc "$POST_DEPLOY_COMMAND"
fi

printf 'Remote deployment completed for release %s\n' "$RELEASE_ID"
