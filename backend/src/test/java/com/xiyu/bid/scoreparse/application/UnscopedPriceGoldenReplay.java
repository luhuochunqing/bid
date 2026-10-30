package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import java.math.BigDecimal;
import java.util.List;

/** Synthetic model claim through the real legacy binding, validation and candidate publication policy. */
public final class UnscopedPriceGoldenReplay {
    private UnscopedPriceGoldenReplay() {}
    public static Outcome replay(String text) {
        var fact = new MultimodalScoreFact("", "PRICE", "", "商务", BigDecimal.valueOf(100),
                "供应商报价作为评审参考。", "OBJECTIVE", null, "", List.of(),
                "供应商报价作为评审参考。", .9);
        var result = new MultimodalScoreFactExtractor.ExtractionResult(BigDecimal.valueOf(100),
                List.of(fact), "synthetic-unsupported-price", 0);
        var model = new TextAwareScoreFactExtractorTestAdapter() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extractTextAndProgress(String source, Runnable heartbeat) { return result; }
            public ExtractionResult recheckTextAndProgress(String source, Runnable heartbeat) { return result; }
        };
        var source = new TenderTextSource("synthetic.pdf", "synthetic:price", text);
        var replay = LegacyTextScoreExtraction.extract(source, List.of(), List.of(source), null, model, () -> {});
        return new Outcome(replay.structureIncomplete(), replay.completeness().decision(),
                replay.completeness().declaredTotalScore(), replay.candidates().size(), replay.facts().size());
    }
    public record Outcome(boolean structureIncomplete, com.xiyu.bid.scoreparse.domain.ScoreParseDecision decision,
                          BigDecimal declaredTotal, int candidateCount, int factCount) {}
}
