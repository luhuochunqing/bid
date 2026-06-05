package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;

import java.time.LocalDateTime;

public record OrganizationOperationsStatusResponse(
        boolean enabled,
        boolean eventSdkEnabled,
        long pendingRetryCount,
        long deadLetterCount,
        /** Legacy count for events left in FAILED before retry/dead-letter statuses were introduced. */
        long failedCount,
        LastRun lastRun
) {
    public record LastRun(
            String runKey,
            String runType,
            String status,
            int totalCount,
            int successCount,
            int failedCount,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        static LastRun from(OrganizationSyncRunEntity run) {
            return new LastRun(
                    run.getRunKey(),
                    run.getRunType(),
                    run.getStatus(),
                    run.getTotalCount(),
                    run.getSuccessCount(),
                    run.getFailedCount(),
                    run.getStartedAt(),
                    run.getFinishedAt()
            );
        }
    }
}
