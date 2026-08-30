// Input: LLM 裁决 JSON 响应字符串（固定契约：status/reasonCode/conclusion/suggestion）
// Output: Optional<LlmVerdict>——容错解析（剥离代码围栏/多余文字、提取首个配平 JSON、status 中文同义归一、MANUAL 空结论兜底）；仍非法返回 empty（由调用方降级重试/LOW_CONFIDENCE），不抛异常
// Pos: compliance/service/ - LLM 裁决响应解析（边界 IO 行为：Jackson checked 异常在本层吸收，同 TenderRequirementsParser 先例；spec 049 US5 / T047）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;

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
     * 解析 LLM 响应；提取不到 JSON 对象/缺 status/归一后仍非三值状态才 empty.
     */
    public static Optional<LlmVerdict> parse(final String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        String json = extractJsonObject(response);
        if (json == null) {
            return Optional.empty();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (node == null || !node.isObject() || !node.hasNonNull("status")) {
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
        return new LlmVerdict(status, text(node, "reasonCode"), conclusion, text(node, "suggestion"))
                .validated();
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
        while (start >= 0) {
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
            start = text.indexOf('{', start + 1);
        }
        return null;
    }

    private static String text(final JsonNode node, final String field) {
        return node.hasNonNull(field) ? node.get(field).asText("").trim() : "";
    }
}
