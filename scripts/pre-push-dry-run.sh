#!/usr/bin/env bash
# Input: current workspace (no args needed)
# Output: runs the SAME 14+ gates that CI runs on push
# Pos: scripts/ — Pre-push 完整 CI 模拟 (2026-06-05 skill-progression-map)
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Background (基于 182 PR / 791 commits 全量分析):
#   CI 基础设施是最大痛点（45 次 CI 相关提交、17 个纯 CI 修复 PR）。
#   本脚本模拟 CI 的全部门禁，让 Agent 在推送前发现并修复问题，
#   避免 CI 失败→修复→重推的循环。
#
# Usage:
#   ./scripts/pre-push-dry-run.sh              # 全量检查（耗时长）
#   ./scripts/pre-push-dry-run.sh --frontend   # 前端快速检查
#   ./scripts/pre-push-dry-run.sh --backend    # 后端快速检查
#   ./scripts/pre-push-dry-run.sh --quick      # 编译+架构 (< 2min)
#
# Exit codes:
#   0 = all checks passed
#   1 = one or more checks failed

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

script_name="$(basename "$0")"
PASS=0; FAIL=1; SKIP=2
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'
pass() { printf "[${GREEN}PASS${NC}] %s\n" "$1"; }
fail() { printf "[${RED}FAIL${NC}] %s\n" "$1"; failures=$((failures + 1)); }
info() { printf "[INFO] %s\n" "$1"; }
warn() { printf "[${YELLOW}WARN${NC}] %s\n" "$1"; }

failures=0
run_frontend=true
run_backend=true
quick_mode=false

for arg in "$@"; do
  case "$arg" in
    --frontend) run_backend=false ;;
    --backend)  run_frontend=false ;;
    --quick)    quick_mode=true ;;
  esac
done

echo ""
echo "============================================"
echo "  CI 门禁本地模拟（14 道门禁全量检查）"
echo "  $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"
echo ""

# ====== 门禁 1: agent-locks 冲突 ======
if [ "$quick_mode" = false ]; then
  info "[01/14] agent-locks 冲突检查..."
  if node scripts/check-agent-locks.mjs --changed-only 2>&1; then
    pass "agent-locks 无冲突"
  else
    fail "agent-locks 冲突"
  fi
else
  echo "[SKIP] agent-locks 检查 (快速模式)"
fi

# ====== 门禁 2: line-budget ======
info "[02/14] 行数预算检查..."
if node scripts/check-line-budgets.mjs --staged 2>&1; then
  pass "line-budget"
else
  fail "line-budget"
fi

# ====== 门禁 3: 令牌治理 ======
if [ "$quick_mode" = false ]; then
  info "[03/14] 令牌治理检查..."
  if npm --prefix "$ROOT_DIR" run check:token-governance 2>&1 | tail -5; then
    pass "令牌治理"
  else
    fail "令牌治理"
  fi
else
  echo "[SKIP] 令牌治理 (快速模式)"
fi

# ====== 门禁 4: 前端数据边界 ======
if [ "$run_frontend" = true ] && [ "$quick_mode" = false ]; then
  info "[04/14] 数据边界检查..."
  if node scripts/check-front-data-boundaries.mjs 2>&1; then
    pass "数据边界"
  else
    fail "数据边界"
  fi
fi

# ====== 门禁 5: 文档治理 ======
info "[05/14] 文档治理检查..."
if npm --prefix "$ROOT_DIR" run check:doc-governance 2>&1; then
  pass "文档治理"
else
  fail "文档治理"
fi

# ====== 门禁 6: ESLint ======
if [ "$run_frontend" = true ]; then
  info "[06/14] ESLint 检查..."
  if npm run lint 2>&1 | tail -5; then
    pass "ESLint"
  else
    fail "ESLint"
  fi
fi

# ====== 门禁 7: 前端单元测试 ======
if [ "$run_frontend" = true ] && [ "$quick_mode" = false ]; then
  info "[07/14] 前端单元测试..."
  if npm run test:unit 2>&1 | tail -5; then
    pass "Vitest 单元测试"
  else
    fail "Vitest 单元测试"
  fi
fi

# ====== 门禁 8: 前端构建 ======
if [ "$run_frontend" = true ]; then
  info "[08/14] 前端构建 (VITE_API_MODE=api)..."
  if VITE_API_MODE=api npx vite build 2>&1 | tail -5; then
    pass "前端构建"
  else
    fail "前端构建"
  fi
fi

# ====== 门禁 9: E2E 选择器 ======
if [ "$quick_mode" = false ]; then
  info "[09/14] E2E 选择器检查..."
  if node scripts/check-e2e-selectors.mjs 2>&1; then
    pass "E2E 选择器"
  else
    warn "E2E 选择器 (存在警告但不阻断提交)"
  fi
fi

# ====== 门禁 10: E2E-UI 联动 ======
if [ "$quick_mode" = false ]; then
  info "[10/14] E2E-UI 联动检查..."
  if node scripts/check-e2e-ui-sync.mjs 2>&1; then
    pass "E2E-UI 联动"
  fi
fi

# ====== 门禁 11: 路由兼容 ======
if [ "$quick_mode" = false ]; then
  if git diff --cached --name-only | grep -qE '^src/(router|views)/'; then
    info "[11/14] 路由-E2E 兼容检查..."
    if node scripts/check-route-e2e-compat.mjs 2>&1; then
      pass "路由兼容"
    else
      fail "路由兼容"
    fi
  fi
fi

# ====== 后端门禁 ======
if [ "$run_backend" = true ]; then
  echo ""
  info "--- 后端门禁 ---"

  # 门禁 12: Maven 编译
  info "[12/14] Maven 编译..."
  if (cd backend && mvn compile -q 2>&1 | tail -5); then
    pass "Maven 编译"
  else
    fail "Maven 编译"
  fi

  # 门禁 13: 架构测试
  info "[13/14] 架构测试（ArchitectureTest + FPJava + Responsibility + Guard）..."
  if (cd backend && mvn test -Dtest="ArchitectureTest,FPJavaArchitectureTest,ResponsibilityArchitectureTest,ProjectAccessGuardCoverageTest" -q 2>&1 | tail -10); then
    pass "架构测试"
  else
    fail "架构测试"
  fi

  # 门禁 14: Flyway 检查（版本 + 回滚 + 头部质量）
  info "[14/14] Flyway 完整性检查..."
  flyway_errors=0
  if [ -n "$(git diff --cached --name-only --diff-filter=ACMR | grep 'migration-mysql/' || true)" ]; then
    if ! bash scripts/check-flyway-versions.sh --staged 2>&1; then
      fail "Flyway 版本冲突"
      flyway_errors=1
    fi
    if ! bash scripts/check-migration-headers.sh 2>&1; then
      fail "Flyway 头部质量"
      flyway_errors=1
    fi
    if ! bash scripts/check-flyway-rollback.sh 2>&1; then
      fail "Flyway 回滚完整性"
      flyway_errors=1
    fi
    if [ "$flyway_errors" -eq 0 ]; then
      pass "Flyway 完整性检查"
    fi
  else
    pass "Flyway 完整性检查 (无变更)"
  fi

  cd "$ROOT_DIR"
fi

# ====== 总结 ======
echo ""
echo "============================================"
echo "  结果"
echo "============================================"
if [ "$failures" -eq 0 ]; then
  printf "[${GREEN}ALL PASS${NC}] 所有门禁通过 — 可以推送。\n"
  echo "  运行: git push origin HEAD"
  echo ""
  echo "  参考: cat scripts/ci-scenario-book.md（CI 故障模式知识库）"
  exit 0
else
  echo ""
  printf "[${RED}${failures} FAIL${NC}] 门禁未通过 — 修复后重试。\n"
  echo ""
  echo "  CI 故障模式参考: cat scripts/ci-scenario-book.md"
  echo "  快速恢复命令:"
  echo "    npm run agent:lock-cleanup       # 清理过期锁"
  echo "    ./scripts/check-flyway-versions.sh --staged  # Flyway 版本"
  echo "    ./scripts/check-flyway-rollback.sh            # Flyway 回滚"
  exit 1
fi
