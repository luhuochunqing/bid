#!/usr/bin/env bash
# ============================================================
# 验证脚本：PR !2267 上线后验证 — 确认 admin 被排除、/bidAdmin 收到通知
#
# 用法：
#   bash scripts/verify-notification-recipient-excludes-admin.sh [BASE_URL] [ADMIN_USER] [ADMIN_PASS]
#
# 默认值：
#   BASE_URL=http://127.0.0.1:18089
#   ADMIN_USER=bid_admin       （/bidAdmin 角色，应收到通知）
#   ADMIN_PASS=Test@123
#
# 前置条件：
#   1. 后端已启动（主工作区 trae 的 18089 端口）
#   2. 数据库中有至少一个处于评估阶段的项目
#   3. bid_admin 账号已存在（LocalDevAccountInitializer 创建）
# ============================================================
set -euo pipefail

BASE_URL="${1:-http://127.0.0.1:18089}"
ADMIN_USER="${2:-bid_admin}"
ADMIN_PASS="${3:-Test@123}"

echo "=========================================="
echo "PR !2267 上线后验证脚本"
echo "目标: $BASE_URL"
echo "登录用户: $ADMIN_USER (/bidAdmin 角色)"
echo "=========================================="
echo ""

# ── Step 1: 登录获取 token ──
echo "[1/5] 登录 $ADMIN_USER ..."
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('token',''))" 2>/dev/null || echo "")

if [ -z "$TOKEN" ]; then
  echo "  ✗ 登录失败，响应: $LOGIN_RESP"
  exit 1
fi
echo "  ✓ 登录成功"

# ── Step 2: 查询当前用户角色，确认是 /bidAdmin ──
echo ""
echo "[2/5] 确认当前用户角色 ..."
ME_RESP=$(curl -s "$BASE_URL/api/auth/me" \
  -H "Authorization: Bearer $TOKEN")
ROLE_CODE=$(echo "$ME_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('roleCode',''))" 2>/dev/null || echo "")
echo "  当前用户角色: $ROLE_CODE"
if [ "$ROLE_CODE" != "/bidAdmin" ] && [ "$ROLE_CODE" != "bid-SystemAdmin" ] && [ "$ROLE_CODE" != "bid-TeamLeader" ]; then
  echo "  ⚠ 警告: 用户角色不是 /bidAdmin / bid-SystemAdmin / bid-TeamLeader，可能无法验证"
fi

# ── Step 3: 查询通知列表，记录当前未读数 ──
echo ""
echo "[3/5] 查询当前未读通知数 ..."
NOTIF_BEFORE=$(curl -s "$BASE_URL/api/notifications?status=unread&page=0&size=1" \
  -H "Authorization: Bearer $TOKEN")
UNREAD_BEFORE=$(echo "$NOTIF_BEFORE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('totalElements',0))" 2>/dev/null || echo "0")
echo "  当前未读通知数: $UNREAD_BEFORE"

# ── Step 4: 查询可用项目列表 ──
echo ""
echo "[4/5] 查询可用项目 ..."
PROJECTS_RESP=$(curl -s "$BASE_URL/api/projects?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN")
PROJECT_ID=$(echo "$PROJECTS_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin).get('data', {})
items = data.get('content', data.get('items', []))
for p in items:
    stage = p.get('stage', p.get('currentStage', ''))
    if stage in ('EVALUATION', 'RESULT_PENDING', 'RETROSPECTIVE'):
        print(p.get('id', ''))
        break
" 2>/dev/null || echo "")

if [ -z "$PROJECT_ID" ]; then
  echo "  ⚠ 未找到评估阶段的项目，尝试用第一个项目 ..."
  PROJECT_ID=$(echo "$PROJECTS_RESP" | python3 -c "
import sys, json
data = json.load(sys.stdin).get('data', {})
items = data.get('content', data.get('items', []))
if items:
    print(items[0].get('id', ''))
" 2>/dev/null || echo "")
fi

if [ -z "$PROJECT_ID" ]; then
  echo "  ✗ 未找到可用项目，请先创建一个项目"
  exit 1
fi
echo "  使用项目 ID: $PROJECT_ID"

# ── Step 5: 触发弃标通知 ──
echo ""
echo "[5/5] 触发弃标通知 ..."
ABANDON_RESP=$(curl -s -X POST "$BASE_URL/api/projects/$PROJECT_ID/evaluation/abandon" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"PR !2267 上线验证 — 测试 admin 排除后 /bidAdmin 是否收到通知"}')

echo "  弃标响应: $(echo "$ABANDON_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('message','?'))" 2>/dev/null || echo "$ABANDON_RESP" | head -c 200)"

# ── 等待通知创建 ──
echo ""
echo "等待 2 秒通知创建 ..."
sleep 2

# ── 查询通知列表 ──
NOTIF_AFTER=$(curl -s "$BASE_URL/api/notifications?status=unread&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN")
UNREAD_AFTER=$(echo "$NOTIF_AFTER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('totalElements',0))" 2>/dev/null || echo "0")
echo "  当前未读通知数: $UNREAD_AFTER"

# ── 检查是否有新通知 ──
NEW_COUNT=$((UNREAD_AFTER - UNREAD_BEFORE))
echo ""
if [ "$NEW_COUNT" -gt 0 ]; then
  echo "  ✓ /bidAdmin 用户收到了 $NEW_COUNT 条新通知"
  echo ""
  echo "  最近通知内容:"
  echo "$NOTIF_AFTER" | python3 -c "
import sys, json
data = json.load(sys.stdin).get('data', {})
items = data.get('content', data.get('items', []))
for n in items[:5]:
    title = n.get('title', '?')
    created = n.get('createdAt', n.get('created_at', '?'))
    print(f'    [{created}] {title}')
" 2>/dev/null || echo "    (解析失败)"
else
  echo "  ✗ /bidAdmin 用户未收到新通知（可能该用户不在项目成员中，或通知已读）"
fi

# ── 附加验证：检查 admin 用户是否被排除 ──
echo ""
echo "=========================================="
echo "附加验证：admin 用户通知排除检查"
echo "=========================================="
echo ""
echo "需手动确认：登录 admin 账号，检查是否收到了上述弃标通知。"
echo "如果 admin 未收到通知 → 修复生效 ✓"
echo "如果 admin 收到了通知 → 修复未生效 ✗"
echo ""
echo "验证命令（需 admin 凭据）:"
echo "  curl -s '$BASE_URL/api/notifications?status=unread&page=0&size=5' \\"
echo "    -H 'Authorization: Bearer <admin_token>' | python3 -m json.tool"
echo ""
echo "=========================================="
echo "验证完成"
echo "=========================================="
