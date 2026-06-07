package com.xiyu.bid.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_score_previews", indexes = {
        @Index(name = "idx_score_preview_project", columnList = "project_id,created_at"),
        @Index(name = "idx_score_preview_tender", columnList = "tender_id,created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectScorePreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "tender_id")
    private Long tenderId;

    @Column(name = "project_name", length = 255)
    private String projectName;

    @Column(length = 100)
    private String industry;

    @Column(precision = 19, scale = 2)
    private BigDecimal budget;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    @Column(name = "win_score", nullable = false)
    private Integer winScore;

    @Column(name = "win_level", nullable = false, length = 20)
    private String winLevel;

    @Column(name = "payload_json", columnDefinition = "TEXT", nullable = false)
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
