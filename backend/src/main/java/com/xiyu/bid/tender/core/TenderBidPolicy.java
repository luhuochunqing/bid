package com.xiyu.bid.tender.core;

import com.xiyu.bid.entity.Tender;

/**
 * Pure domain policy for tender bid participation decisions.
 * No side effects, no IO, testable in isolation (FP-Java core).
 */
public final class TenderBidPolicy {

    private TenderBidPolicy() {}

    public static final class BidDecision {
        public final boolean allowed;
        public final String rejectionReason;

        public BidDecision(boolean allowed, String rejectionReason) {
            this.allowed = allowed;
            this.rejectionReason = rejectionReason;
        }
    }

    public static BidDecision decideParticipation(Tender tender) {
        return switch (tender.getStatus()) {
            case BIDDING -> new BidDecision(false, "该标讯已投标");
            case ABANDONED -> new BidDecision(false, "该标讯已放弃，无法投标");
            default -> new BidDecision(true, null);
        };
    }
}
