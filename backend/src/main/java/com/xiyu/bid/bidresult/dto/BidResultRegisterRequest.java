package com.xiyu.bid.bidresult.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BidResultRegisterRequest {
    private Long projectId;
    private String result; // "won" or "lost"
    private BigDecimal amount;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private Integer contractDurationMonths;
    private String remark;
    private Integer skuCount;
    private Long attachmentDocumentId;
}
