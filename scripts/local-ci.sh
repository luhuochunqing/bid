#!/usr/bin/env bash
# Input: local CI mode, optional LOCAL_CI_BASE override, and developer machine toolchain state; H2 integration tests explicitly own ddl-auto
# Output: GitHub Actions-equivalent local validation for frontend, backend, E2E, and optional release gates
# Pos: scripts/ - Local CI fallback for repository validation
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
MODE="${1:-full}"
TRACKED_VITE_CACHE_ARCHIVE=""

usage() {
  cat <<'EOF'
Usage: bash scripts/local-ci.sh [quick|full|release]

Modes:
  quick    Fast local preflight: governance checks, frontend unit/API build, backend compile and architecture gates
  full     Default local CI: GitHub CI-equivalent core gates plus API-backed E2E
  release  Full local CI plus release rehearsal, signoff packet, and release package

Environment:
  LOCAL_CI_BASE        Override line-budget base revision. Defaults to merge-base of HEAD and origin/main.
  VITE_API_BASE_URL    API base URL for API-mode frontend builds. Defaults to http://127.0.0.1:18080.
  BACKEND_PORT         Local E2E backend port. Defaults to 18080.
  FRONTEND_PORT        Local E2E frontend port. Defaults to 1314.
EOF
}

if (( $# > 1 )); then
  printf 'Unexpected extra arguments: %s\n\n' "${*:2}" >&2
  usage >&2
  exit 1
fi

case "$MODE" in
  quick|full|release)
    ;;
  -h|--help|help)
    usage
    exit 0
    ;;
  *)
    printf 'Unsupported local CI mode: %s\n\n' "$MODE" >&2
    usage >&2
    exit 1
    ;;
esac

export CI=true
export VITE_API_MODE=api
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-http://127.0.0.1:18080}"
export PLATFORM_ENCRYPTION_KEY="${PLATFORM_ENCRYPTION_KEY:-ci-test-key-2026}"
export PLATFORM_ACCOUNT_ENCRYPTION_KEY="${PLATFORM_ACCOUNT_ENCRYPTION_KEY:-test-platform-encryption-key-2026}"
export JWT_SECRET="${JWT_SECRET:-ci-e2e-jwt-secret-key-with-32-chars-min}"
export BACKEND_PORT="${BACKEND_PORT:-18080}"
export FRONTEND_PORT="${FRONTEND_PORT:-1314}"
export DB_PASSWORD="${DB_PASSWORD:-unused}"

log_step() {
  printf '\n==> %s\n' "$1"
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command_name" >&2
    exit 1
  fi
}

require_docker() {
  require_command docker
  if ! docker info >/dev/null 2>&1; then
    printf 'Docker is required for full/release local CI because backend migration gates use Testcontainers.\n' >&2
    printf 'Start Docker, or run quick mode with: npm run ci:local:quick\n' >&2
    exit 1
  fi
}

prepare_tracked_vite_cache_restore() {
  local tracked_files
  tracked_files="$(git -C "$ROOT_DIR" ls-files node_modules/.vite/deps)"
  if [[ -z "$tracked_files" ]]; then
    return
  fi

  if ! git -C "$ROOT_DIR" diff --quiet -- node_modules/.vite/deps ||
    ! git -C "$ROOT_DIR" diff --cached --quiet -- node_modules/.vite/deps; then
    printf 'Tracked Vite cache files already have local changes. Commit or clean them before running local CI.\n' >&2
    exit 1
  fi

  TRACKED_VITE_CACHE_ARCHIVE="$(mktemp "${TMPDIR:-/tmp}/xiyu-local-ci-vite-cache.XXXXXX.tar")"
  git -C "$ROOT_DIR" archive --format=tar -o "$TRACKED_VITE_CACHE_ARCHIVE" HEAD node_modules/.vite/deps
}

restore_tracked_vite_cache() {
  if [[ -n "$TRACKED_VITE_CACHE_ARCHIVE" && -f "$TRACKED_VITE_CACHE_ARCHIVE" ]]; then
    tar -xf "$TRACKED_VITE_CACHE_ARCHIVE" -C "$ROOT_DIR"
    rm -f "$TRACKED_VITE_CACHE_ARCHIVE"
  fi
}

run_root() {
  log_step "$*"
  (cd "$ROOT_DIR" && "$@")
}

run_backend() {
  log_step "backend: $*"
  (cd "$BACKEND_DIR" && "$@")
}

prepare_backend_build() {
  run_backend mvn clean
}

resolve_line_budget_base() {
  if [[ -n "${LOCAL_CI_BASE:-}" ]]; then
    printf '%s\n' "$LOCAL_CI_BASE"
    return
  fi

  if git -C "$ROOT_DIR" rev-parse --verify origin/main >/dev/null 2>&1; then
    git -C "$ROOT_DIR" merge-base HEAD origin/main
    return
  fi

  printf 'origin/main is unavailable; falling back to HEAD~1 for line-budget base.\n' >&2
  git -C "$ROOT_DIR" rev-parse HEAD~1
}

install_frontend_dependencies() {
  run_root npm ci
}

run_line_budget_gate() {
  local base
  base="$(resolve_line_budget_base)"
  log_step "line budget gate: base=$base head=HEAD"
  (cd "$ROOT_DIR" && node scripts/check-line-budgets.mjs --base "$base" --head HEAD)
}

run_frontend_quick() {
  run_root npm run check:version-sync
  run_root npm run check:front-data-boundaries
  run_root npm run check:doc-governance
  run_root npm run check:token-governance
  run_root npm run test:agent-start-task-contract
  run_root npm run test:unit
  run_root npm run build:api
}

run_frontend_full() {
  run_root npm run test:unit
  run_root npm run build
  run_root npm run build:api
}

run_backend_quick() {
  prepare_backend_build
  run_backend mvn -DskipTests compile
  run_backend mvn -DforkCount=0 -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest test
}

run_backend_full() {
  prepare_backend_build
  run_backend mvn -DskipTests compile
  run_backend mvn -DforkCount=0 -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest test
  run_backend mvn -Dtest=ExpenseControllerIntegrationTest,BarCertificateControllerIntegrationTest test
  run_backend mvn -Dtest=FlywayMysqlContainerTest,ArchitectureTest test
  run_backend mvn -Pjava-quality,java-quality-spotbugs,quality-strict -DskipTests -Djacoco.skip=true checkstyle:check pmd:check spotbugs:check
}

run_e2e_gate() {
  run_root npx playwright install chromium
  log_step "API-backed E2E commercial flow"
  (
    cd "$ROOT_DIR"
    SPRING_PROFILES_ACTIVE=e2e \
      DB_PASSWORD="$DB_PASSWORD" \
      JWT_SECRET="$JWT_SECRET" \
      BACKEND_PORT="$BACKEND_PORT" \
      FRONTEND_PORT="$FRONTEND_PORT" \
      npm run test:e2e:commercial
  )
}

run_release_gates() {
  run_root bash scripts/release/rehearse-release.sh
  run_root node scripts/release/build-signoff-packet.mjs
  run_root bash scripts/release/package-release.sh
}

run_common_setup() {
  require_command git
  require_command node
  require_command npm
  require_command java
  require_command mvn
  prepare_tracked_vite_cache_restore
  install_frontend_dependencies
  run_line_budget_gate
}

trap restore_tracked_vite_cache EXIT

printf 'Local CI mode: %s\n' "$MODE"
printf 'API mode: %s (%s)\n' "$VITE_API_MODE" "$VITE_API_BASE_URL"

case "$MODE" in
  quick)
    run_common_setup
    run_frontend_quick
    run_backend_quick
    ;;
  full)
    require_docker
    run_common_setup
    run_frontend_full
    run_backend_full
    run_e2e_gate
    ;;
  release)
    require_docker
    run_common_setup
    run_frontend_full
    run_backend_full
    run_e2e_gate
    run_release_gates
    ;;
esac

printf '\nLocal CI completed successfully: %s\n' "$MODE"
