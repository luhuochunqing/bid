package com.xiyu.bid.alerts.entity;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "`condition`", nullable = false)
    private ConditionType condition;

    /**
     * 告警阈值，语义由 {@link AlertType} 决定：
     * <ul>
     *     <li>DEADLINE / QUALIFICATION_EXPIRY / PERFORMANCE_EXPIRY / CA_EXPIRY / CA_BORROW_OVERDUE / DEPOSIT_RETURN：天数</li>
     *     <li>RISK：风险评分（0-100）</li>
     *     <li>DOCUMENT：缺失文档数量</li>
     *     <li>BUDGET：金额（元）</li>
     * </ul>
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal threshold;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

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

    public enum AlertType {
        DEADLINE,
        BUDGET,
        RISK,
        DOCUMENT,
        QUALIFICATION_EXPIRY,
        DEPOSIT_RETURN,
        PERFORMANCE_EXPIRY,
        CA_EXPIRY,
        CA_BORROW_OVERDUE
    }

    public enum ConditionType {
        GREATER_THAN,
        LESS_THAN,
        EQUALS,
        CONTAINS
    }
}
