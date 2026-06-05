package com.xiyu.bid.integration.organization.dto;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;

import java.time.LocalDateTime;

public record OrganizationSyncRunResponse(
        Long runId,
        String runKey,
        String runType,
        String sourceApp,
        String status,
        int totalCount,
        int successCount,
        int failedCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String lastErrorCode,
        String lastErrorMessage
) {
    public static OrganizationSyncRunResponse from(OrganizationSyncRunEntity run) {
        return new OrganizationSyncRunResponse(
                run.getId(),
                run.getRunKey(),
                run.getRunType(),
                run.getSourceApp(),
                run.getStatus(),
                run.getTotalCount(),
                run.getSuccessCount(),
                run.getFailedCount(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getLastErrorCode(),
                run.getLastErrorMessage()
        );
    }
}
