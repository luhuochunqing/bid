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
        double confidence,
        ChildAggregation childAggregation,
        String schemeId,
        String scopeStatus
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
        childAggregation = childAggregation == null ? ChildAggregation.UNKNOWN : childAggregation;
        schemeId = safe(schemeId);
        scopeStatus = safe(scopeStatus);
    }

    public MultimodalScoreFact(String sectionTitle, String code, String parentCode, String dimension,
                               BigDecimal weight, String ruleText, String scoreType, Integer pageNumber,
                               String tableId, List<String> sourceCellIds, String quotedText, double confidence) {
        this(sectionTitle, code, parentCode, dimension, weight, ruleText, scoreType, pageNumber,
                tableId, sourceCellIds, quotedText, confidence, ChildAggregation.SUM, "", "");
    }

    public MultimodalScoreFact(String sectionTitle, String code, String parentCode, String dimension,
                               BigDecimal weight, String ruleText, String scoreType, Integer pageNumber,
                               String tableId, List<String> sourceCellIds, String quotedText, double confidence,
                               ChildAggregation childAggregation) {
        this(sectionTitle, code, parentCode, dimension, weight, ruleText, scoreType, pageNumber,
                tableId, sourceCellIds, quotedText, confidence, childAggregation, "", "");
    }

    public MultimodalScoreFact withScope(String newSchemeId, String newScopeStatus) {
        return new MultimodalScoreFact(sectionTitle, code, parentCode, dimension, weight, ruleText, scoreType,
                pageNumber, tableId, sourceCellIds, quotedText, confidence, childAggregation,
                newSchemeId, newScopeStatus);
    }

    public MultimodalScoreFact withParentCode(String newParentCode) {
        return new MultimodalScoreFact(sectionTitle, code, newParentCode, dimension, weight, ruleText, scoreType,
                pageNumber, tableId, sourceCellIds, quotedText, confidence, childAggregation, schemeId, scopeStatus);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
