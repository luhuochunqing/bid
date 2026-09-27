package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Frozen candidate. Validation is evidence quality; computability is an independent numerical contract. */
public record ExtractedScoreItem(int payloadVersion, String itemId, String factId, String schemeId,
        String fileId, String name, String category, String ruleText, List<String> materialRequirements,
        BigDecimal rawMaximum, BigDecimal schemeRatio, BigDecimal effectiveMaximum, String scoreUnit,
        String scoreType, String parentItemId, String aggregation, Map<String,String> fieldSources,
        List<String> diagnostics, String validationStatus, BigDecimal confidence, boolean computable,
        ScoreFact fact, OriginalScoreEvidencePolicy.Qualification qualification) {}
