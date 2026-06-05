package com.xiyu.bid.bidresult.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BidResultUpdateRequest {
    private String result;
    private BigDecimal amount;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private Integer contractDurationMonths;
    private String remark;
    private Integer skuCount;
    private Long attachmentDocumentId;
}
