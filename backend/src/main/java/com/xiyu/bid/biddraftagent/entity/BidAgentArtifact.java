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
@Table(name = "bid_agent_artifacts", indexes = {
        @Index(name = "idx_bid_agent_artifacts_run", columnList = "run_id"),
        @Index(name = "idx_bid_agent_artifacts_type", columnList = "artifact_type"),
        @Index(name = "idx_bid_agent_artifacts_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidAgentArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "artifact_type", nullable = false, length = 60)
    private String artifactType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(name = "handoff_target", length = 100)
    private String handoffTarget;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    @Builder.Default
    private Status status = Status.DRAFTED;

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
        READY_FOR_WRITER
    }
}
