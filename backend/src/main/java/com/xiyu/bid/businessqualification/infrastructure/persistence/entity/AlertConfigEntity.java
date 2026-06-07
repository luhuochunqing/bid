package com.xiyu.bid.businessqualification.infrastructure.persistence.entity;

import com.xiyu.bid.businessqualification.domain.model.AlertConfig;
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
@Table(name = "qualification_alert_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_days", nullable = false)
    private int alertDays;

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

    public AlertConfig toDomain() {
        return new AlertConfig(id, alertDays, enabled);
    }

    public static AlertConfigEntity fromDomain(AlertConfig config) {
        return AlertConfigEntity.builder()
                .id(config.id())
                .alertDays(config.alertDays())
                .enabled(config.enabled())
                .build();
    }
}
