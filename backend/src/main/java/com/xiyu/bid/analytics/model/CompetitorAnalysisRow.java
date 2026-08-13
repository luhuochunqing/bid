package com.xiyu.bid.analytics.model;

import com.xiyu.bid.entity.Project;

import java.time.LocalDateTime;

public record CompetitorAnalysisRow(
    Long projectId,
    String competitorName,
    String discount,
    String tenderEntity,
    Project.Status projectStatus,
    LocalDateTime createdAt
) {
}