package com.xiyu.bid.integration.tenderevent.infrastructure.persistence.entity;

import com.xiyu.bid.integration.tenderevent.domain.TenderEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 标讯事件推送流水实体。
 *
 * <p>对应迁移脚本 {@code V1185__create_tender_event_logs.sql}，
 * 记录每次标讯事件推送的关键信息，用于问题定位与追踪。
 */
@Getter
@Setter
@Entity
@Table(name = "tender_event_logs")
public class TenderEventLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "event_code", nullable = false, length = 100)
    private String eventCode;

    @Column(name = "event_source", nullable = false, length = 100)
    private String eventSource;

    @Column(name = "event_topic", nullable = false, length = 100)
    private String eventTopic;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 128)
    private String spanId;

    @Column(name = "parent_id", nullable = false, length = 128)
    private String parentId;

    @Column(name = "event_content", columnDefinition = "TEXT")
    private String eventContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenderEventStatus status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}