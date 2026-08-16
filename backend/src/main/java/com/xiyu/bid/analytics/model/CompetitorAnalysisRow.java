package com.xiyu.bid.analytics.model;

import com.xiyu.bid.entity.Project;

import java.time.LocalDateTime;

public record CompetitorAnalysisRow(
    Long projectId,
    String projectName,
    String competitorName,
    String discount,
    String tenderEntity,
    String paymentTerm,
    String resultType,
    Project.Status projectStatus,
    LocalDateTime createdAt
) {
}
