package com.xiyu.bid.scoreparse.application;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreParseEntryRouterTest {
    @Test void emptyEvidenceAlsoFallsBack() {
        var evidence = StructuredTableResult.evidencePipeline(List.of(), List.of(), null, List.of());
        var recovered = StructuredTableResult.structureIncomplete("NO_SCORE_TABLE");
        assertThat(ScoreParseEntryRouter.extract(() -> evidence, () -> recovered)).isSameAs(recovered);
    }

    @Test void emptyCodeExtractionFallsBackExactlyOnceInOrder() {
        var calls = new ArrayList<String>();
        var recovered = StructuredTableResult.evidencePipeline(List.of(), List.of(), null, List.of());
        var result = ScoreParseEntryRouter.extract(() -> {
            calls.add("code"); return StructuredTableResult.ok(List.of());
        }, () -> { calls.add("legacy"); return recovered; });
        assertThat(calls).containsExactly("code", "legacy");
        assertThat(result).isSameAs(recovered);
    }

    @Test void failedFallbackDoesNotEraseStructureFailure() {
        var failed = StructuredTableResult.structureIncomplete("STRUCTURE_DEFECT");
        assertThat(ScoreParseEntryRouter.extract(() -> failed,
                () -> StructuredTableResult.ok(List.of()))).isSameAs(failed);
    }
}
