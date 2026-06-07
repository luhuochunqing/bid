package com.xiyu.bid.notification.outbound.entity;

import com.xiyu.bid.notification.outbound.core.OutboundChannel;
import com.xiyu.bid.notification.outbound.core.OutboundStatus;
import com.xiyu.bid.notification.outbound.core.SkipReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_outbound_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "channel", nullable = false, length = 16)
    private OutboundChannel channel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    private OutboundStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "skip_reason", length = 50)
    private SkipReason skipReason;

    @Column(name = "wecom_errcode")
    private Integer wecomErrcode;

    @Column(name = "wecom_errmsg", length = 500)
    private String wecomErrmsg;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
