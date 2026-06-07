package com.xiyu.bid.warehouse.infrastructure;

import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarehouseType type;

    @Column(nullable = false, length = 20)
    private String region;

    @Column(nullable = false, length = 50)
    private String province;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal area;

    @Column(name = "contact_person", nullable = false, length = 100)
    private String contactPerson;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, length = 200)
    private String lessor;

    @Column(nullable = false, length = 200)
    private String lessee;

    @Column(name = "invoice_period", length = 100)
    private String invoicePeriod;

    @Column(name = "close_plan", length = 500)
    private String closePlan;

    /** 关仓原因，仅在 status=CLOSED 时有值 */
    @Column(name = "close_reason", length = 500)
    private String closeReason;

    @Column(name = "has_property_cert", nullable = false)
    private Boolean hasPropertyCert;

    @Column(name = "has_invoice", nullable = false)
    private Boolean hasInvoice;

    @Column(name = "has_photos", nullable = false)
    private Boolean hasPhotos;

    @Column(name = "cert_remarks", length = 500)
    private String certRemarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private WarehouseStatus status;

    /** 乐观锁版本号，closeReason 不在本字段生命周期中设置 */
    @Version
    private Long version;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (status == null) status = WarehouseStatus.IN_USE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
