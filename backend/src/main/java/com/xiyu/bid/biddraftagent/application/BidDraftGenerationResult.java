package com.xiyu.bid.biddraftagent.application;

import java.util.List;

public record BidDraftGenerationResult(
        String draftText,
        String reviewSummary,
        List<GeneratedArtifactSpec> artifactSpecs
) {

    public BidDraftGenerationResult {
        artifactSpecs = List.copyOf(artifactSpecs);
    }
}
