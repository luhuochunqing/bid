package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;

/**
 * 评分项 DTO（阶段 1 清单，contracts/score-parse-api.md §3）。
 * <p>主观项 estScore/kbHit 为 null；detail 为完整原文（禁止摘要）。
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
        String location
) {
}
