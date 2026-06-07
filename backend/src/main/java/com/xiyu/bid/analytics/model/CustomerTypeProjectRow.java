package com.xiyu.bid.analytics.model;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerTypeProjectRow(
    Long projectId,
    Long tenderId,
    String projectName,
    String tenderTitle,
    String customer,
    String customerType,
    Project.Status projectStatus,
    Long managerId,
    String managerName,
    BigDecimal amount,
    LocalDateTime referenceDate,
    LocalDateTime endDate,
    Tender.Status tenderStatus
) {
}
