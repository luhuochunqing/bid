package com.xiyu.bid.compliance.dto;

import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 合规检查结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckResultDTO {

    private Long id;
    private Long projectId;
    private Long tenderId;
    private ComplianceCheckResult.Status overallStatus;
    private List<ComplianceIssue> issues;
    private Integer riskScore;
    private LocalDateTime checkedAt;
    private String checkedBy;
}
