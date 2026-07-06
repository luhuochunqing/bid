#!/usr/bin/env bash
# Input: backend/src/main/java/**/*.java source tree
# Output: exit 0 if no high-risk List/Map/Set.toString() patterns found;
#         exit 1 with detailed report otherwise
# Pos: scripts/ - Repository maintenance guardrail against CO-469 JSON serialization root cause
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# CO-469 第八轮 P1 审计：CI 守卫脚本
# 用途：扫描后端 Java 代码中疑似将集合 toString() 写入 JSON 字段的高风险模式。
#
# 防复发场景：
#   - CO-469 第八轮 PersonnelImportTaskRepositoryAdapter.serializeErrorDetails 用 List.toString()
#     写入 MySQL JSON 字段，触发 DataIntegrityViolationException
#   - CO-469 第八轮 P1 TenderSourceConfig.toJsonArray 手写拼接未转义控制字符
#
# 用法：
#   bash scripts/check-json-field-serialization.sh            # 全量扫描
#   bash scripts/check-json-field-serialization.sh --diff     # 增量扫描（仅本次变更文件）
#   bash scripts/check-json-field-serialization.sh --cached   # 增量扫描（仅暂存区变更）
#
# 退出码：
#   0 = 通过（无高风险隐患）
#   1 = 发现高风险隐患（必须修复）
#
# 设计原则（宁可误报不可漏报）：
#   - Pattern A（阻断）：集合/对象 .toString() 调用，排除明确安全的类型
#   - Pattern B/C（警告）：手写拼接、Collectors.joining，仅警告不阻断
#   - 误报豁免：在该行加注释 // SAFE: <具体理由>
#
# 为什么不用"精确匹配 JSON 字段写入路径"？
#   bash grep 做不了语义分析，无法追踪"toString 结果最终是否写入了 JSON 字段"。
#   选择"宁错杀不放过"策略：所有 .toString() 都过一遍，明确安全的才排除。
#   误报成本低（加 // SAFE: 即可），漏检成本高（线上事故）。

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

SRC_DIR="backend/src/main/java"

# ── 参数解析：增量扫描模式 ──────────────────────────
SCAN_MODE="full"
DIFF_TARGET=""
for arg in "$@"; do
    case "$arg" in
        --diff)    SCAN_MODE="diff"; DIFF_TARGET="HEAD" ;;
        --cached)  SCAN_MODE="diff"; DIFF_TARGET="--cached" ;;
        --full|-*) SCAN_MODE="full" ;;
    esac
done

# ── 确定扫描文件列表 ────────────────────────────────
if [ "$SCAN_MODE" = "diff" ]; then
    if [ "$DIFF_TARGET" = "--cached" ]; then
        SCAN_FILES=$(git diff --name-only --cached -- "$SRC_DIR" 2>/dev/null || true)
    else
        SCAN_FILES=$(git diff --name-only HEAD -- "$SRC_DIR" 2>/dev/null || true)
    fi
    # 过滤掉已删除的文件，只保留 .java 文件
    SCAN_FILES=$(echo "$SCAN_FILES" | grep '\.java$' | grep -v '^$' || true)
    if [ -z "$SCAN_FILES" ]; then
        echo "json-field-serialization: 增量模式，无 backend/ Java 文件变更，跳过。"
        exit 0
    fi
    FILE_COUNT=$(echo "$SCAN_FILES" | grep -c '^' || echo 0)
    echo "json-field-serialization: 增量扫描模式，$FILE_COUNT 个文件..."
else
    if [ ! -d "$SRC_DIR" ]; then
        echo "json-field-serialization: 无 Java 源码目录，跳过。"
        exit 0
    fi
    SCAN_FILES=""
    echo "json-field-serialization: 全量扫描模式（${SRC_DIR}）..."
fi

# grep 函数：增量模式传文件列表，全量模式递归目录
scan_grep() {
    local pattern="$1"
    if [ "$SCAN_MODE" = "diff" ]; then
        # 增量：逐个文件 grep
        echo "$SCAN_FILES" | xargs grep -nE "$pattern" 2>/dev/null || true
    else
        # 全量：递归目录
        grep -rnE "$pattern" "$SRC_DIR" 2>/dev/null || true
    fi
}

HAS_ERROR=0

# ────────────────────────────────────────────────────
# Pattern A（阻断，高置信度）：明确集合名的 .toString() 调用
# ────────────────────────────────────────────────────
#
# bash grep 做不了类型推断，无法 100% 确定某个 .toString() 的调用者是不是集合。
# 策略：只拦截"命名明确暗示是集合"的调用，保证低误报率。
#
# 高置信度集合命名模式（满足任一即命中）：
#   - 变量/方法名以 List / Map / Set 结尾：errorList, userMap, itemSet
#   - 变量/方法名以 List / Map / Set 开头：listItems, mapEntries
#   - 变量/方法名包含 _list / _map / _set （蛇形）
#   - 方法名 getXxxList / getXxxMap / getXxxSet
#   - 常见集合复数名：items, results, details, errors, records, entries, values, keys
#
# 排除：
#   - // SAFE: 注释豁免
#   - 注释行（以 // 或 * 开头的行，避免注释内容误触发）
#   - 日志调用（log.info/debug/warn/error/trace）
#   - .name().toString()（枚举）
#   - .values().toString()（枚举 values 是集合但这是类方法，实际很少用）
#
# 注意：Pattern A 故意保持保守，宁可漏检一些命名不好的变量，
#      也不要因为高误报让开发者养成跳过门禁的习惯。
#      命名不好的变量走 Pattern A2（增强警告，不阻断）。

ALL_TOSTRING=$(scan_grep '\.toString\(\)' || true)

if [ -n "$ALL_TOSTRING" ]; then
    # 先排除明确安全的上下文
    # 注意：grep 输出格式为 "文件名:行号:内容"，所以注释行匹配要从 : 后面开始
    FILTERED=$(echo "$ALL_TOSTRING" | \
        grep -vE ':[[:space:]]*//' | \
        grep -vE ':[[:space:]]*\*' | \
        grep -vE '//[[:space:]]*SAFE:' | \
        grep -vE 'log\.(debug|info|warn|error|trace)' | \
        grep -vE 'System\.(out|err)\.println' | \
        grep -vE '\.name\(\)\.toString' || true)

    # Pattern A：高置信度集合命名（阻断）
    PATTERN_A=$(echo "$FILTERED" | \
        grep -E '([A-Za-z_](List|Map|Set)[A-Za-z0-9_]*|(list|map|set)[A-Z][A-Za-z0-9_]*|_[Ll]ist|_[Mm]ap|_[Ss]et)\.toString\(\)' | \
        head -50 || true)

    # Pattern A2：中等置信度集合暗示（警告不阻断）
    # 常见集合复数变量名 + JSON 字段相关的暗示
    PATTERN_A2=$(echo "$FILTERED" | \
        grep -vE '([A-Za-z_](List|Map|Set)[A-Za-z0-9_]*|(list|map|set)[A-Z][A-Za-z0-9_]*|_[Ll]ist|_[Mm]ap|_[Ss]et)\.toString\(\)' | \
        grep -E '\b(items|results|details|errors|records|entries|values|keys|rows|columns|children|tags|ids|names)\.toString\(\)' | \
        head -50 || true)
else
    PATTERN_A=""
    PATTERN_A2=""
fi

# ── Pattern B（警告）：手写 JSON 字符串拼接 ──────────
PATTERN_B=$(scan_grep '"\\"" \+|\+"\\"' 2>/dev/null | \
    grep -vE ':[[:space:]]*//' | \
    grep -vE ':[[:space:]]*\*' | \
    grep -vE '//[[:space:]]*SAFE:' | \
    grep -vE 'log\.|System\.out|System\.err' | \
    head -50 || true)

# ── Pattern C（警告）：Collectors.joining 拼接 ────────
PATTERN_C=$(scan_grep 'Collectors\.joining\(' 2>/dev/null | \
    grep -vE ':[[:space:]]*//' | \
    grep -vE ':[[:space:]]*\*' | \
    grep -vE '//[[:space:]]*SAFE:' | \
    grep -vE 'log\.(debug|info|warn|error|trace)' | \
    head -50 || true)

# ── 输出结果 ────────────────────────────────────────
if [ -n "$PATTERN_A" ]; then
    echo
    echo "❌ PATTERN A (阻断): 明确集合名的 .toString() 调用"
    echo "  CO-469 第八轮根因模式：List.toString() 输出 [Item[field=...]] 不是合法 JSON。"
    echo "  命中位置："
    echo "$PATTERN_A" | sed 's/^/    /'
    echo
    echo "  修复方案：用 Jackson ObjectMapper.writeValueAsString() 替代。"
    echo "  若为误报（如自定义值对象命名恰好包含 List/Map/Set），在该行加：// SAFE: <具体豁免理由>"
    HAS_ERROR=1
fi

if [ -n "$PATTERN_A2" ]; then
    echo
    echo "⚠️  PATTERN A2 (警告): 疑似集合的 .toString() 调用"
    echo "  变量名暗示是集合（items/results/details/errors 等），但类型不明确，需人工确认。"
    echo "  命中位置："
    echo "$PATTERN_A2" | sed 's/^/    /'
    echo
    echo "  审查要点：确认是否写入 columnDefinition=\"JSON\" 字段。"
    echo "  若是，必须改为 Jackson writeValueAsString()。"
    echo "  若确认安全，加：// SAFE: <具体原因>"
fi

if [ -n "$PATTERN_B" ]; then
    echo
    echo "⚠️  PATTERN B (警告): 手写 JSON 字符串拼接"
    echo "  CO-469 第八轮 P1 TenderSourceConfig.toJsonArray 同类隐患：未转义控制字符。"
    echo "  命中位置："
    echo "$PATTERN_B" | sed 's/^/    /'
    echo
    echo "  审查清单：确认每个拼接是否用于写入 columnDefinition=\"JSON\" 字段。"
    echo "  若是，必须改为 Jackson writeValueAsString()。"
    echo "  若写入的是 varchar/TEXT 字段，可降级为 P2 优化（不阻断）。"
    echo "  若为误报，可在该行加：// SAFE: <具体原因>"
fi

if [ -n "$PATTERN_C" ]; then
    echo
    echo "⚠️  PATTERN C (警告): Collectors.joining 拼接"
    echo "  CO-469 第八轮 P1 ApprovalRequest.attachmentIds 同类隐患：CSV 拼接到 JSON 字段。"
    echo "  命中位置（前 20 条）："
    echo "$PATTERN_C" | head -20 | sed 's/^/    /'
    TOTAL_C=$(echo "$PATTERN_C" | wc -l | tr -d ' ')
    if [ "$TOTAL_C" -gt 20 ]; then
        echo "    ...(共 $TOTAL_C 条，完整清单请全量运行脚本)"
    fi
    echo
    echo "  审查清单：确认每个 joining 是否用于写入 columnDefinition=\"JSON\" 字段。"
    echo "  若是，必须改为 Jackson writeValueAsString() 输出 JSON 数组。"
    echo "  若用于审计日志、CSV 文件生成、显示拼接，可在该行加：// SAFE: 用于 <具体场景>"
fi

if [ "$HAS_ERROR" -ne 0 ]; then
    echo
    echo "json-field-serialization: ❌ found HIGH-RISK patterns."
    echo "  必须修复后再 commit/push。"
    echo
    echo "  历史背景：CO-469 第八轮 PersonnelImportTaskRepositoryAdapter.serializeErrorDetails"
    echo "  用 List.toString() 写入 MySQL JSON 字段，触发 DataIntegrityViolationException"
    echo "  被 SimpleAsyncUncaughtExceptionHandler 吞掉，任务永卡 PROCESSING/5%。"
    exit 1
fi

echo "json-field-serialization: ✅ passed (no high-risk List/Map/Set.toString patterns)."
WARN_COUNT=0
if [ -n "$PATTERN_A2" ]; then WARN_COUNT=$((WARN_COUNT + 1)); fi
if [ -n "$PATTERN_B" ]; then WARN_COUNT=$((WARN_COUNT + 1)); fi
if [ -n "$PATTERN_C" ]; then WARN_COUNT=$((WARN_COUNT + 1)); fi
if [ "$WARN_COUNT" -gt 0 ]; then
    echo "  ⚠️  有 $WARN_COUNT 类警告（A2/B/C），请人工审查（不阻断本次提交）。"
fi
