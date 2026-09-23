package com.xiyu.bid.scoreparse.infrastructure.openai;

import com.xiyu.bid.scoreparse.application.MultimodalScoreFactExtractor;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void conflictingDeclaredTotalsFailClosed() {
        assertThatThrownBy(() -> ScoreFactChunkMerger.merge(List.of(
                result(BigDecimal.valueOf(100), fact("1", "5", 0.8)),
                result(BigDecimal.valueOf(80), fact("2", "3", 0.8)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting declared totals");
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
