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

printf '==> Updating backend artifact\n'
cp "$RELEASE_DIR/backend/app.jar" "$BACKEND_JAR_PATH"

printf '==> Writing deployed release record %s\n' "$DEPLOYED_RELEASE_RECORD"
write_deployed_release_record

printf '==> Restarting backend service %s\n' "$BACKEND_SERVICE_NAME"
run_systemctl daemon-reload || true
run_systemctl restart "$BACKEND_SERVICE_NAME"
run_systemctl --no-pager --full status "$BACKEND_SERVICE_NAME" || true

printf '==> Waiting for health check %s\n' "$HEALTHCHECK_URL"
for _ in {1..60}; do
  if curl -fsS "$HEALTHCHECK_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
curl -fsS "$HEALTHCHECK_URL" >/dev/null

if [[ -n "$POST_DEPLOY_COMMAND" ]]; then
  printf '==> Running post deploy command\n'
  bash -lc "$POST_DEPLOY_COMMAND"
fi

printf 'Remote deployment completed for release %s\n' "$RELEASE_ID"
