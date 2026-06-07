package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "expense_approval_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalResult result;

    @Column(length = 500)
    private String comment;

    @Column(nullable = false, length = 100)
    private String approver;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt;

    @PrePersist
    protected void onCreate() {
        if (actedAt == null) {
            actedAt = LocalDateTime.now();
        }
    }

    public enum ApprovalResult {
        APPROVED,
        REJECTED
    }
}
