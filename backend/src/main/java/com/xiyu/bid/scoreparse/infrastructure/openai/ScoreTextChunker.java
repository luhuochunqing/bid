package com.xiyu.bid.scoreparse.infrastructure.openai;

import java.util.ArrayList;
import java.util.List;

/** Splits complete tender text into bounded, overlapping model context windows. */
final class ScoreTextChunker {
    static final int MAX_CHARS = 24_000;
    static final int OVERLAP_CHARS = 1_200;

    private ScoreTextChunker() {}

    static List<Chunk> split(String source, int maxChars, int overlapChars) {
        if (maxChars < 2 || overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("Invalid score text chunk bounds");
        }
        String text = source == null ? "" : source;
        if (text.isEmpty()) return List.of(new Chunk(0, 0, ""));
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = safeBoundary(text, Math.min(start + maxChars, text.length()), false);
            if (end < text.length()) {
                int lineBreak = text.lastIndexOf('\n', end - 1);
                int minimumLineEnd = start + (maxChars * 3 / 4);
                if (lineBreak >= minimumLineEnd) end = lineBreak + 1;
            }
            if (end <= start) end = safeBoundary(text, Math.min(start + maxChars, text.length()), true);
            chunks.add(new Chunk(start, end, text.substring(start, end)));
            if (end == text.length()) break;
            start = safeBoundary(text, Math.max(start + 1, end - overlapChars), false);
        }
        return List.copyOf(chunks);
    }

    private static int safeBoundary(String text, int index, boolean forward) {
        int boundary = Math.max(0, Math.min(index, text.length()));
        if (boundary > 0 && boundary < text.length()
                && Character.isHighSurrogate(text.charAt(boundary - 1))
                && Character.isLowSurrogate(text.charAt(boundary))) {
            return forward ? boundary + 1 : boundary - 1;
        }
        return boundary;
    }

    record Chunk(int start, int end, String text) {}
}
