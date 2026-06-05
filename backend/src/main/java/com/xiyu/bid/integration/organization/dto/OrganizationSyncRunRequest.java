package com.xiyu.bid.integration.organization.dto;

import java.time.LocalDateTime;

public record OrganizationSyncRunRequest(
        String sourceApp,
        String runType,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}
