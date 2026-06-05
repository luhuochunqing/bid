package com.xiyu.bid.biddraftagent.application;

import java.math.BigDecimal;
import java.util.List;

public record BidDraftAgentDocumentSection(
        String sectionKey,
        String title,
        String content,
        List<String> sourceReferences,
        BigDecimal confidence,
        boolean manual,
        List<BidDraftAgentDocumentSection> children
) {

    public BidDraftAgentDocumentSection {
        sourceReferences = List.copyOf(sourceReferences);
        children = List.copyOf(children);
    }
}
