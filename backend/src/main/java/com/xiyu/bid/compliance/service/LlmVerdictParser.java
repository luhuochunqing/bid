// Input: LLM 裁决 JSON 响应字符串（固定契约：status/reasonCode/conclusion/suggestion）
// Output: Optional<LlmVerdict>——结构非法返回 empty（由调用方降级 LOW_CONFIDENCE），不抛异常
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
 * status 归一为大写；MANUAL 时 reasonCode 缺省由调用方补 AMBIGUOUS.
 */
public final class LlmVerdictParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmVerdictParser() {
    }

    /**
     * 解析 LLM 响应；JSON 非法/缺 status/结构不符一律 empty.
     */
    public static Optional<LlmVerdict> parse(final String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(response);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (node == null || !node.isObject() || !node.hasNonNull("status")) {
            return Optional.empty();
        }
        String status = node.get("status").asText("").trim().toUpperCase();
        String conclusion = text(node, "conclusion");
        if (conclusion.isBlank()) {
            conclusion = switch (status) {
                case "PASS" -> "语义比对通过";
                case "FAIL" -> "语义比对未通过";
                default -> "";
            };
        }
        return new LlmVerdict(status, text(node, "reasonCode"), conclusion, text(node, "suggestion"))
                .validated();
    }

    private static String text(final JsonNode node, final String field) {
        return node.hasNonNull(field) ? node.get(field).asText("").trim() : "";
    }
}
