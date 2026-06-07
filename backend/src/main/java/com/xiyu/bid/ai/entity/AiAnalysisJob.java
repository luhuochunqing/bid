package com.xiyu.bid.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis_jobs", indexes = {
        @Index(name = "idx_ai_job_target", columnList = "target_type,target_id"),
        @Index(name = "idx_ai_job_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 50)
    private AnalysisType analysisType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobStatus status;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AnalysisType {
        TENDER_ANALYSIS,
        PROJECT_SCORE_PREVIEW
    }

    public enum TargetType {
        TENDER,
        PROJECT
    }

    public enum JobStatus {
        PENDING,
        COMPLETED,
        FAILED
    }
}
