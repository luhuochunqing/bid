#!/usr/bin/env bash
# 067 剔除契约断言脚本（contracts/removal-contract.md 机器化）
# 退出码非 0 = 违约。例外项按契约"例外登记"显式豁免。
set -u
cd "$(git rev-parse --show-toplevel)" || exit 2
FAIL=0

# 检索域：仅前端源码 + e2e；排除文档域
SEARCH_DIRS="src e2e"
INC="--include=*.js --include=*.vue"

pass() { echo "  ✓ $1"; }
fail() { echo "  ✗ $1"; FAIL=1; }

expect_zero() { # $1=描述 $2=pattern $3=额外grep参数（可选）
  local hits
  hits=$(grep -rn $INC ${3:-} "$2" $SEARCH_DIRS 2>/dev/null | wc -l | tr -d ' ')
  if [ "$hits" = "0" ]; then pass "$1"; else
    fail "$1 (命中 $hits 行):"
    grep -rn $INC ${3:-} "$2" $SEARCH_DIRS 2>/dev/null | head -10 | sed 's/^/      /'
  fi
}

expect_absent() { if [ ! -e "$1" ]; then pass "不存在: $1"; else fail "仍存在: $1"; fi }
expect_present() { if [ -e "$1" ]; then pass "仍存在: $1"; else fail "被误删: $1"; fi }

echo "== Part I: 必须消失 =="

# 1. 被删路由地址（豁免：/api/** 后端接口路径、后端权限键、注释说明）
echo "-- 1. 被删路由地址 --"
for p in "resource/expense" "resource/contract-borrow" "resource/bid-result"; do
  expect_zero "路由地址 $p" "$p"
done
# customer-opportunities：仅允许 /api/customer-opportunities/* API 路径（后端接口保留）
hits=$(grep -rn $INC "customer-opportunities" $SEARCH_DIRS 2>/dev/null \
  | grep -v "/api/customer-opportunities" || true)
if [ -z "$hits" ]; then pass "路由地址 customer-opportunities（API 路径豁免后）"; else
  fail "路由地址 customer-opportunities 非法残留:"; echo "$hits" | head -10 | sed 's/^/      /'
fi
# knowledge/template：仅允许 /api/knowledge/templates* API 路径（knowledgeApi 现役）
hits=$(grep -rn $INC "knowledge/template" $SEARCH_DIRS 2>/dev/null \
  | grep -v "/api/knowledge/templates" || true)
if [ -z "$hits" ]; then pass "路由地址 knowledge/template（API 路径豁免后）"; else
  fail "路由地址 knowledge/template 非法残留:"; echo "$hits" | head -10 | sed 's/^/      /'
fi
# audit-logs：豁免后端操作日志 API、角色权限键 fixture、负向断言 spec、注释说明
hits=$(grep -rn $INC "audit-logs" $SEARCH_DIRS 2>/dev/null \
  | grep -v "src/api/modules/audit.js\|src/api/modules/resources/accounts.js\|src/api/modules/ca.js\|e2e/qualification-.*\.spec\.js\|api/qualifications\|api/tenders\|api/knowledge\|api/platform/accounts\|qualifications/\|权限清单\|Settings.spec.js\|MainLayout.spec.js\|// " || true)
if [ -z "$hits" ]; then pass "路由地址 audit-logs（白名单豁免后）"; else
  fail "路由地址 audit-logs 非法残留:"; echo "$hits" | head -10 | sed 's/^/      /'
fi
# resource/bar：豁免 MarketingTiming 的 /api/market-prediction 无关，仅查路由串
expect_zero "路由地址 resource/bar" "resource/bar"

# 2. 被删路由 name
echo "-- 2. 被删路由 name --"
expect_zero "路由 name 集合" "'CustomerOpportunityCenter'|'AuditLogs'|name: 'BAR'|'BAR_SiteList'|'BAR_SiteDetail'|'BAR_SOPDetail'|'Expense'|'ContractBorrow'|'BidResult'"

# 3. 被删文件路径符号
echo "-- 3. 被删文件符号 --"
for p in "views/Resource/BAR" "views/Resource/expense" "Resource/Expense.vue" "Resource/ContractBorrow" "views/Resource/BidResult" "views/Resource/bid-result" "views/Resource/Calendar" "AuditLogPage" "customer-opportunity/" "views/Knowledge/Template.vue" "Knowledge/components/template/" "views/Analytics/Dashboard.vue" "views/Analytics/CompetitionIntel" "views/Analytics/ScoreAnalysis" "views/Analytics/ROIAnalysis" "Bidding/Favorites" "Document/DocumentAssembly" "modules/contractBorrow" "modules/bidResults" "api/modules/templates.js"; do
  expect_zero "文件符号 $p" "$p"
done
# market-prediction：仅允许 /api/market-prediction/* 后端接口路径（MarketTiming 现役功能）
hits=$(grep -rn $INC "market-prediction" $SEARCH_DIRS 2>/dev/null | grep -v "/api/market-prediction" || true)
if [ -z "$hits" ]; then pass "文件符号 market-prediction（API 路径豁免后）"; else
  fail "文件符号 market-prediction 非法残留:"; echo "$hits" | head -10 | sed 's/^/      /'
fi

# 4. 隐藏机制与联动符号（豁免 spec 中的负向断言 toBeUndefined）
echo "-- 4. 机制/联动符号 --"
for p in "hiddenApiMenuNames" "isApiDeliveryMode" "goToResultPage" "contractBorrowApi" "bidResultsApi"; do
  hits=$(grep -rn $INC "$p" $SEARCH_DIRS 2>/dev/null | grep -v "toBeUndefined" || true)
  if [ -z "$hits" ]; then pass "符号 $p"; else
    fail "符号 $p (命中 $(echo "$hits" | wc -l | tr -d ' ') 行):"
    echo "$hits" | head -10 | sed 's/^/      /'
  fi
done

echo "-- 5. 文件不存在断言 --"
for f in src/views/Resource/BAR src/views/Resource/Expense.vue src/views/Resource/ContractBorrow.vue \
         src/views/Resource/BidResult.vue src/views/Resource/Calendar.vue src/views/System/AuditLogPage.vue \
         src/views/Bidding/CustomerOpportunityCenter.vue src/views/Bidding/customer-opportunity \
         src/views/Knowledge/Template.vue src/views/Knowledge/components/template \
         src/views/Bidding/Favorites.vue src/views/Bidding/market-prediction \
         src/views/Document/DocumentAssembly.vue src/api/modules/contractBorrow.js \
         src/api/modules/bidResults.js src/api/modules/templates.js \
         e2e/customer-opportunity-center.spec.js e2e/template-advanced-flow.spec.js e2e/template-three-dimensional-flow.spec.js; do
  expect_absent "$f"
done

echo ""
echo "== Part II: 必须存在（共享白名单）=="
for f in src/views/System/settings/AuditLogPanel.vue src/views/System/OperationLogPage.vue \
         src/api/modules/customerOpportunity.js src/components/ai/CompetitionIntel.vue \
         src/components/ai/ROIAnalysis.vue src/views/Document/useDocumentAssembly.js \
         src/api/modules/tenderFavorites.js src/views/Bidding/list/components/FavoriteButton.vue \
         src/views/AI/MarketTiming.vue; do
  expect_present "$f"
done

# 第二轮评审修复：这些曾列入白名单但评审后确认零引用，改为必须消失
for f in src/config/templateLibrary.js src/views/Dashboard/workbench-demo-data.js \
         src/api/modules/roi.js src/api/modules/scoreAnalysis.js src/api/modules/bidResults.normalizers.js \
         src/views/Resource/contractBorrow.css src/components/expense; do
  expect_absent "$f"
done

# 共享调用链
grep -q "knowledgeApi.templates" src/composables/projectDetail/useProjectDetailBoot.js \
  && pass "knowledgeApi.templates 调用链" || fail "knowledgeApi.templates 调用链断裂"
grep -q "createBorrow" src/views/Dashboard/useWorkbenchQuickStart.js \
  && pass "资质借阅 createBorrow 保留" || fail "资质借阅 createBorrow 丢失"
grep -q "convertToProject" src/views/Project/create/composables/useProjectCreateSubmit.js \
  && pass "商机转化 convertToProject 保留" || fail "商机转化 convertToProject 丢失"

echo ""
if [ "$FAIL" = "0" ]; then echo "✅ verify-removal: 全部断言通过"; exit 0
else echo "❌ verify-removal: 存在违约项"; exit 1; fi
