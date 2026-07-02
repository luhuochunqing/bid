package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ca_borrow_applications")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CaBorrowApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ca_certificate_id", nullable = false)
    private Long caCertificateId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "applicant_name", length = 100, nullable = false)
    private String applicantName;

    /**
     * CO-465: 申请人工号。新建申请时存 {@code user.getDisplayEmployeeNumber()}
     * （employee_number 为空时回退到 username，与统一人员格式化器契约一致）。
     */
    @Column(name = "applicant_employee_number", length = 100)
    private String applicantEmployeeNumber;

    @Column(length = 500, nullable = false)
    private String purpose;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "borrow_duration_type", length = 20, nullable = false)
    private String borrowDurationType;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "commitment_letter_url", length = 500)
    private String commitmentLetterUrl;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = BorrowStatus.PENDING_APPROVAL.name();

    /** CO-459: 借用申请状态枚举，消除魔法字符串。 */
    public enum BorrowStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        RETURNED,
        CANCELLED
    }

    /** CO-459: 借用时长类型枚举。 */
    public enum BorrowDurationType {
        SHORT_TERM,
        LONG_TERM
    }

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "actual_return_date")
    private LocalDate actualReturnDate;

    @Column(name = "return_notes", length = 500)
    private String returnNotes;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
