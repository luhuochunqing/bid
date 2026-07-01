package com.xiyu.bid.resources.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ca_certificates")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CaCertificateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform_ids", length = 512)
    private String platformIds;

    @Column(name = "ca_type", length = 20, nullable = false)
    private String caType;

    @Column(name = "seal_type", length = 30, nullable = false)
    private String sealType;

    @Column(name = "electronic_account", length = 100)
    private String electronicAccount;

    @Column(name = "ca_password", length = 512)
    private String caPassword;

    @Column(length = 100)
    private String issuer;

    @Column(name = "holder_name", length = 100)
    private String holderName;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "ca_platform_url", length = 500)
    private String caPlatformUrl;

    @Column(name = "custodian_id", nullable = false)
    private Long custodianId;

    @Column(name = "custodian_name", length = 100, nullable = false)
    private String custodianName;

    @Column(name = "borrow_status", length = 30, nullable = false)
    @Builder.Default
    private String borrowStatus = CaBorrowStatus.IN_STOCK.name();

    /** CO-459: CA 证书借用状态枚举。 */
    public enum CaBorrowStatus {
        IN_STOCK,
        BORROWED,
        OVERDUE
    }

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(columnDefinition = "TEXT")
    private String remarks;

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
