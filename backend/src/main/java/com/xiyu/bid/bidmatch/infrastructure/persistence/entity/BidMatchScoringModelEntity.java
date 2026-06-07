package com.xiyu.bid.bidmatch.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bid_match_scoring_models", indexes = {
        @Index(name = "idx_bid_match_model_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class BidMatchScoringModelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "draft_revision", nullable = false)
    private Long draftRevision;

    @Column(name = "model_json", nullable = false, columnDefinition = "text")
    private String modelJson;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Column(name = "active_version_no")
    private Integer activeVersionNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "INACTIVE";
        }
        if (draftRevision == null) {
            draftRevision = 1L;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
