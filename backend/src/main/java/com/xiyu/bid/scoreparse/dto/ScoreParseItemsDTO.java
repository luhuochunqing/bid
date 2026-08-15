package com.xiyu.bid.scoreparse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 评分项清单响应（contracts/score-parse-api.md §3）。
 * <p>summary.weightWarning=true 时前端展示实际总分（FR-022）。
 */
public record ScoreParseItemsDTO(
        List<ScoreItemDTO> items,
        Summary summary
) {

    public record Summary(
            BigDecimal totalWeight,
            BigDecimal totalEstScore,
            int okCount,
            int dangerCount,
            int pendingCount,
            BigDecimal objectiveWeight,
            BigDecimal subjectiveWeight,
            boolean weightWarning
    ) {
    }
}
