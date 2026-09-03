package com.xiyu.bid.scoreparse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评分原文单元格证据实体（spec 059 Phase 5）。
 * <p>持久化单次事实在文档表格网格中的精确单元格定位与引用。
 */
@Entity
@Table(name = "score_parse_evidence")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreParseEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fact_id", nullable = false, length = 64)
    private String factId;

    @Column(name = "parse_task_id", nullable = false)
    private Long parseTaskId;

    @Column(name = "cell_id", length = 64)
    private String cellId;

    @Column(nullable = false, length = 64)
    private String kind;

    @Column(name = "quoted_text", columnDefinition = "TEXT")
    private String quotedText;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Column(length = 255)
    private String explanation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
