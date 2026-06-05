/** JPA entities for manufacturer authorization persistence. */
package com.xiyu.bid.brandauth.manufacturer.infrastructure.persistence.entity;

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

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.AuthStatus;
import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "manufacturer_authorization")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManufacturerAuthorizationEntity {

    /** Field length constants. */
    private static final int LEN_AUTH_TYPE = 20;
    private static final int LEN_PROD_LINE = 50;
    private static final int LEN_CODE = 100;
    private static final int LEN_NAME = 200;
    private static final int LEN_IMPORT = 10;
    private static final int LEN_REMARKS = 1000;
    private static final int LEN_REVOKE = 500;
    private static final int LEN_STATUS = 20;

    /** Primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Authorization type: MANUFACTURER or AGENT. */
    @Column(name = "authorization_type", nullable = false, length = LEN_AUTH_TYPE)
    @Builder.Default
    private String authorizationType = "MANUFACTURER";

    /** Product line. */
    @Enumerated(EnumType.STRING)
    @Column(name = "product_line", nullable = false, columnDefinition = "VARCHAR(" + LEN_PROD_LINE + ")")
    private ProductLine productLine;

    /** Brand identifier. */
    @Column(name = "brand_id", nullable = false, length = LEN_CODE)
    private String brandId;

    /** Brand display name. */
    @Column(name = "brand_name", nullable = false, length = LEN_NAME)
    private String brandName;

    /** Import or domestic. */
    @Column(name = "import_domestic", nullable = false, length = LEN_IMPORT)
    private String importDomestic;

    /** Manufacturer company name. */
    @Column(name = "manufacturer_name", nullable = false, length = LEN_NAME)
    private String manufacturerName;

    /** Agent company name (AGENT type only). */
    @Column(name = "agent_name", length = LEN_NAME)
    private String agentName;

    /** Authorization start date. */
    @Column(name = "auth_start_date", nullable = false)
    private LocalDate authStartDate;

    /** Authorization end date. */
    @Column(name = "auth_end_date", nullable = false)
    private LocalDate authEndDate;

    /** Auth-1 start date (manufacturer to agent). */
    @Column(name = "auth1_start_date")
    private LocalDate auth1StartDate;

    /** Auth-1 end date. */
    @Column(name = "auth1_end_date")
    private LocalDate auth1EndDate;

    /** Auth-1 remarks. */
    @Column(name = "auth1_remarks", length = LEN_REMARKS)
    private String auth1Remarks;

    /** Auth-2 start date (agent to xiyu). */
    @Column(name = "auth2_start_date")
    private LocalDate auth2StartDate;

    /** Auth-2 end date. */
    @Column(name = "auth2_end_date")
    private LocalDate auth2EndDate;

    /** Auth-2 remarks. */
    @Column(name = "auth2_remarks", length = LEN_REMARKS)
    private String auth2Remarks;

    /** General remarks. */
    @Column(name = "remarks", length = LEN_REMARKS)
    private String remarks;

    /** Current status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(" + LEN_STATUS + ")")
    private AuthStatus status;

    /** Reason for revocation. */
    @Column(name = "revoke_reason", length = LEN_REVOKE)
    private String revokeReason;

    /** Creator user ID. */
    @Column(name = "created_by")
    private Long createdBy;

    /** Creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Last update timestamp. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Optimistic lock version. */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /** Set defaults before persist. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = AuthStatus.ACTIVE;
        }
        if (this.version == null) {
            this.version = 0;
        }
    }

    /** Update timestamp before update. */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
