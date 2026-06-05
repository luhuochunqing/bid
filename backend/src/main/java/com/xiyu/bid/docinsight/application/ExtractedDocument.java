package com.xiyu.bid.docinsight.application;

import java.util.Map;

public record ExtractedDocument(
        String text,
        int textLength,
        String structuredMetadata,
        String extractorKey,
        Map<String, Object> extras
) {
}
