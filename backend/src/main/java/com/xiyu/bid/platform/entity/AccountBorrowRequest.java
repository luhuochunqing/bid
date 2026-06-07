package com.xiyu.bid.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Tracks platform account borrow request lifecycle for notifications. */
@Entity
@Table(name = "account_borrow_requests", indexes = {
    @Index(name = "idx_abr_account", columnList = "account_id"),
    @Index(name = "idx_abr_borrower", columnList = "borrower_id"),
    @Index(name = "idx_abr_status", columnList = "status"),
    @Index(name = "idx_abr_expected", columnList = "expected_return_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBorrowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "borrower_id", nullable = false)
    private Long borrowerId;

    @Column(name = "custodian_id")
    private Long custodianId;

    @Column(length = 500)
    private String purpose;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "actual_return_date")
    private LocalDate actualReturnDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private BorrowStatus status = BorrowStatus.PENDING;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approval_comment", length = 1000)
    private String approvalComment;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Approve the request. Only valid from PENDING. */
    public void approve(Long approver, String comment) {
        if (this.status != BorrowStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING requests can be approved. Current: " + this.status);
        }
        this.status = BorrowStatus.APPROVED;
        this.approverId = approver;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }

    /** Reject the request. Only valid from PENDING. */
    public void reject(Long approver, String reason) {
        if (this.status != BorrowStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING requests can be rejected. Current: " + this.status);
        }
        this.status = BorrowStatus.REJECTED;
        this.approverId = approver;
        this.approvalComment = reason;
        this.approvedAt = LocalDateTime.now();
    }

    /** Mark as returned. */
    public void markReturned() {
        this.status = BorrowStatus.RETURNED;
        this.actualReturnDate = LocalDate.now();
        this.returnedAt = LocalDateTime.now();
    }

    /** Cancel the request (borrower only, when PENDING). */
    public void cancel() {
        if (this.status != BorrowStatus.PENDING) {
            throw new IllegalStateException(
                    "Only PENDING requests can be cancelled");
        }
        this.status = BorrowStatus.CANCELLED;
    }

    /** Borrow request status enumeration. */
    public enum BorrowStatus {
        PENDING, APPROVED, REJECTED, RETURNED, CANCELLED
    }
}
