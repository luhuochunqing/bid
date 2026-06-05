#!/usr/bin/env bash
# Input: repository root and Node runtime
# Output: compatibility entry that runs the documentation governance checker
# Pos: scripts/ - Legacy shell wrapper for doc governance
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
node "${ROOT_DIR}/scripts/check-doc-governance.mjs"
