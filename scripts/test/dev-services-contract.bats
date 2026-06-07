#!/usr/bin/env bats
# Dev Services 启动脚本行为合约测试
# 不启动真实服务，只测试关键函数的逻辑正确性
# Pos: scripts/test/ - dev-services.sh behavioral contract tests

setup() {
  export ROOT_DIR="/tmp/dev-services-test-$$"
  export RUNTIME_DIR="$ROOT_DIR/.runtime/dev-services"
  export SIDECAR_PORT=18001
  export BACKEND_PORT=18002
  export FRONTEND_PORT=18003
  mkdir -p "$RUNTIME_DIR"
}

teardown() {
  rm -rf "$ROOT_DIR" 2>/dev/null || true
}

# ── frontend_matches_workspace ─────────────────────────────────

@test "frontend_matches_workspace: no longer depends on dev-frontend-health.sh" {
  local script="${BATS_TEST_DIRNAME}/../dev-services.sh"
  # frontend_matches_workspace 函数体内不应出现 FRONTEND_HEALTH_SCRIPT
  # 只有变量定义行（~97行）允许
  local inside_fn
  inside_fn=$(awk '/^frontend_matches_workspace\(\)/,/^frontend_current_for_pid\(\)|^print_frontend_mismatch\(\)|^wait_frontend\(\)/' "$script" | grep -c 'FRONTEND_HEALTH_SCRIPT' || true)
  [ "$inside_fn" -eq 0 ]
}

# ── wait_frontend ─────────────────────────────────────────────

@test "wait_frontend: has Phase 1 + Phase 2 (cwd + HTTP)" {
  local script="${BATS_TEST_DIRNAME}/../dev-services.sh"
  run grep -c 'Phase 1' "$script"
  [ "$output" -gt 0 ]
  run grep -c 'Phase 2' "$script"
  [ "$output" -gt 0 ]
}

# ── 进程清理 ───────────────────────────────────────────────────

@test "start_frontend has force cleanup logic" {
  local script="${BATS_TEST_DIRNAME}/../dev-services.sh"
  run grep -c 'forcing cleanup of conflicting process' "$script"
  [ "$output" -gt 0 ]
}

@test "start_backend has force cleanup logic" {
  local script="${BATS_TEST_DIRNAME}/../dev-services.sh"
  run grep -c 'forcing cleanup of conflicting process' "$script"
  [ "$output" -ge 2 ]  # backend + frontend + sidecar 各一个
}

# ── dev-services-health.sh ────────────────────────────────────

@test "dev-services-health.sh: outputs valid JSON" {
  run bash "${BATS_TEST_DIRNAME}/../dev-services-health.sh"
  [ "$status" -eq 0 ]
  echo "$output" | python3 -m json.tool > /dev/null 2>&1
}

@test "dev-services-health.sh: contains all three services" {
  run bash "${BATS_TEST_DIRNAME}/../dev-services-health.sh"
  [[ "$output" == *'"sidecar"'* ]]
  [[ "$output" == *'"backend"'* ]]
  [[ "$output" == *'"frontend"'* ]]
}

# ── launchd ────────────────────────────────────────────────────

@test "launchd plist uses bash -c not bash -l" {
  run grep -c 'bash -lc' "${BATS_TEST_DIRNAME}/../dev-services-launchd.sh"
  [ "$output" -eq 0 ]
}

# ── 端口清理 ───────────────────────────────────────────────────

@test "launchd cleanup forces port-level kill" {
  run grep -c 'force-killing processes on port' "${BATS_TEST_DIRNAME}/../dev-services-launchd.sh"
  [ "$output" -gt 0 ]
}
