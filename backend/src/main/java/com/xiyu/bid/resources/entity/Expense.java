package com.xiyu.bid.resources.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    private static final String DEPOSIT_EXPENSE_TYPE = "保证金";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Column(name = "expense_type", length = 100)
    private String expenseType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 500)
    private String description;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseStatus status;

    @Column(name = "approval_comment", length = 500)
    private String approvalComment;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "return_requested_at")
    private LocalDateTime returnRequestedAt;

    @Column(name = "return_confirmed_at")
    private LocalDateTime returnConfirmedAt;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "last_return_reminder_at")
    private LocalDateTime lastReturnReminderAt;

    @Column(name = "return_comment", length = 500)
    private String returnComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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

    public void updateDetails(
            ExpenseCategory pCategory,
            BigDecimal pAmount,
            LocalDate pDate,
            String pExpenseType,
            String pDescription
    ) {
        if (pCategory != null) {
            this.category = pCategory;
        }
        if (pAmount != null) {
            if (pAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Amount must be positive");
            }
            this.amount = pAmount;
        }
        if (pDate != null) {
            if (pDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Date cannot be in the future");
            }
            this.date = pDate;
        }
        if (pExpenseType != null) {
            this.expenseType = pExpenseType;
        }
        if (pDescription != null) {
            this.description = pDescription;
        }
    }

    public void updateExpectedReturnDate(LocalDate pExpectedReturnDate) {
        this.expectedReturnDate = pExpectedReturnDate;
    }

    public void markApproved(String approver, String comment, ExpenseStatus nextStatus) {
        if (status != ExpenseStatus.PENDING_APPROVAL && status != ExpenseStatus.REJECTED) {
            throw new IllegalStateException("Expense is not in an approvable state");
        }

        this.status = nextStatus;
        this.approvedBy = approver;
        this.approvalComment = comment;
        this.approvedAt = LocalDateTime.now();
    }

    public void requestReturn(String actor, String pComment) {
        if (!isReturnable()) {
            throw new IllegalStateException("Only deposit-like expenses can enter return flow");
        }
        if (status == ExpenseStatus.RETURNED) {
            throw new IllegalStateException("Expense has already been returned");
        }

        this.status = ExpenseStatus.RETURN_REQUESTED;
        this.approvedBy = actor;
        this.returnComment = pComment;
        this.returnRequestedAt = LocalDateTime.now();
    }

    public void confirmReturn(String actor, String pComment) {
        if (!isReturnable()) {
            throw new IllegalStateException("Only deposit-like expenses can enter return flow");
        }
        if (status != ExpenseStatus.RETURN_REQUESTED
                && status != ExpenseStatus.PAID
                && status != ExpenseStatus.APPROVED) {
            throw new IllegalStateException("Expense is not awaiting return confirmation");
        }

        this.status = ExpenseStatus.RETURNED;
        this.returnComment = pComment;
        this.returnConfirmedAt = LocalDateTime.now();
        if (this.approvedBy == null || this.approvedBy.isBlank()) {
            this.approvedBy = actor;
        }
    }

    public void markPaid() {
        if (status != ExpenseStatus.APPROVED && status != ExpenseStatus.PAID) {
            throw new IllegalStateException("Only approved or already-paid expenses can register payment records");
        }
        this.status = ExpenseStatus.PAID;
    }

    public void recordReturnReminder(LocalDateTime remindedAt) {
        this.lastReturnReminderAt = remindedAt;
    }

    public boolean isReturnable() {
        return DEPOSIT_EXPENSE_TYPE.equals(this.expenseType);
    }

    public enum ExpenseCategory {
        MATERIAL,
        LABOR,
        EQUIPMENT,
        TRANSPORTATION,
        SUBCONTRACTING,
        OVERHEAD,
        OTHER
    }

    public enum ExpenseStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        PAID,
        RETURN_REQUESTED,
        RETURNED
    }
}
