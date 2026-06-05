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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bid_match_score_evaluations", indexes = {
        @Index(name = "idx_bid_match_eval_tender", columnList = "tender_id"),
        @Index(name = "idx_bid_match_eval_version", columnList = "model_version_id"),
        @Index(name = "idx_bid_match_eval_time", columnList = "evaluated_at")
})
@Getter
@Setter
@NoArgsConstructor
public class BidMatchScoreEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "model_id", nullable = false)
    private Long modelId;

    @Column(name = "model_version_id", nullable = false)
    private Long modelVersionId;

    @Column(name = "model_version_no", nullable = false)
    private Integer modelVersionNo;

    @Column(name = "total_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "dimension_scores_json", nullable = false, columnDefinition = "text")
    private String dimensionScoresJson;

    @Column(name = "evidence_json", nullable = false, columnDefinition = "text")
    private String evidenceJson;

    @Column(name = "evidence_fingerprint", nullable = false, length = 128)
    private String evidenceFingerprint;

    @Column(name = "model_snapshot_json", nullable = false, columnDefinition = "text")
    private String modelSnapshotJson;

    @Column(name = "evaluated_by", length = 100)
    private String evaluatedBy;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        if (evaluatedAt == null) {
            evaluatedAt = now;
        }
    }
}
