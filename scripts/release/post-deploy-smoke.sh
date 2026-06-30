#!/usr/bin/env bash
# Post-deploy smoke verification script (runs on the target server)
# Input: API base URL, smoke credentials, backend service name
# Output: smoke check results with pass/fail summary and CRM integration log regression scan
# Pos: scripts/release/ - Release automation and rehearsal helpers
# 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# 注意：端点路径必须与实际 Controller @RequestMapping 一致
set -euo pipefail

SMOKE_API_BASE_URL="${1:-${SMOKE_API_BASE_URL:-http://127.0.0.1:8080}}"
SMOKE_USERNAME="${2:-${SMOKE_USERNAME:-admin}}"
SMOKE_PASSWORD="${3:-${SMOKE_PASSWORD:-}}"
BACKEND_SERVICE_NAME="${4:-${BACKEND_SERVICE_NAME:-xiyu-bid-backend}}"

PASS=0
FAIL=0
WARN=0

pass() { echo "  ✅ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ❌ $1"; FAIL=$((FAIL + 1)); }
warn() { echo "  ⚠️  $1"; WARN=$((WARN + 1)); }
section() { echo ""; echo "━━━ $1 ━━━"; }

echo "============================================"
echo "  Post-Deploy Smoke Verification"
echo "  Target: $SMOKE_API_BASE_URL"
echo "  Time:   $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "============================================"

# ── P0: Health check ──
section "P0: Health"
HEALTH_BODY=$(curl -fsS --connect-timeout 10 "$SMOKE_API_BASE_URL/actuator/health" 2>/dev/null || true)
if echo "$HEALTH_BODY" | grep -q '"UP"'; then
  pass "Health check: UP"
else
  fail "Health check: NOT UP ($(echo "$HEALTH_BODY" | head -c 60))"
fi

# ── P1: Login ──
section "P1: Login"
TOKEN=""
LOGIN_LIMITED=false
if [[ -z "$SMOKE_PASSWORD" ]]; then
  warn "SMOKE_PASSWORD 未设置，跳过登录验活"
else
  LOGIN_BODY=$(curl -sS --connect-timeout 10 -X POST "$SMOKE_API_BASE_URL/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "$(printf '{"username":"%s","password":"%s"}' "$SMOKE_USERNAME" "$SMOKE_PASSWORD")" 2>/dev/null || true)

  if echo "$LOGIN_BODY" | grep -q '"success":true'; then
    TOKEN=$(echo "$LOGIN_BODY" | grep -o '"token":"[^"]*"' | cut -d\" -f4 || true)
    pass "Login: success (token obtained)"
  elif echo "$LOGIN_BODY" | grep -q "rate_limit_exceeded"; then
    LOGIN_LIMITED=true
    warn "Login: rate_limit_exceeded（限流，不影响业务，跳过本轮登录验活）"
  else
    LOGIN_MSG=$(echo "$LOGIN_BODY" | grep -o '"msg":"[^"]*"' | cut -d\" -f4 || echo "unknown error")
    fail "Login: $LOGIN_MSG"
  fi
fi

# ── P1: 关键业务端点 ──
section "P1: Business Endpoints"
if [[ -n "$TOKEN" ]]; then
  EP_LIST=(
    "/api/auth/me"
    "/api/tenders?page=0&size=1"
    "/api/projects?page=0&size=1"
    "/api/knowledge/qualifications?status=VALID&page=0&size=1"
    "/api/resources/expenses?page=0&size=1"
    "/api/cases?page=0&size=1"
    "/api/knowledge/templates?page=0&size=1"
    "/api/resources/bar-assets?page=0&size=1"
    "/api/analytics/overview"
  )

  for ep in "${EP_LIST[@]}"; do
    HTTP=$(curl -sS --connect-timeout 10 -o /dev/null -w '%{http_code}' \
      -H "Authorization: Bearer $TOKEN" \
      "$SMOKE_API_BASE_URL$ep" 2>/dev/null || echo 'FAIL')
    if [[ "$HTTP" == "200" || "$HTTP" == "403" ]]; then
      pass "GET $ep → $HTTP"
    else
      fail "GET $ep → $HTTP (unexpected, expected 200/403)"
    fi
  done
elif [[ "$LOGIN_LIMITED" == "true" ]]; then
  # 限流情况下用简单方法验证：404 或 401/403 说明业务正常
  warn "登录限流，降级为无 token 基础连通性检测"
  for ep in "/api/tenders?page=0&size=1" "/api/projects?page=0&size=1"; do
    HTTP=$(curl -sS --connect-timeout 10 -o /dev/null -w '%{http_code}' \
      "$SMOKE_API_BASE_URL$ep" 2>/dev/null || echo 'FAIL')
    if [[ "$HTTP" == "401" || "$HTTP" == "403" ]]; then
      pass "GET $ep → $HTTP（已降级，无 token 正确拒绝）"
    else
      warn "GET $ep → $HTTP（降级模式，无法判断）"
    fi
  done
else
  warn "无 token，跳过业务端点测试"
fi

# ── P1: 日志 schema 错误检测 ──
section "P1: Log Schema Check"
if command -v journalctl &>/dev/null; then
  LOGS=$(journalctl -u "$BACKEND_SERVICE_NAME" --no-pager -n 500 2>/dev/null || true)
  SCHEMA_ERRORS=0

  for pattern in \
    "Unknown column" \
    "Table.*doesn.t exist" \
    "Table.*not exist" \
    "Duplicate column name" \
    "Duplicate key name" \
    "doesn.t have a default value" \
    "cannot be null" \
    "Data truncated"; do
    if echo "$LOGS" | grep -qi "$pattern" 2>/dev/null; then
      MATCH=$(echo "$LOGS" | grep -i "$pattern" | tail -1 | head -c 120)
      fail "日志中发现 schema 错误: $pattern"
      echo "     → $MATCH"
      SCHEMA_ERRORS=$((SCHEMA_ERRORS + 1))
    fi
  done

  if [[ "$SCHEMA_ERRORS" -eq 0 ]]; then
    pass "日志中无 schema 错误"
  fi

  CRM_ERRORS=0
  for pattern in \
    "baseUrl=null" \
    "Cannot acquire CRM token" \
    "OSS token acquisition failed"; do
    if echo "$LOGS" | grep -qi "$pattern" 2>/dev/null; then
      MATCH=$(echo "$LOGS" | grep -i "$pattern" | tail -1 | head -c 120)
      fail "日志中发现 CRM 配置/token 回归: $pattern"
      echo "     → $MATCH"
      CRM_ERRORS=$((CRM_ERRORS + 1))
    fi
  done

  if [[ "$CRM_ERRORS" -eq 0 ]]; then
    pass "日志中无 CRM 配置/token 回归"
  fi
else
  warn "journalctl 不可用，跳过日志扫描"
fi

# ── P0: Headless 模式验证（CO-438 防复发）──
section "P0: Headless Mode (CO-438)"
if [[ -z "$BACKEND_SERVICE_NAME" ]]; then
  warn "BACKEND_SERVICE_NAME 未设置，跳过 headless 验证"
else
  # 检查 systemd ExecStart 是否包含 -Djava.awt.headless=true
  SERVICE_FILE="/etc/systemd/system/${BACKEND_SERVICE_NAME}.service"
  if [[ -f "$SERVICE_FILE" ]]; then
    if grep -q 'java\.awt\.headless=true' "$SERVICE_FILE" 2>/dev/null; then
      pass "systemd ExecStart 包含 -Djava.awt.headless=true"
    else
      fail "systemd ExecStart 缺少 -Djava.awt.headless=true（CO-438 根因）"
      echo "     → 参考模板: docs/release/systemd/xiyu-bid-backend.service"
    fi
  else
    warn "service 文件不存在 ($SERVICE_FILE)，跳过检查"
  fi

  # 检查运行中 JVM 进程是否实际带 headless 参数
  JVM_PID=$(systemctl show "$BACKEND_SERVICE_NAME" -p MainPID 2>/dev/null | cut -d= -f2)
  if [[ -n "$JVM_PID" && "$JVM_PID" -gt 0 ]]; then
    if tr '\0' ' ' < "/proc/$JVM_PID/cmdline" 2>/dev/null | grep -q 'java\.awt\.headless=true'; then
      pass "运行中 JVM 进程 (PID=$JVM_PID) 已启用 headless 模式"
    else
      fail "运行中 JVM 进程 (PID=$JVM_PID) 未启用 headless 模式"
      echo "     → 需要在 systemd ExecStart 添加 -Djava.awt.headless=true 并 restart"
    fi
  else
    warn "无法获取后端服务 PID，跳过运行时 headless 检查"
  fi

  # 检查 fontconfig 错误是否出现在日志中
  if command -v journalctl &>/dev/null; then
    FONTCONFIG_ERR=$(journalctl -u "$BACKEND_SERVICE_NAME" --no-pager -n 500 2>/dev/null \
      | grep -ci 'Fontconfig.*head.*null\|fontmanager.*null\|X11FontManager' || true)
    if [[ "$FONTCONFIG_ERR" -eq 0 ]]; then
      pass "日志中无 fontconfig head null 错误"
    else
      fail "日志中发现 fontconfig 错误（$FONTCONFIG_ERR 条），headless 未生效"
    fi
  fi
fi

# ── Summary ──
section "Summary"
TOTAL=$((PASS + FAIL + WARN))
echo ""
echo "  ✅ 通过: ${PASS}"
echo "  ❌ 失败: ${FAIL}"
echo "  ⚠️  警告: ${WARN}"
echo "  总计: ${TOTAL} 项"
echo ""

if [[ "$FAIL" -gt 0 ]]; then
  echo "结论: ❌ SMOKE FAILED — 需要人工介入"
  exit 1
elif [[ "$WARN" -gt 0 ]]; then
  echo "结论: ⚠️  SMOKE PASSED WITH WARNINGS"
else
  echo "结论: ✅ SMOKE PASSED"
fi
echo "============================================"
