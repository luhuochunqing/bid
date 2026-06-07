#!/usr/bin/env bash
# Input: release environment variables, MySQL 8.0 rehearsal configuration, UAT/report paths, and Playwright bootstrap controls
# Output: rehearsal lifecycle using the release stack, UAT execution, backup, restore verification, and startup diagnostics
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/release/rehearsal-env.sh"

print_backend_log_tail() {
  if [[ -f "$STATE_DIR/backend.log" ]]; then
    printf '\n==> Backend log tail\n' >&2
    tail -n 200 "$STATE_DIR/backend.log" >&2 || true
  fi
}

wait_for_backend_health() {
  local label="$1"
  local pid

  printf '==> Waiting for backend %s\n' "$label"
  for i in {1..180}; do
    if curl -fsS "$UAT_API_BASE_URL/actuator/health" >/dev/null 2>&1; then
      return 0
    fi

    if [[ -f "$STATE_DIR/backend.pid" ]]; then
      pid="$(cat "$STATE_DIR/backend.pid")"
      if ! kill -0 "$pid" >/dev/null 2>&1; then
        printf 'Backend process exited before health check passed after %s\n' "$label" >&2
        print_backend_log_tail
        return 1
      fi
    fi

    sleep 2
  done

  printf 'Backend health check timed out after %s: %s\n' "$label" "$UAT_API_BASE_URL/actuator/health" >&2
  print_backend_log_tail
  return 1
}

cleanup() {
  bash "$ROOT_DIR/scripts/release/rehearsal-down.sh" >/dev/null 2>&1 || true
}
trap cleanup EXIT

printf '==> Starting local rehearsal environment\n'
bash "$ROOT_DIR/scripts/release/rehearsal-up.sh"

printf '\n==> Running automated UAT\n'
cd "$ROOT_DIR"
node "$ROOT_DIR/scripts/release/run-uat.mjs"
UAT_REPORT_JSON="$(ls -1t "$REPORT_DIR"/uat-report-*.json | head -n 1)"
export UAT_REPORT_JSON
export PLAYWRIGHT_BASE_URL="$UAT_WEB_BASE_URL"
export PLAYWRIGHT_API_BASE_URL="${PLAYWRIGHT_API_BASE_URL:-$UAT_API_BASE_URL}"
printf 'UAT report: %s\n' "$UAT_REPORT_JSON"

printf '\n==> Running browser E2E gate (chromium only)\n'
PLAYWRIGHT_DISABLE_API_BOOTSTRAP=1 npm run test:e2e:commercial -- --project=chromium

printf '\n==> Creating rehearsal backup\n'
export MYSQL_CONTAINER_NAME
export BACKUP_DIR="$STATE_DIR/backups"
bash "$ROOT_DIR/scripts/release/backup-db.sh"
BACKUP_FILE="$(ls -1t "$BACKUP_DIR"/*.sql | head -n 1)"
printf 'Backup file: %s\n' "$BACKUP_FILE"

printf '\n==> Creating post-backup mutation marker\n'
RESTORE_MARKER_JSON="$(node "$ROOT_DIR/scripts/release/create-restore-marker.mjs" "$UAT_REPORT_JSON")"
printf 'Restore marker: %s\n' "$RESTORE_MARKER_JSON"

printf '\n==> Stopping backend before restore verification\n'
if [[ -f "$STATE_DIR/backend.pid" ]]; then
  kill "$(cat "$STATE_DIR/backend.pid")" >/dev/null 2>&1 || true
  rm -f "$STATE_DIR/backend.pid"
fi

printf '==> Restoring rehearsal backup\n'
CONFIRM_RESTORE=YES bash "$ROOT_DIR/scripts/release/restore-db.sh" "$BACKUP_FILE"

printf '==> Restarting backend after restore\n'
bash "$ROOT_DIR/scripts/release/start-backend.sh"

wait_for_backend_health "after restore"

printf '==> Verifying restore removed post-backup mutation\n'
POST_RESTORE_REPORT="$(node "$ROOT_DIR/scripts/release/verify-restore.mjs" "$UAT_REPORT_JSON" "$RESTORE_MARKER_JSON")"

printf '\n==> Rehearsal completed\n'
printf 'UAT and restore reports are in %s\n' "$REPORT_DIR"
