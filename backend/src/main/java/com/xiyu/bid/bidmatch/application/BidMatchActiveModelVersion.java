package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchModelVersionSnapshot;

public record BidMatchActiveModelVersion(
        Long versionEntityId,
        BidMatchModelVersionSnapshot snapshot
) {
}
