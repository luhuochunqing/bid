package com.xiyu.bid.contractborrow.infrastructure.persistence.entity;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contract_borrow_applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractBorrowApplicationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_no", length = 100, nullable = false)
    private String contractNo;

    @Column(name = "contract_name", length = 255, nullable = false)
    private String contractName;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "borrower_name", length = 100, nullable = false)
    private String borrowerName;

    @Column(name = "borrower_dept", length = 100)
    private String borrowerDept;

    @Column(name = "customer_name", length = 255)
    private String customerName;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "borrow_type", length = 100)
    private String borrowType;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "return_remark", columnDefinition = "TEXT")
    private String returnRemark;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "last_comment", columnDefinition = "TEXT")
    private String lastComment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContractBorrowStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
