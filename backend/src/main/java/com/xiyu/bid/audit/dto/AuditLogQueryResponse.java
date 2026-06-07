package com.xiyu.bid.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogQueryResponse {
    private List<AuditLogItemDTO> items;
    private AuditLogSummaryDTO summary;
}
