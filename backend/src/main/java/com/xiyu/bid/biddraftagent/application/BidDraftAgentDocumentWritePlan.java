package com.xiyu.bid.biddraftagent.application;

import java.util.List;

public record BidDraftAgentDocumentWritePlan(
        String runId,
        String structureName,
        List<BidDraftAgentDocumentSection> sections
) {

    public BidDraftAgentDocumentWritePlan {
        sections = List.copyOf(sections);
    }
}
