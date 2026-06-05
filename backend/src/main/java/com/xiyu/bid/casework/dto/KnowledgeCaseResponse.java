package com.xiyu.bid.casework.dto;

public record KnowledgeCaseResponse(
        Long caseId,
        String scoringTitle,
        Long sourceProjectId,
        String responseTextSummary,
        String projectType,
        String customerType,
        Integer reuseCount,
        String createdAt,
        Boolean pinned,
        String bidResult,
        String sourceProjectName,
        String productLine
) {
}