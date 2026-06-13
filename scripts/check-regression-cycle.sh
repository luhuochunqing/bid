#!/usr/bin/env bash
# Input: none (reads git log)
# Output: regression cycle report or OK status
# Pos: scripts/ — 项目自动化检查套件
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。


# ============================================================
# check-regression-cycle.sh
#
# 根因分析触发检测 — 检测同一模块的连续修复模式
#
# 当同一模块/同一目录在最近 N 次提交中出现 ≥3 次 fix 类 commit，
# 说明可能存在"只修症状不治根因"的问题，建议先跑 investigation。
#
# 用于触发 CO-155 资质证书三连环故障式问题的预防机制。
#
# 用法：
#   bash scripts/check-regression-cycle.sh              # 检查当前分支
#   bash scripts/check-regression-cycle.sh --depth 10   # 检查最近 10 次提交
#   bash scripts/check-regression-cycle.sh --ci         # CI 模式（会失败）
#
# 退出码：
#   0 - 无连续修复模式
#   1 - 检测到可疑的连续修复模式
# ============================================================

set -euo pipefail

RED='[0;31m'
YELLOW='[1;33m'
GREEN='[0;32m'
CYAN='[0;36m'
NC='[0m'
BOLD='[1m'

CI_MODE=false
DEPTH=15
THRESHOLD=3  # 同一模块 fix 次数 ≥3 触发告警

for arg in "$@"; do
  case "$arg" in
    --ci) CI_MODE=true ;;
    --depth=*) DEPTH="${arg#*=}" ;;
    --threshold=*) THRESHOLD="${arg#*=}" ;;
  esac
done

echo -e "${BOLD}📊 连续修复模式检测${NC}"
echo "   分析深度: 最近 ${DEPTH} 次提交"
echo "   告警阈值: 同一模块 fix 次数 >= ${THRESHOLD}"
echo ""

# --- 收集最近 N 次提交中 fix 类 commit 的修改文件 ---
declare -A MODULE_FIX_COUNT
declare -A MODULE_FIX_COMMITS

while IFS= read -r commit_hash; do
  COMMIT_MSG=$(git log --format="%s" -1 "$commit_hash" 2>/dev/null || true)

  # 只关心 fix 类 commit
  echo "$COMMIT_MSG" | grep -qiE "fix|修复|hotfix|bug|补丁" || continue

  # 获取该 commit 修改的源文件（排除不关心类型）
  FILES=$(git diff --name-only "${commit_hash}~1..${commit_hash}" 2>/dev/null || true)
  if [ -z "$FILES" ]; then
    continue
  fi

  # 提取模块目录（取 src/main/java 或 src/components 等前两级）
  while IFS= read -r file; do
    case "$file" in
      src/main/java/*)
        # java: com/xiyu/bid/<module>/
        MODULE=$(echo "$file" | sed -n 's|src/main/java/com/xiyu/bid/\([^/]*\)/.*|\1|p')
        ;;
      src/components/*)
        # vue: 取 components 下第一级子目录
        MODULE=$(echo "$file" | sed -n 's|src/components/\([^/]*\)/.*|\1|p')
        # 如果直接在 components/ 下
        [ -z "$MODULE" ] && MODULE="components"
        ;;
      src/api/*)
        MODULE="api"
        ;;
      src/stores/*)
        MODULE="stores"
        ;;
      src/utils/*)
        MODULE="utils"
        ;;
      src/composables/*)
        MODULE="composables"
        ;;
      backend/src/main/java/com/xiyu/bid/*)
        # 后端的 backend/ 子目录模式
        MODULE=$(echo "$file" | sed -n 's|backend/src/main/java/com/xiyu/bid/\([^/]*\)/.*|\1|p')
        ;;
      *)
        # 其他文件跳过
        continue
        ;;
    esac

    [ -z "$MODULE" ] && continue

    MODULE_FIX_COUNT["$MODULE"]=$((MODULE_FIX_COUNT["$MODULE"] + 1))
    if [ -z "${MODULE_FIX_COMMITS["$MODULE"]:-}" ]; then
      MODULE_FIX_COMMITS["$MODULE"]="$commit_hash ($COMMIT_MSG)"
    else
      MODULE_FIX_COMMITS["$MODULE"]="${MODULE_FIX_COMMITS["$MODULE"]}"$'
'"$commit_hash ($COMMIT_MSG)"
    fi
  done <<< "$FILES"
done < <(git log --format="%H" -"$DEPTH" 2>/dev/null || true)

# --- 生成报告 ---
HAS_REGRESSION=false
REPORT=""

echo -e "${BOLD}模块 fix 频率统计:${NC}"
for module in "${!MODULE_FIX_COUNT[@]}"; do
  count=${MODULE_FIX_COUNT["$module"]}
  if [ "$count" -ge "$THRESHOLD" ]; then
    HAS_REGRESSION=true
    REPORT+="${RED}🔴 [${count}x] ${module}${NC}
"
    REPORT+="    提交记录:
"
    while IFS= read -r line; do
      REPORT+="    └─ ${line}
"
    done <<< "${MODULE_FIX_COMMITS["$module"]}"
    REPORT+="
"
  else
    echo -e "  ${GREEN}[${count}x]${NC} ${module}"
  fi
done

echo ""
if [ "$HAS_REGRESSION" = true ]; then
  echo -e "${RED}⚠️  检测到可疑的连续修复模式：${NC}"
  echo ""
  echo -e "$REPORT"
  echo -e "${YELLOW}💡 建议${NC}"
  echo "  此模块在短期内被频繁修复，可能只是每次都修表面症状。"
  echo "  建议在修改代码前先运行 investigation/root-cause 流程："
  echo "    1. 分析该模块最近的所有 fix commit"
  echo "    2. 找出共同根因而非每个问题独立修复"
  echo "    3. 编写覆盖根因的测试后再修改实现"
  echo ""
  echo -e "${CYAN}  可执行: ${BOLD}scripts/agent-start-task.sh codex root-cause-<module>${NC}"
  echo ""

  if [ "$CI_MODE" = true ]; then
    echo -e "${RED}CI 门禁: 检测到连续修复模式，请先做根因分析再推送${NC}"
    exit 1
  fi
  exit 1
else
  echo -e "${GREEN}[OK]${NC} 未检测到连续修复模式（同一模块 fix 次数 < ${THRESHOLD}）"
  exit 0
fi
