package com.xiyu.bid.biddraftagent.application;

public record BidDraftAgentSkippedSection(
        Long sectionId,
        String sectionKey,
        String title,
        boolean locked,
        String reason
) {
}
