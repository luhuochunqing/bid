package com.xiyu.bid.scoreparse.application;

import java.util.function.Supplier;

/** Structured code extraction first; legacy model extraction is a lazy fallback. */
final class ScoreParseEntryRouter {
    private ScoreParseEntryRouter() {}

    static StructuredTableResult extract(Supplier<StructuredTableResult> structured,
                                         Supplier<StructuredTableResult> fallback) {
        var result = structured.get();
        // Evidence awaiting review must retain its source diagnostics and publication safeguards.
        if ((result.evidencePipeline() && !result.facts().isEmpty())
                || (!result.structureIncomplete() && !result.candidates().isEmpty())) {
            return result;
        }
        var recovered = fallback.get();
        return result.structureIncomplete() && !recovered.evidencePipeline()
                && recovered.candidates().isEmpty() ? result : recovered;
    }
}
