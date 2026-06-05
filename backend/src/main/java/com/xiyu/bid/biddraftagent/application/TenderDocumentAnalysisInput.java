package com.xiyu.bid.biddraftagent.application;

public record TenderDocumentAnalysisInput(
        Long projectId,
        Long tenderId,
        String fileName,
        String extractedText,
        String structuredMetadata
) {
    public TenderDocumentAnalysisInput(Long projectId, Long tenderId, String fileName, String extractedText) {
        this(projectId, tenderId, fileName, extractedText, null);
    }
}
