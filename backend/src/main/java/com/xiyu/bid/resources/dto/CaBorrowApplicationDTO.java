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
    private Long applicantId;
    private String applicantName;
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
        return CaBorrowApplicationDTO.builder()
                .id(entity.getId())
                .caCertificateId(entity.getCaCertificateId())
                .applicantId(entity.getApplicantId())
                .applicantName(entity.getApplicantName())
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
