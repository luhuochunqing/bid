// Input: LLM 结构化提取的 JSON 响应字符串（contracts/tender-requirements-extraction.md §2，容忍 markdown 代码围栏与前后解释文字）
// Output: TenderRequirements 招标要求模型；JSON 非法或响应为空 → missingSentinel，不抛异常
// Pos: compliance/service/ - 招标要求 JSON 解析适配器（spec 049 US2，契约 §2/§3）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;

import java.util.ArrayList;
import java.util.List;

/**
 * 招标要求 LLM JSON 响应解析适配器（spec 049 第二步）.
 *
 * <p>JSON 解析属于边界 IO 行为（Jackson 抛 checked 异常），故置于 service 层
 * 而非纯核心 record（FP-Java：纯核心禁止以异常驱动业务流）；解析失败一律降级
 * {@link TenderRequirements#missingSentinel()}，不抛异常、不阻断任务.
 *
 * <p>容错机制：剥离 markdown 代码围栏（```json）与前后解释文字，通过首个配平花括号
 * 提取 JSON 对象子串，避免 LLM 自然语言交互引发的虚假反序列化失败与降级.
 */
public final class TenderRequirementsParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TenderRequirementsParser() {
    }

    /**
     * 解析 LLM JSON 响应（契约 §2）：五个顶层键缺失按空数组兜底；
     * 容忍 markdown 代码围栏与前后解释文字；
     * JSON 非法或响应为空 → {@link TenderRequirements#missingSentinel()}，不抛异常.
     *
     * @param llmJson LLM 返回的 JSON 字符串
     * @return 招标要求模型或哨兵值
     */
    public static TenderRequirements parse(final String llmJson) {
        if (llmJson == null || llmJson.isBlank()) {
            return TenderRequirements.missingSentinel();
        }
        String json = extractJsonObject(llmJson);
        if (json == null) {
            return TenderRequirements.missingSentinel();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            if (root == null || !root.isObject()) {
                return TenderRequirements.missingSentinel();
            }
            List<TenderRequirements.ScoringItem> scoringItems = new ArrayList<>();
            for (JsonNode node : arrayAt(root, "scoringItems")) {
                String name = textAt(node, "name");
                if (name.isBlank()) {
                    continue;
                }
                Double maxScore = node.path("maxScore").isNumber() ? node.path("maxScore").asDouble() : null;
                scoringItems.add(new TenderRequirements.ScoringItem(name, maxScore, textAt(node, "criteria")));
            }
            List<TenderRequirements.VetoClause> vetoClauses = new ArrayList<>();
            for (JsonNode node : arrayAt(root, "vetoClauses")) {
                String clause = textAt(node, "clause");
                if (clause.isBlank()) {
                    continue;
                }
                vetoClauses.add(new TenderRequirements.VetoClause(clause, stringListAt(node, "keywords")));
            }
            return new TenderRequirements(
                    scoringItems,
                    stringListAt(root, "mandatoryQualifications"),
                    vetoClauses,
                    stringListAt(root, "compositionRequirements"),
                    stringListAt(root, "formatRequirements"),
                    false);
        } catch (JsonProcessingException e) {
            return TenderRequirements.missingSentinel();
        }
    }

    private static Iterable<JsonNode> arrayAt(final JsonNode root, final String key) {
        JsonNode node = root.path(key);
        return node.isArray() ? node : List.of();
    }

    private static List<String> stringListAt(final JsonNode root, final String key) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : arrayAt(root, key)) {
            if (node.isTextual() && !node.asText().isBlank()) {
                values.add(node.asText().trim());
            }
        }
        return values;
    }

    private static String textAt(final JsonNode node, final String key) {
        JsonNode value = node.path(key);
        return value.isTextual() ? value.asText().trim() : "";
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
}
