package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;

/** Model-produced fact before deterministic validation and persistence. */
public record MultimodalScoreFact(
        String sectionTitle,
        String code,
        String parentCode,
        String dimension,
        BigDecimal weight,
        String ruleText,
        String scoreType,
        Integer pageNumber,
        String tableId,
        List<String> sourceCellIds,
        String quotedText,
        double confidence
) {
    public MultimodalScoreFact {
        sectionTitle = safe(sectionTitle);
        code = safe(code);
        parentCode = safe(parentCode);
        dimension = safe(dimension);
        ruleText = safe(ruleText);
        scoreType = safe(scoreType);
        tableId = safe(tableId);
        sourceCellIds = sourceCellIds == null ? List.of() : List.copyOf(sourceCellIds);
        quotedText = safe(quotedText);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
