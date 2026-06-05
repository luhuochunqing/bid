package com.xiyu.bid.bidresult.dto;

import lombok.Data;

import java.util.List;

@Data
public class BidResultBatchRequest {
    private List<Long> ids;
    private String comment;
}

