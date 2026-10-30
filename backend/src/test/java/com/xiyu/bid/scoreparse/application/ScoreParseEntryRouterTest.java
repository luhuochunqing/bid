package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreParseEntryRouterTest {
    @Test void primaryIsFirstAndSuccessfulResultNeverLoadsOldGeometry() {
        var calls = new ArrayList<String>();
        var route = ScoreParseEntryRouter.extract(source("100"), model(calls, "100", false), () -> {}, () -> {
            throw new AssertionError("old geometry must not be invoked");
        });
        assertThat(calls).containsExactly("primary");
        assertThat(route.primaryAccepted()).isTrue();
        assertThat(route.primary().totalWeight()).isEqualByComparingTo("100");
    }
    @Test void invalidTotalTriggersOldPipelineExactlyOnceAfterPrimary() {
        var calls = new ArrayList<String>();
        var route = ScoreParseEntryRouter.extract(source("100"), model(calls, "180", false), () -> {}, () -> {
            calls.add("fallback"); return StructuredTableResult.ok(List.of());
        });
        assertThat(calls).containsExactly("primary", "fallback");
        assertThat(route.primaryAccepted()).isFalse();
        assertThat(route.fallback()).isNotNull();
    }
    @Test void providerFailureStillRunsExistingPipeline() {
        var calls = new ArrayList<String>();
        var route = ScoreParseEntryRouter.extract(source("100"), model(calls, "100", true), () -> {}, () -> {
            calls.add("fallback"); return StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT");
        });
        assertThat(calls).containsExactly("primary", "fallback");
        assertThat(route.fallback().failureReason()).isEqualTo("STRUCTURE_DEFECT");
    }
    @Test void sourceDeclaredEightyIsNotForcedToOneHundred() {
        var calls = new ArrayList<String>();
        var route = ScoreParseEntryRouter.extract(source("80"), model(calls, "80", false), () -> {}, () -> {
            throw new AssertionError("valid non100 source total");
        });
        assertThat(route.primary().totalWeight()).isEqualByComparingTo("80");
        assertThat(route.primaryAccepted()).isTrue();
    }
    private static TenderTextSource source(String total) {
        return new TenderTextSource("synthetic.pdf", "local", "本项目评分总分" + total + "分。独立评分规则满分" + total + "分");
    }
    private static MultimodalScoreFactExtractor model(List<String> calls, String weight, boolean fail) {
        return new MultimodalScoreFactExtractor() {
            public boolean supportsTextOnly() { return true; }
            public ExtractionResult extract(DocumentArtifact artifact, List<DocumentImage> images) {
                throw new AssertionError("not text primary");
            }
            public ExtractionResult extractTextAndProgress(String text, Runnable heartbeat) {
                calls.add("primary");
                if (fail) throw new IllegalStateException("provider unavailable");
                return new ExtractionResult(new BigDecimal(weight), List.of(new MultimodalScoreFact(
                        "评分", "1", "", "独立评分", new BigDecimal(weight), "独立评分规则满分" + weight + "分", "OBJECTIVE",
                        1, "", List.of(), "独立评分规则", 1)), "boundary", 0);
            }
        };
    }
}
