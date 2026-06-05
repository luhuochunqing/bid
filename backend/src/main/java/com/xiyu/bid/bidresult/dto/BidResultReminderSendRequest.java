package com.xiyu.bid.bidresult.dto;

import lombok.Data;

@Data
public class BidResultReminderSendRequest {
    private Long resultId;
    private String comment;
}

