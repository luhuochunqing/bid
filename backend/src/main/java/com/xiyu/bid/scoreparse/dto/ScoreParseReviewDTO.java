package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 人工复核快照 DTO（spec 059 Phase 5 task3）。
 */
public record ScoreParseReviewDTO(
        Long projectId,
        Long parseTaskId,
        String taskId,
        String status,
        String decisionStatus,
        String fallbackReason,
        String reviewReason,
        String pipelineVersion,
        String artifactSha256,
        BigDecimal declaredTotal,
        String adoptionStatus,
        List<Fact> facts,
        List<Audit> audits
) {
    public ScoreParseReviewDTO(Long projectId, Long parseTaskId, String taskId, String status,
                               String decisionStatus, String fallbackReason, String reviewReason,
                               String pipelineVersion, String artifactSha256,
                               List<Fact> facts, List<Audit> audits) {
        this(projectId, parseTaskId, taskId, status, decisionStatus, fallbackReason, reviewReason,
                pipelineVersion, artifactSha256, BigDecimal.valueOf(100), "DECLARED_TOTAL", facts, audits);
    }
    public record Fact(
            String factId,
            String code,
            String dimension,
            BigDecimal weight,
            String scoreType,
            String validationStatus,
            String validationMessage,
            String sourceText,
            String sourceCellIds,
            String weightEvidence,
            String parentFactId,
            String extractionMethod,
            BigDecimal confidence,
            List<Evidence> evidences,
            String childAggregation
    ) {
        public Fact(String factId, String code, String dimension, BigDecimal weight, String scoreType,
                    String validationStatus, String validationMessage, String sourceText, String sourceCellIds,
                    String weightEvidence, String parentFactId, String extractionMethod, BigDecimal confidence,
                    List<Evidence> evidences) {
            this(factId, code, dimension, weight, scoreType, validationStatus, validationMessage, sourceText,
                    sourceCellIds, weightEvidence, parentFactId, extractionMethod, confidence, evidences, "SUM");
        }
    }

    public record Evidence(
            String cellId,
            String kind,
            String quotedText,
            Integer startOffset,
            Integer endOffset,
            String explanation
    ) {
    }

    public record Audit(
            String factId,
            String action,
            BigDecimal originalWeight,
            BigDecimal adjustedWeight,
            String reason,
            Long operatorId,
            String operatorName,
            LocalDateTime createdAt
    ) {
    }
}
