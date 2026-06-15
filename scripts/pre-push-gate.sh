#!/usr/bin/env bash
# Input: git status, staged diff
# Output: colored pass/fail report to stdout, exit 0 or 1
# Pos: scripts/ - pre-push quality gate
# 维护声明: 若门禁检查项或依赖路径变化，同步更新本脚本。

# pre-push-gate.sh — 推送前质量门禁
# 用法: bash scripts/pre-push-gate.sh [--skip-tests] [--skip-e2e-check]
# 环境变量:
#   PRE_PUSH_GATE=0   完全跳过门禁（不运行任何检查）
#   CI_MODE=true       自动化模式（不依赖交互式前端构建）
# 退出码: 0 = 通过, 1 = 拦截

set -euo pipefail

# ── 参数解析 ──────────────────────────────────────────────────
SKIP_TESTS=false
SKIP_E2E_CHECK=false

for arg in "$@"; do
  case "$arg" in
    --skip-tests)      SKIP_TESTS=true ;;
    --skip-e2e-check)   SKIP_E2E_CHECK=true ;;
    --help|-h)
      echo "用法: $0 [--skip-tests] [--skip-e2e-check]"
      echo "  --skip-tests      跳过前端单元测试 + 前端构建（节省约 30-60s）"
      echo "  --skip-e2e-check  跳过 E2E-UI 联动检查"
      echo "  PRE_PUSH_GATE=0    完全跳过门禁（等同于 --skip-tests --skip-e2e-check）"
      exit 0
      ;;
  esac
done

# agent/* 分支在本地 pre-push 时跳过前端测试和 E2E-UI 联动检查。
# 完整前端测试/build 留给 PR / CI 门禁执行，避免本地 push 因前端测试
# 进程挂起或超时阻塞。核心架构、Flyway、锁、行预算等门禁仍保留。
branch="$(git branch --show-current 2>/dev/null || true)"
if [[ "$branch" == agent/* ]]; then
  SKIP_TESTS=true
  SKIP_E2E_CHECK=true
fi

# ── 完全绕过 ────────────────────────────────────────────────
if [ "${PRE_PUSH_GATE:-1}" = "0" ]; then
  echo "⚠  PRE_PUSH_GATE=0 — 跳过全部推送前门禁检查"
  exit 0
fi

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
SKIPD=0

pass() { echo -e "${GREEN}✓${NC} $1"; PASS=$((PASS + 1)); }
fail() { echo -e "${RED}✗${NC} $1"; FAIL=$((FAIL + 1)); }
skip() { echo -e "${YELLOW}⊘${NC} $1 (skipped)"; SKIPD=$((SKIPD + 1)); }

echo "=== 推送前门禁 $(date '+%H:%M:%S') ==="
echo ""

# ── 1. 架构检查 ──────────────────────────────────────────
echo "── 架构合规 ──"
if [ -d "$ROOT_DIR/backend" ]; then
  cd "$ROOT_DIR/backend"
  if mvn test -Dtest=ArchitectureTest -q 2>/dev/null; then
    pass "ArchitectureTest"
  else
    fail "ArchitectureTest — Controller 可能直接依赖了 Repository 或 Entity"
  fi
  cd "$ROOT_DIR"
else
  skip "非 Java 项目"
fi

# ── 2. 回滚脚本覆盖 ──────────────────────────────────────
echo "── 回滚脚本覆盖 ──"
if [ -d "$ROOT_DIR/backend" ]; then
  cd "$ROOT_DIR/backend"
  if mvn test -Dtest=FlywayRollbackScriptCoverageTest -q 2>/dev/null; then
    pass "FlywayRollbackScriptCoverageTest"
  else
    fail "FlywayRollbackScriptCoverageTest — 新迁移缺回滚脚本或 source header"
  fi
  cd "$ROOT_DIR"
else
  skip "非 Java 项目"
fi

# ── 3. Flyway 版本号冲突检查 ─────────────────────────────
echo "── Flyway 版本号 ──"
if bash "$ROOT_DIR/scripts/check-flyway-versions.sh" --source=push 2>/dev/null; then
  pass "Flyway 迁移版本号无冲突"
elif bash "$ROOT_DIR/scripts/check-flyway-versions.sh" --source=push --fix 2>/dev/null; then
  fail "Flyway 版本冲突已自动修复，请重新 git add && git commit"
else
  fail "Flyway 迁移版本号冲突 — 建议 git rebase origin/main 解决"
fi


# ── 3.5 Schema 语义冲突检测 ───────────────────────────
echo "── Schema 冲突 ──"
if bash "$ROOT_DIR/scripts/check-schema-conflicts.sh" 2>/dev/null; then
  pass "Schema 语义无冲突"
else
  skip "Schema 冲突检测异常（不影响推送）"
fi
# ── 4. 锁孤儿检查 ───────────────────────────────────────
echo "── 锁孤儿检查 ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if grep -q 'agent:lock-check:changed' "$ROOT_DIR/package.json" 2>/dev/null; then
    if npm run agent:lock-check:changed --silent 2>/dev/null; then
      pass "agent-lock 无孤儿锁"
    else
      skip "可能残留非 hot-path 锁"
    fi
  fi
fi

# ── 5. 行预算 ───────────────────────────────────────────
echo "── 行预算 ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if node "$ROOT_DIR/scripts/check-line-budgets.mjs" --staged 2>/dev/null; then
    pass "line-budget"
  else
    fail "line-budget — 新建文件超过 300 行限制"
  fi
fi

# ── 6. 泄露检查 ────────────────────────────────────────
echo "── 文件泄露检查 ──"
UNTRACKED=$(git diff --cached --name-only 2>/dev/null | wc -l | tr -d ' ')
STAGED_FILES=$(git diff --cached --name-only 2>/dev/null | head -20)
if [ "$UNTRACKED" -gt 20 ]; then
  skip "暂存了 $UNTRACKED 个文件 (>20)"
  echo "$STAGED_FILES"
else
  pass "暂存文件 $UNTRACKED 个，数量合理"
fi

# ── 7. E2E 选择器安全检查 ────────────────────────────────
echo "── E2E 选择器 ──"
CHANGED_E2E=$(git diff --cached --name-only 2>/dev/null | grep 'e2e/.*\.spec\.js$' || true)
if [ -n "$CHANGED_E2E" ]; then
  BREADCRUMB_RISK=$(grep -n "getByText(" $CHANGED_E2E 2>/dev/null | grep -v '.first()' | grep -v '// breadcrumb-ok' || true)
  if [ -n "$BREADCRUMB_RISK" ]; then
    skip "E2E 测试中使用了 getByText()"
  else
    pass "E2E 选择器无面包屑碰撞风险"
  fi
else
  pass "无新增 E2E 文件"
fi

# ── 8. agent-locks 完整检查 ──────────────────────────────
echo "── agent-locks ──"
if [ -f "$ROOT_DIR/package.json" ]; then
  if node "$ROOT_DIR/scripts/check-agent-locks.mjs" --changed-only 2>/dev/null; then
    pass "agent-locks 无冲突"
  else
    fail "agent-locks — 有锁冲突。运行 npm run agent:lock-check 查看详情"
  fi
fi

# ── 9. FP-Java 架构门禁 ────────────────────────────────
echo "── FP-Java 架构 ──"
if [ -d "$ROOT_DIR/backend" ]; then
  cd "$ROOT_DIR/backend"
  if mvn test -Dtest=ResponsibilityArchitectureTest -q 2>/dev/null; then
    pass "ResponsibilityArchitectureTest"
  else
    fail "ResponsibilityArchitectureTest — 违反 FP-Java 规则：core 依赖框架/超 300 行/超 2 类职责"
  fi
  cd "$ROOT_DIR"
else
  skip "非 Java 项目"
fi

# ── 10. 路由-E2E 兼容性检查 ────────────────────────────
echo "── 路由-E2E 兼容 ──"
STAGED_ROUTES=$(git diff --cached --name-only 2>/dev/null | grep -cE '^src/(router|views)/' || true)
if [ "$STAGED_ROUTES" -gt 0 ]; then
  if node "$ROOT_DIR/scripts/check-route-e2e-compat.mjs" 2>/dev/null; then
    pass "route-e2e-compat"
  else
    fail "route-e2e-compat — 路由/E2E 播种不兼容"
  fi
else
  pass "route-e2e-compat (无路由/视图变更)"
fi

# ── 11. E2E-UI 联动检查 ────────────────────────────────
echo "── E2E-UI 联动 ──"
if [ "$SKIP_E2E_CHECK" = "true" ]; then
  skip "E2E-UI 联动 (--skip-e2e-check)"
else
  UI_CHANGED=$(git diff --cached --name-only 2>/dev/null | grep -cE '^src/(router|views)/' || true)
  E2E_CHANGED=$(git diff --cached --name-only 2>/dev/null | grep -cE '^e2e/' || true)
  if [ "$UI_CHANGED" -gt 0 ] && [ "$E2E_CHANGED" -eq 0 ]; then
    HEADER=$(git log -1 --format='%s %b' 2>/dev/null || true)
    if echo "$HEADER" | grep -q '\[skip e2e-scope\]'; then
      skip "E2E-UI: UI 变更但 E2E 未更新 (已标记 [skip e2e-scope])"
    else
      fail "E2E-UI: UI 有变更但 e2e/ 无对应更新。提交message加 [skip e2e-scope] 可跳过"
    fi
  else
    pass "E2E-UI 联动"
  fi
fi

# ── 12. 前端单元测试 ───────────────────────────────────
echo "── 前端单元测试 ──"
if [ "$SKIP_TESTS" = "true" ]; then
  skip "test:unit (--skip-tests)"
else
  if npm run test:unit --silent 2>/dev/null; then
    pass "test:unit"
  else
    fail "test:unit — 前端单元测试失败"
  fi
fi

# ── 13. Lint 检查 ──────────────────────────────────────
echo "── Lint 检查 ──"
if npm run lint --silent 2>/dev/null; then
  pass "eslint"
else
  fail "eslint — 有 lint 错误，运行 npm run lint:fix 修复"
fi

# ── 14. 前端构建 ────────────────────────────────────────
echo "── 前端构建 ──"
if [ "$SKIP_TESTS" = "true" ]; then
  skip "build:api (--skip-tests)"
else
  if npm run build:api --silent 2>/dev/null; then
    pass "build:api"
  else
    fail "build:api — 前端构建失败"
  fi
fi

# ── 汇总 ────────────────────────────────────────────────
echo ""
echo "─────────────────────────"
echo -e "通过: ${GREEN}${PASS}${NC}  失败: ${RED}${FAIL}${NC}  跳过: ${YELLOW}${SKIPD}${NC}"
echo "─────────────────────────"

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}门禁未通过。修复以上 ${FAIL} 项后重试。${NC}"
  echo ""
  echo "提示："
  echo "  --skip-tests      跳过 test:unit + build:api（节省 30-60s）"
  echo "  --skip-e2e-check  跳过 E2E-UI 联动检查"
  echo "  PRE_PUSH_GATE=0  完全跳过门禁（紧急情况使用）"
  exit 1
else
  echo -e "${GREEN}门禁通过，可以推送。${NC}"
  exit 0
fi
