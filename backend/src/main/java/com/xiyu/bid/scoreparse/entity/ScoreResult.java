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
 * 打分结果实体（spec 041 阶段 2 产物）。
 * <p>与 {@link ScoreItem} 1:1（uk_sr_score_item），由投标文件 LLM 对标打分产生。
 * <p>校验规则（domain 纯核心执行）：actual_score ∈ [0, weight]（超区间置 NULL + PENDING）；
 * 状态判定：满分=OK、零分=DANGER、部分分或证书过期=PENDING。
 */
@Entity
@Table(name = "score_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1:1 关联评分项 */
    @Column(name = "score_item_id", nullable = false, unique = true)
    private Long scoreItemId;

    /** 产生本结果的打分任务 ID */
    @Column(name = "scoring_task_id", nullable = false)
    private Long scoringTaskId;

    /** 实际得分；主观项/异常项 NULL */
    @Column(name = "actual_score", precision = 6, scale = 2)
    private BigDecimal actualScore;

    /** OK / DANGER / PENDING（阶段 2 打分状态） */
    @Column(name = "status_stage2", nullable = false, length = 20)
    private String statusStage2;

    /** 评分依据 */
    @Column(columnDefinition = "TEXT")
    private String evidence;

    /** 标书引用原文（含章节页码）；无则 NULL（前端显示"标书引用：无"） */
    @Column(columnDefinition = "TEXT")
    private String quote;

    /** 缺失说明 */
    @Column(name = "missed_reason", columnDefinition = "TEXT")
    private String missedReason;

    /** 修改建议（主观项/待确认/不满足项） */
    @Column(columnDefinition = "TEXT")
    private String suggestion;

    /** 匹配比例 0-100 */
    @Column(name = "match_ratio")
    private Integer matchRatio;

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
