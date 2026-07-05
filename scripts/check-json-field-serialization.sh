#!/usr/bin/env bash
# CO-469 第八轮 P1 审计：CI 守卫脚本
# 用途：扫描所有 columnDefinition = "JSON" 或 @JdbcTypeCode(SqlTypes.JSON) 的 entity 字段，
#       检查对应的 Adapter/Service/Repository 中是否使用了 List.toString() / Map.toString()
#       等非 Jackson/Gson 序列化方式写入 JSON 字段。
#
# 防复发场景：
#   - CO-469 第八轮 PersonnelImportTaskRepositoryAdapter.serializeErrorDetails 用 List.toString()
#     写入 MySQL JSON 字段，触发 DataIntegrityViolationException
#   - CO-469 第八轮 P1 TenderSourceConfig.toJsonArray 手写拼接未转义控制字符
#
# 用法：
#   bash scripts/check-json-field-serialization.sh
#
# 退出码：
#   0 = 通过（无高风险隐患）
#   1 = 发现高风险隐患（必须修复）
#
# 设计原则：
#   - 只阻断"高置信度"模式（List.toString() / Map.toString() 写入）
#   - "中置信度"模式（Collectors.joining、手写拼接）只警告不阻断，避免误报卡住 PR
#   - 若命中是误报，可在该行加注释：// SAFE: <具体豁免理由>

set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

# 1. 找出所有 columnDefinition = "JSON" 或 @JdbcTypeCode(SqlTypes.JSON) 的 entity 文件
JSON_ENTITIES=$(grep -rlE 'columnDefinition\s*=\s*["\x27]JSON["\x27]|@JdbcTypeCode\(SqlTypes\.JSON\)' \
    backend/src/main/java 2>/dev/null || true)

if [ -z "$JSON_ENTITIES" ]; then
    echo "json-field-serialization: no JSON entity fields found, skip."
    exit 0
fi

echo "json-field-serialization: scanning for high-risk serialization patterns (List/Map.toString to JSON fields)..."

HAS_ERROR=0

# 模式 A（高置信度，阻断）：List<X>.toString() 或 Map<X,Y>.toString() 直接调用
# 这是 CO-469 第八轮根因模式，必须阻断
# 排除：log.debug/info/warn/error 调用、// SAFE: 注释
PATTERN_A=$(grep -rnE '\b(List|Map|Set)<[^>]+>\s+\w+\s*=\s*[^;]*;\s*$' backend/src/main/java 2>/dev/null | \
    grep -E '\.toString\(\)' | \
    grep -vE '//\s*SAFE:' | \
    grep -vE 'log\.(debug|info|warn|error|trace)' | \
    head -50 || true)

# 模式 B（中置信度，警告不阻断）：手写 JSON 字符串拼接（\"" + value + "\" 模式）
# 仅警告，开发者根据清单人工审查
PATTERN_B=$(grep -rnE '"\\"" \+|\+"\\"' backend/src/main/java 2>/dev/null | \
    grep -vE '//\s*SAFE:' | \
    grep -vE 'log\.|System\.out|System\.err' | \
    head -50 || true)

# 模式 C（中置信度，警告不阻断）：Collectors.joining 用于拼接（疑似 CSV 写入 JSON 字段）
# 仅警告，开发者根据清单人工审查
PATTERN_C=$(grep -rnE 'Collectors\.joining\(' backend/src/main/java 2>/dev/null | \
    grep -vE '//\s*SAFE:' | \
    head -50 || true)

if [ -n "$PATTERN_A" ]; then
    echo
    echo "❌ PATTERN A (阻断): List/Map/Set.toString() 调用"
    echo "  CO-469 第八轮根因模式：List.toString() 输出 [Item[field=...]] 不是合法 JSON。"
    echo "  命中位置："
    echo "$PATTERN_A" | sed 's/^/    /'
    echo
    echo "  修复方案：用 Jackson ObjectMapper.writeValueAsString() 替代。"
    echo "  若为误报（如仅用于日志），在该行加：// SAFE: <具体豁免理由>"
    HAS_ERROR=1
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
    echo "  命中位置（仅前 20 条）："
    echo "$PATTERN_C" | head -20 | sed 's/^/    /'
    echo "  ...(完整清单见 scripts/check-json-field-serialization.sh)"
    echo
    echo "  审查清单：确认每个 joining 是否用于写入 columnDefinition=\"JSON\" 字段。"
    echo "  若是，必须改为 Jackson writeValueAsString() 输出 JSON 数组。"
    echo "  若用于审计日志、CSV 文件生成，可在该行加：// SAFE: 用于 <具体场景>"
fi

if [ "$HAS_ERROR" -ne 0 ]; then
    echo
    echo "json-field-serialization: ❌ found HIGH-RISK patterns (List/Map.toString to JSON fields)."
    echo "  必须修复后再 commit/push。"
    echo
    echo "  历史背景：CO-469 第八轮 PersonnelImportTaskRepositoryAdapter.serializeErrorDetails"
    echo "  用 List.toString() 写入 MySQL JSON 字段，触发 DataIntegrityViolationException"
    echo "  被 SimpleAsyncUncaughtExceptionHandler 吞掉，任务永卡 PROCESSING/5%。"
    exit 1
fi

echo "json-field-serialization: ✅ passed (no high-risk List/Map.toString patterns)."
echo "  若 PATTERN B/C 有警告，请人工审查（不阻断本次提交）。"
