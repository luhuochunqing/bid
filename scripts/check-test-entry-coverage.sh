#!/usr/bin/env bash
# Input: pre-push-gate.sh 的 mvn test -Dtest=... 列表 + backend/src/test/**/*Test.java
# Output: 验证测试列表中的测试类文件存在，以及 guard-pattern 测试是否在列表中
# Pos: scripts/ — 测试入口覆盖扫描（test entry coverage scanner）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# Rule (hard block):
#   pre-push-gate.sh 中 mvn test -Dtest=... 列出的每个测试类必须有对应的 Java 文件。
#   如果文件不存在 → 硬阻断（push 时会静默失败，门禁假绿）。
# Rule (soft warn):
#   backend/src/test/ 中匹配 guard-pattern 的测试类（*CoverageTest, *ArchitectureTest, *GuardTest）
#   如果不在 pre-push-gate.sh 的测试列表中 → 警告（可能是遗漏）。
#
# 工程背景（2026-07-04 P2 事故）：
#   PR !1655 新增 EntityTableMigrationCoverageTest 但未加入 pre-push-gate.sh 的 mvn test 列表，
#   导致 L2 守卫（@Table 实体建表迁移覆盖）在 pre-push 阶段不运行，只在 CI 完整 mvn test 时生效。
#   PR !1657 修复。
#
# 贝叶斯边界声明（Bayesian boundary）：
#   本脚本的似然比 ≈ 4.67（能检测"测试类是否存在 + guard-pattern 是否在列表"）。
#   盲区：
#     - 检测不到"测试类在列表中但测试本身写错了"（断言写反、mock 配错、空测试方法）
#     - 检测不到"测试类在列表中但 surefire XML 解析逻辑有 bug"
#     - 检测不到"测试类在列表中但 BACKEND_CHANGED=0 时被 skip"
#     - 检测不到"测试类不在列表但也不匹配 guard-pattern 的科学遗漏"
#     - guard-pattern 匹配是启发式的（`*CoverageTest`/`*ArchitectureTest`/`*GuardTest`），
#       可能漏掉不匹配这些命名模式的新类型守卫测试
#   这些盲区需要外部补位（adversarial review、人类 code review、CI 完整 mvn test）。
#
# 逃生阀：GUARD_ALLOW_TEST_ENTRY_SKIP=1 可绕过（仅在确认是已知盲区时使用）。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '')"
if [ -z "$ROOT_DIR" ]; then
  echo "test-entry-coverage: not in a git repo, skipping."
  exit 0
fi
cd "$ROOT_DIR"

script_name="$(basename "$0")"

red()   { printf '\033[31m%s\033[0m\n' "$*" >&2; }
green() { printf '\033[32m%s\033[0m\n' "$*" >&2; }
yellow(){ printf '\033[33m%s\033[0m\n' "$*" >&2; }

# 逃生阀
if [ "${GUARD_ALLOW_TEST_ENTRY_SKIP:-0}" = "1" ]; then
  yellow "${script_name}: GUARD_ALLOW_TEST_ENTRY_SKIP=1 set, skipping."
  exit 0
fi

GATE_FILE="$ROOT_DIR/scripts/pre-push-gate.sh"
TEST_DIR="$ROOT_DIR/backend/src/test"

if [ ! -f "$GATE_FILE" ]; then
  echo "${script_name}: pre-push-gate.sh not found, skipping."
  exit 0
fi

if [ ! -d "$TEST_DIR" ]; then
  echo "${script_name}: no backend/src/test/ directory, skipping."
  exit 0
fi

# ── 检查 1: 解析 pre-push-gate.sh 中的测试列表，验证文件存在 ──
# 从 mvn test -Dtest='...' 提取逗号分隔的测试类名列表
TEST_LIST=$(sed -n "s/.*mvn test -Dtest='\([^']*\)'.*/\1/p" "$GATE_FILE" 2>/dev/null | head -1 || true)

if [ -z "$TEST_LIST" ]; then
  echo "${script_name}: no mvn test -Dtest=... found in pre-push-gate.sh, skipping."
  exit 0
fi

HARD_FAILS=0
SOFT_WARNS=0

echo ""
echo "── 测试列表存在性检查 ──"

# 将逗号分隔的列表转为逐个检查
IFS=',' read -ra TEST_CLASSES <<< "$TEST_LIST"
for test_class in "${TEST_CLASSES[@]}"; do
  test_class=$(echo "$test_class" | xargs)  # trim whitespace
  if [ -z "$test_class" ]; then
    continue
  fi

  # 查找对应的 Java 文件（支持子包路径）
  test_file=$(find "$TEST_DIR" -name "${test_class}.java" -print -quit 2>/dev/null || true)

  if [ -z "$test_file" ]; then
    red "  ✗ ${test_class} — 在 pre-push 测试列表中但文件不存在"
    HARD_FAILS=$((HARD_FAILS + 1))
  else
    green "  ✓ ${test_class}"
  fi
done

# ── 检查 2: guard-pattern 测试是否在列表中 ──
echo ""
echo "── Guard-pattern 测试覆盖扫描 ──"

# 查找匹配 guard pattern 的测试类
GUARD_PATTERN_TESTS=$(find "$TEST_DIR" -type f \( \
  -name "*CoverageTest.java" -o \
  -name "*ArchitectureTest.java" -o \
  -name "*GuardTest.java" \
\) 2>/dev/null | sort || true)

if [ -z "$GUARD_PATTERN_TESTS" ]; then
  echo "  no guard-pattern tests found, skipping."
else
  GUARD_EXEMPTIONS="FPJavaArchitectureTest MaintainabilityArchitectureTest"
  # 注意：FPJavaArchitectureTest 和 MaintainabilityArchitectureTest 是 ArchUnit 导入测试，
  # 已在 CI 完整 mvn test 中运行但不在 pre-push 中，属合理豁免。

  while IFS= read -r test_path; do
    class_name=$(basename "$test_path" .java)

    # 检查是否在豁免列表中
    if echo "$GUARD_EXEMPTIONS" | grep -qw "$class_name"; then
      continue
    fi

    # 检查是否在 pre-push 测试列表中
    if echo "$TEST_LIST" | grep -qw "$class_name"; then
      green "  ✓ ${class_name} (in pre-push list)"
    else
      yellow "  ⚠ ${class_name} — guard-pattern 测试但不在 pre-push 测试列表中"
      SOFT_WARNS=$((SOFT_WARNS + 1))
    fi
  done <<< "$GUARD_PATTERN_TESTS"
fi

echo ""

# ── 结果 ──
if [ "$HARD_FAILS" -gt 0 ]; then
  red ""
  red "❌ ${script_name}: ${HARD_FAILS} 个测试类在 pre-push 列表中但文件不存在"
  red ""
  red "这会导致 push 时 mvn test 静默失败，门禁假绿。"
  red "修复：如果测试类已删除，从 pre-push-gate.sh 的 mvn test -Dtest=... 列表中移除它。"
  red "     如果测试类已重命名，更新列表中的名称。"
  red ""
  red "逃生阀：GUARD_ALLOW_TEST_ENTRY_SKIP=1 可绕过。"
  exit 1
fi

if [ "$SOFT_WARNS" -gt 0 ]; then
  yellow ""
  yellow "⚠ ${script_name}: ${SOFT_WARNS} 个 guard-pattern 测试不在 pre-push 列表中（不阻断）"
  yellow ""
  yellow "这些测试匹配 guard-pattern（*CoverageTest/*ArchitectureTest/*GuardTest），"
  yellow "但不在 pre-push-gate.sh 的 mvn test 列表中。"
  yellow "如果它们是新增的守卫测试，请考虑加入 pre-push 列表以在 push 阶段拦截。"
  yellow "如果是有意豁免（如仅 CI 运行），请在 GUARD_EXEMPTIONS 中标注。"
  yellow ""
  yellow "历史事故：2026-07-04 EntityTableMigrationCoverageTest 未加入 pre-push 列表。"
  yellow "详见 PR !1657。"
  yellow ""
fi

green "✓ ${script_name}: 测试入口覆盖检查通过"
exit 0