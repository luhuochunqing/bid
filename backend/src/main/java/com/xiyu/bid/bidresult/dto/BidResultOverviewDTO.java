package com.xiyu.bid.bidresult.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BidResultOverviewDTO {
    private long pendingFetchCount;
    private long pendingReminderCount;
    private long competitorCount;
}
