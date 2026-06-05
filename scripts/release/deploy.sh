#!/usr/bin/env bash
# Input: release environment variables, repository filesystem, and packaging arguments
# Output: verified release package plus operator-facing deployment next steps
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"

printf '==> Running release preflight\n'
bash "$ROOT_DIR/scripts/release/preflight.sh"

printf '\n==> Running rehearsal env contract checks\n'
bash "$ROOT_DIR/scripts/test/release-rehearsal-contract.sh"

printf '\n==> Building frontend (mock mode)\n'
cd "$ROOT_DIR"
npm run build

printf '\n==> Building frontend (api mode)\n'
VITE_API_MODE=api npm run build

printf '\n==> Compiling backend\n'
cd "$BACKEND_DIR"
mvn -DskipTests compile

printf '\n==> Running critical backend tests\n'
mvn -Dtest=ExpenseControllerIntegrationTest,BarCertificateControllerIntegrationTest test

if command -v docker >/dev/null 2>&1; then
  printf '\n==> Running MySQL Testcontainers baseline verification\n'
  mvn -Dtest=FlywayMysqlContainerTest test
else
  printf '\nSkipping MySQL Testcontainers verification because Docker is unavailable.\n'
fi

printf '\n==> Packaging release archive\n'
bash "$ROOT_DIR/scripts/release/package-release.sh"

printf '\n==> Release pre-deploy bundle completed\n'
printf 'Next steps:\n'
printf '1. Trigger .github/workflows/main-release.yml or upload the archive to the target host\n'
printf '2. Run the remote activation step with scripts/release/remote-deploy.sh\n'
printf '3. Execute node scripts/release/run-prod-smoke.mjs against production\n'
printf '4. Complete docs/GO_LIVE_CHECKLIST.md and docs/PRODUCTION_RELEASE_PIPELINE.md\n'
