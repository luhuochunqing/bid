package com.xiyu.bid.performance.infrastructure.persistence.entity;

import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "performance_alert_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAlertConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_days_soe", nullable = false)
    private int alertDaysSoe;

    @Column(name = "alert_days_default", nullable = false)
    private int alertDaysDefault;

    @Column(nullable = false)
    private boolean enabled;

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

    public PerformanceAlertConfig toDomain() {
        return new PerformanceAlertConfig(id, alertDaysSoe, alertDaysDefault, enabled);
    }

    public static PerformanceAlertConfigEntity fromDomain(PerformanceAlertConfig config) {
        return PerformanceAlertConfigEntity.builder()
                .id(config.id())
                .alertDaysSoe(config.alertDaysSoe())
                .alertDaysDefault(config.alertDaysDefault())
                .enabled(config.enabled())
                .build();
    }
}
