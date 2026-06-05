package com.xiyu.bid.integration.organization.infrastructure.persistence.entity;

import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "organization_event_logs",
        indexes = @Index(name = "idx_org_event_logs_next_retry", columnList = "status,next_retry_at")
)
public class OrganizationEventLogEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, unique = true, length = 128)
    private String eventKey;

    @Column(name = "upstream_event_key", length = 128)
    private String upstreamEventKey;

    @Column(name = "event_topic", nullable = false, length = 100)
    private String eventTopic;

    @Column(name = "source_app", nullable = false, length = 100)
    private String sourceApp;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "span_id", length = 128)
    private String spanId;

    @Column(name = "parent_id", length = 128)
    private String parentId;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "external_dept_id", length = 128)
    private String externalDeptId;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrganizationEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(length = 500)
    private String message;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    void onCreate() {
        receivedAt = LocalDateTime.now();
    }
}
