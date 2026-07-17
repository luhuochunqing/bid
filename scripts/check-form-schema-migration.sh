#!/usr/bin/env bash
# Input: backend/src/main/resources/db/migration-mysql/*.sql
# Output: exit 0 if no unsafe schema overwrites found;
#         exit 1 with detailed report otherwise
# Pos: scripts/ - Repository guardrail against schema migration overwriting user customizations
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 教训 #61 防复发：自定义表单 schema 迁移必须 merge 不能覆盖
# 用途：扫描 Flyway 迁移脚本中 UPDATE form_definition_registry SET schema_json 的覆盖式写法
#
# 防复发场景：
#   - V1007/V1120/V1166/V1167 都是无条件 UPDATE schema_json 覆盖用户自定义
#   - 用户在 /settings/workflow-forms 配置的显隐/必填/排序会丢失
#
# 用法：
#   bash scripts/check-form-schema-migration.sh                  # 全量审计扫描
#   bash scripts/check-form-schema-migration.sh --range <base>   # pre-push：commit range 内新增（A 状态）
#
# 为什么 --range 只看 A 状态（新增）？
#   修改已发布迁移会被 check-flyway-immutable.sh 拦截（Flyway checksum 不可变），
#   所以历史迁移无法加 SAFE 注释。两个门禁互补：
#     - 修改已发布迁移 → check-flyway-immutable.sh 拦截
#     - 新增迁移用覆盖式 UPDATE → check-form-schema-migration.sh 拦截
#   全量模式（无参数）仍扫描所有迁移，用于审计发现历史债务。
#
# 退出码：
#   0 = 通过（无高风险覆盖式迁移，或已加 SAFE 注释豁免）
#   1 = 发现高风险覆盖式迁移（必须修复或加 SAFE 注释）
#
# 设计原则（宁可误报不可漏报）：
#   - 阻断：UPDATE form_definition_registry SET schema_json = '...'
#     无条件覆盖，不检查用户是否自定义过
#   - 豁免：在该行上方加注释 -- SAFE-SCHEMA-MERGE: <具体理由>
#     理由必须说明"为什么这次覆盖是安全的"（如：首次初始化、字段 key 未变只加新字段等）
#
# 为什么不用"语义分析判断是否 merge"？
#   bash grep 做不了 JSON 解析和 merge 语义分析。
#   选择"宁错杀不放过"策略：所有 UPDATE schema_json 都过一遍，明确安全的才用 SAFE 注释排除。
#   误报成本低（加 -- SAFE-SCHEMA-MERGE: 即可），漏检成本高（用户自定义丢失）。

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

MIGRATION_DIR="backend/src/main/resources/db/migration-mysql"

# ── 参数解析 ────────────────────────────────────────
SCAN_MODE="full"
RANGE_BASE=""
while [ $# -gt 0 ]; do
    case "$1" in
        --range)   SCAN_MODE="range"; shift; RANGE_BASE="$1" ;;
        --full)    SCAN_MODE="full" ;;
        -*)        SCAN_MODE="full" ;;
    esac
    shift
done

# ── 确定扫描文件列表 ────────────────────────────────
if [ "$SCAN_MODE" = "range" ]; then
    # pre-push 模式：只看 commit range 内新增（A 状态）
    if [ -z "$RANGE_BASE" ]; then
        echo "form-schema-migration: --range 需要 <base> 参数" >&2
        exit 1
    fi
    SCAN_FILES=$(git diff --name-only --diff-filter=A "$RANGE_BASE"..HEAD -- "$MIGRATION_DIR" 2>/dev/null || true)
    SCAN_FILES=$(echo "$SCAN_FILES" | grep '\.sql$' | grep -v '^$' || true)
    if [ -z "$SCAN_FILES" ]; then
        echo "form-schema-migration: pre-push 模式，无新增 migration-mysql/ 文件，跳过。"
        exit 0
    fi
    FILE_COUNT=$(echo "$SCAN_FILES" | grep -c '^' || echo 0)
    echo "form-schema-migration: pre-push 扫描模式（仅新增 A 状态，range=$RANGE_BASE..HEAD），$FILE_COUNT 个文件..."
else
    if [ ! -d "$MIGRATION_DIR" ]; then
        echo "form-schema-migration: 无迁移目录，跳过。"
        exit 0
    fi
    SCAN_FILES=""
    echo "form-schema-migration: 全量扫描模式（${MIGRATION_DIR}）..."
fi

HAS_ERROR=0

# ────────────────────────────────────────────────────
# 阻断：UPDATE form_definition_registry SET schema_json
# ────────────────────────────────────────────────────
#
# 覆盖式 schema 迁移会丢失用户自定义（显隐/必填/排序/标签）。
# 正确做法：
#   1. 读取当前 schema_json
#   2. 检查是否有用户自定义（custom_version > 0 或 version > 系统基线）
#   3. 如果有用户自定义，只追加新字段，不动现有字段的 enabled/required/label
#   4. 如果没有用户自定义，直接写入系统基线
#
# 豁免条件（必须加注释 -- SAFE-SCHEMA-MERGE: <理由>）：
#   - 首次初始化（表为空或 version=1 系统基线）
#   - 字段 key 未变，只加新字段（纯追加，不修改现有字段）
#   - 紧急修复字段 key 拼写错误（需在 PR 描述说明影响）

# 先找所有 UPDATE form_definition_registry 的行（UPDATE 和表名可能跨行）
# SQL 中 UPDATE form_definition_registry SET ... schema_json = '...' 可能跨多行
# 策略：先找 UPDATE form_definition_registry 的行，再检查后续 3 行内是否有 schema_json
ALL_OVERWRITES=""
if [ "$SCAN_MODE" = "range" ]; then
    # 增量模式：逐个文件检查
    ALL_OVERWRITES=$(echo "$SCAN_FILES" | while IFS= read -r f; do
        [ -z "$f" ] && continue
        line_numbers=$(grep -nE 'UPDATE\s+form_definition_registry' "$f" 2>/dev/null | cut -d: -f1 || true)
        for lineno in $line_numbers; do
            context=$(sed -n "${lineno},$((lineno+3))p" "$f" 2>/dev/null || true)
            if echo "$context" | grep -qE 'schema_json\s*='; then
                echo "${f}:${lineno}:UPDATE form_definition_registry ... schema_json"
            fi
        done
    done)
else
    # 全量模式：递归扫描目录
    ALL_OVERWRITES=$(grep -rnE 'UPDATE\s+form_definition_registry' "$MIGRATION_DIR" 2>/dev/null | while IFS=: read -r f lineno content; do
        context=$(sed -n "${lineno},$((lineno+3))p" "$f" 2>/dev/null || true)
        if echo "$context" | grep -qE 'schema_json\s*='; then
            echo "${f}:${lineno}:${content}"
        fi
    done)
fi

if [ -n "$ALL_OVERWRITES" ]; then
    # 排除有 SAFE 注释豁免的（检查当前行和上一行）
    # SAFE 注释格式：-- SAFE-SCHEMA-MERGE: <理由>
    # 注意：SQL 注释是 --，不是 //
    VIOLATIONS=""
    while IFS= read -r line; do
        if [ -z "$line" ]; then continue; fi
        # 提取文件名和行号
        file=$(echo "$line" | cut -d: -f1)
        lineno=$(echo "$line" | cut -d: -f2)
        # 检查上一行是否有 SAFE-SCHEMA-MERGE 注释
        prev_line=$(sed -n "$((lineno-1))p" "$file" 2>/dev/null || true)
        if echo "$prev_line" | grep -qE -- '^\s*--\s*SAFE-SCHEMA-MERGE:'; then
            # 有 SAFE 注释，跳过
            continue
        fi
        # 也检查同一行末尾是否有 SAFE 注释（行内注释）
        if echo "$line" | grep -qE -- '--\s*SAFE-SCHEMA-MERGE:'; then
            continue
        fi
        # 无 SAFE 注释，加入违规列表
        VIOLATIONS="${VIOLATIONS}${line}"$'\n'
    done <<< "$ALL_OVERWRITES"
    # 去除末尾换行
    VIOLATIONS=$(echo "$VIOLATIONS" | sed '/^$/d')
else
    VIOLATIONS=""
fi

# ── 输出结果 ────────────────────────────────────────
if [ -n "$VIOLATIONS" ]; then
    echo
    echo "❌ 阻断: UPDATE form_definition_registry SET schema_json 无 SAFE 豁免"
    echo
    echo "  教训 #61：自定义表单 schema 迁移必须 merge 不能覆盖"
    echo "  覆盖式迁移会丢失用户在 /settings/workflow-forms 配置的显隐/必填/排序/标签。"
    echo
    echo "  命中位置："
    echo "$VIOLATIONS" | sed 's/^/    /'
    echo
    echo "  修复方案（三选一）："
    echo "    1. 改为 merge 模式：读取当前 schema_json → 检查 custom_version → merge 或写入"
    echo "    2. 加 SAFE 注释豁免（仅限已记录豁免场景）："
    echo "       在 UPDATE 语句上方加：-- SAFE-SCHEMA-MERGE: <具体理由>"
    echo "       理由必须说明：为什么这次覆盖是安全的（如首次初始化/纯追加字段等）"
    echo "    3. 如果是紧急修复，在 PR 描述说明影响范围 + 用户确认方案"
    echo
    echo "  历史背景："
    echo "    V1007/V1120/V1166/V1167 都是无条件覆盖，用户自定义会丢失"
    echo "    详见 docs/lessons/lessons-learned.md #61"
    HAS_ERROR=1
fi

if [ "$HAS_ERROR" -ne 0 ]; then
    echo
    echo "form-schema-migration: ❌ found HIGH-RISK schema overwrites."
    echo "  必须修复或加 SAFE 注释后再 commit/push。"
    echo
    echo "  逃生阀：FORM_SCHEMA_MIGRATION_SKIP=1（仅限已记录豁免场景，需在 PR 描述说明理由）"
    exit 1
fi

echo "form-schema-migration: ✅ passed (no unsafe schema overwrites)."
