// Input: 带原文偏移的结构化摘录 + Markdown 章节索引
// Output: 带稳定段落 ID 的 AI 摘录与可验证的 ID -> 来源章节映射
// Pos: casework/application 纯函数边界，不调用 AI、数据库或文件 I/O
package com.xiyu.bid.casework.application;

import com.xiyu.bid.scoreparse.application.ScoreDocExcerptExtractor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class CaseSourceExcerpt {

    private CaseSourceExcerpt() {
    }

    public static Prepared prepare(
            ScoreDocExcerptExtractor.Excerpt excerpt,
            MarkdownSourceSectionIndex sectionIndex) {
        if (excerpt == null || excerpt.text() == null || excerpt.text().isBlank()) {
            return new Prepared("", Map.of());
        }
        if (excerpt.segments() == null || excerpt.segments().isEmpty()) {
            return new Prepared(excerpt.text(), Map.of());
        }

        Map<String, MarkdownSourceSectionIndex.Provenance> provenanceBySegmentId = new LinkedHashMap<>();
        for (ScoreDocExcerptExtractor.Segment segment : excerpt.segments()) {
            MarkdownSourceSectionIndex.Provenance provenance =
                    sectionIndex.locateByOriginalOffset(segment.sourceStartOffset());
            if (provenance != null) {
                provenanceBySegmentId.put(segment.id(), provenance);
            }
        }
        String promptText = excerpt.segments().stream()
                .map(segment -> "[" + segment.id() + "]\n" + segment.text())
                .collect(Collectors.joining("\n\n---\n\n"));
        return new Prepared(promptText, Map.copyOf(provenanceBySegmentId));
    }

    public record Prepared(
            String promptText,
            Map<String, MarkdownSourceSectionIndex.Provenance> provenanceBySegmentId) {

        public MarkdownSourceSectionIndex.Provenance provenanceFor(String segmentId) {
            if (segmentId == null || segmentId.isBlank()) {
                return null;
            }
            return provenanceBySegmentId.get(segmentId);
        }
    }
}
