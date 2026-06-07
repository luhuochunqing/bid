package com.xiyu.bid.bidresult.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidResultSyncResponseDTO {
    private int affectedCount;
    private String message;
}
