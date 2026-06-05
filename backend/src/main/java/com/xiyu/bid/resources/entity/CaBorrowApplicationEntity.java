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
    private String status = "PENDING_APPROVAL";

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
