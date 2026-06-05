package com.xiyu.bid.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_analysis_results", indexes = {
        @Index(name = "idx_ai_result_tender", columnList = "tender_id,created_at"),
        @Index(name = "idx_ai_result_project", columnList = "project_id,created_at"),
        @Index(name = "idx_ai_result_job", columnList = "job_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "tender_id")
    private Long tenderId;

    @Column(name = "project_id")
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 50)
    private AiAnalysisJob.AnalysisType analysisType;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "risk_level", length = 30)
    private String riskLevel;

    @Column(length = 500)
    private String suggestion;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
