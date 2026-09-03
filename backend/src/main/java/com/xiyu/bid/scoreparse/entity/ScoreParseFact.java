package com.xiyu.bid.scoreparse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不可变评分事实快照实体（spec 059 Phase 5）。
 * <p>持久化单次解析从 Word/Sidecar 网格中提取的确定性事实。
 */
@Entity
@Table(name = "score_parse_fact")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreParseFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parse_task_id", nullable = false)
    private Long parseTaskId;

    @Column(name = "fact_id", nullable = false, length = 64)
    private String factId;

    @Column(name = "source_table_id", length = 64)
    private String sourceTableId;

    @Column(name = "source_cell_ids", columnDefinition = "JSON")
    private String sourceCellIds;

    @Column(name = "source_text", nullable = false, columnDefinition = "TEXT")
    private String sourceText;

    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String dimension;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    @Column(name = "weight_evidence", columnDefinition = "JSON")
    private String weightEvidence;

    @Column(name = "parent_fact_id", length = 64)
    private String parentFactId;

    @Column(name = "extraction_method", nullable = false, length = 64)
    private String extractionMethod;

    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "validation_status", nullable = false, length = 32)
    @Builder.Default
    private String validationStatus = "AUTO_APPROVED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
