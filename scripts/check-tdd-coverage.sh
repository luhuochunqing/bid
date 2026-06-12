#!/usr/bin/env bash
# Input: none (reads git diff --name-only)
# Output: missing test file report or OK status
# Pos: scripts/ — 项目自动化检查套件
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。


# ============================================================
# check-tdd-coverage.sh
#
# TDD 先行检查 — 确保业务代码有对应的测试文件
#
# 在 pre-commit 阶段检测：如果新增/修改了业务逻辑文件，
# 但未同时新增/修改对应的测试文件，则给出告警（CI 模式下阻断）。
#
# 旨在改善 65% fix vs 11% test 的比例问题，
# 推动"先写红测再实现"的开发节奏。
#
# 用法：
#   bash scripts/check-tdd-coverage.sh               # 交互模式
#   bash scripts/check-tdd-coverage.sh --ci           # CI 模式（会失败）
#   bash scripts/check-tdd-coverage.sh --changed HEAD # 检查指定范围内的变更
#
# 退出码：
#   0 - 所有业务代码有对应测试（或跳过通过）
#   1 - 存在业务代码缺少测试
# ============================================================

set -euo pipefail

RED='[0;31m'
YELLOW='[1;33m'
GREEN='[0;32m'
NC='[0m'
BOLD='[1m'

CI_MODE=false
REV_RANGE="HEAD"

for arg in "$@"; do
  case "$arg" in
    --ci) CI_MODE=true ;;
    --changed) ;;
    --*) echo "Unknown: $arg" >&2; exit 1 ;;
    *) REV_RANGE="$arg" ;;
  esac
done

# --- 后端 Java 源文件 → 对应测试文件 ---
java_to_test() {
  local src="$1"
  # src/main/java/com/xiyu/bid/xxx/YYY.java → src/test/java/com/xiyu/bid/xxx/YYYTest.java
  local test_file="${src/src\/main\/java/src\/test\/java}"
  # 去掉 .java 后缀，加 Test.java
  test_file="${test_file%.java}Test.java"
  echo "$test_file"
}

# --- 前端 Vue/JS 源文件 → 对应测试文件 ---
frontend_to_test() {
  local src="$1"
  local dir
  local basename
  dir=$(dirname "$src")
  basename=$(basename "$src")

  # 组件名.spec.js 或 组件名.spec.vue 等
  # test 目录约定: src/components/Xxx.vue → src/components/Xxx.spec.js
  #               src/utils/Xxx.js → src/utils/Xxx.spec.js
  case "$basename" in
    *.vue)
      local name="${basename%.vue}"
      # 检查是否存在对应的 .spec.js 或 .spec.ts
      for ext in spec.js spec.ts spec.vue; do
        if [ -f "${dir}/${name}.${ext}" ]; then
          echo "${dir}/${name}.${ext}"
          return
        fi
      done
      # 默认路径
      echo "${dir}/${name}.spec.js"
      ;;
    *.js|*.ts)
      # 排除 .spec / .test 文件本身
      echo "$basename" | grep -qE '\.(spec|test)\.' && return 1
      local name="${basename%.*}"
      for ext in spec.js spec.ts; do
        if [ -f "${dir}/${name}.${ext}" ]; then
          echo "${dir}/${name}.${ext}"
          return
        fi
      done
      echo "${dir}/${name}.spec.js"
      ;;
  esac
}

# --- 获取变更文件 ---
CHANGED_FILES=$(git diff --name-only --diff-filter=AMR "${REV_RANGE}" 2>/dev/null || true)
if [ -z "$CHANGED_FILES" ]; then
  echo -e "${GREEN}[OK]${NC} 无变更文件"
  exit 0
fi

echo -e "${BOLD}📋 TDD 覆盖检查${NC}"
echo "   检查范围: ${REV_RANGE}"
echo ""

MISSING_TESTS=()
SKIPPED=0

while IFS= read -r file; do
  # 跳过非业务文件
  case "$file" in
    *.spec.*|*.test.*|*.md|*.css|*.json|*.yaml|*.yml|*.sh|*.sql|*.properties|*.xml) continue ;;
    src/main/java/*.java) ;;
    src/api/*.js|src/components/*.vue|src/composables/*.js|src/utils/*.js|src/stores/*.js) ;;
    src/api/*.ts|src/components/*.tsx|src/composables/*.ts|src/utils/*.ts|src/stores/*.ts) ;;
    *) SKIPPED=$((SKIPPED + 1)); continue ;;
  esac

  # 后端 Java
  if [[ "$file" == src/main/java/*.java ]]; then
    # 豁免不需要直接单测的纯配置类、数据结构、实体、工具常数或异常定义包
    if [[ "$file" =~ /(config|entity|model|dto|exception|constant|mapper|support|infrastructure)/ ]]; then
      SKIPPED=$((SKIPPED + 1))
      continue
    fi
    # 仅在 domain / core / policy / service / controller / listener 等关键业务逻辑包上强制推行
    TEST_FILE=$(java_to_test "$file")
    if [ ! -f "$TEST_FILE" ]; then
      MISSING_TESTS+=("$file  →  预期测试: $TEST_FILE（不存在）")
    fi
  fi

  # 前端 Vue/JS
  if [[ "$file" == src/components/*.vue ]] || [[ "$file" == src/utils/*.js ]] || [[ "$file" == src/stores/*.js ]] || [[ "$file" == src/composables/*.js ]]; then
    TEST_PATH=$(frontend_to_test "$file" 2>/dev/null || true)
    if [ -n "$TEST_PATH" ] && [ ! -f "$TEST_PATH" ]; then
      MISSING_TESTS+=("$file  →  预期测试: ${TEST_PATH}（不存在）")
    fi
  fi

done <<< "$CHANGED_FILES"

echo ""
if [ ${#MISSING_TESTS[@]} -eq 0 ]; then
  echo -e "${GREEN}[PASS]${NC} 所有业务代码有对应测试（或已跳过非业务文件）"
  exit 0
else
  echo -e "${YELLOW}[WARN]${NC} 以下业务代码缺少对应的测试文件："
  for mt in "${MISSING_TESTS[@]}"; do
    echo "  • $mt"
  done
  echo ""
  echo -e "${YELLOW}提示:${NC} 建议先编写测试再实现业务逻辑（TDD 模式）。"
  echo "  如果确实不需要测试（如纯配置变更），可忽略此告警。"

  if [ "$CI_MODE" = true ]; then
    echo -e "${RED}CI 门禁: 存在无测试的业务代码变更，请补充测试或标记豁免${NC}"
    exit 1
  fi
  exit 0
fi
