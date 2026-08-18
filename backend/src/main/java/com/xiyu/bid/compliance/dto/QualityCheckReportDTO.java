package com.xiyu.bid.compliance.dto;

import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标书质量核查完整报告 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckReportDTO {

    private Long id;
    private String taskId;
    private Long projectId;
    private ComplianceCheckResult.TaskStatus status;
    private ComplianceCheckResult.Status overallStatus;
    private Integer riskScore;
    private Integer passedCount;
    private Integer failedCount;
    private Integer manualReviewCount;
    private Integer totalCount;
    private String bidFileName;
    private String bidFileHash;
    private String tenderFileHash;
    private LocalDateTime checkedAt;
    private List<QualityCheckItemResult> items;
}
