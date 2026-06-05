package com.xiyu.bid.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTypeDrillDownRowDTO {
    private Long projectId;
    private Long tenderId;
    private String projectName;
    private String tenderTitle;
    private String customer;
    private String customerType;
    private String status;
    private String outcome;
    private Long managerId;
    private String managerName;
    private BigDecimal amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
