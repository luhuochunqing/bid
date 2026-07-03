// Input: 结项预览视图（蓝图 §3.3.1.6 - 项目结项）
// Output: 结项前端 GET /closure/preview 的 DTO
// Pos: project/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClosurePreviewDTO {
    private Long projectId;
    private boolean hasDeposit;
    private BigDecimal depositAmount;
    private String depositPaymentMethod;  // 缴纳方式（只读，来自立项表）
    private String depositReturnStatus;   // NOT_RETURNED | FULLY_RETURNED | TRANSFERRED_TO_FEE | PARTIAL_RETURN_PARTIAL_TRANSFER | NA
    private LocalDateTime depositReturnDate;
    private Long depositReturnEvidenceId;
    /** 退回凭证文件名（来自 ProjectDocument.name，供前端只读展示） */
    private String depositReturnEvidenceName;
    private BigDecimal transferAmount;
    private BigDecimal returnedAmount;
    private boolean canClose;
    private List<String> blockingReasons;
    private int totalTaskCount;
    private int completedTaskCount;
    private int incompleteTaskCount;
    private Boolean alreadyClosed;
    private Boolean stageLocked;
    private String reviewStatus;          // DRAFT | PENDING | APPROVED | REJECTED
    private String projectSummary;
    private String rejectionReason;
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
}
