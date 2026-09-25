package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.application.MultimodalScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreFactChunkMergerTest {
    @Test
    void removesExactOverlapDuplicateButKeepsDistinctFacts() {
        var first = result(BigDecimal.valueOf(100), fact("1", "5", 0.8), fact("2", "3", 0.8));
        var overlap = result(BigDecimal.valueOf(100), fact("1", "5", 0.9), fact("3", "2", 0.8));

        var merged = ScoreFactChunkMerger.merge(List.of(first, overlap));

        assertThat(merged.facts()).hasSize(3);
        assertThat(merged.declaredTotal()).isEqualByComparingTo("100");
    }

    @Test
    void conflictingDeclaredTotalsKeepFactsAndMarkTotalUnknown() {
        var merged = ScoreFactChunkMerger.merge(List.of(
                result(BigDecimal.valueOf(100), fact("1", "5", 0.8)),
                result(BigDecimal.valueOf(80), fact("2", "3", 0.8))));

        assertThat(merged.declaredTotal()).isNull();
        assertThat(merged.facts()).extracting(MultimodalScoreFact::code)
                .containsExactly("1", "2");
    }

    @Test
    void sameCodesFromDifferentSchemesAndAuditRangesAreNotCollapsed() {
        var inScheme = fact("1", "5", 0.8).withScope("scheme-a", "IN_SCHEME");
        var otherScheme = fact("1", "5", 0.9).withScope("scheme-b", "IN_SCHEME");
        var audit = fact("1", "5", 0.95).withScope("scheme-a", "OUT_OF_SCHEME");

        var merged = ScoreFactChunkMerger.merge(List.of(result(null, inScheme, otherScheme, audit)));

        assertThat(merged.facts()).hasSize(3);
        assertThat(merged.facts()).extracting(MultimodalScoreFact::scopeStatus)
                .containsExactly("IN_SCHEME", "IN_SCHEME", "OUT_OF_SCHEME");
    }

    private static MultimodalScoreFactExtractor.ExtractionResult result(BigDecimal total, MultimodalScoreFact... facts) {
        return new MultimodalScoreFactExtractor.ExtractionResult(total, List.of(facts), "model", 0);
    }

    private static MultimodalScoreFact fact(String code, String weight, double confidence) {
        return new MultimodalScoreFact("技术", code, "", "项" + code, new BigDecimal(weight), "完整规则" + code,
                "OBJECTIVE", 1, "table", List.of("cell-" + code), "逐字原文" + code, confidence,
                ChildAggregation.SUM);
    }
}
