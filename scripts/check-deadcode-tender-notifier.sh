#!/usr/bin/env bash
# ============================================================
# 检查脚本：TenderPendingAssignmentNotifier 死代码确认
#
# 用途：确认 TenderPendingAssignmentNotifier 是否真的没有任何调用方
# 结论：如果输出只有类定义自身（1 处），则为死代码
#
# Input: $1 后端源码目录（默认 backend/src）
# Output: 类名/方法名/注入引用计数 + 死代码判定结论（stdout）
# Pos: scripts/ - 死代码检查工具
# 维护声明: 若改动本脚本，请同步更新本 header 注释与 scripts/README.md
# ============================================================
set -euo pipefail

BACKEND_DIR="${1:-backend/src}"

echo "=========================================="
echo "TenderPendingAssignmentNotifier 死代码检查"
echo "搜索范围: $BACKEND_DIR"
echo "=========================================="
echo ""

# 检查类名引用
echo "[1/3] 搜索类名 TenderPendingAssignmentNotifier ..."
CLASS_HITS=$(grep -rn "TenderPendingAssignmentNotifier" "$BACKEND_DIR" || true)
CLASS_COUNT=$(echo "$CLASS_HITS" | grep -c "." || echo "0")
echo "$CLASS_HITS"
echo ""
echo "  类名引用数: $CLASS_COUNT"

# 检查方法名引用
echo ""
echo "[2/3] 搜索方法名 notifyPendingAssignment ..."
METHOD_HITS=$(grep -rn "notifyPendingAssignment" "$BACKEND_DIR" || true)
METHOD_COUNT=$(echo "$METHOD_HITS" | grep -c "." || echo "0")
echo "$METHOD_HITS"
echo ""
echo "  方法名引用数: $METHOD_COUNT"

# 检查 Spring 注入（@Autowired / 构造函数 / @RequiredArgsConstructor）
echo ""
echo "[3/3] 搜索 Spring 注入模式 ..."
INJECT_HITS=$(grep -rn "TenderPendingAssignmentNotifier\|notifyPendingAssignment" "$BACKEND_DIR" \
  | grep -v "^.*\.java:.*class TenderPendingAssignmentNotifier" \
  | grep -v "^.*\.java:.*public void notifyPendingAssignment" \
  | grep -v "^.*\.java:.*//.*" \
  | grep -v "^.*\.java:.*\*" \
  || true)
INJECT_COUNT=$(echo "$INJECT_HITS" | grep -c "." || echo "0")
if [ "$INJECT_COUNT" -gt 0 ]; then
  echo "  发现外部引用:"
  echo "$INJECT_HITS"
else
  echo "  未发现任何外部引用"
fi

# 结论
echo ""
echo "=========================================="
if [ "$CLASS_COUNT" -le 1 ] && [ "$METHOD_COUNT" -le 1 ]; then
  echo "结论: ✅ 确认是死代码"
  echo "  - 类名仅出现在自身定义处"
  echo "  - 方法名仅出现在自身定义处"
  echo "  - 无任何 Spring 注入或方法调用"
  echo ""
  echo "建议:"
  echo "  1. 确认是否是未完成的功能（标讯待分配通知）"
  echo "  2. 如果是计划中的功能，保留并添加 TODO 注释"
  echo "  3. 如果是废弃代码，可安全删除"
else
  echo "结论: ⚠ 存在外部引用，不是死代码"
  echo "  请检查上方输出确认引用方"
fi
echo "=========================================="
