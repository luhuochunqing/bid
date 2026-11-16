// Input: LLM 裁决 JSON 响应字符串（固定契约：status/reasonCode/conclusion/suggestion）
// Output: Optional<LlmVerdict>——容错解析（剥离代码围栏/多余文字、提取配平 JSON、结构恢复统一要求完整非空文本结论、单段字段按继续/结束/拒绝状态迭代扫描后结构校验、status 中文同义归一、MANUAL 空结论兜底）；仍非法返回 empty，不抛异常
// Pos: compliance/service/ - LLM 裁决响应解析（边界 IO 行为：Jackson checked 异常在本层吸收，同 TenderRequirementsParser 先例；spec 049 US5 / T047）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LLM 裁决响应解析（确定性纯函数，边界适配器）.
 *
 * <p>JSON 解析属于边界 IO 行为（Jackson 抛 checked 异常），故置于 service 层；
 * 纯核心（domain/policy）只消费解析后的 {@link LlmVerdict} 值.
 *
 * <p>响应契约：{@code {"status":"PASS|FAIL|MANUAL","conclusion":"...","suggestion":"...","reasonCode":"..."}}；
 * 容错：代码围栏或 JSON 前后多余文字不影响解析（提取首个配平 JSON 对象）；
 * status 归一为大写并兼容中文同义（通过/不通过/待人工等）；
 * MANUAL 时 reasonCode 缺省由调用方补 AMBIGUOUS，conclusion 缺省兜底默认文案。
 */
public final class LlmVerdictParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private LlmVerdictParser() {
    }

    /**
     * 解析 LLM 响应；多级容错（标准提取 -> 截断修复 -> 单段字段补齐后结构校验）；截断响应必须已输出完整非空结论，否则 empty.
     */
    public static Optional<LlmVerdict> parse(final String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        int objectStart = response.indexOf('{');
        if (objectStart >= 0 && response.substring(0, objectStart).matches(
                "(?s).*\"(?:status|conclusion|suggestion|reasonCode)\"\\s*:.*")) {
            return Optional.empty();
        }
        String json = extractJsonObject(response);
        if (json != null) {
            return tryParseNode(json, false);
        }
        String healed = healTruncatedJson(response);
        if (healed != null) {
            return tryParseNode(healed, true);
        }
        // 仅恢复无对象边界的单段字段；所有字段仍由同一次 JSON 结构解析验证。
        String fragment = extractFieldFragment(response);
        return fragment == null ? Optional.empty() : tryParseNode("{" + fragment + "}", true);
    }

    private static final java.util.Set<String> CONTRACT_KEYS = java.util.Set.of(
            "status", "conclusion", "suggestion", "reasonCode");

    /** 逐字符扫描连续字符串字段，调用栈不随字段长度增长；JSON 转义仍由 Jackson 校验。 */
    private static String extractFieldFragment(final String response) {
        int start = response.indexOf('"');
        if (start < 0 || !isFragmentProse(response.substring(0, start))) return null;
        int cursor = start;
        int lastValueEnd;
        FieldTail tail;
        do {
            lastValueEnd = scanNextField(response, cursor);
            if (lastValueEnd < 0) return null;
            tail = classifyFieldTail(response, lastValueEnd);
            cursor = tail.cursor();
        } while (tail.state() == FieldTailState.CONTINUE);
        return tail.state() == FieldTailState.END ? response.substring(start, lastValueEnd) : null;
    }

    private enum FieldTailState { CONTINUE, END, REJECT }

    private record FieldTail(FieldTailState state, int cursor) { }

    /** 逗号后引号表示下一字段；其余尾文仅允许无结构符号的说明文字。 */
    private static FieldTail classifyFieldTail(final String response, final int valueEnd) {
        int cursor = skipWhitespace(response, valueEnd);
        if (cursor < response.length() && response.charAt(cursor) == ',') {
            cursor = skipWhitespace(response, cursor + 1);
            if (cursor < response.length() && response.charAt(cursor) == '"') {
                return new FieldTail(FieldTailState.CONTINUE, cursor);
            }
        }
        FieldTailState state = isFragmentProse(response.substring(cursor))
                ? FieldTailState.END : FieldTailState.REJECT;
        return new FieldTail(state, cursor);
    }

    private static int scanNextField(final String text, final int start) {
        int keyEnd = stringEnd(text, start);
        if (keyEnd < 0) return -1;
        String key = text.substring(start + 1, keyEnd - 1);
        if (!CONTRACT_KEYS.contains(key)) return -1;
        int colon = skipWhitespace(text, keyEnd);
        if (colon >= text.length() || text.charAt(colon) != ':') return -1;
        int valStart = skipWhitespace(text, colon + 1);
        return stringEnd(text, valStart);
    }

    private static int stringEnd(final String text, final int start) {
        if (start >= text.length() || text.charAt(start) != '"') return -1;
        boolean escaped = false;
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') return i + 1;
        }
        return -1;
    }

    private static int skipWhitespace(final String text, final int start) {
        int cursor = start;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) cursor++;
        return cursor;
    }

    private static boolean isFragmentProse(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if ("\"{}[]".indexOf(text.charAt(i)) >= 0) return false;
        }
        return true;
    }

    private static Optional<LlmVerdict> tryParseNode(final String json, final boolean recovered) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject() || !node.hasNonNull("status")) {
                return Optional.empty();
            }
            // 所有结构恢复路径共用此边界；不能用默认文案补造模型未输出的结论。
            JsonNode originalConclusion = node.get("conclusion");
            if (recovered && (originalConclusion == null || !originalConclusion.isTextual()
                    || originalConclusion.asText().isBlank())) {
                return Optional.empty();
            }
            String status = normalizeStatus(node.get("status").asText(""));
            String conclusion = text(node, "conclusion");
            if (conclusion.isBlank()) {
                conclusion = switch (status) {
                    case "PASS" -> "语义比对通过";
                    case "FAIL" -> "语义比对未通过";
                    case "MANUAL" -> "AI 无法给出确定裁决，需人工复核该条款";
                    default -> "";
                };
            }
            return new LlmVerdict(status, text(node, "reasonCode"), conclusion, text(node, "suggestion"),
                    node.path("evidenceChunk").isIntegralNumber() && node.path("evidenceChunk").canConvertToInt()
                            ? node.path("evidenceChunk").intValue() : null,
                    node.path("evidenceQuote").isTextual() ? text(node, "evidenceQuote") : "")
                    .validated();
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private record ScanState(boolean inString, int stringStart, List<Character> stack, List<Integer> posStack) { }

    static String healTruncatedJson(final String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        String sub = text.substring(start).trim();
        ScanState scan = scanTokens(sub);
        if (!scan.inString() && scan.stack().isEmpty()) return null;
        return buildHealedJson(sub, scan);
    }

    private static ScanState scanTokens(final String sub) {
        boolean inString = false, escaped = false;
        int stringStart = -1;
        List<Character> stack = new ArrayList<>();
        List<Integer> posStack = new ArrayList<>();
        for (int i = 0; i < sub.length(); i++) {
            char c = sub.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
                stringStart = i;
            } else if (c == '{' || c == '[') {
                stack.add(c);
                posStack.add(i);
            } else if (c == '}' || c == ']') {
                popMatchingOpener(stack, posStack, c == '}' ? '{' : '[');
            }
        }
        return new ScanState(inString, stringStart, List.copyOf(stack), List.copyOf(posStack));
    }

    private static void popMatchingOpener(final List<Character> stack, final List<Integer> posStack, final char expected) {
        if (!stack.isEmpty() && stack.get(stack.size() - 1) == expected) {
            stack.remove(stack.size() - 1);
            posStack.remove(posStack.size() - 1);
        }
    }

    private static String buildHealedJson(final String sub, final ScanState scan) {
        StringBuilder sb = new StringBuilder(computePrefix(sub, scan));
        int len = sb.length();
        while (len > 0 && Character.isWhitespace(sb.charAt(len - 1))) len--;
        if (len > 0 && sb.charAt(len - 1) == ',') sb.setLength(len - 1);
        int remaining = scan.stack().size() - (isIncompleteArrayObject(scan) ? 1 : 0);
        for (int i = remaining - 1; i >= 0; i--) {
            char opener = scan.stack().get(i);
            sb.append(opener == '{' ? '}' : ']');
        }
        return sb.toString();
    }

    private static boolean isIncompleteArrayObject(final ScanState scan) {
        return scan.stack().size() >= 2
                && scan.stack().get(scan.stack().size() - 1) == '{'
                && scan.stack().get(scan.stack().size() - 2) == '[';
    }

    private static String computePrefix(final String sub, final ScanState scan) {
        if (isIncompleteArrayObject(scan)) {
            return sub.substring(0, backtrackBeforeCut(sub, scan.posStack().get(scan.posStack().size() - 1)));
        }
        if (!scan.stack().isEmpty() && scan.stack().get(scan.stack().size() - 1) == '[' && scan.inString()) {
            return sub.substring(0, backtrackBeforeCut(sub, scan.stringStart()));
        }
        if (scan.inString()) {
            return sub.substring(0, scan.stringStart()) + "null";
        }
        return sub;
    }

    private static int backtrackBeforeCut(final String text, final int index) {
        int cut = index;
        while (cut > 0 && Character.isWhitespace(text.charAt(cut - 1))) cut--;
        if (cut > 0 && text.charAt(cut - 1) == ',') cut--;
        return cut;
    }

    /**
     * status 归一：大写化 + 常见同义变体映射到三值集合；未知值原样返回（由 validated 拒绝）.
     */
    static String normalizeStatus(final String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        return switch (s) {
            case "PASS", "PASSED", "通过", "符合", "合规" -> "PASS";
            case "FAIL", "FAILED", "不通过", "未通过", "不符合" -> "FAIL";
            case "MANUAL", "MANUAL_REVIEW", "REVIEW", "待人工", "待确认", "人工复核", "需人工复核" -> "MANUAL";
            default -> s;
        };
    }

    /**
     * 从响应中提取首个配平的 JSON 对象子串（容忍代码围栏与前后缀文字）；不存在返回 null.
     */
    static String extractJsonObject(final String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        // 首个对象未闭合时交给截断修复，不把其嵌套示例提升为顶层裁决。
        return null;
    }

    private static String text(final JsonNode node, final String field) {
        return node.hasNonNull(field) ? node.get(field).asText("").trim() : "";
    }
}
