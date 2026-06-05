package com.xiyu.bid.casework.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "knowledge_case")
public class KnowledgeCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_project_id", nullable = false)
    private Long sourceProjectId;

    @Column(name = "source_project_name", nullable = false)
    private String sourceProjectName;

    @Column(name = "scoring_point_title", nullable = false)
    private String scoringPointTitle;

    @Column(name = "requirement_raw", nullable = false, columnDefinition = "TEXT")
    private String requirementRaw;

    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "reuse_count", nullable = false)
    private Integer reuseCount = 0;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(name = "status", nullable = false)
    private String status; // ACTIVE, OFF_SHELF

    @Column(name = "customer_type")
    private String customerType;

    @Column(name = "project_type")
    private String projectType;

    @Column(name = "bid_result", length = 20)
    private String bidResult;

    @Column(name = "scoring_category", length = 50)
    private String scoringCategory;

    @Column(name = "product_line", length = 100)
    private String productLine;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
