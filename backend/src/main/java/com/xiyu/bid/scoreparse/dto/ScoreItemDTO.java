package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;

/**
 * 评分项 DTO（阶段 1 清单，contracts/score-parse-api.md §3）。
 * <p>主观项 estScore/kbHit 为 null；detail 为完整原文（禁止摘要）。
 * <p>spec 059 Phase 5：追加 sourceCellIds / weightEvidence / extractionMethod / confidence / validationStatus / parentScoreItemId。
 * <p>Step 2.2：追加 deliverableType / scoringMode 结构化打标字段。
 */
public record ScoreItemDTO(
        Long id,
        String code,
        String dim,
        String detail,
        BigDecimal weight,
        String scoreType,
        String status,
        BigDecimal estScore,
        String estBasis,
        Boolean kbHit,
        String location,
        String sourceCellIds,
        String weightEvidence,
        String extractionMethod,
        BigDecimal confidence,
        String validationStatus,
        Long parentScoreItemId,
        String estimateStatus,
        java.time.LocalDateTime estimatedAt,
        String deliverableType,
        String scoringMode
) {
    public ScoreItemDTO(Long id, String code, String dim, String detail, BigDecimal weight,
            String scoreType, String status, BigDecimal estScore, String estBasis, Boolean kbHit,
            String location, String sourceCellIds, String weightEvidence, String extractionMethod,
            BigDecimal confidence, String validationStatus, Long parentScoreItemId,
            String estimateStatus, java.time.LocalDateTime estimatedAt) {
        this(id, code, dim, detail, weight, scoreType, status, estScore, estBasis, kbHit,
                location, sourceCellIds, weightEvidence, extractionMethod, confidence,
                validationStatus, parentScoreItemId, estimateStatus, estimatedAt, null, null);
    }

    public ScoreItemDTO(Long id, String code, String dim, String detail, BigDecimal weight,
            String scoreType, String status, BigDecimal estScore, String estBasis, Boolean kbHit,
            String location, String sourceCellIds, String weightEvidence, String extractionMethod,
            BigDecimal confidence, String validationStatus, Long parentScoreItemId) {
        this(id, code, dim, detail, weight, scoreType, status, estScore, estBasis, kbHit,
                location, sourceCellIds, weightEvidence, extractionMethod, confidence,
                validationStatus, parentScoreItemId, null, null, null, null);
    }

    /** 向后兼容构造器：旧测试/调用方继续可用，新证据与打标字段默认 null。 */
    public ScoreItemDTO(Long id, String code, String dim, String detail, BigDecimal weight,
            String scoreType, String status, BigDecimal estScore, String estBasis,
            Boolean kbHit, String location) {
        this(id, code, dim, detail, weight, scoreType, status, estScore, estBasis,
                kbHit, location, null, null, null, null, null, null, null, null, null, null);
    }

    /** 推荐服务轻量投影构造器：包含基础评分属性 + 交付物形态与评价模式。 */
    public ScoreItemDTO(Long id, String code, String dim, String detail, BigDecimal weight,
            String scoreType, String status, BigDecimal estScore, String estBasis,
            Boolean kbHit, String location, String deliverableType, String scoringMode) {
        this(id, code, dim, detail, weight, scoreType, status, estScore, estBasis,
                kbHit, location, null, null, null, null, null, null, null, null,
                deliverableType, scoringMode);
    }
}
