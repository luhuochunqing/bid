package com.xiyu.bid.bidmatch.domain;

public record BidMatchModelVersionSnapshot(
        Long modelId,
        int versionNo,
        BidMatchScoringModel model
) {
}
