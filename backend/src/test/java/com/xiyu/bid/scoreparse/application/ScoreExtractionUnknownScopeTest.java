package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreExtractionUnknownScopeTest {
    @Test void unclassifiedTextScopeRemainsUnknownWithoutCrashingOrInventingAScheme() throws Exception {
        String text;
        try (var input = getClass().getResourceAsStream("/scoreparse-golden/case-unscoped-price-only.md")) {
            text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(OriginalScoringContext.textScopeAt(text, 2).schemeId()).isNull();
        var fact = new MultimodalScoreFact("", "PRICE", "", "商务", BigDecimal.valueOf(100),
                "供应商报价作为评审参考。", "OBJECTIVE", 1, "", List.of("text:line[2]"),
                "供应商报价作为评审参考。", .9);
        var assigned = MultimodalExtractionReviewPolicy.assignScope(fact, null, null, text, List.of());
        assertThat(assigned.schemeId()).isEmpty();
        assertThat(assigned.scopeStatus()).isEmpty();
    }
}
