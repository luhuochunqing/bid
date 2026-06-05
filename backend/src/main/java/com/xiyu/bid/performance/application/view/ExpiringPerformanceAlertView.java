package com.xiyu.bid.performance.application.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpiringPerformanceAlertView {

    private Long recordId;
    private String contractName;
    private String signingEntity;
    private String groupCompany;
    private String customerTypeLabel;
    private String projectTypeLabel;
    private LocalDate expiryDate;
    private long remainingDays;
    private String xiyuProjectManager;
    private String contactPerson;
    private String contactInfo;
    private String relatedId;
    private String message;
}
