#!/usr/bin/env bash
# Input: pom.xml (un-staged, or --staged: git diff --cached pom.xml paths)
# Output: pre-commit / CI validation result for includeSystemScope configuration
# Pos: scripts/质量守卫脚本 - 防止 system-scope jar 被 spring-boot-maven-plugin 静默排除
# 维护声明: 当 pom.xml 依赖新增 <scope>system</scope> 时，检查逻辑不变；
#           若 spring-boot-maven-plugin 的 configuration 语法变更，同步更新 grep 条件。
#
# Check that system-scope Maven dependencies won't be silently excluded
# from the Spring Boot repackaged jar.
#
# When pom.xml declares dependencies with <scope>system</scope>,
# spring-boot-maven-plugin must have <includeSystemScope>true</includeSystemScope>
# or those jars won't appear in BOOT-INF/lib/ of the final app.jar.
#
# 2026-05-28 incident: ClientSDK-release_0.0.2.jar was missing from the deployed
# app.jar because includeSystemScope was absent. The OrganizationEventSdkConsumerAdapter
# bean silently failed to create, causing the org-sdk integration to be non-functional
# despite correct env vars and network.
#
# Usage: scripts/check-system-scope-jar.sh [--staged]
#   --staged: check staged pom.xml changes only (for pre-commit hook)

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || echo '.')"
status=0

check_pom() {
    local pom="$1"
    local label="$2"

    if [ ! -f "$pom" ]; then
        return 0
    fi

    # Check if pom.xml has any <scope>system</scope> dependency
    if ! grep -q '<scope>system</scope>' "$pom" 2>/dev/null; then
        return 0  # no system scope deps, nothing to check
    fi

    # Check if includeSystemScope is set to true
    if grep -q '<includeSystemScope>true</includeSystemScope>' "$pom"; then
        return 0  # correctly configured
    fi

    echo "ERROR: pom.xml has <scope>system</scope> dependencies but"
    echo "       spring-boot-maven-plugin is missing <includeSystemScope>true</includeSystemScope>."
    echo ""
    echo "       System-scope jars (e.g. ClientSDK) will NOT be packaged into"
    echo "       BOOT-INF/lib/ of the final app.jar, causing ClassNotFoundException"
    echo "       at runtime."
    echo ""
    echo "       Fix: add to the plugin <configuration> block in $pom:"
    echo "         <includeSystemScope>true</includeSystemScope>"
    status=1
}

if [ "${1:-}" = "--staged" ]; then
    # Check staged pom.xml changes only
    while IFS= read -r file; do
        if [[ "$file" =~ pom\.xml$ ]]; then
            staged_content=$(git show ":${file}" 2>/dev/null || true)
            if [ -n "$staged_content" ]; then
                if echo "$staged_content" | grep -q '<scope>system</scope>'; then
                    if ! echo "$staged_content" | grep -q '<includeSystemScope>true</includeSystemScope>'; then
                        echo "ERROR [staged]: $file has <scope>system</scope> deps but no <includeSystemScope>true</includeSystemScope>"
                        status=1
                    fi
                fi
            fi
        fi
    done < <(git diff --cached --name-only)
else
    check_pom "$ROOT_DIR/backend/pom.xml" "backend/pom.xml"
fi

exit $status
