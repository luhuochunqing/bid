#!/usr/bin/env bash
# R6 权限键覆盖率预审脚本（specs/024-preauthorize-unification）
# 扫描所有 @PreAuthorize hasAnyRole/hasRole 使用点，按 Controller 模块分类，
# 并对照 RoleProfileCatalog 已注册权限键，输出迁移可行性分析。
#
# 用法: bash scripts/audit-preauthorize-migration.sh
# 输出: stdout（可直接重定向到文件归档）

set -euo pipefail

REPO_ROOT="$(CDPATH= cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_DIR="$REPO_ROOT/backend/src/main/java"
CATALOG="$JAVA_DIR/com/xiyu/bid/entity/RoleProfileCatalog.java"

echo "# P3 迁移预审报告"
echo "# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "# 基线: PR #1557 合入后（EXPECTED_LEGACY_USE_COUNT=201）"
echo ""

# 1. 已注册权限键清单（来自 RoleProfileCatalog 常量 + menuPermissions 字面量）
echo "## 1. RoleProfileCatalog 已注册权限键"
echo ""
echo "### 常量定义（PERMISSION = \"xxx\"）"
grep -oE 'PERMISSION = "[^"]+"' "$CATALOG" | sed 's/PERMISSION = "//; s/"//' | sort -u | sed 's/^/- /'
echo ""
echo "### menuPermissions 中出现的权限键（运行时实际授予角色的）"
PERM_KEYS=$(grep -oE '"[a-z][a-z0-9.-]*"' "$CATALOG" | tr -d '"' | sort -u)
echo "$PERM_KEYS" | sed 's/^/- /' | head -50
echo ""

# 2. 按业务模块分组统计 hasAnyRole/hasRole 使用点
echo "## 2. 按业务模块分组的使用点统计"
echo ""
echo "| 模块 | 方法级 | 类级 | 总计 |"
echo "|---|---|---|---|"
for pkg in $(ls -d "$JAVA_DIR"/com/xiyu/bid/*/ 2>/dev/null | xargs -n1 basename | sort -u); do
  method_count=$(grep -rEn '^\s+@PreAuthorize\("(hasAnyRole|hasRole)|^\s+@PreAuthorize\([A-Z_]' "$JAVA_DIR/com/xiyu/bid/$pkg" --include="*.java" 2>/dev/null | grep -v "/test/" | wc -l | tr -d ' ')
  class_count=$(grep -rEn '^@PreAuthorize\("(hasAnyRole|hasRole)' "$JAVA_DIR/com/xiyu/bid/$pkg" --include="*.java" 2>/dev/null | grep -v "/test/" | wc -l | tr -d ' ')
  total=$((method_count + class_count))
  if [ "$total" -gt 0 ]; then
    echo "| $pkg | $method_count | $class_count | $total |"
  fi
done
echo ""

# 3. 表达式模式分布
echo "## 3. 表达式模式分布（决定迁移难度）"
echo ""
echo "| 表达式 | 数量 | 迁移难度 | 说明 |"
echo "|---|---|---|---|"
echo "| hasAnyRole('ADMIN','MANAGER') | $(grep -rEn "hasAnyRole\('ADMIN', 'MANAGER'\)" "$JAVA_DIR" --include="*.java" | grep -v "/test/" | wc -l | tr -d ' ') | 中 | 需判断模块语义选权限键 |"
echo "| hasRole('ADMIN') | $(grep -rEn "hasRole\('ADMIN'\)" "$JAVA_DIR" --include="*.java" | grep -v "/test/" | wc -l | tr -d ' ') | 低 | 通常对应 admin 专属模块权限键 |"
echo "| hasAnyRole(...多角色...) | $(grep -rEn "hasAnyRole\('[^']*', '[^']*'" "$JAVA_DIR" --include="*.java" | grep -v "/test/" | grep -v "'ADMIN', 'MANAGER'" | wc -l | tr -d ' ') | 高 | 手抄角色列表，需逐个映射 |"
echo "| 混合表达式（hasAnyRole or hasAuthority） | $(grep -rEn "hasAnyRole.*or.*hasAuthority" "$JAVA_DIR" --include="*.java" | grep -v "/test/" | wc -l | tr -d ' ') | 中 | CO-409 补丁，统一清理 |"
echo ""

# 4. 识别程序化鉴权（EC5，非 @PreAuthorize）
echo "## 4. 程序化鉴权（EC5，不在 @PreAuthorize 守卫范围）"
echo ""
grep -rEn 'hasAnyRole|hasRole' "$JAVA_DIR" --include="*.java" | grep -v "/test/" | grep -v "@PreAuthorize" | grep -vE "^\s*\*|//|import " | head -10 | sed 's|^|- |'
echo ""

# 5. 重点：需补权限键的模块（grep 找 Controller 路径推断语义）
echo "## 5. 需求权限键预审（按模块推断）"
echo ""
echo "以下模块在 RoleProfileCatalog 已有对应权限键，可直接迁移："
echo "- brandauth → brand-auth.view/create/edit/revoke（CO-394 已迁移验证）"
echo "- personnel → personnel.view/manage（CO-394 已迁移验证）"
echo "- performance → performance.manage（CO-394 已迁移验证）"
echo "- qualification → qualification.manage/view（CO-394 已迁移验证）"
echo "- resources/ca → resource-ca（CO-409 已注册）"
echo "- tender → tender.view（已有）"
echo "- project → project.view/create（已有）"
echo ""
echo "⚠️ 以下模块可能缺权限键，需 R6 预检确认："
for pkg in roi bidmatch calendar collaboration competitionintel contractborrow documenteditor documents fees marketinsight marketprediction scoreanalysis versionhistory alerts audit approval batch; do
  count=$(grep -rEn 'hasAnyRole|hasRole' "$JAVA_DIR/com/xiyu/bid/$pkg" --include="*.java" 2>/dev/null | grep -v "/test/" | wc -l | tr -d ' ')
  if [ "$count" -gt 0 ]; then
    # 检查该模块是否在 RoleProfileCatalog 有对应权限键
    has_perm=$(grep -c "\"$pkg\\." "$CATALOG" 2>/dev/null || echo 0)
    if [ "$has_perm" = "0" ]; then
      echo "- $pkg（$count 处）→ 无 ${pkg}.* 权限键，需补注册或评估能否用现有权限键"
    fi
  fi
done
echo ""
echo "## 6. 结论与建议"
echo ""
echo "1. 大部分模块已有对应权限键（CO-394/CO-409 注册过），可直接迁移"
echo "2. 少数模块（roi/bidmatch/calendar 等）可能缺权限键，需逐个评估"
echo "3. 建议分批策略：先迁移已有权限键的（低风险），再处理需补权限键的（中风险）"
