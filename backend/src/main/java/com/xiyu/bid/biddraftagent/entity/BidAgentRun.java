package com.xiyu.bid.biddraftagent.entity;

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
@Table(name = "bid_agent_runs", indexes = {
        @Index(name = "idx_bid_agent_runs_project", columnList = "project_id"),
        @Index(name = "idx_bid_agent_runs_tender", columnList = "tender_id"),
        @Index(name = "idx_bid_agent_runs_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidAgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "project_name", nullable = false, length = 500)
    private String projectName;

    @Column(name = "tender_title", nullable = false, length = 500)
    private String tenderTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private Status status = Status.DRAFTED;

    @Column(name = "snapshot_json", columnDefinition = "text", nullable = false)
    private String snapshotJson;

    @Column(name = "requirement_classification_json", columnDefinition = "text", nullable = false)
    private String requirementClassificationJson;

    @Column(name = "material_match_score_json", columnDefinition = "text", nullable = false)
    private String materialMatchScoreJson;

    @Column(name = "gap_check_json", columnDefinition = "text", nullable = false)
    private String gapCheckJson;

    @Column(name = "manual_confirmation_json", columnDefinition = "text", nullable = false)
    private String manualConfirmationJson;

    @Column(name = "write_coverage_json", columnDefinition = "text", nullable = false)
    private String writeCoverageJson;

    @Column(name = "draft_text", columnDefinition = "text", nullable = false)
    private String draftText;

    @Column(name = "review_text", columnDefinition = "text")
    private String reviewText;

    @Column(name = "generator_key", nullable = false, length = 100)
    private String generatorKey;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = Status.DRAFTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum Status {
        DRAFTED,
        REVIEWED,
        READY_FOR_WRITER
    }
}
