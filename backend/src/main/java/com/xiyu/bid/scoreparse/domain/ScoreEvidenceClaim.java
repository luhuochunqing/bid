package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;

    record ScoreEvidenceClaim(String id, String qualificationId, String tableId, String dimension,
                         List<String> sourceCellIds, List<String> weightCellIds, BigDecimal weight,
                         boolean parent, boolean contributesToTotal,
                         String quotedText, String tenderText, String schemeId, String scopeStatus,
                         ChildAggregation childAggregation, OriginalRuleRestoration.Result ruleUnit) {
        ScoreEvidenceClaim {
            ruleUnit = ruleUnit == null ? new OriginalRuleRestoration.Result("", List.of(), false) : ruleUnit;
        }
        boolean textLocator() {
            return (tableId == null || tableId.isBlank())
                    && sourceCellIds != null && sourceCellIds.size() == 1
                    && sourceCellIds.get(0).startsWith("text:line[");
        }
    }
