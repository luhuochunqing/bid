package com.xiyu.bid.performance.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 业绩合订本导出任务实体。
 *
 * <p>对标 WarehouseExportTaskEntity，状态机：PENDING → PROCESSING → COMPLETED/FAILED。
 * 文件 TTL 默认 24h，由 PerformanceBundleExportTaskStateService.complete 写入 expires_at。
 */
@Entity
@Table(name = "performance_export_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceExportTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExportStatus status;

    @Column(name = "filter_snapshot", columnDefinition = "TEXT")
    private String filterSnapshot;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "stored_file_path", length = 500)
    private String storedFilePath;

    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /** 导出统计 JSON：totalCount/wordBytes/elapsedMs/筛选摘要 */
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    public enum ExportStatus { PENDING, PROCESSING, COMPLETED, FAILED }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ExportStatus.PENDING;
    }
}
