#!/usr/bin/env bash
# Input: git status (all changes should be committed or stashed before running)
# Output: colored pass/fail report, exit 0 or 1
# Pos: scripts/ — Pre-PR local CI gate (GitHub CI equivalent, all local)
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# ci-pre-pr.sh — 提交 PR 前一站式本地门禁
# GitHub 账号被封后的替代方案：在本地完整模拟 CI 检测。
# 用法: bash scripts/ci-pre-pr.sh
# 退出码: 0 = 通过, 1 = 拦截
#
# 对比 GitHub CI `ci.yml`:
#   agent-locks     ✅  → pre-push-gate.sh
#   line-budget     ✅  → check-line-budgets.mjs
#   frontend        ✅  → test:unit + build:api + governance checks
#   backend         ✅  → ArchitectureTest + quality gates
#   quality-scope   ✅  → route-e2e-compat + e2e-ui-sync
#   e2e-scope       ✅  → check-route-e2e-compat.mjs + check-e2e-ui-sync.mjs
#   e2e             ⚠️  → 可选 (添加 --e2e 参数)
#   flyway-dryrun   ⚠️  → 可选 (添加 --flyway 参数，需本地 MySQL)

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0
RUN_E2E=false
RUN_FLYWAY=false

# Parse args
for arg in "$@"; do
  case "$arg" in
    --e2e) RUN_E2E=true ;;
    --flyway) RUN_FLYWAY=true ;;
    -h|--help)
      echo "Usage: bash scripts/ci-pre-pr.sh [--e2e] [--flyway]"
      echo "  --e2e     also run E2E Playwright tests (commercial flow)"
      echo "  --flyway  also run Flyway dry-run against local MySQL"
      exit 0
      ;;
  esac
done

pass() { echo -e "${GREEN}✓${NC} $1"; PASS=$((PASS + 1)); }
fail() { echo -e "${RED}✗${NC} $1"; FAIL=$((FAIL + 1)); }
warn() { echo -e "${YELLOW}⚠${NC} $1"; SKIP=$((SKIP + 1)); }

section() {
  echo ""
  echo "=== $1 ==="
}

echo "=========================================="
echo "  PR 提交前本地门禁  $(date '+%Y-%m-%d %H:%M:%S')"
echo "  环境: Node $(node -v) Java $(java -version 2>&1 | head -1 | cut -d' ' -f2-)"
echo "=========================================="

# ── Phase 1: Fast governance checks ──
section "前端治理检查"
if npm run check:front-data-boundaries --silent 2>/dev/null; then
  pass "front-data-boundaries"
else
  fail "front-data-boundaries"
fi

if npm run check:doc-governance --silent 2>/dev/null; then
  pass "doc-governance"
else
  fail "doc-governance"
fi

if npm run check:line-budgets --silent 2>/dev/null; then
  pass "line-budgets"
else
  fail "line-budgets"
fi

if npm run check:token-governance --silent 2>/dev/null; then
  pass "token-governance"
else
  fail "token-governance"
fi

# ── Phase 2: Frontend contract tests + unit tests + build ──
section "前端契约测试 + 单元测试 + 构建"
if npm run test:agent-start-task-contract --silent 2>/dev/null; then
  pass "test:agent-start-task-contract"
else
  fail "test:agent-start-task-contract — agent-start-task dry-run 契约输出回退"
fi

if npm run test:unit --silent 2>/dev/null; then
  pass "test:unit"
else
  fail "test:unit — 前端单元测试失败"
fi

if npm run build:api --silent 2>/dev/null; then
  pass "build:api"
else
  fail "build:api — 前端构建失败"
fi

# ── Phase 3: Lint check ──
section "Lint 检查"
if npm run lint --silent 2>/dev/null; then
  pass "eslint"
else
  fail "eslint — 有 lint 错误，运行 npm run lint:fix 修复"
fi

# ── Phase 4: Route-E2E compatibility ──
section "路由-E2E 兼容检查"
if node scripts/check-route-e2e-compat.mjs 2>/dev/null; then
  pass "check-route-e2e-compat"
else
  fail "check-route-e2e-compat — 路由变更与 E2E 播种不兼容"
fi

# ── Phase 5: E2E-UI 联动检查 ──
section "E2E-UI 联动检查"
if node scripts/check-e2e-ui-sync.mjs 2>/dev/null; then
  pass "check-e2e-ui-sync"
else
  fail "check-e2e-ui-sync — UI 变更但 E2E 测试未同步更新"
fi

# ── Phase 6: Pre-push quality gate ──
section "Pre-push 质量门禁"
if bash scripts/pre-push-gate.sh 2>/dev/null; then
  pass "pre-push-gate"
else
  fail "pre-push-gate — 详见上方错误"
fi

# ── Phase 7: Backend quality gates (lightweight) ──
section "后端质量门禁（编译 + Checkstyle + PMD）"
if [ -d "$ROOT_DIR/backend" ]; then
  cd "$ROOT_DIR/backend"
  if mvn -Pjava-quality -DskipTests -Djacoco.skip=true -q checkstyle:check 2>/dev/null; then
    pass "checkstyle:check"
  else
    fail "checkstyle:check — 有 Checkstyle 违规"
  fi
  if mvn -Pjava-quality -DskipTests -Djacoco.skip=true -q pmd:check 2>/dev/null; then
    pass "pmd:check"
  else
    fail "pmd:check — 有 PMD 违规"
  fi
  cd "$ROOT_DIR"
else
  warn "非 Java 项目，跳过后端质量门禁"
fi

# ── Phase 8: Optional E2E ──
if [ "$RUN_E2E" = true ]; then
  section "E2E 测试（可选）"
  echo "启动 Playwright E2E 测试..."
  if npx playwright test commercial-main-flow --config playwright.config.js 2>/dev/null; then
    pass "E2E commercial-main-flow"
  else
    fail "E2E commercial-main-flow — 测试失败，查看 playwright-report/ 了解详情"
  fi
fi

# ── Phase 9: Optional Flyway dry-run ──
if [ "$RUN_FLYWAY" = true ]; then
  section "Flyway 迁移预演（可选，需本地 MySQL）"
  echo "运行 Flyway 迁移 dry-run..."
  if [ -f "$ROOT_DIR/backend/pom.xml" ]; then
    cd "$ROOT_DIR/backend"
    if mvn org.flywaydb:flyway-maven-plugin:9.22.3:migrate \
      -Dflyway.url="jdbc:mysql://127.0.0.1:3306/xiyu_bid_local?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
      -Dflyway.user=root \
      -Dflyway.password="${DB_PASSWORD:-XiyuDB!2026}" \
      -Dflyway.locations=filesystem:src/main/resources/db/migration-mysql \
      -Dflyway.baselineOnMigrate=true \
      -Dflyway.validateOnMigrate=true -q 2>/dev/null; then
      pass "flyway-migrate:dry-run"
    else
      fail "flyway-migrate:dry-run — 迁移失败，检查 migration SQL"
    fi
    cd "$ROOT_DIR"
  else
    warn "非 Java 项目，跳过 Flyway"
  fi
fi

# ── Summary ──
echo ""
echo "=========================================="
echo -e "  通过: ${GREEN}${PASS}${NC}  失败: ${RED}${FAIL}${NC}  跳过: ${YELLOW}${SKIP}${NC}"
echo "=========================================="

if [ "$FAIL" -gt 0 ]; then
  echo ""
  echo -e "${RED}门禁未通过。修复以上 ${FAIL} 项后重试。${NC}"
  echo "  快速修复: npm run lint:fix  (自动修复 lint 问题)"
  echo "  跳过 E2E/Flyway: bash scripts/ci-pre-pr.sh  (默认跳过)"
  echo "  查看详情: npm run ci:local  (完整 CI 模拟)"
  exit 1
fi

echo -e "${GREEN}所有门禁通过，可以提交 PR！${NC}"
exit 0
