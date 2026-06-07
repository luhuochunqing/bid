package com.xiyu.bid.performance.infrastructure.persistence.entity;

import com.xiyu.bid.performance.domain.valueobject.CustomerLevel;
import com.xiyu.bid.performance.domain.valueobject.CustomerType;
import com.xiyu.bid.performance.domain.valueobject.DockingMethod;
import com.xiyu.bid.performance.domain.valueobject.ProjectType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Version;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "performance_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── 合同基础信息 ──
    @Column(name = "contract_name", nullable = false, length = 200)
    private String contractName;

    @Column(name = "signing_entity", length = 200)
    private String signingEntity;

    @Column(name = "group_company", length = 200)
    private String groupCompany;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", columnDefinition = "varchar(50)")
    private CustomerType customerType;

    @Column(name = "industry", length = 100)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "project_type", columnDefinition = "varchar(50)")
    private ProjectType projectType;

    @Enumerated(EnumType.STRING)
    @Column(name = "docking_method", columnDefinition = "varchar(50)")
    private DockingMethod dockingMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_level", columnDefinition = "varchar(50)")
    private CustomerLevel customerLevel;

    // ── 合同关键日期 ──
    @Column(name = "signing_date")
    private LocalDate signingDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "total_expiry_date")
    private LocalDate totalExpiryDate;

    // ── 客户与联系人 ──
    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(name = "contact_info", length = 200)
    private String contactInfo;

    @Column(name = "territory", length = 200)
    private String territory;

    @Column(name = "customer_address", length = 500)
    private String customerAddress;

    @Column(name = "xiyu_project_manager", length = 100)
    private String xiyuProjectManager;

    // ── 附件资料相关 ──
    @Column(name = "mall_website_url", length = 500)
    private String mallWebsiteUrl;

    @Column(name = "has_bid_notice", nullable = false)
    private boolean hasBidNotice;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    // ── 系统字段 ──
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
