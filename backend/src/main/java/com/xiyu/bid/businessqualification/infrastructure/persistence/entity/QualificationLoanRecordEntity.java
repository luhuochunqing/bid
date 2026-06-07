package com.xiyu.bid.businessqualification.infrastructure.persistence.entity;

import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "qualification_loan_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualificationLoanRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "qualification_id", nullable = false)
    private Long qualificationId;

    @Column(nullable = false, length = 120)
    private String borrower;

    @Column(length = 120)
    private String department;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(length = 255)
    private String purpose;

    @Column(length = 500)
    private String remark;

    @Column(name = "borrowed_at", nullable = false)
    private LocalDateTime borrowedAt;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "return_remark", length = 500)
    private String returnRemark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LoanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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
}
