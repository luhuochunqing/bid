package com.xiyu.bid.bidmatch.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bid_match_model_versions", indexes = {
        @Index(name = "idx_bid_match_version_model", columnList = "model_id"),
        @Index(name = "idx_bid_match_version_active", columnList = "active_flag")
})
@Getter
@Setter
@NoArgsConstructor
public class BidMatchModelVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;

    @Column(name = "active_flag", nullable = false)
    private boolean active;

    @Column(name = "activated_at", nullable = false)
    private LocalDateTime activatedAt;

    @Column(name = "activated_by", length = 100)
    private String activatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
