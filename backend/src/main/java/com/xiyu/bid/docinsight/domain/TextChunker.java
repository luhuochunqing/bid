package com.xiyu.bid.docinsight.domain;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    public static List<String> split(String text, int maxChars, int overlapChars) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + maxChars, length);
            
            // Try to find a good breaking point (newline)
            if (end < length) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start + (maxChars / 2)) {
                    end = lastNewline + 1;
                }
            }
            
            chunks.add(text.substring(start, end));
            start = end;
            
            if (start < length) {
                start -= overlapChars;
                if (start < 0) start = 0;
            }
        }

        return chunks;
    }
}
