package com.xiyu.bid.integration.organization.application;

public record OrganizationEventRetrySummary(
        int totalCount,
        int successCount,
        int failedCount
) {
    static OrganizationEventRetrySummary empty() {
        return new OrganizationEventRetrySummary(0, 0, 0);
    }

    OrganizationEventRetrySummary add(OrganizationEventRetrySummary other) {
        return new OrganizationEventRetrySummary(
                totalCount + other.totalCount,
                successCount + other.successCount,
                failedCount + other.failedCount
        );
    }
}
