package com.xiyu.bid.bidmatch.dto;

import java.time.LocalDateTime;

public record BidMatchActivationResponse(
        Long modelId,
        Long versionId,
        int versionNo,
        String status,
        LocalDateTime activatedAt
) {
}
