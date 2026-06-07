package com.xiyu.bid.integration.organization.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "organization_sync_items")
public class OrganizationSyncItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "entity_type", nullable = false, length = 32)
    private String entityType;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "external_dept_id", length = 128)
    private String externalDeptId;

    @Column(name = "internal_user_id")
    private Long internalUserId;

    @Column(name = "department_code", length = 100)
    private String departmentCode;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "event_key", length = 128)
    private String eventKey;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
