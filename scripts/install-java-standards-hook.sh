#!/usr/bin/env bash
# Input: Git repository common hooks directory and bundled pre-commit source hook
# Output: installed pre-commit hook with executable permissions for local quality checks
# Pos: scripts/开发环境安装脚本
# 维护声明: 仅维护本地 hook 安装逻辑；若 hook 来源、规则或 worktree 支持变化请同步更新安装提示和 scripts/README.md。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
COMMON_GIT_DIR="$(git rev-parse --git-common-dir)"
SOURCE_HOOK="$ROOT_DIR/.githooks/pre-commit"
HOOKS_PATH="$(git config --get core.hooksPath || true)"

if [[ -n "$HOOKS_PATH" && "$HOOKS_PATH" = /* ]]; then
  TARGET_HOOK="$HOOKS_PATH/pre-commit"
elif [[ -n "$HOOKS_PATH" ]]; then
  TARGET_HOOK="$ROOT_DIR/$HOOKS_PATH/pre-commit"
else
  TARGET_HOOK="$COMMON_GIT_DIR/hooks/pre-commit"
fi

TARGET_DIR="$(dirname "$TARGET_HOOK")"

if [ ! -f "$SOURCE_HOOK" ]; then
  echo "Missing source hook: $SOURCE_HOOK"
  exit 1
fi

mkdir -p "$TARGET_DIR"

if [ -f "$TARGET_HOOK" ]; then
  backup="$TARGET_HOOK.backup.$(date +%Y%m%d%H%M%S)"
  cp "$TARGET_HOOK" "$backup"
  echo "Backed up existing pre-commit hook to: $backup"
fi

cp "$SOURCE_HOOK" "$TARGET_HOOK"
chmod +x "$TARGET_HOOK"
chmod +x "$SOURCE_HOOK"
chmod +x "$ROOT_DIR/scripts/agent-worktree-guard.sh"
chmod +x "$ROOT_DIR/scripts/check-java-coding-standards.sh"

echo "Installed pre-commit hook."
echo "Hook target: $TARGET_HOOK"
