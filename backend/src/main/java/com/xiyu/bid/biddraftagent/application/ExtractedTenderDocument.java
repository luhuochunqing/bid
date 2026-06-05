package com.xiyu.bid.biddraftagent.application;

public record ExtractedTenderDocument(
        String fileName,
        String contentType,
        String text,
        int textLength,
        String extractorKey,
        String structuredMetadata
) {
    public ExtractedTenderDocument(String fileName, String contentType, String text, int textLength, String extractorKey) {
        this(fileName, contentType, text, textLength, extractorKey, null);
    }
}
