package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.CaBorrowApplicationEntity;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CaBorrowApplicationDTO {
    private Long id;
    private Long caCertificateId;
    /**
     * CO-466: CA 证书显示名，由 Service 层 enrich 拼装，格式与前端
     * CABorrowDialog.vue caLabel 一致：
     * <pre>[持有人, 关联平台(逗号分隔), 印章中文].filter(Boolean).join(' / ')</pre>
     */
    private String caName;
    private Long applicantId;
    private String applicantName;
    /** CO-465: 申请人工号，供前端 formatDisplayName 渲染"姓名（工号）"。 */
    private String applicantEmployeeNumber;
    private String purpose;
    private Long projectId;
    private String projectName;
    private String borrowDurationType;
    private LocalDate expectedReturnDate;
    private String commitmentLetterUrl;
    private String status;
    private Long approverId;
    private String approverName;
    private String approvalComment;
    private LocalDateTime approvedAt;
    private LocalDate actualReturnDate;
    private String returnNotes;
    private LocalDateTime returnedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CaBorrowApplicationDTO from(CaBorrowApplicationEntity entity) {
        return from(entity, null);
    }

    /**
     * CO-466: 带 caName 的重载，由 Service 层 enrich 后调用。
     * caName 为 null 时留空（前端会 fallback 到 CA#${caCertificateId}）。
     */
    public static CaBorrowApplicationDTO from(CaBorrowApplicationEntity entity, String caName) {
        return CaBorrowApplicationDTO.builder()
                .id(entity.getId())
                .caCertificateId(entity.getCaCertificateId())
                .caName(caName)
                .applicantId(entity.getApplicantId())
                .applicantName(entity.getApplicantName())
                .applicantEmployeeNumber(entity.getApplicantEmployeeNumber())
                .purpose(entity.getPurpose())
                .projectId(entity.getProjectId())
                .projectName(entity.getProjectName())
                .borrowDurationType(entity.getBorrowDurationType())
                .expectedReturnDate(entity.getExpectedReturnDate())
                .commitmentLetterUrl(entity.getCommitmentLetterUrl())
                .status(entity.getStatus())
                .approverId(entity.getApproverId())
                .approverName(entity.getApproverName())
                .approvalComment(entity.getApprovalComment())
                .approvedAt(entity.getApprovedAt())
                .actualReturnDate(entity.getActualReturnDate())
                .returnNotes(entity.getReturnNotes())
                .returnedAt(entity.getReturnedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
