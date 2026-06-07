package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class BarSiteVerificationDTO {
    private final Long id;
    private final Long barAssetId;
    private final String verifiedBy;
    private final LocalDateTime verifiedAt;
    private final String status;
    private final String message;
}
