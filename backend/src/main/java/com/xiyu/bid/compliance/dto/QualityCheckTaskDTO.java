package com.xiyu.bid.compliance.dto;

import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标书质量核查异步任务状态 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityCheckTaskDTO {

    private String taskId;
    private Long projectId;
    private ComplianceCheckResult.TaskStatus status;
    private Integer progress;
    private String currentStep;
    private String errorMessage;
    private String bidFileName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
