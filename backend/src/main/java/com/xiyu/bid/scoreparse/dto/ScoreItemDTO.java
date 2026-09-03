package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;

/**
 * 评分项 DTO（阶段 1 清单，contracts/score-parse-api.md §3）。
 * <p>主观项 estScore/kbHit 为 null；detail 为完整原文（禁止摘要）。
 * <p>spec 059 Phase 5：追加 sourceCellIds / weightEvidence / extractionMethod / confidence / validationStatus / parentScoreItemId。
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
        Long parentScoreItemId
) {
    /** 向后兼容构造器：旧测试/调用方继续可用，新证据字段默认 null。 */
    public ScoreItemDTO(Long id, String code, String dim, String detail, BigDecimal weight,
            String scoreType, String status, BigDecimal estScore, String estBasis,
            Boolean kbHit, String location) {
        this(id, code, dim, detail, weight, scoreType, status, estScore, estBasis,
                kbHit, location, null, null, null, null, null, null);
    }
}
