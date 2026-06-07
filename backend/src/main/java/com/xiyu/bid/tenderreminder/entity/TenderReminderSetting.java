package com.xiyu.bid.tenderreminder.entity;

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

/**
 * 标讯提醒设置实体
 */
@Entity
@Table(name = "tender_reminder_settings", indexes = {
    @Index(name = "idx_tender_reminder_tender", columnList = "tender_id"),
    @Index(name = "idx_tender_reminder_type", columnList = "reminder_type"),
    @Index(name = "idx_tender_reminder_enabled", columnList = "enabled")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenderReminderSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, columnDefinition = "VARCHAR(50)")
    private ReminderType reminderType;

    @Column(name = "remind_before_hours")
    @Builder.Default
    private Integer remindBeforeHours = 24;

    @Column(name = "reminder_targets", columnDefinition = "JSON")
    private String reminderTargets;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "created_by")
    private Long createdBy;

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
}
