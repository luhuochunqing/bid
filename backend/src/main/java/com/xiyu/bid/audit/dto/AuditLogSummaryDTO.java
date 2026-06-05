package com.xiyu.bid.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogSummaryDTO {
    private long todayCount;
    private long weekCount;
    private long failedCount;
    private long activeUserCount;
    private long totalCount;
}
