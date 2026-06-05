package com.xiyu.bid.brandauth.infrastructure.persistence.entity;

import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationScope;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "brand_authorization_deprecated")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandAuthorizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand_name", nullable = false, length = 200)
    private String brandName;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "authorization_scope", nullable = false, columnDefinition = "VARCHAR(50)")
    private AuthorizationScope authorizationScope;

    @Column(name = "scope_detail", length = 500)
    private String scopeDetail;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    private AuthorizationStatus status;

    @Column(name = "authorization_doc_url", length = 500)
    private String authorizationDocUrl;

    @Column(length = 500)
    private String remarks;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

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
