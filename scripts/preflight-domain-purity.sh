#!/usr/bin/env bash
# Input: staged Java files from git index
# Output: blocking gate — rejects if domain/policy/core packages import Spring framework annotations
# Pos: scripts/ — FPJava 纯核心门禁源头治理
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

STAGED_JAVA=$(git diff --cached --name-only --diff-filter=ACMR | grep -E '^backend/src/main/java/.*\.java$' || true)
if [ -z "$STAGED_JAVA" ]; then
    echo "preflight-domain-purity: no staged Java files, skipping."
    exit 0
fi

# 纯核心包模式：domain/ policy/ core/
PURITY_PACKAGES='/(domain|policy|core)/'

VIOLATIONS=0
for file in $STAGED_JAVA; do
    # 只扫描纯核心包下的文件
    if ! echo "$file" | grep -qE "$PURITY_PACKAGES"; then
        continue
    fi

    # 检查是否 import 了 Spring 框架类（纯核心不应有框架依赖）
    if grep -nE '^import org\.springframework\.' "$file"; then
        echo "ERROR: [preflight-domain-purity] $file imports Spring framework classes"
        echo "       Pure core (domain/policy/core) must NOT depend on Spring framework."
        echo "       Use plain Java constructs. If you need dependency injection,"
        echo "       pass dependencies as constructor parameters from the shell layer."
        echo ""
        VIOLATIONS=$((VIOLATIONS + 1))
    fi

    # 检查是否使用了 System.*（隐式输入）
    if grep -nE 'System\.(currentTimeMillis|getenv|getProperty|nanoTime|in|out|exit)' "$file"; then
        echo "ERROR: [preflight-domain-purity] $file uses System.* (implicit input)"
        echo "       Pure core must receive all inputs as explicit parameters."
        echo ""
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

if [ "$VIOLATIONS" -gt 0 ]; then
    echo "FAIL: preflight-domain-purity — $VIOLATIONS violation(s) found."
    echo "Fix the violations above before committing."
    echo "If this is a false positive, add the file to domain-purity-baseline.txt"
    exit 1
fi

echo "preflight-domain-purity: passed (checked $(echo "$STAGED_JAVA" | wc -l) staged files)"
