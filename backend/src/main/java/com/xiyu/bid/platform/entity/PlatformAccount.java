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

import java.time.LocalDateTime;

/** Platform Account entity for bidding platform account management. */
@Entity
@Table(name = "platform_accounts", indexes = {
    @Index(name = "idx_platform_username", columnList = "username"),
    @Index(name = "idx_platform_status", columnList = "status"),
    @Index(name = "idx_platform_type", columnList = "platform_type"),
    @Index(name = "idx_platform_borrowed_by", columnList = "borrowed_by")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccount {

    /** Field length constants. */
    private static final int LEN_USERNAME = 100;
    private static final int LEN_ACCT_NAME = 200;
    private static final int LEN_CONTACT = 200;
    private static final int LEN_PHONE = 20;
    private static final int LEN_EMAIL = 200;
    private static final int LEN_URL = 500;
    private static final int LEN_TYPE = 50;
    private static final int LEN_REMARKS = 500;
    private static final int LEN_STATUS = 20;

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Platform login username (unique). */
    @Column(unique = true, nullable = false, length = LEN_USERNAME)
    private String username;

    /** Encrypted password. */
    @Column(nullable = false)
    private String password;

    /** Display name of the platform. */
    @Column(name = "account_name", nullable = false, length = LEN_ACCT_NAME)
    private String accountName;

    /** Contact person name. */
    @Column(name = "contact_person", length = LEN_CONTACT)
    private String contactPerson;

    /** Contact phone number. */
    @Column(name = "contact_phone", length = LEN_PHONE)
    private String contactPhone;

    /** Contact email address. */
    @Column(name = "contact_email", length = LEN_EMAIL)
    private String contactEmail;

    /** Platform type category. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform_type", nullable = false, length = LEN_TYPE)
    private PlatformType platformType;

    /** Platform website URL. */
    @Column(length = LEN_URL)
    private String url;

    /** Whether CA certificate is associated. */
    @Column(name = "has_ca", nullable = false)
    @Builder.Default
    private Boolean hasCa = false;

    /** CA custodian user ID. */
    @Column(name = "ca_custodian")
    private Long caCustodian;

    /** Account custodian user ID. */
    @Column(name = "custodian")
    private Long custodian;

    /** Optional remarks. */
    @Column(length = LEN_REMARKS)
    private String remarks;

    /** Account status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = LEN_STATUS)
    @Builder.Default
    private AccountStatus status = AccountStatus.AVAILABLE;

    /** ID of user who borrowed the account. */
    @Column(name = "borrowed_by")
    private Long borrowedBy;

    /** Timestamp when borrowed. */
    @Column(name = "borrowed_at")
    private LocalDateTime borrowedAt;

    /** Due timestamp for return. */
    @Column(name = "due_at")
    private LocalDateTime dueAt;

    /** Total return count. */
    @Column(name = "return_count")
    @Builder.Default
    private Integer returnCount = 0;

    /** Creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last update timestamp. */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AccountStatus.AVAILABLE;
        }
        if (returnCount == null) {
            returnCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Update profile fields from a request DTO. */
    public void updateProfile(String pUsername, String pPassword, String pAccountName,
            PlatformType pPlatformType, String pUrl, String pContactPerson,
            String pContactPhone, String pContactEmail,
            Boolean pHasCa, Long pCaCustodian, Long pCustodian, String pRemarks) {
        if (pUsername != null && !pUsername.trim().isEmpty()) {
            this.username = pUsername;
        }
        if (pPassword != null && !pPassword.trim().isEmpty()) {
            this.password = pPassword;
        }
        if (pAccountName != null && !pAccountName.trim().isEmpty()) {
            this.accountName = pAccountName;
        }
        if (pPlatformType != null) {
            this.platformType = pPlatformType;
        }
        if (pUrl != null) {
            this.url = pUrl;
        }
        if (pContactPerson != null) {
            this.contactPerson = pContactPerson;
        }
        if (pContactPhone != null) {
            this.contactPhone = pContactPhone;
        }
        if (pContactEmail != null) {
            this.contactEmail = pContactEmail;
        }
        if (pHasCa != null) {
            this.hasCa = pHasCa;
        }
        if (pCaCustodian != null) {
            this.caCustodian = pCaCustodian;
        }
        if (pCustodian != null) {
            this.custodian = pCustodian;
        }
        if (pRemarks != null) {
            this.remarks = pRemarks;
        }
    }

    /** Mark the account as pending approval (before borrow approval workflow). */
    public void markPendingApproval() {
        if (status != AccountStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Account not available. Status: " + status.getDescription());
        }
        this.status = AccountStatus.PENDING_APPROVAL;
    }

    /** Borrow the account to a user. */
    public void borrow(Long borrowerId, LocalDateTime pBorrowedAt, LocalDateTime pDueAt) {
        if (status != AccountStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Account not available. Status: " + status.getDescription());
        }
        this.status = AccountStatus.IN_USE;
        this.borrowedBy = borrowerId;
        this.borrowedAt = pBorrowedAt;
        this.dueAt = pDueAt;
    }

    /** Return the account to pool. */
    public void returnToPool() {
        if (status != AccountStatus.IN_USE && status != AccountStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Account not in use. Status: " + status.getDescription());
        }
        this.status = AccountStatus.AVAILABLE;
        this.borrowedBy = null;
        this.borrowedAt = null;
        this.dueAt = null;
        this.returnCount = (returnCount == null ? 0 : returnCount) + 1;
    }

    /** Return the account to pool and update the encrypted password. */
    public void returnWithPassword(String newEncryptedPassword) {
        returnToPool();
        this.password = newEncryptedPassword;
    }

    /** Platform type enumeration. */
    public enum PlatformType {
        GOV_PROCUREMENT("政府采购网"),
        BIDDING_PLATFORM("招投标平台"),
        CONSTRUCTION_PLATFORM("建设工程平台"),
        OTHER("其他");

        private final String description;

        PlatformType(String pDescription) {
            this.description = pDescription;
        }

        public String getDescription() {
            return description;
        }
    }

    /** Account status enumeration. */
    public enum AccountStatus {
        AVAILABLE("可用"),
        PENDING_APPROVAL("审批中"),
        IN_USE("使用中"),
        MAINTENANCE("维护中"),
        DISABLED("已禁用");

        private final String description;

        AccountStatus(String pDescription) {
            this.description = pDescription;
        }

        public String getDescription() {
            return description;
        }
    }
}
