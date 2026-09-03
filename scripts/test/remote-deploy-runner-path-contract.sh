#!/usr/bin/env bash
# Input: none
# Output: validates remote-deploy.sh FLYWAY_REPAIR_RUNNER authoritative path resolution
# Pos: scripts/test/remote-deploy-runner-path-contract.sh
# 维护声明: remote-deploy.sh 的 flyway-repair-runner.sh 权威位置为 $APP_ROOT/bin/
#           （deploy-test.sh / deploy-prod.sh 每次部署 scp 最新版至此）。
#           本契约重放第 155 次测试部署事故（2026-09-03）：脚本所在目录（incoming/）
#           残留旧版 runner（缺 ignoreMigrationPatterns("*:pending")）时不得被误用，
#           否则 pending 新迁移会被误报为 validate 失败。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOY_SCRIPT="$REPO_ROOT/scripts/release/remote-deploy.sh"

PASS_COUNT=0

assert_contains() {
  local haystack="$1" needle="$2" description="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "[FAIL] missing: $description" >&2
    echo "        expected snippet: $needle" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

assert_file_exists() {
  local path="$1" description="$2"
  if [[ ! -f "$path" ]]; then
    echo "[FAIL] expected file missing: $description" >&2
    echo "        path: $path" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

assert_file_absent() {
  local path="$1" description="$2"
  if [[ -f "$path" ]]; then
    echo "[FAIL] unexpected file exists: $description" >&2
    echo "        path: $path" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

# --- 场景 0：bash 语法检查 ---
bash -n "$DEPLOY_SCRIPT"
echo "[PASS] remote-deploy.sh bash 语法有效"
PASS_COUNT=$((PASS_COUNT + 1))

# --- 隔离环境：假 APP_ROOT / 假前端目录 / 假 release archive / incoming 副本 ---
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

APP_ROOT="$WORK/app-root"
FRONTEND_DIR="$WORK/frontend"
INCOMING_DIR="$WORK/incoming"
mkdir -p "$APP_ROOT/bin" "$FRONTEND_DIR" "$INCOMING_DIR"

ARCHIVE="$WORK/release.tar.gz"
STAGE_DIR="$WORK/stage"
mkdir -p "$STAGE_DIR/backend" "$STAGE_DIR/frontend"
echo "dummy-jar" > "$STAGE_DIR/backend/app.jar"
echo "<html>dummy</html>" > "$STAGE_DIR/frontend/index.html"
tar -czf "$ARCHIVE" -C "$STAGE_DIR" .

cp "$DEPLOY_SCRIPT" "$INCOMING_DIR/remote-deploy.sh"

# 毒 runner：模拟 incoming/ 残留的旧版（第 155 次部署事故现场）
cat > "$INCOMING_DIR/flyway-repair-runner.sh" <<'EOF'
#!/usr/bin/env bash
echo "POISONED" > "$(dirname "$0")/poison-executed.marker"
exit 0
EOF
chmod +x "$INCOMING_DIR/flyway-repair-runner.sh"

run_deploy() {
  env \
    RELEASE_ARCHIVE="$ARCHIVE" \
    RELEASE_ID="contract-test" \
    APP_ROOT="$APP_ROOT" \
    FRONTEND_PUBLIC_DIR="$FRONTEND_DIR" \
    BACKEND_SERVICE_NAME="dummy-service" \
    HEALTHCHECK_URL="http://127.0.0.1:1/actuator/health" \
    BACKEND_ENV_FILE="/nonexistent/backend.env" \
    SYSTEMCTL_BIN="/usr/bin/false" \
    SYSTEMCTL_SUDO="false" \
    bash "$INCOMING_DIR/remote-deploy.sh" \
    >"$WORK/stdout.log" 2>"$WORK/stderr.log" || true
}

# --- 场景 1（事故重放）：bin/ 权威 runner 存在时，incoming/ 毒 runner 不得被执行 ---
cat > "$APP_ROOT/bin/flyway-repair-runner.sh" <<'EOF'
#!/usr/bin/env bash
echo "OK" > "$(dirname "$0")/bin-executed.marker"
exit 1
EOF
chmod +x "$APP_ROOT/bin/flyway-repair-runner.sh"

run_deploy
STDERR="$(cat "$WORK/stderr.log")"

assert_file_exists "$APP_ROOT/bin/bin-executed.marker" "场景1: bin/ 权威 runner 被执行"
assert_file_absent "$INCOMING_DIR/poison-executed.marker" "场景1: incoming/ 毒 runner 未被执行（155 次事故不复发）"
assert_contains "$STDERR" "$APP_ROOT/bin/flyway-repair-runner.sh" "场景1: validate 失败提示指向 bin/ 权威路径"

# --- 场景 2：bin/ 权威 runner 缺失时优雅降级（警告跳过，不回退到脚本同目录）---
rm -f "$APP_ROOT/bin/flyway-repair-runner.sh" "$APP_ROOT/bin/bin-executed.marker" "$INCOMING_DIR/poison-executed.marker"

run_deploy
STDERR="$(cat "$WORK/stderr.log")"

assert_contains "$STDERR" "Flyway validate 预检跳过" "场景2: bin/ 缺 runner 时预检跳过并警告"
assert_contains "$STDERR" "$APP_ROOT/bin/" "场景2: 警告文案指明权威位置"
assert_file_absent "$INCOMING_DIR/poison-executed.marker" "场景2: 不回退执行脚本同目录的毒 runner"

# --- 场景 3：显式 FLYWAY_REPAIR_RUNNER 环境变量仍可覆盖默认值（兼容性）---
EXPLICIT_RUNNER="$WORK/explicit-runner.sh"
cat > "$EXPLICIT_RUNNER" <<'EOF'
#!/usr/bin/env bash
echo "OK" > "$(dirname "$0")/explicit-executed.marker"
exit 1
EOF
chmod +x "$EXPLICIT_RUNNER"

env \
  RELEASE_ARCHIVE="$ARCHIVE" \
  RELEASE_ID="contract-test" \
  APP_ROOT="$APP_ROOT" \
  FRONTEND_PUBLIC_DIR="$FRONTEND_DIR" \
  BACKEND_SERVICE_NAME="dummy-service" \
  HEALTHCHECK_URL="http://127.0.0.1:1/actuator/health" \
  BACKEND_ENV_FILE="/nonexistent/backend.env" \
  SYSTEMCTL_BIN="/usr/bin/false" \
  SYSTEMCTL_SUDO="false" \
  FLYWAY_REPAIR_RUNNER="$EXPLICIT_RUNNER" \
  bash "$INCOMING_DIR/remote-deploy.sh" \
  >"$WORK/stdout.log" 2>"$WORK/stderr.log" || true

assert_file_exists "$WORK/explicit-executed.marker" "场景3: 显式 FLYWAY_REPAIR_RUNNER 覆盖默认值"

echo
echo "remote-deploy runner-path contract: ${PASS_COUNT} assertions passed"
