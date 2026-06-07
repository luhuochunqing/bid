package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated 已由 {@link com.xiyu.bid.docinsight.domain.StructuralDocumentChunker} 取代。
 *             新代码请使用 docinsight 领域层的分块器。将在 next-release 移除。
 */
@Deprecated(since = "next-release", forRemoval = true)
final class TenderDocumentTextChunker {

    private TenderDocumentTextChunker() {
    }

    static List<String> split(String text, int maxChars, int overlapChars) {
        if (text == null || text.isBlank()) {
            return List.of("");
        }
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                end = nearestBoundary(text, start, end);
            }
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            int nextStart = Math.max(0, end - overlapChars);
            start = nextStart <= start ? end : nextStart;
        }
        return List.copyOf(chunks);
    }

    private static int nearestBoundary(String text, int start, int preferredEnd) {
        int minBoundary = start + Math.max(1, (preferredEnd - start) / 2);
        String[] markers = {"\n\n", "\n", "。", "；", ";"};
        for (String marker : markers) {
            int searchEnd = preferredEnd - marker.length();
            int boundary = searchEnd >= minBoundary ? text.lastIndexOf(marker, searchEnd) : -1;
            if (boundary >= minBoundary) {
                return boundary + marker.length();
            }
        }
        return preferredEnd;
    }
}
