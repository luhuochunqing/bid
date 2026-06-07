package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bar_certificates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bar_asset_id", nullable = false)
    private Long barAssetId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(length = 100)
    private String provider;

    @Column(name = "serial_no", nullable = false, length = 200)
    private String serialNo;

    @Column(length = 100)
    private String holder;

    @Column(length = 200)
    private String location;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CertificateStatus status;

    @Column(name = "current_borrower", length = 100)
    private String currentBorrower;

    @Column(name = "current_project_id")
    private Long currentProjectId;

    @Column(name = "borrow_purpose", length = 200)
    private String borrowPurpose;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(length = 500)
    private String remark;

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

    public void borrow(String borrowerName, Long projectId, String purpose, LocalDate returnDate, String pRemark) {
        if (status != CertificateStatus.AVAILABLE) {
            throw new IllegalStateException("Certificate is not available for borrowing");
        }

        this.status = CertificateStatus.BORROWED;
        this.currentBorrower = borrowerName;
        this.currentProjectId = projectId;
        this.borrowPurpose = purpose;
        this.expectedReturnDate = returnDate;
        this.remark = pRemark;
    }

    public void returnToPool(String pRemark) {
        if (status != CertificateStatus.BORROWED) {
            throw new IllegalStateException("Only borrowed certificates can be returned");
        }

        this.status = CertificateStatus.AVAILABLE;
        this.currentBorrower = null;
        this.currentProjectId = null;
        this.borrowPurpose = null;
        this.expectedReturnDate = null;
        if (pRemark != null && !pRemark.isBlank()) {
            this.remark = pRemark;
        }
    }

    public enum CertificateStatus {
        AVAILABLE,
        BORROWED,
        EXPIRED,
        DISABLED
    }
}
