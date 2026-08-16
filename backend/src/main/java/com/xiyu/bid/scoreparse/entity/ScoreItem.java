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
 * 评分项实体（spec 041 阶段 1 解析产物）。
 * <p>由四路召回 + LLM 结构化提取 + 合并去重 + 闭环校验后落库。
 * <p>校验规则（domain 纯核心执行）：weight &gt; 0；est_score ∈ [0, weight]；
 * score_type=SUBJECTIVE 时 est_score/kb_hit MUST NULL。
 */
@Entity
@Table(name = "score_item")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 冗余项目 ID 便于直查 */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 产生本批的解析任务 ID */
    @Column(name = "parse_task_id", nullable = false)
    private Long parseTaskId;

    /** 表内序号（编号可能重复，去重保留首次出现） */
    @Column(name = "item_index", nullable = false)
    private Integer itemIndex;

    /** 评分项编号（原文提取，如 A1/B2） */
    @Column(nullable = false, length = 50)
    private String code;

    /** 评分项名称 */
    @Column(nullable = false, length = 200)
    private String dim;

    /** 详细要素（完整保留原文，禁止摘要） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String detail;

    /** 权重绝对分值 */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal weight;

    /** OBJECTIVE / SUBJECTIVE（含报价类） */
    @Column(name = "score_type", nullable = false, length = 20)
    private String scoreType;

    /** OK / DANGER / PENDING（阶段 1 预计状态） */
    @Column(name = "status_stage1", nullable = false, length = 20)
    private String statusStage1;

    /** 预计得分；主观项 NULL */
    @Column(name = "est_score", precision = 6, scale = 2)
    private BigDecimal estScore;

    /** 阶段 1 评分依据 */
    @Column(name = "est_basis", columnDefinition = "TEXT")
    private String estBasis;

    /** 知识库命中标记（仅客观项可 true） */
    @Column(name = "kb_hit")
    private Boolean kbHit;

    /** 评分规则上下文（注/说明/备注） */
    @Column(name = "context_note", columnDefinition = "TEXT")
    private String contextNote;

    /** 原文依据 */
    @Column(name = "source_text", columnDefinition = "TEXT")
    private String sourceText;

    /** 页码/位置 */
    @Column(length = 200)
    private String location;

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
