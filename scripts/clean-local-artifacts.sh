#!/usr/bin/env bash
# Input: repository root and local generated artifact directories
# Output: cleaned local rehearsal, report, and test artifact folders
# Pos: scripts/ - Local maintenance utilities
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Cleaning local-only artifacts under ${ROOT_DIR}"

rm -rf "${ROOT_DIR}/.rehearsal"
rm -rf "${ROOT_DIR}/test-results"
rm -rf "${ROOT_DIR}/playwright-report"
rm -rf "${ROOT_DIR}/backend/docs/reports"

find "${ROOT_DIR}/docs/reports" -mindepth 1 ! -name '.gitkeep' -delete 2>/dev/null || true

echo "Local artifact cleanup complete."
