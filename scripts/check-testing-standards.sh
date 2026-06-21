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
  # Skip if SKIP_TESTING_GATE=1 (worktree environment without full node_modules)
  if [ "${SKIP_TESTING_GATE:-0}" == "1" ]; then
    echo "testing-gate: SKIP_TESTING_GATE=1, skipping vitest."
  else
    # 策略：优先只跑与 staged 文件直接对应的 spec/test，避免 vitest --changed HEAD
    # 在遇到新文件时回退为全量 213 个测试（>30s），进而触发 session-gate 中途 auto-stash
    # 导致新 spec 文件被删除、vitest 报 "Failed to load url" 的问题。
    # 只有当找不到对应测试时才回退到 --changed HEAD。
    TARGETED_TESTS=""
    for file in $STAGED_FRONTEND_FILES; do
      dir=$(dirname "$file")
      base=$(basename "$file")
      name="${base%.*}"
      for ext in spec.js spec.ts test.js; do
        if [ -f "${dir}/${name}.${ext}" ]; then
          TARGETED_TESTS="$TARGETED_TESTS ${dir}/${name}.${ext}"
          break
        fi
      done
    done

    # 新添加的测试文件也显式加入，避免 --changed HEAD 把它们当成“无历史”而跑全量
    NEW_TEST_FILES=$(git diff --cached --name-only --diff-filter=A | grep -E '^src/.*\.(spec|test)\.(js|ts)$' || true)
    if [ -n "$NEW_TEST_FILES" ]; then
      TARGETED_TESTS="$TARGETED_TESTS $NEW_TEST_FILES"
    fi

    # 去重
    TARGETED_TESTS=$(echo "$TARGETED_TESTS" | tr ' ' '\n' | sort -u | tr '\n' ' ' | sed 's/^ *//;s/ *$//')

    if [ -n "$TARGETED_TESTS" ]; then
      echo "testing-gate: running targeted tests: $TARGETED_TESTS"
      npx vitest run $TARGETED_TESTS
    elif git rev-parse HEAD >/dev/null 2>&1; then
      echo "testing-gate: no targeted tests, falling back to --changed HEAD"
      npx vitest run --changed HEAD
    else
      echo "testing-gate: no targeted tests and no HEAD, running full suite"
      npx vitest run
    fi
  fi
fi

echo "testing-gate: passed."
