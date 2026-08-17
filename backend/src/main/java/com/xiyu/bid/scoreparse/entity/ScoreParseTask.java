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

import java.time.LocalDateTime;

/**
 * AI 评分解析/打分异步任务实体（spec 041）。
 * <p>持久化解析（PARSE）与打分（SCORING）两类异步任务状态，
 * 支持超时扫描（30min）与服务重启后恢复。
 * <p>参考 TenderImportTask 范式（spec 031 异步四件套）。
 *
 * @see com.xiyu.bid.tender.entity.TenderImportTask
 */
@Entity
@Table(name = "score_parse_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreParseTask {

    /** 业务任务 ID（UUID），对外暴露防猜测 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 36)
    private String taskId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** PARSE / SCORING */
    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType;

    /** PENDING / PROCESSING / COMPLETED / FAILED（终态不回退） */
    @Column(nullable = false, length = 20)
    private String status;

    /** 进度 0-100 */
    @Column(nullable = false)
    @Builder.Default
    private Integer progress = 0;

    /** 进度阶段描述（召回/提取/校验/匹配/打分） */
    @Column(length = 50)
    private String stage;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** doc-insight:// URL */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "trigger_source", length = 16)
    private String triggerSource;

    @Column(name = "bid_content_hash", length = 64)
    private String bidContentHash;

    @Column(name = "item_set_hash", length = 64)
    private String itemSetHash;

    @Column(name = "chapter_hashes", columnDefinition = "TEXT")
    private String chapterHashes;

    /** 超时扫描 job 标记（超时置 FAILED 时写 1） */
    @Column(name = "timeout_marked", nullable = false)
    @Builder.Default
    private Boolean timeoutMarked = false;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

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
