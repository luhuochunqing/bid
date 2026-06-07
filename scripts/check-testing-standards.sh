#!/usr/bin/env bash
# Input: staged files from git index
# Output: testing gate result (Maven tests for backend, Vitest for frontend)
# Pos: scripts/质量守卫脚本 - 测试门禁
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

# 1. 后端测试门禁 (Java)
STAGED_JAVA_FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep -E '^backend/src/main/java/.*\.java$' || true)
STAGED_BACKEND_FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep -E '^backend/(src/main/java|src/test/java)/.*\.java$' || true)

if [ -n "$STAGED_BACKEND_FILES" ]; then
  echo "testing-gate: detected staged backend files, running architecture tests..."
  (cd backend && mvn -DforkCount=0 -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest,ProjectAccessGuardCoverageTest test -DfailIfNoTests=false -Djacoco.skip=true)
fi

if [ -n "$STAGED_JAVA_FILES" ]; then
  echo "testing-gate: detected staged Java files, running related tests..."
  
  # 提取类名并尝试运行对应的测试类 (例如 TenderService.java -> TenderServiceTest)
  TEST_CLASSES=()
  for file in $STAGED_JAVA_FILES; do
    basename=$(basename "$file" .java)
    test_class="${basename}Test"
    # 检查测试类是否存在
    if find backend/src/test/java -name "${test_class}.java" | grep -q .; then
      TEST_CLASSES+=("$test_class")
    fi
  done

  if [ "${#TEST_CLASSES[@]}" -gt 0 ]; then
    TEST_LIST=$(IFS=,; echo "${TEST_CLASSES[*]}")
    echo "testing-gate: running Maven tests: $TEST_LIST"
    # jacoco.skip=true 绕开 jacoco 0.8.12 + Java 21 "Unknown block type c0" 报告 bug
    # （与本 PR 改动无关；覆盖率检查由 CI 单独流水线处理）
    (cd backend && mvn test -Dtest="$TEST_LIST" -DfailIfNoTests=false -Djacoco.skip=true)
  else
    echo "testing-gate: no specific test classes found for staged files, skipping Maven test."
  fi
fi

# 2. 前端测试门禁 (Vue/JS)
STAGED_FRONTEND_FILES=$(git diff --cached --name-only --diff-filter=ACMR | grep -E '^src/.*\.(vue|js|ts|jsx|tsx)$' || true)

if [ -n "$STAGED_FRONTEND_FILES" ]; then
  echo "testing-gate: detected staged frontend files, running vitest..."
  # 为简单起见，运行所有相关的单元测试 (Vitest 默认只跑受影响的文件)
  # Skip if SKIP_TESTING_GATE=1 (worktree environment without full node_modules)
  if [ "${SKIP_TESTING_GATE:-0}" != "1" ]; then
    npx vitest run
  else
    echo "testing-gate: SKIP_TESTING_GATE=1, skipping vitest."
  fi
fi

echo "testing-gate: passed."
