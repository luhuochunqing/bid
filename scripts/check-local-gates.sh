#!/usr/bin/env bash
# Input: current git worktree
# Output: colored pass/fail report on local gate readiness
# Pos: scripts/ - 本地门禁自检 (2026-06-09)
# 维护声明: 仅维护本地门禁自检逻辑；新增门禁项目时请同步检查逻辑。
# 一旦我被更新，务必更新我的开头注释。
#
# check-local-gates.sh — Agent 对话开始时快速验证本地门禁是否就绪。
#
# 背景: GitHub CI被封后，门禁完全依赖每个 Worktree 中配置的 git hooks
# 和脚本包装器。本脚本在一次调用中确认所有门禁组件就绪。
#
# 用法:
#   bash scripts/check-local-gates.sh
#   bash scripts/check-local-gates.sh --verbose
#
# 退出码: 0 = 所有门禁就绪, 1 = 有门禁未就绪

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VERBOSE=false

for arg in "$@"; do
  case "$arg" in
    --verbose|-v) VERBOSE=true ;;
  esac
done

PASS=0; FAIL=0; SKIP=0
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

pass() { PASS=$((PASS + 1)); echo -e "${GREEN}✓${NC} $1"; }
fail() { FAIL=$((FAIL + 1)); echo -e "${RED}✗${NC} $1"; }
skip() { SKIP=$((SKIP + 1)); echo -e "${YELLOW}⊘${NC} $1"; }

cd "$ROOT_DIR"

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  🔒 本地门禁自检                                  ║"
echo "║  worktree: $(basename "$ROOT_DIR")"
echo "║  $(date '+%Y-%m-%d %H:%M:%S')"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# ── 1. core.hooksPath 是否指向 .githooks ──
HOOKS_PATH="$(git config core.hooksPath 2>/dev/null || echo '')"
if [ "$HOOKS_PATH" = ".githooks" ]; then
  pass "core.hooksPath = .githooks"
else
  fail "core.hooksPath ≠ .githooks (当前: ${HOOKS_PATH:-未设置}) — 运行 bash scripts/install-githooks.sh"
fi

# ── 2. .githooks/ 文件完整性 ──
if [ -f "$ROOT_DIR/.githooks/pre-commit" ] && [ -x "$ROOT_DIR/.githooks/pre-commit" ]; then
  pass ".githooks/pre-commit 存在且可执行"
else
  fail ".githooks/pre-commit 缺失或不可执行"
fi

if [ -f "$ROOT_DIR/.githooks/pre-push" ] && [ -x "$ROOT_DIR/.githooks/pre-push" ]; then
  pass ".githooks/pre-push 存在且可执行"
else
  fail ".githooks/pre-push 缺失或不可执行"
fi

# ── 3. pre-push hook 是否会调用 pre-push-gate.sh ──
if grep -q 'pre-push-gate.sh' "$ROOT_DIR/.githooks/pre-push" 2>/dev/null; then
  pass "pre-push hook 会触发 pre-push-gate.sh（14 道门禁）"
else
  fail "pre-push hook 未调用 pre-push-gate.sh — 门禁不会自动执行"
fi

# ── 4. scripts/git 包装器是否在 PATH 中有效 ──
GIT_PATH="$(command -v git 2>/dev/null || echo '')"
if [[ "$GIT_PATH" == *"/scripts/git" ]]; then
  pass "git 包装器生效: $GIT_PATH"
else
  fail "git 包装器未激活 (当前: ${GIT_PATH:-未找到}) — 运行 source scripts/dev-env.sh"
fi

# ── 5. pre-push-gate.sh 脚本存在 ──
if [ -f "$ROOT_DIR/scripts/pre-push-gate.sh" ] && [ -x "$ROOT_DIR/scripts/pre-push-gate.sh" ]; then
  pass "pre-push-gate.sh 存在（14 道门禁入口）"
else
  fail "scripts/pre-push-gate.sh 不存在或不可执行"
fi

# ── 6. 快速门禁检查: agent-locks 脚本是否存在 ──
if [ -f "$ROOT_DIR/scripts/check-agent-locks.mjs" ]; then
  pass "agent-locks 检查脚本存在"
else
  skip "check-agent-locks.mjs 未找到"
fi

echo ""
echo "─────────────────────────"
echo -e "通过: ${GREEN}${PASS}${NC}  失败: ${RED}${FAIL}${NC}  跳过: ${YELLOW}${SKIP}${NC}"
echo "─────────────────────────"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}部分门禁未就绪。修复后重新运行本脚本。${NC}"
  echo ""
  echo "快捷修复:"
  [ "$HOOKS_PATH" != ".githooks" ] && echo "  bash scripts/install-githooks.sh    # 安装 git hooks"
  [[ "$GIT_PATH" != *"/scripts/git" ]] && echo "  source scripts/dev-env.sh          # 激活 git 包装器"
  exit 1
else
  echo -e "${GREEN}本地门禁全部就绪，可以开始开发。${NC}"
  exit 0
fi
