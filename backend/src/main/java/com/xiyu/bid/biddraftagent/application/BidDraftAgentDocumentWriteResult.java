package com.xiyu.bid.biddraftagent.application;

import java.util.List;

public record BidDraftAgentDocumentWriteResult(
        Long projectId,
        Long structureId,
        boolean structureCreated,
        int totalSections,
        int createdSections,
        int updatedSections,
        int skippedSectionsCount,
        List<BidDraftAgentSkippedSection> skippedSections
) {

    public BidDraftAgentDocumentWriteResult {
        skippedSections = List.copyOf(skippedSections);
    }
}
