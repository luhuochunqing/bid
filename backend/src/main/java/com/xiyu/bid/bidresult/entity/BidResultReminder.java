package com.xiyu.bid.bidresult.entity;

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

@Entity
@Table(name = "bid_result_reminders", indexes = {
        @Index(name = "idx_bid_result_reminder_project", columnList = "project_id"),
        @Index(name = "idx_bid_result_reminder_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidResultReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "project_name", nullable = false, length = 500)
    private String projectName;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "owner_name", nullable = false, length = 255)
    private String ownerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType reminderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status;

    @Column(name = "remind_time", nullable = false)
    private LocalDateTime remindTime;

    @Column(name = "last_reminder_comment", length = 500)
    private String lastReminderComment;

    @Column(name = "last_result_id")
    private Long lastResultId;

    @Column(name = "attachment_document_id")
    private Long attachmentDocumentId;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_by_name", nullable = false, length = 255)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (remindTime == null) {
            remindTime = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReminderType {
        NOTICE,
        REPORT
    }

    public enum ReminderStatus {
        PENDING,
        REMINDED,
        UPLOADED
    }
}
