#!/usr/bin/env bash
# Input: rehearsal env overrides and shell execution context
# Output: contract assertions for release rehearsal env defaults and validation behavior
# Pos: scripts/test/ - Playwright and API-backed test baseline helpers
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_SCRIPT="$ROOT_DIR/scripts/release/rehearsal-env.sh"

# Provide placeholder secrets so rehearsal-env.sh fail-fast guards (`${VAR:?...}`)
# do not abort the contract test. The contract test exercises non-secret defaults
# (ports, hostnames, container names) — secret values are unexercised here, but
# must be present so sourcing the script succeeds.
export DB_PASSWORD="${DB_PASSWORD:-rehearsal-test-db-password}"
export MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-rehearsal-test-root-password}"
export JWT_SECRET="${JWT_SECRET:-rehearsal-test-jwt-secret-32-chars-minimum-padding}"
export PLATFORM_ENCRYPTION_KEY="${PLATFORM_ENCRYPTION_KEY:-rehearsal-test-platform-key-32}"
export UAT_TEST_PASSWORD="${UAT_TEST_PASSWORD:-rehearsal-test-uat}"
export ADMIN_PASSWORD="${ADMIN_PASSWORD:-rehearsal-test-admin}"

set +e
PORT_ERR_OUTPUT="$(env REDIS_PORT=70000 bash -lc "source '$ENV_SCRIPT'" 2>&1)"
PORT_EXIT=$?
set -e
if [[ "$PORT_EXIT" -eq 0 ]]; then
  printf 'Expected rehearsal-env.sh to fail when REDIS_PORT is out of range.\n' >&2
  exit 1
fi
if [[ "$PORT_ERR_OUTPUT" != *"Invalid REDIS_PORT"* ]]; then
  printf 'Unexpected validation error output: %s\n' "$PORT_ERR_OUTPUT" >&2
  exit 1
fi

PATH_OUTPUT="$(env STATE_DIR=.rehearsal-contract REPORT_DIR=docs/reports/contract bash -lc "source '$ENV_SCRIPT'; printf '%s\n%s\n' \"\$STATE_DIR\" \"\$REPORT_DIR\"")"
STATE_DIR_VALUE="$(printf '%s\n' "$PATH_OUTPUT" | sed -n '1p')"
REPORT_DIR_VALUE="$(printf '%s\n' "$PATH_OUTPUT" | sed -n '2p')"
if [[ "$STATE_DIR_VALUE" != "$ROOT_DIR/.rehearsal-contract" ]]; then
  printf 'Expected STATE_DIR to resolve to %s/.rehearsal-contract, got %s\n' "$ROOT_DIR" "$STATE_DIR_VALUE" >&2
  exit 1
fi
if [[ "$REPORT_DIR_VALUE" != "$ROOT_DIR/docs/reports/contract" ]]; then
  printf 'Expected REPORT_DIR to resolve to %s/docs/reports/contract, got %s\n' "$ROOT_DIR" "$REPORT_DIR_VALUE" >&2
  exit 1
fi

API_OUTPUT="$(env UAT_API_BASE_URL=http://127.0.0.1:29080 bash -lc "source '$ENV_SCRIPT'; printf '%s\n%s\n' \"\$UAT_API_BASE_URL\" \"\$PLAYWRIGHT_API_BASE_URL\"")"
UAT_API_BASE_URL_VALUE="$(printf '%s\n' "$API_OUTPUT" | sed -n '1p')"
PLAYWRIGHT_API_BASE_URL_VALUE="$(printf '%s\n' "$API_OUTPUT" | sed -n '2p')"
if [[ "$PLAYWRIGHT_API_BASE_URL_VALUE" != "$UAT_API_BASE_URL_VALUE" ]]; then
  printf 'Expected PLAYWRIGHT_API_BASE_URL (%s) to default to UAT_API_BASE_URL (%s).\n' "$PLAYWRIGHT_API_BASE_URL_VALUE" "$UAT_API_BASE_URL_VALUE" >&2
  exit 1
fi

PLAYWRIGHT_OVERRIDE_VALUE="$(env UAT_API_BASE_URL=http://127.0.0.1:29080 PLAYWRIGHT_API_BASE_URL=http://127.0.0.1:39080 bash -lc "source '$ENV_SCRIPT'; printf '%s\n' \"\$PLAYWRIGHT_API_BASE_URL\"")"
if [[ "$PLAYWRIGHT_OVERRIDE_VALUE" != "http://127.0.0.1:39080" ]]; then
  printf 'Expected explicit PLAYWRIGHT_API_BASE_URL override to be preserved.\n' >&2
  exit 1
fi

DEFAULT_DB_OUTPUT="$(bash -lc "source '$ENV_SCRIPT'; printf '%s\n%s\n%s\n%s\n' \"\$DB_ENGINE\" \"\$DB_PORT\" \"\$SPRING_PROFILES_ACTIVE\" \"\$DB_URL\"")"
DEFAULT_DB_ENGINE="$(printf '%s\n' "$DEFAULT_DB_OUTPUT" | sed -n '1p')"
DEFAULT_DB_PORT="$(printf '%s\n' "$DEFAULT_DB_OUTPUT" | sed -n '2p')"
DEFAULT_SPRING_PROFILES_ACTIVE="$(printf '%s\n' "$DEFAULT_DB_OUTPUT" | sed -n '3p')"
DEFAULT_DB_URL="$(printf '%s\n' "$DEFAULT_DB_OUTPUT" | sed -n '4p')"
if [[ "$DEFAULT_DB_ENGINE" != "mysql" ]]; then
  printf 'Expected default DB_ENGINE to be mysql, got %s.\n' "$DEFAULT_DB_ENGINE" >&2
  exit 1
fi
if [[ "$DEFAULT_DB_PORT" != "53306" ]]; then
  printf 'Expected default MySQL rehearsal DB_PORT to be 53306, got %s.\n' "$DEFAULT_DB_PORT" >&2
  exit 1
fi
if [[ "$DEFAULT_SPRING_PROFILES_ACTIVE" != "prod,mysql" ]]; then
  printf 'Expected default SPRING_PROFILES_ACTIVE to be prod,mysql, got %s.\n' "$DEFAULT_SPRING_PROFILES_ACTIVE" >&2
  exit 1
fi
if [[ "$DEFAULT_DB_URL" != jdbc:mysql://* ]]; then
  printf 'Expected default DB_URL to be a MySQL JDBC URL, got %s.\n' "$DEFAULT_DB_URL" >&2
  exit 1
fi

printf 'Release rehearsal env contract checks passed.\n'
