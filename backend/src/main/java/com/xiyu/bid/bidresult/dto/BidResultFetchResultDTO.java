package com.xiyu.bid.bidresult.dto;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BidResultFetchResultDTO {
    private Long id;
    private String source;
    private Long tenderId;
    private Long projectId;
    private String projectName;
    private BidResultFetchResult.Result result;
    private BigDecimal amount;
    private LocalDateTime fetchTime;
    private BidResultFetchResult.Status status;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
    private String ignoredReason;
    private BidResultFetchResult.RegistrationType registrationType;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private Integer contractDurationMonths;
    private String remark;
    private Integer skuCount;
    private String winAnnounceDocUrl;
    private Long noticeDocumentId;
    private Long analysisDocumentId;
}
