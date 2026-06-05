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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bid_result_sync_logs", indexes = {
        @Index(name = "idx_bid_result_sync_type", columnList = "operation_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidResultSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 20)
    private OperationType operationType;

    @Column(nullable = false, length = 200)
    private String source;

    @Column(length = 500)
    private String message;

    @Column(name = "affected_count", nullable = false)
    private Integer affectedCount;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 255)
    private String operatorName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum OperationType {
        SYNC,
        FETCH
    }
}
