package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** Prompt projection deliberately omits duplicated provenance text; original spans remain in the catalog. */
final class ScoreAnchorPrompt {
    private ScoreAnchorPrompt() {}
    private record PromptAnchor(String anchorId, String sectionTitle, String tableId, String rawText) {}
    static List<com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog.Anchor> select(
            List<com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog.Anchor> anchors, String chunk) {
        String normalized = com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(chunk).text();
        return anchors.stream().filter(anchor -> anchor.sourceSpans().stream().anyMatch(span -> {
            String raw = com.xiyu.bid.scoreparse.domain.EvidenceText.normalize(span.rawText()).text();
            return raw.length() > 1 && normalized.contains(raw.substring(0, Math.min(raw.length(), 24)));
        })).toList();
    }
    static String build(List<com.xiyu.bid.scoreparse.domain.SourceAnchorCatalog.Anchor> anchors, ObjectMapper objectMapper) {
        if (anchors.isEmpty()) return "";
        StringBuilder prompt = new StringBuilder("\n原文锚点：sourceCellIds 只填一个 anchor: ID，tableId 可空。"
                + "dimension 必须是评分子项名称，category单独保留商务、技术等所属类别。ruleText可解释规则，原文将按有效ID恢复。\n");
        for (var anchor : anchors) {
            try {
                String json = objectMapper.writeValueAsString(new PromptAnchor(anchor.id(), anchor.sectionTitle(), anchor.tableId(), anchor.rawText()));
                if (prompt.length() + json.length() > 8_000)
                    throw new IllegalStateException("Source anchor exceeds bounded prompt budget");
                prompt.append(json).append('\n');
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("无法序列化源锚点", exception);
            }
        }
        return prompt.toString();
    }

}
