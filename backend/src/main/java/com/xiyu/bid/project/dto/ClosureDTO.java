// Input: ProjectClosure 实体（蓝图 §3.3.1.6 - 项目结项）
// Output: 结项出参 DTO
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosureDTO {
    private Long id;
    private Long projectId;
    private String depositReturnStatus;
    private LocalDateTime depositReturnDate;
    private Long depositReturnEvidenceId;
    private BigDecimal transferAmount;
    private BigDecimal returnedAmount;
    private String archiveLocation;
    private Boolean stageLocked;
    private String notes;
    private String reviewStatus;
    private String projectSummary;
    private String rejectionReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime closedAt;
    private Long closedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
