package com.xiyu.bid.businessqualification.infrastructure.persistence.entity;

import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubjectType;
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
@Table(name = "business_qualifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessQualificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String level;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    private QualificationSubjectType subjectType;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QualificationCategory category;

    @Column(name = "certificate_no", length = 120)
    private String certificateNo;

    @Column(length = 200)
    private String issuer;

    @Column(length = 200)
    private String agency;

    @Column(name = "agency_contact", length = 200)
    private String agencyContact;

    @Column(name = "cert_scope", columnDefinition = "TEXT")
    private String certScope;

    @Column(name = "cert_review_note", length = 200)
    private String certReviewNote;

    @Column(name = "holder_name", length = 120)
    private String holderName;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QualificationStatus status;

    @Column(name = "retire_reason", length = 500)
    private String retireReason;

    @Column(nullable = false)
    private boolean retired;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled;

    @Column(name = "reminder_days", nullable = false)
    private int reminderDays;

    @Column(name = "last_reminded_at")
    private LocalDateTime lastRemindedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_borrow_status", nullable = false, length = 32)
    private LoanStatus currentBorrowStatus;

    @Column(name = "current_borrower", length = 120)
    private String currentBorrower;

    @Column(name = "current_department", length = 120)
    private String currentDepartment;

    @Column(name = "current_project_id", length = 64)
    private String currentProjectId;

    @Column(name = "borrow_purpose", length = 255)
    private String borrowPurpose;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

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
