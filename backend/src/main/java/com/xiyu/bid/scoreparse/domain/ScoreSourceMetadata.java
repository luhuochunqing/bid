package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/** Keeps model wording separate from immutable original evidence and its diagnostics. */
public record ScoreSourceMetadata(String modelRuleText, String modelQuotedText,
                                  List<SourceSpan> sourceSpans, List<String> diagnostics,
                                  String modelCategory, String modelScoreType, ModelSourceClaim modelSourceClaim) {
    public static final ScoreSourceMetadata EMPTY = new ScoreSourceMetadata("", "", List.of(), List.of());
    public ScoreSourceMetadata(String modelRuleText, String modelQuotedText,
                               List<SourceSpan> sourceSpans, List<String> diagnostics) {
        this(modelRuleText, modelQuotedText, sourceSpans, diagnostics, "", "");
    }
    public ScoreSourceMetadata(String modelRuleText, String modelQuotedText, List<SourceSpan> sourceSpans,
                               List<String> diagnostics, String modelCategory, String modelScoreType) {
        this(modelRuleText, modelQuotedText, sourceSpans, diagnostics, modelCategory, modelScoreType, ModelSourceClaim.EMPTY);
    }
    public ScoreSourceMetadata {
        modelSourceClaim = modelSourceClaim == null ? ModelSourceClaim.EMPTY : modelSourceClaim;
        modelCategory = modelCategory == null ? "" : modelCategory;
        modelScoreType = modelScoreType == null ? "" : modelScoreType;
        modelRuleText = modelRuleText == null ? "" : modelRuleText;
        modelQuotedText = modelQuotedText == null ? "" : modelQuotedText;
        sourceSpans = sourceSpans == null ? List.of() : List.copyOf(sourceSpans);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
