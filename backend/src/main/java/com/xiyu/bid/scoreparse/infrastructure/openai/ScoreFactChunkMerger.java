package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.application.MultimodalScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges chunk responses; conflicting chunk totals remain unknown without discarding facts. */
final class ScoreFactChunkMerger {
    private ScoreFactChunkMerger() {}

    static MultimodalScoreFactExtractor.ExtractionResult merge(
            List<MultimodalScoreFactExtractor.ExtractionResult> results) {
        Map<FactKey, MultimodalScoreFact> facts = new LinkedHashMap<>();
        BigDecimal declaredTotal = null;
        boolean conflictingDeclaredTotal = false;
        String model = "";
        for (var result : results) {
            if (result == null) throw new IllegalStateException("Missing score extraction chunk response");
            if (result.declaredTotal() != null) {
                if (declaredTotal != null && declaredTotal.compareTo(result.declaredTotal()) != 0) {
                    // A chunk can expose a local subtotal; defer document-total resolution downstream.
                    conflictingDeclaredTotal = true;
                } else if (declaredTotal == null) {
                    declaredTotal = result.declaredTotal();
                }
            }
            if (model.isBlank()) model = result.model();
            for (var fact : result.facts()) {
                FactKey key = FactKey.of(fact);
                MultimodalScoreFact existing = facts.get(key);
                if (existing == null || fact.confidence() > existing.confidence()) facts.put(key, fact);
            }
        }
        return new MultimodalScoreFactExtractor.ExtractionResult(
                conflictingDeclaredTotal ? null : declaredTotal, List.copyOf(facts.values()), model, 0);
    }

    private record FactKey(String sectionTitle, String code, String parentCode, String dimension,
                           BigDecimal weight, String ruleText, String scoreType, Integer pageNumber,
                           String tableId, List<String> sourceCellIds, String quotedText,
                           ChildAggregation childAggregation, String schemeId, String scopeStatus) {
        static FactKey of(MultimodalScoreFact fact) {
            BigDecimal weight = fact.weight() == null ? null : fact.weight().stripTrailingZeros();
            return new FactKey(fact.sectionTitle(), fact.code(), fact.parentCode(), fact.dimension(), weight,
                    fact.ruleText(), fact.scoreType(), fact.pageNumber(), fact.tableId(), fact.sourceCellIds(),
                    fact.quotedText(), fact.childAggregation(), fact.schemeId(), fact.scopeStatus());
        }
    }
}
